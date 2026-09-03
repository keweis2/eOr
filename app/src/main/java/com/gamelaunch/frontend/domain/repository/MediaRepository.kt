package com.gamelaunch.frontend.domain.repository

import com.gamelaunch.frontend.domain.model.GameMedia
import kotlinx.coroutines.flow.Flow

/** Kinds of per-game media a user can push from the Web Transfer page. */
enum class MediaUploadType { BOX_ART, SCREENSHOT, WHEEL, MIXIMAGE, VIDEO }

interface MediaRepository {
    suspend fun getMediaForGame(gameId: Long): GameMedia?
    fun observeMediaForGame(gameId: Long): Flow<GameMedia?>
    fun observeAllMedia(): Flow<Map<Long, GameMedia>>
    suspend fun boxArtSampleForPlatform(platformId: String, limit: Int, locked: Boolean = false): List<String>
    /** Every on-disk box-art path, for the background thumbnail pre-generation pass. */
    suspend fun allBoxArtLocalPaths(): List<String>
    suspend fun upsertMedia(media: GameMedia)
    suspend fun downloadAndCacheBoxArt(gameId: Long, url: String): String?
    /** Stores artwork decoded locally from a game package. */
    suspend fun saveEmbeddedBoxArt(gameId: Long, bytes: ByteArray): String?
    /** Stores media bytes uploaded via Web Transfer for [gameId]; returns the stored path or null. */
    suspend fun saveUploadedMedia(gameId: Long, type: MediaUploadType, bytes: ByteArray): String?
    suspend fun downloadAndCacheVideo(gameId: Long, url: String): String?
    suspend fun downloadAndCacheScreenshot(gameId: Long, url: String): String?
    suspend fun downloadAndCacheWheelLogo(gameId: Long, url: String): String?
    suspend fun downloadAndCacheMiximage(gameId: Long, url: String): String?
    suspend fun deleteMediaForGame(gameId: Long)
}
