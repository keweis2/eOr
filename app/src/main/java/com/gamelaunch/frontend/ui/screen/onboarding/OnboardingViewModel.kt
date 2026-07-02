package com.gamelaunch.frontend.ui.screen.onboarding

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamelaunch.frontend.domain.platform.PlatformDefinitions
import com.gamelaunch.frontend.domain.repository.SettingsRepository
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

enum class OnboardingStep { ROM_FOLDER, MEDIA, THEME, SETUP }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.ROM_FOLDER,
    val romPath: String = "",
    val createdRomFolder: Boolean = false,   // we made a starter ROMs folder for the user
    val mediaPath: String = "",
    val createdMediaFolder: Boolean = false,
    val ssId: String = "",
    val ssPassword: String = "",
    val darkMode: Boolean = false,
    val working: Boolean = false             // folder creation / persistence in flight
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val setupManager: FirstRunSetupManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState

    val setupState: StateFlow<FirstRunSetupState> = setupManager.state

    fun setRomPath(path: String) = _uiState.update { it.copy(romPath = path, createdRomFolder = false) }
    fun setMediaPath(path: String) = _uiState.update { it.copy(mediaPath = path, createdMediaFolder = false) }
    fun updateSsId(value: String) = _uiState.update { it.copy(ssId = value) }
    fun updateSsPassword(value: String) = _uiState.update { it.copy(ssPassword = value) }

    /** Persist the theme immediately so the whole app previews it live. */
    fun setDarkMode(dark: Boolean) {
        _uiState.update { it.copy(darkMode = dark) }
        viewModelScope.launch { settingsRepository.setDarkMode(dark) }
    }

    fun backStep() {
        _uiState.update {
            val prev = OnboardingStep.entries.getOrNull(it.step.ordinal - 1) ?: it.step
            it.copy(step = prev)
        }
    }

    /**
     * Leave the ROM step. A blank selection means the user has no library yet, so we create a
     * starter ROMs folder on internal storage with one sub-folder per supported console.
     */
    fun confirmRomStep() {
        if (_uiState.value.working) return
        _uiState.update { it.copy(working = true) }
        viewModelScope.launch {
            var path = _uiState.value.romPath
            if (path.isBlank()) {
                path = createDefaultRomFolders()
                _uiState.update { it.copy(romPath = path, createdRomFolder = true) }
            }
            settingsRepository.setRomRootPath(path)
            _uiState.update { it.copy(working = false, step = OnboardingStep.MEDIA) }
        }
    }

    /**
     * Leave the media step. Blank selection → create a media folder for the user. Credentials are
     * optional; when present they're saved so the media scan uses ScreenScraper directly.
     */
    fun confirmMediaStep() {
        if (_uiState.value.working) return
        _uiState.update { it.copy(working = true) }
        viewModelScope.launch {
            val s = _uiState.value
            var path = s.mediaPath
            if (path.isBlank()) {
                path = createDefaultMediaFolder()
                _uiState.update { it.copy(mediaPath = path, createdMediaFolder = true) }
            }
            settingsRepository.setMediaStoragePath(path)
            if (s.ssId.isNotBlank() && s.ssPassword.isNotBlank()) {
                settingsRepository.setScraperCredentials(s.ssId.trim(), s.ssPassword)
            }
            _uiState.update { it.copy(working = false, step = OnboardingStep.THEME) }
        }
    }

    /** Theme picked — move to the setup checklist and start the pipeline. */
    fun confirmThemeStep() {
        _uiState.update { it.copy(step = OnboardingStep.SETUP) }
        setupManager.start()
    }

    /**
     * Enter the app. If the media scan is still running it continues in the background and posts
     * a notification when done. `onDone` fires only after the first-launch flag is persisted, so
     * a relaunch can never land back in onboarding.
     */
    fun finishOnboarding(onDone: () -> Unit) {
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
