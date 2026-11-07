package com.gamelaunch.frontend.ui.screen.home

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gamelaunch.frontend.domain.lockedmode.LockedModeState
import com.gamelaunch.frontend.domain.lockedmode.PinResult
import com.gamelaunch.frontend.domain.lockedmode.message
import com.gamelaunch.frontend.ui.dualscreen.LocalDualScreenActive
import com.gamelaunch.frontend.ui.input.GamepadA
import com.gamelaunch.frontend.ui.input.GamepadB
import com.gamelaunch.frontend.ui.input.GamepadL1
import com.gamelaunch.frontend.ui.input.GamepadL2
import com.gamelaunch.frontend.ui.input.GamepadR1
import com.gamelaunch.frontend.ui.input.GamepadR2
import com.gamelaunch.frontend.ui.input.GamepadSelect
import com.gamelaunch.frontend.ui.input.GamepadStart
import com.gamelaunch.frontend.ui.theme.LayoutMode
import com.gamelaunch.frontend.ui.lockedmode.LockedModeViewModel

// Carousel has no grid to report a screenful, so bumper page-jumps there step a fixed stride.
private const val CAROUSEL_PAGE = 10

@OptIn(kotlinx.coroutines.FlowPreview::class)
@Composable
fun HomeRoute(
    onGameClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    onScrapeSystem: (String) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    appsViewModel: AppsViewModel = hiltViewModel(),
    lockedModeViewModel: LockedModeViewModel = hiltViewModel(),
    onStateObserved: ((HomeUiState) -> Unit)? = null
) {
    val state     by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnGameClick by rememberUpdatedState(onGameClick)
    LaunchedEffect(viewModel) {
        viewModel.fanTouchActions.collect { action ->
            val game = viewModel.selectFan(action.platformId, action.index)
            if (game != null) when (action.action) {
                com.gamelaunch.frontend.ui.dualscreen.FanAction.SELECT -> Unit
                com.gamelaunch.frontend.ui.dualscreen.FanAction.LAUNCH -> viewModel.launchGame(game.id)
                com.gamelaunch.frontend.ui.dualscreen.FanAction.DETAILS -> currentOnGameClick(game.id)
            }
        }
    }
    val appsState by appsViewModel.uiState.collectAsStateWithLifecycle()
    val lockedModeUiState by lockedModeViewModel.uiState.collectAsStateWithLifecycle()
    val lockedModeState = lockedModeUiState.state
    val lockedModeHasPin = lockedModeUiState.hasPin
    val isLocked = lockedModeState == LockedModeState.LOCKED
    val scope = rememberCoroutineScope()
    val ui = rememberHomeScreenState()
    with(ui) {
        val focusedGame = state.focusedGame(gridFocusIndex, favFocusIndex, recentFocusIndex)
        LaunchedEffect(state, focusedGame?.id, onStateObserved) {
            onStateObserved?.invoke(state.copy(focusedGameId = focusedGame?.id))
        }

        // Move to AppNavGraph if unlocking later becomes a route or app-wide overlay.
        fun openUnlockDialog() {
            unlockError = null
            when (lockedModeHasPin) {
                true -> showUnlock = true
                false -> scope.launch { lockedModeViewModel.unlock() }
                null -> Unit
            }
        }

        fun enterLockedMode() {
            when (lockedModeHasPin) {
                true -> showLockConfirm = true
                false -> scope.launch { lockedModeViewModel.activate() }
                null -> Unit
            }
        }

        // On dual-screen devices the game artwork/video is rendered on the top panel (ArtworkPresentation),
        // so the interactive content here drops its own full-screen backdrop.
        val dualScreen = onStateObserved != null || LocalDualScreenActive.current

        val sizing = homeLayoutSizing(state, LocalConfiguration.current.screenWidthDp)
        val gameGridColumns = sizing.gameGridColumns
        val recentGridColumns = sizing.recentGridColumns
        val appColumns = sizing.appColumns
        val minGameCols = sizing.minGameCols
        val maxGameCols = sizing.maxGameCols

        // Keep focus in bounds when lists change
        LaunchedEffect(state.platforms.size) {
            if (state.platforms.isNotEmpty())
                systemFocusIndex = systemFocusIndex.coerceIn(0, state.platforms.size - 1)
        }
        LaunchedEffect(appsState.apps.size) {
            appFocusIndex = if (appsState.apps.isEmpty()) 0 else appFocusIndex.coerceIn(0, appsState.apps.size - 1)
        }
        val showAppsTab = appsState.isLoading || appsState.apps.isNotEmpty()
        LaunchedEffect(showAppsTab, state.topTab) {
            if (!showAppsTab && state.topTab == TopTab.APPS) {
                appFocusIndex = 0
                viewModel.selectTopTab(TopTab.GAMES)
            }
        }
        LaunchedEffect(state.games.size) {
            if (state.games.isNotEmpty())
                gridFocusIndex = gridFocusIndex.coerceAtMost(state.games.size - 1)
        }
        LaunchedEffect(state.recentlyPlayed.size) {
            if (state.recentlyPlayed.isNotEmpty())
                recentFocusIndex = recentFocusIndex.coerceAtMost(state.recentlyPlayed.size - 1)
        }
        LaunchedEffect(state.favorites.size) {
            if (state.favorites.isNotEmpty())
                favFocusIndex = favFocusIndex.coerceAtMost(state.favorites.size - 1)
        }
        // Dual-screen: the grid layout tracks focus in local state (gridFocusIndex) and doesn't drive
        // the shared selection the way the carousel does, so mirror it into the ViewModel to keep the
        // artwork (top) screen in sync. No effect on single-screen devices (grid draws no backdrop).
        if (onStateObserved != null) {
            LaunchedEffect(state.gameViewActive, state.layoutMode) {
                if (state.gameViewActive && state.layoutMode != LayoutMode.CAROUSEL) {
                    // Only sync the top (artwork) screen once the cursor settles: publishing on every
                    // held-scroll step makes the Presentation decode a new marquee each step, on the same
                    // main thread as the scroll. Waiting for ~150ms of no movement holds the last marquee
                    // while fast-scrolling and loads the focused game's art when the scroll stops.
                    snapshotFlow { gridFocusIndex }
                        .debounce(150)
                        .collect { viewModel.onGameSelected(it) }
                }
            }
        }

        fun cyclePlatform(delta: Int) {
            val idx  = state.platforms.indexOf(state.selectedPlatform)
            val next = state.platforms.getOrNull((idx + delta).coerceIn(0, state.platforms.size - 1))
            next?.let { viewModel.selectPlatform(it) }
        }

        // Jump the game selection a whole page in the given direction, for whichever layout is active.
        // Carousel drives the shared selectedGameIndex; grid/list drive the local gridFocusIndex. The
        // page size is a screenful reported by the grid/list (rows×cols / rows); carousel has no reported
        // size, so it uses a sensible fixed stride.
        fun pageJumpGames(dir: Int) {
            val n = state.games.size
            if (n == 0) return
            if (state.layoutMode == LayoutMode.CAROUSEL) {
                val next = (state.selectedGameIndex + dir * CAROUSEL_PAGE).coerceIn(0, n - 1)
                if (next != state.selectedGameIndex) viewModel.onGameSelected(next)
            } else {
                val page = gridPageSize.takeIf { it > 0 } ?: gameGridColumns
                gridFocusIndex = (gridFocusIndex + dir * page).coerceIn(0, n - 1)
            }
        }

        // Recently Played, RetroAchievements and Friends tabs each appear only when enabled in settings.
        // This list also drives L1/R1 tab cycling, so an omitted filter here lets a disabled tab still be
        // reached with the bumpers (the Friends bug).
        val visibleTabs = TopTab.entries.filter {
            (it != TopTab.FAVORITES         || state.showFavorites) &&
            (it != TopTab.RECENTLY_PLAYED   || state.showRecentlyPlayed) &&
            (it != TopTab.RETROACHIEVEMENTS || state.showRetroAchievements) &&
            (it != TopTab.FRIENDS           || state.showFriends)
                && (it != TopTab.APPS || showAppsTab)
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
        val towardArtwork = if (state.dualScreenSwap) Key.DirectionDown else Key.DirectionUp
        val towardMenu = if (state.dualScreenSwap) Key.DirectionUp else Key.DirectionDown

        fun moveDirection(key: Key): Boolean = when (state.topTab) {
            TopTab.GAMES -> if (!state.gameViewActive) {
                when {
                    dualScreen && state.fanActive -> when (key) {
                        Key.DirectionLeft -> { viewModel.moveFan(-1); true }
                        Key.DirectionRight -> { viewModel.moveFan(1); true }
                        towardMenu -> { viewModel.setFanActive(false); true }
                        towardArtwork -> true
                        else -> false
                    }
                    dualScreen && key == towardArtwork -> { viewModel.setFanActive(true); true }
                    else -> when (key) {
                    Key.DirectionLeft  -> { systemFocusIndex = (systemFocusIndex - 1).coerceAtLeast(0); true }
                    Key.DirectionRight -> { systemFocusIndex = (systemFocusIndex + 1).coerceAtMost(state.platforms.size - 1); true }
                    else -> false
                    }
                }
            } else {
                when (state.layoutMode) {
                    LayoutMode.CAROUSEL -> when (key) {
                        Key.DirectionLeft  -> {
                            val nextIdx = (state.selectedGameIndex - 1).coerceAtLeast(0)
                            if (nextIdx != state.selectedGameIndex) {
                                viewModel.onGameSelected(nextIdx)
                                true
                            } else false
                        }
                        Key.DirectionRight -> {
                            val nextIdx = (state.selectedGameIndex + 1).coerceAtMost(state.games.size - 1)
                            if (nextIdx != state.selectedGameIndex) {
                                viewModel.onGameSelected(nextIdx)
                                true
                            } else false
                        }
                        else -> false
                    }
                    LayoutMode.GRID -> when (key) {
                        Key.DirectionLeft  -> { gridFocusIndex = (gridFocusIndex - 1).coerceAtLeast(0); true }
                        Key.DirectionRight -> { gridFocusIndex = (gridFocusIndex + 1).coerceAtMost(state.games.size - 1); true }
                        Key.DirectionUp    -> { (gridFocusIndex - gameGridColumns).let { if (it >= 0) gridFocusIndex = it }; true }
                        Key.DirectionDown  -> { (gridFocusIndex + gameGridColumns).let { if (it < state.games.size) gridFocusIndex = it }; true }
                        else -> false
                    }
                    // Single vertical column: Up/Down step one title; Left/Right are unused.
                    LayoutMode.LIST -> when (key) {
                        Key.DirectionUp   -> { gridFocusIndex = (gridFocusIndex - 1).coerceAtLeast(0); true }
                        Key.DirectionDown -> { gridFocusIndex = (gridFocusIndex + 1).coerceAtMost(state.games.size - 1); true }
                        else -> false
                    }
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
            TopTab.FAVORITES -> {
                val n = state.favorites.size
                when (key) {
                    Key.DirectionLeft  -> { favFocusIndex = (favFocusIndex - 1).coerceAtLeast(0); true }
                    Key.DirectionRight -> { favFocusIndex = (favFocusIndex + 1).coerceAtMost(n - 1); true }
                    Key.DirectionUp    -> { (favFocusIndex - recentGridColumns).let { if (it >= 0) favFocusIndex = it }; true }
                    Key.DirectionDown  -> { (favFocusIndex + recentGridColumns).let { if (it < n) favFocusIndex = it }; true }
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
            TopTab.FRIENDS -> false
        }

        // Hold a direction to keep moving: first press fires immediately, then after a short delay
        // the move repeats until the key is released.
        // Read the latest selection during a hold, including after carousel recompositions.
        val currentMove by rememberUpdatedState<(Key) -> Boolean>(::moveDirection)
        fun startHold(key: Key) = ui.startHold(scope, key) { currentMove(it) }
        DisposableEffect(ui) {
            onDispose { ui.stopRepeat() }
        }

        // Grab (and re-grab) controller focus on every resume — e.g. after returning from a game.
        // Without this the focusable Box can stay unfocused after a heavy emulator session, so the
        // launcher renders but ignores the gamepad and looks frozen. Retry briefly because window focus
        // can lag ON_RESUME, and clear any stuck hold-to-scroll state.
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            stopRepeat()
            scope.launch {
                var tries = 0
                while (!isFocused && tries++ < 15) {
                    runCatching { focusRequester.requestFocus() }
                    delay(80)
                }
            }
        }

        val controllerModifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.hasFocus }
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

                // ── Game-grid quick menu (opened with Select): it captures all input ──
                if (showGameOptions) {
                    when (key) {
                        Key.DirectionUp   -> optionsFocusIndex = (optionsFocusIndex - 1 + GAME_OPTIONS_ROWS) % GAME_OPTIONS_ROWS
                        Key.DirectionDown -> optionsFocusIndex = (optionsFocusIndex + 1) % GAME_OPTIONS_ROWS
                        // Size row: left = smaller (more columns), right = larger (fewer columns).
                        Key.DirectionLeft  -> if (optionsFocusIndex == 0)
                            viewModel.setGameGridColumns((gameGridColumns + 1).coerceAtMost(maxGameCols))
                        Key.DirectionRight -> if (optionsFocusIndex == 0)
                            viewModel.setGameGridColumns((gameGridColumns - 1).coerceAtLeast(minGameCols))
                        GamepadA, Key.DirectionCenter, Key.Enter -> {
                            val sortIdx = optionsFocusIndex - 1
                            when {
                                sortIdx in gameSortOptions.indices ->
                                    viewModel.setGameSort(gameSortOptions[sortIdx])
                                optionsFocusIndex == GAME_OPTIONS_SCRAPE_ROW -> {
                                    showGameOptions = false
                                    state.selectedPlatform?.let(onScrapeSystem)
                                }
                            }
                        }
                        GamepadB, Key.Back, GamepadSelect -> showGameOptions = false
                        else -> {}
                    }
                    return@onKeyEvent true
                }

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
                    GamepadStart -> {
                        if (isLocked) {
                            openUnlockDialog()
                        } else {
                            onSettingsClick()
                        }
                        return@onKeyEvent true
                    }
                }

                when (state.topTab) {
                    // ══ GAMES ════════════════════════════════════════
                    TopTab.GAMES -> if (!state.gameViewActive) {
                        when (key) {
                            GamepadB, Key.Back -> if (state.fanActive) {
                                viewModel.setFanActive(false); true
                            } else false
                            GamepadA, Key.DirectionCenter, Key.Enter -> {
                                if (dualScreen && state.fanActive) {
                                    state.fanGames.getOrNull(state.fanIndex)?.let { viewModel.launchGame(it.id) }
                                } else state.platforms.getOrNull(systemFocusIndex)?.let {
                                    gridFocusIndex = 0
                                    viewModel.enterSystem(it)
                                }; true
                            }
                            else -> false
                        }
                    } else {
                        when (key) {
                            GamepadA, Key.DirectionCenter, Key.Enter -> {
                                val idx = if (state.layoutMode == LayoutMode.CAROUSEL) state.selectedGameIndex else gridFocusIndex
                                state.games.getOrNull(idx)?.let { onGameClick(it.id) }; true
                            }
                            // Bumpers (L1/R1) skip a full page of games — in grid, carousel, and
                            // list alike. Triggers (L2/R2) move between systems.
                            GamepadL1 -> { pageJumpGames(-1); true }
                            GamepadR1 -> { pageJumpGames(+1); true }
                            GamepadL2 -> { cyclePlatform(-1); true }
                            GamepadR2 -> { cyclePlatform(+1); true }
                            GamepadB, Key.Back -> { viewModel.exitToSystems(); true }
                            GamepadSelect -> {
                                optionsFocusIndex = 0
                                showGameOptions = true
                                true
                            }
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

                    // ══ FAVORITES ════════════════════════════════════
                    TopTab.FAVORITES -> when (key) {
                        GamepadA, Key.DirectionCenter, Key.Enter -> {
                            state.favorites.getOrNull(favFocusIndex)?.let { onGameClick(it.id) }; true
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
                    TopTab.FRIENDS -> false
                }
            }
        HomeScreen(
            state = state,
            appsState = appsState,
            ui = ui,
            sizing = sizing,
            lockedModeState = lockedModeState,
            dualScreen = dualScreen,
            packageManagerHelper = appsViewModel.packageManagerHelper,
            actions = HomeActions(
                enterSystem = viewModel::enterSystem,
                exitToSystems = viewModel::exitToSystems,
                focusSystem = viewModel::focusSystem,
                onGameSelected = viewModel::onGameSelected,
                toggleMute = viewModel::toggleMute,
                selectTopTab = viewModel::selectTopTab,
                setGameGridColumns = viewModel::setGameGridColumns,
                setGameSort = viewModel::setGameSort,
                refreshApps = appsViewModel::refresh,
                launchApp = appsViewModel::launchApp,
            ),
            onGameClick = viewModel::launchGame,
            onGameLongClick = onGameClick,
            onSettingsClick = onSettingsClick,
            onScrapeSystem = onScrapeSystem,
            onEnterLockedMode = ::enterLockedMode,
            onUnlock = ::openUnlockDialog,
            modifier = controllerModifier,
        )

        HomeDialogs(
            ui = ui,
            onActivate = { scope.launch { lockedModeViewModel.activate() } },
            onUnlock = { pin ->
                scope.launch {
                    val result = lockedModeViewModel.unlock(pin)
                    unlockError = result.message()
                    if (result == PinResult.Success) showUnlock = false
                }
            },
        )
    }
}
