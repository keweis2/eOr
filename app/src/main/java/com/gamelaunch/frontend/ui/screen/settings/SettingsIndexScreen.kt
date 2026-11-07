package com.gamelaunch.frontend.ui.screen.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The Settings landing screen: a scrollable, d-pad-navigable list of category rows. Tapping a row
 * drills into that category's own screen. This index is the app's findability mechanism as settings
 * grow — adding a feature is one new [SettingsCategory] entry plus its screen, never re-cramming an
 * existing tab. Route strings are mirrored by the matching objects in `Screen.kt`.
 */
enum class SettingsCategory(
    val route: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
) {
    APPEARANCE(
        "settings_appearance", "Appearance",
        "Theme, performance", Icons.Default.Palette
    ),
    HOME_LAYOUT(
        "settings_home_layout", "Home & Layout",
        "Home app, sections, system order, layout, hidden systems, dual screen", Icons.Default.Home
    ),
    GAMES(
        "settings_games", "Games & Library",
        "ROM folders, Android & Steam games, emulators", Icons.Default.VideogameAsset
    ),
    MEDIA(
        "settings_media", "Media & Artwork",
        "Media storage, ScreenScraper, artwork database", Icons.Default.PermMedia
    ),
    RETRO_ACHIEVEMENTS(
        "settings_retro_achievements", "Achievements",
        "Log in and track achievements", Icons.Default.EmojiEvents
    ),
    SAVE_SYNC(
        "settings_save_sync", "Save Sync",
        "Sync saves across devices", Icons.Default.Sync
    ),
    WEB_TRANSFER(
        "settings_web_transfer", "Web Transfer",
        "Send files from your computer's browser", Icons.Default.Wifi
    ),
    FRIENDS(
        "settings_friends", "Friends",
        "Share activity and manage friends", Icons.Default.Group
    ),
    LOCKED_MODE(
        "settings_locked", "Locked Mode",
        "Restrict eOr to approved games and apps", Icons.Default.Lock
    ),
    BACKUP(
        "settings_backup", "Backup & Setup",
        "Back up or restore settings, run setup again", Icons.Default.SettingsBackupRestore
    ),
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsIndexScreen(
    onBack: (() -> Unit)?,
    onGoToLibrary: () -> Unit,
    onOpenCategory: (SettingsCategory) -> Unit,
    viewModel: SettingsViewModel,
    selectedCategory: SettingsCategory? = null,
    categoryScrollState: ScrollState? = null,
    focusCategories: Boolean = false,
    onCategoriesTouched: (() -> Unit)? = null,
) {
    // Collected so the setup "Library" finish button can persist credentials entered on any
    // sub-screen (the view model is shared across the whole settings graph).
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val selectedFocus = remember { FocusRequester() }
    val selectedVisibility = remember { BringIntoViewRequester() }
    LaunchedEffect(selectedCategory) {
        if (selectedCategory != null) {
            androidx.compose.runtime.withFrameNanos { }
            selectedVisibility.bringIntoView()
        }
    }
    LaunchedEffect(selectedCategory, focusCategories) {
        if (selectedCategory != null && focusCategories) {
            androidx.compose.runtime.withFrameNanos { }
            // Navigation can detach the outgoing list before this frame resumes.
            runCatching { selectedFocus.requestFocus() }
        }
    }

    SettingsDetailScaffold(
        title = if (selectedCategory != null) "Settings categories" else "Settings",
        scrollState = categoryScrollState,
        onBack = onBack,
        actions = {
            // First-launch setup only (no back button yet): a way to finish and enter the library.
            if (onBack == null) {
                SetupFinishAction(
                    onClick = {
                        viewModel.completeSetup(onGoToLibrary)
                    }
                )
            }
        }
    ) {
        SettingsCategory.entries.forEach { category ->
            SettingsCategoryRow(
                category = category,
                selected = category == selectedCategory,
                modifier = if (category == selectedCategory) Modifier
                    .bringIntoViewRequester(selectedVisibility)
                    .focusRequester(selectedFocus) else Modifier,
                onClick = { onOpenCategory(category) },
                onTouched = onCategoriesTouched,
            )
        }
    }
}

@Composable
private fun SettingsCategoryRow(
    category: SettingsCategory,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    onTouched: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    ElevatedCard(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)) else Modifier)
            .pointerInput(onTouched) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.any { it.pressed }) onTouched?.invoke()
                    }
                }
            }
            .dpadFocusable(shape = RoundedCornerShape(14.dp), onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    category.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    category.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    category.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
    Spacer(Modifier.size(4.dp))
}

@Composable
private fun SetupFinishAction(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(end = 12.dp)
            .clip(RoundedCornerShape(50))
            .background(gradientBrush)
            .dpadFocusable(shape = RoundedCornerShape(50), onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            "Library",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(14.dp)
        )
    }
}
