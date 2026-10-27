package com.gamelaunch.frontend

import com.gamelaunch.frontend.domain.usecase.ThreeDsArtworkExtractor
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.io.InputStream

class ThreeDsArtworkExtractorTest {
    @get:Rule val tmp = TemporaryFolder()
    private val extractor = ThreeDsArtworkExtractor()

    @Test fun `decodes NCSD cartridge icon with correct colors and tile orientation`() {
        val ncch = ncch()
        val cartridge = ByteArray(0x4000) + ncch
        cartridge.magic(0x100, "NCSD")
        cartridge.le32(0x120, 0x4000 / 512)
        cartridge.le32(0x124, ncch.size / 512)
        assertIcon(extractor.extract(fixture("game.cci", cartridge)))
        assertIcon(extractor.extract(fixture("game.3ds", cartridge)))
    }

    @Test fun `reads standalone NCCH and case insensitive extension`() {
        assertIcon(extractor.extract(fixture("game.3DS", ncch())))
    }

    @Test fun `reads CIA metadata even when content is encrypted`() {
        val cia = cia(metadata = smdh(), encrypted = true)
        assertIcon(extractor.extract(fixture("game.CIA", cia)))
    }

    @Test fun `reads CIA main content when metadata is absent or invalid`() {
        assertIcon(extractor.extract(fixture("no-meta.cia", cia())))
        assertIcon(extractor.extract(fixture("bad-meta.cia", cia(metadata = ByteArray(0x36c0)))))
    }

    @Test fun `skips absent and non-main CIA content using TMD sizes`() {
        assertIcon(extractor.extract(fixture("multi.cia", cia(extraContent = true))))
    }

    @Test fun `encrypted content without metadata is a miss`() {
        assertNull(extractor.extract(fixture("encrypted.cia", cia(encrypted = true))))
        val encrypted = ncch().apply { this[0x18f] = 0 }
        assertNull(extractor.extract(fixture("encrypted.3ds", encrypted)))
    }

    @Test fun `missing icon and invalid SMDH are misses`() {
        assertNull(extractor.extract(fixture("no-icon.cci", ncch().apply { magic(0x400, "other") })))
        assertNull(extractor.extract(fixture("bad-icon.cci", ncch().apply { magic(0x600, "NOPE") })))
    }

    @Test fun `truncated files and out of bounds sections are misses`() {
        val valid = ncch()
        for (size in listOf(0, 0x100, 0x200, 0x600, valid.size - 1)) {
            assertNull(extractor.extract(fixture("truncated-$size.3ds", valid.copyOf(size))))
        }
        assertNull(extractor.extract(fixture("bad-offset.3ds", ncch().apply { le32(0x1a0, -1) })))
        assertNull(extractor.extract(fixture("bad-size.3ds", ncch().apply { le32(0x40c, -1) })))
        assertNull(extractor.extract(fixture("bad-unit.3ds", ncch().apply { this[0x18e] = 127 })))
        assertNull(extractor.extract(fixture("bad-content.cia", cia().apply { le64(24, Long.MAX_VALUE) })))
        assertNull(extractor.extract(fixture("bad-tmd.cia", cia().apply { le32(16, -1) })))
        assertNull(extractor.extract(File(tmp.root, "missing.3ds")))
    }

    /** Optional local fixtures: only sparse headers/icons need be copied from a user's device. */
    @Test fun `real local ROM fixtures decode when supplied`() {
        val root = System.getenv("EOR_TEST_3DS_DIR")?.let(::File)
        assumeTrue(root?.isDirectory == true)
        val files = root!!.listFiles()!!.filter { it.extension.lowercase() in setOf("cci", "3ds", "cia") }
        assertTrue(files.isNotEmpty())
        files.forEach { rom ->
            val png = extractor.extract(rom)
            assertNotNull("No icon from ${rom.name}", png)
            val image = decodePng(png!!)
            assertEquals(48, image.width)
            assertEquals(48, image.height)

        }
    }

    private data class DecodedImage(val width: Int, val height: Int, val pixels: IntArray)

    private fun decodePng(bytes: ByteArray): DecodedImage {
        // Android's compile bootclasspath excludes java.desktop. The unit-test JVM has ImageIO,
        // so use it reflectively to validate PNGs independently without a production dependency.
        val image = Class.forName("javax.imageio.ImageIO").getMethod("read", InputStream::class.java)
            .invoke(null, ByteArrayInputStream(bytes))!!
        val width = image.javaClass.getMethod("getWidth").invoke(image) as Int
        val height = image.javaClass.getMethod("getHeight").invoke(image) as Int
        val getRgb = image.javaClass.getMethod("getRGB", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        return DecodedImage(width, height, IntArray(width * height) { i ->
            getRgb.invoke(image, i % width, i / width) as Int
        })
    }

    private fun assertIcon(bytes: ByteArray?) {
        assertNotNull(bytes)
        // ImageIO is an independent PNG decoder: also verifies the complete emitted image format.
        val image = decodePng(bytes!!)
        assertNotNull(image)
        assertEquals(48, image.width)
        assertEquals(48, image.height)
        for (y in 0 until 48) for (x in 0 until 48) {
            val r = x and 31
            val g = y
            val b = (x + y) and 31
            val expected = (0xff shl 24) or (((r shl 3) or (r ushr 2)) shl 16) or
                (((g shl 2) or (g ushr 4)) shl 8) or ((b shl 3) or (b ushr 2))
            assertEquals("Pixel $x,$y", expected, image.pixels[y * 48 + x])
        }
    }

    private fun smdh(): ByteArray = ByteArray(0x36c0).apply {
        magic(0, "SMDH")
        // Invert Morton order with a fixed coordinate lookup, independently of the decoder.
        val coord = intArrayOf(0, 1, 8, 9, 2, 3, 10, 11)
        for (tileY in 0 until 6) for (tileX in 0 until 6) for (pixel in 0 until 64) {
            val x = tileX * 8 + coord[pixel and 7] % 8 + (if (pixel and 16 != 0) 4 else 0)
            val y = tileY * 8 + coord[pixel and 7] / 8 + (if (pixel and 8 != 0) 2 else 0) +
                (if (pixel and 32 != 0) 4 else 0)
            val color = ((x and 31) shl 11) or (y shl 5) or ((x + y) and 31)
            val offset = 0x24c0 + ((tileY * 6 + tileX) * 64 + pixel) * 2
            ByteBuffer.wrap(this, offset, 2).order(ByteOrder.LITTLE_ENDIAN).putShort(color.toShort())
        }
    }

    private fun ncch(): ByteArray = ByteArray(0x3e00).apply {
        magic(0x100, "NCCH")
        le32(0x104, size / 512)
        this[0x18f] = 4 // NoCrypto
        le32(0x1a0, 2) // ExeFS at 0x400
        le32(0x1a4, (size - 0x400) / 512)
        magic(0x400, "icon")
        le32(0x408, 0)
        le32(0x40c, 0x36c0)
        smdh().copyInto(this, 0x600)
    }

    private fun cia(metadata: ByteArray? = null, encrypted: Boolean = false, extraContent: Boolean = false): ByteArray {
        val main = ncch()
        val count = if (extraContent) 3 else 1
        val tmd = 0x140 + 0x9c4 + count * 0x30
        val tmdOffset = 0x20c0 // deliberately unaligned cert/ticket sizes
        val contentOffset = (tmdOffset + tmd + 63) and -64
        val contentSize = main.size + if (extraContent) 0x200 else 0
        val metaOffset = (contentOffset + contentSize + 63) and -64
        val metaSize = if (metadata != null) 0x400 + metadata.size else 0
        return ByteArray(metaOffset + metaSize).apply {
            le32(0, 0x2020)
            le32(8, 7) // cert
            le32(12, 13) // ticket
            le32(16, tmd)
            le32(20, metaSize)
            le64(24, contentSize.toLong())
            this[0x20] = if (extraContent) 0xa0.toByte() else 0x80.toByte() // indexes 0 and 2
            ByteBuffer.wrap(this, tmdOffset, 4).putInt(0x10004)
            ByteBuffer.wrap(this, tmdOffset + 0x140 + 0x9e, 2).putShort(count.toShort())
            fun chunk(entry: Int, index: Int, size: Int, type: Int = 0) {
                ByteBuffer.wrap(this, tmdOffset + 0x140 + 0x9c4 + entry * 0x30, 16)
                    .putInt(index).putShort(index.toShort()).putShort(type.toShort()).putLong(size.toLong())
            }
            if (extraContent) {
                chunk(0, 1, 0x10000) // omitted: must consume no bytes
                chunk(1, 2, 0x200) // present, non-main
                chunk(2, 0, main.size)
                main.copyInto(this, contentOffset + 0x200)
            } else {
                chunk(0, 0, main.size, if (encrypted) 1 else 0)
                main.copyInto(this, contentOffset)
            }
            metadata?.copyInto(this, metaOffset + 0x400)
        }
    }

    private fun fixture(name: String, bytes: ByteArray) = tmp.newFile(name).apply { writeBytes(bytes) }
    private fun ByteArray.magic(at: Int, value: String) = value.encodeToByteArray().copyInto(this, at)
    private fun ByteArray.le32(at: Int, value: Int) {
        ByteBuffer.wrap(this, at, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(value)
    }
    private fun ByteArray.le64(at: Int, value: Long) {
        ByteBuffer.wrap(this, at, 8).order(ByteOrder.LITTLE_ENDIAN).putLong(value)
    }
}
