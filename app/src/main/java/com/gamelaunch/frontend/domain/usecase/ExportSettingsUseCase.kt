package com.gamelaunch.frontend.domain.usecase

import com.gamelaunch.frontend.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

/**
 * Serialises the safe subset of app settings ([SettingsTransfer.EXPORTED_KEYS]) to a JSON string that
 * can be downloaded from the Web Transfer page and re-imported on another device. Credentials, tokens
 * and the Locked-Mode PIN are never included (see [SettingsTransfer.SENSITIVE_KEYS]).
 */
class ExportSettingsUseCase @Inject constructor(
    private val settings: SettingsRepository
) {
    suspend operator fun invoke(): String {
        val scraper = settings.scraperConfig.first()
        val json = JSONObject().apply {
            put("format", SettingsTransfer.FORMAT)
            put("version", SettingsTransfer.VERSION)

            put(SettingsTransfer.ROM_ROOT_PATH, settings.romRootPath.first())
            put(SettingsTransfer.BIOS_FOLDER_PATH, settings.biosFolderPath.first())
            put(SettingsTransfer.MEDIA_STORAGE_PATH, settings.mediaStoragePath.first())
            put(SettingsTransfer.STEAM_LIBRARY_PATH, settings.steamLibraryPath.first())

            put(SettingsTransfer.LAYOUT_MODE, settings.layoutMode.first().name)
            put(SettingsTransfer.DARK_MODE, settings.darkMode.first())
            put(SettingsTransfer.CARD_COLOR_SCHEME, settings.cardColorScheme.first().name)
            put(SettingsTransfer.CARD_MONO_COLOR, settings.cardMonoColor.first())

            put(SettingsTransfer.BG_IMAGE_ENABLED, settings.backgroundImageEnabled.first())
            put(SettingsTransfer.BG_IMAGE_MODE, settings.backgroundImageMode.first())
            put(SettingsTransfer.BG_IMAGE_OPACITY, settings.backgroundImageOpacity.first().toDouble())

            put(SettingsTransfer.VIDEO_AUTOPLAY_DELAY_MS, settings.videoAutoplayDelayMs.first())
            put(SettingsTransfer.VIDEO_MUTED, settings.videoMuted.first())

            put(SettingsTransfer.SHOW_RECENTLY_PLAYED, settings.showRecentlyPlayed.first())
            put(SettingsTransfer.SHOW_FAVORITES, settings.showFavorites.first())
            put(SettingsTransfer.SHOW_RETRO_ACHIEVEMENTS, settings.showRetroAchievements.first())

            put(SettingsTransfer.DUAL_SCREEN_ENABLED, settings.dualScreenEnabled.first())
            put(SettingsTransfer.DUAL_SCREEN_SWAP, settings.dualScreenSwap.first())
            put(SettingsTransfer.GAME_LAUNCH_ON_TOP, settings.gameLaunchOnTop.first())
            put(SettingsTransfer.PERFORMANCE_MODE, settings.performanceMode.first())

            put(SettingsTransfer.PREFERRED_REGION, scraper.preferredRegion)
            put(SettingsTransfer.SCRAPE_METADATA, scraper.scrapeMetadata)
            put(SettingsTransfer.SCRAPE_BOX_ART, scraper.scrapeBoxArt)
            put(SettingsTransfer.SCRAPE_SCREENSHOTS, scraper.scrapeScreenshots)
            put(SettingsTransfer.SCRAPE_WHEEL_LOGOS, scraper.scrapeWheelLogos)
            put(SettingsTransfer.SCRAPE_VIDEOS, scraper.scrapeVideos)

            put(SettingsTransfer.GAME_SORT, settings.gameSort.first().name)
            put(SettingsTransfer.SYSTEM_SORT, JSONArray(settings.systemSort.first().map { it.name }))
            put(SettingsTransfer.MASTER_GAME_GRID_COLUMNS, settings.masterGameGridColumns.first())
            put(SettingsTransfer.HIDDEN_PLATFORMS, JSONArray(settings.hiddenPlatforms.first().toList()))
            put(SettingsTransfer.EMULATOR_UPDATE_NOTIFICATIONS, settings.emulatorUpdateNotifications.first())
        }
        return json.toString(2)
    }
}
