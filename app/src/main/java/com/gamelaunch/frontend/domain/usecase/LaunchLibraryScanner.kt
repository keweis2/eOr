package com.gamelaunch.frontend.domain.usecase

import com.gamelaunch.frontend.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * While the app is in the foreground (returning users only — the very first run goes through
 * [FirstRunSetupManager]), quietly pick up newly-installed Android games, new Steam-library
 * entries, and new ROMs, including files uploaded while Home remains visible. The ROM folder uses
 * a periodic fast, no-hash probe first ([ScanRomsUseCase.hasNewGames]), so the expensive full ROM
 * scan only runs when there's actually something new on disk.
 *
 * Home reflects additions live because its lists are Room-backed.
 */
@Singleton
class LaunchLibraryScanner @Inject constructor(
    private val scanRomsUseCase: ScanRomsUseCase,
    private val scanAndroidGamesUseCase: ScanAndroidGamesUseCase,
    private val scanSteamLibraryUseCase: ScanSteamLibraryUseCase,
    private val settingsRepository: SettingsRepository
) {
    // True only while the (potentially slow) full ROM scan runs, so Home can show a lightweight
    // "scanning for new games" indicator. The sub-second Android/Steam refreshes don't flip it.
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    /**
     * Run one library refresh. The foreground lifecycle owner calls this sequentially, so this
     * class does not need to own a polling job or synchronize overlapping scans.
     */
    suspend fun scan() {
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
            runCatching {
                scanRomsUseCase.hasNewGames(romPath, ROM_UPLOAD_QUIET_PERIOD_MS)
            }.getOrDefault(false)
        if (romsHaveNew) {
            _isScanning.value = true
            try {
                runCatching { scanRomsUseCase(romPath).collect { } }
            } finally {
                _isScanning.value = false
            }
        }
    }

    private companion object {
        // Long enough to avoid importing a file between FTP write bursts. Because foreground probes
        // run every 30 seconds, a completed upload is normally discovered on the next or following
        // pass without repeatedly hashing a growing file.
        const val ROM_UPLOAD_QUIET_PERIOD_MS = 10_000L
    }
}
