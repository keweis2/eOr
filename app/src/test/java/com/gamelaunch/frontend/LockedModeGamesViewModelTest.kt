package com.gamelaunch.frontend

import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.repository.GameRepository
import com.gamelaunch.frontend.domain.repository.MediaRepository
import com.gamelaunch.frontend.ui.lockedmode.LockedModeGamesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class LockedModeGamesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: GameRepository
    private lateinit var mediaRepository: MediaRepository
    private lateinit var games: MutableStateFlow<List<Game>>

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = mock()
        mediaRepository = mock()
        games = MutableStateFlow(emptyList())
        whenever(repository.getAllGames()).thenReturn(games)
        whenever(mediaRepository.observeAllMedia()).thenReturn(MutableStateFlow(emptyMap()))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `games load alphabetically with current availability`() = runTest(dispatcher) {
        games.value = listOf(game(2, "Zelda", false), game(1, "asteroids", true))
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(listOf("asteroids", "Zelda"), viewModel.uiState.value.games.map { it.title })
        assertEquals(listOf(true, false), viewModel.uiState.value.games.map { it.isAvailableInLockedMode })
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `successful toggle updates optimistically and persists`() = runTest(dispatcher) {
        games.value = listOf(game(1, "Game", false))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.setGameAllowed(1, true)

        assertTrue(viewModel.uiState.value.games.single().isAvailableInLockedMode)
        assertTrue(1L in viewModel.uiState.value.savingGameIds)
        advanceUntilIdle()
        verify(repository).setAvailableInLockedMode(1, true)
        assertTrue(viewModel.uiState.value.savingGameIds.isEmpty())
    }

    @Test
    fun `duplicate input while saving is ignored`() = runTest(dispatcher) {
        games.value = listOf(game(1, "Game", false))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.setGameAllowed(1, true)
        viewModel.setGameAllowed(1, false)
        advanceUntilIdle()

        verify(repository).setAvailableInLockedMode(1, true)
    }

    @Test
    fun `failed update rolls back only that game and exposes error`() = runTest(dispatcher) {
        games.value = listOf(game(1, "Fail", true), game(2, "Keep", false))
        whenever(repository.setAvailableInLockedMode(1, false)).thenThrow(IllegalStateException("save failed"))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.setGameAllowed(1, false)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.games.first { it.id == 1L }.isAvailableInLockedMode)
        assertFalse(viewModel.uiState.value.games.first { it.id == 2L }.isAvailableInLockedMode)
        assertEquals("save failed", viewModel.uiState.value.error)
    }

    @Test
    fun `database emissions refresh the list`() = runTest(dispatcher) {
        games.value = listOf(game(1, "Before", true))
        val viewModel = viewModel()
        advanceUntilIdle()

        games.value = listOf(game(1, "After", false), game(2, "Added", true))
        advanceUntilIdle()

        assertEquals(listOf("Added", "After"), viewModel.uiState.value.games.map { it.title })
        assertFalse(viewModel.uiState.value.games.first { it.id == 1L }.isAvailableInLockedMode)
    }

    private fun game(id: Long, title: String, allowed: Boolean) = Game(
        id = id,
        title = title,
        romPath = "/$title",
        romFilename = title,
        platformId = "nes",
        isAvailableInLockedMode = allowed,
    )

    private fun viewModel() = LockedModeGamesViewModel(repository, mediaRepository)
}
