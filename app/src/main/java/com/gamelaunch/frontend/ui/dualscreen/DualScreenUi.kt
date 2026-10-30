package com.gamelaunch.frontend.ui.dualscreen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    AppNavGraph(navController, startDestination)
    LaunchedEffect(navController, artworkBus) {
        navController.currentBackStackEntryFlow.collect { entry ->
            val route = entry.destination.route
            artworkBus.setSettingsActive(
                route?.startsWith("settings_") == true || route == Screen.EmulatorConfig.route
            )
        }
    }
    DisposableEffect(artworkBus) {
        onDispose { artworkBus.setSettingsActive(false) }
    }
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
