package com.gamelaunch.frontend.ui.screen.home

import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.ui.theme.LayoutMode
import org.junit.Assert.*
import org.junit.Test

class HomeArtworkSelectionTest {
    private fun game(id: Long) = Game(id, "Game $id", "/game$id", "game$id", "nes")

    @Test fun `each visible game view resolves its own cursor`() {
        val state = HomeUiState(
            gameViewActive = true, games = listOf(game(1), game(2)),
            selectedGameIndex = 0, favorites = listOf(game(3)),
            recentlyPlayed = listOf(game(4), game(5)),
        )
        assertEquals(2L, state.focusedGame(1, 0, 1)?.id)
        assertEquals(1L, state.copy(layoutMode = LayoutMode.CAROUSEL).focusedGame(1, 0, 1)?.id)
        assertEquals(3L, state.copy(topTab = TopTab.FAVORITES).focusedGame(1, 0, 1)?.id)
        assertEquals(5L, state.copy(topTab = TopTab.RECENTLY_PLAYED).focusedGame(1, 0, 1)?.id)
        assertNull(state.copy(gameViewActive = false).focusedGame(1, 0, 1))
        assertNull(state.copy(topTab = TopTab.APPS).focusedGame(1, 0, 1))
        assertNull(state.copy(topTab = TopTab.FAVORITES).focusedGame(1, -1, 1))
    }

    @Test fun `stale focus cannot select a game from a hidden collection`() {
        val state = HomeUiState(topTab = TopTab.RECENTLY_PLAYED,
            games = listOf(game(1)), recentlyPlayed = listOf(game(2)), focusedGameId = 1L)
        assertNull(state.artworkGame())
        assertEquals(2L, state.copy(focusedGameId = 2L).artworkGame()?.id)
    }
}
