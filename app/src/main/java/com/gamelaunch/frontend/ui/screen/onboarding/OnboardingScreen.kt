package com.gamelaunch.frontend.ui.screen.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gamelaunch.frontend.domain.usecase.FirstRunSetupState
import com.gamelaunch.frontend.domain.usecase.SetupStep
import com.gamelaunch.frontend.ui.component.Confetti
import com.gamelaunch.frontend.ui.component.Mascot
import com.gamelaunch.frontend.ui.component.MascotMood
import com.gamelaunch.frontend.ui.component.SpeechBubble
import com.gamelaunch.frontend.ui.input.GamepadA
import com.gamelaunch.frontend.ui.input.GamepadB
import com.gamelaunch.frontend.ui.theme.AmbientBackground
import com.gamelaunch.frontend.ui.theme.ElectricBlue
import com.gamelaunch.frontend.ui.theme.NeonPurple
import com.gamelaunch.frontend.ui.theme.ThemedScreen
import com.gamelaunch.frontend.util.StorageUtils

/**
 * First-launch experience, guided by Otto (the eOr donkey): welcome → find your games → theme →
 * build the library, ending in a confetti celebration. Technical bits (ScreenScraper, custom media
 * folder) hide behind an optional "Advanced" section so the default path is grandma-easy.
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val setupState by viewModel.setupState.collectAsState()

    val romPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { viewModel.setRomPath(StorageUtils.resolveTreeUriToPath(it) ?: it.toString()) }
    }
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { viewModel.setMediaPath(StorageUtils.resolveTreeUriToPath(it) ?: it.toString()) }
    }

    val allSettled = setupState.romScan.isSettled && setupState.emulatorDetect.isSettled &&
        setupState.androidScan.isSettled
    val anyFailed = setupState.romScan is SetupStep.Failed || setupState.emulatorDetect is SetupStep.Failed ||
        setupState.androidScan is SetupStep.Failed
    val celebrating = state.step == OnboardingStep.SETUP && allSettled && !anyFailed

    // A (advance) / B (back) for controllers.
    fun primaryAction() {
        when (state.step) {
            OnboardingStep.WELCOME -> viewModel.startFromWelcome()
            OnboardingStep.GAMES -> viewModel.confirmGamesStep()
            OnboardingStep.THEME -> viewModel.confirmThemeStep()
            OnboardingStep.SETUP -> if (celebrating) viewModel.finishOnboarding(onFinished)
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    ThemedScreen {
        AmbientBackground(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester)
                    .focusable()
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (event.key) {
                            GamepadA -> { primaryAction(); true }
                            GamepadB -> {
                                if (state.step == OnboardingStep.GAMES || state.step == OnboardingStep.THEME) {
                                    viewModel.backStep(); true
                                } else false
                            }
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
                    AnimatedContent(targetState = state.step, label = "onboarding_step") { step ->
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            when (step) {
                                OnboardingStep.WELCOME -> WelcomeStep(onStart = viewModel::startFromWelcome)
                                OnboardingStep.GAMES -> GamesStep(
                                    state = state,
                                    onPickRom = { romPicker.launch(null) },
                                    onPickMedia = { mediaPicker.launch(null) },
                                    onToggleAdvanced = viewModel::toggleAdvanced,
                                    onSsId = viewModel::updateSsId,
                                    onSsPass = viewModel::updateSsPassword,
                                    onContinue = viewModel::confirmGamesStep
                                )
                                OnboardingStep.THEME -> ThemeStep(
                                    darkMode = state.darkMode,
                                    onSetDark = viewModel::setDarkMode,
                                    onBack = viewModel::backStep,
                                    onContinue = viewModel::confirmThemeStep
                                )
                                OnboardingStep.SETUP -> BuildStep(
                                    setup = setupState,
                                    celebrating = celebrating,
                                    anyFailed = anyFailed,
                                    onRetry = viewModel::retryFailedSetup,
                                    onFinish = { viewModel.finishOnboarding(onFinished) }
                                )
                            }
                        }
                    }
                }

                // Confetti + controller hint overlays.
                Confetti(play = celebrating, modifier = Modifier.fillMaxSize())
                if (state.step != OnboardingStep.SETUP) {
                    ControllerHint(Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp))
                }
            }
        }
    }
}

// ── Steps ─────────────────────────────────────────────────────────────────

@Composable
private fun WelcomeStep(onStart: () -> Unit) {
    Spacer(Modifier.height(24.dp))
    Mascot(MascotMood.IDLE, size = 120.dp)
    Spacer(Modifier.height(20.dp))
    SpeechBubble("Hi, I'm Otto! Let's get your games ready to play — it only takes a minute.")
    Spacer(Modifier.height(28.dp))
    FillButton("Let's go!", onStart, Modifier.fillMaxWidth().height(54.dp))
}

@Composable
private fun GamesStep(
    state: OnboardingUiState,
    onPickRom: () -> Unit,
    onPickMedia: () -> Unit,
    onToggleAdvanced: () -> Unit,
    onSsId: (String) -> Unit,
    onSsPass: (String) -> Unit,
    onContinue: () -> Unit
) {
    val found = state.romPath.isNotBlank() && !state.createdRomFolder
    val mood = when {
        state.detecting -> MascotMood.THINKING
        found -> MascotMood.CHEER
        else -> MascotMood.IDLE
    }
    Spacer(Modifier.height(12.dp))
    Mascot(mood, size = 92.dp)
    Spacer(Modifier.height(16.dp))
    SpeechBubble(
        when {
            state.detecting -> "Looking for your games…"
            found -> "I found your games! 🎮"
            else -> "No games yet? No problem — I'll make you a folder."
        }
    )
    Spacer(Modifier.height(20.dp))

    Card {
        if (state.detecting) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = ElectricBlue, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Searching your storage…", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (found) {
            FolderRow(state.romPath, "")
            if (state.detectedGameCount > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "${state.detectedGameCount}${if (state.detectedGameCount >= 500) "+" else ""} games in here",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ElectricBlue
                )
            }
            Spacer(Modifier.height(14.dp))
            FillButton("Yes, that's them!", onContinue, Modifier.fillMaxWidth().height(50.dp), loading = state.working)
            Spacer(Modifier.height(6.dp))
            TextLink("Pick a different folder", onPickRom)
        } else {
            Text(
                "I'll create a games folder with a spot for every console. Just drop your games in later.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            FillButton("Create my games folder", onContinue, Modifier.fillMaxWidth().height(50.dp), loading = state.working)
            Spacer(Modifier.height(6.dp))
            TextLink("I'll choose one myself", onPickRom)
        }

        Spacer(Modifier.height(6.dp))
        TextLink(if (state.advancedOpen) "Hide advanced" else "Advanced", onToggleAdvanced)
        if (state.advancedOpen) {
            Spacer(Modifier.height(10.dp))
            Text("Artwork folder (optional)", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            FolderRow(state.mediaPath, "We'll pick a spot for you")
            Spacer(Modifier.height(8.dp))
            OutlineButton("Choose artwork folder", onPickMedia, Modifier.fillMaxWidth())
            Spacer(Modifier.height(14.dp))
            Text("ScreenScraper account (optional)", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(
                "A free screenscraper.fr account gives the best box art and videos. Without one, eOr uses libretro & LaunchBox.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(state.ssId, onSsId, label = { Text("Username") }, singleLine = true,
                modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(state.ssPassword, onSsPass, label = { Text("Password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ThemeStep(
    darkMode: Boolean,
    onSetDark: (Boolean) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    Spacer(Modifier.height(12.dp))
    Mascot(MascotMood.IDLE, size = 88.dp)
    Spacer(Modifier.height(16.dp))
    SpeechBubble("Day or night? Pick the look you like.")
    Spacer(Modifier.height(20.dp))
    Card {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ThemeChoice("Light", Icons.Default.LightMode, selected = !darkMode,
                onClick = { onSetDark(false) }, modifier = Modifier.weight(1f))
            ThemeChoice("Dark", Icons.Default.DarkMode, selected = darkMode,
                onClick = { onSetDark(true) }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlineButton("Back", onBack, Modifier.weight(1f))
            FillButton("Continue", onContinue, Modifier.weight(1f))
        }
    }
}

@Composable
private fun BuildStep(
    setup: FirstRunSetupState,
    celebrating: Boolean,
    anyFailed: Boolean,
    onRetry: () -> Unit,
    onFinish: () -> Unit
) {
    // Ask for notification permission up front so the "library ready" notification can be delivered
    // if the user continues while media is still downloading.
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val gettingReady = combineSteps(setup.emulatorDetect, setup.androidScan)
    val games = gamesFound(setup)
    val mediaRunning = celebrating && !setup.mediaScan.isSettled

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
        FriendlyRow("Downloading box art & videos", setup.mediaScan)
        Spacer(Modifier.height(18.dp))

        when {
            anyFailed -> {
                FillButton("Try again", onRetry, Modifier.fillMaxWidth().height(50.dp))
            }
            celebrating -> {
                if (games > 0) {
                    Text(
                        "$games${if (games >= 500) "+" else ""} games loaded",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        color = ElectricBlue, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                }
                FillButton("Let's Play!", onFinish, Modifier.fillMaxWidth().height(54.dp))
                if (mediaRunning) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Box art is still coming in — I'll ping you when it's done.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                    )
                }
            }
            else -> {
                FillButton("Working…", {}, Modifier.fillMaxWidth().height(50.dp), enabled = false)
            }
        }
    }
}

// ── Small pieces ──────────────────────────────────────────────────────────

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
private fun ControllerHint(modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        HintPill("A", "Next")
        HintPill("B", "Back")
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

private fun gamesFound(setup: FirstRunSetupState): Int = when (val s = setup.romScan) {
    is SetupStep.Running -> s.done
    is SetupStep.Done -> Regex("\\d+").find(s.summary)?.value?.toIntOrNull() ?: 0
    else -> 0
}

@Composable
private fun FriendlyRow(label: String, step: SetupStep) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (step) {
                is SetupStep.Pending -> Icon(Icons.Default.RadioButtonUnchecked, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                is SetupStep.Running -> CircularProgressIndicator(color = ElectricBlue, strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp))
                is SetupStep.Done -> Icon(Icons.Default.CheckCircle, "Done", tint = ElectricBlue,
                    modifier = Modifier.size(20.dp))
                is SetupStep.Failed -> Icon(Icons.Default.ErrorOutline, "Failed",
                    tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f))
            if (step is SetupStep.Running && step.total > 0) {
                Text("${step.done} / ${step.total}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (step is SetupStep.Running && step.done > 0) {
                Text("${step.done}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ThemeChoice(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(if (selected) 1.04f else 1f, label = "themeScale")
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(2.dp, if (selected) ElectricBlue else Color.Transparent, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, tint = if (selected) ElectricBlue else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size((30 * scale).dp))
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(18.dp),
        content = content
    )
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
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 6.dp, horizontal = 2.dp)
    )
}

@Composable
private fun FillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    val alpha = if (enabled) 1f else 0.5f
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.horizontalGradient(listOf(ElectricBlue.copy(alpha = alpha), NeonPurple.copy(alpha = alpha))))
            .then(if (enabled && !loading) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
        } else {
            Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White,
                maxLines = 1, modifier = Modifier.padding(horizontal = 12.dp))
        }
    }
}

@Composable
private fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}
