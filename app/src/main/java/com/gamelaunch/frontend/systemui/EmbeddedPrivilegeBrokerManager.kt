package com.gamelaunch.frontend.systemui

import android.content.Context
import android.os.Build
import android.os.IBinder
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gives the app a small amount of privileged, shell-level functionality without requiring root or
 * keeping a broadly privileged service permanently exposed.
 */
@Singleton
class EmbeddedPrivilegeBrokerManager @Inject constructor(@ApplicationContext private val context: Context) {
    private val startMutex = Mutex()
    fun currentBroker(): IEorPrivilegeBroker? {
        val value = broker ?: return null
        if (!value.asBinder().isBinderAlive ||
            runCatching { value.protocolVersion }.getOrDefault(-1) != EmbeddedBrokerMain.PROTOCOL_VERSION
        ) {
            broker = null; return null
        }
        return value
    }

    fun readiness(): SystemNavigationLockStatus = when {
        Build.VERSION.SDK_INT < 30 -> SystemNavigationLockStatus.UNSUPPORTED
        !EncryptedAdbIdentity(context).isPaired() -> SystemNavigationLockStatus.PAIRING_REQUIRED
        else -> SystemNavigationLockStatus.START_REQUIRED
    }

    suspend fun verifyPairing(): Boolean? {
        if (!EncryptedAdbIdentity(context).isPaired()) return false
        return runCatching {
            val endpoint = localTlsEndpointOrNull()
                ?: discoverLocalAdb(context, "_adb-tls-connect._tcp", 5_000)
            withContext(Dispatchers.IO) {
                EorAdbClient(
                    endpoint,
                    EorAdbKey(context)
                ).use { it.connect() }
            }
            true
        }.getOrElse { failure ->
            if (failure.isAdbAuthenticationFailure()) {
                EncryptedAdbIdentity(context).clearPaired()
                Log.i(EOR_BROKER_TAG, "pairing/revoked")
                false
            } else null
        }
    }

    fun invalidateCurrentBroker() {
        val value = broker
        broker = null
        runCatching { value?.shutdown() }
    }

    suspend fun connectOrStart(): SystemNavigationLockStatus = startMutex.withLock {
        Log.i(EOR_BROKER_TAG, "broker/connect-or-start")
        // Wireless debugging requires Android 11+, which also makes longVersionCode safe below.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R)
            return@withLock SystemNavigationLockStatus.UNSUPPORTED
        currentBroker()?.let { return@withLock SystemNavigationLockStatus.READY }
        if (!EncryptedAdbIdentity(context).isPaired())
            return@withLock SystemNavigationLockStatus.PAIRING_REQUIRED
        return runCatching {
            val endpoint = discoverConnectionEndpoint()
            val token = newToken();
            val app = context.applicationInfo
            val version = context
                .packageManager
                .getPackageInfo(context.packageName, 0)
                .longVersionCode
            BootstrapTokens.issue(token, version)
            val starter = "${app.nativeLibraryDir}/libeor_starter.so"
            val values = listOf(
                starter,
                app.sourceDir,
                app.uid.toString(),
                version.toString(),
                "${context.packageName}.privilege.bootstrap",
                token
            )
            require(values.all { SAFE.matches(it) })
            withContext(Dispatchers.IO) {
                EorAdbClient(
                    endpoint,
                    EorAdbKey(context)
                ).use { it.connect(); it.shell(values.joinToString(" ") { value -> "'$value'" }) }
            }
            repeat(50) {
                currentBroker()?.let {
                    Log.i(EOR_BROKER_TAG, "broker/ready")
                    return@withLock SystemNavigationLockStatus.READY
                }; delay(100)
            }
            SystemNavigationLockStatus.ERROR
        }.getOrElse { failure ->
            Log.e(EOR_BROKER_TAG, "broker/start-failed/${failure.javaClass.simpleName}")
            if (failure.isAdbAuthenticationFailure()) {
                EncryptedAdbIdentity(context).clearPaired()
                Log.i(EOR_BROKER_TAG, "pairing/revoked")
                SystemNavigationLockStatus.PAIRING_REQUIRED
            } else {
                SystemNavigationLockStatus.WIRELESS_DEBUGGING_REQUIRED
            }
        }
    }

    suspend fun pair(code: String, manualPort: Int? = null): SystemNavigationLockStatus =
        runCatching {
            Log.i(
                EOR_BROKER_TAG,
                "pairing/begin/${if (manualPort == null) "discovery" else "manual"}"
            )
            val endpoint = manualPort?.let { port ->
                EorAdbEndpointCache.pairing?.takeIf { it.port == port } ?: LocalAdbEndpoint(
                    preferredLocalAddress(),
                    port
                )
            }
                ?: discoverLocalAdb(context, "_adb-tls-pairing._tcp")
            val ok = withContext(Dispatchers.IO) {
                EorAdbPairingClient(
                    endpoint,
                    code,
                    EorAdbKey(context)
                ).use { it.pair() }
            }
            if (ok && EncryptedAdbIdentity(context).markPaired()) connectOrStart() else SystemNavigationLockStatus.PAIRING_REQUIRED
        }.getOrElse {
            Log.e(
                EOR_BROKER_TAG,
                "pairing/failed/${it.javaClass.simpleName}"
            ); SystemNavigationLockStatus.PAIRING_REQUIRED
        }

    private fun newToken() = ByteArray(32).also(SecureRandom()::nextBytes)
        .let { Base64.encodeToString(it, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING) }

    private suspend fun discoverConnectionEndpoint(): LocalAdbEndpoint {
        // Some Android 13 OEM builds run TLS ADB but fail to publish the connect service through
        // Android's NSD API. The read-only system property is local to this device and provides a
        // safe, narrowly scoped fallback without enabling ADB or requiring an arbitrary shell API.
        localTlsEndpointOrNull()?.let {
            Log.i(EOR_BROKER_TAG, "connection/local-port-ready")
            return it
        }
        var last: Throwable? = null
        repeat(6) { attempt ->
            try {
                return discoverLocalAdb(context, "_adb-tls-connect._tcp", 15_000)
            } catch (failure: Throwable) {
                // A discovery attempt's own timeout is retryable. Only cancellation from the
                // owning lifecycle should abort startup recovery.
                if (failure is kotlinx.coroutines.CancellationException && failure !is TimeoutCancellationException) throw failure
                last = failure
                Log.i(EOR_BROKER_TAG, "connection/discovery-retry/${attempt + 1}")
                localTlsEndpointOrNull()?.let { return it }
                delay(500)
            }
        }
        throw last ?: IllegalStateException("Connection discovery failed")
    }

    companion object {
        private val SAFE = Regex("[A-Za-z0-9_./:=~-]+")

        @Volatile
        private var broker: IEorPrivilegeBroker? = null
        internal fun accept(value: IBinder, version: Long) {
            val candidate = IEorPrivilegeBroker.Stub.asInterface(value)
            if (version <= 0) return
            broker = candidate
            runCatching {
                value.linkToDeath(
                    { if (broker?.asBinder() === value) broker = null },
                    0
                )
            }
        }
    }
}
