package com.gamelaunch.frontend.ui.screen.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gamelaunch.frontend.ui.theme.ElectricBlue

@Composable
fun BackupSettingsScreen(
    onBack: () -> Unit,
    onRunSetupAgain: () -> Unit,
    viewModel: BackupSettingsViewModel = hiltViewModel(),
) {
    val message by viewModel.message.collectAsStateWithLifecycle()
    val backupPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(viewModel::backupTo) }
    val restorePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::restoreFrom) }

    SettingsDetailScaffold(title = "Backup & Setup", onBack = onBack) {
        SettingsSectionHeader("Backup")
        SettingsCard {
            Text(
                "Save your eOr settings to a file — copy it to a new device and choose \"Restore from my " +
                    "old device\" during setup (or Restore below). Passwords, logins and your Locked Mode " +
                    "PIN are never included.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GradientFillButton(
                    text = "Back Up",
                    onClick = { backupPicker.launch("eor-settings.json") },
                    modifier = Modifier.weight(1f)
                )
                GradientOutlineButton(
                    text = "Restore",
                    onClick = { restorePicker.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    modifier = Modifier.weight(1f)
                )
            }
            message?.let { m ->
                Spacer(Modifier.height(8.dp))
                StatusRow(
                    if (m.isError) Icons.Default.Close else Icons.Default.Check,
                    m.text,
                    if (m.isError) MaterialTheme.colorScheme.error else ElectricBlue
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        SettingsSectionHeader("Setup")
        SettingsCard {
            Text(
                "Walk through first-time setup again: find your games, choose artwork and pick a theme. " +
                    "Your library, favorites and play history are kept.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            GradientOutlineButton(
                text = "Run Setup Again",
                onClick = onRunSetupAgain,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
