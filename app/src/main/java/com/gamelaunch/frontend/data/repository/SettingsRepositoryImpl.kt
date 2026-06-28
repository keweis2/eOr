package com.gamelaunch.frontend.data.repository

import com.gamelaunch.frontend.data.preferences.AppDataStore
import com.gamelaunch.frontend.domain.model.ScraperConfig
import com.gamelaunch.frontend.domain.repository.SettingsRepository
import com.gamelaunch.frontend.ui.theme.LayoutMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: AppDataStore
) : SettingsRepository {

    override val romRootPath: Flow<String> = dataStore.romRootPath
    override val mediaFolderPath: Flow<String> = dataStore.mediaFolderPath

    override val layoutMode: Flow<LayoutMode> = dataStore.layoutMode.map { name ->
        runCatching { LayoutMode.valueOf(name) }.getOrDefault(LayoutMode.CAROUSEL)
    }

    override val scraperConfig: Flow<ScraperConfig> = combine(
        dataStore.ssId,
        dataStore.ssPassword,
        dataStore.preferredRegion,
        dataStore.scrapeBoxArt,
        dataStore.scrapeScreenshots,
        dataStore.scrapeWheelLogos,
        dataStore.scrapeVideos
    ) { values ->
        ScraperConfig(
            ssid = values[0] as String,
            sspassword = values[1] as String,
            preferredRegion = values[2] as String,
            scrapeBoxArt = values[3] as Boolean,
            scrapeScreenshots = values[4] as Boolean,
            scrapeWheelLogos = values[5] as Boolean,
            scrapeVideos = values[6] as Boolean
        )
    }

    override val videoAutoplayDelayMs: Flow<Long> = dataStore.videoAutoplayDelayMs
    override val videoMuted: Flow<Boolean> = dataStore.videoMuted
    override val isFirstLaunch: Flow<Boolean> = dataStore.isFirstLaunch
    override val showRecentlyPlayed: Flow<Boolean> = dataStore.showRecentlyPlayed

    override suspend fun setRomRootPath(path: String) { dataStore.setRomRootPath(path) }
    override suspend fun setMediaFolderPath(path: String) { dataStore.setMediaFolderPath(path) }

    override suspend fun setLayoutMode(mode: LayoutMode) { dataStore.setLayoutMode(mode.name) }

    override suspend fun setScraperCredentials(ssid: String, sspassword: String) {
        dataStore.setSsCredentials(ssid, sspassword)
    }

    override suspend fun updateScraperOptions(
        scrapeBoxArt: Boolean,
        scrapeScreenshots: Boolean,
        scrapeWheelLogos: Boolean,
        scrapeVideos: Boolean
    ) {
        dataStore.setScrapeBoxArt(scrapeBoxArt)
        dataStore.setScrapeScreenshots(scrapeScreenshots)
        dataStore.setScrapeWheelLogos(scrapeWheelLogos)
        dataStore.setScrapeVideos(scrapeVideos)
    }

    override suspend fun setPreferredRegion(region: String) { dataStore.setPreferredRegion(region) }
    override suspend fun setVideoAutoplayDelayMs(ms: Long) { dataStore.setVideoAutoplayDelayMs(ms) }
    override suspend fun setVideoMuted(muted: Boolean) { dataStore.setVideoMuted(muted) }
    override suspend fun setFirstLaunchComplete() { dataStore.setFirstLaunchComplete() }
    override suspend fun setShowRecentlyPlayed(enabled: Boolean) { dataStore.setShowRecentlyPlayed(enabled) }
}
