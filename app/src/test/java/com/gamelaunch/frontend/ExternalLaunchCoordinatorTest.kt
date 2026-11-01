package com.gamelaunch.frontend

import android.os.Bundle
import com.gamelaunch.frontend.launcher.ExternalLaunchCoordinator
import com.gamelaunch.frontend.launcher.LaunchDisplayPolicy
import com.gamelaunch.frontend.launcher.LaunchScreen
import com.gamelaunch.frontend.ui.dualscreen.ExternalAppReturnActivity
import com.gamelaunch.frontend.ui.dualscreen.ExternalAppReturnHost
import com.gamelaunch.frontend.ui.dualscreen.ExternalAppSessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ExternalLaunchCoordinatorTest {
    private val policy = mock<LaunchDisplayPolicy>()
    private val session = ExternalAppSessionState()
    private val returnHost = mock<ExternalAppReturnHost>()
    private val coordinator = ExternalLaunchCoordinator(returnHost, policy, session)

    @Test fun `passes platform to policy and leaves artwork alone for normal routing`() = runTest {
        val result = coordinator.launch("3ds") { options ->
            assertTrue(options == null)
            Result.success(Unit)
        }
        assertTrue(result.isSuccess)
        verify(policy).options("3ds", null)
        assertFalse(session.launchedOnTop.value)
    }

    @Test fun `preserves launch failure`() = runTest {
        val failure = IllegalStateException("No emulator configured")
        val result = coordinator.launch { Result.failure(failure) }
        assertSame(failure, result.exceptionOrNull())
        assertFalse(session.launchedOnTop.value)
    }

    @Test fun `cancellation propagates and releases launch lock`() = runTest {
        val cancellation = CancellationException("Cancelled launch")
        try {
            coordinator.launch { throw cancellation }
            throw AssertionError("Cancellation was swallowed")
        } catch (caught: CancellationException) {
            assertSame(cancellation, caught)
        }
        assertTrue(coordinator.launch { Result.success(Unit) }.isSuccess)
    }
    @Test fun `explicit screen is forwarded regardless of an existing top session`() = runTest {
        session.begin()
        assertTrue(coordinator.launch(screen = LaunchScreen.BOTTOM) { Result.success(Unit) }.isSuccess)
        verify(policy).options(null, LaunchScreen.BOTTOM)
        assertTrue(session.launchedOnTop.value)
    }

    @Test fun `bottom launch prepares menu return and preserves top list`() = runTest {
        val options = mock<Bundle>()
        val activity = mock<ExternalAppReturnActivity>()
        whenever(policy.options(null, LaunchScreen.BOTTOM)).thenReturn(options)
        whenever(returnHost.prepare(options, true)).thenReturn(activity)

        val result = coordinator.launch(screen = LaunchScreen.BOTTOM) {
            assertSame(options, it)
            assertTrue(session.keepAppListOnTop)
            Result.success(Unit)
        }

        assertTrue(result.isSuccess)
        assertTrue(session.keepAppListOnTop)
        assertFalse(session.launchedOnTop.value)
        verify(returnHost).prepare(options, true)
        verify(activity, never()).finishAndRemoveTask()
    }

    @Test fun `failed bottom launch clears top list retention and removes return host`() = runTest {
        val options = mock<Bundle>()
        val activity = mock<ExternalAppReturnActivity>()
        val failure = IllegalStateException("Launch failed")
        whenever(policy.options(null, LaunchScreen.BOTTOM)).thenReturn(options)
        whenever(returnHost.prepare(options, true)).thenReturn(activity)

        val result = coordinator.launch(screen = LaunchScreen.BOTTOM) { Result.failure(failure) }

        assertSame(failure, result.exceptionOrNull())
        assertFalse(session.keepAppListOnTop)
        assertFalse(session.launchedOnTop.value)
        verify(activity).finishAndRemoveTask()
    }

    @Test fun `cancellation after host preparation removes host and releases lock`() = runTest {
        val options = mock<Bundle>()
        val activity = mock<ExternalAppReturnActivity>()
        val cancellation = CancellationException("Cancelled after preparation")
        whenever(policy.options(null, LaunchScreen.BOTTOM)).thenReturn(options)
        whenever(returnHost.prepare(options, true)).thenReturn(activity)

        try {
            coordinator.launch(screen = LaunchScreen.BOTTOM) {
                assertTrue(session.keepAppListOnTop)
                throw cancellation
            }
            throw AssertionError("Cancellation was swallowed")
        } catch (caught: CancellationException) {
            assertSame(cancellation, caught)
        }

        assertFalse(session.keepAppListOnTop)
        assertFalse(session.launchedOnTop.value)
        verify(activity).finishAndRemoveTask()
        assertTrue(coordinator.launch { Result.success(Unit) }.isSuccess)
    }

    @Test fun `top launch begins session with requested dimming`() = runTest {
        val options = mock<Bundle>()
        val activity = mock<ExternalAppReturnActivity>()
        whenever(policy.options(null, LaunchScreen.TOP)).thenReturn(options)
        whenever(returnHost.prepare(options, false)).thenReturn(activity)

        for (dim in listOf(true, false)) {
            session.end()
            val result = coordinator.launch(screen = LaunchScreen.TOP, dimBottomScreen = dim) {
                assertSame(options, it)
                assertFalse(session.launchedOnTop.value)
                Result.success(Unit)
            }
            assertTrue(result.isSuccess)
            assertTrue(session.launchedOnTop.value)
            assertEquals(dim, session.dimBottomScreen.value)
            assertFalse(session.keepAppListOnTop)
        }
        verify(activity, never()).finishAndRemoveTask()
    }

    @Test fun `host preparation failure prevents launch and clears retention`() = runTest {
        val options = mock<Bundle>()
        val failure = IllegalStateException("Return host unavailable")
        whenever(policy.options(null, LaunchScreen.BOTTOM)).thenReturn(options)
        whenever(returnHost.prepare(options, true)).thenThrow(failure)
        var started = false

        val result = coordinator.launch(screen = LaunchScreen.BOTTOM) {
            started = true
            Result.success(Unit)
        }

        assertSame(failure, result.exceptionOrNull())
        assertFalse(started)
        assertFalse(session.keepAppListOnTop)
        assertFalse(session.launchedOnTop.value)
        assertTrue(coordinator.launch { Result.success(Unit) }.isSuccess)
    }

}
