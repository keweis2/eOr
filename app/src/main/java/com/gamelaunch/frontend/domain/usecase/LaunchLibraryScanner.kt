package com.gamelaunch.frontend.domain.usecase

import com.gamelaunch.frontend.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On each app launch (returning users only — the very first run goes through
 * [FirstRunSetupManager]), quietly pick up games added since the last launch: newly-installed
 * Android games, new Steam-library entries, and new ROMs. The ROM folder is checked with a fast,
 * no-hash probe first ([ScanRomsUseCase.hasNewGames]), so the expensive full ROM scan only runs when
 * there's actually something new on disk — a launch with nothing new does no heavy work and the user
 * lands straight on Home.
 *
 * App-scoped (its own scope, not a viewModelScope) so a scan started at launch keeps running even if
 * the user navigates around; Home reflects additions live because its lists are Room-backed.
 */
@Singleton
class LaunchLibraryScanner @Inject constructor(
    private val scanRomsUseCase: ScanRomsUseCase,
    private val scanAndroidGamesUseCase: ScanAndroidGamesUseCase,
    private val scanSteamLibraryUseCase: ScanSteamLibraryUseCase,
    private val settingsRepository: SettingsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // True only while the (potentially slow) full ROM scan runs, so Home can show a lightweight
    // "scanning for new games" indicator. The sub-second Android/Steam refreshes don't flip it.
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    @Volatile private var started = false

    /** Kick off the launch scan once per process. Safe to call from every onCreate. */
    fun scanOnLaunch() {
        if (started) return
        started = true
        scope.launch { run() }
    }

    private suspend fun run() {
        // Never fight the first-run setup pipeline — onboarding owns the initial full scan.
        if (settingsRepository.isFirstLaunch.first()) return

        // Android + Steam: cheap and idempotent — refresh quietly to catch new installs / entries.
        runCatching { scanAndroidGamesUseCase().collect { } }
        if (settingsRepository.steamLibraryPath.first().isNotBlank()) {
            runCatching { scanSteamLibraryUseCase().collect { } }
        }

        // ROMs: only run the hashing full scan when the quick probe finds something new on disk.
        val romPath = settingsRepository.romRootPath.first()
        val romsHaveNew = romPath.isNotBlank() &&
            runCatching { scanRomsUseCase.hasNewGames(romPath) }.getOrDefault(false)
        if (romsHaveNew) {
            _isScanning.value = true
            try {
                runCatching { scanRomsUseCase(romPath).collect { } }
            } finally {
                _isScanning.value = false
            }
        }
    }
}
