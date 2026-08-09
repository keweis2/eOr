package com.gamelaunch.frontend.ui.lockedmode

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gamelaunch.frontend.ui.input.GamepadA
import com.gamelaunch.frontend.ui.input.GamepadB

private val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")

@Composable
fun PinPadDialog(
    title: String,
    subtitle: String? = null,
    error: String? = null,
    onDismiss: () -> Unit,
    onPinComplete: (String) -> Unit
) {
    var pin by remember(title, error) { mutableStateOf("") }
    var selected by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }

    fun press(index: Int) {
        when (val value = keys[index]) {
            "" -> Unit
            "⌫" -> pin = pin.dropLast(1)
            else -> if (pin.length < 4) {
                pin += value
                if (pin.length == 4) onPinComplete(pin)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 10.dp,
            modifier = Modifier
                .padding(24.dp)
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> selected = (selected - 1).coerceAtLeast(0)
                        Key.DirectionRight -> selected = (selected + 1).coerceAtMost(11)
                        Key.DirectionUp -> selected = (selected - 3).coerceAtLeast(0)
                        Key.DirectionDown -> selected = (selected + 3).coerceAtMost(11)
                        GamepadA, Key.DirectionCenter, Key.Enter -> press(selected)
                        GamepadB, Key.Back -> onDismiss()
                        else -> return@onKeyEvent false
                    }
                    true
                }
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                subtitle?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(4) { index ->
                        Box(
                            Modifier
                                .size(15.dp)
                                .background(
                                    if (index < pin.length) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    CircleShape
                                )
                                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        )
                    }
                }
                error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(18.dp))
                keys.chunked(3).forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEachIndexed { columnIndex, value ->
                            val index = rowIndex * 3 + columnIndex
                            Box(
                                modifier = Modifier
                                    .size(62.dp)
                                    .background(
                                        if (selected == index) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(16.dp)
                                    )
                                    .then(if (value.isNotEmpty()) Modifier.clickable { press(index) } else Modifier),
                                contentAlignment = Alignment.Center
                            ) {
                                when (value) {
                                    "⌫" -> Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Delete digit")
                                    "" -> Unit
                                    else -> Text(value, style = MaterialTheme.typography.headlineSmall)
                                }
                            }
                        }
                    }
                    if (rowIndex < 3) Spacer(Modifier.height(10.dp))
                }
            }
        }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
}
