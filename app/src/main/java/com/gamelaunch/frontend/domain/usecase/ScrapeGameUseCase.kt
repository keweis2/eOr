package com.gamelaunch.frontend.domain.usecase

import com.gamelaunch.frontend.util.LegacyTls
import com.gamelaunch.frontend.data.network.SteamGridDbScraper
import com.gamelaunch.frontend.data.network.dto.GameInfoDto
import com.gamelaunch.frontend.data.repository.RateLimitException
import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.model.GameMedia
import com.gamelaunch.frontend.domain.model.ScraperConfig
import com.gamelaunch.frontend.domain.platform.PlatformDefinitions
import com.gamelaunch.frontend.domain.repository.GameRepository
import com.gamelaunch.frontend.domain.repository.MediaRepository
import com.gamelaunch.frontend.domain.repository.ScraperRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
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
    private val steamGridDbScraper: SteamGridDbScraper,
    private val gameRepository: GameRepository,
    private val mediaRepository: MediaRepository
) {
    suspend operator fun invoke(game: Game, config: ScraperConfig): ScrapeResult {
        val result = scrapeSources(game, onlyMissing(game, config))
        if (!config.hasSteamGridDb) return result
        if (result is ScrapeResult.RateLimited || result is ScrapeResult.Error) return result
        val filled = runCatching { fillFromSteamGridDb(game, config) }.getOrDefault(false)
        if (result is ScrapeResult.NotFound && filled) {
            // Nothing else knew the game but SteamGridDB had art — keep the title, mark it done.
            gameRepository.markScraped(game.id, game.title)
            return ScrapeResult.Success(game.id, game.title)
        }
        return result
    }

    /**
     * Adds a SteamGridDB cover and/or logo where the game still has none (after the other
     * sources ran). Never replaces art that's already there. Returns true if it added anything.
     */
    private suspend fun fillFromSteamGridDb(game: Game, config: ScraperConfig): Boolean {
        val existing = mediaRepository.getMediaForGame(game.id)
        val needBox = config.scrapeBoxArt && existing?.hasBoxArt != true
        val needLogo = config.scrapeWheelLogos &&
            existing?.wheelLogoLocalPath == null && existing?.wheelLogoRemoteUrl == null
        if (!needBox && !needLogo) return false
        val art = steamGridDbScraper.findArt(
            apiKey = config.steamGridDbKey,
            title = game.title,
            steamAppId = SteamGridDbScraper.steamAppId(game.romPath),
            wantBoxArt = needBox,
            wantLogo = needLogo
        ) ?: return false
        mediaRepository.upsertMedia(
            GameMedia(
                gameId             = game.id,
                boxArtRemoteUrl    = art.boxArt,
                wheelLogoRemoteUrl = art.logo,
                scraperTimestampMs = System.currentTimeMillis()
            )
        )
        art.boxArt?.let { mediaRepository.downloadAndCacheBoxArt(game.id, it) }
        art.logo?.let { mediaRepository.downloadAndCacheWheelLogo(game.id, it) }
        return true
    }

    /**
     * Narrow [config]'s media switches to the types this game doesn't already have. A game is often
     * queued for just one gap (a wheel logo, a description); without this every source re-downloaded
     * ALL enabled types and repointed the game at eOr's own copy — silently duplicating, and then
     * shadowing, art the user already had (e.g. an ES-DE library). Existing media is never replaced.
     */
    private suspend fun onlyMissing(game: Game, config: ScraperConfig): ScraperConfig {
        val have = mediaRepository.getMediaForGame(game.id) ?: return config
        return config.copy(
            scrapeBoxArt      = config.scrapeBoxArt && !have.hasBoxArt,
            scrapeScreenshots = config.scrapeScreenshots && have.effectiveScreenshot == null,
            scrapeWheelLogos  = config.scrapeWheelLogos &&
                have.wheelLogoLocalPath == null && have.wheelLogoRemoteUrl == null,
            scrapeVideos      = config.scrapeVideos && !have.hasVideo,
            scrapeMiximages   = config.scrapeScreenshots && have.effectiveMiximage == null,
        )
    }

    /** Compute (once) and persist the ROM hash ScreenScraper matches on; null if unreadable. */
    private suspend fun hashAndStore(game: Game): String? = withContext(Dispatchers.IO) {
        if (!game.romPath.startsWith("/")) return@withContext null  // synthetic entries (Steam, Android)
        RomHasher.partialMd5(File(game.romPath))?.also { gameRepository.updateMd5(game.id, it) }
    }

    /** ScreenScraper box art: the 3D box render first when the user prefers it, else the 2D cover. */
    private fun ssBoxArt(info: GameInfoDto, config: ScraperConfig): String? =
        (if (config.prefer3dBoxes) info.getMediaUrl("box-3D", config.preferredRegion) else null)
            ?: info.getMediaUrl("box-2D", config.preferredRegion)

    private suspend fun scrapeSources(game: Game, config: ScraperConfig): ScrapeResult {
        val platform = PlatformDefinitions.byId[game.platformId]
            ?: return ScrapeResult.Error(game.id, IllegalArgumentException("Unknown platform: ${game.platformId}"))

        // ── Play Store Scraper for Android Platform ──
        if (game.platformId == "android") {
            val pkg = game.romFilename
            val url = "https://play.google.com/store/apps/details?id=$pkg"
            // Network I/O must run off the main thread; the batch-scrape flow is collected on
            // Dispatchers.Main, so a blocking OkHttp call here would throw
            // NetworkOnMainThreadException (swallowed by runCatching → always NotFound).
            val result = withContext(Dispatchers.IO) {
                runCatching {
                val httpClient = LegacyTls.client()
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
            }

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

                if (scrapedImageUrl != null && config.scrapeBoxArt) {
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
                md5      = game.md5 ?: hashAndStore(game)
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
                    boxArtRemoteUrl    = if (config.scrapeBoxArt)     ssBoxArt(gameInfo, config) else null,
                    screenshotRemoteUrl= if (config.scrapeScreenshots) gameInfo.getMediaUrl("ss",    config.preferredRegion) else null,
                    wheelLogoRemoteUrl = if (config.scrapeWheelLogos)  gameInfo.getMediaUrl("wheel", config.preferredRegion) else null,
                    videoRemoteUrl     = if (config.scrapeVideos)
                        gameInfo.getMediaUrl("video-normalized", config.preferredRegion)
                            ?: gameInfo.getMediaUrl("video", config.preferredRegion)
                        else null,
                    // ScreenScraper's server-composited "mix" (box + screenshot + logo). Fetched
                    // alongside screenshots so the dual-screen "miximage" top-panel option has art.
                    miximageRemoteUrl  = if (config.scrapeMiximages)
                        gameInfo.getMediaUrl("mixrbv2", config.preferredRegion)
                            ?: gameInfo.getMediaUrl("mixrbv1", config.preferredRegion)
                        else null,
                    scraperTimestampMs = System.currentTimeMillis()
                )
                mediaRepository.upsertMedia(media)
                media.boxArtRemoteUrl?.let     {
                    val is3d = config.prefer3dBoxes && it == gameInfo.getMediaUrl("box-3D", config.preferredRegion)
                    mediaRepository.downloadAndCacheBoxArt(game.id, it, threeD = is3d)
                }
                media.screenshotRemoteUrl?.let { mediaRepository.downloadAndCacheScreenshot(game.id, it) }
                media.wheelLogoRemoteUrl?.let  { mediaRepository.downloadAndCacheWheelLogo(game.id, it) }
                media.videoRemoteUrl?.let      { mediaRepository.downloadAndCacheVideo(game.id, it) }
                media.miximageRemoteUrl?.let   { mediaRepository.downloadAndCacheMiximage(game.id, it) }
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
