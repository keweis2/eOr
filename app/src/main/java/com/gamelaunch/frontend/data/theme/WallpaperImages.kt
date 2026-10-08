package com.gamelaunch.frontend.data.theme

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** Turns an imported or picked image into the JPEG a theme stores. Throws [ThemeFileException]. */
fun interface WallpaperNormalizer {
    fun normalize(bytes: ByteArray): ByteArray
}

/**
 * Decoding for theme background images. Imported images are shrunk to at most [MAX_SIDE] px and
 * re-encoded as JPEG, so a theme stays small to share and nothing huge is ever decoded at draw
 * time. For display the blur is baked in once, here, instead of blurring every frame — that keeps
 * it cheap on the lite build's low-power chipsets and works on every Android version.
 */
object WallpaperImages : WallpaperNormalizer {

    private const val MAX_SIDE = 1920
    private const val MAX_SOURCE_PIXELS = 80_000_000L
    private const val TARGET_BYTES = 900 * 1024

    override fun normalize(bytes: ByteArray): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) throw ThemeFileException("That image couldn't be read")
        if (w.toLong() * h > MAX_SOURCE_PIXELS) throw ThemeFileException("That image is too large")
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
            inSampleSize = sampleSize(max(w, h), MAX_SIDE)
        }) ?: throw ThemeFileException("That image couldn't be read")
        val scaled = fit(decoded, MAX_SIDE)
        try {
            // Step quality down until it's comfortably small; photos rarely need the second pass.
            var quality = 85
            while (true) {
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                if (out.size() <= TARGET_BYTES || quality <= 55) return out.toByteArray()
                quality -= 10
            }
        } finally {
            if (scaled !== decoded) scaled.recycle()
            decoded.recycle()
        }
    }

    /**
     * The bitmap AmbientBackground draws for [file]. With [blur] > 0 it's shrunk and box-blurred,
     * so it's also small in memory; it gets stretched back to full screen when drawn.
     */
    fun loadForDisplay(file: File, blur: Float): Bitmap? =
        loadForDisplay(blur) { opts -> BitmapFactory.decodeFile(file.path, opts) }

    /** [loadForDisplay] for an image held in memory (the theme editor's not-yet-saved pick). */
    fun loadForDisplay(bytes: ByteArray, blur: Float): Bitmap? =
        loadForDisplay(blur) { opts -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) }

    private fun loadForDisplay(blur: Float, decode: (BitmapFactory.Options) -> Bitmap?): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        decode(bounds)
        if (bounds.outWidth <= 0) return null
        val longest = max(bounds.outWidth, bounds.outHeight)
        val b = blur.coerceIn(0f, 1f)
        // Unblurred: up to full size. Blurred: the stronger the blur, the smaller the working copy.
        val target = if (b <= 0.01f) MAX_SIDE else (640 - 400 * b).roundToInt()
        val decoded = decode(BitmapFactory.Options().apply {
            inSampleSize = sampleSize(longest, target)
        }) ?: return null
        val small = fit(decoded, target)
        if (small !== decoded) decoded.recycle()
        if (b <= 0.01f) return small
        val out = if (small.isMutable && small.config == Bitmap.Config.ARGB_8888) small
        else small.copy(Bitmap.Config.ARGB_8888, true).also { small.recycle() }
        boxBlur(out, radius = (1 + b * 5).roundToInt())
        out
    }.getOrNull()

    private fun sampleSize(longest: Int, target: Int): Int {
        var s = 1
        while (longest / (s * 2) >= target) s *= 2
        return s
    }

    private fun fit(src: Bitmap, maxSide: Int): Bitmap {
        val longest = max(src.width, src.height)
        if (longest <= maxSide) return src
        val f = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(src, (src.width * f).roundToInt().coerceAtLeast(1), (src.height * f).roundToInt().coerceAtLeast(1), true)
    }

    /** Three box-blur passes (≈ gaussian), horizontal then vertical, in place on a mutable bitmap. */
    private fun boxBlur(bmp: Bitmap, radius: Int) {
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)
        repeat(3) {
            pass(px, tmp, w, h, radius, horizontal = true)
            pass(tmp, px, w, h, radius, horizontal = false)
        }
        bmp.setPixels(px, 0, w, 0, 0, w, h)
    }

    private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val window = 2 * r + 1
        for (line in 0 until lines) {
            fun at(i: Int): Int {
                val c = i.coerceIn(0, len - 1)
                return if (horizontal) line * w + c else c * w + line
            }
            var rs = 0; var gs = 0; var bs = 0
            for (i in -r..r) {
                val p = src[at(i)]
                rs += (p shr 16) and 0xFF; gs += (p shr 8) and 0xFF; bs += p and 0xFF
            }
            for (i in 0 until len) {
                dst[at(i)] = (0xFF shl 24) or ((rs / window) shl 16) or ((gs / window) shl 8) or (bs / window)
                val out = src[at(i - r)]
                val inn = src[at(i + r + 1)]
                rs += ((inn shr 16) and 0xFF) - ((out shr 16) and 0xFF)
                gs += ((inn shr 8) and 0xFF) - ((out shr 8) and 0xFF)
                bs += (inn and 0xFF) - (out and 0xFF)
            }
        }
    }
}
