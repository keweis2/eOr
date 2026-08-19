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

    @Query(
        """
        SELECT * FROM games
        WHERE platform_id = :platformId
          AND rom_filename NOT LIKE '.%'
          AND (:locked = 0 OR available_in_locked_mode = 1)
        ORDER BY title ASC
        """
    )
    fun getGamesByPlatform(platformId: String, locked: Boolean): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun getGameById(id: Long): GameEntity?

    @Query("SELECT * FROM games WHERE rom_path = :romPath LIMIT 1")
    suspend fun getGameByRomPath(romPath: String): GameEntity?

    @Query("SELECT * FROM games WHERE is_scraped = 0 ORDER BY title ASC")
    suspend fun getUnscrapedGames(): List<GameEntity>

    /** Just the rom paths of non-Android games — a cheap set for the launch "any new ROMs?" check. */
    @Query("SELECT rom_path FROM games WHERE platform_id != 'android'")
    suspend fun getNonAndroidRomPaths(): List<String>

    /**
     * Games that still need scraping: missing any enabled artwork (box art / screenshots / wheel
     * logos / videos) or, when metadata is enabled, a description. A game is judged purely on what it
     * actually has — art and descriptions imported from ES-DE, an embedded ROM cover, or a previous
     * scrape all count — so a game that already has everything enabled is skipped even if it never
     * went through the network scraper. Flags are passed as 1/0.
     *
     * Deliberately NOT gated on `is_scraped`: that flag is only set by the network scraper, so gating
     * on it re-queued every ES-DE-/embedded-imported game (which already has art) — a big cause of an
     * implausibly huge "needs scraping" count. Descriptions from ES-DE's gamelist.xml are imported so
     * the metadata clause below doesn't flag games that already have a local description.
     */
    @Query(
        """
        SELECT g.* FROM games g
        LEFT JOIN game_media m ON m.game_id = g.id
        WHERE g.rom_filename NOT LIKE '.%'
          AND (
            (:needMeta = 1 AND g.description IS NULL)
            OR (:needBox = 1 AND m.box_art_local IS NULL AND m.box_art_remote IS NULL)
            OR (:needShot = 1 AND m.screenshot_local IS NULL AND m.screenshot_remote IS NULL)
            OR (:needWheel = 1 AND m.wheel_logo_local IS NULL AND m.wheel_logo_remote IS NULL)
            OR (:needVideo = 1 AND m.video_local IS NULL AND m.video_remote IS NULL)
          )
        ORDER BY g.title ASC
        """
    )
    suspend fun getGamesNeedingScrape(
        needMeta: Int,
        needBox: Int,
        needShot: Int,
        needWheel: Int,
        needVideo: Int
    ): List<GameEntity>

    @Query(
        """
        SELECT * FROM games
        WHERE is_favorite = 1
          AND (:locked = 0 OR available_in_locked_mode = 1)
        ORDER BY title ASC
        """
    )
    fun getFavorites(locked: Boolean): Flow<List<GameEntity>>

    @Query(
        """
        SELECT * FROM games
        WHERE last_played_ms IS NOT NULL
          AND (:locked = 0 OR available_in_locked_mode = 1)
        ORDER BY last_played_ms DESC
        LIMIT :limit
        """
    )
    fun getRecentlyPlayed(limit: Int = 20, locked: Boolean): Flow<List<GameEntity>>

    @Query(
        """
        SELECT DISTINCT platform_id FROM games
        WHERE rom_filename NOT LIKE '.%'
          AND (:locked = 0 OR available_in_locked_mode = 1)
        ORDER BY platform_id ASC
        """
    )
    fun getDistinctPlatformIds(locked: Boolean): Flow<List<String>>

    @Query(
        """
        SELECT platform_id AS platformId, COUNT(*) AS count
        FROM games
        WHERE rom_filename NOT LIKE '.%'
          AND (:locked = 0 OR available_in_locked_mode = 1)
        GROUP BY platform_id
        """
    )
    fun getPlatformCounts(locked: Boolean): Flow<List<PlatformCount>>

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

    /**
     * Rename a game without marking it scraped. Used by the scan to backfill arcade romset short
     * names ("afighter") with their real titles ("Action Fighter") on existing library entries,
     * while leaving them eligible for a later online metadata scrape.
     */
    @Query("UPDATE games SET title = :title WHERE id = :gameId")
    suspend fun renameGame(gameId: Long, title: String)

    /** Fill a game's description only if it doesn't already have one (ES-DE gamelist.xml import). */
    @Query("UPDATE games SET description = :description WHERE id = :gameId AND (description IS NULL OR description = '')")
    suspend fun fillDescriptionIfMissing(gameId: Long, description: String)

    @Query("UPDATE games SET is_favorite = :isFavorite WHERE id = :gameId")
    suspend fun setFavorite(gameId: Long, isFavorite: Boolean)

    @Query(
        """
        UPDATE games
        SET available_in_locked_mode = :available
        WHERE id = :gameId
        """
    )
    suspend fun setAvailableInLockedMode(gameId: Long, available: Boolean)

    @Query("UPDATE games SET last_played_ms = :timestamp, play_count = play_count + 1 WHERE id = :gameId")
    suspend fun recordPlay(gameId: Long, timestamp: Long)

    // Reconciles ROM-folder games only. Steam entries (rom_path "steam:…") are not files under the
    // ROM root and are never in validPaths, so the synthetic 'steam' platform is excluded here —
    // otherwise a full ROM scan would delete the whole Steam library.
    @Query("DELETE FROM games WHERE platform_id NOT IN ('android', 'steam') AND rom_path NOT IN (:validPaths)")
    suspend fun deleteGamesNotInPaths(validPaths: List<String>): Int

    // Used when the ROM folder is empty. Still must not touch Android or Steam games.
    @Query("DELETE FROM games WHERE platform_id NOT IN ('android', 'steam')")
    suspend fun deleteAllNonAndroidGames(): Int

    @Query("DELETE FROM games WHERE platform_id = 'android' AND rom_path NOT IN (:validPaths)")
    suspend fun deleteAndroidGamesNotIn(validPaths: List<String>): Int

    @Query("DELETE FROM games WHERE platform_id = 'android'")
    suspend fun deleteAllAndroidGames(): Int

    @Query("DELETE FROM games WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM games")
    suspend fun getTotalCount(): Int

    @Query("SELECT COUNT(*) FROM games WHERE platform_id = :platformId")
    suspend fun getCountForPlatform(platformId: String): Int
}
