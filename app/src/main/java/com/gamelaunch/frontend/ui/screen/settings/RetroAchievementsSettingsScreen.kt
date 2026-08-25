package com.gamelaunch.frontend.ui.screen.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.provider.Settings
import android.view.Display
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.gamelaunch.frontend.BuildConfig
import com.gamelaunch.frontend.domain.friends.Friend
import com.gamelaunch.frontend.domain.friends.FriendStatus
import com.gamelaunch.frontend.domain.lockedmode.LockedModeState
import com.gamelaunch.frontend.domain.model.EmulatorUpdate
import com.gamelaunch.frontend.domain.sync.EmulatorSyncStatus
import com.gamelaunch.frontend.domain.sync.SyncReadiness
import com.gamelaunch.frontend.domain.usecase.EsdeImportStatus
import com.gamelaunch.frontend.domain.usecase.LbSyncStatus
import com.gamelaunch.frontend.launcher.HomeLauncherHelper
import com.gamelaunch.frontend.systemui.SystemNavigationLockStatus
import com.gamelaunch.frontend.systemui.SystemNavigationSetupProgress
import com.gamelaunch.frontend.ui.component.AppIcon
import com.gamelaunch.frontend.ui.component.QrCode
import com.gamelaunch.frontend.ui.input.GamepadA
import com.gamelaunch.frontend.ui.lockedmode.LockedModeActivationDialog
import com.gamelaunch.frontend.ui.lockedmode.LockedModeDialogStep
import com.gamelaunch.frontend.ui.lockedmode.LockedModeSettingsViewModel
import com.gamelaunch.frontend.ui.lockedmode.PinPadDialog
import com.gamelaunch.frontend.ui.screen.friends.FriendsViewModel
import com.gamelaunch.frontend.ui.theme.CardColorScheme
import com.gamelaunch.frontend.ui.theme.ElectricBlue
import com.gamelaunch.frontend.ui.theme.LayoutMode
import com.gamelaunch.frontend.ui.theme.MonochromeSeeds
import com.gamelaunch.frontend.ui.theme.NeonPurple
import com.gamelaunch.frontend.ui.theme.ThemedScreen
import com.gamelaunch.frontend.util.StorageUtils
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
private fun RetroAchievementsSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    SettingsSectionHeader("RetroAchievements")
    SettingsCard {
        Text(
            "Sign in with your RetroAchievements username and password to see your points and profile.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.raUsername,
            onValueChange = viewModel::updateRaUsername,
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElectricBlue,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.raPassword,
            onValueChange = viewModel::updateRaPassword,
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElectricBlue,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
        Spacer(Modifier.height(10.dp))
        GradientFillButton(
            text = if (state.raLoggedIn) "Update Sign-In" else "Sign In",
            onClick = { viewModel.saveRaCredentials() },
            enabled = !state.raLoggingIn,
            loading = state.raLoggingIn,
            modifier = Modifier.fillMaxWidth()
        )
        state.raLoginResult?.let { msg ->
            val ok = msg.startsWith("Signed in")
            Spacer(Modifier.height(8.dp))
            StatusRow(
                icon = if (ok) Icons.Default.Check else Icons.Default.Close,
                text = msg,
                color = if (ok) ElectricBlue else MaterialTheme.colorScheme.error
            )
        }
        if (state.raLoggedIn) {
            Spacer(Modifier.height(6.dp))
            GradientOutlineButton(
                text = "Sign Out",
                onClick = { viewModel.signOutRa() },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(12.dp))
        CardDivider()
        Spacer(Modifier.height(12.dp))

        Text(
            "Optional: add a Web API Key (retroachievements.org → Settings → Keys) to also see your rank and recently-played games with completion progress.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.raApiKey,
            onValueChange = viewModel::updateRaApiKey,
            label = { Text("Web API Key (optional)") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElectricBlue,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
    }
}

// ── Reusable design system components ─────────────────────────────────────

/** Inline segmented control — connected pill segments, the selected one filled. */

// ── Screen ────────────────────────────────────────────────────────────────

@Composable
fun RetroAchievementsSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel,
) {
    val state by viewModel.uiState.collectAsState()
    SettingsDetailScaffold(title = "RetroAchievements", onBack = onBack) {
        RetroAchievementsSection(state, viewModel)
    }
}
