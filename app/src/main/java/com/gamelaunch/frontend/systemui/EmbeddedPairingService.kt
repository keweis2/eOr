package com.gamelaunch.frontend.systemui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.gamelaunch.frontend.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Keeps wireless-debugging pairing available through a notification while Settings is open. */
@AndroidEntryPoint
class EmbeddedPairingService : Service() {
    @Inject
    lateinit var controller: SystemNavigationLockController
    private var retainResultNotification = false

    override fun onCreate() {
        super.onCreate()
        Log.i(EOR_BROKER_TAG, "pairing-helper/started")
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                "eOr pairing",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Return to eOr before the Wireless debugging code expires" })
        val pi = PendingIntent.getActivity(
            this,
            0,
            Intent(
                this,
                MainActivity::class.java
            ).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val replyIntent = Intent(this, EmbeddedPairingService::class.java).setAction(ACTION_PAIR)
        val replyPendingIntent = PendingIntent.getService(
            this,
            1,
            replyIntent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val reply = RemoteInput.Builder(KEY_CODE)
            .setLabel("6 digits; mistake: space, then retry")
            .build()
        val replyAction =
            NotificationCompat.Action.Builder(0, "Enter pairing code", replyPendingIntent)
                .addRemoteInput(reply).build()
        controller.markPairingNotificationSetup()
        startForeground(
            PAIRING_NOTIFICATION_ID, NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(com.gamelaunch.frontend.R.drawable.ic_notification)
                .setContentTitle("Pair eOr with Wireless debugging")
                .setContentText("Keep Settings open; enter the six-digit code here")
                .setContentIntent(pi).setOngoing(true).setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .addAction(replyAction).build()
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PAIR) {
            val code = pairingCodeFrom(
                RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_CODE)
            )
            if (code != null) {
                Log.i(EOR_BROKER_TAG, "pairing-helper/code-received")
                controller.markPairingNotificationPairing()
                showPairingProgress()
                controller.pair(code, EorAdbEndpointCache.pairing?.port, ::showPairingResult)
            } else Log.i(EOR_BROKER_TAG, "pairing-helper/code-invalid")
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Log.i(
            EOR_BROKER_TAG,
            "pairing-helper/stopped"
        )
        if (!retainResultNotification) stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun showPairingProgress() {
        val progressNotification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(com.gamelaunch.frontend.R.drawable.ic_notification)
            .setContentTitle("Pairing eOr…")
            .setContentText("Keep Wireless debugging open")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(0, 0, true)
            .build()

        getSystemService(NotificationManager::class.java)
            .notify(PAIRING_NOTIFICATION_ID, progressNotification)
    }

    private fun showPairingResult(status: SystemNavigationLockStatus) {
        val paired = status in setOf(
            SystemNavigationLockStatus.READY,
            SystemNavigationLockStatus.ACTIVE,
        )
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(com.gamelaunch.frontend.R.drawable.ic_notification)
            .setContentTitle(if (paired) "Pairing complete" else "Pairing failed")
            .setContentText(if (paired) "Tap to return to eOr" else "Tap to return and try again")
            .setContentIntent(returnToEorIntent())
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
        if (paired) notificationBuilder.setTimeoutAfter(PAIRING_SUCCESS_TIMEOUT_MS)

        retainResultNotification = true
        controller.markPairingNotificationResult(resetAfterTimeout = paired)
        getSystemService(NotificationManager::class.java)
            .notify(PAIRING_NOTIFICATION_ID, notificationBuilder.build())
        stopForeground(STOP_FOREGROUND_DETACH)
    }

    private fun returnToEorIntent() = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL = "eor_pairing";
        const val PAIRING_NOTIFICATION_ID = 7103
        // limit how long the notification stays visible after successful pairing
        const val PAIRING_SUCCESS_TIMEOUT_MS = 30_000L
        const val ACTION_PAIR = "com.gamelaunch.frontend.action.PAIR_EMBEDDED_ADB";
        const val KEY_CODE = "eor_pairing_code"
    }
}

internal fun pairingCodeFrom(input: CharSequence?): String? = input?.toString()
    ?.let { text -> Regex("(?:^|[^0-9])([0-9]{6})(?=[^0-9]|$)").findAll(text).lastOrNull() }
    ?.groupValues?.get(1)
