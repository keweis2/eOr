package com.gamelaunch.frontend.domain.repository

import com.gamelaunch.frontend.domain.model.GameSort
import com.gamelaunch.frontend.domain.model.ScraperConfig
import com.gamelaunch.frontend.domain.platform.SystemSort
import com.gamelaunch.frontend.ui.theme.LayoutMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val romRootPath: Flow<String>
    val mediaFolderPath: Flow<String>
    val mediaStoragePath: Flow<String>
    val layoutMode: Flow<LayoutMode>
    val scraperConfig: Flow<ScraperConfig>
    val videoAutoplayDelayMs: Flow<Long>
    val videoMuted: Flow<Boolean>
    val isFirstLaunch: Flow<Boolean>
    val showRecentlyPlayed: Flow<Boolean>
    val showRetroAchievements: Flow<Boolean>
    val darkMode: Flow<Boolean>
    val backgroundImageEnabled: Flow<Boolean>
    val backgroundImagePath: Flow<String>
    val backgroundImageMode: Flow<String>
    val backgroundImageOpacity: Flow<Float>
    val saveSyncEnabled: Flow<Boolean>
    val syncWifiOnly: Flow<Boolean>
    val syncChargingOnly: Flow<Boolean>
    val systemSort: Flow<List<SystemSort>>
    val gameSort: Flow<GameSort>
    val gameGridColumns: Flow<Int>
    val raUsername: Flow<String>
    val raApiKey: Flow<String>
    val raToken: Flow<String>
    val raPoints: Flow<Int>
    val raSoftcorePoints: Flow<Int>
    val hiddenPlatforms: Flow<Set<String>>
    val excludedPaths: Flow<Set<String>>

    suspend fun setRomRootPath(path: String)
    suspend fun setMediaFolderPath(path: String)
    suspend fun setMediaStoragePath(path: String)
    suspend fun setLayoutMode(mode: LayoutMode)
    suspend fun setScraperCredentials(ssid: String, sspassword: String)
    suspend fun updateScraperOptions(
        scrapeMetadata: Boolean,
        scrapeBoxArt: Boolean,
        scrapeScreenshots: Boolean,
        scrapeWheelLogos: Boolean,
        scrapeVideos: Boolean
    )
    suspend fun setPreferredRegion(region: String)
    suspend fun setVideoAutoplayDelayMs(ms: Long)
    suspend fun setVideoMuted(muted: Boolean)
    suspend fun setFirstLaunchComplete()
    suspend fun setShowRecentlyPlayed(enabled: Boolean)
    suspend fun setShowRetroAchievements(enabled: Boolean)
    suspend fun setDarkMode(enabled: Boolean)
    suspend fun setBackgroundImageEnabled(enabled: Boolean)
    suspend fun setBackgroundImagePath(path: String)
    suspend fun setBackgroundImageMode(mode: String)
    suspend fun setBackgroundImageOpacity(opacity: Float)
    suspend fun setSaveSyncEnabled(enabled: Boolean)
    suspend fun setSyncWifiOnly(v: Boolean)
    suspend fun setSyncChargingOnly(v: Boolean)
    suspend fun clearBackgroundImage()
    suspend fun setSystemSort(keys: List<SystemSort>)
    suspend fun setGameSort(sort: GameSort)
    suspend fun setGameGridColumns(columns: Int)
    suspend fun setRaApiKey(apiKey: String)
    suspend fun setRaSession(username: String, token: String, points: Int, softcorePoints: Int)
    suspend fun clearRaCredentials()
    suspend fun setPlatformHidden(platformId: String, hidden: Boolean)
    suspend fun addExcludedPath(romPath: String)
}
