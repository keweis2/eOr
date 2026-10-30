package com.gamelaunch.frontend.ui.screen.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.gamelaunch.frontend.ui.component.formatPlaytime
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gamelaunch.frontend.domain.model.GameSort
import com.gamelaunch.frontend.ui.theme.ElectricBlue
import com.gamelaunch.frontend.ui.theme.LocalDarkMode
import com.gamelaunch.frontend.ui.theme.SteelGray
import com.gamelaunch.frontend.ui.theme.TileSub
import com.gamelaunch.frontend.ui.theme.grid.GridHomeContent
import com.gamelaunch.frontend.ui.theme.carousel.CarouselHomeContent
import com.gamelaunch.frontend.ui.theme.list.ListHomeContent
import com.gamelaunch.frontend.ui.theme.LayoutMode
import com.gamelaunch.frontend.ui.screen.retroachievements.RetroAchievementsScreen

/** Tab bodies consume the same state and sizing on either device layout. */
@Composable
internal fun androidx.compose.foundation.layout.BoxScope.HomeTabContent(
    state: HomeUiState,
    appsState: AppsUiState,
    ui: HomeScreenState,
    sizing: HomeLayoutSizing,
    isLocked: Boolean,
    dualScreen: Boolean,
    packageManagerHelper: com.gamelaunch.frontend.launcher.PackageManagerHelper,
    actions: HomeActions,
    onGameClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
) = with(ui) {
    val gameGridColumns = sizing.gameGridColumns
    val recentGridColumns = sizing.recentGridColumns
    val appColumns = sizing.appColumns
    when {
        state.isLoading && state.topTab == TopTab.GAMES ->
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = ElectricBlue)

        state.topTab == TopTab.GAMES && !state.gameViewActive ->
            SystemSelectionContent(
                platforms       = state.platforms,
                counts          = state.platformCounts,
                playtime        = state.platformPlaytime,
                focusedIndex    = systemFocusIndex,
                previewArt      = state.systemPreviewArt,
                onSystemFocused = actions.focusSystem,
                isLocked         = isLocked,
                onSystemClick   = { gridFocusIndex = 0; actions.enterSystem(it) },
                modifier        = Modifier.fillMaxSize(),
                showPreviewArt  = !dualScreen
            )

        state.topTab == TopTab.GAMES -> when (state.layoutMode) {
            LayoutMode.CAROUSEL ->
                CarouselHomeContent(
                    games             = state.games,
                    selectedGameMedia = state.selectedGameMedia,
                    mediaForGames     = state.mediaForGames,
                    selectedIndex     = state.selectedGameIndex,
                    shouldPlayVideo   = state.shouldPlayVideo,
                    videoMuted        = state.videoMuted,
                    onGameSelected    = actions.onGameSelected,
                    onGameClick       = onGameClick,
                    onMuteToggle      = actions.toggleMute,
                    gameSort          = state.gameSort,
                    modifier          = Modifier.fillMaxSize(),
                    showBackgroundArtwork = !dualScreen
                )
            LayoutMode.GRID ->
                GridHomeContent(
                    games            = state.games,
                    onGameClick      = onGameClick,
                    columns          = gameGridColumns,
                    mediaForGames    = state.mediaForGames,
                    focusedGameIndex = gridFocusIndex,
                    onPageSizeChange = { gridPageSize = it },
                    gameSort         = state.gameSort,
                    modifier         = Modifier.fillMaxSize()
                )
            LayoutMode.LIST ->
                ListHomeContent(
                    games            = state.games,
                    mediaForGames    = state.mediaForGames,
                    focusedGameIndex = gridFocusIndex,
                    onGameClick      = onGameClick,
                    onGameFocused    = { gridFocusIndex = it },
                    onPageSizeChange = { gridPageSize = it },
                    gameSort         = state.gameSort,
                    modifier         = Modifier.fillMaxSize()
                )
        }

        state.topTab == TopTab.RECENTLY_PLAYED ->
            if (state.recentlyPlayed.isEmpty()) {
                EmptyState(
                    icon     = Icons.Default.History,
                    title    = "No recent games",
                    subtitle = "Games you launch will appear here",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val playtimeBadges = remember(state.playtimeByGame) {
                    state.playtimeByGame
                        .filterValues { it > 0 }
                        .mapValues { (_, ms) -> formatPlaytime(ms) }
                }
                // Overall play time across every game, above the list.
                val totalPlayed = remember(state.playtimeByGame) { state.playtimeByGame.values.sum() }
                Column(Modifier.fillMaxSize()) {
                    if (totalPlayed >= 60_000) {
                        Text(
                            "${formatPlaytime(totalPlayed)} played in total",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (LocalDarkMode.current) SteelGray else TileSub,
                            modifier = Modifier.padding(start = 20.dp, top = 2.dp, bottom = 4.dp)
                        )
                    }
                    GridHomeContent(
                        games              = state.recentlyPlayed,
                        onGameClick        = onGameClick,
                        columns            = recentGridColumns,
                        mediaForGames      = state.mediaForGames,
                        focusedGameIndex   = recentFocusIndex,
                        gameSort           = GameSort.RECENTLY_PLAYED,
                        uniformAspectRatio = 0.72f,
                        // Home list, not a per-system library — no fast-scroll popup/blur.
                        sectionPopupEnabled = false,
                        badges             = playtimeBadges,
                        modifier         = Modifier.fillMaxWidth().weight(1f)
                    )
                }
            }

        state.topTab == TopTab.FAVORITES ->
            if (state.favorites.isEmpty()) {
                EmptyState(
                    icon     = Icons.Default.Favorite,
                    title    = "No favorites yet",
                    subtitle = "Tap the heart on a game's detail screen to collect it here",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                GridHomeContent(
                    games              = state.favorites,
                    onGameClick        = onGameClick,
                    columns            = recentGridColumns,
                    mediaForGames      = state.mediaForGames,
                    focusedGameIndex   = favFocusIndex,
                    gameSort           = GameSort.FAVORITES,
                    uniformAspectRatio = 0.72f,
                    // Home list, not a per-system library — no fast-scroll popup/blur.
                    sectionPopupEnabled = false,
                    modifier           = Modifier.fillMaxSize()
                )
            }

        state.topTab == TopTab.APPS ->
            AppsContent(
                apps                 = appsState.apps,
                isLoading            = appsState.isLoading,
                focusedIndex         = appFocusIndex,
                columns              = appColumns,
                packageManagerHelper = packageManagerHelper,
                onAppClick           = actions.launchApp,
                modifier             = Modifier.fillMaxSize()
            )

        state.topTab == TopTab.FRIENDS ->
            com.gamelaunch.frontend.ui.screen.friends.FriendsScreen(
                onGoToSettings = onSettingsClick,
                modifier       = Modifier.fillMaxSize()
            )

        else -> RetroAchievementsScreen(
            onGoToSettings = onSettingsClick,
            modifier       = Modifier.fillMaxSize()
        )
    }
}
