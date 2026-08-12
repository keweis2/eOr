package com.gamelaunch.frontend

import com.gamelaunch.frontend.domain.lockedmode.LockedModeState
import com.gamelaunch.frontend.systemui.SystemNavigationLockStatus
import com.gamelaunch.frontend.systemui.SystemNavigationSetupProgress
import com.gamelaunch.frontend.ui.systemui.SystemNavigationPrompt
import com.gamelaunch.frontend.ui.systemui.toPrompt
import com.gamelaunch.frontend.ui.screen.settings.shouldShowSystemNavigationSetupSteps
import com.gamelaunch.frontend.ui.screen.settings.systemNavigationSetupStepCompletion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemNavigationLockPresentationTest {
    @Test
    fun `setup step completion is gated by preceding requirements`() {
        assertEquals(
            listOf(false, false, false),
            systemNavigationSetupStepCompletion(
                SystemNavigationSetupProgress(
                    developerOptionsEnabled = false,
                    wirelessDebuggingEnabled = false,
                    paired = true,
                ),
            ),
        )
        assertEquals(
            listOf(true, false, false),
            systemNavigationSetupStepCompletion(
                SystemNavigationSetupProgress(
                    developerOptionsEnabled = true,
                    wirelessDebuggingEnabled = false,
                    paired = true,
                ),
            ),
        )
        assertEquals(
            listOf(true, true, true),
            systemNavigationSetupStepCompletion(
                SystemNavigationSetupProgress(
                    developerOptionsEnabled = true,
                    wirelessDebuggingEnabled = true,
                    paired = true,
                ),
            ),
        )
    }

    @Test
    fun `setup steps are hidden after system navigation setup completes`() {
        listOf(
            SystemNavigationLockStatus.READY,
            SystemNavigationLockStatus.APPLYING,
            SystemNavigationLockStatus.ACTIVE,
        ).forEach { status ->
            assertFalse(status.name, shouldShowSystemNavigationSetupSteps(status))
        }
    }

    @Test
    fun `setup steps remain available before setup completes or after readiness is lost`() {
        SystemNavigationLockStatus.entries
            .filterNot {
                it in setOf(
                    SystemNavigationLockStatus.READY,
                    SystemNavigationLockStatus.APPLYING,
                    SystemNavigationLockStatus.ACTIVE,
                )
            }
            .forEach { status ->
                assertTrue(status.name, shouldShowSystemNavigationSetupSteps(status))
            }
    }

    @Test
    fun `restore warning is shown regardless of current locked mode settings`() {
        assertEquals(
            SystemNavigationPrompt.RESTORE_NAVIGATION,
            SystemNavigationLockStatus.RESTORE_REQUIRED.toPrompt(
                lockedMode = LockedModeState.DISABLED,
                optionEnabled = false,
            ),
        )
    }

    @Test
    fun `internal setup never requires a user-facing prompt`() {
        assertNull(
            SystemNavigationLockStatus.START_REQUIRED.toPrompt(
                lockedMode = LockedModeState.READY,
                optionEnabled = true,
            )
        )
        assertNull(
            SystemNavigationLockStatus.START_REQUIRED.toPrompt(
                lockedMode = LockedModeState.LOCKED,
                optionEnabled = false,
            )
        )
        assertNull(
            SystemNavigationLockStatus.START_REQUIRED.toPrompt(
                lockedMode = LockedModeState.LOCKED,
                optionEnabled = true,
            )
        )
    }

    @Test
    fun `actionable controller failures map to their matching prompts`() {
        val prompts = mapOf(
            SystemNavigationLockStatus.DEVELOPER_OPTIONS_REQUIRED to SystemNavigationPrompt.ENABLE_DEVELOPER_OPTIONS,
            SystemNavigationLockStatus.WIRELESS_DEBUGGING_REQUIRED to SystemNavigationPrompt.ENABLE_WIRELESS_DEBUGGING,
            SystemNavigationLockStatus.PAIRING_REQUIRED to SystemNavigationPrompt.PAIR_DEVICE,
        )

        prompts.forEach { (status, expectedPrompt) ->
            assertEquals(
                expectedPrompt,
                status.toPrompt(
                    lockedMode = LockedModeState.LOCKED,
                    optionEnabled = true,
                ),
            )
        }
    }
}
