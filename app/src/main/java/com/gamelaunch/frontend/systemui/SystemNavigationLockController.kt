package com.gamelaunch.frontend.systemui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.provider.Settings.Global.BOOT_COUNT
import com.gamelaunch.frontend.domain.lockedmode.LockedModeRepository
import com.gamelaunch.frontend.domain.lockedmode.LockedModeState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

enum class SystemNavigationLockStatus {
    DISABLED,
    UNSUPPORTED,
    DEVELOPER_OPTIONS_REQUIRED,
    WIRELESS_DEBUGGING_REQUIRED,
    PAIRING_REQUIRED,
    DISCOVERING,
    START_REQUIRED,
    STARTING,
    READY,
    APPLYING,
    ACTIVE,
    RESTORE_REQUIRED,
    ERROR,
}

internal enum class PairingNotificationPhase {
    IDLE,
    SETUP,
    PAIRING,
    RESULT,
}

data class SystemNavigationSetupProgress(
    val developerOptionsEnabled: Boolean = false,
    val wirelessDebuggingEnabled: Boolean = false,
    val paired: Boolean = false,
)

@Singleton
class SystemNavigationLockController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val lockedModeRepository: LockedModeRepository,
    private val brokerManager: EmbeddedPrivilegeBrokerManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private val prefs = context.getSharedPreferences("system_navigation_lock", Context.MODE_PRIVATE)
    private val _status = MutableStateFlow(SystemNavigationLockStatus.DISABLED)
    val status: StateFlow<SystemNavigationLockStatus> = _status.asStateFlow()
    private val _pairingPort = MutableStateFlow<Int?>(null)
    val pairingPort: StateFlow<Int?> = _pairingPort.asStateFlow()
    private val _setupProgress = MutableStateFlow(readSetupProgress())
    val setupProgress: StateFlow<SystemNavigationSetupProgress> = _setupProgress.asStateFlow()
    private var pairingDiscoveryJob: Job? = null
    private var reconciliationJob: Job? = null
    private var navigationRecoveryJob: Job? = null
    private var pairingResultResetJob: Job? = null
    private var pairingNotificationPhase = PairingNotificationPhase.IDLE
    private var pairingNotificationGeneration = 0L
    private var lastPairingVerificationAt = Long.MIN_VALUE
    private var optionEnabled = false
    private var lockedModeState = LockedModeState.DISABLED

    init {
        scope.launch { combine(lockedModeRepository.state, lockedModeRepository.blockSystemNavigation) { a,b -> a to b }
            .collect { (state, enabled) ->
                lockedModeState = state
                optionEnabled = enabled
                if (!enabled) dismissPairingNotification()
                reconcileNow()
            } }
        scope.launch { status.collect {
            if (shouldDismissPairingNotification(it, pairingNotificationPhase)) {
                dismissPairingNotification()
            } else {
                ensurePairingNotification(it)
            }
        } }
        scope.launch { recoverBrokerOnStartup() }
    }
    fun reconcile() {
        refreshSetupProgress()
        if (reconciliationJob?.isActive == true) return
        reconciliationJob = scope.launch {
            verifyStoredPairingIfDue()
            reconcileNow()
            ensurePairingNotification(_status.value)
        }
    }
    suspend fun reconcileFromRepository() { lockedModeState=lockedModeRepository.state.first(); optionEnabled=lockedModeRepository.blockSystemNavigation.first(); reconcileNow() }

    private suspend fun verifyStoredPairingIfDue() {
        val progress = _setupProgress.value
        if (!optionEnabled || lockedModeState == LockedModeState.LOCKED || !progress.paired ||
            !progress.developerOptionsEnabled || !progress.wirelessDebuggingEnabled ||
            brokerManager.currentBroker() == null
        ) return
        val now = SystemClock.elapsedRealtime()
        if (lastPairingVerificationAt != Long.MIN_VALUE && now - lastPairingVerificationAt < 5_000L) return
        lastPairingVerificationAt = now
        if (brokerManager.verifyPairing() == false) {
            brokerManager.invalidateCurrentBroker()
            refreshSetupProgress()
        }
    }
    fun openDevelopmentSettings(): Boolean = runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    }.getOrDefault(false)
    fun openDeviceInfoSettings(): Boolean {
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK
        return runCatching {
            context.startActivity(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS).addFlags(flags))
            true
        }.getOrElse {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(flags))
                true
            }.getOrDefault(false)
        }
    }
    fun preparePairingNotification() {
        ContextCompat.startForegroundService(context, Intent(context, EmbeddedPairingService::class.java))
    }
    fun dismissPairingNotification() {
        pairingResultResetJob?.cancel()
        pairingResultResetJob = null
        pairingNotificationPhase = PairingNotificationPhase.IDLE
        pairingNotificationGeneration++
        context.getSystemService(android.app.NotificationManager::class.java)
            .cancel(EmbeddedPairingService.PAIRING_NOTIFICATION_ID)
        context.stopService(Intent(context, EmbeddedPairingService::class.java))
    }

    internal fun markPairingNotificationSetup() {
        pairingResultResetJob?.cancel()
        pairingResultResetJob = null
        pairingNotificationGeneration++
        pairingNotificationPhase = PairingNotificationPhase.SETUP
    }

    internal fun markPairingNotificationPairing() {
        pairingResultResetJob?.cancel()
        pairingResultResetJob = null
        pairingNotificationGeneration++
        pairingNotificationPhase = PairingNotificationPhase.PAIRING
    }

    internal fun markPairingNotificationResult(resetAfterTimeout: Boolean) {
        pairingResultResetJob?.cancel()
        pairingResultResetJob = null
        val generation = ++pairingNotificationGeneration
        pairingNotificationPhase = PairingNotificationPhase.RESULT
        if (resetAfterTimeout) {
            pairingResultResetJob = scope.launch {
                delay(EmbeddedPairingService.PAIRING_SUCCESS_TIMEOUT_MS)
                if (shouldResetPairingNotificationLifecycle(
                        pairingNotificationPhase,
                        pairingNotificationGeneration,
                        generation,
                    )
                ) {
                    pairingNotificationPhase = PairingNotificationPhase.IDLE
                }
                pairingResultResetJob = null
            }
        }
    }
    private fun ensurePairingNotification(status: SystemNavigationLockStatus) {
        if (!optionEnabled || _setupProgress.value.paired || status !in setOf(
                SystemNavigationLockStatus.WIRELESS_DEBUGGING_REQUIRED,
                SystemNavigationLockStatus.PAIRING_REQUIRED,
            )
        ) return
        val notificationsAllowed = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (notificationsAllowed) preparePairingNotification()
    }
    fun beginPairingSetup() {
        refreshSetupProgress()
        ContextCompat.startForegroundService(context, Intent(context, EmbeddedPairingService::class.java))
        pairingDiscoveryJob?.cancel()
        _pairingPort.value=null
        pairingDiscoveryJob=scope.launch {
            repeat(8) {
                val discovered=runCatching { discoverLocalAdb(context,"_adb-tls-pairing._tcp",15_000) }.getOrNull()
                if(discovered != null) {
                    EorAdbEndpointCache.pairing=discovered
                    _pairingPort.value=discovered.port
                    android.util.Log.i(EOR_BROKER_TAG,"pairing/background-discovery-ready")
                    return@launch
                }
                delay(500)
            }
            android.util.Log.i(EOR_BROKER_TAG,"pairing/background-discovery-unavailable")
        }
        openDevelopmentSettings()
    }
    fun startBrokerSetup() { scope.launch {
        refreshSetupProgress()
        brokerSetupRequirement(_setupProgress.value)?.let {
            _status.value = it
            return@launch
        }
        _status.value = SystemNavigationLockStatus.DISCOVERING
        val result = brokerManager.connectOrStart()
        if (result == SystemNavigationLockStatus.READY) reconcileNow()
        else {
            refreshSetupProgress()
            val status = setupStatus(result, _setupProgress.value)
            if (status == SystemNavigationLockStatus.STARTING) scheduleNavigationRecovery()
            else _status.value = status
        }
    } }
    fun pair(code:String,port:Int?,onComplete:((SystemNavigationLockStatus)->Unit)?=null) { scope.launch {
        pairingDiscoveryJob?.cancel()
        refreshSetupProgress()
        brokerSetupRequirement(_setupProgress.value)?.let {
            _status.value = it
            onComplete?.invoke(it)
            context.stopService(Intent(context,EmbeddedPairingService::class.java))
            return@launch
        }
        ContextCompat.startForegroundService(context,Intent(context,EmbeddedPairingService::class.java))
        _status.value=SystemNavigationLockStatus.DISCOVERING
        val result=brokerManager.pair(code,port ?: _pairingPort.value)
        if (result == SystemNavigationLockStatus.READY) reconcileNow() else _status.value=result
        onComplete?.invoke(_status.value)
        context.stopService(Intent(context,EmbeddedPairingService::class.java))
    } }

    private suspend fun reconcileNow() = mutex.withLock {
        refreshSetupProgress()
        val desired = shouldLockSystemNavigation(optionEnabled, lockedModeState)
        val bootCount = currentBootCount()
        val applied = prefs.getBoolean("applied", false) &&
            prefs.getInt("applied_boot_count", -1) == bootCount
        if (!optionEnabled && !applied) { _status.value=SystemNavigationLockStatus.DISABLED; return }
        if (Build.VERSION.SDK_INT < 30) { _status.value=SystemNavigationLockStatus.UNSUPPORTED; return }
        var broker = brokerManager.currentBroker()
        if (broker == null) {
            brokerSetupRequirement(_setupProgress.value)?.let {
                _status.value = if (!desired && applied) SystemNavigationLockStatus.RESTORE_REQUIRED else it
                return
            }
            val readiness = brokerManager.readiness()
            if (readiness != SystemNavigationLockStatus.START_REQUIRED) {
                _status.value = if (!desired && applied) SystemNavigationLockStatus.RESTORE_REQUIRED else readiness
                return
            }
            _status.value = SystemNavigationLockStatus.DISCOVERING
            val result = brokerManager.connectOrStart()
            if (result != SystemNavigationLockStatus.READY) {
                refreshSetupProgress()
                val status = if (!desired && applied) SystemNavigationLockStatus.RESTORE_REQUIRED
                    else setupStatus(result, _setupProgress.value)
                if (status == SystemNavigationLockStatus.STARTING) scheduleNavigationRecovery()
                else _status.value = status
                return
            }
            broker = brokerManager.currentBroker()
            if (broker == null) { _status.value = SystemNavigationLockStatus.ERROR; return }
        }
        // A new boot clears shell-owned SystemUI restrictions. If eOr has not applied anything in
        // this boot and Locked Mode is inactive, broker availability alone means setup is ready.
        if (!desired && !applied) {
            _status.value = if (optionEnabled) SystemNavigationLockStatus.READY
                else SystemNavigationLockStatus.DISABLED
            return
        }
        _status.value=SystemNavigationLockStatus.APPLYING
        val ok=runCatching { withTimeout(8_000) {
            repeat(5) { attempt ->
                val appliedNow = runCatching {
                    broker.setNavigationLocked(desired) == 0 &&
                        broker.getNavigationLockState() == if (desired) 1 else 0
                }.getOrDefault(false)
                if (appliedNow) return@withTimeout true
                if (attempt < 4) delay(250L * (1L shl attempt))
            }
            false
        } }.getOrDefault(false)
        if(ok) { prefs.edit().putBoolean("applied",desired).putInt("applied_boot_count",bootCount).apply(); _status.value=if(desired) SystemNavigationLockStatus.ACTIVE else if(optionEnabled) SystemNavigationLockStatus.READY else SystemNavigationLockStatus.DISABLED }
        else if (!desired && applied) _status.value=SystemNavigationLockStatus.RESTORE_REQUIRED
        else scheduleNavigationRecovery()
    }

    private fun scheduleNavigationRecovery() {
        refreshSetupProgress()
        val recoveryStatus = navigationRecoveryStatus(_setupProgress.value)
        if (recoveryStatus != SystemNavigationLockStatus.STARTING) {
            _status.value = recoveryStatus
            navigationRecoveryJob?.cancel()
            navigationRecoveryJob = null
            return
        }
        _status.value = SystemNavigationLockStatus.STARTING
        if (navigationRecoveryJob?.isActive == true) return
        navigationRecoveryJob = scope.launch {
            try {
                for (retryDelay in listOf(2_000L, 4_000L, 8_000L, 15_000L, 30_000L)) {
                    delay(retryDelay)
                    if (_status.value != SystemNavigationLockStatus.STARTING) return@launch
                    reconcileNow()
                    if (_status.value != SystemNavigationLockStatus.STARTING) return@launch
                }
                _status.value = SystemNavigationLockStatus.ERROR
            } finally {
                navigationRecoveryJob = null
            }
        }
    }

    private suspend fun recoverBrokerOnStartup() {
        if (!EncryptedAdbIdentity(context).isPaired()) return
        for (retryDelay in listOf(0L, 1_000L, 2_000L, 4_000L, 8_000L, 15_000L, 30_000L)) {
            delay(retryDelay)
            refreshSetupProgress()
            if (brokerSetupRequirement(_setupProgress.value) != null) continue
            android.util.Log.i(EOR_BROKER_TAG, "broker/startup-recovery")
            // The normal reconciliation path owns broker startup and its retry lifecycle.
            reconcileNow()
            return
        }
    }

    private fun currentBootCount(): Int = runCatching {
        Settings.Global.getInt(context.contentResolver, BOOT_COUNT)
    }.getOrDefault(-1)

    fun refreshSetupProgress() {
        _setupProgress.value = readSetupProgress()
    }

    private fun readSetupProgress() = SystemNavigationSetupProgress(
        developerOptionsEnabled = runCatching {
            Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                0,
            ) == 1
        }.getOrDefault(false),
        // Android has exposed this global setting since Wireless debugging was introduced,
        // but not as a public SDK constant. Treat an unavailable OEM setting as disabled.
        wirelessDebuggingEnabled = Build.VERSION.SDK_INT >= 30 && runCatching {
            Settings.Global.getInt(context.contentResolver, "adb_wifi_enabled", 0) == 1
        }.getOrDefault(false),
        paired = EncryptedAdbIdentity(context).isPaired(),
    )
}

internal fun shouldLockSystemNavigation(optionEnabled:Boolean, lockedModeState:LockedModeState)=optionEnabled && lockedModeState==LockedModeState.LOCKED

internal fun shouldDismissPairingNotification(
    status: SystemNavigationLockStatus,
    phase: PairingNotificationPhase,
): Boolean = status in setOf(
    SystemNavigationLockStatus.READY,
    SystemNavigationLockStatus.ACTIVE,
) && phase in setOf(
    PairingNotificationPhase.IDLE,
    PairingNotificationPhase.SETUP,
)

internal fun shouldResetPairingNotificationLifecycle(
    phase: PairingNotificationPhase,
    currentGeneration: Long,
    scheduledGeneration: Long,
): Boolean = phase == PairingNotificationPhase.RESULT &&
    currentGeneration == scheduledGeneration

internal fun navigationRecoveryStatus(
    progress: SystemNavigationSetupProgress,
): SystemNavigationLockStatus = when {
    !progress.developerOptionsEnabled -> SystemNavigationLockStatus.DEVELOPER_OPTIONS_REQUIRED
    !progress.wirelessDebuggingEnabled -> SystemNavigationLockStatus.WIRELESS_DEBUGGING_REQUIRED
    !progress.paired -> SystemNavigationLockStatus.PAIRING_REQUIRED
    else -> SystemNavigationLockStatus.STARTING
}

internal fun brokerSetupRequirement(
    progress: SystemNavigationSetupProgress,
): SystemNavigationLockStatus? = when {
    !progress.developerOptionsEnabled -> SystemNavigationLockStatus.DEVELOPER_OPTIONS_REQUIRED
    !progress.wirelessDebuggingEnabled -> SystemNavigationLockStatus.WIRELESS_DEBUGGING_REQUIRED
    else -> null
}

internal fun setupStatus(
    status: SystemNavigationLockStatus,
    progress: SystemNavigationSetupProgress,
): SystemNavigationLockStatus {
    if (status != SystemNavigationLockStatus.WIRELESS_DEBUGGING_REQUIRED) {
        return status
    }

    return when {
        !progress.developerOptionsEnabled -> SystemNavigationLockStatus.DEVELOPER_OPTIONS_REQUIRED
        !progress.wirelessDebuggingEnabled -> SystemNavigationLockStatus.WIRELESS_DEBUGGING_REQUIRED
        !progress.paired -> SystemNavigationLockStatus.PAIRING_REQUIRED
        else -> SystemNavigationLockStatus.STARTING
    }
}
