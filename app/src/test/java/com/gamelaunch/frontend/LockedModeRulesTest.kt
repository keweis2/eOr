package com.gamelaunch.frontend

import com.gamelaunch.frontend.data.repository.deriveLockedModeState
import com.gamelaunch.frontend.data.repository.isValidLockedModePin
import com.gamelaunch.frontend.domain.lockedmode.LockedModeState
import com.gamelaunch.frontend.domain.lockedmode.UNKNOWN_BOOT_COUNT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockedModeRulesTest {
    @Test
    fun `state derives independently from PIN configuration`() {
        assertEquals(
            LockedModeState.DISABLED,
            deriveLockedModeState(
                enabled = false,
                active = false,
                activeBootCount = 42,
                currentBootCount = 42,
            ),
        )
        assertEquals(
            LockedModeState.DISABLED,
            deriveLockedModeState(
                enabled = false,
                active = true,
                activeBootCount = 42,
                currentBootCount = 42,
            ),
        )
        assertEquals(
            LockedModeState.READY,
            deriveLockedModeState(
                enabled = true,
                active = false,
                activeBootCount = 42,
                currentBootCount = 42,
            ),
        )
        assertEquals(
            LockedModeState.LOCKED,
            deriveLockedModeState(
                enabled = true,
                active = true,
                activeBootCount = 42,
                currentBootCount = 42,
            ),
        )
    }

    @Test
    fun `active lock from an earlier boot becomes ready`() {
        assertEquals(
            LockedModeState.READY,
            deriveLockedModeState(
                enabled = true,
                active = true,
                activeBootCount = 41,
                currentBootCount = 42,
            ),
        )
        assertEquals(
            LockedModeState.LOCKED,
            deriveLockedModeState(
                enabled = true,
                active = true,
                activeBootCount = 42,
                currentBootCount = 42,
            ),
        )
    }

    @Test
    fun `unknown boot counts never establish a lock`() {
        assertEquals(
            LockedModeState.READY,
            deriveLockedModeState(
                enabled = true,
                active = true,
                activeBootCount = UNKNOWN_BOOT_COUNT,
                currentBootCount = UNKNOWN_BOOT_COUNT,
            ),
        )
        assertEquals(
            LockedModeState.READY,
            deriveLockedModeState(
                enabled = true,
                active = true,
                activeBootCount = 42,
                currentBootCount = UNKNOWN_BOOT_COUNT,
            ),
        )
        assertEquals(
            LockedModeState.READY,
            deriveLockedModeState(
                enabled = true,
                active = true,
                activeBootCount = UNKNOWN_BOOT_COUNT,
                currentBootCount = 42,
            ),
        )
    }

    @Test
    fun `only exactly four numeric digits form a PIN`() {
        assertTrue(isValidLockedModePin("0427"))
        assertFalse(isValidLockedModePin("427"))
        assertFalse(isValidLockedModePin("04270"))
        assertFalse(isValidLockedModePin("12a4"))
    }
}
