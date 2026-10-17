package com.gamelaunch.frontend.data.sync

import androidx.core.content.ContextCompat
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
import kotlin.concurrent.thread

/**
 * Foreground service that runs the embedded Syncthing daemon for Save Sync. Keeps the daemon alive
 * while eOr is backgrounded (required for syncing) behind a persistent notification.
 */
@AndroidEntryPoint
class SyncthingService : Service() {

    @Inject lateinit var controller: SyncthingController

    @Volatile private var process: Process? = null
    private var logThread: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        if (process == null && controller.isSupported()) {
            runCatching {
                val proc = controller.buildProcess()
                process = proc
                // Drain the daemon's merged stdout/stderr into logcat so failures are diagnosable.
                logThread = thread(name = "syncthing-log", isDaemon = true) {
                    runCatching {
                        proc.inputStream.bufferedReader().forEachLine { Log.d(TAG, it) }
                    }
                }
            }.onFailure { Log.e(TAG, "Failed to start Syncthing", it) }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { process?.destroy() }
        process = null
        logThread?.interrupt()
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Save Sync", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_donkey_silhouette)
            .setContentTitle("Save Sync")
            .setContentText("Syncing your emulator saves")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    companion object {
        private const val TAG = "SyncthingService"
        private const val CHANNEL_ID = "save_sync"
        private const val NOTIF_ID = 2001

        fun start(context: Context) {
            val intent = Intent(context, SyncthingService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SyncthingService::class.java))
        }
    }
}
