package com.gamelaunch.frontend.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowRightAlt
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.gamelaunch.frontend.ui.component.DeviceStatusIcons
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamelaunch.frontend.R
import com.gamelaunch.frontend.domain.lockedmode.LockedModeState
import com.gamelaunch.frontend.ui.component.platformDisplayName
import com.gamelaunch.frontend.ui.theme.AmbientBackground
import com.gamelaunch.frontend.ui.theme.BrandBlue
import com.gamelaunch.frontend.ui.theme.IceWhite
import com.gamelaunch.frontend.ui.theme.LightBg
import com.gamelaunch.frontend.ui.theme.LocalDarkMode
import com.gamelaunch.frontend.ui.theme.NavyBg
import com.gamelaunch.frontend.ui.theme.SteelGray
import com.gamelaunch.frontend.ui.theme.TileSub
import com.gamelaunch.frontend.ui.theme.TileText
import com.gamelaunch.frontend.ui.theme.glassChip
import com.gamelaunch.frontend.ui.theme.LayoutMode

/** Home rendering shared by single-screen and dual-screen devices. */
@Composable
internal fun HomeScreen(
    state: HomeUiState,
    appsState: AppsUiState,
    ui: HomeScreenState,
    sizing: HomeLayoutSizing,
    lockedModeState: LockedModeState?,
    dualScreen: Boolean,
    packageManagerHelper: com.gamelaunch.frontend.launcher.PackageManagerHelper,
    actions: HomeActions,
    onGameClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    onScrapeSystem: (String) -> Unit,
    onEnterLockedMode: () -> Unit,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) = with(ui) {
    val isLocked = lockedModeState == LockedModeState.LOCKED
    val showAppsTab = appsState.isLoading || appsState.apps.isNotEmpty()
    val gameGridColumns = sizing.gameGridColumns
    val minGameCols = sizing.minGameCols
    val maxGameCols = sizing.maxGameCols
    val darkMode      = LocalDarkMode.current
    val textPrimary   = if (darkMode) IceWhite else TileText
    val textSecondary = if (darkMode) SteelGray else TileSub
    val bgColor       = if (darkMode) NavyBg else LightBg

    Scaffold(containerColor = bgColor) { _ ->
        Box(modifier = modifier) {
            AmbientBackground(
                Modifier.fillMaxSize(),
                // Keep the background crisp only on the main Games tab (console selection); blur &
                // fade it on every other view — game selection, Recent, Apps, RetroAchievements.
                patternSubdued = !(state.topTab == TopTab.GAMES && !state.gameViewActive)
            ) {
                Column(Modifier.fillMaxSize()) {

                    // ── Header + mode tabs ─────────────────────────────────
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(
                                    start = 16.dp, end = 16.dp, top = if (dualScreen) 8.dp else 16.dp,
                                    // Inside a system there's no tab bar under the header, so give the
                                    // settings button / breadcrumb more breathing room above the grid.
                                    bottom = if (!dualScreen && state.topTab == TopTab.GAMES && state.gameViewActive) 16.dp else 6.dp
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Touch-first back affordance: inside a system the only way back to the
                            // console list was the controller's B button. Mirror the detail screen's
                            // top-left back button so touch users can leave a system too.
                            if (state.topTab == TopTab.GAMES && state.gameViewActive) {
                                IconButton(
                                    onClick  = { actions.exitToSystems() },
                                    modifier = Modifier.size(40.dp).glassChip(CircleShape)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = textPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(Modifier.width(16.dp))
                            }
                            if (!dualScreen) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_donkey_silhouette),
                                    contentDescription = null,
                                    tint = BrandBlue,
                                    modifier = Modifier.size(26.dp).padding(end = 6.dp)
                                )
                                Text("e",  fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = BrandBlue, letterSpacing = 2.sp)
                                Text("Or", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = textPrimary, letterSpacing = 2.sp)
                            }

                            // Launch auto-scan found new ROMs and is adding them — small live indicator.
                            // Shown only on the system grid / non-game views so it never crowds the
                            // in-system breadcrumb.
                            if (state.isScanningLibrary && !(state.topTab == TopTab.GAMES && state.gameViewActive)) {
                                Spacer(Modifier.width(12.dp))
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = BrandBlue
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Scanning for new games…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = textSecondary,
                                    maxLines = 1
                                )
                            }

                            // Inside a system, breadcrumb: eOr · <Console> → <hovered game>
                            if (state.topTab == TopTab.GAMES && state.gameViewActive) {
                                state.selectedPlatform?.let { pid ->
                                    Text(
                                        if (dualScreen) platformDisplayName(pid) else "  ·  " + platformDisplayName(pid),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = textPrimary.copy(alpha = 0.85f),
                                        maxLines = 1
                                    )
                                }
                                val idx = if (state.layoutMode == LayoutMode.CAROUSEL) state.selectedGameIndex else gridFocusIndex
                                val hoveredGame = state.games.getOrNull(idx)
                                if (hoveredGame != null && !dualScreen) {
                                    // Arrow as a vector Icon rather than a "→" glyph in the title text:
                                    // the Row centres it vertically, whereas the glyph sat low on the
                                    // text baseline and looked like it was resting on the bottom.
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowRightAlt,
                                        contentDescription = null,
                                        tint = BrandBlue,
                                        modifier = Modifier.padding(horizontal = 6.dp).size(22.dp)
                                    )
                                    Text(
                                        hoveredGame.title,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = BrandBlue,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                } else {
                                    Spacer(Modifier.weight(1f))
                                }
                            } else {
                                Spacer(Modifier.weight(1f))
                            }

                            if (state.showStatusIcons && !dualScreen) {
                                DeviceStatusIcons(tint = textPrimary, modifier = Modifier.padding(end = 20.dp))
                            }
                            if (lockedModeState == LockedModeState.READY) {
                                IconButton(
                                    onClick = onEnterLockedMode,
                                    modifier = Modifier.size(40.dp).glassChip(CircleShape)
                                ) {
                                    Icon(Icons.Default.LockOpen, contentDescription = "Lock eOr", tint = textPrimary, modifier = Modifier.size(20.dp))
                                }
                                Spacer(Modifier.size(8.dp))
                            }
                            if (isLocked) {
                                IconButton(
                                    onClick = onUnlock,
                                    modifier = Modifier.size(40.dp).glassChip(CircleShape)
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = "Unlock eOr", tint = textPrimary, modifier = Modifier.size(20.dp))
                                }
                            } else if (lockedModeState != null) {
                                IconButton(
                                    onClick  = onSettingsClick,
                                    modifier = Modifier.size(40.dp).glassChip(CircleShape)
                                ) {
                                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = textPrimary, modifier = Modifier.size(20.dp))
                                }
                            }
                        }

                        // Single-screen keeps its existing tabs above content; dual-screen places them below.
                        if (!dualScreen && !(state.topTab == TopTab.GAMES && state.gameViewActive)) {
                            ModeTabBar(
                                selected = state.topTab,
                                showFavorites = state.showFavorites,
                                showRecentlyPlayed = state.showRecentlyPlayed,
                                showRetroAchievements = state.showRetroAchievements,
                                showFriends = state.showFriends,
                                showApps = showAppsTab,
                                onSelect = { tab ->
                                    actions.selectTopTab(tab)
                                    if (tab == TopTab.APPS) actions.refreshApps()
                                }
                            )
                        }
                    }

                    // ── Content ────────────────────────────────────────────
                    Box(Modifier.weight(1f).fillMaxSize()) {
                        HomeTabContent(
                            state = state,
                            appsState = appsState,
                            ui = ui,
                            sizing = sizing,
                            isLocked = isLocked,
                            dualScreen = dualScreen,
                            packageManagerHelper = packageManagerHelper,
                            actions = actions,
                            onGameClick = onGameClick,
                            onSettingsClick = onSettingsClick,
                        )
                    }
                    if (dualScreen) {
                        HomeBottomNavigation(
                            selected = state.topTab,
                            showFavorites = state.showFavorites,
                            showRecentlyPlayed = state.showRecentlyPlayed,
                            showRetroAchievements = state.showRetroAchievements,
                            showFriends = state.showFriends,
                            showApps = showAppsTab,
                            onSelect = { tab ->
                                actions.selectTopTab(tab)
                                if (tab == TopTab.APPS) actions.refreshApps()
                            }
                        )
                    }
                }
            }

            if (showGameOptions) {
                GameViewOptions(
                    sort         = state.gameSort,
                    columns      = gameGridColumns,
                    minColumns   = minGameCols,
                    maxColumns   = maxGameCols,
                    focusIndex   = optionsFocusIndex,
                    onSetColumns = actions.setGameGridColumns,
                    onPickSort   = actions.setGameSort,
                    onScrapeArtwork = {
                        showGameOptions = false
                        state.selectedPlatform?.let(onScrapeSystem)
                    },
                    onClose      = { showGameOptions = false }
                )
            }
        }
    }
}

@Composable
internal fun EmptyState(
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
