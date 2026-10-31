package com.gamelaunch.frontend

import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.model.GameMedia
import com.gamelaunch.frontend.ui.dualscreen.*
import com.gamelaunch.frontend.ui.navigation.Screen
import com.gamelaunch.frontend.ui.screen.home.HomeUiState
import com.gamelaunch.frontend.ui.screen.detail.GameDetailUiState
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

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

    @Test fun `fan selection beyond first five reaches artwork screen and survives detail return`() {
        coordinator.onDestination(Screen.Home.route)
        val games = (1L..20L).map(::game)
        coordinator.onHomeState(HomeUiState(
            fanGames = games, fanIndex = 14, fanActive = true,
            previewPlatformId = "nes", systemPreviewArt = games.map { "cover${it.id}" }))
        assertEquals(ArtworkMode.SYSTEM_GRID, bus.state.value.mode)
        assertEquals(14, bus.state.value.fanIndex)
        assertEquals("Game 15", bus.state.value.fanTitle)
        assertTrue(bus.state.value.fanActive)
        assertEquals(20, bus.state.value.systemPreviewArt.size)
        coordinator.onDestination(Screen.GameDetail.route, 15)
        detail(15)
        coordinator.onDestination(Screen.Home.route)
        assertEquals(14, bus.state.value.fanIndex)
        assertEquals("nes", bus.state.value.focusedPlatformId)
    }

    @Test fun `touch commands reject stale systems and hidden fans`() = runBlocking {
        val actions = mutableListOf<FanTouchAction>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            bus.fanActions.collect { actions.add(it) }
        }
        try {
            bus.publish(ArtworkUiState(mode = ArtworkMode.SYSTEM_GRID, focusedPlatformId = "3ds"))
            bus.touchFan(FanTouchAction("nes", 1, FanAction.LAUNCH))
            bus.touchFan(FanTouchAction("3ds", 2))
            bus.setSettingsActive(true)
            bus.touchFan(FanTouchAction("3ds", 2, FanAction.DETAILS))
            bus.setSettingsActive(false)
            bus.publish(ArtworkUiState(mode = ArtworkMode.GAME, focusedPlatformId = "3ds"))
            bus.touchFan(FanTouchAction("3ds", 2, FanAction.DETAILS))
            yield()
            assertEquals(listOf(FanTouchAction("3ds", 2)), actions)
        } finally {
            collector.cancel()
        }
    }

    @Test fun `unknown destinations clear stale artwork`() {
        home(1)
        coordinator.onDestination(Screen.Scan.route)
        assertEquals(ArtworkMode.IDLE, bus.state.value.mode)
        assertNull(bus.state.value.media)
    }
}
