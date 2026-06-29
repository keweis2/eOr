package com.gamelaunch.frontend.ui.screen.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gamelaunch.frontend.domain.usecase.EsdeImportStatus
import com.gamelaunch.frontend.domain.usecase.LbSyncStatus
import com.gamelaunch.frontend.ui.input.GamepadL1
import com.gamelaunch.frontend.ui.input.GamepadR1
import com.gamelaunch.frontend.ui.theme.ElectricBlue
import com.gamelaunch.frontend.ui.theme.GameColorScheme
import com.gamelaunch.frontend.ui.theme.GameLightColorScheme
import com.gamelaunch.frontend.ui.theme.LayoutMode
import com.gamelaunch.frontend.ui.theme.LocalDarkMode
import com.gamelaunch.frontend.ui.theme.NeonPurple
import com.gamelaunch.frontend.util.StorageUtils

private val gradientBrush = Brush.horizontalGradient(listOf(ElectricBlue, NeonPurple))

private enum class SettingsTab(val label: String, val icon: ImageVector) {
    GENERAL("General", Icons.Default.Tune),
    MEDIA("Media", Icons.Default.PermMedia),
    GAMES("Games", Icons.Default.VideogameAsset),
    RETRO_ACHIEVEMENTS("RetroAchievements", Icons.Default.EmojiEvents)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: (() -> Unit)?,
    onGoToLibrary: () -> Unit,
    onEmulatorConfigClick: () -> Unit,
    onScrapeAllClick: () -> Unit,
    onRescanClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state   by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val storageVolumes = remember { StorageUtils.getStorageVolumes(context) }

    var selectedTab by rememberSaveable { mutableStateOf(SettingsTab.GENERAL) }

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

    val mediaFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            val path = StorageUtils.resolveTreeUriToPath(it) ?: it.toString()
            viewModel.setMediaFolderPath(path)
        }
    }

    // Honour the user's light/dark choice locally — the rest of the app keeps the dark
    // MaterialTheme and reads LocalDarkMode for its own colours, so we override only here.
    val settingsScheme = if (LocalDarkMode.current) GameColorScheme else GameLightColorScheme

    // L1 / R1 cycle between tabs (with wraparound), mirroring the home screen.
    fun cycleTab(delta: Int) {
        val entries = SettingsTab.entries
        val cur = entries.indexOf(selectedTab)
        selectedTab = entries[(cur + delta + entries.size) % entries.size]
    }

    val tabFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { tabFocusRequester.requestFocus() } }

    MaterialTheme(colorScheme = settingsScheme) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(tabFocusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    GamepadL1 -> { cycleTab(-1); true }
                    GamepadR1 -> { cycleTab(+1); true }
                    else -> false
                }
            }
    ) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data -> Snackbar(snackbarData = data) }
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        style    = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(
                            onClick  = onBack,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                actions = {
                    // Gradient "Go to Library" button — also persists any typed ScreenScraper creds.
                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clip(RoundedCornerShape(50))
                            .background(gradientBrush)
                            .clickable {
                                viewModel.saveCredentials()
                                viewModel.finishSetup()
                                onGoToLibrary()
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Library",
                                style  = MaterialTheme.typography.labelLarge,
                                color  = Color.White
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint     = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(Modifier.fillMaxSize().padding(paddingValues)) {

            SettingsTabBar(selected = selectedTab, onSelect = { selectedTab = it })

            // Fresh scroll state per tab so switching tabs starts at the top.
            key(selectedTab) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (selectedTab) {
                        SettingsTab.GENERAL -> {
                            DisplaySection(state, viewModel)
                        }
                        SettingsTab.MEDIA -> {
                            ScreenScraperSection(state, viewModel, onScrapeAllClick)
                            Spacer(Modifier.height(4.dp))
                            ArtworkDatabaseSection(state, viewModel)
                            Spacer(Modifier.height(4.dp))
                            MediaImportSection(state, viewModel, onPickMediaFolder = { mediaFolderPicker.launch(null) })
                        }
                        SettingsTab.GAMES -> {
                            RomLibrarySection(
                                state, viewModel, storageVolumes,
                                onPickRomFolder = { folderPicker.launch(null) },
                                onRescanClick = onRescanClick,
                                onScrapeAllClick = onScrapeAllClick
                            )
                            Spacer(Modifier.height(4.dp))
                            AndroidGamesSection(state, viewModel)
                            Spacer(Modifier.height(4.dp))
                            EmulatorsSection(state, viewModel, onEmulatorConfigClick)
                        }
                        SettingsTab.RETRO_ACHIEVEMENTS -> RetroAchievementsSection(state, viewModel)
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
    }
    }
}

@Composable
private fun SettingsTabBar(selected: SettingsTab, onSelect: (SettingsTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsTab.entries.forEach { tab ->
            val isSel = tab == selected
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .then(
                        if (isSel) Modifier.background(gradientBrush)
                        else Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    .clickable { onSelect(tab) }
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Icon(
                    tab.icon,
                    contentDescription = null,
                    tint = if (isSel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    tab.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

// ── Section: Display ──────────────────────────────────────────────────────

@Composable
private fun DisplaySection(state: SettingsUiState, viewModel: SettingsViewModel) {
    SettingsSectionHeader("Display")
    SettingsCard {
        CardSwitchRow(
            label     = "Carousel layout",
            checked   = state.layoutMode == LayoutMode.CAROUSEL,
            onCheckedChange = {
                viewModel.setLayoutMode(if (it) LayoutMode.CAROUSEL else LayoutMode.GRID)
            }
        )
        CardSwitchRow(
            label           = "Recently Played tab",
            checked         = state.showRecentlyPlayed,
            onCheckedChange = viewModel::setShowRecentlyPlayed
        )
        CardSwitchRow(
            label           = "Dark mode",
            checked         = state.darkMode,
            onCheckedChange = viewModel::setDarkMode
        )
    }
}

// ── Section: ROM Library ──────────────────────────────────────────────────

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
                            .clickable { viewModel.setRomRootPath(path) }
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
                value       = state.romRootPath.ifEmpty { "Not configured" },
                onValueChange = { viewModel.setRomRootPath(it) },
                label       = { Text("ROM Folder") },
                modifier    = Modifier.weight(1f),
                singleLine  = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = ElectricBlue,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick  = onPickRomFolder,
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
                text     = "Rescan ROMs",
                onClick  = onRescanClick,
                modifier = Modifier.weight(1f)
            )
            GradientFillButton(
                text     = "Scrape All",
                onClick  = onScrapeAllClick,
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
            text     = "Scan Android Games",
            onClick  = { viewModel.scanAndroidGames() },
            modifier = Modifier.fillMaxWidth()
        )
        state.androidScanResult?.let { result ->
            Spacer(Modifier.height(6.dp))
            StatusRow(
                icon  = Icons.Default.Check,
                text  = result,
                color = ElectricBlue
            )
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
            text     = "Auto-detect Emulators",
            onClick  = { viewModel.autoDetectEmulators() },
            enabled  = !state.emulatorDetecting,
            loading  = state.emulatorDetecting,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        GradientOutlineButton(
            text     = "Configure Emulators Manually",
            onClick  = onEmulatorConfigClick,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ── Section: ScreenScraper ────────────────────────────────────────────────

@Composable
private fun ScreenScraperSection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    onScrapeAllClick: () -> Unit
) {
    SettingsSectionHeader("ScreenScraper (Recommended)")
    SettingsCard {
        Text(
            "ScreenScraper is the default scraper — it provides the best box art, screenshots, wheel logos, and video previews. " +
            "Create a free account at screenscraper.fr, then enter your username and password below. " +
            "Without credentials the app falls back to libretro thumbnails and LaunchBox.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value         = state.ssId,
            onValueChange = viewModel::updateSsId,
            label         = { Text("Username (ssid)") },
            modifier      = Modifier.fillMaxWidth(),
            singleLine    = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = ElectricBlue,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value                  = state.ssPassword,
            onValueChange          = viewModel::updateSsPassword,
            label                  = { Text("Password") },
            visualTransformation   = PasswordVisualTransformation(),
            modifier               = Modifier.fillMaxWidth(),
            singleLine             = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = ElectricBlue,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GradientOutlineButton(
                text    = "Save",
                onClick = { viewModel.saveCredentials() },
                modifier = Modifier.weight(1f)
            )
            GradientFillButton(
                text     = "Validate",
                onClick  = { viewModel.validateCredentials() },
                enabled  = !state.credentialValidating,
                loading  = state.credentialValidating,
                modifier = Modifier.weight(1f)
            )
        }
        state.credentialValid?.let { valid ->
            Spacer(Modifier.height(8.dp))
            StatusRow(
                icon  = if (valid) Icons.Default.Check else Icons.Default.Close,
                text  = if (valid) "Credentials valid" else "Invalid credentials",
                color = if (valid) ElectricBlue else MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.height(12.dp))
        CardDivider()
        Spacer(Modifier.height(12.dp))

        Text(
            "Scrape options",
            style      = MaterialTheme.typography.labelLarge,
            color      = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        CardSwitchRow("Metadata",       state.scrapeMetadata,      viewModel::setScrapeMetadata)
        CardSwitchRow("Box Art",        state.scrapeBoxArt,        viewModel::setScrapeBoxArt)
        CardSwitchRow("Screenshots",    state.scrapeScreenshots,   viewModel::setScrapeScreenshots)
        CardSwitchRow("Wheel Logos",    state.scrapeWheelLogos,    viewModel::setScrapeWheelLogos)
        CardSwitchRow("Video Previews", state.scrapeVideos,        viewModel::setScrapeVideos)

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
            text     = "Scrape Now",
            onClick  = onScrapeAllClick,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ── Section: Artwork Database ─────────────────────────────────────────────

@Composable
private fun ArtworkDatabaseSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    SettingsSectionHeader("Artwork Database")
    SettingsCard {
        Text(
            "LaunchBox DB — box art & screenshots. ~190 MB, one-time download. No account needed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))

        val lbSyncing = state.lbSyncStatus is LbSyncStatus.Downloading ||
                        state.lbSyncStatus is LbSyncStatus.Parsing

        GradientFillButton(
            text     = if (lbSyncing) "Syncing…" else "Sync Artwork DB",
            onClick  = { viewModel.syncLaunchBox() },
            enabled  = !lbSyncing,
            modifier = Modifier.fillMaxWidth(),
            loading  = lbSyncing
        )

        when (val status = state.lbSyncStatus) {
            is LbSyncStatus.Downloading -> {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color    = ElectricBlue
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
                    color    = NeonPurple
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
                    icon  = Icons.Default.Check,
                    text  = "Sync complete — ${"%,d".format(status.totalGames)} games",
                    color = ElectricBlue
                )
            }
            is LbSyncStatus.Error -> {
                Spacer(Modifier.height(6.dp))
                StatusRow(
                    icon  = Icons.Default.Close,
                    text  = status.message,
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
}

// ── Section: Media Import ─────────────────────────────────────────────────

@Composable
private fun MediaImportSection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    onPickMediaFolder: () -> Unit
) {
    SettingsSectionHeader("Media Import")
    SettingsCard {
        Text(
            "Point to your ES-DE downloaded_media folder to use art & videos you already scraped.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value         = state.mediaFolderPath.ifEmpty { "Not configured" },
                onValueChange = { viewModel.setMediaFolderPath(it) },
                label         = { Text("Media Folder") },
                modifier      = Modifier.weight(1f),
                singleLine    = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = ElectricBlue,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick  = onPickMediaFolder,
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = "Browse", tint = ElectricBlue)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Tip: pick the downloaded_media folder or its parent ES-DE folder.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))

        val importing = state.esdeImportStatus is EsdeImportStatus.Scanning
        GradientFillButton(
            text     = if (importing) "Importing…" else "Import Media",
            onClick  = { viewModel.importEsdeMedia() },
            enabled  = state.mediaFolderPath.isNotEmpty() && !importing,
            loading  = importing,
            modifier = Modifier.fillMaxWidth()
        )

        when (val s = state.esdeImportStatus) {
            is EsdeImportStatus.Complete -> {
                Spacer(Modifier.height(6.dp))
                StatusRow(
                    icon  = Icons.Default.Check,
                    text  = "Imported ${s.matched} of ${s.total} games",
                    color = ElectricBlue
                )
            }
            is EsdeImportStatus.Error -> {
                Spacer(Modifier.height(6.dp))
                StatusRow(
                    icon  = Icons.Default.Close,
                    text  = s.message,
                    color = MaterialTheme.colorScheme.error
                )
            }
            else -> {}
        }
    }
}

// ── Section: RetroAchievements ────────────────────────────────────────────

@Composable
private fun RetroAchievementsSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    SettingsSectionHeader("RetroAchievements")
    SettingsCard {
        Text(
            "Sign in with your RetroAchievements username and password to see your points and profile.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value         = state.raUsername,
            onValueChange = viewModel::updateRaUsername,
            label         = { Text("Username") },
            modifier      = Modifier.fillMaxWidth(),
            singleLine    = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = ElectricBlue,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value                = state.raPassword,
            onValueChange        = viewModel::updateRaPassword,
            label                = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier             = Modifier.fillMaxWidth(),
            singleLine           = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = ElectricBlue,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
        Spacer(Modifier.height(10.dp))
        GradientFillButton(
            text     = if (state.raLoggedIn) "Update Sign-In" else "Sign In",
            onClick  = { viewModel.saveRaCredentials() },
            enabled  = !state.raLoggingIn,
            loading  = state.raLoggingIn,
            modifier = Modifier.fillMaxWidth()
        )
        state.raLoginResult?.let { msg ->
            val ok = msg.startsWith("Signed in")
            Spacer(Modifier.height(8.dp))
            StatusRow(
                icon  = if (ok) Icons.Default.Check else Icons.Default.Close,
                text  = msg,
                color = if (ok) ElectricBlue else MaterialTheme.colorScheme.error
            )
        }
        if (state.raLoggedIn) {
            Spacer(Modifier.height(6.dp))
            GradientOutlineButton(
                text     = "Sign Out",
                onClick  = { viewModel.signOutRa() },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(12.dp))
        CardDivider()
        Spacer(Modifier.height(12.dp))

        Text(
            "Optional: add a Web API Key (retroachievements.org → Settings → Keys) to also see your rank and recently-played games with completion progress.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value                = state.raApiKey,
            onValueChange        = viewModel::updateRaApiKey,
            label                = { Text("Web API Key (optional)") },
            visualTransformation = PasswordVisualTransformation(),
            modifier             = Modifier.fillMaxWidth(),
            singleLine           = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = ElectricBlue,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
    }
}

// ── Reusable design system components ─────────────────────────────────────

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text     = title,
        style    = MaterialTheme.typography.labelLarge,
        color    = ElectricBlue,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
private fun CardDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), thickness = 0.5.dp)
}

@Composable
private fun CardSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment   = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked         = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor       = Color.White,
                checkedTrackColor       = ElectricBlue,
                uncheckedThumbColor     = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor     = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@Composable
private fun GradientFillButton(
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
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color    = Color.White,
                strokeWidth = 2.dp
            )
        } else {
            Text(text, style = MaterialTheme.typography.labelLarge, color = Color.White)
        }
    }
}

@Composable
private fun GradientOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text  = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatusRow(
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
