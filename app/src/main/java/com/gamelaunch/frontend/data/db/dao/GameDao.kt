package com.gamelaunch.frontend.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.gamelaunch.frontend.data.db.entity.GameEntity
import kotlinx.coroutines.flow.Flow

data class PlatformCount(val platformId: String, val count: Int)

@Dao
interface GameDao {

    @Query("SELECT * FROM games ORDER BY title ASC")
    fun getAllGames(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE platform_id = :platformId AND rom_filename NOT LIKE '.%' ORDER BY title ASC")
    fun getGamesByPlatform(platformId: String): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun getGameById(id: Long): GameEntity?

    @Query("SELECT * FROM games WHERE is_scraped = 0 ORDER BY title ASC")
    suspend fun getUnscrapedGames(): List<GameEntity>

    @Query("SELECT * FROM games WHERE is_favorite = 1 ORDER BY title ASC")
    fun getFavorites(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE last_played_ms IS NOT NULL ORDER BY last_played_ms DESC LIMIT :limit")
    fun getRecentlyPlayed(limit: Int = 20): Flow<List<GameEntity>>

    @Query("SELECT DISTINCT platform_id FROM games WHERE rom_filename NOT LIKE '.%' ORDER BY platform_id ASC")
    fun getDistinctPlatformIds(): Flow<List<String>>

    @Query("SELECT platform_id AS platformId, COUNT(*) AS count FROM games WHERE rom_filename NOT LIKE '.%' GROUP BY platform_id")
    fun getPlatformCounts(): Flow<List<PlatformCount>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGame(entity: GameEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGames(entities: List<GameEntity>): List<Long>

    @Update
    suspend fun updateGame(entity: GameEntity)

    @Query("""
        UPDATE games SET
            scraper_game_id = :scraperGameId,
            description = :description,
            genre = :genre,
            release_year = :releaseYear,
            rating = :rating,
            is_scraped = 1
        WHERE id = :gameId
    """)
    suspend fun updateScrapedMetadata(
        gameId: Long,
        scraperGameId: Long?,
        description: String?,
        genre: String?,
        releaseYear: Int?,
        rating: Float?
    )

    @Query("UPDATE games SET title = :title, is_scraped = 1 WHERE id = :gameId")
    suspend fun updateTitle(gameId: Long, title: String)

    @Query("UPDATE games SET is_favorite = :isFavorite WHERE id = :gameId")
    suspend fun setFavorite(gameId: Long, isFavorite: Boolean)

    @Query("UPDATE games SET last_played_ms = :timestamp, play_count = play_count + 1 WHERE id = :gameId")
    suspend fun recordPlay(gameId: Long, timestamp: Long)

    @Query("DELETE FROM games WHERE rom_path NOT IN (:validPaths)")
    suspend fun deleteGamesNotInPaths(validPaths: List<String>): Int

    @Query("DELETE FROM games WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM games")
    suspend fun getTotalCount(): Int

    @Query("SELECT COUNT(*) FROM games WHERE platform_id = :platformId")
    suspend fun getCountForPlatform(platformId: String): Int
}
