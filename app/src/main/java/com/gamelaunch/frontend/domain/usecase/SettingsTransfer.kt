package com.gamelaunch.frontend.domain.usecase

/**
 * Shared contract for exporting/importing app settings over Web Transfer.
 *
 * [EXPORTED_KEYS] is the exact, deliberate whitelist of settings we move between devices — appearance,
 * layout, library paths, scraper *options* and sort preferences. [SENSITIVE_KEYS] enumerates values
 * that must NEVER leave the device (credentials, tokens, the Locked-Mode PIN) or that are
 * device-specific / would auto-start a network service. The two sets are asserted disjoint by a unit
 * test, so the export can never accidentally include a secret.
 */
object SettingsTransfer {
    const val FORMAT = "eor-settings"
    const val VERSION = 1

    // JSON keys (reuse the DataStore names for clarity).
    const val ROM_ROOT_PATH = "rom_root_path"
    const val BIOS_FOLDER_PATH = "bios_folder_path"
    const val MEDIA_STORAGE_PATH = "media_storage_path"
    const val STEAM_LIBRARY_PATH = "steam_library_path"
    const val LAYOUT_MODE = "layout_mode"
    const val DARK_MODE = "dark_mode"
    const val CARD_COLOR_SCHEME = "card_color_scheme"
    const val CARD_MONO_COLOR = "card_mono_color"
    const val BG_IMAGE_ENABLED = "background_image_enabled"
    const val BG_IMAGE_MODE = "background_image_mode"
    const val BG_IMAGE_OPACITY = "background_image_opacity"
    const val VIDEO_AUTOPLAY_DELAY_MS = "video_autoplay_delay_ms"
    const val VIDEO_MUTED = "video_muted"
    const val SHOW_RECENTLY_PLAYED = "show_recently_played"
    const val SHOW_FAVORITES = "show_favorites"
    const val SHOW_RETRO_ACHIEVEMENTS = "show_retro_achievements"
    const val DUAL_SCREEN_ENABLED = "dual_screen_enabled"
    const val DUAL_SCREEN_SWAP = "dual_screen_swap"
    const val GAME_LAUNCH_ON_TOP = "game_launch_on_top"
    const val PERFORMANCE_MODE = "performance_mode"
    const val PREFERRED_REGION = "preferred_region"
    const val SCRAPE_METADATA = "scrape_metadata"
    const val SCRAPE_BOX_ART = "scrape_box_art"
    const val SCRAPE_SCREENSHOTS = "scrape_screenshots"
    const val SCRAPE_WHEEL_LOGOS = "scrape_wheel_logos"
    const val SCRAPE_VIDEOS = "scrape_videos"
    const val GAME_SORT = "game_sort"
    const val SYSTEM_SORT = "system_sort"
    const val MASTER_GAME_GRID_COLUMNS = "master_game_grid_columns"
    const val HIDDEN_PLATFORMS = "hidden_platforms"
    const val EMULATOR_UPDATE_NOTIFICATIONS = "emulator_update_notifications"

    val EXPORTED_KEYS: Set<String> = linkedSetOf(
        ROM_ROOT_PATH, BIOS_FOLDER_PATH, MEDIA_STORAGE_PATH, STEAM_LIBRARY_PATH,
        LAYOUT_MODE, DARK_MODE, CARD_COLOR_SCHEME, CARD_MONO_COLOR,
        BG_IMAGE_ENABLED, BG_IMAGE_MODE, BG_IMAGE_OPACITY,
        VIDEO_AUTOPLAY_DELAY_MS, VIDEO_MUTED,
        SHOW_RECENTLY_PLAYED, SHOW_FAVORITES, SHOW_RETRO_ACHIEVEMENTS,
        DUAL_SCREEN_ENABLED, DUAL_SCREEN_SWAP, GAME_LAUNCH_ON_TOP, PERFORMANCE_MODE,
        PREFERRED_REGION, SCRAPE_METADATA, SCRAPE_BOX_ART, SCRAPE_SCREENSHOTS,
        SCRAPE_WHEEL_LOGOS, SCRAPE_VIDEOS,
        GAME_SORT, SYSTEM_SORT, MASTER_GAME_GRID_COLUMNS, HIDDEN_PLATFORMS,
        EMULATOR_UPDATE_NOTIFICATIONS,
    )

    /**
     * Never exported: credentials/secrets, the Locked-Mode PIN, device-specific paths, and flags that
     * would silently start a network service on the receiving device.
     */
    val SENSITIVE_KEYS: Set<String> = setOf(
        "ss_id", "ss_password",
        "ra_username", "ra_api_key", "ra_token", "ra_points", "ra_softcore_points",
        "locked_mode_pin", "locked_mode_enabled", "locked_mode_active",
        "locked_mode_allowed_app_packages",
        "background_image_path",
        "save_sync_enabled", "friends_enabled", "friend_display_name",
        "web_transfer_enabled", "web_transfer_port",
    )
}
