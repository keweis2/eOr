package com.gamelaunch.frontend.ui.navigation

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Home : Screen("home")
    object Scan : Screen("scan")
    object Settings : Screen("settings")
    object EmulatorConfig : Screen("emulator_config")
    object ScrapeProgress : Screen("scrape_progress")
    object About : Screen("about")

    object GameDetail : Screen("game_detail/{gameId}") {
        const val ARG_GAME_ID = "gameId"
        fun route(gameId: Long) = "game_detail/$gameId"
    }
}
