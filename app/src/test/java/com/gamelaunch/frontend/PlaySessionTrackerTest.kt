package com.gamelaunch.frontend

import com.gamelaunch.frontend.data.db.dao.GameTotal
import com.gamelaunch.frontend.data.db.dao.PlaySessionDao
import com.gamelaunch.frontend.data.db.dao.SessionSlice
import com.gamelaunch.frontend.data.db.entity.PlaySessionEntity
import com.gamelaunch.frontend.data.playtime.PlaySessionTracker
import com.gamelaunch.frontend.data.playtime.PlaySessions
import com.gamelaunch.frontend.domain.model.Game
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaySessionTrackerTest {

    private class FakeDao : PlaySessionDao {
        val rows = linkedMapOf<Long, PlaySessionEntity>()
        private var nextId = 1L
        override suspend fun insert(session: PlaySessionEntity): Long =
            nextId++.also { rows[it] = session.copy(id = it) }
        override suspend fun update(session: PlaySessionEntity) { rows[session.id] = session }
        override suspend fun delete(id: Long) { rows.remove(id) }
        override suspend fun getOpen() = rows.values.filter { it.endedAt == null }
        override fun totalForGame(gameId: Long): Flow<Long> = flowOf(0)
        override fun totalsByGame(): Flow<List<GameTotal>> = flowOf(emptyList())
        override fun slicesForGameSince(gameId: Long, since: Long): Flow<List<SessionSlice>> = flowOf(emptyList())
    }

    private val min = 60_000L
    private var now = 1_000_000L
    private val dao = FakeDao()
    private val tracker = PlaySessionTracker(dao) { now }
    private val game = Game(id = 7, title = "Moon", romPath = "/r/moon.gb", romFilename = "moon.gb", platformId = "gb")

    @Test fun `a session runs from launch until the user returns`() = runTest {
        tracker.onGameLaunched(game)
        now += 30 * min
        tracker.onReturnedToEor()

        val s = dao.rows.values.single()
        assertEquals(30 * min, s.durationMs)
        assertEquals(now, s.endedAt)
        assertEquals("gb", s.platformId)
    }

    @Test fun `screen-off time is not counted`() = runTest {
        tracker.onGameLaunched(game)
        now += 10 * min
        tracker.onScreenOff()
        now += 8 * 60 * min            // left paused overnight
        tracker.onScreenOn()
        now += 5 * min
        tracker.onReturnedToEor()

        assertEquals(15 * min, dao.rows.values.single().durationMs)
    }

    @Test fun `instant bounces back to eOr are dropped`() = runTest {
        tracker.onGameLaunched(game)
        now += 3_000
        tracker.onReturnedToEor()
        assertTrue(dao.rows.isEmpty())
    }

    @Test fun `a session from a killed process is closed with a capped final stretch`() = runTest {
        // Previous process opened it and banked 20 minutes, then died while the game kept running.
        dao.insert(PlaySessions.start(7, "gb", now).copy(durationMs = 20 * min, segmentStartedAt = now))
        now += 10 * 60 * min           // user only comes back 10 hours later

        PlaySessionTracker(dao) { now }.onReturnedToEor()

        val s = dao.rows.values.single()
        assertEquals(20 * min + PlaySessions.MAX_UNOBSERVED_SEGMENT_MS, s.durationMs)
        assertNull(s.segmentStartedAt)
    }

    @Test fun `screen events don't touch sessions this process didn't open`() = runTest {
        dao.insert(PlaySessions.start(7, "gb", now))
        val fresh = PlaySessionTracker(dao) { now }
        now += 5 * min
        fresh.onScreenOff()
        assertEquals(0L, dao.rows.values.single().durationMs)
    }

    @Test fun `launching another game closes the one still open`() = runTest {
        tracker.onGameLaunched(game)
        now += 20 * min
        tracker.onGameLaunched(game.copy(id = 8))
        now += 5 * min
        tracker.onReturnedToEor()

        assertEquals(listOf(20 * min, 5 * min), dao.rows.values.map { it.durationMs })
        assertTrue(dao.rows.values.all { it.endedAt != null })
    }
}
