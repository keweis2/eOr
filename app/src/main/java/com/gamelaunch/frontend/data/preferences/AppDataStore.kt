package com.gamelaunch.frontend.data.preferences

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

@Singleton
class AppDataStore @Inject constructor(@ApplicationContext private val context: Context) {

    data class LockedModeRecord(
        val pin: String,
        val active: Boolean
    )

    private object Keys {
        val ROM_ROOT_PATH = stringPreferencesKey("rom_root_path")
        val MEDIA_FOLDER_PATH = stringPreferencesKey("media_folder_path")
        val MEDIA_STORAGE_PATH = stringPreferencesKey("media_storage_path")
        val LAYOUT_MODE = stringPreferencesKey("layout_mode")
        val SS_ID = stringPreferencesKey("ss_id")
        val SS_PASSWORD = stringPreferencesKey("ss_password")
        val PREFERRED_REGION = stringPreferencesKey("preferred_region")
        val SCRAPE_METADATA = booleanPreferencesKey("scrape_metadata")
        val SCRAPE_BOX_ART = booleanPreferencesKey("scrape_box_art")
        val SCRAPE_SCREENSHOTS = booleanPreferencesKey("scrape_screenshots")
        val SCRAPE_WHEEL_LOGOS = booleanPreferencesKey("scrape_wheel_logos")
        val SCRAPE_VIDEOS = booleanPreferencesKey("scrape_videos")
        val VIDEO_AUTOPLAY_DELAY_MS = longPreferencesKey("video_autoplay_delay_ms")
        val VIDEO_MUTED = booleanPreferencesKey("video_muted")
        val FIRST_LAUNCH = booleanPreferencesKey("first_launch")
        val SHOW_RECENTLY_PLAYED = booleanPreferencesKey("show_recently_played")
        val SHOW_FAVORITES = booleanPreferencesKey("show_favorites")
        val SHOW_RETRO_ACHIEVEMENTS = booleanPreferencesKey("show_retro_achievements")
        // Which media the dual-screen top panel shows in game view: MARQUEE / SCREENSHOT / MIXIMAGE.
        val TOP_SCREEN_IMAGE = stringPreferencesKey("top_screen_image")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        // Dual-screen (Anbernic RG DS / AYN Thor): auto-detect a second display and split the UI.
        val DUAL_SCREEN_ENABLED = booleanPreferencesKey("dual_screen_enabled")
        // Manual override that flips the top/bottom (artwork/menu) assignment on unknown devices.
        val DUAL_SCREEN_SWAP = booleanPreferencesKey("dual_screen_swap")
        // Full build only: reduce animations + delay preview video for smoother browsing on weak chips.
        val PERFORMANCE_MODE = booleanPreferencesKey("performance_mode")
        // Dual-screen: launch single-screen games on the top panel (vs the default/bottom display).
        val GAME_LAUNCH_ON_TOP = booleanPreferencesKey("game_launch_on_top")
        val BG_IMAGE_ENABLED = booleanPreferencesKey("background_image_enabled")
        val BG_IMAGE_PATH = stringPreferencesKey("background_image_path")
        val BG_IMAGE_MODE = stringPreferencesKey("background_image_mode")
        val BG_IMAGE_OPACITY = floatPreferencesKey("background_image_opacity")
        val SAVE_SYNC_ENABLED = booleanPreferencesKey("save_sync_enabled")
        val SYNC_WIFI_ONLY = booleanPreferencesKey("sync_wifi_only")
        val SYNC_CHARGING_ONLY = booleanPreferencesKey("sync_charging_only")
        val SYSTEM_SORT = stringPreferencesKey("system_sort")
        val GAME_SORT = stringPreferencesKey("game_sort")
        val GAME_GRID_COLUMNS = intPreferencesKey("game_grid_columns")
        val RA_USERNAME = stringPreferencesKey("ra_username")
        val RA_API_KEY = stringPreferencesKey("ra_api_key")
        val RA_TOKEN = stringPreferencesKey("ra_token")
        val RA_POINTS = intPreferencesKey("ra_points")
        val RA_SOFTCORE_POINTS = intPreferencesKey("ra_softcore_points")
        // Friends (P2P social) — all local. Display name is generated once on first use if blank.
        val FRIENDS_ENABLED = booleanPreferencesKey("friends_enabled")
        val FRIEND_DISPLAY_NAME = stringPreferencesKey("friend_display_name")
        val FRIEND_SHARE_LAST_PLAYED = booleanPreferencesKey("friend_share_last_played")
        val FRIEND_SHARE_RA = booleanPreferencesKey("friend_share_ra")
        // Platform ids the user has chosen to hide from the home screen (e.g. "pc", "android").
        val HIDDEN_PLATFORMS = stringSetPreferencesKey("hidden_platforms")
        // rom_path identifiers the user removed from the library; scans skip these so they don't
        // come back. Android games use the synthetic path "package:<pkg>".
        val EXCLUDED_PATHS = stringSetPreferencesKey("excluded_paths")
        // Intentionally plaintext: Locked Mode simplifies the UI for kids; it is not a
        // security boundary. A kid extracting it via ADB may bypass it (me proud!).
        // If stronger security is ever needed, harden it here.
        val LOCKED_MODE_PIN = stringPreferencesKey("locked_mode_pin")
        val LOCKED_MODE_ACTIVE = booleanPreferencesKey("locked_mode_active")
        // Small allowlist, if more info about apps is needed, move to database
        val LOCKED_MODE_ALLOWED_APP_PACKAGES = stringSetPreferencesKey("locked_mode_allowed_app_packages")
    }

    val romRootPath: Flow<String> = context.dataStore.data.map { it[Keys.ROM_ROOT_PATH] ?: "" }
    val mediaFolderPath: Flow<String> = context.dataStore.data.map { it[Keys.MEDIA_FOLDER_PATH] ?: "" }
    // Where scraped media is saved. Empty = app's internal default folder.
    val mediaStoragePath: Flow<String> = context.dataStore.data.map { it[Keys.MEDIA_STORAGE_PATH] ?: "" }
    val layoutMode: Flow<String> = context.dataStore.data.map { it[Keys.LAYOUT_MODE] ?: "CAROUSEL" }
    val ssId: Flow<String> = context.dataStore.data.map { it[Keys.SS_ID] ?: "" }
    val ssPassword: Flow<String> = context.dataStore.data.map { it[Keys.SS_PASSWORD] ?: "" }
    val preferredRegion: Flow<String> = context.dataStore.data.map { it[Keys.PREFERRED_REGION] ?: "us" }
    val scrapeMetadata: Flow<Boolean> = context.dataStore.data.map { it[Keys.SCRAPE_METADATA] ?: true }
    val scrapeBoxArt: Flow<Boolean> = context.dataStore.data.map { it[Keys.SCRAPE_BOX_ART] ?: true }
    val scrapeScreenshots: Flow<Boolean> = context.dataStore.data.map { it[Keys.SCRAPE_SCREENSHOTS] ?: true }
    val scrapeWheelLogos: Flow<Boolean> = context.dataStore.data.map { it[Keys.SCRAPE_WHEEL_LOGOS] ?: true }
    val scrapeVideos: Flow<Boolean> = context.dataStore.data.map { it[Keys.SCRAPE_VIDEOS] ?: true }
    val videoAutoplayDelayMs: Flow<Long> = context.dataStore.data.map { it[Keys.VIDEO_AUTOPLAY_DELAY_MS] ?: 1500L }
    val videoMuted: Flow<Boolean> = context.dataStore.data.map { it[Keys.VIDEO_MUTED] ?: true }
    val isFirstLaunch: Flow<Boolean> = context.dataStore.data.map { it[Keys.FIRST_LAUNCH] ?: true }
    val showRecentlyPlayed: Flow<Boolean> = context.dataStore.data.map { it[Keys.SHOW_RECENTLY_PLAYED] ?: true }
    val showFavorites: Flow<Boolean> = context.dataStore.data.map { it[Keys.SHOW_FAVORITES] ?: true }
    val showRetroAchievements: Flow<Boolean> = context.dataStore.data.map { it[Keys.SHOW_RETRO_ACHIEVEMENTS] ?: true }
    val topScreenImage: Flow<String> = context.dataStore.data.map { it[Keys.TOP_SCREEN_IMAGE] ?: "MARQUEE" }
    val darkMode: Flow<Boolean> = context.dataStore.data.map { it[Keys.DARK_MODE] ?: false }
    // Dual-screen is on by default: it only ever activates when a second display is actually present.
    val dualScreenEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.DUAL_SCREEN_ENABLED] ?: true }
    val dualScreenSwap: Flow<Boolean> = context.dataStore.data.map { it[Keys.DUAL_SCREEN_SWAP] ?: false }
    val performanceMode: Flow<Boolean> = context.dataStore.data.map { it[Keys.PERFORMANCE_MODE] ?: false }
    // Default true: in dual-screen mode single-screen games open on the top panel.
    val gameLaunchOnTop: Flow<Boolean> = context.dataStore.data.map { it[Keys.GAME_LAUNCH_ON_TOP] ?: true }
    // Optional user-supplied branded background. Path points at the processed single-colour
    // mask PNG in filesDir; mode is FILL (one full-width silhouette) or TILE (repeating pattern).
    val backgroundImageEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.BG_IMAGE_ENABLED] ?: false }
    val backgroundImagePath: Flow<String> = context.dataStore.data.map { it[Keys.BG_IMAGE_PATH] ?: "" }
    val backgroundImageMode: Flow<String> = context.dataStore.data.map { it[Keys.BG_IMAGE_MODE] ?: "FILL" }
    val backgroundImageOpacity: Flow<Float> = context.dataStore.data.map { it[Keys.BG_IMAGE_OPACITY] ?: 0.15f }
    val saveSyncEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.SAVE_SYNC_ENABLED] ?: false }
    val syncWifiOnly: Flow<Boolean> = context.dataStore.data.map { it[Keys.SYNC_WIFI_ONLY] ?: false }
    val syncChargingOnly: Flow<Boolean> = context.dataStore.data.map { it[Keys.SYNC_CHARGING_ONLY] ?: false }
    // Up to two sort keys, comma-joined (e.g. "RELEASE_DATE,BRAND"). Empty = default order.
    val systemSort: Flow<List<String>> = context.dataStore.data.map {
        it[Keys.SYSTEM_SORT]?.split(",")?.filter { s -> s.isNotBlank() } ?: emptyList()
    }
    // Game-grid view options (set from the Select-button quick menu on the game grid).
    val gameSort: Flow<String> = context.dataStore.data.map { it[Keys.GAME_SORT] ?: "ALPHABETICAL" }
    // 0 = auto-fit columns to screen; > 0 = user-chosen fixed column count.
    val gameGridColumns: Flow<Int> = context.dataStore.data.map { it[Keys.GAME_GRID_COLUMNS] ?: 0 }
    val raUsername: Flow<String> = context.dataStore.data.map { it[Keys.RA_USERNAME] ?: "" }
    val raApiKey: Flow<String> = context.dataStore.data.map { it[Keys.RA_API_KEY] ?: "" }
    val raToken: Flow<String> = context.dataStore.data.map { it[Keys.RA_TOKEN] ?: "" }
    val raPoints: Flow<Int> = context.dataStore.data.map { it[Keys.RA_POINTS] ?: 0 }
    val raSoftcorePoints: Flow<Int> = context.dataStore.data.map { it[Keys.RA_SOFTCORE_POINTS] ?: 0 }
    // Friends feature is opt-in (off by default): enabling it starts the P2P sync engine.
    val friendsEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.FRIENDS_ENABLED] ?: false }
    // Empty until first generated (see FriendRepository); never blank once the feature is used.
    val friendDisplayName: Flow<String> = context.dataStore.data.map { it[Keys.FRIEND_DISPLAY_NAME] ?: "" }
    val friendShareLastPlayed: Flow<Boolean> = context.dataStore.data.map { it[Keys.FRIEND_SHARE_LAST_PLAYED] ?: true }
    val friendShareRa: Flow<Boolean> = context.dataStore.data.map { it[Keys.FRIEND_SHARE_RA] ?: true }
    val hiddenPlatforms: Flow<Set<String>> = context.dataStore.data.map { it[Keys.HIDDEN_PLATFORMS] ?: emptySet() }
    val excludedPaths: Flow<Set<String>> = context.dataStore.data.map { it[Keys.EXCLUDED_PATHS] ?: emptySet() }
    val lockedMode: Flow<LockedModeRecord> = context.dataStore.data.map {
        LockedModeRecord(
            pin = it[Keys.LOCKED_MODE_PIN] ?: "",
            active = it[Keys.LOCKED_MODE_ACTIVE] ?: false
        )
    }
    val lockedModeAllowedAppPackages: Flow<Set<String>> = context.dataStore.data.map {
        it[Keys.LOCKED_MODE_ALLOWED_APP_PACKAGES] ?: emptySet()
    }

    suspend fun setRomRootPath(path: String) = context.dataStore.edit { it[Keys.ROM_ROOT_PATH] = path }
    suspend fun setMediaFolderPath(path: String) = context.dataStore.edit { it[Keys.MEDIA_FOLDER_PATH] = path }
    suspend fun setMediaStoragePath(path: String) = context.dataStore.edit { it[Keys.MEDIA_STORAGE_PATH] = path }
    suspend fun setLayoutMode(mode: String) = context.dataStore.edit { it[Keys.LAYOUT_MODE] = mode }
    suspend fun setSsCredentials(id: String, password: String) = context.dataStore.edit {
        it[Keys.SS_ID] = id
        it[Keys.SS_PASSWORD] = password
    }
    suspend fun setPreferredRegion(region: String) = context.dataStore.edit { it[Keys.PREFERRED_REGION] = region }
    suspend fun setScrapeMetadata(enabled: Boolean) = context.dataStore.edit { it[Keys.SCRAPE_METADATA] = enabled }
    suspend fun setScrapeBoxArt(enabled: Boolean) = context.dataStore.edit { it[Keys.SCRAPE_BOX_ART] = enabled }
    suspend fun setScrapeScreenshots(enabled: Boolean) = context.dataStore.edit { it[Keys.SCRAPE_SCREENSHOTS] = enabled }
    suspend fun setScrapeWheelLogos(enabled: Boolean) = context.dataStore.edit { it[Keys.SCRAPE_WHEEL_LOGOS] = enabled }
    suspend fun setScrapeVideos(enabled: Boolean) = context.dataStore.edit { it[Keys.SCRAPE_VIDEOS] = enabled }
    suspend fun setVideoAutoplayDelayMs(ms: Long) = context.dataStore.edit { it[Keys.VIDEO_AUTOPLAY_DELAY_MS] = ms }
    suspend fun setVideoMuted(muted: Boolean) = context.dataStore.edit { it[Keys.VIDEO_MUTED] = muted }
    suspend fun setFirstLaunchComplete() = context.dataStore.edit { it[Keys.FIRST_LAUNCH] = false }
    suspend fun setShowRecentlyPlayed(enabled: Boolean) = context.dataStore.edit { it[Keys.SHOW_RECENTLY_PLAYED] = enabled }
    suspend fun setShowFavorites(enabled: Boolean) = context.dataStore.edit { it[Keys.SHOW_FAVORITES] = enabled }
    suspend fun setShowRetroAchievements(enabled: Boolean) = context.dataStore.edit { it[Keys.SHOW_RETRO_ACHIEVEMENTS] = enabled }
    suspend fun setTopScreenImage(mode: String) = context.dataStore.edit { it[Keys.TOP_SCREEN_IMAGE] = mode }
    suspend fun setDarkMode(enabled: Boolean) = context.dataStore.edit { it[Keys.DARK_MODE] = enabled }
    suspend fun setDualScreenEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.DUAL_SCREEN_ENABLED] = enabled }
    suspend fun setDualScreenSwap(swap: Boolean) = context.dataStore.edit { it[Keys.DUAL_SCREEN_SWAP] = swap }
    suspend fun setPerformanceMode(enabled: Boolean) = context.dataStore.edit { it[Keys.PERFORMANCE_MODE] = enabled }
    suspend fun setGameLaunchOnTop(enabled: Boolean) = context.dataStore.edit { it[Keys.GAME_LAUNCH_ON_TOP] = enabled }
    suspend fun setBackgroundImageEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.BG_IMAGE_ENABLED] = enabled }
    suspend fun setBackgroundImagePath(path: String) = context.dataStore.edit { it[Keys.BG_IMAGE_PATH] = path }
    suspend fun setBackgroundImageMode(mode: String) = context.dataStore.edit { it[Keys.BG_IMAGE_MODE] = mode }
    suspend fun setBackgroundImageOpacity(opacity: Float) = context.dataStore.edit { it[Keys.BG_IMAGE_OPACITY] = opacity }
    suspend fun setSaveSyncEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.SAVE_SYNC_ENABLED] = enabled }
    suspend fun setSyncWifiOnly(v: Boolean) = context.dataStore.edit { it[Keys.SYNC_WIFI_ONLY] = v }
    suspend fun setSyncChargingOnly(v: Boolean) = context.dataStore.edit { it[Keys.SYNC_CHARGING_ONLY] = v }
    // Drop only the user's image (revert to the default silhouette); the enabled flag is controlled
    // independently by its own toggle.
    suspend fun clearBackgroundImage() = context.dataStore.edit {
        it.remove(Keys.BG_IMAGE_PATH)
    }
    suspend fun setSystemSort(keys: List<String>) = context.dataStore.edit { it[Keys.SYSTEM_SORT] = keys.joinToString(",") }
    suspend fun setGameSort(sort: String) = context.dataStore.edit { it[Keys.GAME_SORT] = sort }
    suspend fun setGameGridColumns(columns: Int) = context.dataStore.edit { it[Keys.GAME_GRID_COLUMNS] = columns }
    suspend fun setRaApiKey(apiKey: String) = context.dataStore.edit {
        it[Keys.RA_API_KEY] = apiKey
    }
    suspend fun setRaSession(username: String, token: String, points: Int, softcorePoints: Int) = context.dataStore.edit {
        it[Keys.RA_USERNAME] = username
        it[Keys.RA_TOKEN] = token
        it[Keys.RA_POINTS] = points
        it[Keys.RA_SOFTCORE_POINTS] = softcorePoints
    }
    suspend fun clearRaCredentials() = context.dataStore.edit {
        it.remove(Keys.RA_USERNAME)
        it.remove(Keys.RA_API_KEY)
        it.remove(Keys.RA_TOKEN)
        it.remove(Keys.RA_POINTS)
        it.remove(Keys.RA_SOFTCORE_POINTS)
    }

    suspend fun setFriendsEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.FRIENDS_ENABLED] = enabled }
    suspend fun setFriendDisplayName(name: String) = context.dataStore.edit { it[Keys.FRIEND_DISPLAY_NAME] = name }
    suspend fun setFriendShareLastPlayed(enabled: Boolean) = context.dataStore.edit { it[Keys.FRIEND_SHARE_LAST_PLAYED] = enabled }
    suspend fun setFriendShareRa(enabled: Boolean) = context.dataStore.edit { it[Keys.FRIEND_SHARE_RA] = enabled }

    suspend fun setPlatformHidden(platformId: String, hidden: Boolean) = context.dataStore.edit {
        val current = it[Keys.HIDDEN_PLATFORMS] ?: emptySet()
        it[Keys.HIDDEN_PLATFORMS] = if (hidden) current + platformId else current - platformId
    }

    suspend fun addExcludedPath(romPath: String) = context.dataStore.edit {
        it[Keys.EXCLUDED_PATHS] = (it[Keys.EXCLUDED_PATHS] ?: emptySet()) + romPath
    }

    suspend fun configureLockedMode(pin: String) = context.dataStore.edit {
        it[Keys.LOCKED_MODE_PIN] = pin
        it[Keys.LOCKED_MODE_ACTIVE] = false
    }

    suspend fun setLockedModeActive(active: Boolean) = context.dataStore.edit {
        val configured = !it[Keys.LOCKED_MODE_PIN].isNullOrBlank()
        if (active && !configured) {
            Log.w(TAG, "Ignoring Locked Mode activation because no PIN is configured")
        }
        it[Keys.LOCKED_MODE_ACTIVE] = active && configured
    }

    suspend fun clearLockedMode() = context.dataStore.edit {
        it.remove(Keys.LOCKED_MODE_PIN)
        it.remove(Keys.LOCKED_MODE_ACTIVE)
    }

    suspend fun setLockedModeAppAllowed(packageName: String, allowed: Boolean) = context.dataStore.edit {
        val current = it[Keys.LOCKED_MODE_ALLOWED_APP_PACKAGES] ?: emptySet()
        it[Keys.LOCKED_MODE_ALLOWED_APP_PACKAGES] = if (allowed) current + packageName else current - packageName
    }

    suspend fun clearLockedModeAllowedApps() = context.dataStore.edit {
        it[Keys.LOCKED_MODE_ALLOWED_APP_PACKAGES] = emptySet()
    }

    private companion object {
        const val TAG = "AppDataStore"
    }
}
