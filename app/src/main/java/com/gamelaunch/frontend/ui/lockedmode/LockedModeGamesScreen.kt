package com.gamelaunch.frontend.ui.lockedmode

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.model.GameMedia
import com.gamelaunch.frontend.domain.platform.PlatformDefinitions
import com.gamelaunch.frontend.ui.component.AsyncGameArtwork
import com.gamelaunch.frontend.ui.input.GamepadA
import com.gamelaunch.frontend.ui.theme.ElectricBlue
import com.gamelaunch.frontend.ui.theme.ThemedScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockedModeGamesScreen(
    onBack: () -> Unit,
    viewModel: LockedModeGamesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val allowedCount = state.games.count { it.isAvailableInLockedMode }
    val gamesByPlatform = remember(state.games) {
        state.games
            .groupBy { it.platformId }
            .entries
            .sortedBy { (platformId, _) ->
                (PlatformDefinitions.byId[platformId]?.displayName ?: platformId).lowercase()
            }
    }

    ThemedScreen {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Allowed games")
                            Text(
                                "$allowedCount of ${state.games.size} games allowed",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
        ) { paddingValues ->
            Box(
                Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
            ) {
                when {
                    state.isLoading -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = ElectricBlue,
                    )
                    state.games.isEmpty() -> Text(
                        text = state.error ?: "No games in your library",
                        color = if (state.error != null) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    else -> Column(Modifier.fillMaxSize()) {
                        state.error?.let {
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 12.dp),
                            )
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            gamesByPlatform.forEach { (platformId, games) ->
                                item(key = "platform-$platformId") {
                                    Text(
                                        text = PlatformDefinitions.byId[platformId]?.displayName
                                            ?: platformId,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = ElectricBlue,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                                    )
                                }
                                items(games, key = { "game-${it.id}" }) { game ->
                                    LockedModeGameRow(
                                        game = game,
                                        media = state.mediaByGameId[game.id],
                                        enabled = game.id !in state.savingGameIds,
                                        onToggle = {
                                            viewModel.setGameAllowed(
                                                game.id,
                                                !game.isAvailableInLockedMode,
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LockedModeGameRow(
    game: Game,
    media: GameMedia?,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (focused) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) ElectricBlue else MaterialTheme.colorScheme.outlineVariant,
                shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                if (enabled && event.type == KeyEventType.KeyDown &&
                    event.key in setOf(GamepadA, Key.Enter, Key.DirectionCenter)
                ) {
                    onToggle()
                    true
                } else false
            }
            .focusable(enabled)
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncGameArtwork(
            localPath = media?.boxArtLocalPath,
            remoteUrl = media?.boxArtRemoteUrl,
            contentDescription = null,
            packageName = if (game.platformId == "android") game.romFilename else null,
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(7.dp)),
        )
        androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                game.title,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Checkbox(
            checked = game.isAvailableInLockedMode,
            onCheckedChange = null,
            enabled = enabled,
        )
    }
}
