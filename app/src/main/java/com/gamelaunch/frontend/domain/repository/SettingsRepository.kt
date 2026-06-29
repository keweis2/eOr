package com.gamelaunch.frontend.domain.repository

import com.gamelaunch.frontend.domain.model.ScraperConfig
import com.gamelaunch.frontend.ui.theme.LayoutMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val romRootPath: Flow<String>
    val mediaFolderPath: Flow<String>
    val layoutMode: Flow<LayoutMode>
    val scraperConfig: Flow<ScraperConfig>
    val videoAutoplayDelayMs: Flow<Long>
    val videoMuted: Flow<Boolean>
    val isFirstLaunch: Flow<Boolean>
    val showRecentlyPlayed: Flow<Boolean>
    val darkMode: Flow<Boolean>
    val raUsername: Flow<String>
    val raApiKey: Flow<String>
    val raToken: Flow<String>
    val raPoints: Flow<Int>
    val raSoftcorePoints: Flow<Int>

    suspend fun setRomRootPath(path: String)
    suspend fun setMediaFolderPath(path: String)
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
    suspend fun setDarkMode(enabled: Boolean)
    suspend fun setRaApiKey(apiKey: String)
    suspend fun setRaSession(username: String, token: String, points: Int, softcorePoints: Int)
    suspend fun clearRaCredentials()
}
