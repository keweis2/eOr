package com.gamelaunch.frontend.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewCarousel
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import com.gamelaunch.frontend.ui.input.GamepadA
import com.gamelaunch.frontend.ui.input.GamepadL1
import com.gamelaunch.frontend.ui.input.GamepadL2
import com.gamelaunch.frontend.ui.input.GamepadR1
import com.gamelaunch.frontend.ui.input.GamepadR2
import com.gamelaunch.frontend.ui.input.GamepadStart
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gamelaunch.frontend.ui.component.PlatformTabRow
import com.gamelaunch.frontend.ui.theme.ElectricBlue
import com.gamelaunch.frontend.ui.theme.IceWhite
import com.gamelaunch.frontend.ui.theme.LayoutMode
import com.gamelaunch.frontend.ui.theme.NeonPurple
import com.gamelaunch.frontend.ui.theme.carousel.CarouselHomeContent
import com.gamelaunch.frontend.ui.theme.grid.GridHomeContent

@Composable
fun HomeScreen(
    onGameClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state   by viewModel.uiState.collectAsState()
    val density  = LocalDensity.current

    // Measure floating header height so grid can pad below it
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val headerHeightDp = with(density) { headerHeightPx.toDp() }

    // Track focused grid cell for controller navigation in grid mode
    var gridFocusIndex by remember { mutableIntStateOf(0) }

    // Keep grid focus in bounds when the game list changes
    LaunchedEffect(state.games.size) {
        if (state.games.isNotEmpty()) {
            gridFocusIndex = gridFocusIndex.coerceAtMost(state.games.size - 1)
        }
    }

    // Compute grid columns the same way GridHomeContent does (Adaptive 110dp)
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val gridColumns   = maxOf(2, screenWidthDp / 110)

    // Helper: cycle platforms by offset (-1 = previous, +1 = next)
    fun cyclePlatform(delta: Int) {
        val idx  = state.platforms.indexOf(state.selectedPlatform)
        val next = state.platforms.getOrNull((idx + delta).coerceIn(0, state.platforms.size - 1))
        next?.let { viewModel.selectPlatform(it) }
    }

    val focusRequester = remember { FocusRequester() }

    // Grab focus as soon as the screen appears (and after back-navigation)
    LaunchedEffect(Unit) {
        try { focusRequester.requestFocus() } catch (_: Exception) { }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { _ ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false

                    when (event.key) {

                        // ── D-pad left ─────────────────────────────────────
                        Key.DirectionLeft -> {
                            if (state.layoutMode == LayoutMode.CAROUSEL) {
                                viewModel.onGameSelected((state.selectedGameIndex - 1).coerceAtLeast(0))
                            } else {
                                gridFocusIndex = (gridFocusIndex - 1).coerceAtLeast(0)
                            }
                            true
                        }

                        // ── D-pad right ────────────────────────────────────
                        Key.DirectionRight -> {
                            if (state.layoutMode == LayoutMode.CAROUSEL) {
                                viewModel.onGameSelected((state.selectedGameIndex + 1).coerceAtMost(state.games.size - 1))
                            } else {
                                gridFocusIndex = (gridFocusIndex + 1).coerceAtMost(state.games.size - 1)
                            }
                            true
                        }

                        // ── D-pad up ───────────────────────────────────────
                        Key.DirectionUp -> {
                            if (state.layoutMode == LayoutMode.GRID) {
                                val up = gridFocusIndex - gridColumns
                                if (up >= 0) gridFocusIndex = up
                            } else {
                                // In carousel: up cycles to the previous platform
                                cyclePlatform(-1)
                            }
                            true
                        }

                        // ── D-pad down ─────────────────────────────────────
                        Key.DirectionDown -> {
                            if (state.layoutMode == LayoutMode.GRID) {
                                val down = gridFocusIndex + gridColumns
                                if (down < state.games.size) gridFocusIndex = down
                            } else {
                                cyclePlatform(+1)
                            }
                            true
                        }

                        // ── A / D-pad center = confirm / launch ────────────
                        GamepadA, Key.DirectionCenter, Key.Enter -> {
                            if (state.layoutMode == LayoutMode.CAROUSEL) {
                                state.games.getOrNull(state.selectedGameIndex)
                                    ?.let { onGameClick(it.id) }
                            } else {
                                state.games.getOrNull(gridFocusIndex)
                                    ?.let { onGameClick(it.id) }
                            }
                            true
                        }

                        // ── L1 / L2 = previous platform ───────────────────
                        GamepadL1, GamepadL2 -> { cyclePlatform(-1); true }

                        // ── R1 / R2 = next platform ────────────────────────
                        GamepadR1, GamepadR2 -> { cyclePlatform(+1); true }

                        // ── Start = settings ──────────────────────────────
                        GamepadStart -> { onSettingsClick(); true }

                        else -> false
                    }
                }
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color    = ElectricBlue
                )
            } else {
                when (state.layoutMode) {
                    LayoutMode.CAROUSEL -> CarouselHomeContent(
                        games             = state.games,
                        selectedGameMedia = state.selectedGameMedia,
                        selectedIndex     = state.selectedGameIndex,
                        shouldPlayVideo   = state.shouldPlayVideo,
                        videoMuted        = state.videoMuted,
                        onGameSelected    = viewModel::onGameSelected,
                        onGameClick       = onGameClick,
                        onMuteToggle      = viewModel::toggleMute,
                        modifier          = Modifier.fillMaxSize()
                    )
                    LayoutMode.GRID -> GridHomeContent(
                        games            = state.games,
                        onGameClick      = onGameClick,
                        focusedGameIndex = gridFocusIndex,
                        modifier         = Modifier
                            .fillMaxSize()
                            .padding(top = headerHeightDp)
                    )
                }
            }

            // ── Floating glass header overlay ──────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.72f),
                            1f to Color.Transparent
                        )
                    )
                    .onSizeChanged { headerHeightPx = it.height }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text       = "GAME",
                        fontSize   = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color      = ElectricBlue,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text       = "LAUNCHER",
                        fontSize   = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color      = IceWhite,
                        letterSpacing = 1.sp
                    )

                    Spacer(Modifier.weight(1f))

                    IconButton(
                        onClick  = viewModel::toggleLayoutMode,
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color.White.copy(alpha = 0.12f), CircleShape)
                    ) {
                        Icon(
                            if (state.layoutMode == LayoutMode.CAROUSEL) Icons.Default.GridView
                            else Icons.Default.ViewCarousel,
                            contentDescription = "Toggle layout",
                            tint   = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick  = onSettingsClick,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(38.dp)
                            .background(Color.White.copy(alpha = 0.12f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint   = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                PlatformTabRow(
                    platforms          = state.platforms,
                    selectedPlatform   = state.selectedPlatform,
                    onPlatformSelected = viewModel::selectPlatform
                )
            }
        }
    }
}
