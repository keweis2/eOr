/*
 * Adapted from Shizuku, Copyright 2017-2025 Rikka contributors.
 * Copyright 2026 eOr contributors. Licensed under Apache-2.0.
 */
package com.gamelaunch.frontend.systemui

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Base64
import android.util.Log
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.math.BigInteger
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAKeyGenParameterSpec
import java.security.spec.RSAPublicKeySpec
import java.util.Date
import java.util.Locale
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509ExtendedTrustManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.conscrypt.Conscrypt
import kotlin.coroutines.resume

internal object EorAdbProtocol {
    const val CNXN = 0x4e584e43;
    const val AUTH = 0x48545541;
    const val OPEN = 0x4e45504f
    const val OKAY = 0x59414b4f;
    const val CLSE = 0x45534c43;
    const val WRTE = 0x45545257;
    const val STLS = 0x534c5453
    const val VERSION = 0x01000000;
    const val MAX_DATA = 4096;
    const val TOKEN = 1;
    const val SIGNATURE = 2;
    const val PUBLIC_KEY = 3
}

internal data class EorAdbMessage(
    val command: Int,
    val arg0: Int,
    val arg1: Int,
    val data: ByteArray = byteArrayOf()
) {
    fun bytes(): ByteArray =
        ByteBuffer.allocate(24 + data.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(command); putInt(arg0); putInt(arg1); putInt(data.size); putInt(data.sumOf { it.toInt() and 255 }); putInt(
            command xor -1
        ); put(data)
        }.array()

    companion object {
        const val MAX_PAYLOAD = 1024 * 1024
        fun read(input: DataInputStream): EorAdbMessage {
            val h = ByteArray(24); input.readFully(h);
            val b = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN)
            val command = b.int;
            val a0 = b.int;
            val a1 = b.int;
            val size = b.int;
            val checksum = b.int;
            val magic = b.int
            require(size in 0..MAX_PAYLOAD && magic == (command xor -1)) { "Malformed ADB header" }
            val data =
                ByteArray(size); input.readFully(data); require(data.sumOf { it.toInt() and 255 } == checksum) { "Malformed ADB payload" }
            return EorAdbMessage(command, a0, a1, data)
        }
    }
}

internal class EorAdbKey(context: Context) {
    private val privateKey: RSAPrivateKey
    private val publicKey: RSAPublicKey
    private val certificate: X509Certificate

    init {
        val storage = EncryptedAdbIdentity(context)
        privateKey = if (storage.exists()) KeyFactory.getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(storage.load())) as RSAPrivateKey else {
            (KeyPairGenerator.getInstance("RSA")
                .apply { initialize(RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4)) }
                .generateKeyPair().private as RSAPrivateKey).also { storage.store(it.encoded) }
        }
        publicKey = KeyFactory.getInstance("RSA").generatePublic(
            RSAPublicKeySpec(
                privateKey.modulus,
                RSAKeyGenParameterSpec.F4
            )
        ) as RSAPublicKey
        val built = X509v3CertificateBuilder(
            X500Name("CN=eOr"),
            BigInteger.ONE,
            Date(0),
            Date(4102444800000L),
            Locale.ROOT,
            X500Name("CN=eOr"),
            SubjectPublicKeyInfo.getInstance(publicKey.encoded)
        ).build(JcaContentSignerBuilder("SHA256withRSA").build(privateKey))
        certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(built.encoded.inputStream()) as X509Certificate
    }

    val adbPublicKey: ByteArray get() = publicKey.adbEncoded("eor@localhost")
    fun sign(token: ByteArray): ByteArray {
        require(token.size == 20);
        val padding = byteArrayOf(0, 1) + ByteArray(218) { -1 } + byteArrayOf(
            0,
            0x30,
            0x21,
            0x30,
            9,
            6,
            5,
            0x2b,
            0x0e,
            3,
            2,
            0x1a,
            5,
            0,
            4,
            0x14
        )
        return javax.crypto.Cipher.getInstance("RSA/ECB/NoPadding")
            .run { init(javax.crypto.Cipher.ENCRYPT_MODE, privateKey); doFinal(padding + token) }
    }

    val sslContext: SSLContext by lazy {
        val km = object : X509ExtendedKeyManager() {
            override fun chooseClientAlias(
                k: Array<out String>,
                i: Array<out Principal>?,
                s: Socket?
            ) = "eor";

            override fun getCertificateChain(a: String?) =
                if (a == "eor") arrayOf(certificate) else null;

            override fun getPrivateKey(a: String?) = if (a == "eor") privateKey else null;
            override fun getClientAliases(k: String?, i: Array<out Principal>?) = arrayOf("eor");
            override fun getServerAliases(k: String?, i: Array<out Principal>?) = null;
            override fun chooseServerAlias(k: String?, i: Array<out Principal>?, s: Socket?) = null
        }
        val tm = object : X509ExtendedTrustManager() {
            override fun getAcceptedIssuers() = emptyArray<X509Certificate>();
            override fun checkClientTrusted(c: Array<out X509Certificate>?, a: String?) {};
            override fun checkServerTrusted(c: Array<out X509Certificate>?, a: String?) {};
            override fun checkClientTrusted(
                c: Array<out X509Certificate>?,
                a: String?,
                s: Socket?
            ) {
            };
            override fun checkServerTrusted(
                c: Array<out X509Certificate>?,
                a: String?,
                s: Socket?
            ) {
            };
            override fun checkClientTrusted(
                c: Array<out X509Certificate>?,
                a: String?,
                e: SSLEngine?
            ) {
            };
            override fun checkServerTrusted(
                c: Array<out X509Certificate>?,
                a: String?,
                e: SSLEngine?
            ) {
            }
        }
        SSLContext.getInstance("TLS", Conscrypt.newProvider())
            .apply { init(arrayOf(km), arrayOf(tm), SecureRandom()) }
    }
}

private fun RSAPublicKey.adbEncoded(name: String): ByteArray {
    fun BigInteger.words(): IntArray {
        val out = IntArray(64);
        var n = this;
        val r = BigInteger.ONE.shiftLeft(32); for (i in out.indices) {
            val q = n.divideAndRemainder(r); n = q[0]; out[i] = q[1].toInt()
        }; return out
    }

    val r32 = BigInteger.ONE.shiftLeft(32);
    val n0 = modulus.mod(r32).modInverse(r32).negate();
    val rr = BigInteger.ONE.shiftLeft(2048).pow(2).mod(modulus)
    val raw = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN).apply {
        putInt(64); putInt(n0.toInt()); modulus.words().forEach(::putInt); rr.words()
        .forEach(::putInt); putInt(publicExponent.toInt())
    }.array()
    return Base64.encode(raw, Base64.NO_WRAP) + " $name\u0000".toByteArray()
}

internal data class LocalAdbEndpoint(val address: InetAddress, val port: Int)
internal class AdbAuthenticationException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

internal fun Throwable.isAdbAuthenticationFailure(): Boolean = generateSequence(this) { it.cause }
    .any { it is AdbAuthenticationException || it is SSLException }

internal object EorAdbEndpointCache {
    @Volatile
    var pairing: LocalAdbEndpoint? = null
}

internal object EorAdbNative {
    init {
        System.loadLibrary("eor_adb")
    }

    external fun tlsPort(): Int
}

internal fun localTlsEndpointOrNull(): LocalAdbEndpoint? = EorAdbNative.tlsPort()
    .takeIf { it in 1..65535 }
    ?.let { LocalAdbEndpoint(preferredLocalAddress(), it) }

internal fun preferredLocalAddress(): InetAddress =
    NetworkInterface.getNetworkInterfaces().asSequence()
        .filter { it.isUp && !it.isLoopback }.flatMap { it.inetAddresses.asSequence() }
        .firstOrNull { !it.isLoopbackAddress && it.address.size == 4 && it.isSiteLocalAddress }
        ?: InetAddress.getLoopbackAddress()

internal suspend fun discoverLocalAdb(
    context: Context,
    type: String,
    timeoutMs: Long = 15_000
): LocalAdbEndpoint = withTimeout(timeoutMs) {
    suspendCancellableCoroutine { continuation ->
        Log.i(
            EOR_BROKER_TAG,
            "discovery/start/${if (type.contains("pairing")) "pairing" else "connect"}"
        )
        val nsd = context.getSystemService(NsdManager::class.java);
        var started = false
        lateinit var listener: NsdManager.DiscoveryListener
        fun local(address: InetAddress) = NetworkInterface.getNetworkInterfaces().asSequence()
            .flatMap { it.inetAddresses.asSequence() }.any { it == address }

        fun finish(endpoint: LocalAdbEndpoint?) {
            if (continuation.isActive) {
                Log.i(
                    EOR_BROKER_TAG,
                    if (endpoint != null) "discovery/resolved" else "discovery/failed"
                ); if (started) runCatching { nsd.stopServiceDiscovery(listener) }; if (endpoint != null) continuation.resume(
                    endpoint
                ) else continuation.cancel(IllegalStateException("Discovery failed"))
            }
        }
        listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(t: String) {
                started = true
            };
            override fun onDiscoveryStopped(t: String) {};
            override fun onStartDiscoveryFailed(t: String, e: Int) = finish(null);
            override fun onStopDiscoveryFailed(t: String, e: Int) {}
            override fun onServiceLost(s: NsdServiceInfo) {};
            override fun onServiceFound(s: NsdServiceInfo) {
                if (s.serviceType.trimEnd('.') != type.trimEnd('.')) return; nsd.resolveService(s,
                    object : NsdManager.ResolveListener {
                        override fun onResolveFailed(s: NsdServiceInfo, e: Int) {};
                        override fun onServiceResolved(s: NsdServiceInfo) {
                            if (s.port in 1..65535 && local(s.host)) finish(
                                LocalAdbEndpoint(
                                    s.host,
                                    s.port
                                )
                            )
                        }
                    })
            }
        }
        continuation.invokeOnCancellation {
            if (started) runCatching {
                nsd.stopServiceDiscovery(
                    listener
                )
            }
        }
        nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
    }
}

internal class AdbPairingContext private constructor(private val ptr: Long) : Closeable {
    val message get() = nativeMessage(ptr);
    fun init(peer: ByteArray) = nativeInit(ptr, peer);
    fun encrypt(v: ByteArray) = nativeEncrypt(ptr, v);
    fun decrypt(v: ByteArray) = nativeDecrypt(ptr, v)
    override fun close() = nativeDestroy(ptr)
    private external fun nativeMessage(p: Long): ByteArray;
    private external fun nativeInit(p: Long, v: ByteArray): Boolean;
    private external fun nativeEncrypt(p: Long, v: ByteArray): ByteArray?;
    private external fun nativeDecrypt(p: Long, v: ByteArray): ByteArray?;
    private external fun nativeDestroy(p: Long)

    companion object { init {
        System.loadLibrary("eor_adb")
    };
        @JvmStatic
        private external fun nativeCreate(client: Boolean, password: ByteArray): Long;
        fun create(password: ByteArray) =
            nativeCreate(true, password).takeIf { it != 0L }?.let(::AdbPairingContext)
    }
}

internal class EorAdbPairingClient(
    private val endpoint: LocalAdbEndpoint,
    private val code: String,
    private val key: EorAdbKey
) : Closeable {
    private var socket: Socket? = null
    fun pair(): Boolean {
        require(code.matches(Regex("[0-9]{6}")) && endpoint.port in 1..65535)
        Log.i(EOR_BROKER_TAG, "pairing/socket-connect")
        val plain = Socket().apply {
            connect(
                InetSocketAddress(endpoint.address, endpoint.port),
                5000
            ); soTimeout = 5000; tcpNoDelay = true
        }; socket = plain
        val tls = key.sslContext.socketFactory.createSocket(
            plain,
            endpoint.address.hostAddress,
            endpoint.port,
            true
        ) as SSLSocket; tls.startHandshake()
        Log.i(EOR_BROKER_TAG, "pairing/tls-ready")
        val material = Conscrypt.exportKeyingMaterial(tls, "adb-label\u0000", null, 64);
        val context = AdbPairingContext.create(code.toByteArray() + material) ?: return false
        context.use { p ->
            val input = DataInputStream(tls.inputStream);
            val output = DataOutputStream(tls.outputStream)
            fun send(type: Int, data: ByteArray) {
                output.write(
                    byteArrayOf(1, type.toByte()) + ByteBuffer.allocate(4).putInt(data.size)
                        .array() + data
                ); output.flush()
            }

            fun receive(expected: Int): ByteArray {
                val h = ByteArray(6); input.readFully(h);
                val b = ByteBuffer.wrap(h).order(ByteOrder.BIG_ENDIAN); require(
                    b.get().toInt() == 1 && b.get().toInt() == expected
                );
                val n = b.int; require(n in 1..16384); return ByteArray(n).also(input::readFully)
            }
            send(0, p.message); if (!p.init(receive(0))) return false
            Log.i(EOR_BROKER_TAG, "pairing/spake-ready")
            val peer = ByteArray(8192).also {
                it[0] = 0; key.adbPublicKey.copyInto(
                it,
                1,
                0,
                minOf(key.adbPublicKey.size, 8191)
            )
            }; send(1, p.encrypt(peer) ?: return false)
            val accepted = (p.decrypt(receive(1)) ?: return false).size == 8192
            Log.i(EOR_BROKER_TAG, if (accepted) "pairing/accepted" else "pairing/peer-invalid")
            return accepted
        }
    }

    override fun close() {
        runCatching { socket?.close() }
    }
}

internal class EorAdbClient(private val endpoint: LocalAdbEndpoint, private val key: EorAdbKey) :
    Closeable {
    private var socket: Socket? = null;
    private lateinit var input: DataInputStream;
    private lateinit var output: DataOutputStream
    fun connect() {
        Log.i(EOR_BROKER_TAG, "connection/socket-connect");
        val plain = Socket().apply {
            connect(
                InetSocketAddress(endpoint.address, endpoint.port),
                5000
            ); soTimeout = 10_000; tcpNoDelay = true
        }; socket = plain; input = DataInputStream(plain.inputStream); output =
            DataOutputStream(plain.outputStream); write(
            EorAdbProtocol.CNXN,
            EorAdbProtocol.VERSION,
            EorAdbProtocol.MAX_DATA,
            "host::\u0000".toByteArray()
        );
        var m = read(); if (m.command == EorAdbProtocol.STLS) {
            write(EorAdbProtocol.STLS, EorAdbProtocol.VERSION, 0);
            val tls = key.sslContext.socketFactory.createSocket(
                plain,
                endpoint.address.hostAddress,
                endpoint.port,
                true
            ) as SSLSocket; try {
                tls.startHandshake()
            } catch (failure: SSLException) {
                throw AdbAuthenticationException("ADB TLS authentication failed", failure)
            }; input = DataInputStream(tls.inputStream); output =
                DataOutputStream(tls.outputStream); m = read()
        } else if (m.command == EorAdbProtocol.AUTH && m.arg0 == EorAdbProtocol.TOKEN) {
            write(EorAdbProtocol.AUTH, EorAdbProtocol.SIGNATURE, 0, key.sign(m.data)); m =
                read(); if (m.command != EorAdbProtocol.CNXN) {
                write(EorAdbProtocol.AUTH, EorAdbProtocol.PUBLIC_KEY, 0, key.adbPublicKey); m =
                    read()
            }
        }; if (m.command != EorAdbProtocol.CNXN) throw AdbAuthenticationException("ADB authentication failed"); Log.i(
            EOR_BROKER_TAG,
            "connection/authenticated"
        )
    }

    fun shell(command: String): String {
        require(command.length <= 4096); write(
            EorAdbProtocol.OPEN,
            1,
            0,
            "shell:$command\u0000".toByteArray()
        );
        val result = java.io.ByteArrayOutputStream(); while (true) {
            val m = read(); when (m.command) {
                EorAdbProtocol.OKAY -> Unit; EorAdbProtocol.WRTE -> {
                    require(result.size() + m.data.size <= 1024 * 1024); result.write(m.data); write(
                        EorAdbProtocol.OKAY,
                        1,
                        m.arg0
                    )
                }; EorAdbProtocol.CLSE -> {
                    write(
                        EorAdbProtocol.CLSE,
                        1,
                        m.arg0
                    ); return result.toString(Charsets.UTF_8.name())
                }; else -> error("Unexpected ADB response")
            }
        }
    }

    private fun write(c: Int, a0: Int, a1: Int, d: ByteArray = byteArrayOf()) {
        output.write(EorAdbMessage(c, a0, a1, d).bytes()); output.flush()
    };
    private fun read() = EorAdbMessage.read(input)
    override fun close() {
        runCatching { socket?.close() }
    }
}

internal const val EOR_BROKER_TAG = "EorBroker"
