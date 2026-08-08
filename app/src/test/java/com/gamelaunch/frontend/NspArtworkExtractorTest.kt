package com.gamelaunch.frontend

import com.gamelaunch.frontend.domain.usecase.NspArtworkExtractor
import com.gamelaunch.frontend.domain.usecase.ProdKeysLocator
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Fast, device-free coverage of the complete embedded-artwork decoding pipeline. */
class NspArtworkExtractorTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun `extracts icon from a synthetic encrypted NSP`() {
        val icon = byteArrayOf(
            0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
            1, 2, 3, 4
        )
        val headerKey = ByteArray(32) { (it + 1).toByte() }
        val keyAreaKey = ByteArray(16) { (0x40 + it).toByte() }
        val sectionKey = ByteArray(16) { (0x70 + it).toByte() }
        val keys = tmp.newFile("prod.keys").apply {
            writeText(
                "header_key = ${headerKey.hex()}\n" +
                    "key_area_key_application_00 = ${keyAreaKey.hex()}\n"
            )
        }
        val nsp = tmp.newFile("fixture.nsp").apply {
            writeBytes(syntheticNsp(icon, headerKey, keyAreaKey, sectionKey))
        }
        val locator: ProdKeysLocator = mock()
        whenever(locator.findAll()).thenReturn(sequenceOf(keys))

        assertArrayEquals(icon, NspArtworkExtractor(locator).extract(nsp))
    }

    @Test fun `malformed NSP is a best effort miss rather than an exception`() {
        val keys = tmp.newFile("prod.keys").apply { writeText("header_key = ${ByteArray(32).hex()}") }
        val locator: ProdKeysLocator = mock()
        whenever(locator.findAll()).thenReturn(sequenceOf(keys))

        assertNull(NspArtworkExtractor(locator).extract(tmp.newFile("broken.nsp")))
    }

    /**
     * Optional regression test for a user-owned dump. It remains a normal local JVM test and is
     * skipped unless both paths are supplied, so no game data or keys enter the repository:
     *
     * EOR_TEST_NSP=/path/game.nsp EOR_TEST_PROD_KEYS=/path/prod.keys \
     *   ./gradlew testFullDebugUnitTest --tests '*NspArtworkExtractorTest.real*'
     */
    @Test fun `real user NSP yields image bytes when fixture paths are supplied`() {
        val nsp = System.getenv("EOR_TEST_NSP")?.let(::File)
        val keys = System.getenv("EOR_TEST_PROD_KEYS")?.let(::File)
        assumeTrue(nsp?.isFile == true && keys?.isFile == true)
        val locator: ProdKeysLocator = mock()
        whenever(locator.findAll()).thenReturn(sequenceOf(keys!!))

        val artwork = NspArtworkExtractor(locator).extract(nsp!!)

        assertNotNull("The supplied NSP could not be decoded", artwork)
        val isJpeg = artwork!!.size >= 3 && artwork[0] == 0xff.toByte() &&
            artwork[1] == 0xd8.toByte() && artwork[2] == 0xff.toByte()
        val isPng = artwork.size >= 8 && artwork.copyOfRange(0, 8).contentEquals(
            byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
        )
        assertTrue("Extracted bytes are not a JPEG or PNG", isJpeg || isPng)
    }

    private fun syntheticNsp(
        icon: ByteArray,
        headerKey: ByteArray,
        keyAreaKey: ByteArray,
        sectionKey: ByteArray
    ): ByteArray {
        val sectionStart = 0xc00
        val sectionSize = 0x600
        val romFsOffset = 0x200
        val metadata = ByteArray(align(0x20 + "icon_AmericanEnglish.dat".length, 4)).also {
            it.putLong(8, 0)
            it.putLong(16, icon.size.toLong())
            it.putInt(28, "icon_AmericanEnglish.dat".length)
            "icon_AmericanEnglish.dat".encodeToByteArray().copyInto(it, 0x20)
        }
        val romFs = ByteArray(sectionSize)
        romFs.putLong(romFsOffset, 0x50)
        romFs.putLong(romFsOffset + 0x38, 0x50)
        romFs.putLong(romFsOffset + 0x40, metadata.size.toLong())
        romFs.putLong(romFsOffset + 0x48, (0x50 + metadata.size).toLong())
        metadata.copyInto(romFs, romFsOffset + 0x50)
        icon.copyInto(romFs, romFsOffset + 0x50 + metadata.size)

        val header = ByteArray(0xc00)
        "NCA3".encodeToByteArray().copyInto(header, 0x200)
        header[0x205] = 2 // Control content.
        header[0x206] = 1 // master-key revision 0.
        header.putInt(0x240, sectionStart / 0x200)
        header.putInt(0x244, (sectionStart + sectionSize) / 0x200)
        header[0x402] = 0 // RomFS partition.
        header[0x403] = 3 // RomFS filesystem.
        header[0x404] = 3 // AES-CTR.
        "IVFC".encodeToByteArray().copyInto(header, 0x408)
        header.putInt(0x414, 7) // Master-hash layer plus six level records.
        val finalIvfcLevel = 0x418 + 5 * 0x18
        header.putLong(finalIvfcLevel, romFsOffset.toLong())
        header.putLong(finalIvfcLevel + 8, (0x50 + metadata.size + icon.size).toLong())
        repeat(4) { slot ->
            val plain = if (slot == 2) sectionKey else ByteArray(16)
            aesEcb(keyAreaKey, plain, Cipher.ENCRYPT_MODE).copyInto(header, 0x300 + slot * 16)
        }
        val encryptedHeader = xts(header, headerKey, Cipher.ENCRYPT_MODE)
        val counter = ByteArray(16).also {
            ByteBuffer.wrap(it, 8, 8).order(ByteOrder.BIG_ENDIAN)
                .putLong((sectionStart / 16).toLong())
        }
        val encryptedSection = aesCtr(sectionKey, counter, romFs)
        val nca = encryptedHeader + encryptedSection

        val name = "control.nca\u0000".encodeToByteArray()
        val pfsHeader = ByteArray(0x10 + 0x18 + name.size)
        "PFS0".encodeToByteArray().copyInto(pfsHeader)
        pfsHeader.putInt(4, 1)
        pfsHeader.putInt(8, name.size)
        pfsHeader.putLong(0x18, nca.size.toLong())
        name.copyInto(pfsHeader, 0x28)
        return pfsHeader + nca
    }

    private fun xts(input: ByteArray, key: ByteArray, mode: Int): ByteArray = input.copyOf().also { output ->
        val dataKey = key.copyOfRange(0, 16)
        val tweakKey = key.copyOfRange(16, 32)
        for (sector in output.indices step 0x200) {
            val tweakInput = ByteArray(16).also {
                ByteBuffer.wrap(it, 8, 8).order(ByteOrder.BIG_ENDIAN).putLong((sector / 0x200).toLong())
            }
            var tweak = aesEcb(tweakKey, tweakInput, Cipher.ENCRYPT_MODE)
            for (block in sector until minOf(sector + 0x200, output.size) step 16) {
                val mixed = ByteArray(16) { i -> (output[block + i].toInt() xor tweak[i].toInt()).toByte() }
                val transformed = aesEcb(dataKey, mixed, mode)
                for (i in 0 until 16) output[block + i] = (transformed[i].toInt() xor tweak[i].toInt()).toByte()
                tweak = multiplyByX(tweak)
            }
        }
    }

    private fun multiplyByX(value: ByteArray): ByteArray {
        var carry = 0
        for (i in value.indices) {
            val next = (value[i].toInt() ushr 7) and 1
            value[i] = ((value[i].toInt() shl 1) or carry).toByte()
            carry = next
        }
        if (carry != 0) value[0] = (value[0].toInt() xor 0x87).toByte()
        return value
    }

    private fun aesEcb(key: ByteArray, input: ByteArray, mode: Int): ByteArray =
        Cipher.getInstance("AES/ECB/NoPadding").run {
            init(mode, SecretKeySpec(key, "AES")); doFinal(input)
        }

    private fun aesCtr(key: ByteArray, counter: ByteArray, input: ByteArray): ByteArray =
        Cipher.getInstance("AES/CTR/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(counter)); doFinal(input)
        }

    private fun ByteArray.putInt(offset: Int, value: Int) {
        ByteBuffer.wrap(this, offset, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(value)
    }

    private fun ByteArray.putLong(offset: Int, value: Long) {
        ByteBuffer.wrap(this, offset, 8).order(ByteOrder.LITTLE_ENDIAN).putLong(value)
    }

    private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 0xff) }
    private fun align(value: Int, alignment: Int) = (value + alignment - 1) and (alignment - 1).inv()
}
