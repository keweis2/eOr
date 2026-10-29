package com.gamelaunch.frontend

import com.gamelaunch.frontend.domain.usecase.ThreeDsPackageClassifier
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ThreeDsPackageClassifierTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun `all CIA signature layouts classify public title ID without content`() {
        for ((signature, body) in listOf(0x10000 to 0x240, 0x10001 to 0x140,
            0x10002 to 0x80, 0x10003 to 0x240, 0x10004 to 0x140, 0x10005 to 0x80)) {
            for (category in listOf(0x00040000L, 0x0004000eL, 0x0004008cL)) {
                val data = ByteBuffer.allocate(0x2500).order(ByteOrder.LITTLE_ENDIAN)
                data.putInt(0, 0x2020).putInt(8, 17).putInt(12, 65).putInt(16, body + 0xa4)
                // Certificates and ticket each use 64-byte alignment.
                data.order(ByteOrder.BIG_ENDIAN).putInt(0x2100, signature)
                    .putLong(0x2100 + body + 0x4c, (category shl 32) or 0x123400)
                val file = tmp.newFile("$signature-$category.cia").apply { writeBytes(data.array()) }
                assertEquals(category != 0x00040000L, ThreeDsPackageClassifier.isSupplemental(file))
            }
        }
    }

    @Test fun `standalone and cartridge NCCH use little endian program ID even when encrypted`() {
        for (cartridge in listOf(false, true)) for (category in listOf(0x40000L, 0x4000eL, 0x4008cL)) {
            val data = ByteBuffer.allocate(0x600)
            val offset = if (cartridge) 0x400 else 0
            if (cartridge) {
                data.putInt(0x100, 0x4e435344)
                data.order(ByteOrder.LITTLE_ENDIAN).putInt(0x120, 2).putInt(0x124, 1)
            }
            data.order(ByteOrder.BIG_ENDIAN).putInt(offset + 0x100, 0x4e434348)
            data.order(ByteOrder.LITTLE_ENDIAN).putLong(offset + 0x118, (category shl 32) or 0x123400)
            val file = tmp.newFile("$cartridge-$category.${if (cartridge) "cci" else "3ds"}")
                .apply { writeBytes(data.array()) }
            assertEquals(category != 0x40000L, ThreeDsPackageClassifier.isSupplemental(file))
        }
    }

    @Test fun `malformed headers remain visible`() {
        val file = tmp.newFile("Bad.cia")
        assertFalse(ThreeDsPackageClassifier.isSupplemental(file))
        val data = ByteBuffer.allocate(0x2020).order(ByteOrder.LITTLE_ENDIAN)
        data.putInt(0, 0x2020).putInt(8, -1).putInt(12, -1)
        file.writeBytes(data.array())
        assertFalse(ThreeDsPackageClassifier.isSupplemental(file))
    }
}
