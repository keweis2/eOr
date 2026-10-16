package com.gamelaunch.frontend.ui.screen.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.gamelaunch.frontend.domain.usecase.DetectRomFolderUseCase
import com.gamelaunch.frontend.domain.usecase.FirstRunSetupState
import com.gamelaunch.frontend.domain.usecase.SetupStep
import com.gamelaunch.frontend.domain.usecase.SetupSystemSummary
import com.gamelaunch.frontend.ui.component.Confetti
import com.gamelaunch.frontend.ui.component.Mascot
import com.gamelaunch.frontend.ui.component.MascotMood
import com.gamelaunch.frontend.ui.component.SpeechBubble
import com.gamelaunch.frontend.ui.input.GamepadA
import com.gamelaunch.frontend.ui.input.GamepadB
import com.gamelaunch.frontend.ui.screen.settings.LocalFocusedAction
import com.gamelaunch.frontend.ui.screen.settings.dpadFocusable as settingsDpadFocusable
import com.gamelaunch.frontend.ui.screen.settings.rememberDpadEditableField
import com.gamelaunch.frontend.ui.theme.AmbientBackground
import com.gamelaunch.frontend.ui.theme.ElectricBlue
import com.gamelaunch.frontend.ui.theme.EorThemes
import com.gamelaunch.frontend.ui.theme.NeonPurple
import com.gamelaunch.frontend.ui.theme.ThemedScreen
import com.gamelaunch.frontend.util.StorageUtils
import kotlinx.coroutines.delay

/**
 * First-launch experience, guided by Otto (the eOr donkey): welcome → storage access → find your
 * games → artwork → theme → build the library, ending in a confetti celebration.
 *
 * Fully controller-driven: the d-pad moves a focus ring between controls, **A** activates the
 * focused one and **B** steps back — the same model as the Settings screens (it reuses their
 * [dpadFocusable] / [LocalFocusedAction] plumbing). Touch works everywhere too.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val setupState by viewModel.setupState.collectAsState()
    val context = LocalContext.current

    fun pickedPath(uri: Uri) = StorageUtils.resolveTreeUriToPath(uri) ?: uri.toString()
    val romPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { viewModel.setRomPath(pickedPath(it)) }
    }
    val existingArtPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { viewModel.setExistingArtPath(pickedPath(it)) }
    }
    val downloadFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { viewModel.setDownloadPath(pickedPath(it)) }
    }
    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::restoreFromBackup)
    }
    // Pre-Android 11 storage permission; 11+ uses the "All files access" settings page instead.
    val legacyStoragePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshStorageAccess() }

    // Coming back from the system "All files access" page: pick up the new grant.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshStorageAccess()
        onPauseOrDispose { }
    }

    fun requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        .setData(Uri.parse("package:${context.packageName}"))
                )
            }.onFailure {
                runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
            }
        } else {
            legacyStoragePermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    val anyFailed = setupState.romScan is SetupStep.Failed || setupState.emulatorDetect is SetupStep.Failed ||
        setupState.androidScan is SetupStep.Failed
    val celebrating = state.step == OnboardingStep.SETUP && setupState.requiredStepsSettled && !anyFailed
    val mediaRunning = celebrating && !setupState.mediaScan.isSettled

    // Only ask for notifications if they'd actually be used: the user is leaving while art is still
    // downloading. Either answer finishes onboarding.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.finishOnboarding(onFinished) }
    fun finish() {
        val needsAsk = mediaRunning && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsAsk) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else viewModel.finishOnboarding(onFinished)
    }

    // ── Controller focus plumbing (mirrors SettingsDetailScaffold) ──
    val focusManager = LocalFocusManager.current
    val focusedAction = remember { mutableStateOf<(() -> Unit)?>(null) }
    // The current step's primary button's action, so A works even before anything has focus.
    val primaryAction = remember { mutableStateOf<(() -> Unit)?>(null) }
    val rootFocus = remember { FocusRequester() }
    // The step's main button. Focus starts here on every step so A always does the obvious thing.
    val primaryFocus = remember { FocusRequester() }
    var hasFocus by remember { mutableStateOf(false) }
    var rootFocused by remember { mutableStateOf(false) }
    val view = LocalView.current

    DisposableEffect(view) {
        val prev = view.isFocusableInTouchMode
        view.isFocusableInTouchMode = true
        onDispose { view.isFocusableInTouchMode = prev }
    }
    // Land on the step's primary button whenever the step (or what it shows) changes. Retried briefly
    // because AnimatedContent attaches the new step a frame or two later.
    LaunchedEffect(state.step, state.detecting, state.choosingRomFolder, celebrating, anyFailed) {
        repeat(6) {
            delay(120)
            runCatching { primaryFocus.requestFocus() }
        }
    }
    // Keep the d-pad alive: if nothing holds focus (fresh screen, or after a touch), park it on the
    // screen root. The root is a plain focusable, which — unlike the clickable buttons — can take focus
    // even in touch mode; the first controller press then hands it to the step's primary button.
    LaunchedEffect(Unit) {
        while (true) {
            // No IME check here (unlike Settings): on the Retroid isImeVisible reads true at launch with
            // no keyboard up, which blocked this forever. A text field being edited holds focus, so
            // hasFocus already keeps us out of the keyboard's way.
            if (!hasFocus && view.hasWindowFocus()) {
                runCatching { rootFocus.requestFocus() }
            }
            delay(150)
        }
    }

    ThemedScreen {
        CompositionLocalProvider(LocalFocusedAction provides focusedAction, LocalPrimaryAction provides primaryAction) {
            AmbientBackground(Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(rootFocus)
                        .onFocusChanged {
                            hasFocus = it.hasFocus
                            rootFocused = it.isFocused
                        }
                        .focusable()
                        .onKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                            if (rootFocused) {
                                return@onKeyEvent when (event.key) {
                                    // First press: jump to the step's primary button (fall back to the
                                    // first control if this step has none showing).
                                    Key.DirectionDown, Key.DirectionUp, Key.DirectionLeft, Key.DirectionRight -> {
                                        runCatching { primaryFocus.requestFocus() }
                                        if (rootFocused) focusManager.moveFocus(FocusDirection.Enter)
                                        true
                                    }
                                    // A straight away does the obvious thing: press the primary button.
                                    // In touch mode A can't move focus (only the d-pad switches Android to
                                    // key navigation), so press the step's primary button directly.
                                    GamepadA, Key.DirectionCenter, Key.Enter -> {
                                        runCatching { primaryFocus.requestFocus() }
                                        if (!rootFocused) focusedAction.value?.invoke()
                                        else primaryAction.value?.invoke()
                                        true
                                    }
                                    GamepadB, Key.Back -> { viewModel.backStep(); true }
                                    else -> false
                                }
                            }
                            when (event.key) {
                                Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
                                Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
                                Key.DirectionLeft -> focusManager.moveFocus(FocusDirection.Left)
                                Key.DirectionRight -> focusManager.moveFocus(FocusDirection.Right)
                                GamepadA, Key.DirectionCenter, Key.Enter -> {
                                    val action = focusedAction.value
                                    if (action != null) { action(); true } else false
                                }
                                GamepadB -> { viewModel.backStep(); true }
                                else -> false
                            }
                        }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .padding(horizontal = 28.dp, vertical = 16.dp)
                    ) {
                        StepDots(state.step)
                        Spacer(Modifier.height(8.dp))
                        AnimatedContent(
                            targetState = state.step, label = "onboarding_step",
                            modifier = Modifier.weight(1f)
                        ) { step ->
                            Column(
                                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                when (step) {
                                    OnboardingStep.WELCOME -> WelcomeStep(
                                        state = state,
                                        primaryFocus = primaryFocus,
                                        onStart = viewModel::startFromWelcome,
                                        onRestore = { backupPicker.launch(arrayOf("application/json", "text/plain", "*/*")) }
                                    )
                                    OnboardingStep.ACCESS -> AccessStep(
                                        primaryFocus = primaryFocus,
                                        onAllow = ::requestStorageAccess,
                                        onSkip = viewModel::skipStorageAccess,
                                        onBack = viewModel::backStep
                                    )
                                    OnboardingStep.GAMES -> GamesStep(
                                        state = state,
                                        primaryFocus = primaryFocus,
                                        onConfirm = viewModel::confirmGamesStep,
                                        onShowChoices = viewModel::showRomFolderChoices,
                                        onChooseCandidate = viewModel::chooseRomCandidate,
                                        onBrowse = { romPicker.launch(null) },
                                        onCreateFolder = viewModel::chooseCreateRomFolder,
                                        onAllowAccess = ::requestStorageAccess,
                                        onBack = viewModel::backStep
                                    )
                                    OnboardingStep.ARTWORK -> ArtworkStep(
                                        state = state,
                                        estimatedBytes = viewModel.estimatedDownloadBytes(),
                                        primaryFocus = primaryFocus,
                                        onChoice = viewModel::setArtworkChoice,
                                        onPickExisting = { existingArtPicker.launch(null) },
                                        onPickDownloadFolder = { downloadFolderPicker.launch(null) },
                                        onAlsoDownload = viewModel::setAlsoDownloadMissing,
                                        onToggleSs = viewModel::toggleScreenScraper,
                                        onSsId = viewModel::updateSsId,
                                        onSsPass = viewModel::updateSsPassword,
                                        onBack = viewModel::backStep,
                                        onContinue = viewModel::confirmArtworkStep
                                    )
                                    OnboardingStep.THEME -> ThemeStep(
                                        darkMode = state.darkMode,
                                        themeId = state.themeId,
                                        primaryFocus = primaryFocus,
                                        onSetDark = viewModel::setDarkMode,
                                        onSetTheme = viewModel::setThemeId,
                                        onBack = viewModel::backStep,
                                        onContinue = viewModel::confirmThemeStep
                                    )
                                    OnboardingStep.SETUP -> BuildStep(
                                        setup = setupState,
                                        celebrating = celebrating,
                                        anyFailed = anyFailed,
                                        mediaRunning = mediaRunning,
                                        missingEssentialCount = state.missingEssentialCount,
                                        primaryFocus = primaryFocus,
                                        onInstallEmulators = viewModel::installEssentialsWithObtainium,
                                        onRetry = viewModel::retryFailedSetup,
                                        onStopMedia = viewModel::stopMediaDownload,
                                        onChangeFolders = viewModel::backStep,
                                        onFinish = ::finish
                                    )
                                }
                                Spacer(Modifier.height(12.dp))
                            }
                        }
                        ControllerHint(
                            // B steps back everywhere but Welcome, and on the build screen only once
                            // the scans have settled.
                            showBack = state.step != OnboardingStep.WELCOME &&
                                (state.step != OnboardingStep.SETUP || setupState.requiredStepsSettled),
                            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp)
                        )
                    }

                    Confetti(play = celebrating, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

// ── Steps ─────────────────────────────────────────────────────────────────

@Composable
private fun WelcomeStep(
    state: OnboardingUiState,
    primaryFocus: FocusRequester,
    onStart: () -> Unit,
    onRestore: () -> Unit
) {
    Spacer(Modifier.height(24.dp))
    Mascot(MascotMood.IDLE, size = 120.dp)
    Spacer(Modifier.height(20.dp))
    SpeechBubble("Hi, I'm Otto! Let's get your games ready to play — it only takes a minute.")
    Spacer(Modifier.height(28.dp))
    FillButton("Let's go!", onStart, Modifier.fillMaxWidth().height(54.dp), focusRequester = primaryFocus)
    Spacer(Modifier.height(8.dp))
    TextLink("Restore from my old device", onRestore)
    state.restoreMessage?.let {
        Spacer(Modifier.height(6.dp))
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center)
    }
}

@Composable
private fun AccessStep(
    primaryFocus: FocusRequester,
    onAllow: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit
) {
    Spacer(Modifier.height(12.dp))
    Mascot(MascotMood.IDLE, size = 92.dp)
    Spacer(Modifier.height(16.dp))
    SpeechBubble("First, I need permission to look at your files so I can find your games.")
    Spacer(Modifier.height(20.dp))
    Card {
        Text(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                "Android will open a settings page — switch on \"Allow access to manage all files\" for eOr, " +
                    "then come back. eOr only reads your games and artwork, and saves media where you tell it to."
            else "Android will ask to let eOr read your storage. eOr only reads your games and artwork.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))
        FillButton("Allow access", onAllow, Modifier.fillMaxWidth().height(50.dp), focusRequester = primaryFocus)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextLink("Back", onBack)
            TextLink("Not now — I'll pick folders myself", onSkip)
        }
    }
}

@Composable
private fun GamesStep(
    state: OnboardingUiState,
    primaryFocus: FocusRequester,
    onConfirm: () -> Unit,
    onShowChoices: (Boolean) -> Unit,
    onChooseCandidate: (String) -> Unit,
    onBrowse: () -> Unit,
    onCreateFolder: () -> Unit,
    onAllowAccess: () -> Unit,
    onBack: () -> Unit
) {
    val selected = state.selectedCandidate
    val hasFolder = state.romPath.isNotBlank()
    val mood = when {
        state.detecting -> MascotMood.THINKING
        hasFolder -> MascotMood.CHEER
        else -> MascotMood.IDLE
    }
    Spacer(Modifier.height(12.dp))
    Mascot(mood, size = 92.dp)
    Spacer(Modifier.height(16.dp))
    SpeechBubble(
        when {
            state.detecting -> "Looking for your games…"
            !state.hasStorageAccess && !hasFolder -> "I can't see your files yet."
            state.choosingRomFolder -> "Which one is your games folder?"
            hasFolder && selected != null -> "I found your games! 🎮"
            hasFolder -> "Got it — I'll use that folder."
            else -> "I couldn't find any games yet."
        }
    )
    Spacer(Modifier.height(20.dp))

    state.restoreMessage?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = ElectricBlue, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
    }

    Card {
        when {
            state.detecting -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = ElectricBlue, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Searching your storage…", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            state.choosingRomFolder -> {
                state.romCandidates.forEachIndexed { i, c ->
                    CandidateRow(
                        candidate = c,
                        selected = c.path == state.romPath,
                        onClick = { onChooseCandidate(c.path) },
                        modifier = if (i == 0) Modifier.focusRequester(primaryFocus) else Modifier
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlineButton(
                    "Browse for a folder…", onBrowse, Modifier.fillMaxWidth(),
                    focusRequester = if (state.romCandidates.isEmpty()) primaryFocus else null
                )
                Spacer(Modifier.height(6.dp))
                TextLink("Cancel", { onShowChoices(false) })
            }

            !state.hasStorageAccess && !hasFolder -> {
                Text(
                    "Without file access I can't search for your games. Allow access, or pick your games " +
                        "folder yourself.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                FillButton("Allow access", onAllowAccess, Modifier.fillMaxWidth().height(50.dp),
                    focusRequester = primaryFocus)
                Spacer(Modifier.height(6.dp))
                TextLink("Pick my games folder", onBrowse)
            }

            hasFolder -> {
                FolderRow(state.romPath, "")
                if (selected != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${selected.gameCount}${if (selected.capped) "+" else ""} games in here",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ElectricBlue
                    )
                    if (selected.perSystem.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        SystemChips(selected.perSystem)
                    }
                }
                Spacer(Modifier.height(14.dp))
                FillButton("Yes, that's them!", onConfirm, Modifier.fillMaxWidth().height(50.dp),
                    focusRequester = primaryFocus)
                Spacer(Modifier.height(6.dp))
                TextLink("Not right? Pick a different folder", { onShowChoices(true) })
            }

            else -> {
                Text(
                    "Already have games somewhere? Point me at the folder. Otherwise I can make you a games " +
                        "folder with a spot for every console — just drop your games in later.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                FillButton("Pick my games folder", onBrowse, Modifier.fillMaxWidth().height(50.dp),
                    focusRequester = primaryFocus)
                Spacer(Modifier.height(8.dp))
                OutlineButton("Create a games folder for me", onCreateFolder, Modifier.fillMaxWidth())
            }
        }

        state.esde?.takeIf { !state.detecting && !state.choosingRomFolder && it.mediaPath != null }?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                "I also spotted your ES-DE setup — next, I'll offer to use its artwork.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!state.choosingRomFolder) {
            Spacer(Modifier.height(4.dp))
            TextLink("Back", onBack)
        }
    }
}

@Composable
private fun ArtworkStep(
    state: OnboardingUiState,
    estimatedBytes: Long,
    primaryFocus: FocusRequester,
    onChoice: (ArtworkChoice) -> Unit,
    onPickExisting: () -> Unit,
    onPickDownloadFolder: () -> Unit,
    onAlsoDownload: (Boolean) -> Unit,
    onToggleSs: () -> Unit,
    onSsId: (String) -> Unit,
    onSsPass: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    val esdeArt = state.esde?.mediaPath
    val usingEsde = esdeArt != null && state.existingArtPath == esdeArt
    Spacer(Modifier.height(12.dp))
    Mascot(MascotMood.IDLE, size = 88.dp)
    Spacer(Modifier.height(16.dp))
    SpeechBubble(
        if (esdeArt != null) "Your ES-DE library already has artwork. Want me to use it?"
        else "Box art makes your library shine. How should I get it?"
    )
    Spacer(Modifier.height(20.dp))
    Card {
        // 1 · Existing art
        ChoiceRow(
            title = if (esdeArt != null) "Use my ES-DE artwork" else "Use artwork I already have",
            subtitle = when {
                state.existingArtPath.isNotBlank() -> state.existingArtPath
                else -> "Pick the folder with your box art (e.g. ES-DE/downloaded_media)"
            },
            selected = state.artworkChoice == ArtworkChoice.EXISTING,
            onClick = {
                if (state.existingArtPath.isBlank()) onPickExisting() else onChoice(ArtworkChoice.EXISTING)
            }
        )
        if (state.artworkChoice == ArtworkChoice.EXISTING && state.existingArtPath.isNotBlank()) {
            Column(Modifier.padding(start = 34.dp)) {
                if (usingEsde && (state.esde?.mediaSystemCount ?: 0) > 0) {
                    Text("Artwork for ${state.esde!!.mediaSystemCount} systems — nothing to download.",
                        style = MaterialTheme.typography.bodySmall, color = ElectricBlue)
                }
                SwitchRow(
                    title = "Also download art for games missing it",
                    subtitle = "Only fills gaps — never replaces what's there.",
                    checked = state.alsoDownloadMissing,
                    onChange = onAlsoDownload
                )
                TextLink("Use a different folder", onPickExisting)
            }
        }
        Spacer(Modifier.height(6.dp))

        // 2 · Download
        val sizeText = if (estimatedBytes > 0) " · roughly ${Formatter.formatShortFileSize(context, estimatedBytes)}" else ""
        ChoiceRow(
            title = "Download artwork for me",
            subtitle = "Saved to ${state.downloadPath.ifBlank { "eOr/media" }}$sizeText",
            selected = state.artworkChoice == ArtworkChoice.DOWNLOAD,
            onClick = { onChoice(ArtworkChoice.DOWNLOAD) }
        )
        if (state.artworkChoice == ArtworkChoice.DOWNLOAD) {
            Column(Modifier.padding(start = 34.dp)) {
                Text(
                    "Box art, screenshots and short videos. Runs in the background — you can stop it anytime.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextLink("Save somewhere else", onPickDownloadFolder)
                TextLink(if (state.ssOpen) "Hide ScreenScraper account" else "Add a ScreenScraper account (optional)", onToggleSs)
                if (state.ssOpen) ScreenScraperFields(state, onSsId, onSsPass)
            }
        }
        Spacer(Modifier.height(6.dp))

        // 3 · Skip
        ChoiceRow(
            title = "Skip for now",
            subtitle = "Get artwork later in Settings › Media & Artwork",
            selected = state.artworkChoice == ArtworkChoice.SKIP,
            onClick = { onChoice(ArtworkChoice.SKIP) }
        )

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlineButton("Back", onBack, Modifier.weight(1f))
            FillButton(
                "Continue", onContinue, Modifier.weight(1f),
                enabled = state.artworkChoice != ArtworkChoice.EXISTING || state.existingArtPath.isNotBlank(),
                loading = state.working, focusRequester = primaryFocus
            )
        }
    }
}

@Composable
private fun ScreenScraperFields(state: OnboardingUiState, onSsId: (String) -> Unit, onSsPass: (String) -> Unit) {
    Text(
        "A free screenscraper.fr account gives the best box art and videos. Without one, eOr uses " +
            "libretro & LaunchBox. You can add it later in Settings too.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))
    val idField = rememberDpadEditableField()
    OutlinedTextField(state.ssId, onSsId, label = { Text("Username") }, singleLine = true,
        readOnly = idField.readOnly, modifier = Modifier.fillMaxWidth().then(idField.modifier))
    Spacer(Modifier.height(8.dp))
    val pwField = rememberDpadEditableField()
    OutlinedTextField(state.ssPassword, onSsPass, label = { Text("Password") }, singleLine = true,
        readOnly = pwField.readOnly,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth().then(pwField.modifier))
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun ThemeStep(
    darkMode: Boolean,
    themeId: String,
    primaryFocus: FocusRequester,
    onSetDark: (Boolean) -> Unit,
    onSetTheme: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    Spacer(Modifier.height(12.dp))
    Mascot(MascotMood.IDLE, size = 88.dp)
    Spacer(Modifier.height(16.dp))
    SpeechBubble("Pick a look you like — you'll see it right away.")
    Spacer(Modifier.height(20.dp))
    Card {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ModeChoice("Light", Icons.Default.LightMode, selected = !darkMode,
                onClick = { onSetDark(false) }, modifier = Modifier.weight(1f))
            ModeChoice("Dark", Icons.Default.DarkMode, selected = darkMode,
                onClick = { onSetDark(true) }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        Text("Colors", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        val selectedId = EorThemes.byId(themeId).id
        EorThemes.All.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                row.forEach { theme ->
                    ThemeSwatch(
                        name = theme.name,
                        colors = listOf(theme.accent, theme.accent2, theme.accent3),
                        selected = theme.id == selectedId,
                        onClick = { onSetTheme(theme.id) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Text("More themes — and your own — in Settings › Appearance.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlineButton("Back", onBack, Modifier.weight(1f))
            FillButton("Continue", onContinue, Modifier.weight(1f), focusRequester = primaryFocus)
        }
    }
}

@Composable
private fun BuildStep(
    setup: FirstRunSetupState,
    celebrating: Boolean,
    anyFailed: Boolean,
    mediaRunning: Boolean,
    missingEssentialCount: Int,
    primaryFocus: FocusRequester,
    onInstallEmulators: () -> Unit,
    onRetry: () -> Unit,
    onStopMedia: () -> Unit,
    onChangeFolders: () -> Unit,
    onFinish: () -> Unit
) {
    val gettingReady = combineSteps(setup.emulatorDetect, setup.androidScan)
    val games = gamesFound(setup)

    Spacer(Modifier.height(8.dp))
    Mascot(if (celebrating) MascotMood.CHEER else MascotMood.THINKING, size = if (celebrating) 108.dp else 84.dp)
    Spacer(Modifier.height(16.dp))
    SpeechBubble(
        when {
            anyFailed -> "Hmm, that didn't quite work."
            celebrating -> "Your arcade is ready! 🎉"
            else -> "Building your arcade…"
        }
    )
    Spacer(Modifier.height(20.dp))

    Card {
        FriendlyRow("Finding your games", setup.romScan)
        FriendlyRow("Getting everything ready", gettingReady)
        FriendlyRow(
            if (setup.downloadingArtwork) "Downloading box art & videos" else "Importing your artwork",
            setup.mediaScan
        )
        if (setup.downloadingArtwork && setup.mediaScan is SetupStep.Running) {
            TextLink("Stop downloading", onStopMedia)
        }
        Spacer(Modifier.height(14.dp))

        when {
            anyFailed -> {
                FillButton("Try again", onRetry, Modifier.fillMaxWidth().height(50.dp), focusRequester = primaryFocus)
                Spacer(Modifier.height(6.dp))
                TextLink("Change folders", onChangeFolders)
            }
            celebrating -> {
                if (games > 0) {
                    Text(
                        "$games game${if (games == 1) "" else "s"} loaded",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        color = ElectricBlue, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(10.dp))
                }
                if (setup.systems.isNotEmpty()) {
                    SystemSummaryList(setup.systems)
                    Spacer(Modifier.height(12.dp))
                }
                val emulators = emulatorsFound(setup)
                if (games == 0) {
                    SetupTips(noGames = true, noEmulators = false)
                    Spacer(Modifier.height(14.dp))
                }
                // Missing recommended emulators → offer a one-tap Obtainium install. Falls back to the
                // manual tip only if the pack couldn't be loaded (missingEssentialCount stays 0).
                if (missingEssentialCount > 0) {
                    ObtainiumInstallTip(count = missingEssentialCount, onInstall = onInstallEmulators)
                    Spacer(Modifier.height(14.dp))
                } else if (emulators == 0) {
                    SetupTips(noGames = false, noEmulators = true)
                    Spacer(Modifier.height(14.dp))
                }
                FillButton("Let's Play!", onFinish, Modifier.fillMaxWidth().height(54.dp), focusRequester = primaryFocus)
                if (mediaRunning) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Box art is still coming in — it'll keep going in the background.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(4.dp))
                TextLink("Change folders", onChangeFolders)
            }
            else -> {
                FillButton("Working…", {}, Modifier.fillMaxWidth().height(50.dp), enabled = false)
            }
        }
    }
}

// ── Small pieces ──────────────────────────────────────────────────────────

private val LocalPrimaryAction = androidx.compose.runtime.compositionLocalOf<androidx.compose.runtime.MutableState<(() -> Unit)?>?> { null }

/** Register [onClick] as the step's primary action while this button is on screen. */
@Composable
private fun RegisterPrimary(onClick: () -> Unit) {
    val holder = LocalPrimaryAction.current ?: return
    val latest by androidx.compose.runtime.rememberUpdatedState(onClick)
    val stable = remember { { latest() } }
    DisposableEffect(holder) {
        holder.value = stable
        onDispose { if (holder.value === stable) holder.value = null }
    }
}

/**
 * The settings d-pad focus target with a bolder, high-contrast ring: onboarding sits on the bright
 * ambient background, where the theme's focus colour (white for Default in light mode) disappears.
 */
@Composable
private fun Modifier.dpadFocusable(
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(10.dp),
    onClick: () -> Unit
): Modifier = settingsDpadFocusable(
    shape = shape,
    focusColor = MaterialTheme.colorScheme.onSurface,
    focusWidth = 3.dp,
    onClick = onClick
)

@Composable
private fun StepDots(step: OnboardingStep) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        OnboardingStep.entries.forEach { s ->
            val on = s.ordinal <= step.ordinal
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (s == step) 11.dp else 8.dp)
                    .clip(CircleShape)
                    .background(if (on) ElectricBlue else MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    }
}

@Composable
private fun ControllerHint(showBack: Boolean, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        HintPill("A", "Select")
        if (showBack) HintPill("B", "Back")
    }
}

@Composable
private fun HintPill(button: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(button, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Combine two setup steps into one friendly status. */
private fun combineSteps(a: SetupStep, b: SetupStep): SetupStep = when {
    a is SetupStep.Failed -> a
    b is SetupStep.Failed -> b
    a is SetupStep.Done && b is SetupStep.Done -> SetupStep.Done("Ready to play")
    a is SetupStep.Running || b is SetupStep.Running -> SetupStep.Running(detail = "Setting things up…")
    else -> SetupStep.Pending
}

private fun gamesFound(setup: FirstRunSetupState): Int =
    setup.systems.sumOf { it.games }.takeIf { it > 0 } ?: when (val s = setup.romScan) {
        is SetupStep.Running -> s.done
        is SetupStep.Done -> Regex("\\d+").find(s.summary)?.value?.toIntOrNull() ?: 0
        else -> 0
    }

// Emulator count from the "Found N emulators · configured M systems" summary; -1 until settled so
// the "install an emulator" tip never flashes before detection has actually run.
private fun emulatorsFound(setup: FirstRunSetupState): Int = when (val s = setup.emulatorDetect) {
    is SetupStep.Done -> Regex("\\d+").find(s.summary)?.value?.toIntOrNull() ?: 0
    else -> -1
}

/** Per-system result of setup: games found and which emulator will run them (or that none will yet). */
@Composable
private fun SystemSummaryList(systems: List<SetupSystemSummary>) {
    val shown = systems.take(8)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        shown.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
                Text("${s.games}", style = MaterialTheme.typography.bodyMedium, color = ElectricBlue)
                Spacer(Modifier.width(10.dp))
                Text(
                    s.emulator ?: "No emulator yet",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (s.emulator == null) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(110.dp),
                    textAlign = TextAlign.End
                )
            }
        }
        if (systems.size > shown.size) {
            Text("…and ${systems.size - shown.size} more", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Friendly nudges shown on the finished-setup screen when the library came up empty. */
@Composable
private fun SetupTips(noGames: Boolean, noEmulators: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (noGames) {
            TipRow(
                icon = Icons.Default.VideogameAsset,
                title = "Add some games",
                body = "Your games folder is empty. Copy ROM files into it, then rescan from " +
                    "Settings — eOr sorts them into the right systems automatically."
            )
        }
        if (noEmulators) {
            TipRow(
                icon = Icons.Default.Download,
                title = "Install an emulator",
                body = "I couldn't find any emulators. Install one — RetroArch covers most systems — " +
                    "and eOr will detect and assign it for you in Settings › Configure Emulators."
            )
        }
    }
}

/** Offer to install the user's missing recommended emulators through Obtainium in one tap. */
@Composable
private fun ObtainiumInstallTip(count: Int, onInstall: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TipRow(
            icon = Icons.Default.Download,
            title = "Install emulators",
            body = "Add $count recommended emulator${if (count != 1) "s" else ""} with Obtainium — a " +
                "free app installer that also keeps them updated. Don't have it? We'll help you get " +
                "it first. eOr detects and assigns them automatically."
        )
        FillButton("Install with Obtainium", onInstall, Modifier.fillMaxWidth().height(48.dp))
    }
}

@Composable
private fun TipRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = ElectricBlue, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(body, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FriendlyRow(label: String, step: SetupStep) {
    val running = step as? SetupStep.Running
    // Determinate once the step knows its total; until then (or for open-ended steps) keep spinning.
    val fraction = running?.takeIf { it.total > 0 }?.let { (it.done.toFloat() / it.total).coerceIn(0f, 1f) }
    val eta = rememberEta(label, running)
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (step) {
                is SetupStep.Pending -> Icon(Icons.Default.RadioButtonUnchecked, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                is SetupStep.Running -> if (fraction != null) {
                    CircularProgressIndicator(progress = { fraction }, color = ElectricBlue, strokeWidth = 2.5.dp,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(20.dp))
                } else {
                    CircularProgressIndicator(color = ElectricBlue, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                }
                is SetupStep.Done -> Icon(Icons.Default.CheckCircle, "Done", tint = ElectricBlue,
                    modifier = Modifier.size(20.dp))
                is SetupStep.Failed -> Icon(Icons.Default.ErrorOutline, "Failed",
                    tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f))
            if (fraction != null) {
                Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold, color = ElectricBlue)
            } else if (running != null && running.done > 0) {
                Text("${running.done}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (running != null && fraction != null) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { fraction },
                color = ElectricBlue,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(start = 30.dp).height(5.dp).clip(RoundedCornerShape(3.dp))
            )
            Row(Modifier.fillMaxWidth().padding(start = 30.dp, top = 3.dp)) {
                Text("${running.done} of ${running.total}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(eta ?: "Working out time left…", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // Say what actually went wrong (or what was done) instead of a bare icon.
        val detail = when (step) {
            is SetupStep.Failed -> step.message
            is SetupStep.Done -> step.summary.takeIf { label != "Getting everything ready" }
            else -> null
        }
        detail?.let {
            Text(it, style = MaterialTheme.typography.labelSmall,
                color = if (step is SetupStep.Failed) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 30.dp, top = 2.dp))
        }
    }
}

/**
 * "About 9 min left" for a running step, from its pace over the last ~20 seconds — a rolling window
 * rather than the whole-run average, so the estimate adapts when the pace changes (e.g. files the
 * OS already has cached go much faster than cold reads from an SD card). Null until there's enough
 * to go on, so the first wild guesses never show.
 */
@Composable
private fun rememberEta(key: String, running: SetupStep.Running?): String? {
    val samples = remember(key) { ArrayDeque<Pair<Long, Int>>() }
    if (running == null || running.total <= 0) return null
    val now = System.currentTimeMillis()
    if (samples.lastOrNull()?.second != running.done) samples.addLast(now to running.done)
    while (samples.size > 2 && now - samples.first().first > ETA_WINDOW_MS) samples.removeFirst()
    val (t0, d0) = samples.first()
    val elapsed = now - t0
    val progressed = running.done - d0
    if (elapsed < 4_000 || progressed < 5) return null
    val remainingMs = elapsed.toDouble() / progressed * (running.total - running.done)
    val minutes = (remainingMs / 60_000).toInt()
    return when {
        remainingMs < 45_000 -> "Less than a minute left"
        minutes < 1 -> "About a minute left"
        minutes < 60 -> "About ${minutes + 1} min left"
        else -> "About ${minutes / 60} h ${minutes % 60} min left"
    }
}

private const val ETA_WINDOW_MS = 20_000L

/** A detected games folder in the "pick a different folder" list. */
@Composable
private fun CandidateRow(
    candidate: DetectRomFolderUseCase.Result,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .dpadFocusable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Icon(if (selected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked, null,
            tint = ElectricBlue, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(candidate.path, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${candidate.gameCount}${if (candidate.capped) "+" else ""} games · " +
                candidate.perSystem.entries.sortedByDescending { it.value }.take(3).joinToString { it.key },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Up to six "SNES · 120"-style chips showing what's in a games folder. */
@Composable
private fun SystemChips(perSystem: Map<String, Int>) {
    val top = perSystem.entries.sortedByDescending { it.value }
    val shown = top.take(6)
    shown.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
            row.forEach { (name, n) ->
                Text(
                    "$name · $n${if (n >= DetectRomFolderUseCase.PER_FOLDER_CAP) "+" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
    if (top.size > shown.size) {
        Text("+ ${top.size - shown.size} more systems", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A radio-style option card row. */
@Composable
private fun ChoiceRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) ElectricBlue.copy(alpha = 0.12f) else Color.Transparent)
            .dpadFocusable(shape = RoundedCornerShape(12.dp), onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp)
    ) {
        Icon(if (selected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked, null,
            tint = ElectricBlue, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A whole-row toggle (the row is the focus target, so A flips it). */
@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .dpadFocusable { onChange(!checked) }
            .padding(vertical = 6.dp, horizontal = 2.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ModeChoice(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(if (selected) 1.04f else 1f, label = "modeScale")
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) ElectricBlue.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant)
            .dpadFocusable(shape = RoundedCornerShape(14.dp), onClick = onClick)
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, tint = if (selected) ElectricBlue else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size((28 * scale).dp))
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** A theme tile: its three accent colours as dots plus its name. Selecting applies it live. */
@Composable
private fun ThemeSwatch(name: String, colors: List<Color>, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) ElectricBlue.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant)
            .dpadFocusable(shape = RoundedCornerShape(12.dp), onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
            colors.forEach { c ->
                Box(Modifier.size(16.dp).clip(CircleShape).background(c)
                    .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            content = content
        )
    }
}

@Composable
private fun FolderRow(path: String, placeholder: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.FolderOpen, null, tint = ElectricBlue, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = path.ifBlank { placeholder },
            style = MaterialTheme.typography.bodyMedium,
            color = if (path.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 2, modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TextLink(text: String, onClick: () -> Unit) {
    Text(
        text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .dpadFocusable(shape = RoundedCornerShape(8.dp), onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp)
    )
}

/**
 * Primary pill button. A plain Box + [dpadFocusable] rather than a Material Button: Button brings its
 * own focus target, which would split d-pad focus from the A-button registration.
 */
@Composable
private fun FillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    focusRequester: FocusRequester? = null
) {
    val active = enabled && !loading
    if (focusRequester != null && active) RegisterPrimary(onClick)
    Box(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .height(46.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(ElectricBlue, NeonPurple).map { if (active) it else it.copy(alpha = 0.45f) }
                )
            )
            .then(if (active) Modifier.dpadFocusable(shape = RoundedCornerShape(26.dp), onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
        } else {
            Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
private fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    if (focusRequester != null) RegisterPrimary(onClick)
    Box(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .height(46.dp)
            .clip(RoundedCornerShape(26.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(26.dp))
            .dpadFocusable(shape = RoundedCornerShape(26.dp), onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}
