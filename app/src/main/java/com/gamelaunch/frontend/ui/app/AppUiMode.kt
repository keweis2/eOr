package com.gamelaunch.frontend.ui.app

/** The chosen interface, independent of whether artwork is temporarily hidden by a game. */
enum class AppUiMode {
    SINGLE_SCREEN,
    DUAL_SCREEN;

    companion object {
        fun resolve(dualScreenEnabled: Boolean, secondDisplayAvailable: Boolean): AppUiMode =
            if (dualScreenEnabled && secondDisplayAvailable) DUAL_SCREEN else SINGLE_SCREEN
    }
}
