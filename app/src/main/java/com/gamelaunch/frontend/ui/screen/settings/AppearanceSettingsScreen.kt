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
private fun DisplaySection(state: SettingsUiState, viewModel: SettingsViewModel) {
    SettingsSectionHeader("Display")
    SettingsCard {
        CardSwitchRow(
            label = "Favorites tab",
            checked = state.showFavorites,
            onCheckedChange = viewModel::setShowFavorites
        )
        CardSwitchRow(
            label = "Recently Played tab",
            checked = state.showRecentlyPlayed,
            onCheckedChange = viewModel::setShowRecentlyPlayed
        )
        CardSwitchRow(
            label = "RetroAchievements tab",
            checked = state.showRetroAchievements,
            onCheckedChange = viewModel::setShowRetroAchievements
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Library Layout",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            BackgroundModeChip(
                label = "Carousel",
                selected = state.layoutMode == LayoutMode.CAROUSEL,
                onClick = { viewModel.setLayoutMode(LayoutMode.CAROUSEL) },
                modifier = Modifier.weight(1f)
            )
            BackgroundModeChip(
                label = "Grid",
                selected = state.layoutMode == LayoutMode.GRID,
                onClick = { viewModel.setLayoutMode(LayoutMode.GRID) },
                modifier = Modifier.weight(1f)
            )
            BackgroundModeChip(
                label = "List",
                selected = state.layoutMode == LayoutMode.LIST,
                onClick = { viewModel.setLayoutMode(LayoutMode.LIST) },
                modifier = Modifier.weight(1f)
            )
        }
        // Master grid size — only meaningful (and only shown) when the grid layout is active. Sets the
        // default tile size for every system's game grid; a system sized from its own grid overrides it.
        if (state.layoutMode == LayoutMode.GRID) {
            Spacer(Modifier.height(12.dp))
            MasterGridSizeControl(
                columns = state.masterGridColumns,
                onSet = viewModel::setMasterGridColumns
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Appearance",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        ThemePicker(selectedDark = state.darkMode, onSelect = viewModel::setDarkMode)

        Spacer(Modifier.height(12.dp))
        Text(
            "Card colors",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        CardColorSchemePicker(
            scheme = state.cardColorScheme,
            monoColor = state.cardMonoColor,
            onSchemeSelected = viewModel::setCardColorScheme,
            onMonoColorSelected = viewModel::setCardMonoColor
        )

        Spacer(Modifier.height(10.dp))
        if (BuildConfig.LOW_POWER) {
            // The lite build always runs reduced; there's nothing to toggle.
            Text(
                "Low-power build — performance optimizations are always on.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            CardSwitchRow(
                label = "Performance mode",
                checked = state.performanceMode,
                onCheckedChange = viewModel::setPerformanceMode
            )
            Text(
                "Reduces animations and delays video previews — recommended on low-power handhelds.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Master (default) game-grid size, shown in the Display section only while the grid layout is active.
 * The slider reads small → large (fewer columns = bigger tiles) to match the in-grid quick menu, with
 * an "Auto" stop at the far left that lets the grid fit the screen. Recents/Favorites keep their own
 * fixed sizing, and any system sized from its own grid overrides this default.
 */

@Composable
private fun MasterGridSizeControl(columns: Int, onSet: (Int) -> Unit) {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    // Same column maths as the home grid so the steps line up with what the library actually shows.
    val minCols = 3
    val maxCols = maxOf(1, (screenWidthDp - 2 * 8 + 8) / (92 + 8)).coerceIn(minCols + 1, 12)

    // Fold "Auto" (columns == 0) into the far-left slot; explicit sizes run smallest-tiles (maxCols)
    // up to largest-tiles (minCols) rightward.
    val sliderMax = (maxCols - minCols) + 1
    val sliderValue = if (columns <= 0) 0 else (maxCols - columns.coerceIn(minCols, maxCols) + 1)

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "Grid size",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                if (columns <= 0) "Auto" else "$columns per row",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = sliderValue.toFloat(),
            onValueChange = { v ->
                val iv = v.roundToInt()
                onSet(if (iv <= 0) 0 else maxCols - (iv - 1))
            },
            valueRange = 0f..sliderMax.toFloat(),
            steps = (sliderMax - 1).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = ElectricBlue,
                activeTrackColor = ElectricBlue,
            )
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "Auto",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Larger",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            "Default size for every system's game grid. Resize a single system from its grid to override it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Section: Dual Screen ──────────────────────────────────────────────────

/**
 * Only shown on dual-screen handhelds (a second physical display is present). Lets the user turn
 * the split layout off and manually swap which panel shows the menu vs artwork if auto-detection
 * guessed wrong for an unrecognised device.
 */

@Composable
private fun BackgroundBrandingSection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    onPickImage: () -> Unit
) {
    SettingsSectionHeader("Background")
    SettingsCard {
        val hasImage = state.backgroundImagePath.isNotBlank()
        Text(
            "Brand your background with an image, converted to a single-colour silhouette drawn over " +
                    "the backdrop and recoloured to match light and dark mode. With no image, the eOr " +
                    "silhouette is used.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        CardSwitchRow(
            label = "Enable custom background",
            checked = state.backgroundImageEnabled,
            onCheckedChange = viewModel::setBackgroundImageEnabled
        )
        if (state.backgroundImageEnabled) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Layout",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                BackgroundModeChip(
                    label = "Fill",
                    selected = state.backgroundImageMode == "FILL",
                    onClick = { viewModel.setBackgroundImageMode("FILL") },
                    modifier = Modifier.weight(1f)
                )
                BackgroundModeChip(
                    label = "Tile",
                    selected = state.backgroundImageMode == "TILE",
                    onClick = { viewModel.setBackgroundImageMode("TILE") },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Opacity",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "${(state.backgroundImageOpacity * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Slider(
                value = state.backgroundImageOpacity,
                onValueChange = viewModel::setBackgroundImageOpacity,
                valueRange = 0.05f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = ElectricBlue,
                    activeTrackColor = ElectricBlue,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
        Spacer(Modifier.height(12.dp))
        GradientFillButton(
            text = if (hasImage) "Replace image" else "Upload image",
            onClick = onPickImage,
            modifier = Modifier.fillMaxWidth(),
            loading = state.convertingBackground
        )
        if (hasImage) {
            Spacer(Modifier.height(8.dp))
            GradientOutlineButton(
                text = "Remove image (use eOr silhouette)",
                onClick = viewModel::clearBackgroundImage,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Two visual cards — Light / Dark — each previewing the UI, with the active one highlighted. */
@Composable
private fun ThemePicker(selectedDark: Boolean, onSelect: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        ThemeOption(
            "Light",
            dark = false,
            selected = !selectedDark,
            onClick = { onSelect(false) },
            modifier = Modifier.weight(1f)
        )
        ThemeOption(
            "Dark",
            dark = true,
            selected = selectedDark,
            onClick = { onSelect(true) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ThemeOption(
    label: String,
    dark: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = ElectricBlue
    val bg = if (dark) Color(0xFF06091A) else Color(0xFFEDEFF4)
    val cardColor = if (dark) Color(0xFF172044) else Color(0xFFFFFFFF)
    val barColors = listOf(Color(0xFF7C8CFF), Color(0xFFB07BFF), Color(0xFFFF7AA8))
    val tileShade = if (dark) 0.55f else 0f
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) accent else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(14.dp)
            )
            .dpadFocusable(shape = RoundedCornerShape(14.dp), onClick = onClick)
            .padding(8.dp)
    ) {
        // Mini UI mock-up: background, a top accent bar and three colourful tiles.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(74.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(bg)
                .padding(7.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(0.55f)
                    .height(7.dp)
                    .clip(RoundedCornerShape(50))
                    .background(accent)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                barColors.forEach { c ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(30.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(
                                androidx.compose.ui.graphics.lerp(
                                    c,
                                    Color.Black,
                                    tileShade
                                )
                            )
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxWidth(0.8f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(cardColor)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Home-card colour scheme picker: three chips (Rainbow / Black & White / Monochrome). When
 * Monochrome is active a scrollable row of colour swatches is revealed so the user can pick the
 * single hue the whole grid is tinted with. Mirrors the tile-colour logic in Glass.kt.
 */
@Composable
private fun CardColorSchemePicker(
    scheme: CardColorScheme,
    monoColor: Int,
    onSchemeSelected: (CardColorScheme) -> Unit,
    onMonoColorSelected: (Int) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            BackgroundModeChip(
                label = "Rainbow",
                selected = scheme == CardColorScheme.RAINBOW,
                onClick = { onSchemeSelected(CardColorScheme.RAINBOW) },
                modifier = Modifier.weight(1f)
            )
            BackgroundModeChip(
                label = "B & W",
                selected = scheme == CardColorScheme.BLACK_WHITE,
                onClick = { onSchemeSelected(CardColorScheme.BLACK_WHITE) },
                modifier = Modifier.weight(1f)
            )
            BackgroundModeChip(
                label = "Mono",
                selected = scheme == CardColorScheme.MONOCHROME,
                onClick = { onSchemeSelected(CardColorScheme.MONOCHROME) },
                modifier = Modifier.weight(1f)
            )
        }
        if (scheme == CardColorScheme.MONOCHROME) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Tile color",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MonochromeSeeds.forEach { seed ->
                    val argb = seed.toArgb()
                    ColorSwatch(
                        color = seed,
                        selected = argb == monoColor,
                        onClick = { onMonoColorSelected(argb) }
                    )
                }
            }
        }
    }
}

/** A single circular colour swatch for the monochrome-seed picker; a ring marks the active one. */
@Composable
private fun ColorSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                shape = CircleShape
            )
            .dpadFocusable(shape = CircleShape, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ── Section: ROM Library ──────────────────────────────────────────────────


// ── Screen ────────────────────────────────────────────────────────────────

@Composable
fun AppearanceSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel,
) {
    val state by viewModel.uiState.collectAsState()
    val backgroundImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { viewModel.importBackgroundImage(it) } }

    SettingsDetailScaffold(title = "Appearance", onBack = onBack) {
        DisplaySection(state, viewModel)
        Spacer(Modifier.height(4.dp))
        BackgroundBrandingSection(
            state,
            viewModel,
            onPickImage = {
                backgroundImagePicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        )
    }
}
