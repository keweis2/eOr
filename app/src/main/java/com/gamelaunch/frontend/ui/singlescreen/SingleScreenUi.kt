package com.gamelaunch.frontend.ui.singlescreen

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import com.gamelaunch.frontend.ui.navigation.AppNavGraph

/** Entry point for the existing single-screen interface. */
@Composable
fun SingleScreenUi(navController: NavHostController, startDestination: String) {
    AppNavGraph(navController, startDestination)
}
