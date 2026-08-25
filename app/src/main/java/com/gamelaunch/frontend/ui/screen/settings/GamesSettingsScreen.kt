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
private fun RomLibrarySection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    storageVolumes: List<Pair<String, String>>,
    onPickRomFolder: () -> Unit,
    onRescanClick: () -> Unit,
    onScrapeAllClick: () -> Unit
) {
    SettingsSectionHeader("ROM Library")
    SettingsCard {
        if (storageVolumes.size > 1) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 10.dp)
            ) {
                storageVolumes.forEach { (label, path) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .dpadFocusable(shape = RoundedCornerShape(10.dp)) {
                                viewModel.setRomRootPath(
                                    path
                                )
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }
            }
            CardDivider()
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = if (storageVolumes.size > 1) 10.dp else 0.dp)
        ) {
            OutlinedTextField(
                value = state.romRootPath.ifEmpty { "Not configured" },
                onValueChange = { viewModel.setRomRootPath(it) },
                label = { Text("ROM Folder") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricBlue,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onPickRomFolder,
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = "Browse", tint = ElectricBlue)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Tip: for SD card use /storage/XXXX-XXXX/ROMs",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        CardDivider()
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GradientOutlineButton(
                text = "Rescan ROMs",
                onClick = onRescanClick,
                modifier = Modifier.weight(1f)
            )
            GradientFillButton(
                text = "Scrape All",
                onClick = onScrapeAllClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ── Section: Android Games ────────────────────────────────────────────────

@Composable
private fun AndroidGamesSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    SettingsSectionHeader("Android Games")
    SettingsCard {
        Text(
            "Scan installed Android games (apps tagged as games) and add them to your library.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        GradientFillButton(
            text = "Scan Android Games",
            onClick = { viewModel.scanAndroidGames() },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        GradientOutlineButton(
            text = "Select Games Manually",
            onClick = { viewModel.showAndroidGameSelection(true) },
            modifier = Modifier.fillMaxWidth()
        )
        state.androidScanResult?.let { result ->
            Spacer(Modifier.height(6.dp))
            StatusRow(
                icon = Icons.Default.Check,
                text = result,
                color = ElectricBlue
            )
        }
    }
}

// ── Section: Steam / PC Games ─────────────────────────────────────────────

@Composable
private fun SteamLibrarySection(state: SettingsUiState, viewModel: SettingsViewModel) {
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            val path = StorageUtils.resolveTreeUriToPath(it) ?: it.toString()
            viewModel.setSteamLibraryPath(path)
        }
    }

    SettingsSectionHeader("Steam / PC Games")
    SettingsCard {
        Text(
            "Add PC games run through GameNative, GameHub or Winlator. In GameNative, turn on " +
                    "\"Export for ES-DE\" and point it at a folder on shared storage, then choose that same " +
                    "folder here — eOr reads the exported games and launches them back through GameNative. " +
                    "A raw Steam \"steamapps\" folder works too.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.FolderOpen,
                contentDescription = null,
                tint = ElectricBlue,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = state.steamLibraryPath.ifBlank { "No folder selected" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GradientFillButton(
                text = if (state.steamLibraryPath.isBlank()) "Choose Folder" else "Change Folder",
                onClick = { folderPicker.launch(null) },
                modifier = Modifier.weight(1f)
            )
            if (state.steamLibraryPath.isNotBlank()) {
                GradientOutlineButton(
                    text = "Clear",
                    onClick = { viewModel.clearSteamLibraryPath() },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        if (state.steamLibraryPath.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            GradientFillButton(
                text = "Scan Steam Library",
                onClick = { viewModel.scanSteamLibrary() },
                modifier = Modifier.fillMaxWidth()
            )
        }
        when {
            state.steamScanning -> {
                Spacer(Modifier.height(8.dp))
                LoadingStatusRow(
                    "Scanning Steam library…",
                    MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            state.steamScanResult != null -> {
                Spacer(Modifier.height(6.dp))
                StatusRow(Icons.Default.Check, state.steamScanResult, ElectricBlue)
            }
        }
    }
}

@Composable
private fun AndroidGameSelectionDialog(
    state: SettingsUiState,
    viewModel: SettingsViewModel
) {
    Dialog(
        onDismissRequest = { viewModel.showAndroidGameSelection(false) },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Select Android Games",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Check the apps you want to see in your Games section",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { viewModel.showAndroidGameSelection(false) },
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Scrollable list of apps
                Box(modifier = Modifier.weight(1f)) {
                    if (state.installedApps.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = ElectricBlue)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(state.installedApps, key = { it.packageName }) { app ->
                                val isChecked = state.checkedPackages.contains(app.packageName)
                                var isFocused by remember { mutableStateOf(false) }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isFocused) MaterialTheme.colorScheme.surfaceVariant
                                            else Color.Transparent
                                        )
                                        .border(
                                            width = if (isFocused) 2.dp else 1.dp,
                                            color = if (isFocused) ElectricBlue else MaterialTheme.colorScheme.outlineVariant,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .onFocusChanged { isFocused = it.isFocused }
                                        .focusable()
                                        .clickable {
                                            viewModel.toggleAndroidGameSelection(app, !isChecked)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        AppIcon(
                                            packageName = app.packageName,
                                            packageManagerHelper = viewModel.packageManagerHelper,
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = app.label,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = app.packageName,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = null, // Handled by row clickable
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = ElectricBlue,
                                            checkmarkColor = Color.White
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Done Button
                GradientFillButton(
                    text = "Done",
                    onClick = { viewModel.showAndroidGameSelection(false) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// ── Section: Emulators ────────────────────────────────────────────────────

@Composable
private fun EmulatorsSection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    onEmulatorConfigClick: () -> Unit
) {
    SettingsSectionHeader("Emulators")
    SettingsCard {
        Text(
            "Auto-detect maps installed emulators to your platforms, or configure each one manually.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        GradientFillButton(
            text = "Auto-detect Emulators",
            onClick = { viewModel.autoDetectEmulators() },
            enabled = !state.emulatorDetecting,
            loading = state.emulatorDetecting,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        GradientOutlineButton(
            text = "Configure Emulators Manually",
            onClick = onEmulatorConfigClick,
            modifier = Modifier.fillMaxWidth()
        )
    }
    Spacer(Modifier.height(10.dp))
    EmulatorUpdatesCard(
        obtainiumInstalled = state.obtainiumInstalled,
        isChecking = state.isCheckingUpdates,
        updates = state.emulatorUpdates,
        notificationsEnabled = state.emulatorUpdateNotifications,
        onTrack = { viewModel.trackWithObtainium() },
        onCheck = { viewModel.checkForEmulatorUpdates() },
        onUpdate = { viewModel.updateWithObtainium(it) },
        onNotificationsChange = { viewModel.setEmulatorUpdateNotifications(it) }
    )
}

@Composable
private fun EmulatorUpdatesCard(
    obtainiumInstalled: Boolean,
    isChecking: Boolean,
    updates: List<EmulatorUpdate>,
    notificationsEnabled: Boolean,
    onTrack: () -> Unit,
    onCheck: () -> Unit,
    onUpdate: (EmulatorUpdate) -> Unit,
    onNotificationsChange: (Boolean) -> Unit
) {
    SettingsCard {
        Text("Emulator updates", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            if (obtainiumInstalled) {
                "Obtainium keeps your emulators up to date in the background."
            } else {
                "Track and install emulator updates with Obtainium — a free, open-source app " +
                    "updater. Tap below to install it, then come back."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        GradientFillButton(
            text = if (obtainiumInstalled) "Track updates" else "Set up Obtainium",
            onClick = onTrack,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        GradientOutlineButton(
            text = if (isChecking) "Checking…" else "Check for updates",
            onClick = onCheck,
            enabled = !isChecking,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(4.dp))
        CardSwitchRow(
            label = "Update notifications",
            checked = notificationsEnabled,
            onCheckedChange = onNotificationsChange
        )

        if (updates.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                "${updates.size} update${if (updates.size != 1) "s" else ""} available",
                style = MaterialTheme.typography.labelLarge,
                color = ElectricBlue
            )
            updates.forEach { update ->
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(update.displayName, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${update.installedVersion ?: "?"} → ${update.latestVersion}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    GradientFillButton(
                        text = "Update",
                        onClick = { onUpdate(update) },
                        modifier = Modifier.width(104.dp)
                    )
                }
            }
        }
    }
}

// ── Section: ScreenScraper ────────────────────────────────────────────────


// ── Screen ────────────────────────────────────────────────────────────────

@Composable
fun GamesSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel,
    onEmulatorConfigClick: () -> Unit,
    onScrapeAllClick: () -> Unit,
    onRescanClick: () -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val storageVolumes = remember { StorageUtils.getStorageVolumes(context) }

    LaunchedEffect(state.emulatorDetectResult) {
        state.emulatorDetectResult?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearEmulatorDetectResult()
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            val path = StorageUtils.resolveTreeUriToPath(it) ?: it.toString()
            viewModel.setRomRootPath(path)
        }
    }

    SettingsDetailScaffold(title = "Games & Library", onBack = onBack, snackbarHostState = snackbarHostState) {
        RomLibrarySection(
            state, viewModel, storageVolumes,
            onPickRomFolder = { folderPicker.launch(null) },
            onRescanClick = onRescanClick,
            onScrapeAllClick = onScrapeAllClick
        )
        Spacer(Modifier.height(4.dp))
        AndroidGamesSection(state, viewModel)
        Spacer(Modifier.height(4.dp))
        SteamLibrarySection(state, viewModel)
        Spacer(Modifier.height(4.dp))
        EmulatorsSection(state, viewModel, onEmulatorConfigClick)
    }

    if (state.showAndroidGameSelection) {
        AndroidGameSelectionDialog(state = state, viewModel = viewModel)
    }
}
