package com.gamelaunch.frontend.data.repository

import com.gamelaunch.frontend.data.preferences.AppDataStore
import com.gamelaunch.frontend.domain.model.GameSort
import com.gamelaunch.frontend.domain.model.ScraperConfig
import com.gamelaunch.frontend.domain.repository.SettingsRepository
import com.gamelaunch.frontend.ui.theme.LayoutMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import com.gamelaunch.frontend.domain.platform.SystemSort
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: AppDataStore
) : SettingsRepository {

    override val romRootPath: Flow<String> = dataStore.romRootPath
    override val mediaFolderPath: Flow<String> = dataStore.mediaFolderPath
    override val mediaStoragePath: Flow<String> = dataStore.mediaStoragePath

    override val layoutMode: Flow<LayoutMode> = dataStore.layoutMode.map { name ->
        runCatching { LayoutMode.valueOf(name) }.getOrDefault(LayoutMode.CAROUSEL)
    }

    override val scraperConfig: Flow<ScraperConfig> = combine(
        dataStore.ssId,
        dataStore.ssPassword,
        dataStore.preferredRegion,
        dataStore.scrapeMetadata,
        dataStore.scrapeBoxArt,
        dataStore.scrapeScreenshots,
        dataStore.scrapeWheelLogos,
        dataStore.scrapeVideos
    ) { values ->
        ScraperConfig(
            ssid = values[0] as String,
            sspassword = values[1] as String,
            preferredRegion = values[2] as String,
            scrapeMetadata = values[3] as Boolean,
            scrapeBoxArt = values[4] as Boolean,
            scrapeScreenshots = values[5] as Boolean,
            scrapeWheelLogos = values[6] as Boolean,
            scrapeVideos = values[7] as Boolean
        )
    }

    override val videoAutoplayDelayMs: Flow<Long> = dataStore.videoAutoplayDelayMs
    override val videoMuted: Flow<Boolean> = dataStore.videoMuted
    override val isFirstLaunch: Flow<Boolean> = dataStore.isFirstLaunch
    override val showRecentlyPlayed: Flow<Boolean> = dataStore.showRecentlyPlayed
    override val showRetroAchievements: Flow<Boolean> = dataStore.showRetroAchievements
    override val darkMode: Flow<Boolean> = dataStore.darkMode
    override val backgroundImageEnabled: Flow<Boolean> = dataStore.backgroundImageEnabled
    override val backgroundImagePath: Flow<String> = dataStore.backgroundImagePath
    override val backgroundImageMode: Flow<String> = dataStore.backgroundImageMode
    override val backgroundImageOpacity: Flow<Float> = dataStore.backgroundImageOpacity
    override val saveSyncEnabled: Flow<Boolean> = dataStore.saveSyncEnabled
    override val syncWifiOnly: Flow<Boolean> = dataStore.syncWifiOnly
    override val syncChargingOnly: Flow<Boolean> = dataStore.syncChargingOnly
    override val systemSort: Flow<List<SystemSort>> =
        dataStore.systemSort.map { names -> names.mapNotNull { SystemSort.fromName(it) } }
    override val gameSort: Flow<GameSort> = dataStore.gameSort.map { GameSort.fromName(it) }
    override val gameGridColumns: Flow<Int> = dataStore.gameGridColumns
    override val raUsername: Flow<String> = dataStore.raUsername
    override val raApiKey: Flow<String> = dataStore.raApiKey
    override val raToken: Flow<String> = dataStore.raToken
    override val raPoints: Flow<Int> = dataStore.raPoints
    override val raSoftcorePoints: Flow<Int> = dataStore.raSoftcorePoints
    override val hiddenPlatforms: Flow<Set<String>> = dataStore.hiddenPlatforms
    override val excludedPaths: Flow<Set<String>> = dataStore.excludedPaths

    override suspend fun setRomRootPath(path: String) { dataStore.setRomRootPath(path) }
    override suspend fun setMediaFolderPath(path: String) { dataStore.setMediaFolderPath(path) }
    override suspend fun setMediaStoragePath(path: String) { dataStore.setMediaStoragePath(path) }

    override suspend fun setLayoutMode(mode: LayoutMode) { dataStore.setLayoutMode(mode.name) }

    override suspend fun setScraperCredentials(ssid: String, sspassword: String) {
        dataStore.setSsCredentials(ssid, sspassword)
    }

    override suspend fun updateScraperOptions(
        scrapeMetadata: Boolean,
        scrapeBoxArt: Boolean,
        scrapeScreenshots: Boolean,
        scrapeWheelLogos: Boolean,
        scrapeVideos: Boolean
    ) {
        dataStore.setScrapeMetadata(scrapeMetadata)
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
    override suspend fun setShowRetroAchievements(enabled: Boolean) { dataStore.setShowRetroAchievements(enabled) }
    override suspend fun setDarkMode(enabled: Boolean) { dataStore.setDarkMode(enabled) }
    override suspend fun setBackgroundImageEnabled(enabled: Boolean) { dataStore.setBackgroundImageEnabled(enabled) }
    override suspend fun setBackgroundImagePath(path: String) { dataStore.setBackgroundImagePath(path) }
    override suspend fun setBackgroundImageMode(mode: String) { dataStore.setBackgroundImageMode(mode) }
    override suspend fun setBackgroundImageOpacity(opacity: Float) { dataStore.setBackgroundImageOpacity(opacity) }
    override suspend fun setSaveSyncEnabled(enabled: Boolean) { dataStore.setSaveSyncEnabled(enabled) }
    override suspend fun setSyncWifiOnly(v: Boolean) { dataStore.setSyncWifiOnly(v) }
    override suspend fun setSyncChargingOnly(v: Boolean) { dataStore.setSyncChargingOnly(v) }
    override suspend fun clearBackgroundImage() { dataStore.clearBackgroundImage() }
    override suspend fun setSystemSort(keys: List<SystemSort>) { dataStore.setSystemSort(keys.map { it.name }) }
    override suspend fun setGameSort(sort: GameSort) { dataStore.setGameSort(sort.name) }
    override suspend fun setGameGridColumns(columns: Int) { dataStore.setGameGridColumns(columns) }
    override suspend fun setRaApiKey(apiKey: String) { dataStore.setRaApiKey(apiKey) }
    override suspend fun setRaSession(username: String, token: String, points: Int, softcorePoints: Int) {
        dataStore.setRaSession(username, token, points, softcorePoints)
    }
    override suspend fun clearRaCredentials() { dataStore.clearRaCredentials() }
    override suspend fun setPlatformHidden(platformId: String, hidden: Boolean) { dataStore.setPlatformHidden(platformId, hidden) }
    override suspend fun addExcludedPath(romPath: String) { dataStore.addExcludedPath(romPath) }
}
