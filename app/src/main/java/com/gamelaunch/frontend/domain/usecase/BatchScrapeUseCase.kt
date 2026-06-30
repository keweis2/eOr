package com.gamelaunch.frontend.domain.usecase

import android.database.sqlite.SQLiteFullException
import com.gamelaunch.frontend.domain.model.ScraperConfig
import com.gamelaunch.frontend.domain.repository.GameRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.IOException
import javax.inject.Inject

data class BatchScrapeState(
    val total: Int,
    val completed: Int,
    val succeeded: Int,
    val notFound: Int,
    val errors: Int,
    val currentGameTitle: String = "",
    val isFinished: Boolean = false,
    val storageFull: Boolean = false,
    val results: List<ScrapeResult> = emptyList()
)

/** True when a throwable (or any of its causes) is an out-of-disk-space error. */
private fun Throwable.isOutOfSpace(): Boolean {
    var t: Throwable? = this
    while (t != null) {
        if (t is SQLiteFullException) return true
        if (t is IOException && (t.message?.contains("ENOSPC", true) == true ||
                                 t.message?.contains("No space left", true) == true)) return true
        t = t.cause
    }
    return false
}

class BatchScrapeUseCase @Inject constructor(
    private val gameRepository: GameRepository,
    private val scrapeGameUseCase: ScrapeGameUseCase
) {
    operator fun invoke(config: ScraperConfig): Flow<BatchScrapeState> = flow {
        // Only scrape games missing something the user has enabled — fully-complete games are
        // skipped so re-running the scrape doesn't re-fetch everything.
        val games = gameRepository.getGamesNeedingScrape(
            needMeta  = config.scrapeMetadata,
            needBox   = config.scrapeBoxArt,
            needShot  = config.scrapeScreenshots,
            needWheel = config.scrapeWheelLogos,
            needVideo = config.scrapeVideos
        )
        val total = games.size
        val results = mutableListOf<ScrapeResult>()
        var succeeded = 0
        var notFound = 0
        var errors = 0

        if (total == 0) {
            emit(BatchScrapeState(0, 0, 0, 0, 0, isFinished = true))
            return@flow
        }

        games.forEachIndexed { index, game ->
            emit(BatchScrapeState(total, index, succeeded, notFound, errors, game.title, results = results.toList()))

            val result = try {
                scrapeGameUseCase(game, config)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Out of disk space: every remaining write will fail too, so stop now and tell
                // the user instead of crashing the app.
                if (e.isOutOfSpace()) {
                    emit(BatchScrapeState(total, index, succeeded, notFound, errors,
                        isFinished = true, storageFull = true, results = results.toList()))
                    return@flow
                }
                ScrapeResult.Error(game.id, e)
            }
            results.add(result)

            when (result) {
                is ScrapeResult.Success -> succeeded++
                is ScrapeResult.NotFound -> notFound++
                is ScrapeResult.RateLimited -> {
                    errors++
                    delay(5000) // back off extra on 429
                }
                is ScrapeResult.Error -> errors++
            }
        }

        emit(BatchScrapeState(total, total, succeeded, notFound, errors, isFinished = true, results = results.toList()))
    }
}
