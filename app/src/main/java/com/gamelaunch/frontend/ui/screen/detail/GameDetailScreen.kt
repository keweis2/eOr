package com.gamelaunch.frontend.ui.screen.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import com.gamelaunch.frontend.ui.input.GamepadA
import com.gamelaunch.frontend.ui.input.GamepadB
import com.gamelaunch.frontend.ui.input.GamepadY
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gamelaunch.frontend.ui.component.AsyncGameArtwork
import com.gamelaunch.frontend.ui.component.platformDisplayName
import com.gamelaunch.frontend.ui.component.VideoPlayer
import com.gamelaunch.frontend.ui.theme.ElectricBlue
import com.gamelaunch.frontend.ui.theme.NeonPurple
import com.gamelaunch.frontend.ui.theme.ThemedScreen

private val playGradient = Brush.horizontalGradient(listOf(ElectricBlue, NeonPurple))
private val glassColor   = Color.White.copy(alpha = 0.14f)

@Composable
fun GameDetailScreen(
    onBack: () -> Unit,
    viewModel: GameDetailViewModel = hiltViewModel()
) {
    val state          by viewModel.uiState.collectAsState()
    val focusRequester  = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try { focusRequester.requestFocus() } catch (_: Exception) { }
    }

    // Follow the user's light/dark choice for this screen's Material components.
    ThemedScreen {
    Scaffold(containerColor = MaterialTheme.colorScheme.surface) { _ ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ElectricBlue)
            }
            return@Scaffold
        }

        val game  = state.game  ?: return@Scaffold
        val media = state.media

        Column(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        // A = launch
                        GamepadA, Key.DirectionCenter -> { viewModel.launchGame(); true }
                        // B / system back = return to the game grid
                        GamepadB, Key.Back -> { onBack(); true }
                        // Y = toggle favourite
                        GamepadY -> { viewModel.toggleFavorite(); true }
                        else -> false
                    }
                }
                .verticalScroll(rememberScrollState())
        ) {
            // ── Full-bleed hero ────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                if (media?.effectiveVideo != null) {
                    VideoPlayer(
                        videoPath  = media.effectiveVideo,
                        shouldPlay = state.shouldPlayVideo,
                        isMuted    = state.videoMuted,
                        modifier   = Modifier.fillMaxSize()
                    )
                } else {
                    AsyncGameArtwork(
                        localPath          = media?.screenshotLocalPath ?: media?.boxArtLocalPath,
                        remoteUrl          = media?.screenshotRemoteUrl ?: media?.boxArtRemoteUrl,
                        contentDescription = game.title,
                        modifier           = Modifier.fillMaxSize()
                    )
                }

                // Bottom fade into card below
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                1f to MaterialTheme.colorScheme.surface
                            )
                        )
                )

                // Floating back button
                IconButton(
                    onClick  = onBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(start = 14.dp, top = 20.dp, end = 14.dp, bottom = 14.dp)
                        .size(40.dp)
                        .background(glassColor, CircleShape)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint     = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Mute + favorite buttons — top right
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(start = 14.dp, top = 20.dp, end = 14.dp, bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (media?.effectiveVideo != null) {
                        IconButton(
                            onClick  = viewModel::toggleMute,
                            modifier = Modifier
                                .size(40.dp)
                                .background(glassColor, CircleShape)
                        ) {
                            Icon(
                                imageVector = if (state.videoMuted)
                                    Icons.AutoMirrored.Filled.VolumeOff
                                else
                                    Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = null,
                                tint     = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick  = viewModel::toggleFavorite,
                        modifier = Modifier
                            .size(40.dp)
                            .background(glassColor, CircleShape)
                    ) {
                        Icon(
                            imageVector = if (state.isFavorite) Icons.Default.Favorite
                                          else Icons.Default.FavoriteBorder,
                            contentDescription = if (state.isFavorite) "Remove favorite"
                                                 else "Add favorite",
                            tint     = if (state.isFavorite) Color(0xFFFF6B9D) else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // ── Info card (pulls up over hero fade) ────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-20).dp),
                shape  = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {

                    // Title + box art side-by-side
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Mini box art
                        AsyncGameArtwork(
                            localPath          = media?.boxArtLocalPath,
                            remoteUrl          = media?.boxArtRemoteUrl,
                            contentDescription = game.title,
                            modifier = Modifier
                                .width(80.dp)
                                .aspectRatio(0.75f)
                                .shadow(12.dp, RoundedCornerShape(8.dp))
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text  = game.title,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(6.dp))
                            // Metadata chips
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                MetaChip(game.platformId.uppercase())
                                game.releaseYear?.let { MetaChip("$it") }
                                game.genre?.let { MetaChip(it) }
                            }
                            game.rating?.let {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text  = "${"★".repeat(it.toInt().coerceIn(0, 5))} ${"%.1f".format(it)}/5",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFFFCC44)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Gradient PLAY button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .clip(RoundedCornerShape(27.dp))
                            .background(playGradient)
                            .clickable(onClick = viewModel::launchGame),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment      = Alignment.CenterVertically,
                            horizontalArrangement  = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint     = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text       = "Play",
                                color      = Color.White,
                                fontWeight = FontWeight.Bold,
                                style      = MaterialTheme.typography.titleMedium
                            )
                        }
                    }

                    // Description
                    game.description?.let { desc ->
                        Spacer(Modifier.height(20.dp))
                        Text(
                            text  = "About",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text  = desc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Info
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text  = "Info",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(6.dp))
                    InfoRow("System", platformDisplayName(game.platformId))
                    game.genre?.let { InfoRow("Genre", it) }
                    game.releaseYear?.let { InfoRow("Released", "$it") }
                    InfoRow("Times played", "${game.playCount}")
                    game.lastPlayedMs?.let { InfoRow("Last played", formatDate(it)) }
                    InfoRow("Added", formatDate(game.dateAdded))
                    InfoRow("File", game.romFilename)

                    Spacer(Modifier.height(32.dp))
                }
            }
        }

        state.launchError?.let { error ->
            AlertDialog(
                onDismissRequest = viewModel::dismissError,
                title = { Text("Cannot Launch Game") },
                text  = { Text(error) },
                confirmButton = {
                    TextButton(onClick = viewModel::dismissError) { Text("OK") }
                }
            )
        }
    }
    }
}

@Composable
private fun MetaChip(label: String) {
    Text(
        text     = label,
        style    = MaterialTheme.typography.labelSmall,
        color    = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text     = label,
            style    = MaterialTheme.typography.bodyMedium,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(108.dp)
        )
        Text(
            text     = value,
            style    = MaterialTheme.typography.bodyMedium,
            color    = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

private val infoDateFormat = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
private fun formatDate(epochMs: Long): String = infoDateFormat.format(java.util.Date(epochMs))
