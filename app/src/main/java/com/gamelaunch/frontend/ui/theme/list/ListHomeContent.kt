package com.gamelaunch.frontend.ui.theme.list

import android.os.Build
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.model.GameMedia
import com.gamelaunch.frontend.domain.model.GameSort
import com.gamelaunch.frontend.domain.model.sectionLabel
import com.gamelaunch.frontend.ui.component.AsyncGameArtwork
import com.gamelaunch.frontend.ui.component.ScrollSectionIndicator
import com.gamelaunch.frontend.ui.component.boxArtAspectRatio
import com.gamelaunch.frontend.ui.component.rememberSectionIndicatorState
import com.gamelaunch.frontend.ui.perf.LocalReduceMotion
import com.gamelaunch.frontend.ui.theme.ElectricBlue
import com.gamelaunch.frontend.ui.theme.IceWhite
import com.gamelaunch.frontend.ui.theme.LocalDarkMode
import com.gamelaunch.frontend.ui.theme.SteelGray
import com.gamelaunch.frontend.ui.theme.TileSub
import com.gamelaunch.frontend.ui.theme.TileText

/**
 * Vertical list layout: a scrollable column of game titles on the left, with the box art of the
 * currently-focused game shown large on the right. Selection is controller-driven (Up/Down move the
 * focus, A launches); focus is owned by HomeScreen and passed in as [focusedGameIndex].
 */
@Composable
fun ListHomeContent(
    games: List<Game>,
    mediaForGames: Map<Long, GameMedia>,
    focusedGameIndex: Int,
    onGameClick: (Long) -> Unit,
    onGameFocused: (Int) -> Unit,
    // Report how many rows are on screen so L1/R1 can page-jump by a screenful.
    onPageSizeChange: (Int) -> Unit = {},
    // Drives the fast-scroll section popup token so it matches the current game order.
    gameSort: GameSort = GameSort.ALPHABETICAL,
    modifier: Modifier = Modifier
) {
    if (games.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No games found")
        }
        return
    }

    val darkMode = LocalDarkMode.current
    val textPrimary = if (darkMode) IceWhite else TileText
    val textSecondary = if (darkMode) SteelGray else TileSub

    val listState = rememberLazyListState()

    // Keep the focused row on screen, anchored one row down for a bit of context above it.
    LaunchedEffect(focusedGameIndex) {
        if (focusedGameIndex in games.indices) {
            listState.animateScrollToItem((focusedGameIndex - 1).coerceAtLeast(0))
        }
    }

    // Report a "page" (rows currently visible) so paging jumps a screenful at a time.
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.size }
            .collect { visible -> onPageSizeChange(visible.coerceAtLeast(1)) }
    }

    val focusedGame = games.getOrNull(focusedGameIndex)

    // Debounce the right-hand box art: while the cursor is moving, hold the last cover and only load
    // the focused game's art once scrolling settles (~160ms of stillness). Decoding a fresh cover on
    // every step otherwise competes with the scroll on the main thread and makes held-scrolling crawl
    // — the same reason the dual-screen top panel holds its marquee while fast-scrolling.
    var artIndex by remember { mutableIntStateOf(focusedGameIndex) }
    LaunchedEffect(focusedGameIndex) {
        delay(160)
        artIndex = focusedGameIndex
    }
    val artGame = games.getOrNull(artIndex)

    // Fast-scroll "you are here" section token, shared with the grid layout.
    val reduceMotion = LocalReduceMotion.current
    val section = rememberSectionIndicatorState(
        focusedIndex = focusedGameIndex,
        reduceMotion = reduceMotion,
        isScrollInProgress = { listState.isScrollInProgress }
    )
    // On the low-power build, blur the list while fast-scrolling so the right-hand box art (which
    // reloads on every focus step) doesn't visibly pop — the crisp section token stays on top.
    val blurRadius by animateDpAsState(
        targetValue   = if (reduceMotion && section.active) 4.dp else 0.dp,
        animationSpec = tween(160),
        label = "listScrollBlur"
    )

    Box(modifier.fillMaxSize()) {
    Row(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            // Modifier.blur no-ops on API < 31; only run it where RenderEffect exists.
            .then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(blurRadius) else Modifier)
    ) {
        // ── Left: scrollable title list ─────────────────────────────────
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(0.55f)
                .fillMaxHeight(),
            contentPadding = PaddingValues(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            itemsIndexed(games, key = { _, g -> g.id }) { index, game ->
                val focused = index == focusedGameIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .then(
                            if (focused) Modifier.background(ElectricBlue.copy(alpha = 0.20f))
                            else Modifier
                        )
                        .clickable {
                            if (focused) onGameClick(game.id) else onGameFocused(index)
                        }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = game.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (focused) textPrimary else textPrimary.copy(alpha = 0.82f),
                        fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // ── Right: box art of the focused game ──────────────────────────
        Box(
            modifier = Modifier
                .weight(0.45f)
                .fillMaxHeight()
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            artGame?.let { game ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().wrapContentHeight()
                ) {
                    val media = mediaForGames[game.id]
                    AsyncGameArtwork(
                        localPath = media?.boxArtLocalPath,
                        remoteUrl = media?.boxArtRemoteUrl,
                        contentDescription = game.title,
                        modifier = Modifier
                            .fillMaxWidth(0.86f)
                            .aspectRatio(boxArtAspectRatio(game.platformId))
                            .clip(RoundedCornerShape(12.dp)),
                        packageName = if (game.platformId == "android") game.romFilename else null
                    )
                    game.genre?.let { genre ->
                        Text(
                            text = genre,
                            style = MaterialTheme.typography.labelMedium,
                            color = textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }
            }
        }
    }

        // Section token overlay, centred over the list while fast-scrolling.
        if (section.alpha > 0f) {
            ScrollSectionIndicator(
                label    = focusedGame?.sectionLabel(gameSort).orEmpty(),
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer { alpha = section.alpha }
            )
        }
    }
}
