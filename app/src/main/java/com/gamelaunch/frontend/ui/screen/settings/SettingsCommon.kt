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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.ReadOnlyComposable
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
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
import com.gamelaunch.frontend.ui.input.GamepadB
import com.gamelaunch.frontend.ui.lockedmode.LockedModeActivationDialog
import com.gamelaunch.frontend.ui.lockedmode.LockedModeDialogStep
import com.gamelaunch.frontend.ui.lockedmode.LockedModeSettingsViewModel
import com.gamelaunch.frontend.ui.lockedmode.PinPadDialog
import com.gamelaunch.frontend.ui.screen.friends.FriendsViewModel
import com.gamelaunch.frontend.ui.theme.CardColorScheme
import com.gamelaunch.frontend.ui.theme.ElectricBlue
import com.gamelaunch.frontend.ui.theme.FocusRing
import com.gamelaunch.frontend.ui.theme.LayoutMode
import com.gamelaunch.frontend.ui.theme.MonochromeSeeds
import com.gamelaunch.frontend.ui.theme.NeonPurple
import com.gamelaunch.frontend.ui.theme.ThemedScreen
import com.gamelaunch.frontend.util.StorageUtils
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── Shared settings primitives (extracted from the former SettingsScreen monolith) ──

internal val gradientBrush: Brush @Composable @ReadOnlyComposable get() = Brush.horizontalGradient(listOf(ElectricBlue, NeonPurple))

/**
 * Holds the click action of whichever settings control currently has d-pad focus. Compose's
 * [clickable] only self-activates on Enter / DPAD-center, not the gamepad **A** button
 * (KEYCODE_BUTTON_A), so the screen-level key handler reads this to fire A on the focused control.
 *
 * Null outside [SettingsDetailScaffold] — e.g. inside a Dialog, which is its own window and is
 * often composed next to the scaffold rather than in it. Controls still work there (touch,
 * Enter / DPAD-center); they just aren't registered for the scaffold's A-button handler.
 */
internal val LocalFocusedAction = compositionLocalOf<MutableState<(() -> Unit)?>?> { null }

/**
 * Turns a settings control into a d-pad focus target: draws a highlight ring while focused, registers
 * its [onClick] so the screen's A-button handler can activate it, and keeps the ordinary touch click.
 * Use this in place of [clickable] on interactive settings rows/chips/buttons.
 */
@Composable
internal fun Modifier.dpadFocusable(
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(10.dp),
    /** Ring colour; screens on busy/light backgrounds can pass a higher-contrast one. */
    focusColor: Color = FocusRing,
    focusWidth: Dp = 2.dp,
    onClick: () -> Unit
): Modifier {
    if (!enabled) return this
    val focusedAction = LocalFocusedAction.current
    var focused by remember { mutableStateOf(false) }
    // A stable per-instance action that always calls the latest onClick. Registering this fixed
    // reference (rather than the fresh lambda each recomposition) lets us safely clear it on unfocus:
    // otherwise focusing a control that doesn't register an action (a text field, a slider) would
    // leave a previous button's action in LocalFocusedAction and A would fire the wrong control.
    val latestOnClick by rememberUpdatedState(onClick)
    val stableAction = remember { { latestOnClick() } }
    return this
        .onFocusChanged {
            focused = it.isFocused
            if (focusedAction != null) {
                if (it.isFocused) {
                    focusedAction.value = stableAction
                } else if (focusedAction.value === stableAction) {
                    // Only clear if we're still the registered action (focus may have already moved on).
                    focusedAction.value = null
                }
            }
        }
        .border(
            width = if (focused) focusWidth else 0.dp,
            color = if (focused) focusColor else Color.Transparent,
            shape = shape
        )
        .clickable(onClick = onClick)
}

/**
 * State + modifier for a text field that behaves under d-pad control: moving focus onto it only
 * highlights it (no keyboard), and the on-screen keyboard opens only on explicit activation — the
 * gamepad **A** button or a touch tap. When the keyboard closes, the field drops back to
 * highlight-only so the d-pad can navigate away again (leaving it "editing" would trap the d-pad and
 * kill navigation). Apply [DpadEditableField.readOnly] to the field's `readOnly` and
 * [DpadEditableField.modifier] to its `modifier`.
 */
internal class DpadEditableField(
    val readOnly: Boolean,
    val modifier: Modifier,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun rememberDpadEditableField(): DpadEditableField {
    val focusedAction = LocalFocusedAction.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val view = LocalView.current
    val panels = com.gamelaunch.frontend.ui.dualscreen.LocalSettingsPanels.current
    val imeVisible by rememberUpdatedState(WindowInsets.isImeVisible)
    var editing by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }

    // Wait for the editable field's input connection. The insets-controller
    // show path can fail when Android hosts this window's IME on a different display.
    LaunchedEffect(editing) {
        if (editing) {
            val input = view.context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
            do {
                delay(16)
            } while (!view.hasWindowFocus() || !view.onCheckIsTextEditor() || input?.isActive(view) != true)
            // isActive above checks connection readiness, never keyboard visibility.
            input.showSoftInput(view, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            var wasVisible = false
            while (true) {
                delay(50)
                val visible = panels?.isKeyboardVisible() ?: imeVisible
                if (visible) wasVisible = true
                else if (wasVisible) {
                    editing = false
                    break
                }
            }
        }
    }

    val activate = remember { { editing = true } }
    val modifier = Modifier
        .focusRequester(focusRequester)
        .onFocusChanged {
            focused = it.isFocused
            if (it.isFocused) {
                focusedAction?.value = activate
            } else {
                editing = false
                if (focusedAction?.value === activate) focusedAction.value = null
            }
        }
        // Catch A/Center/Enter in the preview phase — before the focused text field's own key
        // handling can swallow it — to enter edit mode. Registering the action in LocalFocusedAction
        // above is not enough on its own because the field consumes the button first.
        .onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                GamepadA, Key.DirectionCenter, Key.Enter ->
                    if (!editing) { editing = true; true } else false
                // While highlight-only (not editing), drive navigation ourselves so the read-only
                // text field can't swallow the d-pad and strand focus. While editing, let the field
                // have the keys (cursor movement) — the user leaves edit mode by closing the keyboard.
                Key.DirectionUp -> if (!editing) { focusManager.moveFocus(FocusDirection.Up); true } else false
                Key.DirectionDown -> if (!editing) { focusManager.moveFocus(FocusDirection.Down); true } else false
                Key.DirectionLeft -> if (!editing) { focusManager.moveFocus(FocusDirection.Left); true } else false
                Key.DirectionRight -> if (!editing) { focusManager.moveFocus(FocusDirection.Right); true } else false
                else -> false
            }
        }
        // Touch: a tap enters edit mode. Uses pointerInput (not clickable) on purpose — clickable
        // would add its own focus target that steals d-pad focus from the actual text field, so the
        // editable field could never gain focus and the keyboard would never open.
        .pointerInput(Unit) {
            // The inner read-only TextField consumes taps during Main. Observe Initial without
            // consuming events so activation still runs and text selection/scrolling keep working.
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var tap = true
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (event.changes.size != 1 ||
                        (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                        tap = false
                    }
                    if (!change.pressed) {
                        if (tap) {
                            focusRequester.requestFocus()
                            editing = true
                        }
                        break
                    }
                }
            }
        }
        .border(
            width = if (focused) 2.dp else 0.dp,
            color = if (focused) FocusRing else Color.Transparent,
            shape = RoundedCornerShape(6.dp)
        )
    return DpadEditableField(readOnly = !editing, modifier = modifier)
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
        color = MaterialTheme.colorScheme.primary,
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
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)
@Composable
internal fun SettingsDetailScaffold(
    title: String,
    onBack: (() -> Unit)?,
    snackbarHostState: SnackbarHostState? = null,
    actions: @Composable RowScope.() -> Unit = {},
    /** False for screens that lay out (and scroll) their own panes, like the theme editor. */
    scrollable: Boolean = true,
    scrollState: ScrollState? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val isSettingsControls = com.gamelaunch.frontend.ui.dualscreen.LocalSettingsControls.current
    val panelBack = com.gamelaunch.frontend.ui.dualscreen.LocalSettingsPanelBack.current
    val panels = com.gamelaunch.frontend.ui.dualscreen.LocalSettingsPanels.current
    val effectiveOnBack = panelBack ?: onBack
    val focusManager = LocalFocusManager.current
    val focusedAction = remember { mutableStateOf<(() -> Unit)?>(null) }
    // Grabs first focus so the d-pad and A button have a target the moment the screen opens.
    // Without this nothing is highlighted on entry and the first key press is a no-op.
    val contentFocusRequester = remember { FocusRequester() }
    var hasFocus by remember { mutableStateOf(false) }
    // True when the scaffold's own Box holds focus directly (i.e. focus has not yet descended into a
    // child control). We keep the Box itself focusable — requesting focus on a bare focusGroup fails
    // to actually place focus on the handhelds, leaving nothing focused so no key ever arrives.
    var boxFocused by remember { mutableStateOf(false) }
    val view = LocalView.current
    val imeVisible = WindowInsets.isImeVisible

    // Touch mode is the enemy of d-pad focus: as soon as the user touches the screen, Android enters
    // "touch mode" and refuses focus requests, so the highlight vanishes and can't be restored. Making
    // the Compose host view focusable-in-touch-mode lets us hold and re-request focus regardless, so a
    // gamepad press (or the watchdog below) brings the highlight straight back.
    DisposableEffect(view) {
        val prev = view.isFocusableInTouchMode
        view.isFocusableInTouchMode = true
        onDispose { view.isFocusableInTouchMode = prev }
    }

    // Keep the d-pad alive: whenever this screen owns the window but nothing inside the content is
    // focused, pull focus back onto the scaffold Box. This grabs initial focus on entry (retrying
    // until the window transition settles) and restores focus if it is ever lost — including after a
    // touch clears it. It stays out of the keyboard's way (a field keeps focus, so hasFocus stays
    // true, and imeVisible guards it) and of dialogs (a dialog owns the window, so hasWindowFocus() is
    // false). Requesting focus when the Box already holds it is a harmless no-op.
    LaunchedEffect(Unit) {
        while (true) {
            val receivesPanelInput = panels?.controlsFocused == true && isSettingsControls
            if (!hasFocus && !imeVisible && (view.hasWindowFocus() || receivesPanelInput)) {
                runCatching { contentFocusRequester.requestFocus() }
            }
            delay(150)
        }
    }

    ThemedScreen {
        CompositionLocalProvider(LocalFocusedAction provides focusedAction) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(contentFocusRequester)
                    .onFocusChanged {
                        hasFocus = it.hasFocus
                        boxFocused = it.isFocused
                    }
                    .focusable()
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                        // When the Box itself holds focus (nothing highlighted yet), the first
                        // directional / A press descends into the content's first control instead.
                        if (boxFocused) {
                            return@onKeyEvent when (event.key) {
                                Key.DirectionDown, Key.DirectionUp, Key.DirectionLeft, Key.DirectionRight,
                                GamepadA, Key.DirectionCenter, Key.Enter -> {
                                    focusManager.moveFocus(FocusDirection.Enter); true
                                }
                                GamepadB, Key.Back -> { effectiveOnBack?.invoke(); effectiveOnBack != null }
                                else -> false
                            }
                        }
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
                            // Gamepad B (KEYCODE_BUTTON_B) is not the system Back key, so back
                            // out of the screen ourselves. No-op in first-launch setup (effectiveOnBack null).
                            GamepadB, Key.Back -> {
                                val back = effectiveOnBack
                                if (back != null) {
                                    back(); true
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
                                if (!isSettingsControls && effectiveOnBack != null) {
                                    IconButton(
                                        onClick = effectiveOnBack,
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
                            .then(if (scrollable) Modifier.verticalScroll(scrollState ?: rememberScrollState()) else Modifier)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        content = content
                    )
                }
            }
        }
    }
}
