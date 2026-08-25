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
internal fun HomeLauncherSection(
    isDefault: Boolean,
    onOpenSettings: () -> Unit
) {
    SettingsSectionHeader("Home Launcher")
    SettingsCard {
        StatusRow(
            icon = Icons.Default.Home,
            text = if (isDefault) "eOr is your Home app" else "Another Home app is selected",
            color = if (isDefault) ElectricBlue else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        if (isDefault) {
            GradientOutlineButton(
                text = "Manage Home app",
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            GradientFillButton(
                text = "Set eOr as Home app",
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}


@Composable
private fun DualScreenSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val hasSecondScreen = remember {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        dm?.displays?.any { it.displayId != Display.DEFAULT_DISPLAY && it.state == Display.STATE_ON } == true
    }
    if (!hasSecondScreen) return

    SettingsSectionHeader("Dual Screen")
    SettingsCard {
        Text(
            "Two screens detected. eOr shows the menu on the bottom panel and game artwork on the top.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        CardSwitchRow(
            label = "Use both screens",
            checked = state.dualScreenEnabled,
            onCheckedChange = viewModel::setDualScreenEnabled
        )
        if (state.dualScreenEnabled) {
            CardSwitchRow(
                label = "Swap screens",
                checked = state.dualScreenSwap,
                onCheckedChange = viewModel::setDualScreenSwap
            )
            CardSwitchRow(
                label = "Launch games on top screen",
                checked = state.gameLaunchOnTop,
                onCheckedChange = viewModel::setGameLaunchOnTop
            )
            Text(
                "Single-screen games (PlayStation, Game Boy, etc.) open on the top panel. DS/3DS games always use both screens.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Top screen image",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                com.gamelaunch.frontend.ui.dualscreen.TopScreenImage.entries.forEach { opt ->
                    BackgroundModeChip(
                        label = opt.label,
                        selected = state.topScreenImage == opt,
                        onClick = { viewModel.setTopScreenImage(opt) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Text(
                "What the top panel shows while browsing a game — its logo, an in-game screenshot, or a composited mix.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
    Spacer(Modifier.height(4.dp))
}

// ── Section: Sort Systems ─────────────────────────────────────────────────

@Composable
private fun SystemSortSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    SettingsSectionHeader("Sort Systems")
    SettingsCard {
        Text(
            "Choose how your consoles are ordered. Pick up to two — the first is the primary sort, the second breaks ties.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        com.gamelaunch.frontend.domain.platform.SystemSort.entries.forEach { sort ->
            val rank = state.systemSort.indexOf(sort)   // -1 if not selected
            val isSel = rank >= 0
            val disabled = !isSel && state.systemSort.size >= 2
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .dpadFocusable(enabled = !disabled) { viewModel.toggleSystemSort(sort) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (isSel) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = when {
                        isSel -> ElectricBlue
                        disabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    sort.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (disabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal
                )
                Spacer(Modifier.weight(1f))
                if (isSel) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(50))
                            .background(gradientBrush),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${rank + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ── Section: Hide Systems ─────────────────────────────────────────────────

@Composable
private fun HideSystemsSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    if (state.libraryPlatforms.isEmpty()) return
    SettingsSectionHeader("Hide Systems")
    SettingsCard {
        Text(
            "Turn a system off to hide its whole category from the home screen. Its games stay in the library and can be shown again anytime.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        state.libraryPlatforms.forEachIndexed { index, platformId ->
            if (index > 0) CardDivider()
            val displayName = com.gamelaunch.frontend.domain.platform.PlatformDefinitions
                .byId[platformId]?.displayName ?: platformId
            CardSwitchRow(
                label = displayName,
                checked = platformId !in state.hiddenPlatforms,
                onCheckedChange = { shown -> viewModel.setPlatformHidden(platformId, !shown) }
            )
        }
    }
}

// ── Section: Save Sync ─────────────────────────────────────────────────────


// ── Screen ────────────────────────────────────────────────────────────────

@Composable
fun HomeLayoutSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel,
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var isDefaultHome by remember { mutableStateOf(HomeLauncherHelper.isDefaultHome(context)) }
    val homeLauncherPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {}

    fun openHomeLauncherSettings() {
        val opened = runCatching {
            homeLauncherPicker.launch(HomeLauncherHelper.selectionIntent(context))
        }.isSuccess || runCatching {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        }.isSuccess
        if (!opened) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Home app settings aren't available on this device")
            }
        }
    }

    // Settings / OEM chooser activities don't return a reliable result, so re-check on resume.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isDefaultHome = HomeLauncherHelper.isDefaultHome(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SettingsDetailScaffold(title = "Home & Layout", onBack = onBack, snackbarHostState = snackbarHostState) {
        HomeLauncherSection(isDefault = isDefaultHome, onOpenSettings = ::openHomeLauncherSettings)
        Spacer(Modifier.height(4.dp))
        DualScreenSection(state, viewModel)
        SystemSortSection(state, viewModel)
        Spacer(Modifier.height(4.dp))
        HideSystemsSection(state, viewModel)
    }
}
