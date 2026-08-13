package com.gamelaunch.frontend

import com.gamelaunch.frontend.domain.lockedmode.LockedModeState
import com.gamelaunch.frontend.systemui.SystemNavigationLockStatus
import com.gamelaunch.frontend.systemui.SystemNavigationSetupProgress
import com.gamelaunch.frontend.systemui.navigationRecoveryStatus
import com.gamelaunch.frontend.systemui.brokerSetupRequirement
import com.gamelaunch.frontend.systemui.shouldLockSystemNavigation
import com.gamelaunch.frontend.systemui.setupStatus
import com.gamelaunch.frontend.systemui.shellDisableFlags
import com.gamelaunch.frontend.systemui.pairingCodeFrom
import com.gamelaunch.frontend.systemui.PairingNotificationPhase
import com.gamelaunch.frontend.systemui.shouldDismissPairingNotification
import com.gamelaunch.frontend.systemui.shouldResetPairingNotificationLifecycle
import com.gamelaunch.frontend.systemui.EmbeddedPairingService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemNavigationLockRulesTest {
    @Test
    fun `usable broker states dismiss idle and setup pairing notifications`() {
        listOf(SystemNavigationLockStatus.READY, SystemNavigationLockStatus.ACTIVE).forEach { status ->
            assertTrue(shouldDismissPairingNotification(status, PairingNotificationPhase.IDLE))
            assertTrue(shouldDismissPairingNotification(status, PairingNotificationPhase.SETUP))
        }
    }

    @Test
    fun `usable broker states preserve pairing and result notifications`() {
        listOf(SystemNavigationLockStatus.READY, SystemNavigationLockStatus.ACTIVE).forEach { status ->
            assertFalse(shouldDismissPairingNotification(status, PairingNotificationPhase.PAIRING))
            assertFalse(shouldDismissPairingNotification(status, PairingNotificationPhase.RESULT))
        }
    }

    @Test
    fun `transitional and setup required states preserve pairing notifications`() {
        listOf(
            SystemNavigationLockStatus.DEVELOPER_OPTIONS_REQUIRED,
            SystemNavigationLockStatus.WIRELESS_DEBUGGING_REQUIRED,
            SystemNavigationLockStatus.PAIRING_REQUIRED,
            SystemNavigationLockStatus.DISCOVERING,
            SystemNavigationLockStatus.START_REQUIRED,
            SystemNavigationLockStatus.STARTING,
            SystemNavigationLockStatus.APPLYING,
        ).forEach { status ->
            PairingNotificationPhase.entries.forEach { phase ->
                assertFalse(
                    "Unexpected cleanup for $status in $phase",
                    shouldDismissPairingNotification(status, phase),
                )
            }
        }
    }

    @Test
    fun `pairing success timeout is thirty seconds`() {
        assertEquals(30_000L, EmbeddedPairingService.PAIRING_SUCCESS_TIMEOUT_MS)
    }

    @Test
    fun `older result timeout cannot reset a newer pairing attempt`() {
        assertTrue(shouldResetPairingNotificationLifecycle(PairingNotificationPhase.RESULT, 4, 4))
        assertFalse(shouldResetPairingNotificationLifecycle(PairingNotificationPhase.RESULT, 5, 4))
        assertFalse(shouldResetPairingNotificationLifecycle(PairingNotificationPhase.PAIRING, 4, 4))
    }

    @Test
    fun `broker discovery requires developer options and wireless debugging`() {
        assertEquals(
            SystemNavigationLockStatus.DEVELOPER_OPTIONS_REQUIRED,
            brokerSetupRequirement(SystemNavigationSetupProgress()),
        )
        assertEquals(
            SystemNavigationLockStatus.WIRELESS_DEBUGGING_REQUIRED,
            brokerSetupRequirement(SystemNavigationSetupProgress(developerOptionsEnabled = true)),
        )
        assertEquals(
            SystemNavigationLockStatus.DEVELOPER_OPTIONS_REQUIRED,
            brokerSetupRequirement(SystemNavigationSetupProgress(wirelessDebuggingEnabled = true)),
        )
        assertNull(
            brokerSetupRequirement(
                SystemNavigationSetupProgress(
                    developerOptionsEnabled = true,
                    wirelessDebuggingEnabled = true,
                ),
            ),
        )
    }

    @Test
    fun `failed broker connection distinguishes pairing from paired startup recovery`() {
        assertEquals(
            SystemNavigationLockStatus.PAIRING_REQUIRED,
            setupStatus(
                SystemNavigationLockStatus.WIRELESS_DEBUGGING_REQUIRED,
                SystemNavigationSetupProgress(
                    developerOptionsEnabled = true,
                    wirelessDebuggingEnabled = true,
                ),
            ),
        )
        assertEquals(
            SystemNavigationLockStatus.STARTING,
            setupStatus(
                SystemNavigationLockStatus.WIRELESS_DEBUGGING_REQUIRED,
                SystemNavigationSetupProgress(
                    developerOptionsEnabled = true,
                    wirelessDebuggingEnabled = true,
                    paired = true,
                ),
            ),
        )
        assertEquals(
            SystemNavigationLockStatus.WIRELESS_DEBUGGING_REQUIRED,
            setupStatus(
                SystemNavigationLockStatus.WIRELESS_DEBUGGING_REQUIRED,
                SystemNavigationSetupProgress(developerOptionsEnabled = true),
            ),
        )
        assertEquals(
            SystemNavigationLockStatus.DEVELOPER_OPTIONS_REQUIRED,
            setupStatus(
                SystemNavigationLockStatus.WIRELESS_DEBUGGING_REQUIRED,
                SystemNavigationSetupProgress(),
            ),
        )
    }

    @Test
    fun `navigation recovery requires developer options and wireless debugging`() {
        assertEquals(
            SystemNavigationLockStatus.DEVELOPER_OPTIONS_REQUIRED,
            navigationRecoveryStatus(SystemNavigationSetupProgress()),
        )
        assertEquals(
            SystemNavigationLockStatus.WIRELESS_DEBUGGING_REQUIRED,
            navigationRecoveryStatus(SystemNavigationSetupProgress(developerOptionsEnabled = true)),
        )
        assertEquals(
            SystemNavigationLockStatus.DEVELOPER_OPTIONS_REQUIRED,
            navigationRecoveryStatus(SystemNavigationSetupProgress(wirelessDebuggingEnabled = true)),
        )

        assertEquals(
            SystemNavigationLockStatus.STARTING,
            navigationRecoveryStatus(
                SystemNavigationSetupProgress(
                    developerOptionsEnabled = true,
                    wirelessDebuggingEnabled = true,
                    paired = true,
                )
            ),
        )
    }

    @Test
    fun `navigation recovery requires pairing after wireless debugging is ready`() {
        assertEquals(
            SystemNavigationLockStatus.PAIRING_REQUIRED,
            navigationRecoveryStatus(
                SystemNavigationSetupProgress(
                    developerOptionsEnabled = true,
                    wirelessDebuggingEnabled = true,
                ),
            ),
        )
    }

    @Test
    fun `pairing code can be re-entered after a mistake without backspace`() {
        assertEquals("123456", pairingCodeFrom("98765 123456"))
        assertEquals("123456", pairingCodeFrom("123456"))
        assertNull(pairingCodeFrom("12345"))
        assertNull(pairingCodeFrom("1234567"))
    }

    @Test
    fun `restriction is desired only in active locked mode with opt in`() {
        assertFalse(shouldLockSystemNavigation(false, LockedModeState.DISABLED))
        assertFalse(shouldLockSystemNavigation(true, LockedModeState.DISABLED))
        assertFalse(shouldLockSystemNavigation(true, LockedModeState.READY))
        assertFalse(shouldLockSystemNavigation(false, LockedModeState.LOCKED))
        assertTrue(shouldLockSystemNavigation(true, LockedModeState.LOCKED))
    }

    @Test
    fun `shell disable flags are read from the shell-owned status bar record`() {
        val dump = """
            mDisableRecords.size=2
              [0] userId=0 what1=0x00000000 pkg=com.android.systemui token=android.os.BinderProxy@1
              [1] userId=0 what1=0x01210000 pkg=android token=com.android.server.statusbar.StatusBarShellCommand${'$'}StatusBarShellCommandToken@2
        """.trimIndent()

        assertEquals(0x01210000L, shellDisableFlags(dump))
    }

    @Test
    fun `missing shell status bar record cannot be mistaken for restored`() {
        assertNull(shellDisableFlags("mDisableRecords.size=0"))
    }
}
