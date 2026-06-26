package com.gamelaunch.frontend.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.gamelaunch.frontend.ui.theme.ElectricBlue
import com.gamelaunch.frontend.ui.theme.NeonPurple

private val platformLabels = mapOf(
    "nes"      to "NES",    "snes"    to "SNES",    "n64"    to "N64",
    "gb"       to "GB",     "gbc"     to "GBC",     "gba"    to "GBA",
    "nds"      to "NDS",    "3ds"     to "3DS",     "switch" to "Switch",
    "ps1"      to "PS1",    "ps2"     to "PS2",     "ps3"    to "PS3",
    "psp"      to "PSP",    "dc"      to "DC",      "saturn" to "Saturn",
    "genesis"  to "GEN",    "gg"      to "GG",      "sms"    to "SMS",
    "pce"      to "PCE",    "neogeo"  to "Neo·Geo", "arcade" to "Arcade",
    "msx"      to "MSX",    "lynx"    to "Lynx",    "atari"  to "Atari"
)

@Composable
fun PlatformTabRow(
    platforms: List<String>,
    selectedPlatform: String?,
    onPlatformSelected: (String) -> Unit
) {
    if (platforms.isEmpty()) return

    val pillShape = RoundedCornerShape(50)
    val gradient  = Brush.horizontalGradient(listOf(ElectricBlue, NeonPurple))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        platforms.forEach { platformId ->
            val isSelected = platformId == selectedPlatform
            val label = platformLabels[platformId] ?: platformId.uppercase()

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(pillShape)
                    .then(
                        if (isSelected)
                            Modifier.background(gradient)
                        else
                            Modifier
                                .background(Color.White.copy(alpha = 0.06f))
                                .border(1.dp, MaterialTheme.colorScheme.outline, pillShape)
                    )
                    .clickable { onPlatformSelected(platformId) }
                    .padding(horizontal = 16.dp, vertical = 7.dp)
            ) {
                Text(
                    text   = label,
                    style  = MaterialTheme.typography.labelMedium,
                    color  = if (isSelected) Color.White
                             else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
