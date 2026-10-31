package com.gamelaunch.frontend.ui.dualscreen

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks whether an external app was just launched onto the top (artwork) panel of a dual-screen device.
 *
 * On these handhelds eOr stays resumed on the bottom while the external app runs on the top (Android
 * multi-resume), so the artwork Presentation isn't torn down by the normal onStop path and would sit
 * *over* the external app. [MainActivity] observes this to suspend the artwork while an external app is on top and
 * restore it when [ExternalAppReturnActivity] observes the external app leaving the top display.
 */
@Singleton
class ExternalAppSessionState @Inject constructor() {
    private val _launchedOnTop = MutableStateFlow(false)
    val launchedOnTop: StateFlow<Boolean> = _launchedOnTop.asStateFlow()

    /** An external app was placed on the top panel — hide the artwork so the app is visible. */
    fun begin() { _launchedOnTop.value = true }

    /** The external app session ended (user returned to eOr) — restore the artwork screen. */
    fun end() { _launchedOnTop.value = false }
}

/**
 * True while an external app is running on the top panel. Read by the bottom-screen UI (e.g. the game-detail
 * preview video) to pause playback while the external app is up. Provided at the root by AppUi.
 */
val LocalExternalAppSessionActive = staticCompositionLocalOf { false }
