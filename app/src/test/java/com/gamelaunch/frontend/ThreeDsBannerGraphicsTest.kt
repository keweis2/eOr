package com.gamelaunch.frontend

import com.gamelaunch.frontend.domain.usecase.ThreeDsBannerGraphics
import com.gamelaunch.frontend.domain.usecase.ThreeDsArtworkExtractor
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ThreeDsBannerGraphicsTest {
    private fun cbmd(stream: ByteArray) = ByteArray(0x88).apply {
        "CBMD".encodeToByteArray().copyInto(this)
        ByteBuffer.wrap(this, 8, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(0x88)
    } + stream

    @Test fun `decodes literal CGFX bytes`() {
        assertArrayEquals("CGFX".encodeToByteArray(), ThreeDsBannerGraphics.decode(
            cbmd(byteArrayOf(0x11, 4, 0, 0, 0) + "CGFX".encodeToByteArray())))
    }

    @Test fun `decodes short medium and long overlapping back references`() {
        for ((length, encoded) in listOf(3 to byteArrayOf(0x20, 0),
            17 to byteArrayOf(0, 0, 0), 273 to byteArrayOf(0x10, 0, 0, 0))) {
            val size = length + 5
            val header = byteArrayOf(0x11, size.toByte(), (size ushr 8).toByte(), 0)
            val decoded = ThreeDsBannerGraphics.decode(cbmd(header + byteArrayOf(4) + "CGFXA".encodeToByteArray() + encoded))
            assertArrayEquals("CGFX".encodeToByteArray() + ByteArray(length + 1) { 'A'.code.toByte() }, decoded)
        }
    }

    @Test fun `rejects malformed truncated and excessive streams`() {
        for (stream in listOf(byteArrayOf(), byteArrayOf(0x11, 4, 0, 0),
            byteArrayOf(0x11, 4, 0, 0, 0x80.toByte(), 0x20, 0),
            byteArrayOf(0x11, 0, 0, 9))) {
            assertNull(ThreeDsBannerGraphics.decode(cbmd(stream)))
        }
        assertNull(ThreeDsBannerGraphics.decode(ByteArray(0x88)))
    }

    @Test fun `real ROM banner decodes exactly like independent desktop extraction when supplied`() {
        val directory = System.getenv("EOR_TEST_3DS_DIR")?.let(::File)
        val expected = System.getenv("EOR_TEST_3DS_CGFX")?.let(::File)
        assumeTrue(directory?.isDirectory == true && expected?.isFile == true)
        val rom = directory!!.listFiles()!!.first { it.name.startsWith("Super Mario 3D Land") }
        val banner = ThreeDsArtworkExtractor().extractBanner(rom)
        assertNotNull(banner)
        assertArrayEquals(expected!!.readBytes(), ThreeDsBannerGraphics.decode(banner!!))
    }
}
