package com.gamelaunch.frontend

import com.gamelaunch.frontend.data.db.dao.SessionSlice
import com.gamelaunch.frontend.data.playtime.PlaytimeRepositoryImpl
import com.gamelaunch.frontend.domain.platform.SystemSort
import com.gamelaunch.frontend.domain.platform.sortedBySystems
import com.gamelaunch.frontend.ui.component.formatPlaytime
import com.gamelaunch.frontend.ui.component.formatPlaytimeShort
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class PlaytimeFormatTest {

    private val min = 60_000L

    @Test fun `formats compact durations`() {
        assertEquals("<1m", formatPlaytime(40_000))
        assertEquals("45m", formatPlaytime(45 * min))
        assertEquals("2h", formatPlaytime(120 * min))
        assertEquals("2h 15m", formatPlaytime(135 * min))
        assertEquals("100h 1m", formatPlaytime(6001 * min))
    }

    @Test fun `short format keeps whole hours only`() {
        assertEquals("<1m", formatPlaytimeShort(40_000))
        assertEquals("59m", formatPlaytimeShort(59 * min))
        assertEquals("2h", formatPlaytimeShort(135 * min))
        assertEquals("100h", formatPlaytimeShort(6001 * min))
    }

    @Test fun `most played sorts systems by play time, unplayed ones alphabetically after`() {
        val playtime = mapOf("snes" to 90 * min, "gba" to 300 * min)
        val sorted = listOf("nes", "snes", "gba", "atari2600").sortedBySystems(
            sorts = listOf(SystemSort.MOST_PLAYED),
            displayName = { it },
            gameCount = { 0 },
            playtime = { playtime[it] ?: 0L }
        )
        assertEquals(listOf("gba", "snes", "atari2600", "nes"), sorted)
    }

    @Test fun `buckets sessions by the local day they started`() {
        val zone = ZoneId.of("America/New_York")
        val today = LocalDate.of(2026, 10, 7)
        fun at(day: LocalDate, hour: Int) = day.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
        val slices = listOf(
            SessionSlice(at(today, 9), 30 * min),
            SessionSlice(at(today, 23), 10 * min),
            SessionSlice(at(today.minusDays(6), 12), 5 * min),   // oldest bucket
            SessionSlice(at(today.minusDays(7), 12), 99 * min)   // outside the window
        )
        val days = PlaytimeRepositoryImpl.dailyTotals(slices, today, 7, zone)
        assertEquals(listOf(5 * min, 0, 0, 0, 0, 0, 40 * min), days)
    }
}
