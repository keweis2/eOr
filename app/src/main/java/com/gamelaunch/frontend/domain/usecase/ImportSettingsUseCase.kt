package com.gamelaunch.frontend.domain.usecase

import com.gamelaunch.frontend.domain.model.GameSort
import com.gamelaunch.frontend.domain.platform.SystemSort
import com.gamelaunch.frontend.domain.repository.SettingsRepository
import com.gamelaunch.frontend.ui.theme.CardColorScheme
import com.gamelaunch.frontend.ui.theme.LayoutMode
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import javax.inject.Inject

/**
 * Applies a settings JSON produced by [ExportSettingsUseCase]. Only keys in
 * [SettingsTransfer.EXPORTED_KEYS] are read; anything else in the document is ignored, so a tampered
 * file can never reach a credential or the Locked-Mode PIN. Returns the number of settings applied.
 */
class ImportSettingsUseCase @Inject constructor(
    private val settings: SettingsRepository
) {
    suspend operator fun invoke(jsonText: String): Int {
        val json = JSONObject(jsonText)
        require(json.optString("format") == SettingsTransfer.FORMAT) { "Not an eOr settings file" }

        var applied = 0
        suspend fun step(key: String, block: suspend (JSONObject) -> Unit) {
            if (!json.has(key) || key !in SettingsTransfer.EXPORTED_KEYS) return
            runCatching { block(json); applied++ }
        }

        step(SettingsTransfer.ROM_ROOT_PATH) { settings.setRomRootPath(it.getString(SettingsTransfer.ROM_ROOT_PATH)) }
        step(SettingsTransfer.BIOS_FOLDER_PATH) { settings.setBiosFolderPath(it.getString(SettingsTransfer.BIOS_FOLDER_PATH)) }
        step(SettingsTransfer.MEDIA_STORAGE_PATH) { settings.setMediaStoragePath(it.getString(SettingsTransfer.MEDIA_STORAGE_PATH)) }
        step(SettingsTransfer.STEAM_LIBRARY_PATH) { settings.setSteamLibraryPath(it.getString(SettingsTransfer.STEAM_LIBRARY_PATH)) }

        step(SettingsTransfer.LAYOUT_MODE) { settings.setLayoutMode(LayoutMode.valueOf(it.getString(SettingsTransfer.LAYOUT_MODE))) }
        step(SettingsTransfer.DARK_MODE) { settings.setDarkMode(it.getBoolean(SettingsTransfer.DARK_MODE)) }
        step(SettingsTransfer.CARD_COLOR_SCHEME) { settings.setCardColorScheme(CardColorScheme.fromName(it.getString(SettingsTransfer.CARD_COLOR_SCHEME))) }
        step(SettingsTransfer.CARD_MONO_COLOR) { settings.setCardMonoColor(it.getInt(SettingsTransfer.CARD_MONO_COLOR)) }

        step(SettingsTransfer.BG_IMAGE_ENABLED) { settings.setBackgroundImageEnabled(it.getBoolean(SettingsTransfer.BG_IMAGE_ENABLED)) }
        step(SettingsTransfer.BG_IMAGE_MODE) { settings.setBackgroundImageMode(it.getString(SettingsTransfer.BG_IMAGE_MODE)) }
        step(SettingsTransfer.BG_IMAGE_OPACITY) { settings.setBackgroundImageOpacity(it.getDouble(SettingsTransfer.BG_IMAGE_OPACITY).toFloat()) }

        step(SettingsTransfer.VIDEO_AUTOPLAY_DELAY_MS) { settings.setVideoAutoplayDelayMs(it.getLong(SettingsTransfer.VIDEO_AUTOPLAY_DELAY_MS)) }
        step(SettingsTransfer.VIDEO_MUTED) { settings.setVideoMuted(it.getBoolean(SettingsTransfer.VIDEO_MUTED)) }

        step(SettingsTransfer.SHOW_RECENTLY_PLAYED) { settings.setShowRecentlyPlayed(it.getBoolean(SettingsTransfer.SHOW_RECENTLY_PLAYED)) }
        step(SettingsTransfer.SHOW_FAVORITES) { settings.setShowFavorites(it.getBoolean(SettingsTransfer.SHOW_FAVORITES)) }
        step(SettingsTransfer.SHOW_RETRO_ACHIEVEMENTS) { settings.setShowRetroAchievements(it.getBoolean(SettingsTransfer.SHOW_RETRO_ACHIEVEMENTS)) }

        step(SettingsTransfer.DUAL_SCREEN_ENABLED) { settings.setDualScreenEnabled(it.getBoolean(SettingsTransfer.DUAL_SCREEN_ENABLED)) }
        step(SettingsTransfer.DUAL_SCREEN_SWAP) { settings.setDualScreenSwap(it.getBoolean(SettingsTransfer.DUAL_SCREEN_SWAP)) }
        step(SettingsTransfer.GAME_LAUNCH_ON_TOP) { settings.setGameLaunchOnTop(it.getBoolean(SettingsTransfer.GAME_LAUNCH_ON_TOP)) }
        step(SettingsTransfer.PERFORMANCE_MODE) { settings.setPerformanceMode(it.getBoolean(SettingsTransfer.PERFORMANCE_MODE)) }

        step(SettingsTransfer.PREFERRED_REGION) { settings.setPreferredRegion(it.getString(SettingsTransfer.PREFERRED_REGION)) }

        // Scrape options travel as a group: merge present keys onto the current config.
        if (SettingsTransfer.EXPORTED_KEYS.any { json.has(it) && it.startsWith("scrape_") }) {
            runCatching {
                val current = settings.scraperConfig.first()
                settings.updateScraperOptions(
                    scrapeMetadata = json.optBoolean(SettingsTransfer.SCRAPE_METADATA, current.scrapeMetadata),
                    scrapeBoxArt = json.optBoolean(SettingsTransfer.SCRAPE_BOX_ART, current.scrapeBoxArt),
                    scrapeScreenshots = json.optBoolean(SettingsTransfer.SCRAPE_SCREENSHOTS, current.scrapeScreenshots),
                    scrapeWheelLogos = json.optBoolean(SettingsTransfer.SCRAPE_WHEEL_LOGOS, current.scrapeWheelLogos),
                    scrapeVideos = json.optBoolean(SettingsTransfer.SCRAPE_VIDEOS, current.scrapeVideos),
                )
                applied++
            }
        }

        step(SettingsTransfer.GAME_SORT) { settings.setGameSort(GameSort.fromName(it.getString(SettingsTransfer.GAME_SORT))) }
        step(SettingsTransfer.SYSTEM_SORT) {
            val arr = it.getJSONArray(SettingsTransfer.SYSTEM_SORT)
            val keys = (0 until arr.length()).mapNotNull { i -> SystemSort.fromName(arr.getString(i)) }
            settings.setSystemSort(keys)
        }
        step(SettingsTransfer.MASTER_GAME_GRID_COLUMNS) { settings.setMasterGameGridColumns(it.getInt(SettingsTransfer.MASTER_GAME_GRID_COLUMNS)) }
        step(SettingsTransfer.HIDDEN_PLATFORMS) {
            val arr = it.getJSONArray(SettingsTransfer.HIDDEN_PLATFORMS)
            for (i in 0 until arr.length()) settings.setPlatformHidden(arr.getString(i), true)
        }
        step(SettingsTransfer.EMULATOR_UPDATE_NOTIFICATIONS) { settings.setEmulatorUpdateNotifications(it.getBoolean(SettingsTransfer.EMULATOR_UPDATE_NOTIFICATIONS)) }

        return applied
    }
}
