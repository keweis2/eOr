package com.gamelaunch.frontend.domain.repository

import com.gamelaunch.frontend.domain.model.Game
import kotlinx.coroutines.flow.Flow

interface GameRepository {
    fun getAllGames(): Flow<List<Game>>
    fun getGamesByPlatform(platformId: String, locked: Boolean = false): Flow<List<Game>>
    suspend fun getGameById(id: Long): Game?
    suspend fun getGameByRomPath(romPath: String): Game?
    suspend fun getUnscrapedGames(): List<Game>
    /** Rom paths of all non-Android games — a cheap set for the launch "any new ROMs?" check. */
    suspend fun getNonAndroidRomPaths(): List<String>
    /** Games missing any enabled scrape output (artwork types + description when metadata is on). */
    suspend fun getGamesNeedingScrape(
        needMeta: Boolean,
        needBox: Boolean,
        needShot: Boolean,
        needWheel: Boolean,
        needVideo: Boolean
    ): List<Game>
    fun getFavorites(locked: Boolean = false): Flow<List<Game>>
    fun getRecentlyPlayed(limit: Int = 20, locked: Boolean = false): Flow<List<Game>>
    fun getDistinctPlatformIds(locked: Boolean = false): Flow<List<String>>
    fun getPlatformCounts(locked: Boolean = false): Flow<Map<String, Int>>
    suspend fun insertGame(game: Game): Long
    suspend fun insertGames(games: List<Game>)
    suspend fun updateGame(game: Game)
    suspend fun updateScrapedMetadata(gameId: Long, scraperGameId: Long?, title: String, description: String?, genre: String?, releaseYear: Int?, rating: Float?)
    /** Mark a game as scraped and keep its title without touching description/genre/year/rating. */
    suspend fun markScraped(gameId: Long, title: String)
    /** Fill a game's description only if it's currently empty (used by ES-DE gamelist.xml import). */
    suspend fun fillDescriptionIfMissing(gameId: Long, description: String)
    suspend fun setFavorite(gameId: Long, isFavorite: Boolean)
    suspend fun setAvailableInLockedMode(gameId: Long, available: Boolean)
    suspend fun recordPlay(gameId: Long)
    suspend fun deleteGamesNotInPaths(validPaths: List<String>): Int
    suspend fun deleteAllNonAndroidGames(): Int
    suspend fun deleteAndroidGamesNotIn(validPaths: List<String>): Int
    suspend fun deleteAllAndroidGames(): Int
    /** Remove a single game row by id (used by the manual "remove from library" action). */
    suspend fun deleteGame(id: Long)
    suspend fun getTotalCount(): Int
}
