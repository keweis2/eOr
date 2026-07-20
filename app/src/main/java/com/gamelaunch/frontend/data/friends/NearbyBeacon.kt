package com.gamelaunch.frontend.data.friends

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.gamelaunch.frontend.domain.friends.FriendCode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LAN discovery for the "add nearby" flow: while active, periodically broadcasts our own friend code
 * over UDP and listens for other eOr devices on the same Wi-Fi, exposing them as a tappable list.
 * Purely for exchanging the (public) device id in person — carries no private data, and only runs
 * while the Add-nearby screen has it started. Ongoing friend sync still happens over Syncthing.
 */
@Singleton
class NearbyBeacon @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class NearbyPeer(val deviceId: String, val displayName: String, val lastSeenMs: Long)

    private val _peers = MutableStateFlow<List<NearbyPeer>>(emptyList())
    val peers: StateFlow<List<NearbyPeer>> = _peers

    private val seen = ConcurrentHashMap<String, NearbyPeer>()
    private var scope: CoroutineScope? = null
    private var socket: DatagramSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    @Synchronized
    fun start(myDeviceId: String, myDisplayName: String) {
        if (scope != null) return // already running
        seen.clear(); _peers.value = emptyList()
        val s = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scope = it }

        val sock = runCatching {
            DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                bind(InetSocketAddress(PORT))
                soTimeout = 1000
            }
        }.getOrElse { Log.w(TAG, "beacon socket failed: ${it.message}"); stop(); return }
        socket = sock

        // A multicast lock lets us receive broadcast/multicast frames on many devices.
        runCatching {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifi.createMulticastLock("eor-friends-nearby").apply {
                setReferenceCounted(false); acquire()
            }
        }

        val payload = JSONObject().apply {
            put("m", MAGIC)
            put("id", myDeviceId)
            put("n", myDisplayName)
        }.toString().toByteArray()

        // Broadcaster
        s.launch {
            val targets = broadcastTargets()
            while (isActive) {
                for (addr in targets) {
                    runCatching { sock.send(DatagramPacket(payload, payload.size, addr, PORT)) }
                }
                delay(2000)
            }
        }
        // Listener
        s.launch {
            val buf = ByteArray(2048)
            while (isActive) {
                val packet = DatagramPacket(buf, buf.size)
                val ok = runCatching { sock.receive(packet); true }.getOrElse { false } // soTimeout → retry
                if (ok) parse(packet, myDeviceId)
            }
        }
        // Pruner: drop peers not seen in the last few seconds.
        s.launch {
            while (isActive) {
                val cutoff = System.currentTimeMillis() - STALE_MS
                var changed = false
                seen.entries.removeIf { if (it.value.lastSeenMs < cutoff) { changed = true; true } else false }
                if (changed) publish()
                delay(1500)
            }
        }
    }

    @Synchronized
    fun stop() {
        scope?.cancel(); scope = null
        runCatching { socket?.close() }; socket = null
        runCatching { multicastLock?.release() }; multicastLock = null
        seen.clear(); _peers.value = emptyList()
    }

    private fun parse(packet: DatagramPacket, myDeviceId: String) {
        runCatching {
            val obj = JSONObject(String(packet.data, packet.offset, packet.length))
            if (obj.optString("m") != MAGIC) return
            val id = obj.optString("id").takeIf { FriendCode.isValidDeviceId(it) } ?: return
            if (id.equals(myDeviceId, ignoreCase = true)) return
            val name = obj.optString("n").ifBlank { "Nearby player" }
            seen[id.uppercase()] = NearbyPeer(id.uppercase(), name, System.currentTimeMillis())
            publish()
        }
    }

    private fun publish() {
        _peers.value = seen.values.sortedBy { it.displayName }
    }

    /** 255.255.255.255 plus the Wi-Fi subnet-directed broadcast (some APs drop limited broadcast). */
    private fun broadcastTargets(): List<InetAddress> {
        val targets = mutableListOf<InetAddress>()
        runCatching { targets.add(InetAddress.getByName("255.255.255.255")) }
        runCatching {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION") val dhcp = wifi.dhcpInfo
            if (dhcp != null && dhcp.ipAddress != 0) {
                val bc = (dhcp.ipAddress and dhcp.netmask) or dhcp.netmask.inv()
                val bytes = byteArrayOf(
                    (bc and 0xff).toByte(),
                    (bc shr 8 and 0xff).toByte(),
                    (bc shr 16 and 0xff).toByte(),
                    (bc shr 24 and 0xff).toByte()
                )
                targets.add(InetAddress.getByAddress(bytes))
            }
        }
        return targets.distinct()
    }

    private companion object {
        const val TAG = "NearbyBeacon"
        const val PORT = 47811
        const val MAGIC = "eor-fr-1"
        const val STALE_MS = 8000L
    }
}
