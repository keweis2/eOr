package com.gamelaunch.frontend.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gamelaunch.frontend.ui.component.QrCode
import com.gamelaunch.frontend.ui.theme.ElectricBlue

/**
 * Web Transfer settings: one master toggle that runs a small web server on the device so a computer on
 * the same Wi-Fi can open a page and drag-and-drop games, BIOS, media, background images and settings.
 * When running it shows the connect URL, a scannable QR of it, and the pairing PIN.
 */
@Composable
fun WebTransferSettingsScreen(onBack: () -> Unit) {
    SettingsDetailScaffold(title = "Web Transfer", onBack = onBack) {
        WebTransferSection()
    }
}

@Composable
private fun WebTransferSection() {
    val vm: WebTransferViewModel = hiltViewModel()
    val ui by vm.uiState.collectAsState()

    Text(
        "Turn this on, then open the address below in your computer's browser to send games, BIOS files, " +
            "artwork, background images and settings straight to this device. Both devices must be on the same Wi-Fi.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
    )

    SettingsCard {
        CardSwitchRow(
            label = "Enable Web Transfer",
            checked = ui.enabled,
            onCheckedChange = vm::setEnabled
        )
    }

    if (ui.enabled) {
        SettingsSectionHeader("Connect from your computer")
        SettingsCard {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (ui.running && ui.url.isNotBlank()) {
                    Text(
                        ui.url,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = ElectricBlue
                    )
                    QrCode(content = ui.url, size = 180.dp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "PIN",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            ui.pin,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp,
                            letterSpacing = 6.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        "Enter this PIN in the browser to pair. It changes each time you turn Web Transfer on.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "Starting the server…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (ui.log.isNotEmpty()) {
            SettingsSectionHeader("Activity")
            SettingsCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ui.log.take(8).forEach { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
