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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gamelaunch.frontend.ui.component.AsyncGameArtwork
import com.gamelaunch.frontend.ui.component.boxArtAspectRatio
import com.gamelaunch.frontend.ui.component.platformDisplayName
import com.gamelaunch.frontend.ui.component.VideoPlayer
import com.gamelaunch.frontend.ui.theme.AmbientBackground
import com.gamelaunch.frontend.ui.theme.ElectricBlue
import com.gamelaunch.frontend.ui.theme.NeonPurple
import com.gamelaunch.frontend.ui.theme.ThemedScreen

private val playGradient = Brush.horizontalGradient(listOf(ElectricBlue, NeonPurple))
private val favoritePink  = Color(0xFFFF6B9D)

@Composable
fun GameDetailScreen(
    onBack: () -> Unit,
    viewModel: GameDetailViewModel = hiltViewModel()
) {
    val state          by viewModel.uiState.collectAsState()
    val focusRequester  = remember { FocusRequester() }
    var showRemoveConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try { focusRequester.requestFocus() } catch (_: Exception) { }
    }

    // Once the game has been removed, leave the detail screen — it no longer exists.
    LaunchedEffect(state.removed) {
        if (state.removed) onBack()
    }

    ThemedScreen {
    // Carry the branded background through to detail, blurred & faded so it stays subtle.
    AmbientBackground(Modifier.fillMaxSize(), patternSubdued = true) {
    Scaffold(containerColor = Color.Transparent) { _ ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ElectricBlue)
            }
            return@Scaffold
        }

        val game  = state.game  ?: return@Scaffold
        val media = state.media

        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        GamepadA, Key.DirectionCenter -> { viewModel.launchGame(); true }
                        GamepadB, Key.Back            -> { onBack(); true }
                        GamepadY                      -> { viewModel.toggleFavorite(); true }
                        else -> false
                    }
                }
        ) {
            Row(Modifier.fillMaxSize().statusBarsPadding()) {

                // ── Left column: back · box art · title · chips · description ──
                Column(
                    modifier = Modifier
                        .weight(0.4f)
                        .fillMaxHeight()
                        .padding(start = 20.dp, top = 16.dp, end = 14.dp, bottom = 20.dp)
                ) {
                    RoundIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        onClick = onBack
                    )
                    Spacer(Modifier.height(14.dp))
                    AsyncGameArtwork(
                        localPath          = media?.boxArtLocalPath,
                        remoteUrl          = media?.boxArtRemoteUrl,
                        contentDescription = game.title,
                        modifier = Modifier
                            .height(178.dp)
                            // Match the grid's per-system box shape so the cover isn't cropped
                            // differently here than in the library.
                            .aspectRatio(boxArtAspectRatio(game.platformId))
                            .shadow(14.dp, RoundedCornerShape(12.dp))
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text  = game.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MetaChip(game.platformId.uppercase())
                        game.releaseYear?.let { MetaChip("$it") }
                        game.genre?.let { MetaChip(it) }
                    }
                    game.rating?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text  = "${"★".repeat(it.toInt().coerceIn(0, 5))} ${"%.1f".format(it)}/5",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFFCC44)
                        )
                    }
                    Spacer(Modifier.height(14.dp))

                    // Scrollable description + info so long synopses don't push the layout.
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        game.description?.let { desc ->
                            Text(
                                "About",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                desc,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(16.dp))
                        }
                        Text(
                            "Info",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        InfoRow("System", platformDisplayName(game.platformId))
                        game.releaseYear?.let { InfoRow("Released", "$it") }
                        InfoRow("Times played", "${game.playCount}")
                        game.lastPlayedMs?.let { InfoRow("Last played", formatDate(it)) }
                        InfoRow("Added", formatDate(game.dateAdded))
                        InfoRow("File", game.romFilename)
                        Spacer(Modifier.height(12.dp))
                    }
                }

                // ── Right column: mute/favorite · video · play ──
                Column(
                    modifier = Modifier
                        .weight(0.6f)
                        .fillMaxHeight()
                        .padding(start = 14.dp, top = 16.dp, end = 20.dp, bottom = 20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                    ) {
                        if (media?.effectiveVideo != null) {
                            RoundIconButton(
                                icon = if (state.videoMuted) Icons.AutoMirrored.Filled.VolumeOff
                                       else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Toggle mute",
                                onClick = viewModel::toggleMute
                            )
                        }
                        RoundIconButton(
                            icon = if (state.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (state.isFavorite) "Remove favorite" else "Add favorite",
                            tint = if (state.isFavorite) favoritePink else null,
                            onClick = viewModel::toggleFavorite
                        )
                        RoundIconButton(
                            icon = Icons.Default.DeleteOutline,
                            contentDescription = "Remove from library",
                            onClick = { showRemoveConfirm = true }
                        )
                    }
                    Spacer(Modifier.height(12.dp))

                    // Video preview (or a screenshot/box-art fallback) fills the column.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
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
                    }
                    Spacer(Modifier.height(14.dp))

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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint     = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Play",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
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

        if (showRemoveConfirm) {
            AlertDialog(
                onDismissRequest = { showRemoveConfirm = false },
                title = { Text("Remove from library?") },
                text  = {
                    Text(
                        "\"${game.title}\" will be removed from your library and won't come back " +
                        "on the next scan. This doesn't delete any files on your device."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showRemoveConfirm = false
                        viewModel.removeFromLibrary()
                    }) { Text("Remove") }
                },
                dismissButton = {
                    TextButton(onClick = { showRemoveConfirm = false }) { Text("Cancel") }
                }
            )
        }
    }
    }
    }
}

@Composable
private fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color? = null
) {
    IconButton(
        onClick  = onClick,
        modifier = Modifier
            .size(40.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint     = tint ?: MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp)
        )
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
            modifier = Modifier.width(104.dp)
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
