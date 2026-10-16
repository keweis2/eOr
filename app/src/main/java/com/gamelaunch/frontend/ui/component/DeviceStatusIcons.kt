package com.gamelaunch.frontend.ui.component

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.hardware.input.InputManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.view.Display
import android.view.InputDevice
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Battery0Bar
import androidx.compose.material.icons.filled.Battery2Bar
import androidx.compose.material.icons.filled.Battery3Bar
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.Battery6Bar
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.NetworkWifi1Bar
import androidx.compose.material.icons.filled.NetworkWifi2Bar
import androidx.compose.material.icons.filled.NetworkWifi3Bar
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamelaunch.frontend.platform.display.DualScreenDevices
import kotlinx.coroutines.delay
import java.util.Date

/** Live device state for the home header (eOr hides Android's status bar). */
data class DeviceStatus(
    val batteryPercent: Int? = null,
    val charging: Boolean = false,
    val network: NetworkKind = NetworkKind.NONE,
    /** Wi-Fi signal 0..4, when known. */
    val wifiLevel: Int = 4,
    val bluetoothOn: Boolean = false,
    /** A Bluetooth headset/speaker or an external controller is connected. */
    val bluetoothConnected: Boolean = false,
    val externalDisplay: Boolean = false
)

enum class NetworkKind { NONE, WIFI, ETHERNET, OTHER }

/**
 * Battery, network, Bluetooth, display-out and clock icons, so users can see device state without
 * leaving eOr. Display-only and never a focus target, so it can't interfere with controller
 * navigation. Everything updates live from system callbacks/broadcasts while it's on screen.
 */
@Composable
fun DeviceStatusIcons(tint: Color, modifier: Modifier = Modifier) {
    val status = rememberDeviceStatus()
    val description = buildString {
        status.batteryPercent?.let { append("Battery $it%${if (status.charging) ", charging" else ""}. ") }
        append(
            when (status.network) {
                NetworkKind.WIFI -> "Wi-Fi connected. "
                NetworkKind.ETHERNET -> "Ethernet connected. "
                NetworkKind.OTHER -> "Online. "
                NetworkKind.NONE -> "No network. "
            }
        )
        if (status.bluetoothOn) append(if (status.bluetoothConnected) "Bluetooth connected. " else "Bluetooth on. ")
        if (status.externalDisplay) append("External display connected. ")
    }
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (status.externalDisplay) StatusIcon(Icons.Default.Tv, tint)
        if (status.bluetoothOn) {
            StatusIcon(if (status.bluetoothConnected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth, tint)
        }
        StatusIcon(networkIcon(status), tint)
        status.batteryPercent?.let { pct ->
            val low = pct <= 15 && !status.charging
            val color = if (low) MaterialTheme.colorScheme.error else tint
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusIcon(batteryIcon(pct, status.charging), color)
                Text("$pct%", color = color, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Clock(tint)
    }
}

@Composable
private fun StatusIcon(icon: ImageVector, tint: Color) =
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(21.dp))

@Composable
private fun Clock(tint: Color) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            // Wake just after the next minute boundary.
            delay(60_000L - System.currentTimeMillis() % 60_000L + 50L)
        }
    }
    Text(
        DateFormat.getTimeFormat(context).format(now),
        color = tint, fontSize = 16.sp, fontWeight = FontWeight.SemiBold
    )
}

private fun networkIcon(s: DeviceStatus): ImageVector = when (s.network) {
    NetworkKind.ETHERNET -> Icons.Default.SettingsEthernet
    NetworkKind.NONE -> Icons.Default.SignalWifiOff
    NetworkKind.WIFI, NetworkKind.OTHER -> when (s.wifiLevel) {
        0, 1 -> Icons.Default.NetworkWifi1Bar
        2 -> Icons.Default.NetworkWifi2Bar
        3 -> Icons.Default.NetworkWifi3Bar
        else -> Icons.Default.SignalWifi4Bar
    }
}

private fun batteryIcon(pct: Int, charging: Boolean): ImageVector = when {
    charging -> Icons.Default.BatteryChargingFull
    pct >= 95 -> Icons.Default.BatteryFull
    pct >= 80 -> Icons.Default.Battery6Bar
    pct >= 65 -> Icons.Default.Battery5Bar
    pct >= 50 -> Icons.Default.Battery4Bar
    pct >= 35 -> Icons.Default.Battery3Bar
    pct >= 15 -> Icons.Default.Battery2Bar
    else -> Icons.Default.Battery0Bar
}

/** Collects [DeviceStatus] from system services while composed; unregisters everything on dispose. */
@Composable
fun rememberDeviceStatus(): DeviceStatus {
    val context = LocalContext.current.applicationContext
    var status by remember { mutableStateOf(DeviceStatus()) }

    DisposableEffect(context) {
        val main = Handler(Looper.getMainLooper())
        fun update(block: (DeviceStatus) -> DeviceStatus) { main.post { status = block(status) } }

        // ── Battery (sticky broadcast, no permission) ──
        val batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val pct = if (level >= 0 && scale > 0) level * 100 / scale else null
                val charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
                update { it.copy(batteryPercent = pct, charging = charging) }
            }
        }
        context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        // ── Network ──
        val cm = context.getSystemService(ConnectivityManager::class.java)
        fun applyCapabilities(caps: NetworkCapabilities) {
            val kind = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkKind.ETHERNET
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkKind.WIFI
                else -> NetworkKind.OTHER
            }
            val level = if (kind == NetworkKind.WIFI && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                caps.signalStrength != NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED
            ) wifiLevel(context, caps.signalStrength) else 4
            update { it.copy(network = kind, wifiLevel = level) }
        }
        val netCallback = object : ConnectivityManager.NetworkCallback() {
            // Android 6/7 don't follow onAvailable with onCapabilitiesChanged (that started in 8.0), so
            // read the capabilities here too or the icon would stay "offline" until something changed.
            override fun onAvailable(network: Network) {
                runCatching { cm?.getNetworkCapabilities(network) }.getOrNull()?.let(::applyCapabilities)
            }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = applyCapabilities(caps)
            override fun onLost(network: Network) = update { it.copy(network = NetworkKind.NONE) }
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) cm?.registerDefaultNetworkCallback(netCallback)
            // Android 6: no "default network" callback; listen to any internet-capable network instead.
            else cm?.registerNetworkCallback(
                android.net.NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
                netCallback
            )
        }

        // ── Bluetooth: radio state + connected audio / external controllers ──
        val btAdapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        val audio = context.getSystemService(AudioManager::class.java)
        val input = context.getSystemService(InputManager::class.java)
        fun refreshBluetooth() {
            val on = runCatching { btAdapter?.isEnabled == true }.getOrDefault(false)
            val btAudio = audio?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.any {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
            } == true
            // A controller paired over Bluetooth shows up as an external input device. (Android doesn't
            // say which bus it's on, so a USB pad also counts while Bluetooth is on — close enough.)
            val externalPad = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && InputDevice.getDeviceIds().any { id ->
                InputDevice.getDevice(id)?.let { d ->
                    d.isExternal && (d.sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                } == true
            }
            update { it.copy(bluetoothOn = on, bluetoothConnected = on && (btAudio || externalPad)) }
        }
        val btReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = refreshBluetooth()
        }
        runCatching { context.registerReceiver(btReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)) }
        val audioCallback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) = refreshBluetooth()
            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>?) = refreshBluetooth()
        }
        audio?.registerAudioDeviceCallback(audioCallback, main)
        val inputListener = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) = refreshBluetooth()
            override fun onInputDeviceRemoved(deviceId: Int) = refreshBluetooth()
            override fun onInputDeviceChanged(deviceId: Int) = Unit
        }
        input?.registerInputDeviceListener(inputListener, main)
        refreshBluetooth()

        // ── External display (HDMI / USB-C) ──
        val dm = context.getSystemService(DisplayManager::class.java)
        val builtInSecondScreen = DualScreenDevices.isKnownDualScreen()
        fun refreshDisplays() {
            val others = dm?.displays.orEmpty().filter { d ->
                d.displayId != Display.DEFAULT_DISPLAY && d.state == Display.STATE_ON &&
                    (d.flags and Display.FLAG_PRIVATE) == 0 &&
                    !d.name.contains("built-in", ignoreCase = true)
            }
            // A dual-screen handheld's own second panel is one of these; only more than that is "out".
            val external = if (builtInSecondScreen) others.size > 1 else others.isNotEmpty()
            update { it.copy(externalDisplay = external) }
        }
        val displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = refreshDisplays()
            override fun onDisplayRemoved(displayId: Int) = refreshDisplays()
            override fun onDisplayChanged(displayId: Int) = refreshDisplays()
        }
        dm?.registerDisplayListener(displayListener, main)
        refreshDisplays()

        onDispose {
            runCatching { context.unregisterReceiver(batteryReceiver) }
            runCatching { context.unregisterReceiver(btReceiver) }
            runCatching { cm?.unregisterNetworkCallback(netCallback) }
            audio?.unregisterAudioDeviceCallback(audioCallback)
            input?.unregisterInputDeviceListener(inputListener)
            dm?.unregisterDisplayListener(displayListener)
            main.removeCallbacksAndMessages(null)
        }
    }
    return status
}

/** Wi-Fi signal (dBm) → 0..4 bars. */
@Suppress("DEPRECATION")
private fun wifiLevel(context: Context, rssi: Int): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.getSystemService(WifiManager::class.java)?.let { wm ->
            val max = wm.maxSignalLevel.coerceAtLeast(1)
            wm.calculateSignalLevel(rssi) * 4 / max
        } ?: 4
    } else WifiManager.calculateSignalLevel(rssi, 5)
