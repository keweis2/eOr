package com.gamelaunch.frontend.ui.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gamelaunch.frontend.domain.model.EmulatorMapping
import com.gamelaunch.frontend.domain.model.InstalledEmulator
import com.gamelaunch.frontend.domain.platform.PlatformDefinitions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmulatorConfigScreen(
    onBack: () -> Unit,
    viewModel: EmulatorConfigViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Show scan result as a snackbar, then clear it
    LaunchedEffect(state.scanResult) {
        state.scanResult?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearScanResult()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configure Emulators") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(
                            onClick = { viewModel.rescanEmulators() }
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Re-scan for emulators")
                        }
                    }
                }
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(snackbarData = data)
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(8.dp)
        ) {
            items(PlatformDefinitions.ALL, key = { it.id }) { platform ->
                val mapping = state.mappings[platform.id]
                PlatformEmulatorCard(
                    platformName = platform.displayName,
                    platformId = platform.id,
                    currentMapping = mapping,
                    emulators = state.installedEmulators,
                    onMappingChanged = { viewModel.upsertMapping(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
            }
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
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    emulators.forEach { emulator ->
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
                            onClick = {
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
                        )
                    }
                }
            }

            if (currentMapping?.isRetroArch == true) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = currentMapping.retroArchCore ?: "",
                    onValueChange = { core ->
                        onMappingChanged(currentMapping.copy(retroArchCore = core.ifBlank { null }))
                    },
                    label = { Text("RetroArch core filename") },
                    placeholder = { Text("e.g. snes9x_libretro.so") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }
    }
}
