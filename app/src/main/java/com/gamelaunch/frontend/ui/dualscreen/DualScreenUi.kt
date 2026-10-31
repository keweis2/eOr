package com.gamelaunch.frontend.ui.dualscreen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.gamelaunch.frontend.ui.navigation.AppNavGraph
import com.gamelaunch.frontend.ui.navigation.Screen

/**
 * Entry point for the interactive panel of the dual-screen interface. Reuses the existing graph
 * until the dual-screen redesign introduces its own navigation and rendering.
 */
@Composable
fun DualScreenUi(
    navController: NavHostController,
    startDestination: String,
    artworkBus: ArtworkBus,
) {
    val coordinator = remember(artworkBus) { DualScreenCoordinator(artworkBus) }
    val preferences: DualScreenPreferences = hiltViewModel()
    val image by preferences.image.collectAsStateWithLifecycle()
    LaunchedEffect(coordinator, image) { coordinator.onImagePreference(image) }
    val session = LocalGameSessionActive.current
    LaunchedEffect(coordinator, session) { coordinator.onGameSession(session) }
    DisposableEffect(navController, coordinator) {
        val listener = NavController.OnDestinationChangedListener { _, destination, args ->
            coordinator.onDestination(destination.route,
                args?.takeIf { it.containsKey(Screen.GameDetail.ARG_GAME_ID) }
                    ?.getLong(Screen.GameDetail.ARG_GAME_ID))
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose {
            navController.removeOnDestinationChangedListener(listener)
            coordinator.onDestination(null)
        }
    }
    AppNavGraph(navController, startDestination, coordinator)
}

/** Dim the menu while the emulator is running on the other panel. */
@Composable
internal fun DualScreenGameOverlay(gameSessionActive: Boolean) {
    val dim by animateFloatAsState(
        targetValue = if (gameSessionActive) 0.82f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "bottomScreenDim",
    )
    if (dim > 0f) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
    }
}
