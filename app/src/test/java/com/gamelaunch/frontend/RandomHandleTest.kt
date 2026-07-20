package com.gamelaunch.frontend

import com.gamelaunch.frontend.domain.friends.RandomHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RandomHandleTest {

    private val format = Regex("^[A-Za-z]+-\\d{4}$")

    @Test fun `handle matches Adjective+Noun-NNNN format`() {
        repeat(200) {
            val handle = RandomHandle.generate()
            assertTrue("bad handle: $handle", format.matches(handle))
        }
    }

    @Test fun `same seed is deterministic`() {
        assertEquals(RandomHandle.generate(Random(7)), RandomHandle.generate(Random(7)))
    }

    @Test fun `number is always four digits`() {
        repeat(200) {
            val number = RandomHandle.generate().substringAfterLast('-')
            assertEquals(4, number.length)
            assertTrue(number.all { it.isDigit() })
        }
    }
}
