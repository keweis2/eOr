package com.gamelaunch.frontend.domain.usecase

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extracts the JPEG/PNG icon from a retail NSP's Control NCA entirely on-device.
 * Keys are read only while decoding and are never copied into eOr storage.
 */
@Singleton
class NspArtworkExtractor @Inject constructor(
    private val prodKeysLocator: ProdKeysLocator
) {
    fun extract(nsp: File): ByteArray? {
        val keyFiles = prodKeysLocator.findAll().toList()
        if (keyFiles.isEmpty()) return null
        return keyFiles.firstNotNullOfOrNull { keyFile -> runCatching {
            RandomAccessFile(nsp, "r").use { packageFile ->
                val keys = ProdKeys(keyFile)
                val entries = Pfs0(packageFile).entries
                val titleKey = entries.firstOrNull { it.name.endsWith(".tik", true) }
                    ?.let { keys.titleKey(packageFile.readAt(it.offset, minOf(it.size, TICKET_MIN_SIZE.toLong()).toInt())) }
                val ncas = entries.filter { it.name.endsWith(".nca", true) }
                val control = ncas.firstNotNullOfOrNull { entry ->
                    Nca(packageFile, entry.offset, entry.size, keys, titleKey)
                        .takeIf { it.contentType == CONTROL_CONTENT_TYPE }
                } ?: return@use null
                control.readIcon()
            }
        }.getOrNull() }
    }

    private inner class ProdKeys(file: File) {
        private val values = file.useLines { lines -> lines.mapNotNull { line ->
            val clean = line.substringBefore('#').trim()
            val equals = clean.indexOf('=')
            if (equals <= 0) null else clean.substring(0, equals).trim() to hex(clean.substring(equals + 1).trim())
        }.toMap() }

        val headerKey = values["header_key"] ?: error("header_key is missing")
        fun keyAreaKey(generation: Int, index: Int): ByteArray {
            val family = when (index) {
                0 -> "application"
                1 -> "ocean"
                2 -> "system"
                else -> error("unsupported key-area index $index")
            }
            values["key_area_key_${family}_%02x".format(generation)]?.let { return it }
            values["key_area_key_${family}_%02X".format(generation)]?.let { return it }

            val master = values["master_key_%02x".format(generation)]
                ?: values["master_key_%02X".format(generation)]
                ?: error("master_key for generation $generation is missing")
            val source = values["key_area_key_${family}_source"]
                ?: error("key_area_key_${family}_source is missing")
            val kekSource = values["aes_kek_generation_source"]
                ?: error("aes_kek_generation_source is missing")
            val keySource = values["aes_key_generation_source"]
                ?: error("aes_key_generation_source is missing")
            val kek = aesEcb(master, kekSource, Cipher.DECRYPT_MODE)
            val sourceKek = aesEcb(kek, source, Cipher.DECRYPT_MODE)
            return aesEcb(sourceKek, keySource, Cipher.DECRYPT_MODE)
        }

        fun titleKey(ticket: ByteArray): ByteArray? {
            if (ticket.size < TICKET_MIN_SIZE) return null
            val generation = ticket[TICKET_KEY_GENERATION_OFFSET].toInt() and 0xff
            val titleKek = values["titlekek_%02x".format(generation)]
                ?: values["titlekek_%02X".format(generation)]
                ?: return null
            return aesEcb(titleKek, ticket.copyOfRange(TICKET_TITLE_KEY_OFFSET, TICKET_TITLE_KEY_OFFSET + 16), Cipher.DECRYPT_MODE)
        }
    }

    private data class PfsEntry(val name: String, val offset: Long, val size: Long)

    private inner class Pfs0(private val file: RandomAccessFile) {
        val entries: List<PfsEntry>
        init {
            val header = file.readAt(0, 0x10)
            require(header.copyOfRange(0, 4).decodeToString() == "PFS0")
            val count = header.leInt(4)
            val stringSize = header.leInt(8)
            require(count in 1..10_000 && stringSize in 0..16_000_000)
            val table = file.readAt(0x10, count * 0x18 + stringSize)
            val strings = table.copyOfRange(count * 0x18, table.size)
            val dataStart = 0x10L + table.size
            entries = (0 until count).map { index ->
                val at = index * 0x18
                val offset = table.leLong(at)
                val size = table.leLong(at + 8)
                val nameAt = table.leInt(at + 16)
                PfsEntry(readCString(strings, nameAt), dataStart + offset, size)
            }
        }
    }

    private inner class Nca(
        private val file: RandomAccessFile,
        private val packageOffset: Long,
        private val packageSize: Long,
        keys: ProdKeys,
        titleKey: ByteArray?
    ) {
        private val header = xtsDecrypt(file.readAt(packageOffset, NCA_HEADER_SIZE), keys.headerKey)
        val contentType: Int = header[0x205].toInt() and 0xff
        private val keyArea: ByteArray

        init {
            require(header.copyOfRange(0x200, 0x204).decodeToString() == "NCA3")
            // NCA's crypto type is one greater than the master-key revision, except
            // that both crypto types 0 and 1 use master_key_00.
            val cryptoType = maxOf(
                header[0x206].toInt() and 0xff,
                header[0x220].toInt() and 0xff
            )
            val masterKeyRevision = if (cryptoType == 0) 0 else cryptoType - 1
            keyArea = ByteArray(0x40)
            if (header.copyOfRange(0x230, 0x240).any { it != 0.toByte() }) {
                titleKey?.copyInto(keyArea, 32)
            } else {
                val keyAreaKey = keys.keyAreaKey(masterKeyRevision, header[0x207].toInt() and 0xff)
                repeat(4) { index ->
                    val encrypted = header.copyOfRange(0x300 + index * 16, 0x310 + index * 16)
                    aesEcb(keyAreaKey, encrypted, Cipher.DECRYPT_MODE).copyInto(keyArea, index * 16)
                }
            }
        }

        fun readIcon(): ByteArray? {
            // Control NCAs use the first AES-CTR (RomFS) section. The CTR key is key-area slot 2.
            for (index in 0 until 4) {
                val start = header.leInt(0x240 + index * 0x10).toLong() * MEDIA_UNIT
                val end = header.leInt(0x244 + index * 0x10).toLong() * MEDIA_UNIT
                if (start <= 0 || end <= start || end > packageSize) continue
                val fsHeader = header.copyOfRange(0x400 + index * 0x200, 0x600 + index * 0x200)
                if ((fsHeader[2].toInt() and 0xff) != PARTITION_ROMFS ||
                    (fsHeader[3].toInt() and 0xff) != FS_TYPE_ROMFS ||
                    (fsHeader[4].toInt() and 0xff) != AES_CTR
                ) continue

                // IVFC describes the integrity layers in the NCA filesystem header. It is not
                // stored at the beginning of the encrypted section: that area contains the first
                // hash layer. The final IVFC level points at the actual RomFS.
                require(fsHeader.copyOfRange(0x08, 0x0c).decodeToString() == "IVFC")
                // MaxLayers includes the master-hash layer, which has no level record. Retail
                // Control NCAs normally report 7 layers followed by 6 level records.
                val maxLayers = fsHeader.leInt(0x14)
                require(maxLayers in 2..IVFC_MAX_LAYERS)
                val dataLevel = 0x18 + (maxLayers - 2) * 0x18
                val romFsOffset = fsHeader.leLong(dataLevel)
                val romFsSize = fsHeader.leLong(dataLevel + 8)
                require(romFsOffset >= 0 && romFsSize > 0 && romFsOffset + romFsSize <= end - start)
                val section = EncryptedSection(
                    file = file,
                    start = packageOffset + start,
                    ncaOffset = start,
                    size = end - start,
                    key = keyArea.copyOfRange(32, 48),
                    upperCounter = fsHeader.copyOfRange(0x140, 0x148)
                )
                return section.readRomFsIcon(romFsOffset, romFsSize)
            }
            return null
        }
    }

    private inner class EncryptedSection(
        private val file: RandomAccessFile,
        private val start: Long,
        private val ncaOffset: Long,
        private val size: Long,
        private val key: ByteArray,
        private val upperCounter: ByteArray
    ) {
        private fun read(offset: Long, length: Int): ByteArray {
            require(offset >= 0 && length >= 0 && offset + length <= size)
            val encrypted = file.readAt(start + offset, length)
            val blockOffset = offset and 15.inv().toLong()
            val prefix = (offset - blockOffset).toInt()
            val decrypted = aesCtr(key, counter(blockOffset), if (prefix == 0) encrypted else file.readAt(start + blockOffset, length + prefix))
            return if (prefix == 0) decrypted else decrypted.copyOfRange(prefix, prefix + length)
        }

        fun readRomFsIcon(romFsOffset: Long, romFsSize: Long): ByteArray? {
            val header = read(romFsOffset, 0x50)
            require(header.leLong(0) == ROMFS_HEADER_SIZE.toLong())
            val fileMetaOffset = header.leLong(0x38)
            val fileMetaSize = header.leLong(0x40)
            val fileDataOffset = header.leLong(0x48)
            require(fileMetaOffset >= ROMFS_HEADER_SIZE && fileMetaSize in 1..16_000_000)
            require(fileMetaOffset + fileMetaSize <= romFsSize)
            require(fileDataOffset >= ROMFS_HEADER_SIZE && fileDataOffset <= romFsSize)
            val metadata = read(romFsOffset + fileMetaOffset, fileMetaSize.toInt())
            var at = 0
            while (at + 0x20 <= metadata.size) {
                val dataOffset = metadata.leLong(at + 8)
                val dataSize = metadata.leLong(at + 16)
                val nameSize = metadata.leInt(at + 28)
                val next = at + 0x20 + nameSize
                if (nameSize < 0 || next > metadata.size) break
                val name = metadata.copyOfRange(at + 0x20, next).decodeToString()
                if (name.startsWith("icon_", true) && name.endsWith(".dat", true) && dataSize in 1..16_000_000) {
                    return read(romFsOffset + fileDataOffset + dataOffset, dataSize.toInt())
                }
                at = (next + 3) and 3.inv()
            }
            return null
        }

        private fun counter(offset: Long): ByteArray = ByteArray(16).also { counter ->
            // The counter prefix is stored little-endian in the NCA FS header,
            // while AES-CTR consumes the full counter as big-endian bytes.
            upperCounter.reversedArray().copyInto(counter)
            ByteBuffer.wrap(counter, 8, 8).order(ByteOrder.BIG_ENDIAN).putLong((ncaOffset + offset) / 16)
        }
    }

    private fun RandomAccessFile.readAt(offset: Long, size: Int): ByteArray = ByteArray(size).also {
        seek(offset)
        readFully(it)
    }

    private fun aesCtr(key: ByteArray, counter: ByteArray, input: ByteArray): ByteArray = Cipher.getInstance("AES/CTR/NoPadding")
        .run { init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), javax.crypto.spec.IvParameterSpec(counter)); doFinal(input) }

    private fun aesEcb(key: ByteArray, input: ByteArray, mode: Int): ByteArray = Cipher.getInstance("AES/ECB/NoPadding")
        .run { init(mode, SecretKeySpec(key, "AES")); doFinal(input) }

    /** NCA headers are AES-XTS, split into 0x200-byte sectors. */
    private fun xtsDecrypt(input: ByteArray, headerKey: ByteArray): ByteArray {
        require(headerKey.size == 32)
        val dataKey = headerKey.copyOfRange(0, 16)
        val tweakKey = headerKey.copyOfRange(16, 32)
        return input.copyOf().also { output ->
            for (sector in output.indices step 0x200) {
                val tweakInput = ByteArray(16)
                // NCA uses Nintendo's non-standard, big-endian sector tweak.
                // The sector number occupies the low (last) 64 bits of the tweak.
                ByteBuffer.wrap(tweakInput, 8, 8)
                    .order(ByteOrder.BIG_ENDIAN)
                    .putLong((sector / 0x200).toLong())
                var tweak = aesEcb(tweakKey, tweakInput, Cipher.ENCRYPT_MODE)
                for (block in sector until minOf(sector + 0x200, output.size) step 16) {
                    val mixed = ByteArray(16) { i -> (output[block + i].toInt() xor tweak[i].toInt()).toByte() }
                    val plain = aesEcb(dataKey, mixed, Cipher.DECRYPT_MODE)
                    for (i in 0 until 16) output[block + i] = (plain[i].toInt() xor tweak[i].toInt()).toByte()
                    tweak = multiplyByX(tweak)
                }
            }
        }
    }

    private fun multiplyByX(value: ByteArray): ByteArray {
        var carry = 0
        for (i in value.indices) {
            val nextCarry = (value[i].toInt() ushr 7) and 1
            value[i] = ((value[i].toInt() shl 1) or carry).toByte()
            carry = nextCarry
        }
        if (carry != 0) value[0] = (value[0].toInt() xor 0x87).toByte()
        return value
    }

    private fun ByteArray.leInt(offset: Int): Int = ByteBuffer.wrap(this, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int
    private fun ByteArray.leLong(offset: Int): Long = ByteBuffer.wrap(this, offset, 8).order(ByteOrder.LITTLE_ENDIAN).long
    private fun readCString(bytes: ByteArray, offset: Int): String {
        require(offset in bytes.indices)
        val end = (offset until bytes.size).firstOrNull { bytes[it] == 0.toByte() } ?: bytes.size
        return bytes.copyOfRange(offset, end).decodeToString()
    }
    private fun hex(value: String): ByteArray {
        val cleaned = value.filter(Char::isLetterOrDigit)
        require(cleaned.length % 2 == 0)
        return ByteArray(cleaned.length / 2) { index -> cleaned.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
    }

    private companion object {
        const val CONTROL_CONTENT_TYPE = 2
        const val PARTITION_ROMFS = 0
        const val FS_TYPE_ROMFS = 3
        const val AES_CTR = 3
        const val IVFC_MAX_LAYERS = 7
        const val ROMFS_HEADER_SIZE = 0x50
        const val MEDIA_UNIT = 0x200L
        const val NCA_HEADER_SIZE = 0xC00
        const val TICKET_TITLE_KEY_OFFSET = 0x180
        const val TICKET_KEY_GENERATION_OFFSET = 0x285
        const val TICKET_MIN_SIZE = TICKET_KEY_GENERATION_OFFSET + 1
    }
}
