package com.gamelaunch.frontend.ui.screen.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.provider.Settings
import android.view.Display
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.gamelaunch.frontend.BuildConfig
import com.gamelaunch.frontend.domain.friends.Friend
import com.gamelaunch.frontend.domain.friends.FriendStatus
import com.gamelaunch.frontend.domain.lockedmode.LockedModeState
import com.gamelaunch.frontend.domain.model.EmulatorUpdate
import com.gamelaunch.frontend.domain.sync.EmulatorSyncStatus
import com.gamelaunch.frontend.domain.sync.SyncReadiness
import com.gamelaunch.frontend.domain.usecase.EsdeImportStatus
import com.gamelaunch.frontend.domain.usecase.LbSyncStatus
import com.gamelaunch.frontend.launcher.HomeLauncherHelper
import com.gamelaunch.frontend.systemui.SystemNavigationLockStatus
import com.gamelaunch.frontend.systemui.SystemNavigationSetupProgress
import com.gamelaunch.frontend.ui.component.AppIcon
import com.gamelaunch.frontend.ui.component.QrCode
import com.gamelaunch.frontend.ui.input.GamepadA
import com.gamelaunch.frontend.ui.lockedmode.LockedModeActivationDialog
import com.gamelaunch.frontend.ui.lockedmode.LockedModeDialogStep
import com.gamelaunch.frontend.ui.lockedmode.LockedModeSettingsViewModel
import com.gamelaunch.frontend.ui.lockedmode.PinPadDialog
import com.gamelaunch.frontend.ui.screen.friends.FriendsViewModel
import com.gamelaunch.frontend.ui.theme.CardColorScheme
import com.gamelaunch.frontend.ui.theme.ElectricBlue
import com.gamelaunch.frontend.ui.theme.LayoutMode
import com.gamelaunch.frontend.ui.theme.MonochromeSeeds
import com.gamelaunch.frontend.ui.theme.NeonPurple
import com.gamelaunch.frontend.ui.theme.ThemedScreen
import com.gamelaunch.frontend.util.StorageUtils
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
private fun SaveSyncSection() {
    val vm: SaveSyncViewModel = hiltViewModel()
    val ui by vm.uiState.collectAsState()
    SettingsSectionHeader("Save Sync")
    SyncEngineCard(
        ui,
        onToggle = vm::setEnabled,
        onLink = vm::linkDevice,
        onWifiOnly = vm::setWifiOnly,
        onChargingOnly = vm::setChargingOnly,
        onResolveConflict = vm::resolveConflict
    )
    Spacer(Modifier.height(8.dp))
    SettingsCard {
        Text(
            "Sync your emulator saves across devices. This shows which installed emulators eOr can " +
                    "sync on this device — Android blocks access to some emulators' private storage.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        when {
            ui.loading ->
                LoadingStatusRow("Scanning emulators…", MaterialTheme.colorScheme.onSurfaceVariant)

            ui.statuses.isEmpty() ->
                Text(
                    "No known emulators detected.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

            else -> ui.statuses.forEachIndexed { i, st ->
                if (i > 0) {
                    Spacer(Modifier.height(6.dp)); CardDivider(); Spacer(Modifier.height(6.dp))
                }
                SaveSyncRow(st)
            }
        }
    }
}

@Composable
private fun SyncEngineCard(
    ui: SaveSyncViewModel.UiState,
    onToggle: (Boolean) -> Unit,
    onLink: (String) -> Unit,
    onWifiOnly: (Boolean) -> Unit,
    onChargingOnly: (Boolean) -> Unit,
    onResolveConflict: (com.gamelaunch.frontend.data.sync.ConflictFile, Boolean) -> Unit
) {
    SettingsCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Save Sync",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    when {
                        !ui.enabled -> "Off"
                        ui.engineStarting -> "Starting…"
                        ui.engineRunning -> "Running"
                        else -> "Enabled"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = ui.enabled,
                onCheckedChange = onToggle,
                enabled = ui.engineSupported,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = ElectricBlue,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
        if (!ui.engineSupported) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Not available for this device's processor.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        ui.engineError?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        // Run conditions + backup status — shown whenever the feature is enabled.
        if (ui.enabled) {
            Spacer(Modifier.height(10.dp))
            CardDivider()
            Spacer(Modifier.height(4.dp))
            CardSwitchRow("Only sync on Wi-Fi", ui.wifiOnly, onWifiOnly)
            CardSwitchRow("Only sync while charging", ui.chargingOnly, onChargingOnly)
            if (ui.backupCount >= 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    if (ui.backupCount > 0)
                        "Backed up ${ui.backupCount} save folder${if (ui.backupCount != 1) "s" else ""} before first sync."
                    else "No existing saves needed backing up.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Conflicts — Syncthing keeps a copy when the same save diverges on two devices.
        if (ui.conflicts.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            CardDivider()
            Spacer(Modifier.height(8.dp))
            Text(
                "Conflicts to resolve",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            ui.conflicts.forEach { conflict ->
                Spacer(Modifier.height(8.dp))
                Text(
                    conflict.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    conflict.folderLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GradientOutlineButton(
                        "Keep this",
                        onClick = { onResolveConflict(conflict, true) },
                        modifier = Modifier.weight(1f)
                    )
                    GradientOutlineButton(
                        "Discard",
                        onClick = { onResolveConflict(conflict, false) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Pairing — shown once the engine is up and reporting this device's ID.
        if (ui.enabled && ui.deviceId != null) {
            Spacer(Modifier.height(14.dp))
            Text(
                "Link a device",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "On your other device, open Save Sync and scan this code (or paste its ID below).",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Only link your own devices — a linked device can sync your save files. Never link an " +
                    "ID someone else sent you.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                QrCode(ui.deviceId, size = 190.dp)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                ui.deviceId,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
                result.contents?.let(onLink)
            }
            GradientFillButton(
                text = "Scan a device's QR",
                onClick = {
                    scanLauncher.launch(
                        ScanOptions().apply {
                            setOrientationLocked(false)
                            setBeepEnabled(false)
                            setPrompt("Point at the other eOr device's QR")
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "or paste its ID",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            var peer by remember { mutableStateOf("") }
            OutlinedTextField(
                value = peer,
                onValueChange = { peer = it },
                label = { Text("Other device's ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricBlue,
                    cursorColor = ElectricBlue
                )
            )
            Spacer(Modifier.height(8.dp))
            GradientFillButton(
                text = "Link device",
                onClick = { onLink(peer); peer = "" },
                modifier = Modifier.fillMaxWidth(),
                enabled = peer.isNotBlank()
            )
            ui.linkResult?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SaveSyncRow(status: EmulatorSyncStatus) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                status.spec.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                status.message,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(10.dp))
        SyncStatusChip(status.readiness)
    }
}

@Composable
private fun SyncStatusChip(readiness: SyncReadiness) {
    val (label, color) = when (readiness) {
        SyncReadiness.READY -> "Ready" to Color(0xFF3FD3A6)
        SyncReadiness.NEEDS_SETUP -> "Needs setup" to Color(0xFFFFC04D)
        SyncReadiness.BLOCKED -> "Blocked" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ── Section: Background ───────────────────────────────────────────────────


// ── Screen ────────────────────────────────────────────────────────────────

@Composable
fun SaveSyncSettingsScreen(onBack: () -> Unit) {
    SettingsDetailScaffold(title = "Save Sync", onBack = onBack) {
        SaveSyncSection()
    }
}
