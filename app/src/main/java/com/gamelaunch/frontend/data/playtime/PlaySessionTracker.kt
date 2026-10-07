package com.gamelaunch.frontend.data.playtime

import com.gamelaunch.frontend.data.db.dao.PlaySessionDao
import com.gamelaunch.frontend.domain.model.Game
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Records play sessions: opened on a successful launch, closed when the user returns to eOr,
 * paused while the screen is off. Sessions are persisted as they go, so one survives eOr being
 * killed mid-game and is closed (with a capped final stretch) on the next return.
 */
@Singleton
class PlaySessionTracker internal constructor(
    private val dao: PlaySessionDao,
    private val clock: () -> Long
) {
    @Inject constructor(dao: PlaySessionDao) : this(dao, System::currentTimeMillis)

    private val mutex = Mutex()

    /** Sessions opened by this process — the only ones whose screen on/off we actually observed. */
    private val observed = mutableSetOf<Long>()

    suspend fun onGameLaunched(game: Game) = mutex.withLock {
        val now = clock()
        // Launching from eOr means the user is in eOr, so anything still open has ended.
        closeOpen(now)
        val id = dao.insert(PlaySessions.start(game.id, game.platformId, now))
        observed += id
    }

    /** The user is back in eOr (foreground on single-screen, focus back on dual-screen). */
    suspend fun onReturnedToEor() = mutex.withLock { closeOpen(clock()) }

    suspend fun onScreenOff() = mutex.withLock {
        val now = clock()
        dao.getOpen().filter { it.id in observed }.forEach { dao.update(PlaySessions.pause(it, now)) }
    }

    suspend fun onScreenOn() = mutex.withLock {
        val now = clock()
        dao.getOpen().filter { it.id in observed }.forEach { dao.update(PlaySessions.resume(it, now)) }
    }

    private suspend fun closeOpen(now: Long) {
        dao.getOpen().forEach { open ->
            val closed = if (open.id in observed) PlaySessions.finish(open, now) else PlaySessions.recover(open, now)
            observed -= open.id
            if (PlaySessions.isWorthKeeping(closed)) dao.update(closed) else dao.delete(open.id)
        }
    }
}
