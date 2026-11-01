package com.gamelaunch.frontend.ui.dualscreen

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.gamelaunch.frontend.launcher.LaunchScreen
import com.gamelaunch.frontend.ui.screen.home.AppsContent
import com.gamelaunch.frontend.ui.screen.home.AppsViewModel

/** Uses the same filtered app list as the menu, with launches targeted to the top panel. */
@Composable
internal fun TopScreenApps(viewModel: AppsViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(state.launchError) {
        state.launchError?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearLaunchError()
        }
    }
    AppsContent(
        apps = state.apps,
        isLoading = state.isLoading,
        focusedIndex = -1,
        columns = (LocalConfiguration.current.screenWidthDp / 140).coerceAtLeast(2),
        packageManagerHelper = viewModel.packageManagerHelper,
        onAppClick = { viewModel.launchApp(it, LaunchScreen.TOP) },
    )
}
