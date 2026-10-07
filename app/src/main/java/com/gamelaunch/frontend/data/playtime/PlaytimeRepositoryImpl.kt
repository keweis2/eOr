package com.gamelaunch.frontend.data.playtime

import com.gamelaunch.frontend.data.db.dao.PlaySessionDao
import com.gamelaunch.frontend.data.db.dao.SessionSlice
import com.gamelaunch.frontend.domain.repository.PlaytimeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaytimeRepositoryImpl @Inject constructor(
    private val dao: PlaySessionDao
) : PlaytimeRepository {

    override fun totalPlaytime(gameId: Long): Flow<Long> = dao.totalForGame(gameId)

    override fun totalsByGame(): Flow<Map<Long, Long>> =
        dao.totalsByGame().map { rows -> rows.associate { it.gameId to it.totalMs } }

    override fun dailyPlaytime(gameId: Long, days: Int): Flow<List<Long>> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val since = today.minusDays(days - 1L).atStartOfDay(zone).toInstant().toEpochMilli()
        return dao.slicesForGameSince(gameId, since).map { dailyTotals(it, today, days, zone) }
    }

    companion object {
        /** Buckets sessions into local days by the day they started; oldest first, today last. */
        fun dailyTotals(slices: List<SessionSlice>, today: LocalDate, days: Int, zone: ZoneId): List<Long> {
            val totals = LongArray(days)
            slices.forEach { s ->
                val day = Instant.ofEpochMilli(s.startedAt).atZone(zone).toLocalDate()
                val index = days - 1 - (today.toEpochDay() - day.toEpochDay()).toInt()
                if (index in 0 until days) totals[index] += s.durationMs
            }
            return totals.toList()
        }
    }
}
