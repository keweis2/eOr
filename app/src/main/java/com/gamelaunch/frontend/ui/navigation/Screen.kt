package com.gamelaunch.frontend.ui.navigation

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Home : Screen("home")
    object Scan : Screen("scan")

    /** Nested graph wrapping the settings index + its drill-in category screens. Navigating here
     * enters [SettingsIndex] (the graph's start). The graph scopes a single shared SettingsViewModel
     * so credentials typed on any category screen survive the first-launch "Library" finish. */
    object Settings : Screen("settings_graph")
    object SettingsIndex : Screen("settings_index")
    // Category routes — kept in sync with SettingsCategory.route in the settings package.
    object SettingsAppearance : Screen("settings_appearance")
    object SettingsHomeLayout : Screen("settings_home_layout")
    object SettingsGames : Screen("settings_games")
    object SettingsMedia : Screen("settings_media")
    object SettingsRetroAchievements : Screen("settings_retro_achievements")
    object SettingsSaveSync : Screen("settings_save_sync")
    object SettingsFriends : Screen("settings_friends")
    object SettingsLocked : Screen("settings_locked")

    object LockedModeGames : Screen("locked_mode_games")
    object LockedModeApps : Screen("locked_mode_apps")
    object EmulatorConfig : Screen("emulator_config")
    object About : Screen("about")

    object ScrapeProgress : Screen("scrape_progress?platformId={platformId}") {
        const val ARG_PLATFORM_ID = "platformId"
        /** No platformId scrapes the whole library; a platformId scrapes just that system. */
        fun route(platformId: String? = null) =
            if (platformId == null) "scrape_progress" else "scrape_progress?platformId=$platformId"
    }

    object GameDetail : Screen("game_detail/{gameId}") {
        const val ARG_GAME_ID = "gameId"
        fun route(gameId: Long) = "game_detail/$gameId"
    }
}
