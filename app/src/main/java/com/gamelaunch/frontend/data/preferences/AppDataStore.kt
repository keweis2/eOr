package com.gamelaunch.frontend.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

@Singleton
class AppDataStore @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val ROM_ROOT_PATH = stringPreferencesKey("rom_root_path")
        val MEDIA_FOLDER_PATH = stringPreferencesKey("media_folder_path")
        val LAYOUT_MODE = stringPreferencesKey("layout_mode")
        val SS_ID = stringPreferencesKey("ss_id")
        val SS_PASSWORD = stringPreferencesKey("ss_password")
        val PREFERRED_REGION = stringPreferencesKey("preferred_region")
        val SCRAPE_BOX_ART = booleanPreferencesKey("scrape_box_art")
        val SCRAPE_SCREENSHOTS = booleanPreferencesKey("scrape_screenshots")
        val SCRAPE_WHEEL_LOGOS = booleanPreferencesKey("scrape_wheel_logos")
        val SCRAPE_VIDEOS = booleanPreferencesKey("scrape_videos")
        val VIDEO_AUTOPLAY_DELAY_MS = longPreferencesKey("video_autoplay_delay_ms")
        val VIDEO_MUTED = booleanPreferencesKey("video_muted")
        val FIRST_LAUNCH = booleanPreferencesKey("first_launch")
    }

    val romRootPath: Flow<String> = context.dataStore.data.map { it[Keys.ROM_ROOT_PATH] ?: "" }
    val mediaFolderPath: Flow<String> = context.dataStore.data.map { it[Keys.MEDIA_FOLDER_PATH] ?: "" }
    val layoutMode: Flow<String> = context.dataStore.data.map { it[Keys.LAYOUT_MODE] ?: "CAROUSEL" }
    val ssId: Flow<String> = context.dataStore.data.map { it[Keys.SS_ID] ?: "" }
    val ssPassword: Flow<String> = context.dataStore.data.map { it[Keys.SS_PASSWORD] ?: "" }
    val preferredRegion: Flow<String> = context.dataStore.data.map { it[Keys.PREFERRED_REGION] ?: "us" }
    val scrapeBoxArt: Flow<Boolean> = context.dataStore.data.map { it[Keys.SCRAPE_BOX_ART] ?: true }
    val scrapeScreenshots: Flow<Boolean> = context.dataStore.data.map { it[Keys.SCRAPE_SCREENSHOTS] ?: true }
    val scrapeWheelLogos: Flow<Boolean> = context.dataStore.data.map { it[Keys.SCRAPE_WHEEL_LOGOS] ?: true }
    val scrapeVideos: Flow<Boolean> = context.dataStore.data.map { it[Keys.SCRAPE_VIDEOS] ?: true }
    val videoAutoplayDelayMs: Flow<Long> = context.dataStore.data.map { it[Keys.VIDEO_AUTOPLAY_DELAY_MS] ?: 1500L }
    val videoMuted: Flow<Boolean> = context.dataStore.data.map { it[Keys.VIDEO_MUTED] ?: true }
    val isFirstLaunch: Flow<Boolean> = context.dataStore.data.map { it[Keys.FIRST_LAUNCH] ?: true }

    suspend fun setRomRootPath(path: String) = context.dataStore.edit { it[Keys.ROM_ROOT_PATH] = path }
    suspend fun setMediaFolderPath(path: String) = context.dataStore.edit { it[Keys.MEDIA_FOLDER_PATH] = path }
    suspend fun setLayoutMode(mode: String) = context.dataStore.edit { it[Keys.LAYOUT_MODE] = mode }
    suspend fun setSsCredentials(id: String, password: String) = context.dataStore.edit {
        it[Keys.SS_ID] = id
        it[Keys.SS_PASSWORD] = password
    }
    suspend fun setPreferredRegion(region: String) = context.dataStore.edit { it[Keys.PREFERRED_REGION] = region }
    suspend fun setScrapeBoxArt(enabled: Boolean) = context.dataStore.edit { it[Keys.SCRAPE_BOX_ART] = enabled }
    suspend fun setScrapeScreenshots(enabled: Boolean) = context.dataStore.edit { it[Keys.SCRAPE_SCREENSHOTS] = enabled }
    suspend fun setScrapeWheelLogos(enabled: Boolean) = context.dataStore.edit { it[Keys.SCRAPE_WHEEL_LOGOS] = enabled }
    suspend fun setScrapeVideos(enabled: Boolean) = context.dataStore.edit { it[Keys.SCRAPE_VIDEOS] = enabled }
    suspend fun setVideoAutoplayDelayMs(ms: Long) = context.dataStore.edit { it[Keys.VIDEO_AUTOPLAY_DELAY_MS] = ms }
    suspend fun setVideoMuted(muted: Boolean) = context.dataStore.edit { it[Keys.VIDEO_MUTED] = muted }
    suspend fun setFirstLaunchComplete() = context.dataStore.edit { it[Keys.FIRST_LAUNCH] = false }
}
