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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gamelaunch.frontend.domain.model.InstalledApp
import com.gamelaunch.frontend.ui.component.AppIcon
import com.gamelaunch.frontend.ui.input.GamepadA
import com.gamelaunch.frontend.ui.theme.ElectricBlue
import com.gamelaunch.frontend.ui.theme.ThemedScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockedModeAppsScreen(
    onBack: () -> Unit,
    viewModel: LockedModeAppsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val installedPackageNames = remember(state.installedApps) {
        state.installedApps.mapTo(mutableSetOf()) { it.packageName }
    }
    val allowedInstalledCount = state.allowedPackages.count { it in installedPackageNames }

    ThemedScreen {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Allowed apps")
                            Text(
                                "$allowedInstalledCount of ${state.installedApps.size} installed allowed",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
            ) {
                when {
                    state.isLoading -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = ElectricBlue,
                    )

                    state.error != null && state.installedApps.isEmpty() -> Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                        Button(onClick = viewModel::retryLoadingApps) { Text("Retry") }
                    }

                    else -> Column(Modifier.fillMaxSize()) {
                        state.error?.let {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 12.dp),
                            )
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(state.installedApps, key = { it.packageName }) { app ->
                                LockedModeAppRow(
                                    app = app,
                                    checked = app.packageName in state.allowedPackages,
                                    enabled = app.packageName !in state.savingPackages,
                                    packageManagerHelper = viewModel.packageManagerHelper,
                                    onToggle = {
                                        viewModel.setAppAllowed(
                                            app.packageName,
                                            app.packageName !in state.allowedPackages,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LockedModeAppRow(
    app: InstalledApp,
    checked: Boolean,
    enabled: Boolean,
    packageManagerHelper: com.gamelaunch.frontend.launcher.PackageManagerHelper,
    onToggle: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (focused) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) ElectricBlue else MaterialTheme.colorScheme.outlineVariant,
                shape = shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                if (
                    enabled && event.type == KeyEventType.KeyDown &&
                    event.key in setOf(GamepadA, Key.DirectionCenter, Key.Enter)
                ) {
                    onToggle()
                    true
                } else {
                    false
                }
            }
            .focusable(enabled)
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.packageName, packageManagerHelper, Modifier.size(36.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                app.label,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
