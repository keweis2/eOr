package com.gamelaunch.frontend.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.gamelaunch.frontend.data.db.entity.GameMediaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GameMediaDao {

    @Query("SELECT * FROM game_media WHERE game_id = :gameId")
    suspend fun getMediaForGame(gameId: Long): GameMediaEntity?

    @Query("SELECT * FROM game_media WHERE game_id = :gameId")
    fun observeMediaForGame(gameId: Long): Flow<GameMediaEntity?>

    @Query("SELECT * FROM game_media")
    fun observeAllMedia(): Flow<List<GameMediaEntity>>

    @Query("""
        SELECT COALESCE(m.box_art_local, m.box_art_remote) FROM game_media m
        JOIN games g ON g.id = m.game_id
        WHERE g.platform_id = :platformId
          AND (:locked = 0 OR g.available_in_locked_mode = 1)
          AND (m.box_art_local IS NOT NULL OR m.box_art_remote IS NOT NULL)
        ORDER BY RANDOM() LIMIT :limit
    """)
    suspend fun getBoxArtSampleForPlatform(platformId: String, limit: Int, locked: Boolean): List<String>

    @Upsert
    suspend fun upsertMedia(entity: GameMediaEntity)

    @Query("DELETE FROM game_media WHERE game_id = :gameId")
    suspend fun deleteMediaForGame(gameId: Long)

    @Query("UPDATE game_media SET video_local = :localPath WHERE game_id = :gameId")
    suspend fun updateVideoLocalPath(gameId: Long, localPath: String)

    @Query("UPDATE game_media SET box_art_local = :localPath WHERE game_id = :gameId")
    suspend fun updateBoxArtLocalPath(gameId: Long, localPath: String)

    @Query("UPDATE game_media SET miximage_local = :localPath WHERE game_id = :gameId")
    suspend fun updateMiximageLocalPath(gameId: Long, localPath: String)
}
