package com.gamelaunch.frontend.ui.dualscreen

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks whether a game was just launched onto the top (artwork) panel of a dual-screen device.
 *
 * On these handhelds eOr stays resumed on the bottom while the emulator runs on the top (Android
 * multi-resume), so the artwork Presentation isn't torn down by the normal onStop path and would sit
 * *over* the game. [MainActivity] observes this to suspend the artwork while a game is on top and
 * restore it when [GameReturnActivity] observes the emulator leaving the top display.
 */
@Singleton
class GameSessionState @Inject constructor() {
    private val _launchedOnTop = MutableStateFlow(false)
    val launchedOnTop: StateFlow<Boolean> = _launchedOnTop.asStateFlow()

    /** A game was placed on the top panel — hide the artwork so the game is visible. */
    fun begin() { _launchedOnTop.value = true }

    /** The game session ended (user returned to eOr) — restore the artwork screen. */
    fun end() { _launchedOnTop.value = false }
}

/**
 * True while a game is running on the top panel. Read by the bottom-screen UI (e.g. the game-detail
 * preview video) to pause playback while the game is up. Provided at the root by AppUi.
 */
val LocalGameSessionActive = staticCompositionLocalOf { false }
