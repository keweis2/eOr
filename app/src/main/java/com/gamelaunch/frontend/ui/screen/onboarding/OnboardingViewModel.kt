package com.gamelaunch.frontend.ui.screen.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamelaunch.frontend.data.audio.SoundPlayer
import com.gamelaunch.frontend.domain.model.PackApp
import com.gamelaunch.frontend.domain.platform.PlatformDefinitions
import com.gamelaunch.frontend.domain.repository.EmulatorRepository
import com.gamelaunch.frontend.domain.repository.ObtainiumPackRepository
import com.gamelaunch.frontend.domain.repository.SettingsRepository
import com.gamelaunch.frontend.domain.usecase.DetectEsdeUseCase
import com.gamelaunch.frontend.domain.usecase.DetectRomFolderUseCase
import com.gamelaunch.frontend.domain.usecase.FirstRunSetupManager
import com.gamelaunch.frontend.domain.usecase.FirstRunSetupState
import com.gamelaunch.frontend.domain.usecase.ImportSettingsUseCase
import com.gamelaunch.frontend.launcher.ObtainiumLauncher
import com.gamelaunch.frontend.util.StorageUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Otto-guided first-run steps: welcome → storage access (only if missing) → find games → artwork →
 * theme → build library.
 */
enum class OnboardingStep { WELCOME, ACCESS, GAMES, ARTWORK, THEME, SETUP }

/** What to do about box art & videos. */
enum class ArtworkChoice {
    /** Use art already on the device (an ES-DE library or another folder). */
    EXISTING,
    /** Download art from the internet into a folder eOr manages. */
    DOWNLOAD,
    /** Nothing for now — the user can scrape later from Settings. */
    SKIP
}

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    /** Whether eOr can read shared storage. Without it detection finds nothing. */
    val hasStorageAccess: Boolean = false,
    val detecting: Boolean = false,
    /** Every folder that looks like a games library, best first. */
    val romCandidates: List<DetectRomFolderUseCase.Result> = emptyList(),
    /** The chosen games folder (detected or picked). Blank = none yet. */
    val romPath: String = "",
    /** True when the user asked us to create a fresh games folder (nothing is created until then). */
    val createRomFolder: Boolean = false,
    /** Showing the "pick a different folder" list. */
    val choosingRomFolder: Boolean = false,
    /** An ES-DE install found on the device, if any. */
    val esde: DetectEsdeUseCase.EsdeInstall? = null,
    val artworkChoice: ArtworkChoice = ArtworkChoice.DOWNLOAD,
    /** EXISTING: the folder with the art. DOWNLOAD: where to save it (blank = eOr/media). */
    val existingArtPath: String = "",
    val downloadPath: String = "",
    /** EXISTING only: also download art for games the folder has none for. */
    val alsoDownloadMissing: Boolean = false,
    val ssOpen: Boolean = false,
    val ssId: String = "",
    val ssPassword: String = "",
    val darkMode: Boolean = false,
    val themeId: String = "",
    val working: Boolean = false,
    /** Set after restoring a backup — tells the user what happened. */
    val restoreMessage: String? = null,
    // Recommended standalone emulators the user is missing — offered for install via Obtainium.
    val missingEssentialCount: Int = 0
) {
    val selectedCandidate: DetectRomFolderUseCase.Result?
        get() = romCandidates.firstOrNull { it.path == romPath }

    /** Whether online downloading will happen with the current artwork choice. */
    val downloadArtwork: Boolean
        get() = when (artworkChoice) {
            ArtworkChoice.DOWNLOAD -> true
            ArtworkChoice.EXISTING -> alsoDownloadMissing
            ArtworkChoice.SKIP -> false
        }
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val setupManager: FirstRunSetupManager,
    private val detectRomFolderUseCase: DetectRomFolderUseCase,
    private val detectEsdeUseCase: DetectEsdeUseCase,
    private val importSettingsUseCase: ImportSettingsUseCase,
    private val soundPlayer: SoundPlayer,
    private val emulatorRepository: EmulatorRepository,
    private val packRepository: ObtainiumPackRepository,
    private val obtainiumLauncher: ObtainiumLauncher
) : ViewModel() {

    // Pack entries for the missing essentials, kept for the Obtainium install hand-off.
    private var missingEssentialEntries: List<PackApp> = emptyList()

    private val _uiState = MutableStateFlow(OnboardingUiState(hasStorageAccess = hasStorageAccess()))
    val uiState: StateFlow<OnboardingUiState> = _uiState

    val setupState: StateFlow<FirstRunSetupState> = setupManager.state

    init {
        // "Run setup again" from Settings re-enters here in the same process: start from a clean slate.
        if (setupManager.state.value.started) setupManager.reset()
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    darkMode = settingsRepository.darkMode.first(),
                    themeId = settingsRepository.themeId.first()
                )
            }
        }
    }

    // ── Welcome ──────────────────────────────────────────────────────────

    /** Leave the welcome screen: ask for storage access if we don't have it, else go find games. */
    fun startFromWelcome() {
        soundPlayer.step()
        goToAccessOrGames()
    }

    private fun goToAccessOrGames() {
        val access = hasStorageAccess()
        _uiState.update { it.copy(hasStorageAccess = access, step = if (access) OnboardingStep.GAMES else OnboardingStep.ACCESS) }
        if (access) detect()
    }

    /**
     * Restore a settings backup from an old device, then carry on with setup. Folders from the
     * backup are only kept if they exist on this device (an SD card usually has a new path here).
     */
    fun restoreFromBackup(uri: Uri) {
        viewModelScope.launch {
            val applied = runCatching {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
                }
                importSettingsUseCase(text)
            }
            if (applied.isFailure) {
                _uiState.update { it.copy(restoreMessage = "That doesn't look like an eOr backup file.") }
                return@launch
            }
            // Folder paths are checked against this device later, in detect() — we may not have
            // storage access yet, and without it every path looks missing.
            val romPath = settingsRepository.romRootPath.first()
            val mediaPath = settingsRepository.mediaStoragePath.first()
            _uiState.update {
                it.copy(
                    darkMode = settingsRepository.darkMode.first(),
                    themeId = settingsRepository.themeId.first(),
                    romPath = romPath,
                    existingArtPath = mediaPath,
                    artworkChoice = if (mediaPath.isNotBlank()) ArtworkChoice.EXISTING else it.artworkChoice,
                    restoreMessage = "Settings restored!"
                )
            }
            soundPlayer.found()
            goToAccessOrGames()
        }
    }

    // ── Storage access ───────────────────────────────────────────────────

    /** Re-check access (called when we come back from the system permission screen). */
    fun refreshStorageAccess() {
        val access = hasStorageAccess()
        val wasOnAccess = _uiState.value.step == OnboardingStep.ACCESS
        _uiState.update { it.copy(hasStorageAccess = access) }
        if (access && wasOnAccess) {
            soundPlayer.found()
            _uiState.update { it.copy(step = OnboardingStep.GAMES) }
            detect()
        }
    }

    /** Continue without access: the user can still pick folders by hand. */
    fun skipStorageAccess() {
        soundPlayer.step()
        _uiState.update { it.copy(step = OnboardingStep.GAMES) }
    }

    private fun hasStorageAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
        else ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED

    // ── Games ────────────────────────────────────────────────────────────

    /** Look for the user's games (and an ES-DE install) in the usual places. */
    fun detect() {
        if (_uiState.value.detecting || !_uiState.value.hasStorageAccess) return
        _uiState.update { it.copy(detecting = true) }
        viewModelScope.launch {
            val esde = runCatching { detectEsdeUseCase() }.getOrNull()
            val candidates = runCatching { detectRomFolderUseCase.all(preferred = esde?.romDirectory) }
                .getOrDefault(emptyList())
            _uiState.update { s0 ->
                // Folders restored from a backup only count if they exist on this device.
                val keptRom = s0.romPath.takeIf { exists(it) }.orEmpty()
                val keptArt = s0.existingArtPath.takeIf { exists(it) }.orEmpty()
                val s = s0.copy(
                    existingArtPath = keptArt,
                    artworkChoice = if (s0.artworkChoice == ArtworkChoice.EXISTING && keptArt.isBlank())
                        ArtworkChoice.DOWNLOAD else s0.artworkChoice,
                    restoreMessage = s0.restoreMessage?.let {
                        if (s0.romPath.isNotBlank() && keptRom.isBlank())
                            "Settings restored! Your old games folder isn't on this device, so here's what I found."
                        else it
                    }
                )
                // A restored/picked folder wins; else the best detected one.
                val romPath = keptRom.ifBlank { candidates.firstOrNull()?.path.orEmpty() }
                val withEsde = if (esde?.mediaPath != null && s.existingArtPath.isBlank())
                    s.copy(artworkChoice = ArtworkChoice.EXISTING, existingArtPath = esde.mediaPath)
                else s
                withEsde.copy(detecting = false, romCandidates = candidates, romPath = romPath, esde = esde)
            }
            if (candidates.isNotEmpty() || esde != null) soundPlayer.found()
        }
    }

    fun showRomFolderChoices(show: Boolean) = _uiState.update { it.copy(choosingRomFolder = show) }

    fun chooseRomCandidate(path: String) =
        _uiState.update { it.copy(romPath = path, createRomFolder = false, choosingRomFolder = false) }

    /** A folder picked with the system picker. Counts its games so the summary stays accurate. */
    fun setRomPath(path: String) {
        _uiState.update { it.copy(romPath = path, createRomFolder = false, choosingRomFolder = false) }
        viewModelScope.launch {
            val counted = runCatching { detectRomFolderUseCase.all(preferred = path) }.getOrDefault(emptyList())
                .firstOrNull { it.path == path }
            if (counted != null) _uiState.update { s ->
                s.copy(romCandidates = listOf(counted) + s.romCandidates.filter { it.path != path })
            }
        }
    }

    /** The user explicitly asked for a fresh games folder (we never create one on our own). */
    fun chooseCreateRomFolder() {
        _uiState.update { it.copy(createRomFolder = true, romPath = "", choosingRomFolder = false) }
        confirmGamesStep()
    }

    /** Leave the games step with the chosen folder. Ignored while detection is still running. */
    fun confirmGamesStep() {
        val s = _uiState.value
        if (s.working || s.detecting) return
        if (s.romPath.isBlank() && !s.createRomFolder) return
        soundPlayer.step()
        _uiState.update { it.copy(step = OnboardingStep.ARTWORK) }
    }

    // ── Artwork ──────────────────────────────────────────────────────────

    fun setArtworkChoice(choice: ArtworkChoice) = _uiState.update { it.copy(artworkChoice = choice) }
    fun setExistingArtPath(path: String) =
        _uiState.update { it.copy(existingArtPath = path, artworkChoice = ArtworkChoice.EXISTING) }
    fun setDownloadPath(path: String) =
        _uiState.update { it.copy(downloadPath = path, artworkChoice = ArtworkChoice.DOWNLOAD) }
    fun setAlsoDownloadMissing(enabled: Boolean) = _uiState.update { it.copy(alsoDownloadMissing = enabled) }
    fun toggleScreenScraper() = _uiState.update { it.copy(ssOpen = !it.ssOpen) }
    fun updateSsId(value: String) = _uiState.update { it.copy(ssId = value) }
    fun updateSsPassword(value: String) = _uiState.update { it.copy(ssPassword = value) }

    /** Persist the folders + artwork decision and move on to the theme. */
    fun confirmArtworkStep() {
        val s0 = _uiState.value
        if (s0.working) return
        if (s0.artworkChoice == ArtworkChoice.EXISTING && s0.existingArtPath.isBlank()) return
        _uiState.update { it.copy(working = true) }
        soundPlayer.step()
        viewModelScope.launch {
            val s = _uiState.value
            val romPath = if (s.createRomFolder) createDefaultRomFolders() else s.romPath
            settingsRepository.setRomRootPath(romPath)

            val mediaPath = when (s.artworkChoice) {
                ArtworkChoice.EXISTING -> s.existingArtPath
                ArtworkChoice.DOWNLOAD -> s.downloadPath.ifBlank { createDefaultMediaFolder() }
                ArtworkChoice.SKIP -> ""
            }
            settingsRepository.setMediaStoragePath(mediaPath)

            if (s.downloadArtwork && s.ssId.isNotBlank() && s.ssPassword.isNotBlank()) {
                settingsRepository.setScraperCredentials(s.ssId.trim(), s.ssPassword)
            }
            _uiState.update { it.copy(working = false, romPath = romPath, step = OnboardingStep.THEME) }
        }
    }

    /** Rough download size for the games we found — enough to set expectations, not a promise. */
    fun estimatedDownloadBytes(): Long {
        val games = _uiState.value.selectedCandidate?.gameCount ?: 0
        return games * BYTES_PER_GAME_ESTIMATE
    }

    // ── Theme ────────────────────────────────────────────────────────────

    /** Persist the look immediately so the whole app previews it live. */
    fun setDarkMode(dark: Boolean) {
        _uiState.update { it.copy(darkMode = dark) }
        viewModelScope.launch { settingsRepository.setDarkMode(dark) }
    }

    fun setThemeId(id: String) {
        _uiState.update { it.copy(themeId = id) }
        viewModelScope.launch { settingsRepository.setThemeId(id) }
    }

    /** Theme picked — move to the build step and start the pipeline. */
    fun confirmThemeStep() {
        soundPlayer.step()
        _uiState.update { it.copy(step = OnboardingStep.SETUP) }
        setupManager.start(downloadArtwork = _uiState.value.downloadArtwork)
        checkMissingEssentials()
    }

    // ── Navigation ───────────────────────────────────────────────────────

    /** Step back. From the build screen this is only allowed once the scans have settled. */
    fun backStep() {
        val s = _uiState.value
        val prev = when (s.step) {
            OnboardingStep.WELCOME -> return
            OnboardingStep.ACCESS -> OnboardingStep.WELCOME
            OnboardingStep.GAMES -> if (s.choosingRomFolder) { showRomFolderChoices(false); return } else OnboardingStep.WELCOME
            OnboardingStep.ARTWORK -> OnboardingStep.GAMES
            OnboardingStep.THEME -> OnboardingStep.ARTWORK
            OnboardingStep.SETUP -> {
                if (!setupState.value.requiredStepsSettled) return
                setupManager.reset()
                OnboardingStep.GAMES
            }
        }
        soundPlayer.step()
        _uiState.update { it.copy(step = prev, createRomFolder = false) }
    }

    // ── Build ────────────────────────────────────────────────────────────

    /** Work out which recommended emulators the user is missing, for the Obtainium install nudge. */
    private fun checkMissingEssentials() {
        viewModelScope.launch {
            val installed = emulatorRepository.getInstalledEmulators()
                .filter { it.isInstalled }
                .map { it.packageName }
                .toSet()
            val missing = runCatching { packRepository.missingEssentials(installed) }.getOrDefault(emptyList())
            missingEssentialEntries = missing
            _uiState.update { it.copy(missingEssentialCount = missing.size) }
        }
    }

    /** Hand the missing essential emulators to Obtainium to install (or open its install page). */
    fun installEssentialsWithObtainium() {
        viewModelScope.launch {
            if (!obtainiumLauncher.isInstalled()) {
                obtainiumLauncher.openInstallPage()
                return@launch
            }
            obtainiumLauncher.importApps(missingEssentialEntries)
        }
    }

    /** Stop the online artwork download; whatever already arrived is kept. */
    fun stopMediaDownload() = setupManager.stopMediaDownload()

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

    private fun exists(path: String) = path.isNotBlank() && File(StorageUtils.resolveStoredPath(path)).isDirectory

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

    private companion object {
        // Box art + screenshot + wheel (~1 MB) plus a short video (~3 MB) with the default options.
        const val BYTES_PER_GAME_ESTIMATE = 4L * 1024 * 1024
    }
}
