package com.gamelaunch.frontend.data.repository

import android.content.Context
import com.gamelaunch.frontend.data.db.dao.GameMediaDao
import com.gamelaunch.frontend.data.db.entity.GameMediaEntity
import com.gamelaunch.frontend.data.preferences.AppDataStore
import com.gamelaunch.frontend.domain.model.GameMedia
import com.gamelaunch.frontend.domain.repository.MediaRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameMediaDao: GameMediaDao,
    private val dataStore: AppDataStore,
    private val okHttpClient: OkHttpClient
) : MediaRepository {

    private val defaultMediaDir: File
        get() = File(context.filesDir, "media").also { it.mkdirs() }

    /**
     * Folder where scraped media is written. Uses the user's chosen folder (e.g. an SD card) when
     * set and writable — an "eor_media" subfolder inside it — otherwise the app's internal default.
     */
    private suspend fun mediaDir(): File {
        val custom = dataStore.mediaStoragePath.first()
        if (custom.isNotBlank() && !custom.startsWith("content://")) {
            val dir = File(custom, "eor_media")
            val ok = runCatching { dir.mkdirs(); dir.exists() && dir.canWrite() }.getOrDefault(false)
            if (ok) return dir
        }
        return defaultMediaDir
    }

    override suspend fun getMediaForGame(gameId: Long): GameMedia? =
        gameMediaDao.getMediaForGame(gameId)?.toDomain()

    override fun observeMediaForGame(gameId: Long): Flow<GameMedia?> =
        gameMediaDao.observeMediaForGame(gameId).map { it?.toDomain() }

    override fun observeAllMedia(): Flow<Map<Long, GameMedia>> =
        gameMediaDao.observeAllMedia().map { list -> list.associate { it.gameId to it.toDomain() } }

    override suspend fun boxArtSampleForPlatform(platformId: String, limit: Int): List<String> =
        gameMediaDao.getBoxArtSampleForPlatform(platformId, limit)

    override suspend fun upsertMedia(media: GameMedia) {
        // @Upsert matches on the primary key `id`, not the unique game_id index. A fresh
        // GameMedia has id = 0, so for a game that already has a media row the insert would
        // conflict and the fallback UPDATE (WHERE id = 0) silently no-ops. Merge against the
        // existing row instead: keep its id, and only overwrite fields the caller actually set.
        val existing = gameMediaDao.getMediaForGame(media.gameId)
        val merged = (existing ?: GameMediaEntity(gameId = media.gameId)).copy(
            boxArtLocalPath     = media.boxArtLocalPath     ?: existing?.boxArtLocalPath,
            boxArtRemoteUrl     = media.boxArtRemoteUrl     ?: existing?.boxArtRemoteUrl,
            screenshotLocalPath = media.screenshotLocalPath ?: existing?.screenshotLocalPath,
            screenshotRemoteUrl = media.screenshotRemoteUrl ?: existing?.screenshotRemoteUrl,
            wheelLogoLocalPath  = media.wheelLogoLocalPath  ?: existing?.wheelLogoLocalPath,
            wheelLogoRemoteUrl  = media.wheelLogoRemoteUrl  ?: existing?.wheelLogoRemoteUrl,
            videoLocalPath      = media.videoLocalPath      ?: existing?.videoLocalPath,
            videoRemoteUrl      = media.videoRemoteUrl      ?: existing?.videoRemoteUrl,
            backgroundLocalPath = media.backgroundLocalPath ?: existing?.backgroundLocalPath,
            miximageLocalPath   = media.miximageLocalPath   ?: existing?.miximageLocalPath,
            miximageRemoteUrl   = media.miximageRemoteUrl   ?: existing?.miximageRemoteUrl,
            scraperTimestampMs  = media.scraperTimestampMs  ?: existing?.scraperTimestampMs,
        )
        gameMediaDao.upsertMedia(merged)
    }

    override suspend fun downloadAndCacheBoxArt(gameId: Long, url: String): String? =
        downloadFile(url, File(mediaDir(), "boxart/${gameId}.jpg"))?.also { path ->
            gameMediaDao.updateBoxArtLocalPath(gameId, path)
        }

    override suspend fun saveEmbeddedBoxArt(gameId: Long, bytes: ByteArray): String? = withContext(Dispatchers.IO) {
        runCatching {
            require(bytes.isNotEmpty())
            val extension = bytes.embeddedImageExtension()
                ?: error("Embedded artwork is not a PNG or JPEG")
            val destination = File(mediaDir(), "boxart/${gameId}.$extension")
            destination.parentFile?.mkdirs()
            destination.outputStream().use { it.write(bytes) }
            // A newly scanned game has no game_media row yet, so UPDATE alone silently affects
            // zero rows. Merge through the repository helper to create or update that row.
            upsertMedia(GameMedia(gameId = gameId, boxArtLocalPath = destination.absolutePath))
            destination.absolutePath
        }.getOrNull()
    }

    override suspend fun downloadAndCacheVideo(gameId: Long, url: String): String? =
        downloadFile(url, File(mediaDir(), "videos/${gameId}.mp4"))?.also { path ->
            gameMediaDao.updateVideoLocalPath(gameId, path)
        }

    override suspend fun downloadAndCacheScreenshot(gameId: Long, url: String): String? =
        downloadFile(url, File(mediaDir(), "screenshots/${gameId}.jpg"))

    override suspend fun downloadAndCacheWheelLogo(gameId: Long, url: String): String? =
        downloadFile(url, File(mediaDir(), "wheels/${gameId}.png"))

    override suspend fun downloadAndCacheMiximage(gameId: Long, url: String): String? =
        downloadFile(url, File(mediaDir(), "miximages/${gameId}.png"))?.also { path ->
            gameMediaDao.updateMiximageLocalPath(gameId, path)
        }

    override suspend fun deleteMediaForGame(gameId: Long) {
        gameMediaDao.deleteMediaForGame(gameId)
        // Media may live in either the current folder or the internal default (if the user moved it).
        listOf(mediaDir(), defaultMediaDir).distinct().forEach { base ->
            listOf("boxart", "videos", "screenshots", "wheels", "miximages").forEach { dir ->
                File(base, dir).listFiles()
                    ?.filter { it.nameWithoutExtension == gameId.toString() }
                    ?.forEach { it.delete() }
            }
        }
    }

    private suspend fun downloadFile(url: String, dest: File): String? = withContext(Dispatchers.IO) {
        runCatching {
            dest.parentFile?.mkdirs()
            if (dest.exists() && dest.length() > 0) return@withContext dest.absolutePath

            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                response.body?.byteStream()?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
            }
            dest.absolutePath
        }.getOrNull()
    }
}

private fun ByteArray.embeddedImageExtension(): String? = when {
    size >= 8 &&
        this[0] == 0x89.toByte() && this[1] == 0x50.toByte() &&
        this[2] == 0x4e.toByte() && this[3] == 0x47.toByte() &&
        this[4] == 0x0d.toByte() && this[5] == 0x0a.toByte() &&
        this[6] == 0x1a.toByte() && this[7] == 0x0a.toByte() -> "png"
    size >= 3 &&
        this[0] == 0xff.toByte() && this[1] == 0xd8.toByte() &&
        this[2] == 0xff.toByte() -> "jpg"
    else -> null
}

private fun GameMediaEntity.toDomain() = GameMedia(
    id = id,
    gameId = gameId,
    boxArtLocalPath = boxArtLocalPath,
    boxArtRemoteUrl = boxArtRemoteUrl,
    screenshotLocalPath = screenshotLocalPath,
    screenshotRemoteUrl = screenshotRemoteUrl,
    wheelLogoLocalPath = wheelLogoLocalPath,
    wheelLogoRemoteUrl = wheelLogoRemoteUrl,
    videoLocalPath = videoLocalPath,
    videoRemoteUrl = videoRemoteUrl,
    backgroundLocalPath = backgroundLocalPath,
    miximageLocalPath = miximageLocalPath,
    miximageRemoteUrl = miximageRemoteUrl,
    scraperTimestampMs = scraperTimestampMs
)

private fun GameMedia.toEntity() = GameMediaEntity(
    id = id,
    gameId = gameId,
    boxArtLocalPath = boxArtLocalPath,
    boxArtRemoteUrl = boxArtRemoteUrl,
    screenshotLocalPath = screenshotLocalPath,
    screenshotRemoteUrl = screenshotRemoteUrl,
    wheelLogoLocalPath = wheelLogoLocalPath,
    wheelLogoRemoteUrl = wheelLogoRemoteUrl,
    videoLocalPath = videoLocalPath,
    videoRemoteUrl = videoRemoteUrl,
    backgroundLocalPath = backgroundLocalPath,
    miximageLocalPath = miximageLocalPath,
    miximageRemoteUrl = miximageRemoteUrl,
    scraperTimestampMs = scraperTimestampMs
)
