package com.gamelaunch.frontend.domain.repository

import com.gamelaunch.frontend.domain.model.Game
import kotlinx.coroutines.flow.Flow

interface GameRepository {
    fun getAllGames(): Flow<List<Game>>
    fun getGamesByPlatform(platformId: String): Flow<List<Game>>
    suspend fun getGameById(id: Long): Game?
    suspend fun getUnscrapedGames(): List<Game>
    /** Games missing any of the enabled scrape outputs (skips ones that already have everything). */
    suspend fun getGamesNeedingScrape(
        needMeta: Boolean,
        needBox: Boolean,
        needShot: Boolean,
        needWheel: Boolean,
        needVideo: Boolean
    ): List<Game>
    fun getFavorites(): Flow<List<Game>>
    fun getRecentlyPlayed(limit: Int = 20): Flow<List<Game>>
    fun getDistinctPlatformIds(): Flow<List<String>>
    fun getPlatformCounts(): Flow<Map<String, Int>>
    suspend fun insertGame(game: Game): Long
    suspend fun insertGames(games: List<Game>)
    suspend fun updateGame(game: Game)
    suspend fun updateScrapedMetadata(gameId: Long, scraperGameId: Long?, title: String, description: String?, genre: String?, releaseYear: Int?, rating: Float?)
    /** Mark a game as scraped and keep its title without touching description/genre/year/rating. */
    suspend fun markScraped(gameId: Long, title: String)
    suspend fun setFavorite(gameId: Long, isFavorite: Boolean)
    suspend fun recordPlay(gameId: Long)
    suspend fun deleteGamesNotInPaths(validPaths: List<String>): Int
    suspend fun deleteAllNonAndroidGames(): Int
    suspend fun deleteAndroidGamesNotIn(validPaths: List<String>): Int
    suspend fun deleteAllAndroidGames(): Int
    suspend fun getTotalCount(): Int
}
