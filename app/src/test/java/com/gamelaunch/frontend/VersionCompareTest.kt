package com.gamelaunch.frontend

import com.gamelaunch.frontend.util.VersionCompare
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionCompareTest {

    @Test
    fun `newer patch and minor and major`() {
        assertTrue(VersionCompare.isNewer("1.4.1", "1.4.0"))
        assertTrue(VersionCompare.isNewer("1.5.0", "1.4.9"))
        assertTrue(VersionCompare.isNewer("2.0.0", "1.9.9"))
    }

    @Test
    fun `equal or older is not newer`() {
        assertFalse(VersionCompare.isNewer("1.4.0", "1.4.0"))
        assertFalse(VersionCompare.isNewer("1.3.0", "1.4.0"))
        assertFalse(VersionCompare.isNewer("1.4", "1.4.0"))
    }

    @Test
    fun `differing component counts compare by position`() {
        assertTrue(VersionCompare.isNewer("1.4.1", "1.4"))
        assertFalse(VersionCompare.isNewer("1.4", "1.4.1"))
        assertTrue(VersionCompare.isNewer("1.4.0.1", "1.4.0"))
    }

    @Test
    fun `non-numeric parts are ignored`() {
        // Tags like "v2.1.0" are trimmed before compare; dashes/suffixes are dropped.
        assertTrue(VersionCompare.isNewer("2.1.0", "2.0.0-beta"))
        assertFalse(VersionCompare.isNewer("2.0.0-rc1", "2.0.0"))
        assertTrue(VersionCompare.isNewer("2024.05.01", "2024.04.30"))
    }

    @Test
    fun `garbage versions do not report an update`() {
        assertFalse(VersionCompare.isNewer("nightly", "1.0.0"))
        assertFalse(VersionCompare.isNewer("", "1.0.0"))
    }
}
