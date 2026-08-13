/*
 * Copyright 2024 Rikka contributors
 * Copyright 2026 eOr contributors
 * Licensed under the Apache License, Version 2.0.
 */
package com.gamelaunch.frontend.systemui

import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.util.Log
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

/** Entry point loaded by /system/bin/app_process under Android's shell UID. */
object EmbeddedBrokerMain {
    const val PROTOCOL_VERSION = 1

    @JvmStatic
    fun main(args: Array<String>) {
        Log.i(EOR_BROKER_TAG, "broker-process/started")
        val parsed = BrokerLaunchArguments.parse(args) ?: run {
            Log.e(EOR_BROKER_TAG, "broker-process/invalid-arguments")
            return
        }
        if (Process.myUid() != Process.SHELL_UID) {
            Log.e(EOR_BROKER_TAG, "broker-process/invalid-uid")
            return
        }
        val broker = EorPrivilegeBroker(parsed.applicationUid)
        val delivered = EmbeddedBrokerBootstrapProvider.deliverFromShell(
            parsed.authority, parsed.token, parsed.versionCode, broker,
        )
        if (!delivered) {
            Log.e(EOR_BROKER_TAG, "broker-process/delivery-rejected")
            return
        }
        Log.i(EOR_BROKER_TAG, "broker-process/delivered")
        Binder.joinThreadPool()
    }
}

internal data class BrokerLaunchArguments(
    val apkPath: String, val applicationUid: Int, val versionCode: Long,
    val authority: String, val token: String,
) {
    companion object {
        fun parse(args: Array<String>): BrokerLaunchArguments? {
            if (args.size != 5) return null
            val path = args[0]
            val uid = args[1].toIntOrNull() ?: return null
            val version = args[2].toLongOrNull() ?: return null
            val authority = args[3]
            val token = args[4]
            if (!path.startsWith("/data/app/") || !path.endsWith(".apk") || uid < 10_000 ||
                version < 1 || !authority.matches(Regex("[a-zA-Z0-9._]+")) ||
                !token.matches(Regex("[A-Za-z0-9_-]{43}"))
            ) return null
            return BrokerLaunchArguments(path, uid, version, authority, token)
        }
    }
}

internal class EorPrivilegeBroker(private val expectedUid: Int) : IEorPrivilegeBroker.Stub() {
    private val stopped = AtomicBoolean(false)
    private var state = 0

    override fun setNavigationLocked(locked: Boolean): Int = authenticated {
        val flags = if (locked) arrayOf(
            "home",
            "recents",
            "statusbar-expansion",
            "notification-peek"
        ) else arrayOf("none")
        repeat(3) {
            if (runCommand("/system/bin/cmd", "statusbar", "send-disable-flag", *flags) == 0 &&
                navigationState() == if (locked) 1 else 0
            ) {
                state = if (locked) 1 else 0
                return@authenticated 0
            }
            Thread.sleep(100)
        }
        -1
    }

    override fun getNavigationLockState(): Int = authenticated { navigationState() }
    override fun getProtocolVersion(): Int = authenticated { EmbeddedBrokerMain.PROTOCOL_VERSION }
    override fun shutdown() =
        authenticated<Unit> { stopped.set(true); Process.killProcess(Process.myPid()) }

    private fun navigationState(): Int {
        val dump = output("/system/bin/dumpsys", "statusbar") ?: return -1
        return shellDisableFlags(dump)?.let { if (it == 0L) 0 else 1 } ?: -1
    }

    private inline fun <T> authenticated(block: () -> T): T {
        check(!stopped.get() && Binder.getCallingUid() == expectedUid) { "unauthorized broker caller" }
        return block()
    }

    private fun runCommand(vararg command: String) = runCatching {
        ProcessBuilder(*command).redirectErrorStream(true).start().let { p ->
            p.inputStream.use { input ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    input.transferTo(OutputStream.nullOutputStream())
                } else {
                    // TODO: not tested this path (no old device)
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (input.read(buffer) != -1) {
                        // Drain output so the child process cannot block on a full pipe.
                    }
                }
            }
            p.waitFor()
        }
    }.getOrDefault(-1)

    private fun output(vararg command: String) = runCatching {
        ProcessBuilder(*command).redirectErrorStream(true).start().let { p ->
            // limit the returned command to 1 mb
            val value = p.inputStream.bufferedReader().use { it.readText().take(1_048_576) }
            if (p.waitFor() == 0) value else null
        }
    }.getOrNull()
}

internal fun shellDisableFlags(statusBarDump: String): Long? {
    val record = statusBarDump.lineSequence().firstOrNull {
        "pkg=android" in it && "StatusBarShellCommandToken" in it
    } ?: return null
    return Regex("what1=0x([0-9a-fA-F]+)").find(record)?.groupValues?.get(1)?.toLongOrNull(16)
}
