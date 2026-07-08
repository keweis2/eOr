package com.gamelaunch.frontend.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.gamelaunch.frontend.ui.screen.detail.GameDetailScreen
import com.gamelaunch.frontend.ui.screen.home.HomeScreen
import com.gamelaunch.frontend.ui.screen.onboarding.OnboardingScreen
import com.gamelaunch.frontend.ui.screen.scan.ScanScreen
import com.gamelaunch.frontend.ui.screen.scrape.ScrapeProgressScreen
import com.gamelaunch.frontend.ui.screen.settings.EmulatorConfigScreen
import com.gamelaunch.frontend.ui.screen.settings.SettingsScreen

/**
 * Go back one screen, or fall back to Home when there's nothing to pop.
 *
 * MainActivity is a `singleTask` launcher activity, so when eOr is resumed on a sub-screen (e.g. the
 * game detail page) via a launcher intent — after launching a game, or pressing Home and reopening —
 * the nav back stack can come back with that screen as the only entry. Then a plain `popBackStack()`
 * is a no-op and every back affordance (touch arrow, gamepad B, and system Back, which the Retroid's
 * B maps to) dead-ends, trapping the user. Routing to Home in that case guarantees an escape.
 */
fun NavController.backOrHome() {
    if (popBackStack()) return
    if (currentDestination?.route != Screen.Home.route) {
        navigate(Screen.Home.route) { popUpTo(0) { inclusive = true } }
    }
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: String
) {
    NavHost(navController = navController, startDestination = startDestination) {

        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onFinished = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Scan.route) {
            ScanScreen(
                onScanComplete = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Scan.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onGameClick = { gameId ->
                    navController.navigate(Screen.GameDetail.route(gameId))
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(
            route = Screen.GameDetail.route,
            arguments = listOf(navArgument(Screen.GameDetail.ARG_GAME_ID) { type = NavType.LongType })
        ) {
            GameDetailScreen(
                onBack = { navController.backOrHome() }
            )
        }

        composable(Screen.Settings.route) {
            val hasPreviousScreen = navController.previousBackStackEntry != null
            SettingsScreen(
                onBack = if (hasPreviousScreen) ({ navController.popBackStack() }) else null,
                onGoToLibrary = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onEmulatorConfigClick = { navController.navigate(Screen.EmulatorConfig.route) },
                onScrapeAllClick = { navController.navigate(Screen.ScrapeProgress.route) },
                onRescanClick = { navController.navigate(Screen.Scan.route) }
            )
        }

        composable(Screen.EmulatorConfig.route) {
            EmulatorConfigScreen(
                onBack = { navController.backOrHome() }
            )
        }

        composable(Screen.ScrapeProgress.route) {
            ScrapeProgressScreen(
                onBack = { navController.backOrHome() }
            )
        }
    }
}
