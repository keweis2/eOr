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
private fun ScreenScraperBody(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    onScrapeAllClick: () -> Unit
) {
    Text(
        "ScreenScraper is the default scraper — it provides the best box art, screenshots, marquees (wheel logos), and video previews. " +
                "Create a free account at screenscraper.fr, then enter your username and password below. " +
                "Without credentials the app falls back to libretro thumbnails and LaunchBox.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = state.ssId,
        onValueChange = viewModel::updateSsId,
        label = { Text("Username (ssid)") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ElectricBlue,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        )
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = state.ssPassword,
        onValueChange = viewModel::updateSsPassword,
        label = { Text("Password") },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ElectricBlue,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        )
    )
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GradientOutlineButton(
            text = "Save",
            onClick = { viewModel.saveCredentials() },
            modifier = Modifier.weight(1f)
        )
        GradientFillButton(
            text = "Validate",
            onClick = { viewModel.validateCredentials() },
            enabled = !state.credentialValidating,
            loading = state.credentialValidating,
            modifier = Modifier.weight(1f)
        )
    }
    state.credentialValid?.let { valid ->
        Spacer(Modifier.height(8.dp))
        StatusRow(
            icon = if (valid) Icons.Default.Check else Icons.Default.Close,
            text = if (valid) "Credentials valid" else "Invalid credentials",
            color = if (valid) ElectricBlue else MaterialTheme.colorScheme.error
        )
    }

    Spacer(Modifier.height(12.dp))
    CardDivider()
    Spacer(Modifier.height(12.dp))

    Text(
        "Scrape options",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(Modifier.height(4.dp))
    CardSwitchRow("Metadata", state.scrapeMetadata, viewModel::setScrapeMetadata)
    CardSwitchRow("Box Art", state.scrapeBoxArt, viewModel::setScrapeBoxArt)
    CardSwitchRow("Screenshots", state.scrapeScreenshots, viewModel::setScrapeScreenshots)
    CardSwitchRow("Marquees (Wheel Logos)", state.scrapeWheelLogos, viewModel::setScrapeWheelLogos)
    CardSwitchRow("Video Previews", state.scrapeVideos, viewModel::setScrapeVideos)

    Spacer(Modifier.height(12.dp))
    CardDivider()
    Spacer(Modifier.height(12.dp))

    Text(
        "Scrapes every game that hasn't been scraped yet, using ScreenScraper first and falling back to free sources.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(10.dp))
    GradientFillButton(
        text = "Scrape Now",
        onClick = onScrapeAllClick,
        modifier = Modifier.fillMaxWidth()
    )
}

// ── Section: Artwork Database ─────────────────────────────────────────────

@Composable
private fun ArtworkDatabaseBody(state: SettingsUiState, viewModel: SettingsViewModel) {
    Text(
        "LaunchBox DB — box art & screenshots. ~190 MB, one-time download. No account needed.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(10.dp))

    val lbSyncing = state.lbSyncStatus is LbSyncStatus.Downloading ||
            state.lbSyncStatus is LbSyncStatus.Parsing

    GradientFillButton(
        text = if (lbSyncing) "Syncing…" else "Sync Artwork DB",
        onClick = { viewModel.syncLaunchBox() },
        enabled = !lbSyncing,
        modifier = Modifier.fillMaxWidth(),
        loading = lbSyncing
    )

    when (val status = state.lbSyncStatus) {
        is LbSyncStatus.Downloading -> {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = ElectricBlue
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Downloading database…",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        is LbSyncStatus.Parsing -> {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = NeonPurple
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Parsing… ${"%,d".format(status.gamesIndexed)} games indexed",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        is LbSyncStatus.Complete -> {
            Spacer(Modifier.height(6.dp))
            StatusRow(
                icon = Icons.Default.Check,
                text = "Sync complete — ${"%,d".format(status.totalGames)} games",
                color = ElectricBlue
            )
        }

        is LbSyncStatus.Error -> {
            Spacer(Modifier.height(6.dp))
            StatusRow(
                icon = Icons.Default.Close,
                text = status.message,
                color = MaterialTheme.colorScheme.error
            )
        }

        null -> {
            if (state.lbGameCount > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "${"%,d".format(state.lbGameCount)} games in local database",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Section: RetroAchievements ────────────────────────────────────────────


@Composable
private fun MediaStorageSection(
    state: SettingsUiState,
    onPickFolder: () -> Unit,
    onUseDefault: () -> Unit
) {
    SettingsSectionHeader("Media Storage")
    SettingsCard {
        Text(
            "Choose where scraped box art, screenshots and videos are saved — for example your SD card. " +
                    "Optional: if you don't pick a folder, media is kept in the app's internal storage. " +
                    "If the folder already contains media (e.g. an ES-DE library), it's imported automatically.",
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
                text = state.mediaStoragePath.ifBlank { "Default — internal storage" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GradientFillButton(
                text = "Choose Folder",
                onClick = onPickFolder,
                modifier = Modifier.weight(1f)
            )
            if (state.mediaStoragePath.isNotBlank()) {
                GradientOutlineButton(
                    text = "Use Default",
                    onClick = onUseDefault,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Auto-import feedback for the chosen folder.
        when (val s = state.esdeImportStatus) {
            is EsdeImportStatus.Scanning -> {
                Spacer(Modifier.height(8.dp))
                LoadingStatusRow(
                    "Checking folder for existing media…",
                    MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            is EsdeImportStatus.Complete -> {
                Spacer(Modifier.height(8.dp))
                if (s.matched > 0) {
                    StatusRow(
                        Icons.Default.Check,
                        "Imported media for ${s.matched} game${if (s.matched == 1) "" else "s"}",
                        ElectricBlue
                    )
                } else {
                    StatusRow(
                        Icons.Default.Check,
                        "No existing media found — new scrapes will be saved here",
                        MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            is EsdeImportStatus.Error -> {
                Spacer(Modifier.height(8.dp))
                StatusRow(Icons.Default.Close, s.message, MaterialTheme.colorScheme.error)
            }

            else -> {}
        }
    }
}


// ── Screen ────────────────────────────────────────────────────────────────

@Composable
fun MediaSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel,
    onScrapeAllClick: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val mediaStoragePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            val path = StorageUtils.resolveTreeUriToPath(it) ?: it.toString()
            viewModel.chooseMediaStorageFolder(path)
        }
    }

    SettingsDetailScaffold(title = "Media & Artwork", onBack = onBack) {
        MediaStorageSection(
            state,
            onPickFolder = { mediaStoragePicker.launch(null) },
            onUseDefault = viewModel::clearMediaStoragePath
        )
        Spacer(Modifier.height(8.dp))
        // The two media sources are rarely used together, so they share one card with an inline
        // segmented selector instead of stacking.
        var mediaSub by rememberSaveable { mutableStateOf(0) }
        SegmentedTabs(
            options = listOf("ScreenScraper", "Artwork DB"),
            selected = mediaSub,
            onSelect = { mediaSub = it }
        )
        Spacer(Modifier.height(8.dp))
        SettingsCard {
            when (mediaSub) {
                0 -> ScreenScraperBody(state, viewModel, onScrapeAllClick)
                else -> ArtworkDatabaseBody(state, viewModel)
            }
        }
    }
}
