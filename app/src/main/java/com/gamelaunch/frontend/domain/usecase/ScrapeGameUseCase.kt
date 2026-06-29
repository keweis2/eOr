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
    data class Success(val gameId: Long) : ScrapeResult()
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
                if (config.scrapeMetadata) {
                    gameRepository.updateScrapedMetadata(
                        gameId        = game.id,
                        scraperGameId = gameInfo.id?.toLongOrNull(),
                        title         = gameInfo.getBestName(config.preferredRegion) ?: game.title,
                        description   = gameInfo.getBestSynopsis(config.preferredRegion),
                        genre         = gameInfo.getPrimaryGenre(),
                        releaseYear   = gameInfo.getReleaseYear(config.preferredRegion),
                        rating        = gameInfo.getRating()
                    )
                } else {
                    gameRepository.markScraped(game.id, game.title)
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
                return ScrapeResult.Success(game.id)
            }

            // Only a rate-limit stops the batch; any other ScreenScraper failure
            // (incl. bad/missing creds or "not found") falls through to the free sources.
            if (ssResult.exceptionOrNull() is RateLimitException) return ScrapeResult.RateLimited(game.id)
        }

        // ── libretro thumbnails (no credentials — box art + screenshot + title) ──
        val thumbs = runCatching {
            libretroThumbnailScraper.fetch(game.romFilename, game.platformId)
        }.getOrNull()

        if (thumbs != null) {
            val media = GameMedia(
                gameId              = game.id,
                boxArtRemoteUrl     = thumbs.boxArt,
                screenshotRemoteUrl = thumbs.screenshot,
                scraperTimestampMs  = System.currentTimeMillis()
            )
            mediaRepository.upsertMedia(media)
            mediaRepository.downloadAndCacheBoxArt(game.id, thumbs.boxArt)
            runCatching { mediaRepository.downloadAndCacheScreenshot(game.id, thumbs.screenshot) }
            // Mark scraped so we don't re-fetch every run, keeping the existing title.
            gameRepository.updateScrapedMetadata(
                gameId = game.id, scraperGameId = null, title = game.title,
                description = null, genre = null, releaseYear = null, rating = null
            )
            return ScrapeResult.Success(game.id)
        }

        // ── LaunchBox fallback ───────────────────────────────────────────
        val lbMedia = runCatching {
            scrapeLaunchBoxUseCase(game.title, game.platformId)
        }.getOrNull()

        if (lbMedia != null) {
            // Update metadata from LaunchBox
            gameRepository.updateScrapedMetadata(
                gameId        = game.id,
                scraperGameId = null,
                title         = lbMedia.title,
                description   = lbMedia.overview,
                genre         = null,
                releaseYear   = lbMedia.releaseYear,
                rating        = lbMedia.rating
            )
            val media = GameMedia(
                gameId              = game.id,
                boxArtRemoteUrl     = lbMedia.boxFrontUrl,
                screenshotRemoteUrl = lbMedia.screenshotUrl,
                wheelLogoRemoteUrl  = lbMedia.logoUrl,
                videoRemoteUrl      = null, // LaunchBox has no hosted videos
                scraperTimestampMs  = System.currentTimeMillis()
            )
            mediaRepository.upsertMedia(media)
            lbMedia.boxFrontUrl?.let     { mediaRepository.downloadAndCacheBoxArt(game.id, it) }
            lbMedia.screenshotUrl?.let   { mediaRepository.downloadAndCacheScreenshot(game.id, it) }
            lbMedia.logoUrl?.let         { mediaRepository.downloadAndCacheWheelLogo(game.id, it) }
            return ScrapeResult.Success(game.id)
        }

        return ScrapeResult.NotFound(game.id, game.romFilename)
    }
}
