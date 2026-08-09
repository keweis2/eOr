package com.gamelaunch.frontend.ui.lockedmode

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun LockedModeActivationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lock eOr?") },
        text = {
            Text(
                "A PIN is set. Once Locked Mode is active, settings, administrative " +
                    "controls, and excluded games and apps will be unavailable. You can " +
                    "only leave Locked Mode by entering your PIN. Make sure you know it " +
                    "before continuing."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Lock") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
