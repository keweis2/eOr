package com.gamelaunch.frontend.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import coil.ImageLoader
import coil.intercept.Interceptor
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.ImageResult
import coil.request.SuccessResult
import coil.size.Scale
import com.gamelaunch.frontend.ui.component.BOX_ART_TILE_PX
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest

/**
 * The whole point of this file: the imported covers are 0.5–1.3 MB PNGs on a (slow) SD card, so
 * decoding one just to fill a ~250 px tile means reading and parsing a full ~1 MB file every single
 * time — the real reason a fresh screenful of art takes many seconds to appear. We can't make that
 * first decode cheap, but we only have to pay it ONCE: the first time a cover is decoded we write a
 * tiny (~20 KB) JPEG thumbnail beside it, and every load after reads that instead — ~50× less I/O,
 * which is what makes browsing feel instant. Full build only (wired up in GameLauncherApp); the lite
 * build is untouched.
 */

/** Coil request parameter marking a request as a box-art TILE load (eligible for thumbnailing). */
const val BOX_ART_THUMB_PARAM = "eor#boxArtThumbnail"

/**
 * Opt a compact box-art request into the on-disk thumbnail cache. Deliberately NOT set on the
 * full-res detail hero, so that view keeps decoding the source at full resolution.
 */
fun ImageRequest.Builder.boxArtThumbnail(): ImageRequest.Builder =
    setParameter(BOX_ART_THUMB_PARAM, true, memoryCacheKey = null)

/** Persistent on-disk store of small box-art thumbnails. Stateless beyond its directory. */
class BoxArtThumbnailStore(context: Context) {
    // filesDir (not cacheDir): the OS can wipe cacheDir under storage pressure, and rebuilding every
    // thumbnail means paying the slow 1 MB decodes all over again. These are cheap to keep.
    private val dir = File(context.filesDir, "boxart_thumbs").also { it.mkdirs() }

    fun thumbFileFor(sourcePath: String): File = File(dir, sha1(sourcePath) + ".jpg")

    /** A thumbnail is usable only if it exists and is at least as new as its source cover. */
    fun isFresh(thumb: File, source: File): Boolean =
        thumb.exists() && thumb.lastModified() >= source.lastModified()

    fun write(thumb: File, source: File, bitmap: Bitmap) {
        if (bitmap.isRecycled) return
        runCatching {
            val tmp = File(dir, thumb.name + ".tmp")   // write-then-rename so readers never see a partial file
            tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            if (!tmp.renameTo(thumb)) { tmp.copyTo(thumb, overwrite = true); tmp.delete() }
            thumb.setLastModified(maxOf(source.lastModified(), System.currentTimeMillis()))
        }
    }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
}

/**
 * Serves box-art tile requests from [store]: swaps in the tiny thumbnail when a fresh one exists, and
 * writes one from the decoded bitmap the first time a cover is loaded. Only touches requests marked
 * [boxArtThumbnail]; everything else (remote images, the full-res detail hero) passes straight through.
 */
class BoxArtThumbnailInterceptor(
    private val store: BoxArtThumbnailStore,
) : Interceptor {
    // Fire-and-forget thumbnail writes; a failed write just means we regenerate next time.
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        if (request.parameters.value<Boolean>(BOX_ART_THUMB_PARAM) != true) return chain.proceed(request)
        val sourcePath = localPath(request.data) ?: return chain.proceed(request)

        val source = File(sourcePath)
        val thumb = store.thumbFileFor(sourcePath)
        if (store.isFresh(thumb, source)) {
            // Load the ~20 KB thumbnail instead of the big source PNG. Keep the ORIGINAL request's
            // memory-cache key (the source path) so tiles, prefetch and prewarm all share one entry.
            return chain.proceed(request.newBuilder().data(thumb).build())
        }

        // First time for this cover: decode the source (the slow part), then persist a thumbnail so
        // every future load is cheap.
        val result = chain.proceed(request)
        if (result is SuccessResult) {
            (result.drawable as? BitmapDrawable)?.bitmap?.let { bmp ->
                io.launch { store.write(thumb, source, bmp) }
            }
        }
        return result
    }

    private fun localPath(data: Any?): String? = when (data) {
        is File -> data.path
        is String -> if (data.startsWith("http", ignoreCase = true)) null else data
        else -> null
    }
}

/**
 * One-time background pass so the FIRST visit to a system is fast too, not only repeat visits: decode
 * every cover that lacks a fresh thumbnail once, throttled to a few concurrent decodes with yields so
 * it never janks browsing. Cheap on later runs — it skips covers that already have a thumbnail.
 */
suspend fun pregenerateBoxArtThumbnails(
    loader: ImageLoader,
    context: Context,
    store: BoxArtThumbnailStore,
    paths: List<String>,
    // Deliberately gentle: this is a one-time background chore, so it must never make the device hot
    // or steal decode threads/SD bandwidth from the covers the user is actively browsing. One decode
    // at a time with a breather between them sips CPU instead of pegging it; the first pass just takes
    // a little longer, unobtrusively. It also skips covers that already have a thumbnail, so every run
    // after the first is almost free.
    pacingDelayMs: Long = 60L,
) {
    for (path in paths) {
        val source = File(path)
        if (store.isFresh(store.thumbFileFor(path), source)) continue
        runCatching {
            loader.execute(
                ImageRequest.Builder(context)
                    .data(source)
                    .memoryCacheKey(path)
                    .size(BOX_ART_TILE_PX, BOX_ART_TILE_PX)
                    .scale(Scale.FILL)
                    // Build the disk thumbnail without holding the bitmap in RAM — this pass touches
                    // the whole library and must not evict the covers on screen.
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .boxArtThumbnail()
                    .build()
            )
        }
        delay(pacingDelayMs)
    }
}
