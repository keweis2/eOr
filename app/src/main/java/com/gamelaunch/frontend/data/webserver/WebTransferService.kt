package com.gamelaunch.frontend.data.webserver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.gamelaunch.frontend.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Foreground service that hosts the Web Transfer LAN server. Keeps the server alive while eOr is
 * backgrounded (so a big transfer doesn't die when the screen turns off) behind a persistent
 * notification that shows the connect URL. Mirrors `SyncthingService`.
 */
@AndroidEntryPoint
class WebTransferService : Service() {

    @Inject lateinit var manager: WebTransferManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        runCatching { manager.onServiceStart() }
            .onFailure { Log.e(TAG, "Failed to start Web Transfer server", it) }
        startForegroundCompat()
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { manager.onServiceStop() }
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Web Transfer", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val url = manager.state.value.url.ifBlank { "Starting…" }
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_donkey_silhouette)
            .setContentTitle("Web Transfer is on")
            .setContentText("Open $url in your computer's browser")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Open $url in your computer's browser to send games and files."))
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    companion object {
        private const val TAG = "WebTransferService"
        private const val CHANNEL_ID = "web_transfer"
        private const val NOTIF_ID = 2002

        fun start(context: Context) {
            context.startForegroundService(Intent(context, WebTransferService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WebTransferService::class.java))
        }
    }
}
