package com.gamelaunch.frontend.ui.screen.scrape

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gamelaunch.frontend.domain.usecase.ScrapeResult
import com.gamelaunch.frontend.ui.theme.ThemedScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScrapeProgressScreen(
    onBack: () -> Unit,
    viewModel: ScrapeViewModel = hiltViewModel()
) {
    val state        by viewModel.uiState.collectAsState()
    val hasSsCreds   by viewModel.hasSsCredentials.collectAsState()

    LaunchedEffect(Unit) {
        if (!state.isRunning && state.batchState == null) {
            viewModel.startScrape()
        }
    }

    ThemedScreen {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scraping Game Data") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (!state.isConfigured) {
            Box(
                Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ScreenScraper credentials not configured.")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onBack) { Text("Go to Settings") }
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // ScreenScraper status banner
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Icon(
                    if (hasSsCreds) Icons.Default.Star else Icons.Default.HourglassEmpty,
                    contentDescription = null,
                    tint = if (hasSsCreds) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (hasSsCreds)
                        "ScreenScraper active — full art, metadata & video"
                    else
                        "No ScreenScraper account — using libretro + LaunchBox fallbacks (add credentials in Settings for best results)",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hasSsCreds) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (hasSsCreds) FontWeight.SemiBold else FontWeight.Normal
                )
            }

            val batch = state.batchState
            if (batch != null) {
                val progress = if (batch.total > 0) batch.completed.toFloat() / batch.total else 0f
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${batch.completed} / ${batch.total}", style = MaterialTheme.typography.bodySmall)
                    Text("✓ ${batch.succeeded}  ? ${batch.notFound}  ✗ ${batch.errors}", style = MaterialTheme.typography.bodySmall)
                }
                if (batch.currentGameTitle.isNotEmpty() && !batch.isFinished) {
                    Spacer(Modifier.height(4.dp))
                    Text("Scraping: ${batch.currentGameTitle}", style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(8.dp))
                if (state.isRunning) {
                    OutlinedButton(onClick = viewModel::cancelScrape, modifier = Modifier.fillMaxWidth()) {
                        Text("Cancel")
                    }
                } else if (batch.storageFull) {
                    Text(
                        "Storage full",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Your device is out of space, so scraping was stopped. Free up some storage and try again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                } else if (batch.isFinished) {
                    Text("Scraping complete!", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                }

                Spacer(Modifier.height(16.dp))

                LazyColumn {
                    items(batch.results.takeLast(50)) { result ->
                        val (icon, label) = when (result) {
                            is ScrapeResult.Success -> Icons.Default.Check to "Scraped: ${result.title}"
                            is ScrapeResult.NotFound -> Icons.Default.Close to "Not found: ${result.romName}"
                            is ScrapeResult.RateLimited -> Icons.Default.HourglassEmpty to "Rate limited, retrying…"
                            is ScrapeResult.Error -> Icons.Default.Close to "Error: ${result.cause.message}"
                        }
                        ListItem(
                            headlineContent = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            leadingContent = { Icon(icon, contentDescription = null) }
                        )
                    }
                }
            }
        }
    }
    }
}
