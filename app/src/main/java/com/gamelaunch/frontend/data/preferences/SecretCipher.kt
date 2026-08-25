package com.gamelaunch.frontend.data.preferences

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

/**
 * Wraps short, sensitive strings (ScreenScraper password, RetroAchievements API key + session token)
 * with an AndroidKeyStore AES/GCM key so they are not sitting in the DataStore file as plaintext.
 * Mirrors the approach in [com.gamelaunch.frontend.systemui.EncryptedAdbIdentity]: the key never
 * leaves the Keystore, so a `config`/DataStore file lifted off the device (cloud/adb backup, or a
 * rooted read) is unreadable without it.
 *
 * On-disk format is `PREFIX + Base64(iv[12] || ciphertext)`. Values without the prefix are treated
 * as legacy plaintext and returned as-is so pre-encryption installs keep working until the next write
 * (or the one-time [reencrypt] migration) re-stores them encrypted.
 */
internal class SecretCipher {

    /** Encrypt for storage. Blank in → blank out (nothing to protect, and keeps "unset" as ""). */
    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        return runCatching {
            val c = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
            val body = c.iv + c.doFinal(plain.toByteArray(Charsets.UTF_8))
            PREFIX + Base64.encodeToString(body, Base64.NO_WRAP)
        }.getOrDefault(plain) // never lose a value we can't encrypt; it just stays plaintext
    }

    /** Decrypt a stored value. Legacy plaintext (no prefix) is returned unchanged. */
    fun decrypt(stored: String): String {
        if (stored.isEmpty()) return ""
        if (!stored.startsWith(PREFIX)) return stored
        return runCatching {
            val body = Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP)
            val c = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, body.copyOfRange(0, 12)))
            }
            String(c.doFinal(body.copyOfRange(12, body.size)), Charsets.UTF_8)
        }.getOrDefault("") // key invalidated / corrupted → treat as unset, user re-enters
    }

    /** True if [stored] is not yet in encrypted form and holds something worth migrating. */
    fun needsReencrypt(stored: String): Boolean = stored.isNotEmpty() && !stored.startsWith(PREFIX)

    private fun key(): java.security.Key {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        ks.getKey(ALIAS, null)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
        }.generateKey()
    }

    companion object {
        private const val ALIAS = "eor.datastore.secrets.v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PREFIX = "enc1:"
    }
}
