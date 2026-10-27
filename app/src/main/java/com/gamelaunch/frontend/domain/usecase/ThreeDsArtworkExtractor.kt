package com.gamelaunch.frontend.domain.usecase

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the 48x48 SMDH icon from decrypted NCSD/NCCH images or CIA metadata/content.
 * Only headers and the icon are read, never the full ROM. Encrypted content needs no special
 * handling: CIA metadata can still supply an icon; otherwise extraction is a best-effort miss.
 *
 * Format references: https://www.3dbrew.org/wiki/{NCSD,NCCH,ExeFS,CIA,SMDH}
 */
@Singleton
class ThreeDsArtworkExtractor @Inject constructor() {
    fun extract(rom: File): ByteArray? = readSection(rom, "icon")?.let(::iconPng)

    private fun readSection(rom: File, section: String): ByteArray? = runCatching {
        RandomAccessFile(rom, "r").use { file ->
            val header = file.readAt(0, HEADER_SIZE)
            val smdh = when (header.magic(0x100)) {
                "NCSD" -> {
                    // Partition 0 is the game; manuals/download-play partitions have other icons.
                    val offset = header.u32(0x120) * MEDIA_UNIT
                    val size = header.u32(0x124) * MEDIA_UNIT
                    require(offset >= HEADER_SIZE && size >= HEADER_SIZE)
                    file.checkRange(offset, size)
                    file.ncchIcon(offset, size, section)
                }
                "NCCH" -> file.ncchIcon(0, file.length(), section)
                else -> if (rom.extension.equals("cia", true)) file.ciaIcon(section) else null
            }
            smdh
        }
    }.getOrNull()

    private fun RandomAccessFile.ncchIcon(offset: Long, size: Long, section: String): ByteArray? {
        if (size < HEADER_SIZE) return null
        checkRange(offset, size)
        val header = readAt(offset, HEADER_SIZE)
        if (header.magic(0x100) != "NCCH") return null
        // No keys are requested or retained by this extractor.
        if (header[0x18f].toInt() and 4 == 0) return null
        val unitShift = header[0x18e].toInt() and 0xff
        require(unitShift <= 22)
        val unit = MEDIA_UNIT shl unitShift
        val contentSize = header.u32(0x104) * unit
        require(contentSize in HEADER_SIZE.toLong()..size)
        val exefsOffset = header.u32(0x1a0) * unit
        val exefsSize = header.u32(0x1a4) * unit
        if (exefsOffset == 0L || exefsSize < HEADER_SIZE) return null
        require(exefsOffset >= HEADER_SIZE && exefsOffset <= contentSize - exefsSize)
        val exefs = readAt(offset + exefsOffset, HEADER_SIZE)
        for (entry in 0 until 10) {
            val at = entry * 16
            val name = exefs.copyOfRange(at, at + 8).decodeToString().trimEnd('\u0000')
            if (name != section) continue
            val iconOffset = HEADER_SIZE + exefs.u32(at + 8)
            val iconSize = exefs.u32(at + 12)
            require(iconSize in 1..16_777_216 && iconOffset <= exefsSize - iconSize)
            if (section == "icon") {
                require(iconSize >= SMDH_SIZE)
                return readAt(offset + exefsOffset + iconOffset, SMDH_SIZE).validSmdh()
            }
            return readAt(offset + exefsOffset + iconOffset, iconSize.toInt())
        }
        return null
    }

    private fun RandomAccessFile.ciaIcon(section: String): ByteArray? {
        val header = readAt(0, CIA_HEADER_SIZE)
        require(header.u32(0) == CIA_HEADER_SIZE.toLong())
        val certOffset = align64(header.u32(0))
        val ticketOffset = align64(certOffset + header.u32(8))
        val tmdOffset = align64(ticketOffset + header.u32(12))
        val tmdSize = header.u32(16)
        val contentOffset = align64(tmdOffset + tmdSize)
        val contentSize = ByteBuffer.wrap(header, 24, 8).order(ByteOrder.LITTLE_ENDIAN).long
        checkRange(contentOffset, contentSize)
        val metaOffset = align64(contentOffset + contentSize)
        val metaSize = header.u32(20)
        if (section == "icon" && metaSize >= 0x400 + SMDH_SIZE) {
            checkRange(metaOffset, metaSize)
            readAt(metaOffset + 0x400, SMDH_SIZE).validSmdh()?.let { return it }
        }

        // CIAs without metadata can still carry an unencrypted main NCCH. Use the TMD's
        // sizes and inclusion bits so omitted content and non-main entries aren't mistaken for it.
        if (tmdSize < 4) return null
        val signature = ByteBuffer.wrap(readAt(tmdOffset, 4)).int
        val bodyOffset = when (signature) {
            0x10000, 0x10003 -> 0x240L // RSA-4096
            0x10001, 0x10004 -> 0x140L // RSA-2048
            0x10002, 0x10005 -> 0x80L // ECDSA
            else -> return null
        }
        require(tmdSize >= bodyOffset + 0x9c4)
        val count = ByteBuffer.wrap(readAt(tmdOffset + bodyOffset + 0x9e, 2)).short.toInt() and 0xffff
        require(bodyOffset + 0x9c4 + count * 0x30L <= tmdSize)
        var position = contentOffset
        repeat(count) { entry ->
            val chunk = ByteBuffer.wrap(readAt(tmdOffset + bodyOffset + 0x9c4 + entry * 0x30L, 16))
            val index = chunk.getShort(4).toInt() and 0xffff
            val type = chunk.getShort(6).toInt() and 0xffff
            val size = chunk.getLong(8)
            if (header[0x20 + index / 8].toInt() and (0x80 ushr (index % 8)) != 0) {
                require(size >= 0 && size <= contentOffset + contentSize - position)
                if (index == 0) return if (type and 1 == 0) ncchIcon(position, size, section) else null
                position += size
            }
        }
        return null
    }

    private fun ByteArray.validSmdh(): ByteArray? = takeIf { magic(0) == "SMDH" }

    /** Untile RGB565 (8x8 Morton order), then encode a small standard RGB PNG off the UI thread. */
    private fun iconPng(smdh: ByteArray): ByteArray {
        val rgb = ByteArray(ICON_SIZE * (1 + ICON_SIZE * 3))
        for (y in 0 until ICON_SIZE) for (x in 0 until ICON_SIZE) {
            var morton = 0
            repeat(3) { bit ->
                morton = morton or (((x ushr bit) and 1) shl (bit * 2))
                morton = morton or (((y ushr bit) and 1) shl (bit * 2 + 1))
            }
            val tiledPixel = ((y / 8) * (ICON_SIZE / 8) + x / 8) * 64 + morton
            val at = 0x24c0 + tiledPixel * 2
            val pixel = (smdh[at].toInt() and 0xff) or ((smdh[at + 1].toInt() and 0xff) shl 8)
            val out = y * (1 + ICON_SIZE * 3) + 1 + x * 3 // PNG filter byte remains 0.
            val r = pixel ushr 11
            val g = (pixel ushr 5) and 63
            val b = pixel and 31
            rgb[out] = ((r shl 3) or (r ushr 2)).toByte()
            rgb[out + 1] = ((g shl 2) or (g ushr 4)).toByte()
            rgb[out + 2] = ((b shl 3) or (b ushr 2)).toByte()
        }
        val compressed = ByteArrayOutputStream().also { buffer ->
            DeflaterOutputStream(buffer).use { it.write(rgb) }
        }.toByteArray()
        return ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { png ->
                png.write(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 13, 10, 26, 10))
                val ihdr = ByteBuffer.allocate(13).putInt(ICON_SIZE).putInt(ICON_SIZE)
                    .put(8.toByte()).put(2.toByte()).put(0.toByte()).put(0.toByte()).put(0.toByte()).array()
                png.chunk("IHDR", ihdr)
                png.chunk("IDAT", compressed)
                png.chunk("IEND", byteArrayOf())
            }
        }.toByteArray()
    }

    private fun DataOutputStream.chunk(type: String, bytes: ByteArray) {
        val name = type.toByteArray(Charsets.US_ASCII)
        writeInt(bytes.size)
        write(name)
        write(bytes)
        val crc = CRC32().apply { update(name); update(bytes) }
        writeInt(crc.value.toInt())
    }

    private fun RandomAccessFile.checkRange(offset: Long, size: Long) {
        require(offset >= 0 && size >= 0 && offset <= length() && size <= length() - offset)
    }

    private fun RandomAccessFile.readAt(offset: Long, size: Int): ByteArray {
        checkRange(offset, size.toLong())
        return ByteArray(size).also { seek(offset); readFully(it) }
    }

    private fun ByteArray.magic(offset: Int) = copyOfRange(offset, offset + 4).decodeToString()
    private fun ByteArray.u32(offset: Int) =
        ByteBuffer.wrap(this, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffffffffL
    private fun align64(value: Long): Long {
        require(value in 0..Long.MAX_VALUE - 63)
        return (value + 63) and -64L
    }

    private companion object {
        const val MEDIA_UNIT = 0x200L
        const val HEADER_SIZE = 0x200
        const val CIA_HEADER_SIZE = 0x2020
        const val SMDH_SIZE = 0x36c0
        const val ICON_SIZE = 48
    }
}
