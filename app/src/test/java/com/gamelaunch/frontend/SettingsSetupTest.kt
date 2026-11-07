package com.gamelaunch.frontend

import com.gamelaunch.frontend.domain.repository.SettingsRepository
import com.gamelaunch.frontend.domain.usecase.CheckEmulatorUpdatesUseCase
import com.gamelaunch.frontend.ui.screen.settings.SettingsViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Answers
import org.mockito.Mockito
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsSetupTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private inline fun <reified T : Any> flowingMock(): T = Mockito.mock(T::class.java) { call ->
        if (Flow::class.java.isAssignableFrom(call.method.returnType)) emptyFlow<Any>()
        else Answers.RETURNS_DEFAULTS.answer(call)
    }

    @Test fun `setup waits for persistence before destroying its navigation graph`() = runTest(dispatcher) {
        val repository = flowingMock<SettingsRepository>()
        val updates = mock<CheckEmulatorUpdatesUseCase>()
        whenever(updates.cachedUpdates()).thenReturn(emptyList())
        val themes = mock<com.gamelaunch.frontend.data.theme.CustomThemeRepository>()
        whenever(themes.themes).thenReturn(MutableStateFlow(emptyList()))
        val model = SettingsViewModel(
            repository, mock(), mock(), mock(), mock(), mock(), mock(), mock(), flowingMock(),
            mock(), flowingMock(), mock(), updates, mock(), mock(), mock(), themes, mock(),
        )
        val stored = CompletableDeferred<Unit>()
        whenever(repository.setScraperCredentials(any(), any())).thenAnswer { stored.complete(Unit); Unit }
        var complete = false
        whenever(repository.setFirstLaunchComplete()).thenAnswer { complete = true; Unit }
        model.updateSsId("setup-user")
        model.updateSsPassword("setup-password")
        var navigated = false
        model.completeSetup {
            assertTrue(stored.isCompleted)
            assertTrue(complete)
            navigated = true
        }
        model.completeSetup { fail("A repeated finish must not navigate twice") }
        assertFalse(navigated)
        advanceUntilIdle()
        assertTrue(navigated)
        inOrder(repository) {
            verify(repository).setScraperCredentials("setup-user", "setup-password")
            verify(repository).setFirstLaunchComplete()
        }
    }
}
