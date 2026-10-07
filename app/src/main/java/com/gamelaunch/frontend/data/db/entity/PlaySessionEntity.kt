package com.gamelaunch.frontend.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One play session: from a successful launch until the user comes back to eOr.
 *
 * No foreign key to `games` on purpose — a ROM that's removed and re-added (or briefly missing)
 * would otherwise take its history with it. [platformId] is a snapshot for per-system totals.
 */
@Entity(
    tableName = "play_sessions",
    indices = [Index(value = ["game_id"]), Index(value = ["started_at"])]
)
data class PlaySessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "game_id") val gameId: Long,
    @ColumnInfo(name = "platform_id") val platformId: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    /** Null while the session is still running. */
    @ColumnInfo(name = "ended_at") val endedAt: Long? = null,
    /** Active play time counted so far (screen-off stretches excluded). */
    @ColumnInfo(name = "duration_ms") val durationMs: Long = 0,
    /** When the current counted stretch began; null while paused (screen off) or ended. */
    @ColumnInfo(name = "segment_started_at") val segmentStartedAt: Long? = null
)
