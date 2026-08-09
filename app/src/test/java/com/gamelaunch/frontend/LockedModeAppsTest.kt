package com.gamelaunch.frontend

import com.gamelaunch.frontend.domain.lockedmode.LockedModeAppRepository
import com.gamelaunch.frontend.domain.lockedmode.LockedModeRepository
import com.gamelaunch.frontend.domain.model.InstalledApp
import com.gamelaunch.frontend.domain.usecase.LaunchAppUseCase
import com.gamelaunch.frontend.launcher.PackageManagerHelper
import com.gamelaunch.frontend.ui.screen.home.filterAppsForLockedMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class LockedModeAppsTest {
    private val apps = listOf(InstalledApp("allowed", "Allowed"), InstalledApp("denied", "Denied"))

    @Test fun `locked filtering is default deny and intersects installed packages`() {
        assertTrue(filterAppsForLockedMode(null, locked = true, allowed = setOf("allowed")).isEmpty())
        assertTrue(filterAppsForLockedMode(apps, locked = true, allowed = emptySet()).isEmpty())
        assertEquals(listOf(apps.first()), filterAppsForLockedMode(apps, locked = true, allowed = setOf("allowed", "uninstalled")))
        assertEquals(apps, filterAppsForLockedMode(apps, locked = false, allowed = emptySet()))
    }

    @Test fun `guard rejects a disallowed app immediately before launch`() = runTest {
        val lock = mock<LockedModeRepository>()
        val allowed = mock<LockedModeAppRepository>()
        val packages = mock<PackageManagerHelper>()
        whenever(lock.isLocked()).thenReturn(true)
        whenever(allowed.isAllowed("denied")).thenReturn(false)

        assertTrue(LaunchAppUseCase(lock, allowed, packages)("denied").isFailure)
        verifyNoInteractions(packages)
    }

    @Test fun `guard launches an allowed app`() = runTest {
        val lock = mock<LockedModeRepository>()
        val allowed = mock<LockedModeAppRepository>()
        val packages = mock<PackageManagerHelper>()
        whenever(lock.isLocked()).thenReturn(true)
        whenever(allowed.isAllowed("allowed")).thenReturn(true)
        whenever(packages.launchApp("allowed")).thenReturn(true)

        assertTrue(LaunchAppUseCase(lock, allowed, packages)("allowed").isSuccess)
        verify(packages).launchApp("allowed")
    }
}
