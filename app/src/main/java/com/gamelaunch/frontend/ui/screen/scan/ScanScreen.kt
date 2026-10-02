package com.gamelaunch.frontend.ui.screen.scan

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gamelaunch.frontend.ui.theme.ElectricBlue
import com.gamelaunch.frontend.ui.theme.NeonPurple
import com.gamelaunch.frontend.ui.theme.ThemedScreen

@Composable
fun ScanScreen(
    onScanComplete: () -> Unit,
    viewModel: ScanViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    // If games are already in the DB and nothing is loading yet, skip straight to library
    LaunchedEffect(state.existingGameCount, state.isScanning, state.isComplete) {
        if (!state.isScanning && !state.isComplete && state.existingGameCount > 0) {
            onScanComplete()
        }
    }

    // Auto-start only when there are no games at all (fresh install / empty library)
    LaunchedEffect(state.existingGameCount) {
        if (!state.isScanning && !state.isComplete && state.existingGameCount == 0) {
            viewModel.startScan()
        }
    }

    LaunchedEffect(state.isComplete) {
        if (state.isComplete) onScanComplete()
    }

    ThemedScreen {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            val progress = state.progress

            if (state.isScanning) {
                // Scanning in progress
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    tint     = ElectricBlue,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Scanning for ROMs",
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(24.dp))

                if (progress != null && progress.total > 0) {
                    LinearProgressIndicator(
                        progress    = { progress.scanned.toFloat() / progress.total },
                        modifier    = Modifier.fillMaxWidth(),
                        color       = ElectricBlue,
                        trackColor  = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "${progress.scanned} / ${progress.total}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        progress.currentFile,
                        style     = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        maxLines  = 1,
                        color     = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${progress.added} games found",
                        style      = MaterialTheme.typography.labelLarge,
                        color      = ElectricBlue,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    LinearProgressIndicator(
                        modifier   = Modifier.fillMaxWidth(),
                        color      = ElectricBlue,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Searching…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (state.existingGameCount == 0 && !state.isComplete) {
                // Initializing — existingGameCount check still loading
                CircularProgressIndicator(color = ElectricBlue, modifier = Modifier.size(40.dp))
            }

            state.errorMessage?.let { error ->
                Spacer(Modifier.height(16.dp))
                Text(
                    error,
                    color     = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onScanComplete,
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("Go to Library Anyway", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
    }
}
