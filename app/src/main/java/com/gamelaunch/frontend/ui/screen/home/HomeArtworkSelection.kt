package com.gamelaunch.frontend.ui.screen.home

import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.ui.theme.LayoutMode

/** Resolve each tab's cursor once, independently of how its games are rendered. */
internal fun HomeUiState.focusedGame(
    gridIndex: Int,
    favoriteIndex: Int,
    recentIndex: Int,
): Game? = when (topTab) {
    TopTab.GAMES -> if (gameViewActive) {
        games.getOrNull(if (layoutMode == LayoutMode.CAROUSEL) selectedGameIndex else gridIndex)
    } else null
    TopTab.FAVORITES -> favorites.getOrNull(favoriteIndex)
    TopTab.RECENTLY_PLAYED -> recentlyPlayed.getOrNull(recentIndex)
    else -> null
}

/** Only resolve an id within the visible collection, so hidden tabs cannot leak artwork. */
internal fun HomeUiState.artworkGame(): Game? {
    val visibleGames = when (topTab) {
        TopTab.GAMES -> games
        TopTab.FAVORITES -> favorites
        TopTab.RECENTLY_PLAYED -> recentlyPlayed
        else -> return null
    }
    return focusedGameId?.let { id -> visibleGames.find { it.id == id } }
        ?: if (topTab == TopTab.GAMES) games.getOrNull(selectedGameIndex) else null
}
