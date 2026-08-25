package com.gamelaunch.frontend.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.gamelaunch.frontend.ui.screen.detail.GameDetailScreen
import com.gamelaunch.frontend.ui.screen.home.HomeScreen
import com.gamelaunch.frontend.ui.screen.onboarding.OnboardingScreen
import com.gamelaunch.frontend.ui.screen.scan.ScanScreen
import com.gamelaunch.frontend.ui.screen.scrape.ScrapeProgressScreen
import com.gamelaunch.frontend.ui.screen.settings.AppearanceSettingsScreen
import com.gamelaunch.frontend.ui.screen.settings.EmulatorConfigScreen
import com.gamelaunch.frontend.ui.screen.settings.FriendsSettingsScreen
import com.gamelaunch.frontend.ui.screen.settings.GamesSettingsScreen
import com.gamelaunch.frontend.ui.screen.settings.HomeLayoutSettingsScreen
import com.gamelaunch.frontend.ui.screen.settings.LockedModeSettingsScreen
import com.gamelaunch.frontend.ui.screen.settings.MediaSettingsScreen
import com.gamelaunch.frontend.ui.screen.settings.RetroAchievementsSettingsScreen
import com.gamelaunch.frontend.ui.screen.settings.SaveSyncSettingsScreen
import com.gamelaunch.frontend.ui.screen.settings.SettingsIndexScreen
import com.gamelaunch.frontend.ui.screen.settings.SettingsViewModel
import com.gamelaunch.frontend.domain.lockedmode.LockedModeState
import com.gamelaunch.frontend.ui.lockedmode.LockedModeViewModel
import com.gamelaunch.frontend.ui.lockedmode.LockedModeGamesScreen
import com.gamelaunch.frontend.ui.lockedmode.LockedModeAppsScreen
import androidx.hilt.navigation.compose.hiltViewModel

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
    val lockedModeViewModel: LockedModeViewModel = hiltViewModel()
    val lockedModeUiState by lockedModeViewModel.uiState.collectAsState()
    val lockedModeState = lockedModeUiState.state
    val isLocked = lockedModeState == LockedModeState.LOCKED
    // null means DataStore is still loading; deny protected routes until the state is known.
    val canAccessProtectedRoutes = lockedModeState != null && !isLocked

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
            ProtectedRoute(lockedModeState, navController) {
                ScanScreen(
                    onScanComplete = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Scan.route) { inclusive = true }
                        }
                    }
                )
            }
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onGameClick = { gameId ->
                    navController.navigate(Screen.GameDetail.route(gameId))
                },
                onSettingsClick = {
                    if (canAccessProtectedRoutes) navController.navigate(Screen.Settings.route)
                },
                onScrapeSystem = { platformId ->
                    navController.navigate(Screen.ScrapeProgress.route(platformId))
                },
                lockedModeViewModel = lockedModeViewModel
            )
        }

        composable(
            route = Screen.GameDetail.route,
            arguments = listOf(navArgument(Screen.GameDetail.ARG_GAME_ID) { type = NavType.LongType })
        ) {
            GameDetailScreen(
                onBack = { navController.backOrHome() },
                isLocked = isLocked
            )
        }

        // Settings is a nested graph so every category screen shares ONE SettingsViewModel
        // (scoped to the graph's back-stack entry). This keeps in-memory credentials entered on a
        // sub-screen alive until the first-launch "Library" finish persists them.
        navigation(
            startDestination = Screen.SettingsIndex.route,
            route = Screen.Settings.route
        ) {
            composable(Screen.SettingsIndex.route) {
                ProtectedRoute(lockedModeState, navController) {
                    // No entry beneath the index means first-launch setup: show the "Library"
                    // finish button instead of a back arrow (mirrors the old SettingsScreen).
                    val hasPreviousScreen = navController.previousBackStackEntry != null
                    SettingsIndexScreen(
                        onBack = if (hasPreviousScreen) ({ navController.popBackStack() }) else null,
                        onGoToLibrary = {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        onOpenCategory = { navController.navigate(it.route) },
                        viewModel = navController.sharedSettingsViewModel()
                    )
                }
            }

            composable(Screen.SettingsAppearance.route) {
                ProtectedRoute(lockedModeState, navController) {
                    AppearanceSettingsScreen(
                        onBack = { navController.backOrHome() },
                        viewModel = navController.sharedSettingsViewModel()
                    )
                }
            }

            composable(Screen.SettingsHomeLayout.route) {
                ProtectedRoute(lockedModeState, navController) {
                    HomeLayoutSettingsScreen(
                        onBack = { navController.backOrHome() },
                        viewModel = navController.sharedSettingsViewModel()
                    )
                }
            }

            composable(Screen.SettingsGames.route) {
                ProtectedRoute(lockedModeState, navController) {
                    GamesSettingsScreen(
                        onBack = { navController.backOrHome() },
                        viewModel = navController.sharedSettingsViewModel(),
                        onEmulatorConfigClick = { navController.navigate(Screen.EmulatorConfig.route) },
                        onScrapeAllClick = { navController.navigate(Screen.ScrapeProgress.route()) },
                        onRescanClick = { navController.navigate(Screen.Scan.route) }
                    )
                }
            }

            composable(Screen.SettingsMedia.route) {
                ProtectedRoute(lockedModeState, navController) {
                    MediaSettingsScreen(
                        onBack = { navController.backOrHome() },
                        viewModel = navController.sharedSettingsViewModel(),
                        onScrapeAllClick = { navController.navigate(Screen.ScrapeProgress.route()) }
                    )
                }
            }

            composable(Screen.SettingsRetroAchievements.route) {
                ProtectedRoute(lockedModeState, navController) {
                    RetroAchievementsSettingsScreen(
                        onBack = { navController.backOrHome() },
                        viewModel = navController.sharedSettingsViewModel()
                    )
                }
            }

            composable(Screen.SettingsSaveSync.route) {
                ProtectedRoute(lockedModeState, navController) {
                    SaveSyncSettingsScreen(onBack = { navController.backOrHome() })
                }
            }

            composable(Screen.SettingsFriends.route) {
                ProtectedRoute(lockedModeState, navController) {
                    FriendsSettingsScreen(
                        onBack = { navController.backOrHome() },
                        viewModel = navController.sharedSettingsViewModel()
                    )
                }
            }

            composable(Screen.SettingsLocked.route) {
                ProtectedRoute(lockedModeState, navController) {
                    LockedModeSettingsScreen(
                        onBack = { navController.backOrHome() },
                        onManageAllowedGames = { navController.navigate(Screen.LockedModeGames.route) },
                        onManageAllowedApps = { navController.navigate(Screen.LockedModeApps.route) }
                    )
                }
            }
        }

        composable(Screen.LockedModeGames.route) {
            ProtectedRoute(lockedModeState, navController) {
                LockedModeGamesScreen(onBack = { navController.backOrHome() })
            }
        }

        composable(Screen.LockedModeApps.route) {
            ProtectedRoute(lockedModeState, navController) {
                LockedModeAppsScreen(onBack = { navController.backOrHome() })
            }
        }

        composable(Screen.EmulatorConfig.route) {
            ProtectedRoute(lockedModeState, navController) {
                EmulatorConfigScreen(onBack = { navController.backOrHome() })
            }
        }

        composable(
            route = Screen.ScrapeProgress.route,
            arguments = listOf(navArgument(Screen.ScrapeProgress.ARG_PLATFORM_ID) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) {
            ProtectedRoute(lockedModeState, navController) {
                ScrapeProgressScreen(onBack = { navController.backOrHome() })
            }
        }
    }
}

@Composable
private fun ProtectedRoute(
    lockedModeState: LockedModeState?,
    navController: NavController,
    content: @Composable () -> Unit,
) {
    when (lockedModeState) {
        null -> Unit // Wait for DataStore before rendering or redirecting.
        LockedModeState.LOCKED -> {
            LaunchedEffect(Unit) { navController.navigateHomeClearingStack() }
        }
        else -> content()
    }
}

private fun NavController.navigateHomeClearingStack() {
    navigate(Screen.Home.route) { popUpTo(0) { inclusive = true } }
}

/**
 * The single SettingsViewModel shared by every settings category screen. Scoping to the settings
 * graph's back-stack entry (rather than each destination) means one instance backs the whole flow,
 * so transient in-memory state — notably credentials awaiting [SettingsViewModel.saveCredentials] —
 * is consistent across drill-ins and survives until the setup "Library" finish persists it.
 */
@Composable
private fun NavController.sharedSettingsViewModel(): SettingsViewModel {
    val parentEntry = remember { getBackStackEntry(Screen.Settings.route) }
    return hiltViewModel(parentEntry)
}
