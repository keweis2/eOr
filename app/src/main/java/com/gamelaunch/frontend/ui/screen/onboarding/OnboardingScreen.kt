package com.gamelaunch.frontend.ui.screen.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gamelaunch.frontend.R
import com.gamelaunch.frontend.domain.usecase.FirstRunSetupState
import com.gamelaunch.frontend.domain.usecase.SetupStep
import com.gamelaunch.frontend.ui.input.GamepadA
import com.gamelaunch.frontend.ui.input.GamepadB
import com.gamelaunch.frontend.ui.theme.BrandBlue
import com.gamelaunch.frontend.ui.theme.ElectricBlue
import com.gamelaunch.frontend.ui.theme.NeonPurple
import com.gamelaunch.frontend.ui.theme.ThemedScreen
import com.gamelaunch.frontend.util.StorageUtils

/**
 * First-launch wizard: ROM folder → media folder + ScreenScraper account → theme → setup
 * checklist. Shown once; [onFinished] navigates into the app after the first-launch flag is
 * cleared.
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state      by viewModel.uiState.collectAsState()
    val setupState by viewModel.setupState.collectAsState()

    val romPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { viewModel.setRomPath(StorageUtils.resolveTreeUriToPath(it) ?: it.toString()) }
    }
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { viewModel.setMediaPath(StorageUtils.resolveTreeUriToPath(it) ?: it.toString()) }
    }

    // Gamepad: A advances the current step, B goes back (never once setup has started).
    fun primaryAction() {
        when (state.step) {
            OnboardingStep.ROM_FOLDER -> viewModel.confirmRomStep()
            OnboardingStep.MEDIA      -> viewModel.confirmMediaStep()
            OnboardingStep.THEME      -> viewModel.confirmThemeStep()
            OnboardingStep.SETUP      ->
                if (setupState.requiredStepsSettled) viewModel.finishOnboarding(onFinished)
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    ThemedScreen {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        GamepadA -> { primaryAction(); true }
                        GamepadB -> {
                            if (state.step != OnboardingStep.SETUP && state.step != OnboardingStep.ROM_FOLDER) {
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
                    .padding(horizontal = 28.dp, vertical = 18.dp)
            ) {
                BrandHeader(step = state.step)
                Spacer(Modifier.height(14.dp))

                AnimatedContent(targetState = state.step, label = "onboarding_step") { step ->
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        when (step) {
                            OnboardingStep.ROM_FOLDER -> RomFolderStep(
                                state         = state,
                                onPickFolder  = { romPicker.launch(null) },
                                onContinue    = viewModel::confirmRomStep
                            )
                            OnboardingStep.MEDIA -> MediaStep(
                                state            = state,
                                onPickFolder     = { mediaPicker.launch(null) },
                                onSsIdChange     = viewModel::updateSsId,
                                onSsPassChange   = viewModel::updateSsPassword,
                                onBack           = viewModel::backStep,
                                onContinue       = viewModel::confirmMediaStep
                            )
                            OnboardingStep.THEME -> ThemeStep(
                                darkMode    = state.darkMode,
                                onSetDark   = viewModel::setDarkMode,
                                onBack      = viewModel::backStep,
                                onContinue  = viewModel::confirmThemeStep
                            )
                            OnboardingStep.SETUP -> SetupStepContent(
                                setup      = setupState,
                                onContinue = { viewModel.finishOnboarding(onFinished) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Header: brand + step dots ─────────────────────────────────────────────

@Composable
private fun BrandHeader(step: OnboardingStep) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(R.drawable.ic_donkey_silhouette),
            contentDescription = null,
            tint = BrandBlue,
            modifier = Modifier.size(30.dp).padding(end = 6.dp)
        )
        Text("e",  fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = BrandBlue, letterSpacing = 2.sp)
        Text("Or", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold,
             color = MaterialTheme.colorScheme.onSurface, letterSpacing = 2.sp)
        Spacer(Modifier.weight(1f))
        OnboardingStep.entries.forEach { s ->
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(if (s == step) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (s.ordinal <= step.ordinal) ElectricBlue
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
            )
        }
    }
}

// ── Step 1: ROM library folder ────────────────────────────────────────────

@Composable
private fun RomFolderStep(
    state: OnboardingUiState,
    onPickFolder: () -> Unit,
    onContinue: () -> Unit
) {
    StepTitle("Welcome to eOr", "First, where do your games live?")
    StepCard {
        Text(
            "Pick the folder that holds your ROMs — for example a ROMs folder on your SD card. " +
            "No library yet? Leave it blank and we'll create a ROMs folder for you, with a " +
            "sub-folder for every console eOr supports.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        FolderRow(
            path        = state.romPath,
            placeholder = "No folder selected — we'll create one"
        )
        if (state.createdRomFolder) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Created ${state.romPath} with folders for each console",
                style = MaterialTheme.typography.labelSmall,
                color = ElectricBlue
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlineButton("Choose Folder", onPickFolder, Modifier.weight(1f))
            FillButton(
                text     = if (state.romPath.isBlank()) "Create For Me" else "Continue",
                onClick  = onContinue,
                loading  = state.working,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ── Step 2: media folder + ScreenScraper account ──────────────────────────

@Composable
private fun MediaStep(
    state: OnboardingUiState,
    onPickFolder: () -> Unit,
    onSsIdChange: (String) -> Unit,
    onSsPassChange: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    StepTitle("Artwork & videos", "Where should box art, screenshots and videos be saved?")
    StepCard {
        Text(
            "Pick a media folder — if it already contains an ES-DE library we'll import it. " +
            "Leave it blank and we'll create one for you.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        FolderRow(
            path        = state.mediaPath,
            placeholder = "No folder selected — we'll create one"
        )
        Spacer(Modifier.height(10.dp))
        OutlineButton("Choose Folder", onPickFolder, Modifier.fillMaxWidth())

        Spacer(Modifier.height(18.dp))
        Text(
            "ScreenScraper account (optional)",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "A free screenscraper.fr account gives the best art and video previews. Without one, " +
            "eOr falls back to libretro thumbnails and LaunchBox.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = state.ssId,
            onValueChange = onSsIdChange,
            label = { Text("Username (ssid)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.ssPassword,
            onValueChange = onSsPassChange,
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlineButton("Back", onBack, Modifier.weight(1f))
            FillButton(
                text     = if (state.mediaPath.isBlank()) "Create For Me" else "Continue",
                onClick  = onContinue,
                loading  = state.working,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ── Step 3: appearance ────────────────────────────────────────────────────

@Composable
private fun ThemeStep(
    darkMode: Boolean,
    onSetDark: (Boolean) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    StepTitle("Pick your look", "You can change this any time in Settings.")
    StepCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ThemeChoice("Light", Icons.Default.LightMode, selected = !darkMode,
                        onClick = { onSetDark(false) }, modifier = Modifier.weight(1f))
            ThemeChoice("Dark", Icons.Default.DarkMode, selected = darkMode,
                        onClick = { onSetDark(true) }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlineButton("Back", onBack, Modifier.weight(1f))
            FillButton("Continue", onContinue, modifier = Modifier.weight(1f))
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
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = 2.dp,
                color = if (selected) ElectricBlue else Color.Transparent,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon, contentDescription = label,
            tint = if (selected) ElectricBlue else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(30.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ── Step 4: setup checklist ───────────────────────────────────────────────

@Composable
private fun SetupStepContent(
    setup: FirstRunSetupState,
    onContinue: () -> Unit
) {
    // The media download can outlive this screen — ask for notification permission up front so
    // the "library ready" notification can be delivered if the user continues early.
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    StepTitle("Building your library", "This runs once — media keeps downloading in the background if you skip ahead.")
    StepCard {
        SetupRow("ROM library scan",  setup.romScan)
        SetupRow("Emulator detection", setup.emulatorDetect)
        SetupRow("Android games",      setup.androidScan)
        SetupRow("Artwork & videos",   setup.mediaScan)

        Spacer(Modifier.height(16.dp))

        val mediaRunning = setup.requiredStepsSettled && !setup.mediaScan.isSettled
        FillButton(
            text = when {
                !setup.requiredStepsSettled -> "Scanning…"
                mediaRunning                -> "Continue — finish media in background"
                else                        -> "Enter eOr"
            },
            onClick  = onContinue,
            enabled  = setup.requiredStepsSettled,
            modifier = Modifier.fillMaxWidth()
        )
        if (mediaRunning) {
            Spacer(Modifier.height(8.dp))
            Text(
                "We'll send a notification when the media download finishes.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun SetupRow(label: String, step: SetupStep) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (step) {
                is SetupStep.Pending -> Icon(
                    Icons.Default.RadioButtonUnchecked, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)
                )
                is SetupStep.Running -> CircularProgressIndicator(
                    color = ElectricBlue, strokeWidth = 2.dp, modifier = Modifier.size(20.dp)
                )
                is SetupStep.Done -> Icon(
                    Icons.Default.CheckCircle, contentDescription = "Done",
                    tint = ElectricBlue, modifier = Modifier.size(20.dp)
                )
                is SetupStep.Failed -> Icon(
                    Icons.Default.ErrorOutline, contentDescription = "Failed",
                    tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.titleSmall,
                 color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            when (step) {
                is SetupStep.Running -> if (step.total > 0) Text(
                    "${step.done} / ${step.total}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> {}
            }
        }
        when (step) {
            is SetupStep.Running -> {
                Spacer(Modifier.height(6.dp))
                if (step.total > 0) {
                    LinearProgressIndicator(
                        progress = { step.done.toFloat() / step.total },
                        color    = ElectricBlue,
                        modifier = Modifier.fillMaxWidth().padding(start = 30.dp)
                    )
                } else {
                    LinearProgressIndicator(
                        color    = ElectricBlue,
                        modifier = Modifier.fillMaxWidth().padding(start = 30.dp)
                    )
                }
                if (step.detail.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    DetailText(step.detail)
                }
            }
            is SetupStep.Done   -> DetailText(step.summary)
            is SetupStep.Failed -> DetailText(step.message, MaterialTheme.colorScheme.error)
            else -> {}
        }
    }
}

@Composable
private fun DetailText(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text, style = MaterialTheme.typography.labelSmall, color = color,
        maxLines = 1, modifier = Modifier.padding(start = 30.dp)
    )
}

// ── Shared bits ───────────────────────────────────────────────────────────

@Composable
private fun StepTitle(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.headlineSmall,
         fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    Spacer(Modifier.height(4.dp))
    Text(subtitle, style = MaterialTheme.typography.bodyMedium,
         color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun StepCard(content: @Composable () -> Unit) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(Modifier.padding(18.dp)) { content() }
    }
}

@Composable
private fun FolderRow(path: String, placeholder: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.FolderOpen, contentDescription = null,
             tint = ElectricBlue, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text  = path.ifBlank { placeholder },
            style = MaterialTheme.typography.bodyMedium,
            color = if (path.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            modifier = Modifier.weight(1f)
        )
    }
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
            .clip(RoundedCornerShape(23.dp))
            .background(Brush.horizontalGradient(
                listOf(ElectricBlue.copy(alpha = alpha), NeonPurple.copy(alpha = alpha))
            ))
            .then(if (enabled && !loading) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
        } else {
            Text(text, style = MaterialTheme.typography.labelLarge, color = Color.White,
                 maxLines = 1, modifier = Modifier.padding(horizontal = 12.dp))
        }
    }
}

@Composable
private fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}
