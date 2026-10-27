package com.gamelaunch.frontend.domain.usecase

import com.gamelaunch.frontend.image.ThreeDsBannerRenderer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject

/** ROM -> ExeFS banner -> bounded LZ11 decode -> offscreen device GPU -> PNG. No network involved. */
class ThreeDsBannerExtractor @Inject constructor(
    private val artworkExtractor: ThreeDsArtworkExtractor,
    private val renderer: ThreeDsBannerRenderer
) {
    fun extract(rom: File): ByteArray? = runCatching {
        artworkExtractor.extractBanner(rom)?.let(ThreeDsBannerGraphics::decode)?.let(renderer::render)
    }.getOrNull()
}

/** Pure, bounded CBMD/LZ11 decoder; also used by the device-free regression tests. */
object ThreeDsBannerGraphics {
    fun decode(banner: ByteArray): ByteArray? = runCatching {
        require(banner.size >= 0x88 && banner.copyOfRange(0, 4).decodeToString() == "CBMD")
        val start = ByteBuffer.wrap(banner, 8, 4).order(ByteOrder.LITTLE_ENDIAN).int
        require(start >= 0x88 && start <= banner.size - 4)
        var position = start
        fun byte(): Int {
            require(position in banner.indices)
            return banner[position++].toInt() and 255
        }
        fun le32(): Long {
            var value = 0L
            repeat(4) { value = value or (byte().toLong() shl (it * 8)) }
            return value
        }
        val header = le32()
        require(header and 255 == 0x11L)
        val size = (header ushr 8).takeUnless { it == 0L } ?: le32()
        require(size in 1..0x80000)
        val output = ByteArray(size.toInt())
        var written = 0
        while (written < output.size) {
            val flags = byte()
            for (bit in 7 downTo 0) {
                if (written == output.size) break
                if (flags and (1 shl bit) == 0) {
                    output[written++] = byte().toByte()
                    continue
                }
                val first = byte()
                val kind = first ushr 4
                val length: Int
                val distance: Int
                when (kind) {
                    0 -> {
                        val second = byte()
                        length = ((first and 15) shl 4 or (second ushr 4)) + 17
                        distance = ((second and 15) shl 8 or byte()) + 1
                    }
                    1 -> {
                        val second = byte()
                        val third = byte()
                        length = ((first and 15) shl 12 or (second shl 4) or (third ushr 4)) + 273
                        distance = ((third and 15) shl 8 or byte()) + 1
                    }
                    else -> {
                        length = kind + 1
                        distance = ((first and 15) shl 8 or byte()) + 1
                    }
                }
                require(distance <= written && length <= output.size - written)
                repeat(length) { output[written] = output[written - distance]; written++ }
            }
        }
        require(output.size >= 4 && output.copyOfRange(0, 4).decodeToString() == "CGFX")
        output
    }.getOrNull()
}
