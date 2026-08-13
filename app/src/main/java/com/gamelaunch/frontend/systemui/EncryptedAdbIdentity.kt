package com.gamelaunch.frontend.systemui

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

/**
 * Securely preserves eOr's ADB identity so Android recognizes the app after it has paired once.
 * The paired flag is cached state, actual proof happens when eOr successfully authenticates.
 */
internal class EncryptedAdbIdentity(private val context: Context) {
    private val file get() = context.noBackupFilesDir.resolve("eor_adb_identity")
    fun exists() = file.isFile && runCatching { load().isNotEmpty() }.getOrDefault(false)
    fun isPaired() = exists() && context.getSharedPreferences("eor_adb", Context.MODE_PRIVATE)
        .getBoolean("paired", false)

    fun markPaired() = context.getSharedPreferences("eor_adb", Context.MODE_PRIVATE).edit()
        .putBoolean("paired", true).commit()

    fun clearPaired() = context.getSharedPreferences("eor_adb", Context.MODE_PRIVATE).edit()
        .putBoolean("paired", false).commit()

    fun store(privateKey: ByteArray) {
        require(privateKey.size in 256..16_384);
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(
            Cipher.ENCRYPT_MODE,
            key()
        ); file.writeBytes(c.iv + c.doFinal(privateKey))
    }

    fun load(): ByteArray {
        val all = file.readBytes(); require(all.size in 29..16_412);
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(128, all.copyOfRange(0, 12))
        ); return c.doFinal(all.copyOfRange(12, all.size))
    }

    private fun key(): java.security.Key {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }; ks.getKey(
            ALIAS,
            null
        )?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()
            )
        }.generateKey()
    }

    companion object {
        const val ALIAS = "eor.embedded.adb.identity.v1"
    }
}
