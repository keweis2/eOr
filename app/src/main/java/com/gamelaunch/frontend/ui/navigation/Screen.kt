package com.gamelaunch.frontend.ui.navigation

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Home : Screen("home")
    object Scan : Screen("scan")
    object Settings : Screen("settings")
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
