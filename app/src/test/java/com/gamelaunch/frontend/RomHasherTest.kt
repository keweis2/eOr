package com.gamelaunch.frontend

import com.gamelaunch.frontend.domain.usecase.RomHasher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RomHasherTest {

    @get:Rule val tmpFolder = TemporaryFolder()

    private fun md5(bytes: ByteArray) =
        MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test fun `hashes uncompressed contents of zip files`() {
        val zipFile = File(tmpFolder.root, "game.zip")
        ZipOutputStream(zipFile.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("inner.sfc"))
            zos.write("ROMDATA".toByteArray())
            zos.closeEntry()
        }
        assertEquals(md5("ROMDATA".toByteArray()), RomHasher.partialMd5(zipFile))
    }

    /**
     * A single ZipInputStream.read() returns only the first inflate chunk, not the whole entry. For an
     * inner ROM larger than one chunk but under the 512 KB window, the hash must cover the ENTIRE inner
     * file so it equals the ROM's full-file md5 (what ScreenScraper matches on). Pseudo-random, poorly
     * compressible data makes the inner stream span several inflate reads.
     */
    @Test fun `hashes full inner rom when it spans multiple inflate chunks`() {
        val innerRom = ByteArray(200 * 1024).also { java.util.Random(42).nextBytes(it) }
        val zipFile = File(tmpFolder.root, "big.zip")
        ZipOutputStream(zipFile.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("inner.sfc"))
            zos.write(innerRom)
            zos.closeEntry()
        }
        assertEquals(md5(innerRom), RomHasher.partialMd5(zipFile))
    }

    @Test fun `hashes only the first 512 KB of a large rom`() {
        val rom = ByteArray(700 * 1024).also { java.util.Random(7).nextBytes(it) }
        val file = File(tmpFolder.root, "big.gba").also { it.writeBytes(rom) }
        assertEquals(md5(rom.copyOf(512 * 1024)), RomHasher.partialMd5(file))
    }

    @Test fun `missing file gives null`() {
        assertNull(RomHasher.partialMd5(File(tmpFolder.root, "nope.sfc")))
    }
}
