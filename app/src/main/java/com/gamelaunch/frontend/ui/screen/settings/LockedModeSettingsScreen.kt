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
private fun LockedModeSection(
    onManageAllowedGames: () -> Unit,
    onManageAllowedApps: () -> Unit,
    viewModel: LockedModeSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val state = uiState.lockedModeState
    val enabled = state == LockedModeState.READY || state == LockedModeState.LOCKED
    var showLockConfirm by remember { mutableStateOf(false) }
    var showNavigationSteps by rememberSaveable { mutableStateOf(true) }
    var openSettingsAfterNotificationPermission by remember { mutableStateOf(false) }
    var notificationPermissionRequested by remember { mutableStateOf(false) }
    val pairingContext = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.prepareEmbeddedPairingNotification()
        if (granted && openSettingsAfterNotificationPermission) {
            viewModel.beginEmbeddedPairingSetup()
        }
        openSettingsAfterNotificationPermission = false
    }
    val setupNeedsPairingNotification = !uiState.systemNavigationSetupProgress.paired &&
            uiState.systemNavigationStatus in setOf(
        SystemNavigationLockStatus.WIRELESS_DEBUGGING_REQUIRED,
        SystemNavigationLockStatus.PAIRING_REQUIRED,
    )
    val showSystemNavigationSetupSteps = showNavigationSteps &&
            shouldShowSystemNavigationSetupSteps(uiState.systemNavigationStatus)
    LifecycleResumeEffect(
        viewModel,
        uiState.blockSystemNavigation,
        setupNeedsPairingNotification,
    ) {
        if (uiState.blockSystemNavigation && setupNeedsPairingNotification) {
            val needsPermission = Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                pairingContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
            if (needsPermission && !notificationPermissionRequested) {
                notificationPermissionRequested = true
                openSettingsAfterNotificationPermission = false
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else if (!needsPermission) {
                viewModel.prepareEmbeddedPairingNotification()
            }
        }
        onPauseOrDispose { }
    }
    LaunchedEffect(uiState.systemNavigationSetupProgress.developerOptionsEnabled) {
        // as long as the developer options are not enabled, we poll
        while (!uiState.systemNavigationSetupProgress.developerOptionsEnabled) {
            viewModel.refreshSystemNavigationSetupProgress()
            kotlinx.coroutines.delay(1_000)
        }
    }
    LaunchedEffect(uiState.blockSystemNavigation, showSystemNavigationSetupSteps) {
        while (uiState.blockSystemNavigation && showSystemNavigationSetupSteps) {
            viewModel.refreshSystemNavigationSetupProgress()
            kotlinx.coroutines.delay(1_000)
        }
    }

    if (uiState.showSystemNavigationWarning) {
        AlertDialog(
            onDismissRequest = viewModel::dismissSystemNavigationWarning,
            title = { Text("Enable system navigation blocking?") },
            text = {
                Text(
                    "This lets eOr use Wireless debugging and low-level Android system " +
                            "commands to block Home, Recents, edge navigation, and the " +
                            "notification shade while Locked Mode is active.\n\n" +
                            "This feature requires low-level system knowledge. Only enable it " +
                            "if you understand what it does and know how to recover system " +
                            "navigation if setup fails."
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmBlockSystemNavigation) {
                    Text("Enable")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissSystemNavigationWarning) {
                    Text("Cancel")
                }
            },
        )
    }

    SettingsSectionHeader("Locked mode")
    SettingsCard {
        Text(
            "Locked Mode creates a simplified Home screen that shows only the games and " +
                    "apps you choose. Enabling the feature does not lock eOr immediately—you " +
                    "decide when to enter Locked Mode.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = ElectricBlue)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when (state) {
                        null -> "Loading…"
                        LockedModeState.DISABLED -> "Disabled"
                        LockedModeState.READY -> "Enabled"
                        LockedModeState.LOCKED -> "Enabled and locked"
                    },
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    when (state) {
                        null -> "Checking Locked Mode status…"
                        LockedModeState.DISABLED ->
                            "Turn on Locked Mode to restrict eOr to the games and apps you allow."

                        LockedModeState.READY ->
                            "Locked Mode is ready. Choose Lock Now here or use the lock button " +
                                    "on Home."

                        LockedModeState.LOCKED ->
                            "Locked Mode is active. Only allowed games and apps are available."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = viewModel::setEnabled,
                enabled = state != null,
            )
        }
        Spacer(Modifier.height(12.dp))
        GradientFillButton(
            text = "Lock Now",
            onClick = {
                if (uiState.hasPin) showLockConfirm = true else viewModel.lockNow()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Turning Locked Mode off exits it immediately. Your PIN and allowed games and " +
                    "apps are kept for the next time you enable it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    SettingsSectionHeader("PIN settings")
    SettingsCard {
        Text(
            "A PIN is optional. Set one if you want to prevent someone from leaving Locked " +
                    "Mode without entering four digits.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                !enabled -> "Enable Locked Mode to set, change, or remove its PIN."
                uiState.hasPin ->
                    "PIN protection is active. Changing or removing the PIN does not " +
                            "require the current PIN."

                else ->
                    "Without a PIN, pressing the unlock button on Home exits Locked Mode " +
                            "immediately."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            GradientOutlineButton(
                text = if (uiState.hasPin) "Change PIN" else "Set PIN",
                onClick = if (uiState.hasPin) viewModel::startChange else viewModel::startSetup,
                modifier = Modifier.weight(1f),
                enabled = enabled,
            )
            GradientOutlineButton(
                text = "Remove PIN",
                onClick = viewModel::removePin,
                modifier = Modifier.weight(1f),
                enabled = enabled && uiState.hasPin,
                contentColor = MaterialTheme.colorScheme.error
            )
        }
    }

    // only show option to block system navigation on a developer enabled device
    if (uiState.systemNavigationSetupProgress.developerOptionsEnabled) {
        SettingsSectionHeader("System navigation")
        SettingsCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = ElectricBlue)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Block system navigation while locked",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Uses Wireless debugging to block Home, Recents, edge navigation, and " +
                                "notification shade access while Locked Mode is active.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = uiState.blockSystemNavigation,
                    onCheckedChange = { checked ->
                        viewModel.setBlockSystemNavigation(checked)
                    },
                    enabled = enabled,
                )
            }
            if (uiState.blockSystemNavigation) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(12.dp))
                Text(
                    "What to expect:\n" +
                            "• While Locked Mode is active, the notification shade, Home, Recent " +
                            "apps, and edge navigation will be unavailable.\n" +
                            "• Unlocking eOr restores normal system navigation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (showSystemNavigationSetupSteps) {
                    Spacer(Modifier.height(12.dp))
                    val setupStepCompletion = systemNavigationSetupStepCompletion(
                        uiState.systemNavigationSetupProgress,
                    )
                    val stepsText = buildAnnotatedString {
                        append("What to do:")
                        fun appendStep(number: Int, complete: Boolean, instruction: String) {
                            append('\n')
                            if (complete) append("✓")
                            else withStyle(SpanStyle(color = Color.Transparent)) { append("✓") }
                            append(" ($number) $instruction")
                        }
                        appendStep(
                            number = 1,
                            complete = setupStepCompletion[0],
                            instruction = "Enable Developer options: open About phone or About " +
                                    "device, find Build number, and tap it seven times.",
                        )
                        appendStep(
                            number = 2,
                            complete = setupStepCompletion[1],
                            instruction = "Open Developer options and enable Wireless debugging.",
                        )
                        if (uiState.systemNavigationSetupProgress.paired) {
                            appendStep(
                                number = 3,
                                complete = setupStepCompletion[2],
                                instruction = "eOr is paired with Wireless debugging.",
                            )
                        } else {
                            appendStep(
                                number = 3,
                                complete = false,
                                instruction = "Open Wireless debugging and choose ‘Pair device " +
                                        "with pairing code’.",
                            )
                            appendStep(
                                number = 4,
                                complete = false,
                                instruction = "Pull down the notification shade and find the eOr " +
                                        "notification, not leaving the pair device screen.",
                            )
                            appendStep(
                                number = 5,
                                complete = false,
                                instruction = "Tap ‘Enter pairing code’ and enter the six-digit code.",
                            )
                            appendStep(
                                number = 6,
                                complete = false,
                                instruction = "Press send and keep the pairing dialog open until " +
                                        "pairing completes.",
                            )
                        }
                    }
                    Text(
                        stepsText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Text(
                            "[hide steps]",
                            style = MaterialTheme.typography.labelMedium,
                            color = ElectricBlue,
                            modifier = Modifier
                                .dpadFocusable { showNavigationSteps = false }
                                .padding(6.dp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(12.dp))
                }
                val statusText = when (uiState.systemNavigationStatus) {
                    SystemNavigationLockStatus.DISABLED -> "Disabled"
                    SystemNavigationLockStatus.UNSUPPORTED -> "Unsupported on Android 8–10"
                    SystemNavigationLockStatus.DEVELOPER_OPTIONS_REQUIRED -> "Enable Developer options"
                    SystemNavigationLockStatus.WIRELESS_DEBUGGING_REQUIRED ->
                        "Enable Wireless debugging"

                    SystemNavigationLockStatus.PAIRING_REQUIRED -> "Pair eOr with Wireless debugging"
                    SystemNavigationLockStatus.DISCOVERING -> "Discovering local ADB endpoint…"
                    SystemNavigationLockStatus.START_REQUIRED -> "Navigation blocking needs setup"
                    SystemNavigationLockStatus.STARTING -> "Waiting for Android…"
                    SystemNavigationLockStatus.READY -> "Ready - activates with Locked Mode"
                    SystemNavigationLockStatus.APPLYING -> "Applying navigation blocking…"
                    SystemNavigationLockStatus.ACTIVE -> "System navigation is blocked"
                    SystemNavigationLockStatus.RESTORE_REQUIRED ->
                        "Restore navigation or reboot Android"

                    SystemNavigationLockStatus.ERROR -> "Could not enable navigation blocking"
                }
                Text(statusText, style = MaterialTheme.typography.bodyMedium)
                val requiresDeveloperOptions = uiState.systemNavigationStatus in setOf(
                    SystemNavigationLockStatus.DEVELOPER_OPTIONS_REQUIRED,
                    SystemNavigationLockStatus.PAIRING_REQUIRED,
                    SystemNavigationLockStatus.WIRELESS_DEBUGGING_REQUIRED,
                )
                if (requiresDeveloperOptions) {
                    Spacer(Modifier.height(10.dp))
                    val developerOptionsRequired =
                        uiState.systemNavigationStatus ==
                                SystemNavigationLockStatus.DEVELOPER_OPTIONS_REQUIRED
                    GradientOutlineButton(
                        text = if (developerOptionsRequired) {
                            "Open About device"
                        } else {
                            "Open Developer options"
                        },
                        onClick = {
                            if (developerOptionsRequired) {
                                viewModel.openDeviceInfoSettings()
                                return@GradientOutlineButton
                            }
                            val needsNotificationPermission =
                                uiState.systemNavigationStatus ==
                                        SystemNavigationLockStatus.PAIRING_REQUIRED &&
                                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        ContextCompat.checkSelfPermission(
                                            pairingContext,
                                            Manifest.permission.POST_NOTIFICATIONS,
                                        ) != PackageManager.PERMISSION_GRANTED

                            if (needsNotificationPermission) {
                                notificationPermissionRequested = true
                                openSettingsAfterNotificationPermission = true
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.beginEmbeddedPairingSetup()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    SettingsSectionHeader("Configuration")
    SettingsCard {
        Text(
            "Build the selection that will be available on Home while Locked Mode is active.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (enabled) "You can update the allowed selection whenever Locked Mode is enabled."
            else "Enable Locked Mode before choosing which games and apps are allowed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        GradientFillButton(
            text = "Manage allowed games",
            onClick = onManageAllowedGames,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
        )
        Spacer(Modifier.height(10.dp))
        GradientFillButton(
            text = "Manage allowed apps",
            onClick = onManageAllowedApps,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
        )
    }

    if (showLockConfirm) {
        LockedModeActivationDialog(
            onConfirm = {
                showLockConfirm = false
                viewModel.lockNow()
            },
            onDismiss = { showLockConfirm = false },
        )
    }

    uiState.dialogStep?.let { step ->
        val title = when (step) {
            LockedModeDialogStep.CREATE_PIN -> "Create PIN"
            LockedModeDialogStep.CONFIRM_PIN -> "Confirm PIN"
            LockedModeDialogStep.NEW_PIN -> "New PIN"
            LockedModeDialogStep.CONFIRM_NEW_PIN -> "Confirm new PIN"
        }
        PinPadDialog(
            title = title,
            subtitle = null,
            error = uiState.error,
            onDismiss = viewModel::dismissDialog,
            onPinComplete = viewModel::submitPin,
        )
    }
}

internal fun shouldShowSystemNavigationSetupSteps(status: SystemNavigationLockStatus): Boolean =
    status !in setOf(
        SystemNavigationLockStatus.READY,
        SystemNavigationLockStatus.APPLYING,
        SystemNavigationLockStatus.ACTIVE,
    )

internal fun systemNavigationSetupStepCompletion(
    progress: SystemNavigationSetupProgress,
): List<Boolean> {
    val developerOptionsComplete = progress.developerOptionsEnabled
    val wirelessDebuggingComplete = developerOptionsComplete && progress.wirelessDebuggingEnabled
    val pairingComplete = wirelessDebuggingComplete && progress.paired
    return listOf(developerOptionsComplete, wirelessDebuggingComplete, pairingComplete)
}


// ── Screen ────────────────────────────────────────────────────────────────

@Composable
fun LockedModeSettingsScreen(
    onBack: () -> Unit,
    onManageAllowedGames: () -> Unit,
    onManageAllowedApps: () -> Unit,
) {
    SettingsDetailScaffold(title = "Locked Mode", onBack = onBack) {
        LockedModeSection(
            onManageAllowedGames = onManageAllowedGames,
            onManageAllowedApps = onManageAllowedApps,
        )
    }
}
