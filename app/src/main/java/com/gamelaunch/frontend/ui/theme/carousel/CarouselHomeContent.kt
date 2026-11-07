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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.model.GameMedia
import com.gamelaunch.frontend.domain.model.GameSort
import com.gamelaunch.frontend.domain.model.sectionLabel
import com.gamelaunch.frontend.ui.component.AsyncGameArtwork
import com.gamelaunch.frontend.ui.component.ScrollSectionIndicator
import com.gamelaunch.frontend.ui.component.VideoPlayer
import com.gamelaunch.frontend.ui.component.rememberSectionIndicatorState
import com.gamelaunch.frontend.ui.perf.LocalReduceMotion
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CarouselHomeContent(
    games: List<Game>,
    selectedGameMedia: GameMedia?,
    mediaForGames: Map<Long, GameMedia>,
    selectedIndex: Int,
    shouldPlayVideo: Boolean,
    videoMuted: Boolean,
    onGameSelected: (Int) -> Unit,
    onGameClick: (Long) -> Unit,
    onGameLongClick: (Long) -> Unit,
    onMuteToggle: () -> Unit,
    gameSort: GameSort = GameSort.ALPHABETICAL,
    modifier: Modifier = Modifier,
    // When false (dual-screen: artwork lives on the top panel), skip the full-screen video/art
    // backdrop and its darkening gradient here so the bottom panel is just the menu.
    showBackgroundArtwork: Boolean = true
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

    val selectedGame = games.getOrNull(selectedIndex)

    // The carousel is the one game-selection view that scrolls horizontally, so it's the sole place
    // the fast-scroll section popup is allowed to appear on a horizontal scroll.
    val reduceMotion = LocalReduceMotion.current
    val section = rememberSectionIndicatorState(
        focusedIndex = selectedIndex,
        reduceMotion = reduceMotion,
        isScrollInProgress = { listState.isScrollInProgress }
    )

    Box(modifier = modifier) {
        // Background fill: video or stretched box art. On dual-screen devices this whole layer moves
        // to the top panel (see ArtworkPresentation), so we skip it here.
        if (showBackgroundArtwork) {
            if (shouldPlayVideo && selectedGameMedia?.effectiveVideo != null) {
                VideoPlayer(
                    videoPath  = selectedGameMedia.effectiveVideo,
                    shouldPlay = true,
                    isMuted    = videoMuted,
                    modifier   = Modifier.fillMaxSize()
                )
            } else {
                AsyncGameArtwork(
                    localPath = selectedGameMedia?.screenshotLocalPath
                        ?: selectedGameMedia?.backgroundLocalPath
                        ?: selectedGameMedia?.boxArtLocalPath,
                    remoteUrl = selectedGameMedia?.screenshotRemoteUrl
                        ?: selectedGameMedia?.effectiveBackground
                        ?: selectedGameMedia?.boxArtRemoteUrl,
                    contentDescription = null,
                    modifier           = Modifier.fillMaxSize(),
                    packageName        = if (selectedGame?.platformId == "android") selectedGame.romFilename else null
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
        }

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
                        media      = mediaForGames[game.id],
                        isSelected = index == selectedIndex,
                        onLongClick = { onGameSelected(index); onGameLongClick(game.id) },
                        onClick    = {
                            onGameSelected(index)
                            onGameClick(game.id)
                        }
                    )
                }
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No games found", color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
        }

        // Fast-scroll "you are here" section token, centred while flicking through the carousel.
        if (section.alpha > 0f) {
            ScrollSectionIndicator(
                label    = games.getOrNull(selectedIndex)?.sectionLabel(gameSort).orEmpty(),
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer { alpha = section.alpha }
            )
        }
    }
}
