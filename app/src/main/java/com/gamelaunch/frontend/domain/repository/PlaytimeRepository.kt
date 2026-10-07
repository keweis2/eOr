package com.gamelaunch.frontend.domain.repository

import kotlinx.coroutines.flow.Flow

interface PlaytimeRepository {
    /** Total recorded play time for a game, in milliseconds. */
    fun totalPlaytime(gameId: Long): Flow<Long>

    /** Play time per local day for the last [days] days, oldest first; the last entry is today. */
    fun dailyPlaytime(gameId: Long, days: Int = 7): Flow<List<Long>>
}
