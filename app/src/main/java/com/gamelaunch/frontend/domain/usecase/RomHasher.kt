package com.gamelaunch.frontend.domain.usecase

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * The ROM fingerprint sent to ScreenScraper: an MD5 of the first [PARTIAL_HASH_BYTES] of the ROM
 * (of the first entry for a .zip). Computed lazily — only for games actually being scraped — because
 * hashing every file during the library scan meant reading half a megabyte per ROM from the SD card,
 * which was ~95% of a first scan's time on a large library.
 */
object RomHasher {
    private const val PARTIAL_HASH_BYTES = 512 * 1024 // 512 KB — first-window hash, keeps memory bounded

    fun partialMd5(file: File): String? = runCatching {
        if (!file.isFile) return@runCatching null
        val md = MessageDigest.getInstance("MD5")
        if (file.extension.equals("zip", ignoreCase = true)) {
            ZipInputStream(file.inputStream().buffered()).use { zip ->
                var entry = zip.nextEntry
                var foundFile = false
                while (entry != null) {
                    if (!entry.isDirectory) {
                        foundFile = true
                        updateWithPartial(md, zip)
                        break
                    }
                    entry = zip.nextEntry
                }
                if (!foundFile) {
                    file.inputStream().use { stream -> updateWithPartial(md, stream) }
                }
            }
        } else {
            file.inputStream().use { stream -> updateWithPartial(md, stream) }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    /**
     * Feeds up to [PARTIAL_HASH_BYTES] of [stream] into [md]. A single [InputStream.read] is not
     * guaranteed to fill the buffer — this is especially true for decompressing streams like
     * [ZipInputStream], where one read typically returns only a small inflate chunk. Looping until the
     * window is filled (or EOF) makes the hashed byte count deterministic and consistent between raw
     * and zipped ROMs.
     */
    private fun updateWithPartial(md: MessageDigest, stream: InputStream) {
        val buffer = ByteArray(PARTIAL_HASH_BYTES)
        var off = 0
        while (off < buffer.size) {
            val n = stream.read(buffer, off, buffer.size - off)
            if (n < 0) break
            off += n
        }
        if (off > 0) md.update(buffer, 0, off)
    }
}
