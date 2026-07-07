package com.gamelaunch.frontend.ui.screen.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gamelaunch.frontend.R
import com.gamelaunch.frontend.ui.component.platformDisplayName
import com.gamelaunch.frontend.ui.input.GamepadA
import com.gamelaunch.frontend.ui.input.GamepadB
import com.gamelaunch.frontend.ui.input.GamepadL1
import com.gamelaunch.frontend.ui.input.GamepadL2
import com.gamelaunch.frontend.ui.input.GamepadR1
import com.gamelaunch.frontend.ui.input.GamepadR2
import com.gamelaunch.frontend.ui.input.GamepadStart
import com.gamelaunch.frontend.ui.theme.AmbientBackground
import com.gamelaunch.frontend.ui.theme.BrandBlue
import com.gamelaunch.frontend.ui.theme.ElectricBlue
import com.gamelaunch.frontend.ui.theme.IceWhite
import com.gamelaunch.frontend.ui.theme.LightBg
import com.gamelaunch.frontend.ui.theme.LocalDarkMode
import com.gamelaunch.frontend.ui.theme.NavyBg
import com.gamelaunch.frontend.ui.theme.SteelGray
import com.gamelaunch.frontend.ui.theme.TileSub
import com.gamelaunch.frontend.ui.theme.TileText
import com.gamelaunch.frontend.ui.theme.glassChip
import com.gamelaunch.frontend.ui.theme.grid.GridHomeContent
import com.gamelaunch.frontend.ui.screen.retroachievements.RetroAchievementsScreen

@Composable
fun HomeScreen(
    onGameClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    appsViewModel: AppsViewModel = hiltViewModel()
) {
    val state     by viewModel.uiState.collectAsState()
    val appsState by appsViewModel.uiState.collectAsState()

    val darkMode      = LocalDarkMode.current
    val textPrimary   = if (darkMode) IceWhite else TileText
    val textSecondary = if (darkMode) SteelGray else TileSub
    val bgColor       = if (darkMode) NavyBg else LightBg

    // Controller focus indices for each grid. rememberSaveable so the selected console/game is
    // restored when returning from the detail screen (the composition is disposed on navigation).
    var systemFocusIndex by rememberSaveable { mutableIntStateOf(0) }
    var appFocusIndex    by rememberSaveable { mutableIntStateOf(0) }
    var gridFocusIndex   by rememberSaveable { mutableIntStateOf(0) }
    var recentFocusIndex by rememberSaveable { mutableIntStateOf(0) }
    // A screenful of games (whole visible rows × columns), reported by the grid, for L2/R2 paging.
    var gridPageSize     by remember { mutableIntStateOf(0) }

    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    // Match LazyVerticalGrid's column maths exactly so D-pad navigation lands on the right cell.
    fun gridColumns(minCellDp: Int, paddingDp: Int, spacingDp: Int) =
        maxOf(1, (screenWidthDp - 2 * paddingDp + spacingDp) / (minCellDp + spacingDp))
    val gameGridColumns = gridColumns(minCellDp = 110, paddingDp = 8, spacingDp = 8)
    val appColumns      = gridColumns(minCellDp = 96, paddingDp = 16, spacingDp = 12)

    // Keep focus in bounds when lists change
    LaunchedEffect(state.platforms.size) {
        if (state.platforms.isNotEmpty())
            systemFocusIndex = systemFocusIndex.coerceIn(0, state.platforms.size - 1)
    }
    LaunchedEffect(appsState.apps.size) {
        if (appsState.apps.isNotEmpty())
            appFocusIndex = appFocusIndex.coerceIn(0, appsState.apps.size - 1)
    }
    LaunchedEffect(state.games.size) {
        if (state.games.isNotEmpty())
            gridFocusIndex = gridFocusIndex.coerceAtMost(state.games.size - 1)
    }
    LaunchedEffect(state.recentlyPlayed.size) {
        if (state.recentlyPlayed.isNotEmpty())
            recentFocusIndex = recentFocusIndex.coerceAtMost(state.recentlyPlayed.size - 1)
    }

    fun cyclePlatform(delta: Int) {
        val idx  = state.platforms.indexOf(state.selectedPlatform)
        val next = state.platforms.getOrNull((idx + delta).coerceIn(0, state.platforms.size - 1))
        next?.let { viewModel.selectPlatform(it) }
    }

    // Recently Played and RetroAchievements tabs each appear only when enabled in settings.
    val visibleTabs = TopTab.entries.filter {
        (it != TopTab.RECENTLY_PLAYED  || state.showRecentlyPlayed) &&
        (it != TopTab.RETROACHIEVEMENTS || state.showRetroAchievements)
    }

    fun cycleTab(delta: Int) {
        val cur  = visibleTabs.indexOf(state.topTab).coerceAtLeast(0)
        val next = visibleTabs[(cur + delta + visibleTabs.size) % visibleTabs.size]
        viewModel.selectTopTab(next)
        if (next == TopTab.APPS) appsViewModel.refresh()
    }

    // ── Directional movement (shared by single presses and hold-to-scroll) ──
    // Applies one step in the given direction for whichever grid/carousel is active and
    // returns whether it was handled.
    fun moveDirection(key: Key): Boolean = when (state.topTab) {
        TopTab.GAMES -> if (!state.gameViewActive) {
            when (key) {
                Key.DirectionLeft  -> { systemFocusIndex = (systemFocusIndex - 1).coerceAtLeast(0); true }
                Key.DirectionRight -> { systemFocusIndex = (systemFocusIndex + 1).coerceAtMost(state.platforms.size - 1); true }
                else -> false
            }
        } else {
            when (key) {
                Key.DirectionLeft  -> { gridFocusIndex = (gridFocusIndex - 1).coerceAtLeast(0); true }
                Key.DirectionRight -> { gridFocusIndex = (gridFocusIndex + 1).coerceAtMost(state.games.size - 1); true }
                Key.DirectionUp    -> { (gridFocusIndex - gameGridColumns).let { if (it >= 0) gridFocusIndex = it }; true }
                Key.DirectionDown  -> { (gridFocusIndex + gameGridColumns).let { if (it < state.games.size) gridFocusIndex = it }; true }
                else -> false
            }
        }
        TopTab.RECENTLY_PLAYED -> {
            val n = state.recentlyPlayed.size
            when (key) {
                Key.DirectionLeft  -> { recentFocusIndex = (recentFocusIndex - 1).coerceAtLeast(0); true }
                Key.DirectionRight -> { recentFocusIndex = (recentFocusIndex + 1).coerceAtMost(n - 1); true }
                Key.DirectionUp    -> { (recentFocusIndex - gameGridColumns).let { if (it >= 0) recentFocusIndex = it }; true }
                Key.DirectionDown  -> { (recentFocusIndex + gameGridColumns).let { if (it < n) recentFocusIndex = it }; true }
                else -> false
            }
        }
        TopTab.APPS -> {
            val n = appsState.apps.size
            when (key) {
                Key.DirectionLeft  -> { appFocusIndex = (appFocusIndex - 1).coerceAtLeast(0); true }
                Key.DirectionRight -> { appFocusIndex = (appFocusIndex + 1).coerceAtMost(n - 1); true }
                Key.DirectionUp    -> { (appFocusIndex - appColumns).let { if (it >= 0) appFocusIndex = it }; true }
                Key.DirectionDown  -> { (appFocusIndex + appColumns).let { if (it < n) appFocusIndex = it }; true }
                else -> false
            }
        }
        TopTab.RETROACHIEVEMENTS -> false
    }

    // Hold a direction to keep moving: first press fires immediately, then after a short delay
    // the move repeats until the key is released.
    val scope = rememberCoroutineScope()
    var heldDirection by remember { mutableStateOf<Key?>(null) }
    var repeatJob by remember { mutableStateOf<Job?>(null) }
    fun stopRepeat() { repeatJob?.cancel(); repeatJob = null; heldDirection = null }
    fun startHold(key: Key) {
        heldDirection = key
        repeatJob?.cancel()
        repeatJob = scope.launch {
            delay(320)                       // hold threshold before auto-repeat begins
            while (isActive) {
                if (!moveDirection(key)) break  // stop at the end of a list
                delay(75)                    // ~13 steps/sec while held
            }
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { try { focusRequester.requestFocus() } catch (_: Exception) {} }

    Scaffold(containerColor = bgColor) { _ ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { event ->
                    val key = event.key
                    val isDirection = key == Key.DirectionLeft || key == Key.DirectionRight ||
                                      key == Key.DirectionUp || key == Key.DirectionDown

                    // Release of a held direction stops auto-repeat.
                    if (event.type == KeyEventType.KeyUp) {
                        if (isDirection && key == heldDirection) { stopRepeat(); return@onKeyEvent true }
                        return@onKeyEvent false
                    }
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false

                    // Directional input: ignore the OS's own auto-repeat while we already drive a
                    // hold; on first press move once and start our own steady repeat.
                    if (isDirection) {
                        if (key == heldDirection) return@onKeyEvent true
                        val handled = moveDirection(key)
                        if (handled) startHold(key)
                        return@onKeyEvent handled
                    }

                    // ── Global shortcuts ──────────────────────────────────
                    // Bumpers (LB/RB): at the top level they move between the top tabs;
                    // inside a system they cycle systems (handled in the game-view block below).
                    val inGameView = state.topTab == TopTab.GAMES && state.gameViewActive
                    when (key) {
                        GamepadL1 -> if (!inGameView) { cycleTab(-1); return@onKeyEvent true }
                        GamepadR1 -> if (!inGameView) { cycleTab(+1); return@onKeyEvent true }
                        GamepadStart -> { onSettingsClick(); return@onKeyEvent true }
                    }

                    when (state.topTab) {
                        // ══ GAMES ════════════════════════════════════════
                        TopTab.GAMES -> if (!state.gameViewActive) {
                            when (key) {
                                GamepadA, Key.DirectionCenter, Key.Enter -> {
                                    state.platforms.getOrNull(systemFocusIndex)?.let {
                                        gridFocusIndex = 0
                                        viewModel.enterSystem(it)
                                    }; true
                                }
                                else -> false
                            }
                        } else {
                            when (key) {
                                GamepadA, Key.DirectionCenter, Key.Enter -> {
                                    state.games.getOrNull(gridFocusIndex)?.let { onGameClick(it.id) }; true
                                }
                                GamepadL1 -> { cyclePlatform(-1); true }
                                GamepadR1 -> { cyclePlatform(+1); true }
                                // L2/R2 jump the selection a full page (screenful of rows) at a time.
                                GamepadL2 -> {
                                    val page = gridPageSize.takeIf { it > 0 } ?: gameGridColumns
                                    gridFocusIndex = (gridFocusIndex - page).coerceAtLeast(0); true
                                }
                                GamepadR2 -> {
                                    val page = gridPageSize.takeIf { it > 0 } ?: gameGridColumns
                                    gridFocusIndex = (gridFocusIndex + page).coerceAtMost(state.games.size - 1); true
                                }
                                GamepadB, Key.Back -> { viewModel.exitToSystems(); true }
                                else -> false
                            }
                        }

                        // ══ RECENTLY PLAYED ══════════════════════════════
                        TopTab.RECENTLY_PLAYED -> when (key) {
                            GamepadA, Key.DirectionCenter, Key.Enter -> {
                                state.recentlyPlayed.getOrNull(recentFocusIndex)?.let { onGameClick(it.id) }; true
                            }
                            else -> false
                        }

                        // ══ APPS ═════════════════════════════════════════
                        TopTab.APPS -> when (key) {
                            GamepadA, Key.DirectionCenter, Key.Enter -> {
                                appsState.apps.getOrNull(appFocusIndex)?.let { appsViewModel.launchApp(it.packageName) }; true
                            }
                            else -> false
                        }

                        // ══ RETROACHIEVEMENTS ════════════════════════════
                        TopTab.RETROACHIEVEMENTS -> false
                    }
                }
        ) {
            AmbientBackground(
                Modifier.fillMaxSize(),
                // Blur & fade the branded pattern once you're inside a system browsing games.
                patternSubdued = state.topTab == TopTab.GAMES && state.gameViewActive
            ) {
            Column(Modifier.fillMaxSize()) {

                // ── Header + mode tabs ─────────────────────────────────
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(
                                start = 16.dp, end = 16.dp, top = 16.dp,
                                // Inside a system there's no tab bar under the header, so give the
                                // settings button / breadcrumb more breathing room above the grid.
                                bottom = if (state.topTab == TopTab.GAMES && state.gameViewActive) 16.dp else 6.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_donkey_silhouette),
                            contentDescription = null,
                            tint = BrandBlue,
                            modifier = Modifier.size(26.dp).padding(end = 6.dp)
                        )
                        Text("e",  fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = BrandBlue, letterSpacing = 2.sp)
                        Text("Or", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = textPrimary, letterSpacing = 2.sp)

                        // Inside a system, breadcrumb: eOr · <Console> → <hovered game>
                        if (state.topTab == TopTab.GAMES && state.gameViewActive) {
                            state.selectedPlatform?.let { pid ->
                                Text(
                                    "  ·  " + platformDisplayName(pid),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = textPrimary.copy(alpha = 0.85f),
                                    maxLines = 1
                                )
                            }
                            val hoveredGame = state.games.getOrNull(gridFocusIndex)
                            if (hoveredGame != null) {
                                Text(
                                    "  →  " + hoveredGame.title,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = BrandBlue,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).padding(start = 2.dp)
                                )
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                        } else {
                            Spacer(Modifier.weight(1f))
                        }

                        IconButton(
                            onClick  = onSettingsClick,
                            modifier = Modifier.size(40.dp).glassChip(CircleShape)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = textPrimary, modifier = Modifier.size(20.dp))
                        }
                    }

                    // No tabs while inside a system — just the games (swap systems with L1/R1).
                    if (!(state.topTab == TopTab.GAMES && state.gameViewActive)) {
                        ModeTabBar(
                            selected = state.topTab,
                            showRecentlyPlayed = state.showRecentlyPlayed,
                            showRetroAchievements = state.showRetroAchievements,
                            onSelect = { tab ->
                                viewModel.selectTopTab(tab)
                                if (tab == TopTab.APPS) appsViewModel.refresh()
                            }
                        )
                    }
                }

                // ── Content ────────────────────────────────────────────
                Box(Modifier.weight(1f).fillMaxSize()) {
                    when {
                        state.isLoading && state.topTab == TopTab.GAMES ->
                            CircularProgressIndicator(Modifier.align(Alignment.Center), color = ElectricBlue)

                        state.topTab == TopTab.GAMES && !state.gameViewActive ->
                            SystemSelectionContent(
                                platforms       = state.platforms,
                                counts          = state.platformCounts,
                                focusedIndex    = systemFocusIndex,
                                previewArt      = state.systemPreviewArt,
                                onSystemFocused = viewModel::focusSystem,
                                onSystemClick   = { gridFocusIndex = 0; viewModel.enterSystem(it) },
                                modifier        = Modifier.fillMaxSize()
                            )

                        state.topTab == TopTab.GAMES -> GridHomeContent(
                            games            = state.games,
                            onGameClick      = onGameClick,
                            columns          = gameGridColumns,
                            mediaForGames    = state.mediaForGames,
                            focusedGameIndex = gridFocusIndex,
                            onPageSizeChange = { gridPageSize = it },
                            modifier         = Modifier.fillMaxSize()
                        )

                        state.topTab == TopTab.RECENTLY_PLAYED ->
                            if (state.recentlyPlayed.isEmpty()) {
                                EmptyState(
                                    icon     = Icons.Default.History,
                                    title    = "No recent games",
                                    subtitle = "Games you launch will appear here",
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                GridHomeContent(
                                    games            = state.recentlyPlayed,
                                    onGameClick      = onGameClick,
                                    columns          = gameGridColumns,
                                    mediaForGames    = state.mediaForGames,
                                    focusedGameIndex = recentFocusIndex,
                                    modifier         = Modifier.fillMaxSize()
                                )
                            }

                        state.topTab == TopTab.APPS ->
                            AppsContent(
                                apps                 = appsState.apps,
                                isLoading            = appsState.isLoading,
                                focusedIndex         = appFocusIndex,
                                columns              = appColumns,
                                packageManagerHelper = appsViewModel.packageManagerHelper,
                                onAppClick           = appsViewModel::launchApp,
                                modifier             = Modifier.fillMaxSize()
                            )

                        else -> RetroAchievementsScreen(
                            onGoToSettings = onSettingsClick,
                            modifier       = Modifier.fillMaxSize()
                        )
                    }
                }
            }
            }
        }
    }
}

private data class TabSpec(val tab: TopTab, val label: String, val icon: ImageVector)

private val tabSpecs = listOf(
    TabSpec(TopTab.GAMES, "Games", Icons.Default.SportsEsports),
    TabSpec(TopTab.RECENTLY_PLAYED, "Recent", Icons.Default.History),
    TabSpec(TopTab.APPS, "Apps", Icons.Default.Apps),
    TabSpec(TopTab.RETROACHIEVEMENTS, "RetroAchievements", Icons.Default.EmojiEvents)
)

@Composable
private fun ModeTabBar(
    selected: TopTab,
    showRecentlyPlayed: Boolean,
    showRetroAchievements: Boolean,
    onSelect: (TopTab) -> Unit
) {
    val pill = RoundedCornerShape(50)
    val tabs = tabSpecs.filter {
        (it.tab != TopTab.RECENTLY_PLAYED  || showRecentlyPlayed) &&
        (it.tab != TopTab.RETROACHIEVEMENTS || showRetroAchievements)
    }
    val darkMode = LocalDarkMode.current
    val unselectedTint = if (darkMode) IceWhite.copy(alpha = 0.8f) else TileText.copy(alpha = 0.8f)

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { spec ->
            val isSel = spec.tab == selected
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .glassChip(pill, selected = isSel)
                    .clickable { onSelect(spec.tab) }
                    .padding(horizontal = 16.dp, vertical = 9.dp)
            ) {
                Icon(
                    spec.icon,
                    contentDescription = null,
                    tint = if (isSel) Color.White else unselectedTint,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = spec.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSel) Color.White else unselectedTint
                )
            }
        }
    }
}

@Composable
private fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    val darkMode = LocalDarkMode.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(64.dp))
        Spacer(Modifier.size(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = if (darkMode) IceWhite else TileText)
        Spacer(Modifier.size(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = if (darkMode) SteelGray else TileSub)
    }
}

