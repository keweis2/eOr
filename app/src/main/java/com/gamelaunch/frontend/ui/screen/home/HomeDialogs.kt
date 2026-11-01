package com.gamelaunch.frontend.ui.screen.home

import androidx.compose.runtime.Composable
import com.gamelaunch.frontend.ui.lockedmode.LockedModeActivationDialog
import com.gamelaunch.frontend.ui.lockedmode.PinPadDialog

@Composable
internal fun HomeDialogs(
    ui: HomeScreenState,
    onActivate: () -> Unit,
    onUnlock: (String) -> Unit,
) {
    if (ui.showLockConfirm) {
        LockedModeActivationDialog(
            onConfirm = {
                ui.showLockConfirm = false
                onActivate()
            },
            onDismiss = { ui.showLockConfirm = false },
        )
    }
    if (ui.showUnlock) {
        PinPadDialog(
            title = "Unlock eOr",
            error = ui.unlockError,
            onDismiss = { ui.showUnlock = false },
            onPinComplete = onUnlock,
        )
    }
}
