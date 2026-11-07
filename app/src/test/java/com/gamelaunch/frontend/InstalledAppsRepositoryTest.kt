package com.gamelaunch.frontend

import androidx.lifecycle.ViewModelStore
import com.gamelaunch.frontend.data.repository.InstalledAppsRepositoryImpl
import com.gamelaunch.frontend.domain.lockedmode.LockedModeAppRepository
import com.gamelaunch.frontend.domain.lockedmode.LockedModeRepository
import com.gamelaunch.frontend.domain.lockedmode.LockedModeState
import com.gamelaunch.frontend.domain.model.InstalledApp
import com.gamelaunch.frontend.domain.usecase.LaunchAppUseCase
import com.gamelaunch.frontend.launcher.LaunchScreen
import com.gamelaunch.frontend.launcher.PackageManagerHelper
import com.gamelaunch.frontend.ui.screen.home.AppsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class InstalledAppsRepositoryTest {
    private val dispatcher = StandardTestDispatcher()
    private val packages = mock<PackageManagerHelper>()
    private val repository = InstalledAppsRepositoryImpl(packages)
    private val stores = mutableListOf<ViewModelStore>()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }

    @After fun tearDown() {
        stores.forEach { it.clear() }
        Dispatchers.resetMain()
    }

    @Test fun `concurrent initial loads share one scan including an empty result`() = runTest(dispatcher) {
        whenever(packages.getInstalledApps()).thenReturn(emptyList())
        assertNull(repository.apps.value)

        List(2) { async { repository.ensureLoaded() } }.awaitAll()
        repository.ensureLoaded()

        assertEquals(emptyList<InstalledApp>(), repository.apps.value)
        verify(packages, times(1)).getInstalledApps()
    }

    @Test fun `failed initial scan leaves loading state and allows retry`() = runTest(dispatcher) {
        val failure = IllegalStateException("Package manager unavailable")
        whenever(packages.getInstalledApps()).thenThrow(failure).thenReturn(emptyList())

        assertEquals(failure.message, runCatching { repository.ensureLoaded() }.exceptionOrNull()?.message)
        assertNull(repository.apps.value)
        repository.ensureLoaded()

        assertEquals(emptyList<InstalledApp>(), repository.apps.value)
        verify(packages, times(2)).getInstalledApps()
    }

    @Test fun `failed refresh retains last successful snapshot and allows retry`() = runTest(dispatcher) {
        val original = listOf(InstalledApp("old", "Old"))
        val failure = IllegalStateException("Package manager unavailable")
        whenever(packages.getInstalledApps()).thenReturn(original).thenThrow(failure).thenReturn(emptyList())
        repository.ensureLoaded()

        assertEquals(failure.message, runCatching { repository.refresh() }.exceptionOrNull()?.message)
        assertEquals(original, repository.apps.value)
        repository.refresh()

        assertEquals(emptyList<InstalledApp>(), repository.apps.value)
    }

    @Test fun `cancelled scan publishes no partial result and releases scan lock`() = runTest(dispatcher) {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        whenever(packages.getInstalledApps()).thenAnswer {
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            listOf(InstalledApp("cancelled", "Cancelled"))
        }
        val scan = async { repository.ensureLoaded() }
        runCurrent()
        try {
            withContext(Dispatchers.IO) { assertTrue(entered.await(5, TimeUnit.SECONDS)) }
            scan.cancel()
        } finally {
            release.countDown()
        }
        scan.cancelAndJoin()
        assertNull(repository.apps.value)
        whenever(packages.getInstalledApps()).thenReturn(emptyList())
        repository.ensureLoaded()
        assertEquals(emptyList<InstalledApp>(), repository.apps.value)
    }

    @Test fun `refresh waits for initial scan and publishes newer result last`() = runTest(dispatcher) {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val original = listOf(InstalledApp("old", "Old"))
        val newer = listOf(InstalledApp("new", "New"))
        whenever(packages.getInstalledApps()).thenAnswer {
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            original
        }.thenReturn(newer)
        val initial = async { repository.ensureLoaded() }
        runCurrent()
        try {
            withContext(Dispatchers.IO) { assertTrue(entered.await(5, TimeUnit.SECONDS)) }
            val refresh = async { repository.refresh() }
            runCurrent()
            verify(packages, times(1)).getInstalledApps()
            release.countDown()
            awaitAll(initial, refresh)
            assertEquals(newer, repository.apps.value)
            verify(packages, times(2)).getInstalledApps()
        } finally {
            release.countDown()
        }
    }

    @Test fun `menu refresh updates both view models and preserves locked mode filtering`() = runTest(dispatcher) {
        val original = InstalledApp("old", "Old")
        val added = InstalledApp("new", "New")
        whenever(packages.getInstalledApps()).thenReturn(listOf(original))
        repository.ensureLoaded()
        val lock = mock<LockedModeRepository>()
        val allowed = mock<LockedModeAppRepository>()
        val lockState = MutableStateFlow(LockedModeState.LOCKED)
        val allowedPackages = MutableStateFlow(setOf("old", "new"))
        whenever(lock.state).thenReturn(lockState)
        whenever(allowed.allowedPackages).thenReturn(allowedPackages)
        fun screen() = AppsViewModel(packages, lock, allowed, repository, mock()).also {
            stores += ViewModelStore().apply { put("apps", it) }
        }
        val menu = screen()
        val top = screen()
        runCurrent()
        assertEquals(listOf(original), top.uiState.value.apps)
        verify(packages, times(1)).getInstalledApps()

        whenever(packages.getInstalledApps()).thenReturn(listOf(added))
        menu.refresh()
        runCurrent()
        // Wait for the in-flight refresh without depending on IO thread timing.
        repository.ensureLoaded()
        runCurrent()

        assertEquals(listOf(added), menu.uiState.value.apps)
        assertEquals(listOf(added), top.uiState.value.apps)
        assertFalse(top.uiState.value.isLoading)
        verify(packages, times(2)).getInstalledApps()

        val recreated = screen()
        runCurrent()
        assertEquals(listOf(added), recreated.uiState.value.apps)
        assertFalse(recreated.uiState.value.isLoading)
        verify(packages, times(2)).getInstalledApps()

        allowedPackages.value = emptySet()
        runCurrent()
        assertEquals(emptyList<InstalledApp>(), menu.uiState.value.apps)
        assertEquals(emptyList<InstalledApp>(), top.uiState.value.apps)
    }
    @Test fun `shared refresh preserves launch error only on its originating screen`() = runTest(dispatcher) {
        whenever(packages.getInstalledApps()).thenReturn(listOf(InstalledApp("app", "App")))
        repository.ensureLoaded()
        val lock = mock<LockedModeRepository>()
        val allowed = mock<LockedModeAppRepository>()
        whenever(lock.state).thenReturn(MutableStateFlow(LockedModeState.LOCKED))
        whenever(allowed.allowedPackages).thenReturn(MutableStateFlow(setOf("app")))
        val launch = mock<LaunchAppUseCase>()
        whenever(launch("app", LaunchScreen.BOTTOM)).thenReturn(Result.failure(IllegalStateException("Launch failed")))
        fun screen() = AppsViewModel(packages, lock, allowed, repository, launch).also {
            stores += ViewModelStore().apply { put("apps", it) }
        }
        val menu = screen()
        val top = screen()
        runCurrent()
        menu.launchApp("app")
        runCurrent()
        assertEquals("Launch failed", menu.uiState.value.launchError)
        assertNull(top.uiState.value.launchError)

        whenever(packages.getInstalledApps()).thenReturn(emptyList())
        repository.refresh()
        runCurrent()
        assertEquals(emptyList<InstalledApp>(), menu.uiState.value.apps)
        assertEquals(emptyList<InstalledApp>(), top.uiState.value.apps)
        assertEquals("Launch failed", menu.uiState.value.launchError)
        assertNull(top.uiState.value.launchError)
        menu.clearLaunchError()
        assertNull(menu.uiState.value.launchError)
    }

}
