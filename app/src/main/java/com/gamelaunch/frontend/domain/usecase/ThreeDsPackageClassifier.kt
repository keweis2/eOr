package com.gamelaunch.frontend.domain.usecase

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reads only public headers; encrypted CIA content needs no keys for classification.
 * Layouts: https://www.3dbrew.org/wiki/Title_metadata and https://www.3dbrew.org/wiki/NCCH
 * Unknown categories are retained; only update (0004000E) and DLC (0004008C) are excluded.
 */
internal object ThreeDsPackageClassifier {
    fun isSupplemental(file: File): Boolean {
        if (file.extension.lowercase() !in setOf("cia", "3ds", "cci", "cxi")) return false
        val titleId = runCatching {
            RandomAccessFile(file, "r").use { input ->
                fun read(offset: Long, size: Int): ByteBuffer {
                    require(offset >= 0 && offset <= input.length() - size)
                    return ByteBuffer.wrap(ByteArray(size).also {
                        input.seek(offset)
                        input.readFully(it)
                    })
                }
                fun u32(offset: Long) = read(offset, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffffffffL
                fun align(value: Long) = (value + 63) and -64L
                fun ncch(offset: Long): Long? {
                    val header = read(offset, 0x200)
                    if (header.getInt(0x100) != 0x4e434348) return null
                    return header.order(ByteOrder.LITTLE_ENDIAN).getLong(0x118)
                }
                if (file.extension.equals("cia", true)) {
                    if (u32(0) != 0x2020L) return@use null
                    val ticket = align(align(u32(0)) + u32(8))
                    val tmd = align(ticket + u32(12))
                    val body = when (read(tmd, 4).int) {
                        0x10000, 0x10003 -> 0x240L
                        0x10001, 0x10004 -> 0x140L
                        0x10002, 0x10005 -> 0x80L
                        else -> return@use null
                    }
                    require(u32(16) >= body + 0xa4)
                    read(tmd + body + 0x4c, 8).long
                } else {
                    when (read(0x100, 4).int) {
                        0x4e434348 -> ncch(0)
                        0x4e435344 -> {
                            val offset = u32(0x120) * 0x200
                            val size = u32(0x124) * 0x200
                            require(offset >= 0x200 && size >= 0x200 && offset <= input.length() - size)
                            ncch(offset)
                        }
                        else -> null
                    }
                }
            }
        }.getOrNull() ?: return false // Unknown or incomplete files stay visible.
        return (titleId ushr 32) in setOf(0x0004000eL, 0x0004008cL)
    }
}
