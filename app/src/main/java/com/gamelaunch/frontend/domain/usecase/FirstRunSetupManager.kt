package com.gamelaunch.frontend.domain.usecase

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.gamelaunch.frontend.R
import com.gamelaunch.frontend.domain.repository.EmulatorRepository
import com.gamelaunch.frontend.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** One item on the first-run setup checklist. */
sealed class SetupStep {
    object Pending : SetupStep()
    data class Running(val done: Int = 0, val total: Int = 0, val detail: String = "") : SetupStep()
    data class Done(val summary: String) : SetupStep()
    data class Failed(val message: String) : SetupStep()

    val isSettled: Boolean get() = this is Done || this is Failed
}

data class FirstRunSetupState(
    val started: Boolean = false,
    val romScan: SetupStep = SetupStep.Pending,
    val emulatorDetect: SetupStep = SetupStep.Pending,
    val androidScan: SetupStep = SetupStep.Pending,
    val mediaScan: SetupStep = SetupStep.Pending
) {
    /** The scans the user must wait for before entering the app (everything but media). */
    val requiredStepsSettled: Boolean
        get() = romScan.isSettled && emulatorDetect.isSettled && androidScan.isSettled
}

/**
 * Runs the first-launch setup pipeline: ROM scan → emulator detection → Android games scan →
 * media import/download. Lives in its own application-scoped coroutine (not a viewModelScope) so
 * the media download keeps going when the user leaves onboarding early; if they do, we post a
 * notification when it finishes.
 */
@Singleton
class FirstRunSetupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scanRomsUseCase: ScanRomsUseCase,
    private val scanAndroidGamesUseCase: ScanAndroidGamesUseCase,
    private val batchScrapeUseCase: BatchScrapeUseCase,
    private val importEsdeMediaUseCase: ImportEsdeMediaUseCase,
    private val emulatorRepository: EmulatorRepository,
    private val settingsRepository: SettingsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(FirstRunSetupState())
    val state: StateFlow<FirstRunSetupState> = _state

    @Volatile private var notifyWhenMediaDone = false

    /** Kick off the pipeline. Safe to call more than once — only the first call starts it. */
    fun start() {
        if (_state.value.started) return
        _state.update { it.copy(started = true) }
        scope.launch { runPipeline() }
    }

    /**
     * The user entered the app while the media scan was still running: finish it in the
     * background and post a notification when it completes.
     */
    fun notifyWhenMediaScanFinishes() {
        if (_state.value.mediaScan.isSettled) return
        notifyWhenMediaDone = true
    }

    private suspend fun runPipeline() {
        // 1 · ROM library scan
        runCatching {
            val romPath = settingsRepository.romRootPath.first()
            var added = 0
            scanRomsUseCase(romPath).collect { p ->
                added = p.added
                _state.update { it.copy(romScan = SetupStep.Running(p.scanned, p.total, p.currentFile)) }
            }
            _state.update { it.copy(romScan = SetupStep.Done("Found $added game${if (added == 1) "" else "s"}")) }
        }.onFailure { e ->
            _state.update { it.copy(romScan = SetupStep.Failed(e.message ?: "ROM scan failed")) }
        }

        // 2 · Emulator detection for the scanned systems
        runCatching {
            _state.update { it.copy(emulatorDetect = SetupStep.Running(detail = "Looking for installed emulators…")) }
            val configured = emulatorRepository.autoDetectAndAssign()
            val found = emulatorRepository.getInstalledEmulators().count { it.isInstalled }
            _state.update {
                it.copy(emulatorDetect = SetupStep.Done(
                    "Found $found emulator${if (found == 1) "" else "s"} · configured $configured system${if (configured == 1) "" else "s"}"
                ))
            }
        }.onFailure { e ->
            _state.update { it.copy(emulatorDetect = SetupStep.Failed(e.message ?: "Emulator detection failed")) }
        }

        // 3 · Installed Android games
        runCatching {
            var added = 0
            scanAndroidGamesUseCase().collect { p ->
                added = p.added
                _state.update { it.copy(androidScan = SetupStep.Running(p.scanned, p.total, p.currentFile)) }
            }
            _state.update { it.copy(androidScan = SetupStep.Done("Found $added Android game${if (added == 1) "" else "s"}")) }
        }.onFailure { e ->
            _state.update { it.copy(androidScan = SetupStep.Failed(e.message ?: "Android game scan failed")) }
        }

        // 4 · Media: import anything already in the media folder, then scrape the rest
        runCatching {
            var imported = 0
            val mediaPath = settingsRepository.mediaStoragePath.first()
            if (mediaPath.isNotBlank()) {
                _state.update { it.copy(mediaScan = SetupStep.Running(detail = "Checking folder for existing media…")) }
                importEsdeMediaUseCase(mediaPath).collect { status ->
                    if (status is EsdeImportStatus.Complete) imported = status.matched
                }
            }

            val config = settingsRepository.scraperConfig.first()
            var last: BatchScrapeState? = null
            batchScrapeUseCase(config).collect { s ->
                last = s
                _state.update { it.copy(mediaScan = SetupStep.Running(s.completed, s.total, s.currentGameTitle)) }
            }
            val summary = buildString {
                append("Downloaded media for ${last?.succeeded ?: 0} game${if (last?.succeeded == 1) "" else "s"}")
                if (imported > 0) append(" · imported $imported")
            }
            _state.update { it.copy(mediaScan = SetupStep.Done(summary)) }
            if (notifyWhenMediaDone) postMediaDoneNotification(summary)
        }.onFailure { e ->
            _state.update { it.copy(mediaScan = SetupStep.Failed(e.message ?: "Media download failed")) }
            if (notifyWhenMediaDone) postMediaDoneNotification("Media download stopped: ${e.message ?: "error"}")
        }
    }

    private fun postMediaDoneNotification(summary: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val nm = NotificationManagerCompat.from(context)
        nm.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("Setup")
                .setDescription("First-time setup progress")
                .build()
        )
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val contentIntent = launchIntent?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }
        runCatching {
            nm.notify(
                NOTIFICATION_ID,
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_donkey_silhouette)
                    .setContentTitle("Your library is ready")
                    .setContentText(summary)
                    .setContentIntent(contentIntent)
                    .setAutoCancel(true)
                    .build()
            )
        }
    }

    private companion object {
        const val CHANNEL_ID = "first_run_setup"
        const val NOTIFICATION_ID = 2001
    }
}
