package com.gamelaunch.frontend

import android.content.Context
import com.gamelaunch.frontend.launcher.ExternalLaunchCoordinator
import com.gamelaunch.frontend.launcher.LaunchDisplayPolicy
import com.gamelaunch.frontend.ui.dualscreen.ExternalAppSessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class ExternalLaunchCoordinatorTest {
    private val policy = mock<LaunchDisplayPolicy>()
    private val session = ExternalAppSessionState()
    private val coordinator = ExternalLaunchCoordinator(mock<Context>(), policy, session)

    @Test fun `passes platform to policy and leaves artwork alone for normal routing`() = runTest {
        val result = coordinator.launch("3ds") { options ->
            assertTrue(options == null)
            Result.success(Unit)
        }
        assertTrue(result.isSuccess)
        verify(policy).options("3ds")
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
}
