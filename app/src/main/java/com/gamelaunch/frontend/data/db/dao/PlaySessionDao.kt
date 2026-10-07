package com.gamelaunch.frontend.data.db.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.gamelaunch.frontend.data.db.entity.PlaySessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaySessionDao {

    @Insert
    suspend fun insert(session: PlaySessionEntity): Long

    @Update
    suspend fun update(session: PlaySessionEntity)

    @Query("DELETE FROM play_sessions WHERE id = :id")
    suspend fun delete(id: Long)

    /** Sessions still running (normally at most one). */
    @Query("SELECT * FROM play_sessions WHERE ended_at IS NULL ORDER BY started_at")
    suspend fun getOpen(): List<PlaySessionEntity>

    @Query("SELECT COALESCE(SUM(duration_ms), 0) FROM play_sessions WHERE game_id = :gameId AND ended_at IS NOT NULL")
    fun totalForGame(gameId: Long): Flow<Long>

    /** Total play time per game, for lists that show many games at once (Recent tab). */
    @Query(
        "SELECT game_id, SUM(duration_ms) AS total_ms FROM play_sessions " +
        "WHERE ended_at IS NOT NULL GROUP BY game_id"
    )
    fun totalsByGame(): Flow<List<GameTotal>>

    /** Finished sessions of a game that started at or after [since], for the per-day chart. */
    @Query(
        "SELECT started_at, duration_ms FROM play_sessions " +
        "WHERE game_id = :gameId AND ended_at IS NOT NULL AND started_at >= :since"
    )
    fun slicesForGameSince(gameId: Long, since: Long): Flow<List<SessionSlice>>
}

data class GameTotal(
    @ColumnInfo(name = "game_id") val gameId: Long,
    @ColumnInfo(name = "total_ms") val totalMs: Long
)

data class SessionSlice(
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long
)
