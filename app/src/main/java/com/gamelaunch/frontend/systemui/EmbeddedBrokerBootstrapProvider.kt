package com.gamelaunch.frontend.systemui

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference

/**
 * Bridges the shell-owned broker into the app process because the broker is launched through
 * `app_process` and therefore has no component binding through which it can return its Binder.
 * This provider is exported only for that bootstrap handoff; the shell UID check and single-use,
 * version-bound token prevent other processes or stale broker instances from publishing a Binder.
 */
class EmbeddedBrokerBootstrapProvider : ContentProvider() {
    override fun onCreate() = true
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (Binder.getCallingUid() != Process.SHELL_UID || method != METHOD) return null
        val token = extras?.getString(KEY_TOKEN) ?: return null
        val version = extras.getLong(KEY_VERSION, -1)
        val broker = extras.getBinder(KEY_BINDER) ?: return null
        if (!BootstrapTokens.consume(token, version)) return null
        EmbeddedPrivilegeBrokerManager.accept(broker, version)
        return Bundle().apply {
            putBoolean(KEY_ACCEPTED, true); putBinder(
            KEY_DEATH_TOKEN,
            APP_DEATH_TOKEN
        )
        }
    }

    override fun query(
        a: Uri,
        b: Array<out String>?,
        c: String?,
        d: Array<out String>?,
        e: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    companion object {
        const val METHOD = "publish_broker";
        const val KEY_TOKEN = "token";
        const val KEY_VERSION = "version"
        const val KEY_BINDER = "binder";
        const val KEY_ACCEPTED = "accepted";
        const val KEY_DEATH_TOKEN = "death_token"
        private val APP_DEATH_TOKEN = Binder()
        fun deliverFromShell(
            authority: String,
            token: String,
            version: Long,
            broker: Binder
        ): Boolean = runCatching {
            val amClass = Class.forName("android.app.ActivityManager")
            val am = amClass.getDeclaredMethod("getService").invoke(null)
            val acquire =
                am.javaClass.methods.first { it.name == "getContentProviderExternal" && it.parameterTypes.size == 4 }
            val holder = acquire.invoke(am, authority, 0, null, "eor_privilege_broker")
                ?: return@runCatching false
            val provider = holder.javaClass.getField("provider").get(holder)
            val extras = Bundle().apply {
                putString(KEY_TOKEN, token); putLong(
                KEY_VERSION,
                version
            ); putBinder(KEY_BINDER, broker)
            }
            val call =
                provider.javaClass.methods.first { it.name == "call" && it.parameterTypes.size in 5..6 }
            val result = when {
                call.parameterTypes.first().name == "android.content.AttributionSource" -> call.invoke(
                    provider,
                    android.content.AttributionSource.Builder(Process.SHELL_UID).build(),
                    authority,
                    METHOD,
                    null,
                    extras
                ) as? Bundle

                call.parameterTypes.size == 6 -> call.invoke(
                    provider,
                    null,
                    null,
                    authority,
                    METHOD,
                    null,
                    extras
                ) as? Bundle

                else -> call.invoke(provider, null, authority, METHOD, null, extras) as? Bundle
            }
            val death = result?.getBinder(KEY_DEATH_TOKEN) ?: return@runCatching false
            death.linkToDeath({ Process.killProcess(Process.myPid()) }, 0)
            result.getBoolean(KEY_ACCEPTED)
        }.getOrDefault(false)
    }
}

internal object BootstrapTokens {
    private data class Expected(val hash: ByteArray, val version: Long)

    private val expected = AtomicReference<Expected?>()
    fun issue(token: String, version: Long) {
        expected.set(Expected(hash(token), version))
    }

    fun consume(token: String, version: Long): Boolean {
        val value = expected.get() ?: return false
        if (value.version != version || !MessageDigest.isEqual(
                value.hash,
                hash(token)
            )
        ) return false
        return expected.compareAndSet(value, null)
    }

    private fun hash(value: String) =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
}
