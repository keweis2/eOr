package com.gamelaunch.frontend.ui.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gamelaunch.frontend.domain.model.EmulatorMapping
import com.gamelaunch.frontend.domain.model.InstalledEmulator
import com.gamelaunch.frontend.domain.platform.PlatformDefinitions
import com.gamelaunch.frontend.ui.input.GamepadA
import com.gamelaunch.frontend.ui.input.GamepadB

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmulatorConfigScreen(
    onBack: () -> Unit,
    viewModel: EmulatorConfigViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Show scan result as a snackbar, then clear it
    LaunchedEffect(state.scanResult) {
        state.scanResult?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearScanResult()
        }
    }

    // Reuse the shared settings scaffold so this screen inherits initial focus, d-pad traversal,
    // A-to-activate, and B-to-go-back like every other settings screen.
    SettingsDetailScaffold(
        title = "Configure Emulators",
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        actions = {
            if (state.isScanning) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(24.dp)
                        .padding(end = 16.dp),
                    strokeWidth = 2.dp
                )
            } else {
                IconButton(onClick = { viewModel.rescanEmulators() }) {
                    Icon(Icons.Default.Search, contentDescription = "Re-scan for emulators")
                }
            }
        }
    ) {
        PlatformDefinitions.ALL.forEach { platform ->
            val mapping = state.mappings[platform.id]
            PlatformEmulatorCard(
                platformName = platform.displayName,
                platformId = platform.id,
                currentMapping = mapping,
                emulators = state.installedEmulators,
                onMappingChanged = { viewModel.upsertMapping(it) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlatformEmulatorCard(
    platformName: String,
    platformId: String,
    currentMapping: EmulatorMapping?,
    emulators: List<InstalledEmulator>,
    onMappingChanged: (EmulatorMapping) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedEmulator = emulators.firstOrNull { it.packageName == currentMapping?.packageName }
    // The scaffold's A-button handler fires whatever is in LocalFocusedAction; the readonly anchor
    // field registers "open the menu" while focused. The open menu tracks its own focused item.
    val focusedAction = LocalFocusedAction.current
    val menuItemAction = remember { mutableStateOf<(() -> Unit)?>(null) }

    Card(modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(platformName, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))

            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = selectedEmulator?.let { e ->
                        if (e.isInstalled) e.displayName else "${e.displayName} (not installed)"
                    } ?: "Not configured",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                        // A on the focused anchor opens the menu (the field is readonly, so the
                        // gamepad A can't otherwise trigger anything here).
                        .onFocusChanged { if (it.isFocused) focusedAction?.value = { expanded = true } }
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    // Arrow keys already move between items natively; add A-to-select the focused item
                    // and B-to-close for the gamepad (neither KEYCODE_BUTTON_A nor _B is handled by
                    // DropdownMenuItem or the menu on its own).
                    modifier = Modifier.onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (event.key) {
                            GamepadA -> menuItemAction.value?.let { it(); true } ?: false
                            GamepadB, Key.Back -> { expanded = false; true }
                            else -> false
                        }
                    }
                ) {
                    emulators.forEach { emulator ->
                        val onItemClick = {
                            expanded = false
                            onMappingChanged(
                                EmulatorMapping(
                                    id = currentMapping?.id ?: 0,
                                    platformId = platformId,
                                    packageName = emulator.packageName,
                                    isRetroArch = emulator.packageName in setOf("org.libretro.retroarch", "com.retroarch.aarch64")
                                )
                            )
                        }
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(emulator.displayName)
                                    if (!emulator.isInstalled) {
                                        Spacer(Modifier.width(6.dp))
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        ) {
                                            Text("not installed", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            },
                            onClick = onItemClick,
                            modifier = Modifier.onFocusChanged {
                                if (it.isFocused) menuItemAction.value = onItemClick
                            }
                        )
                    }
                }
            }

            if (currentMapping?.isRetroArch == true) {
                Spacer(Modifier.height(8.dp))
                val coreField = rememberDpadEditableField()
                OutlinedTextField(
                    value = currentMapping.retroArchCore ?: "",
                    onValueChange = { core ->
                        onMappingChanged(currentMapping.copy(retroArchCore = core.ifBlank { null }))
                    },
                    label = { Text("RetroArch core filename") },
                    placeholder = { Text("e.g. snes9x_libretro.so") },
                    readOnly = coreField.readOnly,
                    modifier = Modifier.fillMaxWidth().then(coreField.modifier),
                    singleLine = true
                )
            }
        }
    }
}
