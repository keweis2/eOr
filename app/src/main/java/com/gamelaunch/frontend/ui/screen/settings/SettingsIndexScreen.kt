package com.gamelaunch.frontend.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
        "Grid size, theme, colours, and background", Icons.Default.Palette
    ),
    HOME_LAYOUT(
        "settings_home_layout", "Home & Layout",
        "Home app, system order, hidden systems, dual screen", Icons.Default.Home
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
        "settings_retro_achievements", "RetroAchievements",
        "Log in and track achievements", Icons.Default.EmojiEvents
    ),
    SAVE_SYNC(
        "settings_save_sync", "Save Sync",
        "Sync saves across devices", Icons.Default.Sync
    ),
    FRIENDS(
        "settings_friends", "Friends",
        "Share activity and manage friends", Icons.Default.Group
    ),
    LOCKED_MODE(
        "settings_locked", "Locked Mode",
        "Restrict eOr to approved games and apps", Icons.Default.Lock
    ),
}

@Composable
fun SettingsIndexScreen(
    onBack: (() -> Unit)?,
    onGoToLibrary: () -> Unit,
    onOpenCategory: (SettingsCategory) -> Unit,
    viewModel: SettingsViewModel,
) {
    // Collected so the setup "Library" finish button can persist credentials entered on any
    // sub-screen (the view model is shared across the whole settings graph).
    val state by viewModel.uiState.collectAsState()

    SettingsDetailScaffold(
        title = "Settings",
        onBack = onBack,
        actions = {
            // First-launch setup only (no back button yet): a way to finish and enter the library.
            if (onBack == null) {
                SetupFinishAction(
                    onClick = {
                        viewModel.saveCredentials()
                        viewModel.finishSetup()
                        onGoToLibrary()
                    }
                )
            }
        }
    ) {
        SettingsCategory.entries.forEach { category ->
            SettingsCategoryRow(category = category, onClick = { onOpenCategory(category) })
        }
    }
}

@Composable
private fun SettingsCategoryRow(
    category: SettingsCategory,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .dpadFocusable(shape = RoundedCornerShape(14.dp), onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(gradientBrush),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                category.icon,
                contentDescription = null,
                tint = Color.White,
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
    Spacer(Modifier.size(2.dp))
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
