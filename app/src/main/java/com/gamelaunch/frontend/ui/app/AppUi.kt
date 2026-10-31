package com.gamelaunch.frontend.ui.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.gamelaunch.frontend.domain.model.EmulatorUpdate
import com.gamelaunch.frontend.domain.usecase.AppUpdate
import com.gamelaunch.frontend.ui.component.EmulatorUpdateBanner
import com.gamelaunch.frontend.ui.component.LoadingScreen
import com.gamelaunch.frontend.ui.component.UpdateBanner
import com.gamelaunch.frontend.ui.dualscreen.ArtworkBus
import com.gamelaunch.frontend.ui.dualscreen.DualScreenExternalAppOverlay
import com.gamelaunch.frontend.ui.dualscreen.DualScreenUi
import com.gamelaunch.frontend.ui.dualscreen.LocalDualScreenActive
import com.gamelaunch.frontend.ui.dualscreen.LocalExternalAppSessionActive
import com.gamelaunch.frontend.ui.navigation.Screen
import com.gamelaunch.frontend.ui.navigation.backOrHome
import com.gamelaunch.frontend.ui.singlescreen.SingleScreenUi
import com.gamelaunch.frontend.ui.systemui.SystemNavigationLockHost

/**
 * UI boundary between the existing single-screen experience and the dual-screen experience.
 * Theme and Android lifecycle work stay in MainActivity; startup, banners and system Back are
 * shared here. Keep navigation above the mode switch so changing display availability preserves
 * the back stack. Each UI entry point can evolve its navigation independently.
 */
@Composable
fun AppUi(
    mode: AppUiMode,
    artworkActive: Boolean,
    externalAppSessionActive: Boolean,
    artworkBus: ArtworkBus,
    ready: Boolean,
    isFirstLaunch: Boolean?,
    homeNavigationRequest: Int,
    onHomeNavigationHandled: () -> Unit,
    update: AppUpdate?,
    onOpenUpdate: (AppUpdate) -> Unit,
    onDismissUpdate: () -> Unit,
    emulatorUpdates: List<EmulatorUpdate>,
    onOpenEmulatorUpdates: () -> Unit,
    onDismissEmulatorUpdates: () -> Unit,
) {
    val navController = rememberNavController()
    CompositionLocalProvider(
        LocalDualScreenActive provides (mode == AppUiMode.DUAL_SCREEN && artworkActive),
        LocalExternalAppSessionActive provides (mode == AppUiMode.DUAL_SCREEN && externalAppSessionActive),
    ) {
        Box(Modifier.fillMaxSize()) {
            // Show the in-app loading screen until artwork is warmed (ready) and the launch
            // destination is known. Guarantees a visible loading state even when the OS skips the
            // system splash (e.g. eOr launched as the home app on cold boot).
            when {
                !ready || isFirstLaunch == null -> {
                    LoadingScreen()
                }
                else -> {
                    val firstLaunch = isFirstLaunch == true
                    val startDestination =
                        if (firstLaunch) Screen.Onboarding.route else Screen.Home.route
                    when (mode) {
                        AppUiMode.SINGLE_SCREEN -> SingleScreenUi(navController, startDestination)
                        AppUiMode.DUAL_SCREEN -> DualScreenUi(navController, startDestination, artworkBus)
                    }
                    LaunchedEffect(navController, homeNavigationRequest) {
                        if (homeNavigationRequest > 0) {
                            // Don't yank a first-launch user out of onboarding, and don't
                            // navigate when already at the library root.
                            val current = navController.currentDestination?.route
                            if (current != Screen.Home.route &&
                                current != Screen.Onboarding.route
                            ) {
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        inclusive = true
                                    }
                                }
                            }
                            // Consume the request so a later recomposition that re-subscribes
                            // this effect can't replay the navigation.
                            onHomeNavigationHandled()
                        }
                    }
                    // System Back (the Retroid's B maps to it): pop the nav stack so the
                    // detail/settings screens return to where they came from. If the stack has
                    // nothing to pop — e.g. eOr was resumed on a sub-screen via a launcher
                    // intent (singleTask) — fall back to Home so Back never dead-ends. At the
                    // Home root nothing pops and we stay, instead of exiting.
                    // Focused screens (e.g. the home game grid) handle Back in their own
                    // onKeyEvent first, so this only runs when nothing else consumed it.
                    BackHandler { navController.backOrHome() }
                }
            }

            // No update banners over onboarding (first launch or "Run setup again"): they cover
            // Otto's steps and steal the first controller press.
            val inOnboarding = isFirstLaunch != false ||
                navController.currentBackStackEntryAsState().value?.destination?.route == Screen.Onboarding.route
            if (!inOnboarding) update?.let { up ->
                UpdateBanner(
                    versionName = up.versionName,
                    onOpen = { onOpenUpdate(up) },
                    onDismiss = onDismissUpdate,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }

            // Emulator updates — only when there's no eOr self-update banner showing, so the two
            // never stack at the top.
            if (!inOnboarding && update == null) {
                emulatorUpdates.takeIf { it.isNotEmpty() }?.let { updates ->
                    EmulatorUpdateBanner(
                        count = updates.size,
                        onOpen = onOpenEmulatorUpdates,
                        onDismiss = onDismissEmulatorUpdates,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            }

            if (mode == AppUiMode.DUAL_SCREEN) {
                DualScreenExternalAppOverlay(externalAppSessionActive)
            }

            SystemNavigationLockHost()
        }
    }
}
