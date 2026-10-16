package com.gamelaunch.frontend

import com.gamelaunch.frontend.domain.usecase.CheckForUpdateUseCase.Companion.tagFromReleaseLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CheckForUpdateUseCaseTest {

    @Test fun `reads the tag from the latest-release redirect`() {
        assertEquals("v3.0.1", tagFromReleaseLocation("https://github.com/keweis2/eOr/releases/tag/v3.0.1"))
    }

    @Test fun `relative redirect targets work too`() {
        assertEquals("v2.8.0", tagFromReleaseLocation("/keweis2/eOr/releases/tag/v2.8.0"))
    }

    @Test fun `url-encoded tags are decoded`() {
        assertEquals("v3.1.0+lite", tagFromReleaseLocation("https://github.com/keweis2/eOr/releases/tag/v3.1.0%2Blite"))
    }

    @Test fun `no releases or no location gives null`() {
        // A repo without releases redirects to the plain releases page.
        assertNull(tagFromReleaseLocation("https://github.com/keweis2/eOr/releases"))
        assertNull(tagFromReleaseLocation(null))
        assertNull(tagFromReleaseLocation(""))
    }
}
