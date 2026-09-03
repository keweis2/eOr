package com.gamelaunch.frontend

import com.gamelaunch.frontend.domain.usecase.SettingsTransfer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the promise that a settings export never carries a credential, token or PIN. */
class SettingsTransferTest {

    @Test fun `exported keys never overlap sensitive keys`() {
        val overlap = SettingsTransfer.EXPORTED_KEYS intersect SettingsTransfer.SENSITIVE_KEYS
        assertTrue("Exported settings must not include sensitive keys: $overlap", overlap.isEmpty())
    }

    @Test fun `credential and pin keys are marked sensitive`() {
        listOf("ss_password", "ra_api_key", "ra_token", "locked_mode_pin").forEach {
            assertTrue("$it should be sensitive", it in SettingsTransfer.SENSITIVE_KEYS)
        }
    }

    @Test fun `format identifier is stable`() {
        assertEquals("eor-settings", SettingsTransfer.FORMAT)
    }
}
