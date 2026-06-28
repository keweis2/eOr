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
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gamelaunch.frontend.ui.component.platformDisplayName
import com.gamelaunch.frontend.ui.input.GamepadA
import com.gamelaunch.frontend.ui.input.GamepadB
import com.gamelaunch.frontend.ui.input.GamepadL1
import com.gamelaunch.frontend.ui.input.GamepadR1
import com.gamelaunch.frontend.ui.input.GamepadStart
import com.gamelaunch.frontend.ui.theme.AmbientBackground
import com.gamelaunch.frontend.ui.theme.BrandBlue
import com.gamelaunch.frontend.ui.theme.ElectricBlue
import com.gamelaunch.frontend.ui.theme.LightBg
import com.gamelaunch.frontend.ui.theme.TileSub
import com.gamelaunch.frontend.ui.theme.TileText
import com.gamelaunch.frontend.ui.theme.glassChip
import com.gamelaunch.frontend.ui.theme.grid.GridHomeContent

@Composable
fun HomeScreen(
    onGameClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    appsViewModel: AppsViewModel = hiltViewModel()
) {
    val state     by viewModel.uiState.collectAsState()
    val appsState by appsViewModel.uiState.collectAsState()

    // Controller focus indices for each grid
    var systemFocusIndex by remember { mutableIntStateOf(0) }
    var appFocusIndex    by remember { mutableIntStateOf(0) }
    var gridFocusIndex   by remember { mutableIntStateOf(0) }

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

    fun cyclePlatform(delta: Int) {
        val idx  = state.platforms.indexOf(state.selectedPlatform)
        val next = state.platforms.getOrNull((idx + delta).coerceIn(0, state.platforms.size - 1))
        next?.let { viewModel.selectPlatform(it) }
    }

    fun cycleTab(delta: Int) {
        val tabs = TopTab.entries
        val next = tabs[(state.topTab.ordinal + delta + tabs.size) % tabs.size]
        viewModel.selectTopTab(next)
        if (next == TopTab.APPS) appsViewModel.refresh()
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { try { focusRequester.requestFocus() } catch (_: Exception) {} }

    Scaffold(containerColor = LightBg) { _ ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false

                    // ── Global shortcuts ──────────────────────────────────
                    // Bumpers (LB/RB): at the top level they move between the top tabs;
                    // inside a system they cycle systems (handled in the game-view block below).
                    val inGameView = state.topTab == TopTab.GAMES && state.gameViewActive
                    when (event.key) {
                        GamepadL1 -> if (!inGameView) { cycleTab(-1); return@onKeyEvent true }
                        GamepadR1 -> if (!inGameView) { cycleTab(+1); return@onKeyEvent true }
                        GamepadStart -> { onSettingsClick(); return@onKeyEvent true }
                    }

                    when (state.topTab) {
                        // ══ GAMES ════════════════════════════════════════
                        TopTab.GAMES -> if (!state.gameViewActive) {
                            // System carousel — left/right only
                            when (event.key) {
                                Key.DirectionLeft  -> { systemFocusIndex = (systemFocusIndex - 1).coerceAtLeast(0); true }
                                Key.DirectionRight -> { systemFocusIndex = (systemFocusIndex + 1).coerceAtMost(state.platforms.size - 1); true }
                                GamepadA, Key.DirectionCenter, Key.Enter -> {
                                    state.platforms.getOrNull(systemFocusIndex)?.let {
                                        gridFocusIndex = 0
                                        viewModel.enterSystem(it)
                                    }; true
                                }
                                else -> false
                            }
                        } else {
                            // Game grid — 2D navigation
                            when (event.key) {
                                Key.DirectionLeft  -> { gridFocusIndex = (gridFocusIndex - 1).coerceAtLeast(0); true }
                                Key.DirectionRight -> { gridFocusIndex = (gridFocusIndex + 1).coerceAtMost(state.games.size - 1); true }
                                Key.DirectionUp    -> { (gridFocusIndex - gameGridColumns).let { if (it >= 0) gridFocusIndex = it }; true }
                                Key.DirectionDown  -> { (gridFocusIndex + gameGridColumns).let { if (it < state.games.size) gridFocusIndex = it }; true }
                                GamepadA, Key.DirectionCenter, Key.Enter -> {
                                    state.games.getOrNull(gridFocusIndex)?.let { onGameClick(it.id) }; true
                                }
                                GamepadL1 -> { cyclePlatform(-1); true }
                                GamepadR1 -> { cyclePlatform(+1); true }
                                GamepadB -> { viewModel.exitToSystems(); true }
                                else -> false
                            }
                        }

                        // ══ APPS ═════════════════════════════════════════
                        TopTab.APPS -> when (event.key) {
                            Key.DirectionLeft  -> { appFocusIndex = (appFocusIndex - 1).coerceAtLeast(0); true }
                            Key.DirectionRight -> { appFocusIndex = (appFocusIndex + 1).coerceAtMost(appsState.apps.size - 1); true }
                            Key.DirectionUp    -> { (appFocusIndex - appColumns).let { if (it >= 0) appFocusIndex = it }; true }
                            Key.DirectionDown  -> { (appFocusIndex + appColumns).let { if (it < appsState.apps.size) appFocusIndex = it }; true }
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
            AmbientBackground(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {

                // ── Header + mode tabs ─────────────────────────────────
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("e",  fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = BrandBlue, letterSpacing = 2.sp)
                        Text("Or", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = TileText, letterSpacing = 2.sp)

                        // Inside a system, show which one (the tabs/pills are hidden here)
                        if (state.topTab == TopTab.GAMES && state.gameViewActive) {
                            state.selectedPlatform?.let { pid ->
                                Text(
                                    "  ·  " + platformDisplayName(pid),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TileText.copy(alpha = 0.85f)
                                )
                            }
                        }

                        Spacer(Modifier.weight(1f))

                        IconButton(
                            onClick  = onSettingsClick,
                            modifier = Modifier.size(40.dp).glassChip(CircleShape)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TileText, modifier = Modifier.size(20.dp))
                        }
                    }

                    // No tabs while inside a system — just the games (swap systems with L1/R1).
                    if (!(state.topTab == TopTab.GAMES && state.gameViewActive)) {
                        ModeTabBar(
                            selected = state.topTab,
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
                            modifier         = Modifier.fillMaxSize()
                        )

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

                        else -> RetroAchievementsPlaceholder(Modifier.fillMaxSize())
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun ModeTabBar(selected: TopTab, onSelect: (TopTab) -> Unit) {
    val tabs = listOf(
        TopTab.GAMES to "Games",
        TopTab.APPS to "Apps",
        TopTab.RETROACHIEVEMENTS to "RetroAchievements"
    )
    val pill = RoundedCornerShape(50)

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { (tab, label) ->
            val isSel = tab == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .glassChip(pill, selected = isSel)
                    .clickable { onSelect(tab) }
                    .padding(horizontal = 18.dp, vertical = 9.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSel) Color.White else TileText.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun RetroAchievementsPlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.EmojiEvents,
            contentDescription = null,
            tint = BrandBlue,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.size(12.dp))
        Text("RetroAchievements", style = MaterialTheme.typography.titleMedium, color = TileText)
        Spacer(Modifier.size(4.dp))
        Text("Coming soon", style = MaterialTheme.typography.bodyMedium, color = TileSub)
    }
}
