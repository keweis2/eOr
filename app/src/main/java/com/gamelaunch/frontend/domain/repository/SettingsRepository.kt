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

    suspend fun setRomRootPath(path: String)
    suspend fun setMediaFolderPath(path: String)
    suspend fun setLayoutMode(mode: LayoutMode)
    suspend fun setScraperCredentials(ssid: String, sspassword: String)
    suspend fun updateScraperOptions(
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
}
