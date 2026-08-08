package com.gamelaunch.frontend.domain.repository

import com.gamelaunch.frontend.domain.model.GameMedia
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    suspend fun getMediaForGame(gameId: Long): GameMedia?
    fun observeMediaForGame(gameId: Long): Flow<GameMedia?>
    fun observeAllMedia(): Flow<Map<Long, GameMedia>>
    suspend fun boxArtSampleForPlatform(platformId: String, limit: Int): List<String>
    suspend fun upsertMedia(media: GameMedia)
    suspend fun downloadAndCacheBoxArt(gameId: Long, url: String): String?
    /** Stores artwork decoded locally from a game package. */
    suspend fun saveEmbeddedBoxArt(gameId: Long, bytes: ByteArray): String?
    suspend fun downloadAndCacheVideo(gameId: Long, url: String): String?
    suspend fun downloadAndCacheScreenshot(gameId: Long, url: String): String?
    suspend fun downloadAndCacheWheelLogo(gameId: Long, url: String): String?
    suspend fun downloadAndCacheMiximage(gameId: Long, url: String): String?
    suspend fun deleteMediaForGame(gameId: Long)
}
