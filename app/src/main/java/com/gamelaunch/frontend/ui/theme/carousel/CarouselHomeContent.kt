package com.gamelaunch.frontend.ui.theme.carousel

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.dp
import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.model.GameMedia
import com.gamelaunch.frontend.ui.component.AsyncGameArtwork
import com.gamelaunch.frontend.ui.component.VideoPlayer
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CarouselHomeContent(
    games: List<Game>,
    selectedGameMedia: GameMedia?,
    selectedIndex: Int,
    shouldPlayVideo: Boolean,
    videoMuted: Boolean,
    onGameSelected: (Int) -> Unit,
    onGameClick: (Long) -> Unit,
    onMuteToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState   = rememberLazyListState()
    val snapBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                if (index != selectedIndex) onGameSelected(index)
            }
    }

    LaunchedEffect(selectedIndex) {
        if (listState.firstVisibleItemIndex != selectedIndex) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    Box(modifier = modifier) {
        // Background fill: video or stretched box art
        if (shouldPlayVideo && selectedGameMedia?.effectiveVideo != null) {
            VideoPlayer(
                videoPath  = selectedGameMedia.effectiveVideo,
                shouldPlay = true,
                isMuted    = videoMuted,
                modifier   = Modifier.fillMaxSize()
            )
        } else {
            AsyncGameArtwork(
                localPath          = selectedGameMedia?.backgroundLocalPath
                    ?: selectedGameMedia?.boxArtLocalPath,
                remoteUrl          = selectedGameMedia?.effectiveBackground
                    ?: selectedGameMedia?.boxArtRemoteUrl,
                contentDescription = null,
                modifier           = Modifier.fillMaxSize()
            )
        }

        // Deep gradient — near-black at bottom, subtle tint at top
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.15f),
                        0.45f to Color.Black.copy(alpha = 0.30f),
                        1.0f to Color.Black.copy(alpha = 0.90f)
                    )
                )
        )

        // Title + genre block
        games.getOrNull(selectedIndex)?.let { game ->
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 196.dp, start = 24.dp, end = 72.dp)
            ) {
                Text(
                    text  = game.title,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        shadow = Shadow(
                            color      = Color.Black,
                            offset     = Offset(0f, 3f),
                            blurRadius = 12f
                        )
                    ),
                    color = Color.White
                )
                game.genre?.let { genre ->
                    Text(
                        text  = genre,
                        style = MaterialTheme.typography.labelMedium.copy(
                            shadow = Shadow(Color.Black, Offset(0f, 2f), 6f)
                        ),
                        color = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // Mute toggle — glass circle pill
        if (selectedGameMedia?.effectiveVideo != null) {
            IconButton(
                onClick  = onMuteToggle,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 200.dp)
                    .size(40.dp)
                    .background(Color.White.copy(alpha = 0.15f), CircleShape)
            ) {
                Icon(
                    imageVector    = if (videoMuted) Icons.AutoMirrored.Filled.VolumeOff
                                     else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = if (videoMuted) "Unmute" else "Mute",
                    tint               = Color.White,
                    modifier           = Modifier.size(20.dp)
                )
            }
        }

        // Game card carousel
        if (games.isNotEmpty()) {
            LazyRow(
                state            = listState,
                flingBehavior    = snapBehavior,
                contentPadding   = PaddingValues(horizontal = 36.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp)
                    .fillMaxWidth()
            ) {
                itemsIndexed(games) { index, game ->
                    CarouselGameCard(
                        game       = game,
                        media      = if (index == selectedIndex) selectedGameMedia else null,
                        isSelected = index == selectedIndex,
                        onClick    = {
                            if (index == selectedIndex) onGameClick(game.id)
                            else onGameSelected(index)
                        }
                    )
                }
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No games found", color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
