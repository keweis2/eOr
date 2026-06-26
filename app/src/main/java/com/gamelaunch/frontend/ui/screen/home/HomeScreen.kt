package com.gamelaunch.frontend.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
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

    // Measure the overlay header height so the grid can pad correctly
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val headerHeightDp = with(density) { headerHeightPx.toDp() }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { _ ->
        Box(Modifier.fillMaxSize()) {

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
                    LayoutMode.GRID -> {
                        // Push content below the floating header
                        GridHomeContent(
                            games      = state.games,
                            onGameClick = onGameClick,
                            modifier   = Modifier
                                .fillMaxSize()
                                .padding(top = headerHeightDp)
                        )
                    }
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
                    // Logo text
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

                    // Layout toggle (glass circle button)
                    IconButton(
                        onClick  = viewModel::toggleLayoutMode,
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color.White.copy(alpha = 0.12f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (state.layoutMode == LayoutMode.CAROUSEL)
                                Icons.Default.GridView else Icons.Default.ViewCarousel,
                            contentDescription = "Toggle layout",
                            tint               = Color.White,
                            modifier           = Modifier.size(20.dp)
                        )
                    }

                    // Settings (glass circle button)
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
                            tint               = Color.White,
                            modifier           = Modifier.size(20.dp)
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
