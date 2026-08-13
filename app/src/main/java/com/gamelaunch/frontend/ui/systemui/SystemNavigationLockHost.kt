package com.gamelaunch.frontend.ui.systemui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SystemNavigationLockHost(
    viewModel: SystemNavigationLockViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(viewModel) {
        viewModel.reconcile()
        onPauseOrDispose { }
    }

    uiState.prompt?.let { prompt ->
        SystemNavigationLockDialog(
            prompt = prompt,
            onPrimaryAction = { viewModel.performPrimaryAction(prompt) },
            onDismiss = viewModel::dismissPrompt,
        )
    }
}

@Composable
private fun SystemNavigationLockDialog(
    prompt: SystemNavigationPrompt,
    onPrimaryAction: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (prompt == SystemNavigationPrompt.RESTORE_NAVIGATION) {
                    "System navigation needs restoring"
                } else {
                    "System navigation is not blocked"
                }
            )
        },
        text = { Text(prompt.message) },
        confirmButton = {
            TextButton(onClick = onPrimaryAction) {
                Text(prompt.primaryActionLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Later")
            }
        },
    )
}

private val SystemNavigationPrompt.message: String
    get() = when (this) {
        SystemNavigationPrompt.RESTORE_NAVIGATION ->
            "eOr has unlocked, but could not restore Android system navigation. Try restoring it again or reboot Android."
        SystemNavigationPrompt.ENABLE_DEVELOPER_OPTIONS -> "Enable Developer options, then return to eOr. Locked Mode remains usable, but system navigation is not blocked."
        SystemNavigationPrompt.ENABLE_WIRELESS_DEBUGGING -> "Enable Wireless debugging, then return to eOr. Locked Mode remains usable, but system navigation is not blocked."
        SystemNavigationPrompt.PAIR_DEVICE -> "Pair eOr with this device from Wireless debugging. No second app is required."
    }

private val SystemNavigationPrompt.primaryActionLabel: String
    get() = when (this) {
        SystemNavigationPrompt.ENABLE_DEVELOPER_OPTIONS,
        SystemNavigationPrompt.ENABLE_WIRELESS_DEBUGGING,
        -> "Open Developer options"
        SystemNavigationPrompt.PAIR_DEVICE -> "Pair device"
        else -> "Restore navigation"
    }
