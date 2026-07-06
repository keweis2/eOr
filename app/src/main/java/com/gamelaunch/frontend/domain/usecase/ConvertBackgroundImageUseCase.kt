package com.gamelaunch.frontend.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Turns a user-picked photo into a single-colour "branding" silhouette that can be tinted to match
 * light/dark mode at draw time.
 *
 * The picked image is downsampled, then reduced to an alpha mask: each pixel's distance from white
 * (covering both dark and saturated-colour subjects) becomes its opacity, while near-white/near-
 * transparent areas drop out. Colour is discarded (RGB forced to white) so the mask can be recoloured
 * with any theme tint via [androidx.compose.ui.graphics.ColorFilter.tint]. The result is saved as a
 * PNG in filesDir and its path returned.
 */
class ConvertBackgroundImageUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val MAX_DIMENSION = 1080     // cap the mask size to keep memory/draw cheap
        private const val THRESHOLD = 0.14f        // below this "ink" level a pixel is fully transparent
        private const val CONTRAST_GAMMA = 0.85f   // <1 lifts mid-tones so faint subjects stay visible
        private val MAX_DIST = sqrt(3f) * 255f      // distance from white for pure black/opposite colour
    }

    /** @return absolute path of the saved mask PNG, or null if the image could not be processed. */
    suspend operator fun invoke(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val source = decodeDownsampled(uri) ?: return@runCatching null
            val mask = toAlphaMask(source)
            if (mask !== source) source.recycle()

            val dir = File(context.filesDir, "branding").apply { mkdirs() }
            // Clear any previous mask so we don't accumulate files, and so the new path differs
            // (a stable filename would defeat the path-keyed decode cache in the UI).
            dir.listFiles()?.forEach { it.delete() }
            val dest = File(dir, "background_mask_${System.currentTimeMillis()}.png")
            dest.outputStream().use { out ->
                mask.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            mask.recycle()
            dest.absolutePath
        }.getOrNull()
    }

    /** Decode the URI, downsampling so the longest edge is at most [MAX_DIMENSION]. */
    private fun decodeDownsampled(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / sample > MAX_DIMENSION) sample *= 2

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
    }

    /** Map each pixel to white-with-alpha, where alpha follows how far the pixel is from white. */
    private fun toAlphaMask(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        for (i in pixels.indices) {
            val p = pixels[i]
            val a = (p ushr 24) and 0xFF
            val r = (p ushr 16) and 0xFF
            val g = (p ushr 8) and 0xFF
            val b = p and 0xFF

            val dr = 255 - r
            val dg = 255 - g
            val db = 255 - b
            val dist = sqrt((dr * dr + dg * dg + db * db).toFloat()) / MAX_DIST

            var ink = ((dist - THRESHOLD) / (1f - THRESHOLD)).coerceIn(0f, 1f)
            ink = ink.pow(CONTRAST_GAMMA)
            val outAlpha = (ink * (a / 255f) * 255f).toInt().coerceIn(0, 255)

            // White RGB + computed alpha; the tint is applied later at draw time.
            pixels[i] = (outAlpha shl 24) or 0x00FFFFFF
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }
}
