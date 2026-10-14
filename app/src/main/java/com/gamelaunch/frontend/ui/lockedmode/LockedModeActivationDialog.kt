package com.gamelaunch.frontend.ui.lockedmode

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.gamelaunch.frontend.ui.input.GamepadA
import com.gamelaunch.frontend.ui.input.GamepadB
import com.gamelaunch.frontend.ui.theme.ElectricBlue

@Composable
fun LockedModeActivationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    // A Dialog is its own focus tree, so wire it for the handhelds' controller: d-pad moves focus
    // between the two buttons, A fires the focused one, B cancels. Focus starts on Cancel (the safe
    // choice — this locks the device). Touch on either button still works as before.
    val focusManager = LocalFocusManager.current
    var focusedAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val cancelFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { cancelFocusRequester.requestFocus() } }

    AlertDialog(
        modifier = Modifier.onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> { focusManager.moveFocus(FocusDirection.Left); true }
                Key.DirectionRight -> { focusManager.moveFocus(FocusDirection.Right); true }
                GamepadA, Key.DirectionCenter, Key.Enter ->
                    focusedAction?.let { it(); true } ?: false
                GamepadB, Key.Back -> { onDismiss(); true }
                else -> false
            }
        },
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
            var focused by remember { mutableStateOf(false) }
            TextButton(
                onClick = onConfirm,
                modifier = Modifier
                    .onFocusChanged {
                        focused = it.isFocused
                        if (it.isFocused) focusedAction = onConfirm
                    }
                    .border(
                        width = if (focused) 2.dp else 0.dp,
                        color = if (focused) ElectricBlue else Color.Transparent,
                        shape = RoundedCornerShape(20.dp)
                    )
            ) { Text("Lock") }
        },
        dismissButton = {
            var focused by remember { mutableStateOf(false) }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .focusRequester(cancelFocusRequester)
                    .onFocusChanged {
                        focused = it.isFocused
                        if (it.isFocused) focusedAction = onDismiss
                    }
                    .border(
                        width = if (focused) 2.dp else 0.dp,
                        color = if (focused) ElectricBlue else Color.Transparent,
                        shape = RoundedCornerShape(20.dp)
                    )
            ) { Text("Cancel") }
        },
    )
}
