package com.gamelaunch.frontend.domain.usecase

import com.gamelaunch.frontend.data.repository.RateLimitException
import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.model.GameMedia
import com.gamelaunch.frontend.domain.model.ScraperConfig
import com.gamelaunch.frontend.domain.platform.PlatformDefinitions
import com.gamelaunch.frontend.domain.repository.GameRepository
import com.gamelaunch.frontend.domain.repository.MediaRepository
import com.gamelaunch.frontend.domain.repository.ScraperRepository
import javax.inject.Inject

sealed class ScrapeResult {
    data class Success(val gameId: Long, val title: String) : ScrapeResult()
    data class NotFound(val gameId: Long, val romName: String) : ScrapeResult()
    data class RateLimited(val gameId: Long) : ScrapeResult()
    data class Error(val gameId: Long, val cause: Throwable) : ScrapeResult()
}

class ScrapeGameUseCase @Inject constructor(
    private val scraperRepository: ScraperRepository,
    private val scrapeLaunchBoxUseCase: ScrapeLaunchBoxUseCase,
    private val libretroThumbnailScraper: LibretroThumbnailScraper,
    private val gameRepository: GameRepository,
    private val mediaRepository: MediaRepository
) {
    suspend operator fun invoke(game: Game, config: ScraperConfig): ScrapeResult {
        val platform = PlatformDefinitions.byId[game.platformId]
            ?: return ScrapeResult.Error(game.id, IllegalArgumentException("Unknown platform: ${game.platformId}"))

        // ── Play Store Scraper for Android Platform ──
        if (game.platformId == "android") {
            val pkg = game.romFilename
            val url = "https://play.google.com/store/apps/details?id=$pkg"
            val result = runCatching {
                val httpClient = okhttp3.OkHttpClient()
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .build()
                httpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching null
                    val html = resp.body?.string() ?: return@runCatching null

                    val titleRegex = """<meta\s+property="og:title"\s+content="([^"]+)"""".toRegex(RegexOption.IGNORE_CASE)
                    val descRegex = """<meta\s+property="og:description"\s+content="([^"]+)"""".toRegex(RegexOption.IGNORE_CASE)
                    val imageRegex = """<meta\s+property="og:image"\s+content="([^"]+)"""".toRegex(RegexOption.IGNORE_CASE)

                    val scrapedTitle = titleRegex.find(html)?.groupValues?.get(1)?.substringBefore(" - Apps on Google Play") ?: game.title
                    val scrapedDesc = descRegex.find(html)?.groupValues?.get(1)
                    val scrapedImageUrl = imageRegex.find(html)?.groupValues?.get(1)

                    Triple(scrapedTitle, scrapedDesc, scrapedImageUrl)
                }
            }.getOrNull()

            if (result != null) {
                val (scrapedTitle, scrapedDesc, scrapedImageUrl) = result
                if (config.scrapeMetadata) {
                    gameRepository.updateScrapedMetadata(
                        gameId        = game.id,
                        scraperGameId = null,
                        title         = scrapedTitle,
                        description   = scrapedDesc,
                        genre         = "Android Game",
                        releaseYear   = null,
                        rating        = null
                    )
                } else {
                    gameRepository.markScraped(game.id, game.title)
                }

                if (scrapedImageUrl != null) {
                    val media = GameMedia(
                        gameId             = game.id,
                        boxArtRemoteUrl    = scrapedImageUrl,
                        scraperTimestampMs = System.currentTimeMillis()
                    )
                    mediaRepository.upsertMedia(media)
                    runCatching { mediaRepository.downloadAndCacheBoxArt(game.id, scrapedImageUrl) }
                }
                return ScrapeResult.Success(game.id, if (config.scrapeMetadata) scrapedTitle else game.title)
            } else {
                return ScrapeResult.NotFound(game.id, game.romFilename)
            }
        }

        // ── ScreenScraper (default/preferred — requires user to have a free SS account) ──
        // Dev credentials (devid/devpassword) are compiled in from local.properties.
        // User credentials (ssid/sspassword) must be set in Settings.
        // Falls through to libretro → LaunchBox only on 404 or other non-429 error.
        if (config.isConfigured) {
            val ssResult = scraperRepository.scrapeGame(
                config   = config,
                systemId = platform.scraperSystemId,
                romName  = game.romFilename,
                md5      = game.md5
            )

            ssResult.onSuccess { gameInfo ->
                // When metadata scraping is off, keep the existing title and don't overwrite
                // description/genre/year/rating — only the media below is fetched.
                val scrapedTitle = if (config.scrapeMetadata) {
                    val title = gameInfo.getBestName(config.preferredRegion) ?: game.title
                    gameRepository.updateScrapedMetadata(
                        gameId        = game.id,
                        scraperGameId = gameInfo.id?.toLongOrNull(),
                        title         = title,
                        description   = gameInfo.getBestSynopsis(config.preferredRegion),
                        genre         = gameInfo.getPrimaryGenre(),
                        releaseYear   = gameInfo.getReleaseYear(config.preferredRegion),
                        rating        = gameInfo.getRating()
                    )
                    title
                } else {
                    gameRepository.markScraped(game.id, game.title)
                    game.title
                }
                val media = GameMedia(
                    gameId             = game.id,
                    boxArtRemoteUrl    = if (config.scrapeBoxArt)     gameInfo.getMediaUrl("box-2D", config.preferredRegion) else null,
                    screenshotRemoteUrl= if (config.scrapeScreenshots) gameInfo.getMediaUrl("ss",    config.preferredRegion) else null,
                    wheelLogoRemoteUrl = if (config.scrapeWheelLogos)  gameInfo.getMediaUrl("wheel", config.preferredRegion) else null,
                    videoRemoteUrl     = if (config.scrapeVideos)
                        gameInfo.getMediaUrl("video-normalized", config.preferredRegion)
                            ?: gameInfo.getMediaUrl("video", config.preferredRegion)
                        else null,
                    scraperTimestampMs = System.currentTimeMillis()
                )
                mediaRepository.upsertMedia(media)
                media.boxArtRemoteUrl?.let     { mediaRepository.downloadAndCacheBoxArt(game.id, it) }
                media.screenshotRemoteUrl?.let { mediaRepository.downloadAndCacheScreenshot(game.id, it) }
                media.wheelLogoRemoteUrl?.let  { mediaRepository.downloadAndCacheWheelLogo(game.id, it) }
                media.videoRemoteUrl?.let      { mediaRepository.downloadAndCacheVideo(game.id, it) }
                return ScrapeResult.Success(game.id, scrapedTitle)
            }

            // Only a rate-limit stops the batch; any other ScreenScraper failure
            // (incl. bad/missing creds or "not found") falls through to the free sources.
            if (ssResult.exceptionOrNull() is RateLimitException) return ScrapeResult.RateLimited(game.id)
        }

        // ── libretro thumbnails (free fallback — box art + screenshot only) ──
        // It contributes no metadata, so only bother when the user actually wants artwork.
        if (config.scrapeBoxArt || config.scrapeScreenshots) {
            val thumbs = runCatching {
                libretroThumbnailScraper.fetch(game.romFilename, game.platformId)
            }.getOrNull()

            if (thumbs != null) {
                val boxArt     = if (config.scrapeBoxArt) thumbs.boxArt else null
                val screenshot = if (config.scrapeScreenshots) thumbs.screenshot else null
                if (boxArt != null || screenshot != null) {
                    val media = GameMedia(
                        gameId              = game.id,
                        boxArtRemoteUrl     = boxArt,
                        screenshotRemoteUrl = screenshot,
                        scraperTimestampMs  = System.currentTimeMillis()
                    )
                    mediaRepository.upsertMedia(media)
                    boxArt?.let     { mediaRepository.downloadAndCacheBoxArt(game.id, it) }
                    screenshot?.let { url -> runCatching { mediaRepository.downloadAndCacheScreenshot(game.id, url) } }
                    // Mark scraped so we don't re-fetch every run, keeping the existing title.
                    gameRepository.markScraped(game.id, game.title)
                    return ScrapeResult.Success(game.id, game.title)
                }
            }
        }

        // ── LaunchBox fallback (metadata + art) ───────────────────────────
        val lbMedia = runCatching {
            scrapeLaunchBoxUseCase(game.title, game.platformId)
        }.getOrNull()

        if (lbMedia != null) {
            if (config.scrapeMetadata) {
                gameRepository.updateScrapedMetadata(
                    gameId        = game.id,
                    scraperGameId = null,
                    title         = lbMedia.title,
                    description   = lbMedia.overview,
                    genre         = null,
                    releaseYear   = lbMedia.releaseYear,
                    rating        = lbMedia.rating
                )
            } else {
                gameRepository.markScraped(game.id, game.title)
            }
            val media = GameMedia(
                gameId              = game.id,
                boxArtRemoteUrl     = if (config.scrapeBoxArt)     lbMedia.boxFrontUrl  else null,
                screenshotRemoteUrl = if (config.scrapeScreenshots) lbMedia.screenshotUrl else null,
                wheelLogoRemoteUrl  = if (config.scrapeWheelLogos)  lbMedia.logoUrl       else null,
                videoRemoteUrl      = null, // LaunchBox has no hosted videos
                scraperTimestampMs  = System.currentTimeMillis()
            )
            mediaRepository.upsertMedia(media)
            media.boxArtRemoteUrl?.let     { mediaRepository.downloadAndCacheBoxArt(game.id, it) }
            media.screenshotRemoteUrl?.let { mediaRepository.downloadAndCacheScreenshot(game.id, it) }
            media.wheelLogoRemoteUrl?.let  { mediaRepository.downloadAndCacheWheelLogo(game.id, it) }
            return ScrapeResult.Success(game.id, if (config.scrapeMetadata) lbMedia.title else game.title)
        }

        return ScrapeResult.NotFound(game.id, game.romFilename)
    }
}
