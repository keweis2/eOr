package com.gamelaunch.frontend

import com.gamelaunch.frontend.systemui.BootstrapTokens
import com.gamelaunch.frontend.systemui.BrokerLaunchArguments
import com.gamelaunch.frontend.systemui.AdbAuthenticationException
import com.gamelaunch.frontend.systemui.isAdbAuthenticationFailure
import javax.net.ssl.SSLProtocolException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedBrokerSecurityTest {
    @Test
    fun `bootstrap token is consumed exactly once`() {
        BootstrapTokens.issue("secret", 39)
        assertFalse(BootstrapTokens.consume("wrong", 39))
        assertTrue(BootstrapTokens.consume("secret", 39))
        assertFalse(BootstrapTokens.consume("secret", 39))
    }

    @Test
    fun `starter accepts only narrow validated arguments`() {
        val token = "a".repeat(43)
        assertNotNull(
            BrokerLaunchArguments.parse(
                arrayOf(
                    "/data/app/eor/base.apk",
                    "10123",
                    "39",
                    "com.gamelaunch.frontend.privilege.bootstrap",
                    token
                )
            )
        )
        assertNull(
            BrokerLaunchArguments.parse(
                arrayOf(
                    "/sdcard/eor.apk",
                    "0",
                    "39",
                    "bad authority!",
                    token
                )
            )
        )
        assertNull(
            BrokerLaunchArguments.parse(
                arrayOf(
                    "/data/app/eor/base.apk",
                    "10123",
                    "39",
                    "ok",
                    "pairing-code"
                )
            )
        )
    }

    @Test
    fun `only authentication failures invalidate saved pairing`() {
        assertTrue(AdbAuthenticationException("rejected").isAdbAuthenticationFailure())
        assertTrue(IllegalStateException(SSLProtocolException("rejected")).isAdbAuthenticationFailure())
        assertFalse(IllegalStateException("endpoint unavailable").isAdbAuthenticationFailure())
    }
}
