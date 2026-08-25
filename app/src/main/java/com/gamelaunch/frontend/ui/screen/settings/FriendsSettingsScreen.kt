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
private fun FriendsToggleSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    SettingsSectionHeader("Friends")
    SettingsCard {
        Text(
            "See a friend's last-played game and RetroAchievements score. Peer-to-peer — no account, " +
                    "nothing stored online. Turning this off stops all sharing and hides the Friends tab on the home screen.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        CardSwitchRow("Enable Friends", state.friendsEnabled, viewModel::setFriendsEnabled)
    }
}

/** Pairing + friends-management, shown as its own Settings tab (only while Friends is enabled). */
@Composable
private fun FriendsSettingsSection() {
    val vm: FriendsViewModel = hiltViewModel()
    val ui by vm.uiState.collectAsState()
    val context = LocalContext.current
    var codeInput by rememberSaveable { mutableStateOf("") }

    if (!ui.engineSupported) {
        SettingsSectionHeader("Friends")
        SettingsCard { Text("Friends needs the sync engine, which isn't available on this device.") }
        return
    }

    // Master switch — always shown here so this tab stays reachable. Turning it off stops all
    // sharing and hides the Home Friends tab, but keeps this tab (and your friends list).
    SettingsSectionHeader("Friends")
    SettingsCard {
        Text(
            "Turning this off stops all sharing and hides the Friends tab on the home screen. Your friends list is kept for when you turn it back on.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        CardSwitchRow("Friends enabled", ui.enabled) { vm.setEnabled(it) }
    }

    // When off, this tab shows only the toggle above — nothing else is active.
    if (!ui.enabled) return

    Spacer(Modifier.height(12.dp))

    // Incoming deep-link confirmation (adding from an eor:// link always needs explicit confirm).
    ui.pendingLink?.let { parsed ->
        SettingsSectionHeader("Friend request")
        SettingsCard {
            Text("Add ${parsed.displayName ?: parsed.deviceId.take(12) + "…"} as a friend?")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GradientFillButton(
                    "Add",
                    onClick = { vm.confirmPendingLink() },
                    modifier = Modifier.weight(1f)
                )
                GradientOutlineButton(
                    "Dismiss",
                    onClick = { vm.dismissPendingLink() },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    // My code card
    SettingsSectionHeader("My friend code")
    SettingsCard {
        var nameInput by rememberSaveable(ui.displayName) { mutableStateOf(ui.displayName) }
        OutlinedTextField(
            value = nameInput,
            onValueChange = { nameInput = it },
            label = { Text("Display name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        GradientOutlineButton(
            "Save name",
            onClick = { vm.saveDisplayName(nameInput) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        when {
            ui.engineStarting -> LoadingStatusRow(
                "Starting the connection engine…",
                MaterialTheme.colorScheme.onSurfaceVariant
            )

            ui.myShareLink != null -> {
                Text(
                    "Share this code with a friend. They add you, you add them back — then you're connected.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                GradientFillButton(
                    "Share my friend code",
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "Add me on eOr: ${ui.myShareLink}")
                        }
                        context.startActivity(Intent.createChooser(send, "Share friend code"))
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            else -> Text("Bringing the engine up…", style = MaterialTheme.typography.labelSmall)
        }
    }

    Spacer(Modifier.height(12.dp))

    // Add a friend
    SettingsSectionHeader("Add a friend")
    SettingsCard {
        OutlinedTextField(
            value = codeInput,
            onValueChange = { codeInput = it },
            label = { Text("Paste a friend code or link") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        GradientFillButton(
            "Add friend",
            onClick = { vm.addFriend(codeInput); codeInput = "" },
            enabled = codeInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        )
        ui.status?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    Spacer(Modifier.height(12.dp))

    // Add nearby (same Wi-Fi) — stop the beacon when this section leaves the screen.
    DisposableEffect(Unit) { onDispose { vm.stopNearby() } }
    SettingsSectionHeader("Add nearby")
    SettingsCard {
        Text(
            "On the same Wi-Fi? Both of you open this, then tap each other to connect — no code needed.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        if (!ui.scanningNearby) {
            GradientFillButton(
                "Find nearby friends",
                onClick = { vm.startNearby() },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            GradientOutlineButton(
                "Stop searching",
                onClick = { vm.stopNearby() },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            if (ui.nearby.isEmpty()) {
                LoadingStatusRow(
                    "Searching for nearby players…",
                    MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                ui.nearby.forEachIndexed { i, peer ->
                    if (i > 0) CardDivider()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { vm.addNearby(peer) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(peer.displayName, style = MaterialTheme.typography.bodyMedium)
                            // The name is unverified (anyone on the Wi-Fi can broadcast it); show the
                            // start of the device id so the user can confirm who they're really adding.
                            Text(
                                "Unverified name · ID ${peer.deviceId.take(7)}…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            "Tap to add",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricBlue
                        )
                    }
                }
            }
        }
    }

    // Incoming requests
    if (ui.incoming.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        SettingsSectionHeader("Friend requests")
        SettingsCard {
            ui.incoming.forEachIndexed { i, f ->
                if (i > 0) CardDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        f.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GradientFillButton("Accept", onClick = { vm.acceptRequest(f.deviceId) })
                        GradientOutlineButton(
                            "Decline",
                            onClick = { vm.declineRequest(f.deviceId) })
                    }
                }
            }
        }
    }

    // My friends
    Spacer(Modifier.height(12.dp))
    SettingsSectionHeader("My friends")
    SettingsCard {
        val all = ui.active + ui.outgoing
        if (all.isEmpty()) {
            Text(
                "No friends yet. Share your code to get started.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            all.forEachIndexed { i, f ->
                if (i > 0) CardDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(f.displayName, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (f.status == FriendStatus.PENDING_OUT) "Waiting for them to add you back" else "Connected",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    GradientOutlineButton("Remove", onClick = { vm.removeFriend(f.deviceId) })
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        GradientOutlineButton(
            "Refresh",
            onClick = { vm.refresh() },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ── Screen ────────────────────────────────────────────────────────────────

@Composable
fun FriendsSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel,
) {
    val state by viewModel.uiState.collectAsState()
    SettingsDetailScaffold(title = "Friends", onBack = onBack) {
        FriendsToggleSection(state, viewModel)
        Spacer(Modifier.height(4.dp))
        FriendsSettingsSection()
    }
}
