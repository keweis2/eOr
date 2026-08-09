package com.gamelaunch.frontend

import com.gamelaunch.frontend.domain.lockedmode.LockedModeAppRepository
import com.gamelaunch.frontend.domain.model.InstalledApp
import com.gamelaunch.frontend.launcher.PackageManagerHelper
import com.gamelaunch.frontend.ui.lockedmode.LockedModeAppsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
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
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class LockedModeAppsViewModelTest {
    private val dispatcher: TestDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeLockedModeAppRepository
    private lateinit var packageManagerHelper: PackageManagerHelper

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeLockedModeAppRepository()
        packageManagerHelper = mock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial load exposes installed and allowed apps`() = runTest(dispatcher) {
        val apps = listOf(InstalledApp("allowed", "Allowed"), InstalledApp("other", "Other"))
        repository.allowed.value = setOf("allowed")
        whenever(packageManagerHelper.getInstalledApps()).thenReturn(apps)

        val viewModel = LockedModeAppsViewModel(repository, packageManagerHelper)
        awaitAppLoad(viewModel)

        assertEquals(apps, viewModel.uiState.value.installedApps)
        assertEquals(setOf("allowed"), viewModel.uiState.value.allowedPackages)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `successful toggle is persisted`() = runTest(dispatcher) {
        whenever(packageManagerHelper.getInstalledApps()).thenReturn(emptyList())
        val viewModel = LockedModeAppsViewModel(repository, packageManagerHelper)
        awaitAppLoad(viewModel)

        viewModel.setAppAllowed("new", true)
        advanceUntilIdle()

        assertEquals(listOf("new" to true), repository.writes)
        assertTrue("new" in viewModel.uiState.value.allowedPackages)
        assertTrue(viewModel.uiState.value.savingPackages.isEmpty())
    }

    @Test
    fun `failed toggle rolls back only its package`() = runTest(dispatcher) {
        repository.allowed.value = setOf("keep", "failing")
        repository.failingPackages += "failing"
        whenever(packageManagerHelper.getInstalledApps()).thenReturn(emptyList())
        val viewModel = LockedModeAppsViewModel(repository, packageManagerHelper)
        awaitAppLoad(viewModel)

        viewModel.setAppAllowed("failing", false)
        viewModel.setAppAllowed("added", true)
        advanceUntilIdle()

        assertEquals(setOf("keep", "failing", "added"), viewModel.uiState.value.allowedPackages)
        assertEquals("save failed", viewModel.uiState.value.error)
    }

    @Test
    fun `duplicate click while package is saving is ignored`() = runTest(dispatcher) {
        whenever(packageManagerHelper.getInstalledApps()).thenReturn(emptyList())
        val viewModel = LockedModeAppsViewModel(repository, packageManagerHelper)
        awaitAppLoad(viewModel)

        viewModel.setAppAllowed("app", true)
        viewModel.setAppAllowed("app", false)
        advanceUntilIdle()

        assertEquals(listOf("app" to true), repository.writes)
    }

    @Test
    fun `load failure can be retried`() = runTest(dispatcher) {
        val apps = listOf(InstalledApp("app", "App"))
        whenever(packageManagerHelper.getInstalledApps())
            .thenThrow(IllegalStateException("load failed"))
            .thenReturn(apps)
        val viewModel = LockedModeAppsViewModel(repository, packageManagerHelper)
        awaitAppLoad(viewModel)

        assertEquals("load failed", viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.installedApps.isEmpty())

        viewModel.retryLoadingApps()
        awaitAppLoad(viewModel)

        assertEquals(apps, viewModel.uiState.value.installedApps)
        assertEquals(null, viewModel.uiState.value.error)
    }

    private fun TestScope.awaitAppLoad(viewModel: LockedModeAppsViewModel) {
        repeat(100) {
            advanceUntilIdle()
            if (!viewModel.uiState.value.isLoading) return
            Thread.sleep(10)
        }
        error("Timed out waiting for installed apps")
    }
}

private class FakeLockedModeAppRepository : LockedModeAppRepository {
    val allowed = MutableStateFlow<Set<String>>(emptySet())
    override val allowedPackages: Flow<Set<String>> = allowed
    val writes = mutableListOf<Pair<String, Boolean>>()
    val failingPackages = mutableSetOf<String>()

    override suspend fun setAllowed(packageName: String, allowed: Boolean) {
        writes += packageName to allowed
        if (packageName in failingPackages) error("save failed")
        this.allowed.value = if (allowed) this.allowed.value + packageName else this.allowed.value - packageName
    }

    override suspend fun clearAllowed() {
        allowed.value = emptySet()
    }

    override suspend fun isAllowed(packageName: String): Boolean = packageName in allowed.value
}
