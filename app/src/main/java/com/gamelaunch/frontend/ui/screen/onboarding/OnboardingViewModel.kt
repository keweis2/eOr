package com.gamelaunch.frontend.ui.screen.onboarding

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamelaunch.frontend.data.audio.SoundPlayer
import com.gamelaunch.frontend.domain.platform.PlatformDefinitions
import com.gamelaunch.frontend.domain.repository.SettingsRepository
import com.gamelaunch.frontend.domain.usecase.DetectRomFolderUseCase
import com.gamelaunch.frontend.domain.usecase.FirstRunSetupManager
import com.gamelaunch.frontend.domain.usecase.FirstRunSetupState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** Otto-guided first-run steps: welcome → find games → theme → build library. */
enum class OnboardingStep { WELCOME, GAMES, THEME, SETUP }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val detecting: Boolean = false,          // auto-detecting the ROM folder
    val detectedGameCount: Int = 0,          // games found by auto-detect
    val romPath: String = "",
    val createdRomFolder: Boolean = false,
    val advancedOpen: Boolean = false,       // reveal media folder + ScreenScraper (hidden by default)
    val mediaPath: String = "",
    val ssId: String = "",
    val ssPassword: String = "",
    val darkMode: Boolean = false,
    val working: Boolean = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val setupManager: FirstRunSetupManager,
    private val detectRomFolderUseCase: DetectRomFolderUseCase,
    private val soundPlayer: SoundPlayer
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState

    val setupState: StateFlow<FirstRunSetupState> = setupManager.state

    /** Leave the welcome screen and kick off auto-detection of the games folder. */
    fun startFromWelcome() {
        soundPlayer.step()
        _uiState.update { it.copy(step = OnboardingStep.GAMES) }
        detectRomFolder()
    }

    /** Look for the user's games in the usual places so they can just tap "Yes, that's them!". */
    fun detectRomFolder() {
        if (_uiState.value.romPath.isNotBlank() || _uiState.value.detecting) return
        _uiState.update { it.copy(detecting = true) }
        viewModelScope.launch {
            val result = detectRomFolderUseCase()
            _uiState.update {
                if (result != null && it.romPath.isBlank())
                    it.copy(detecting = false, romPath = result.path, detectedGameCount = result.gameCount)
                else it.copy(detecting = false)
            }
            if (result != null) soundPlayer.found()
        }
    }

    fun setRomPath(path: String) =
        _uiState.update { it.copy(romPath = path, createdRomFolder = false, detectedGameCount = 0) }

    fun toggleAdvanced() = _uiState.update { it.copy(advancedOpen = !it.advancedOpen) }
    fun setMediaPath(path: String) = _uiState.update { it.copy(mediaPath = path) }
    fun updateSsId(value: String) = _uiState.update { it.copy(ssId = value) }
    fun updateSsPassword(value: String) = _uiState.update { it.copy(ssPassword = value) }

    /** Persist the theme immediately so the whole app previews it live. */
    fun setDarkMode(dark: Boolean) {
        _uiState.update { it.copy(darkMode = dark) }
        viewModelScope.launch { settingsRepository.setDarkMode(dark) }
    }

    fun backStep() {
        soundPlayer.step()
        _uiState.update {
            val prev = when (it.step) {
                OnboardingStep.GAMES -> OnboardingStep.WELCOME
                OnboardingStep.THEME -> OnboardingStep.GAMES
                else -> it.step
            }
            it.copy(step = prev)
        }
    }

    /**
     * Leave the games step. Blank ROM path → create a starter ROMs tree. Media folder + ScreenScraper
     * come from the (optional) Advanced section, else an auto media folder + fallback art sources.
     */
    fun confirmGamesStep() {
        if (_uiState.value.working) return
        _uiState.update { it.copy(working = true) }
        soundPlayer.step()
        viewModelScope.launch {
            val s = _uiState.value
            var romPath = s.romPath
            if (romPath.isBlank()) {
                romPath = createDefaultRomFolders()
                _uiState.update { it.copy(romPath = romPath, createdRomFolder = true) }
            }
            settingsRepository.setRomRootPath(romPath)

            val mediaPath = s.mediaPath.ifBlank { createDefaultMediaFolder() }
            settingsRepository.setMediaStoragePath(mediaPath)

            if (s.ssId.isNotBlank() && s.ssPassword.isNotBlank()) {
                settingsRepository.setScraperCredentials(s.ssId.trim(), s.ssPassword)
            }
            _uiState.update { it.copy(working = false, step = OnboardingStep.THEME) }
        }
    }

    /** Theme picked — move to the build step and start the pipeline. */
    fun confirmThemeStep() {
        soundPlayer.step()
        _uiState.update { it.copy(step = OnboardingStep.SETUP) }
        setupManager.start()
    }

    /** Re-run any failed setup steps. */
    fun retryFailedSetup() = setupManager.retry()

    /**
     * Enter the app. If media is still downloading it continues in the background and posts a
     * notification when done. `onDone` fires only after the first-launch flag is persisted.
     */
    fun finishOnboarding(onDone: () -> Unit) {
        soundPlayer.celebrate()
        setupManager.notifyWhenMediaScanFinishes()
        viewModelScope.launch {
            settingsRepository.setFirstLaunchComplete()
            onDone()
        }
    }

    private suspend fun createDefaultRomFolders(): String = withContext(Dispatchers.IO) {
        val root = File(Environment.getExternalStorageDirectory(), "ROMs")
        PlatformDefinitions.ALL.forEach { platform ->
            runCatching { File(root, platform.folderNames.first()).mkdirs() }
        }
        root.absolutePath
    }

    private suspend fun createDefaultMediaFolder(): String = withContext(Dispatchers.IO) {
        val folder = File(Environment.getExternalStorageDirectory(), "eOr/media")
        runCatching { folder.mkdirs() }
        folder.absolutePath
    }
}
