package com.gamelaunch.frontend

import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.model.GameMedia
import com.gamelaunch.frontend.ui.dualscreen.*
import com.gamelaunch.frontend.ui.navigation.Screen
import com.gamelaunch.frontend.ui.screen.home.HomeUiState
import com.gamelaunch.frontend.ui.screen.detail.GameDetailUiState
import org.junit.Assert.*
import org.junit.Test

class DualScreenCoordinatorTest {
    private val bus = ArtworkBus()
    private val coordinator = DualScreenCoordinator(bus)
    private fun game(id: Long) = Game(id, "Game $id", "/game$id", "game$id", "nes")
    private fun home(id: Long) = coordinator.onHomeState(HomeUiState(
        games = listOf(game(id)), gameViewActive = true, selectedGameMedia = GameMedia(gameId = id)))
    private fun detail(id: Long) = coordinator.onDetailState(GameDetailUiState(game = game(id)))

    @Test fun `back restores latest library selection while detail owns visible artwork`() {
        coordinator.onDestination(Screen.Home.route)
        home(1)
        coordinator.onDestination(Screen.GameDetail.route, 2)
        detail(2)
        home(3)
        assertEquals(2L, bus.state.value.gameId)
        coordinator.onDestination(Screen.Home.route)
        assertEquals(3L, bus.state.value.gameId)
    }

    @Test fun `settings then Home restores selection and clears settings indicator`() {
        home(1)
        coordinator.onDestination(Screen.SettingsAppearance.route)
        assertTrue(bus.settingsActive.value)
        assertNull(bus.state.value.gameId)
        coordinator.onDestination(Screen.Home.route)
        assertFalse(bus.settingsActive.value)
        assertEquals(1L, bus.state.value.gameId)
    }

    @Test fun `delayed detail updates cannot overwrite another destination or game`() {
        home(1)
        coordinator.onDestination(Screen.GameDetail.route, 2)
        detail(2)
        coordinator.onDestination(Screen.GameDetail.route, 3)
        detail(2)
        assertNull(bus.state.value.gameId)
        detail(3)
        assertEquals(3L, bus.state.value.gameId)
        coordinator.onDestination(Screen.Home.route)
        detail(3)
        assertEquals(1L, bus.state.value.gameId)
    }

    @Test fun `gameplay and return retain latest selection and preferences`() {
        coordinator.onDestination(Screen.Home.route)
        home(1)
        coordinator.onGameSession(true)
        home(2)
        coordinator.onImagePreference(TopScreenImage.SCREENSHOT)
        assertTrue(coordinator.state.value.gameSessionActive)
        coordinator.onGameSession(false)
        assertEquals(2L, bus.state.value.gameId)
        assertEquals(TopScreenImage.SCREENSHOT, bus.state.value.topImageType)
    }

    @Test fun `unknown destinations clear stale artwork`() {
        home(1)
        coordinator.onDestination(Screen.Scan.route)
        assertEquals(ArtworkMode.IDLE, bus.state.value.mode)
        assertNull(bus.state.value.media)
    }
}
