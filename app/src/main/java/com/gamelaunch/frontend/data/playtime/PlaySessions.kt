package com.gamelaunch.frontend.data.playtime

import com.gamelaunch.frontend.data.db.entity.PlaySessionEntity
import java.util.concurrent.TimeUnit

/**
 * Pure session-timing rules, separate from storage so they can be tested with a fake clock.
 *
 * A session counts time in "segments": a segment opens at launch and whenever the screen turns
 * back on, and closes when the screen turns off or the user returns to eOr.
 */
object PlaySessions {

    /** Shorter sessions are dropped: failed launches and instant bounces back to eOr. */
    val MIN_SESSION_MS = TimeUnit.SECONDS.toMillis(10)

    /**
     * Most we'll credit for a segment whose end we never saw — eOr's process died while the game
     * ran, so we don't know whether the user played on or left it paused overnight.
     */
    val MAX_UNOBSERVED_SEGMENT_MS = TimeUnit.HOURS.toMillis(3)

    fun start(gameId: Long, platformId: String, now: Long) = PlaySessionEntity(
        gameId = gameId,
        platformId = platformId,
        startedAt = now,
        segmentStartedAt = now
    )

    /** Screen off: bank the running segment. */
    fun pause(s: PlaySessionEntity, now: Long): PlaySessionEntity {
        val seg = s.segmentStartedAt ?: return s
        return s.copy(durationMs = s.durationMs + (now - seg).coerceAtLeast(0), segmentStartedAt = null)
    }

    /** Screen on: start a new segment (no-op if one is already running). */
    fun resume(s: PlaySessionEntity, now: Long): PlaySessionEntity =
        if (s.segmentStartedAt != null) s else s.copy(segmentStartedAt = now)

    /** User came back to eOr while this process was watching the whole session. */
    fun finish(s: PlaySessionEntity, now: Long): PlaySessionEntity =
        pause(s, now).copy(endedAt = now)

    /**
     * Close a session this process didn't see end (eOr was killed mid-game). The banked time is
     * trusted; the segment that was running when we lost track is capped.
     */
    fun recover(s: PlaySessionEntity, now: Long): PlaySessionEntity {
        val seg = s.segmentStartedAt ?: return s.copy(endedAt = now)
        val credited = (now - seg).coerceIn(0, MAX_UNOBSERVED_SEGMENT_MS)
        return s.copy(durationMs = s.durationMs + credited, segmentStartedAt = null, endedAt = seg + credited)
    }

    fun isWorthKeeping(s: PlaySessionEntity) = s.durationMs >= MIN_SESSION_MS
}
