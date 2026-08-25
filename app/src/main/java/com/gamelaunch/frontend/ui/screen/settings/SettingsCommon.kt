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

// ── Shared settings primitives (extracted from the former SettingsScreen monolith) ──

internal val gradientBrush = Brush.horizontalGradient(listOf(ElectricBlue, NeonPurple))

/**
 * Holds the click action of whichever settings control currently has d-pad focus. Compose's
 * [clickable] only self-activates on Enter / DPAD-center, not the gamepad **A** button
 * (KEYCODE_BUTTON_A), so the screen-level key handler reads this to fire A on the focused control.
 */
internal val LocalFocusedAction = compositionLocalOf<MutableState<(() -> Unit)?>> {
    error("LocalFocusedAction not provided")
}

/**
 * Turns a settings control into a d-pad focus target: draws a highlight ring while focused, registers
 * its [onClick] so the screen's A-button handler can activate it, and keeps the ordinary touch click.
 * Use this in place of [clickable] on interactive settings rows/chips/buttons.
 */
@Composable
internal fun Modifier.dpadFocusable(
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(10.dp),
    onClick: () -> Unit
): Modifier {
    if (!enabled) return this
    val focusedAction = LocalFocusedAction.current
    var focused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged {
            focused = it.isFocused
            if (it.isFocused) focusedAction.value = onClick
        }
        .border(
            width = if (focused) 2.dp else 0.dp,
            color = if (focused) ElectricBlue else Color.Transparent,
            shape = shape
        )
        .clickable(onClick = onClick)
}


@Composable
internal fun SegmentedTabs(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { i, label ->
            val isSel = i == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .then(if (isSel) Modifier.background(gradientBrush) else Modifier)
                    .dpadFocusable(shape = RoundedCornerShape(9.dp)) { onSelect(i) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}

// ── Section: Media Storage ────────────────────────────────────────────────


@Composable
internal fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = ElectricBlue,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
    )
}

@Composable
internal fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
internal fun CardDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
        thickness = 0.5.dp
    )
}

@Composable
internal fun CardSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .dpadFocusable(shape = RoundedCornerShape(8.dp)) { onCheckedChange(!checked) }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = ElectricBlue,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@Composable
internal fun GradientFillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    val alpha = if (enabled) 1f else 0.5f
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        ElectricBlue.copy(alpha = alpha),
                        NeonPurple.copy(alpha = alpha)
                    )
                )
            )
            .then(
                if (enabled) Modifier.dpadFocusable(
                    shape = RoundedCornerShape(23.dp),
                    onClick = onClick
                ) else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
        } else {
            Text(text, style = MaterialTheme.typography.labelLarge, color = Color.White)
        }
    }
}

@Composable
internal fun GradientOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = MaterialTheme.colorScheme.primary
) {
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(
                if (enabled) Modifier.dpadFocusable(
                    shape = RoundedCornerShape(23.dp),
                    onClick = onClick
                ) else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) contentColor
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun StatusRow(
    icon: ImageVector,
    text: String,
    color: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
internal fun LoadingStatusRow(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            color = ElectricBlue,
            strokeWidth = 2.dp,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/** Pill selector used for chip-style choices (background layout, dual-screen mode). */
@Composable
internal fun BackgroundModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(21.dp))
            .then(
                if (selected) Modifier.background(gradientBrush)
                else Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
            )
            .dpadFocusable(shape = RoundedCornerShape(21.dp), onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Shared drill-in scaffold ──────────────────────────────────────────────

/**
 * Chrome shared by every settings drill-in screen: honours the light/dark choice, draws a top bar
 * with a back arrow (or, in first-launch setup where [onBack] is null, whatever [actions] provides),
 * and installs the screen-level gamepad handling — d-pad moves focus, and **A** fires the focused
 * control via [LocalFocusedAction] (Compose's clickable ignores KEYCODE_BUTTON_A). Content is placed
 * in a vertically-scrolling, focus-grouped column so d-pad traversal stays contained.
 *
 * There is intentionally no L1/R1 tab cycling here — the index+drill-in model replaced the tab bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsDetailScaffold(
    title: String,
    onBack: (() -> Unit)?,
    snackbarHostState: SnackbarHostState? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val focusedAction = remember { mutableStateOf<(() -> Unit)?>(null) }

    ThemedScreen {
        CompositionLocalProvider(LocalFocusedAction provides focusedAction) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (event.key) {
                            Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
                            Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
                            Key.DirectionLeft -> focusManager.moveFocus(FocusDirection.Left)
                            Key.DirectionRight -> focusManager.moveFocus(FocusDirection.Right)
                            GamepadA, Key.DirectionCenter, Key.Enter -> {
                                val action = focusedAction.value
                                if (action != null) {
                                    action(); true
                                } else false
                            }

                            else -> false
                        }
                    }
            ) {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    snackbarHost = {
                        if (snackbarHostState != null) {
                            SnackbarHost(snackbarHostState) { data -> Snackbar(snackbarData = data) }
                        }
                    },
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    title,
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            },
                            navigationIcon = {
                                if (onBack != null) {
                                    IconButton(
                                        onClick = onBack,
                                        modifier = Modifier
                                            .padding(8.dp)
                                            .size(36.dp)
                                            .background(
                                                MaterialTheme.colorScheme.surfaceVariant,
                                                CircleShape
                                            )
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Back",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            actions = actions,
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    }
                ) { paddingValues ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .focusGroup()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        content = content
                    )
                }
            }
        }
    }
}
