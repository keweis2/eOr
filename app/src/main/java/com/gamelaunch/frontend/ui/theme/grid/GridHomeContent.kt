package com.gamelaunch.frontend.ui.theme.grid

import android.os.Build
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.model.GameMedia
import com.gamelaunch.frontend.domain.model.GameSort
import com.gamelaunch.frontend.domain.model.sectionLabel
import com.gamelaunch.frontend.ui.component.ScrollSectionIndicator
import com.gamelaunch.frontend.ui.component.boxArtAspectRatio
import com.gamelaunch.frontend.ui.component.rememberSectionIndicatorState
import com.gamelaunch.frontend.ui.perf.LocalReduceMotion

@Composable
fun GridHomeContent(
    games: List<Game>,
    onGameClick: (Long) -> Unit,
    columns: Int,
    mediaForGames: Map<Long, GameMedia> = emptyMap(),
    focusedGameIndex: Int = -1,
    onPageSizeChange: (Int) -> Unit = {},
    // Drives the fast-scroll section popup ("A", "★", "This Week"…) so the token matches the order
    // the games are actually in. Defaults to alphabetical.
    gameSort: GameSort = GameSort.ALPHABETICAL,
    // When set, every tile uses this fixed aspect ratio instead of its system's box shape — used by
    // mixed-system lists (Recently played) so the grid stays a uniform rectangle.
    uniformAspectRatio: Float? = null,
    // The fast-scroll section popup + hold-scroll blur only belong on the per-system game grid.
    // The home lists (Recently played, Favorites) pass false so neither appears there.
    sectionPopupEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (games.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No games found")
        }
        return
    }

    val gridState = rememberLazyGridState()

    // Report a "page" (whole rows currently on screen × columns) up to the caller so L2/R2 can jump
    // the selection by a screenful at a time.
    LaunchedEffect(gridState, columns) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.size }
            .collect { visible ->
                val rows = (visible / columns).coerceAtLeast(1)
                onPageSizeChange(rows * columns)
            }
    }

    // Scroll so the controller-focused card is always visible. Anchor the focused card to the
    // second visible row (one row of context above it) instead of pinning it to the top row —
    // scrolling only kicks in once focus moves past the second row.
    LaunchedEffect(focusedGameIndex, columns) {
        if (focusedGameIndex in games.indices) {
            gridState.animateScrollToItem((focusedGameIndex - columns).coerceAtLeast(0))
        }
    }

    // ── Scroll section popup (+ blur) ────────────────────────────────────────────────────────
    // Once a hold has been sustained past the show delay, show a big "you are here" section token —
    // the letter/bucket the focused game sorts under — so the user can aim for a part of the list
    // while the cards behind it are still catching up. On the low-power (lite) build we also blur the
    // grid: the RK3568 can't repaint as fast as the cursor moves, so a hold-scroll otherwise shows
    // half-decoded, popping cards — the blur masks that and buys the grid time to settle. A single
    // nudge never raises it; it appears partway into a continuous hold (sooner on lite) and fades out
    // once the cursor stops and the grid catches up. Draw-only overlay: it deliberately does not
    // touch scroll mechanics or input handling (the source of the reverted double-move regression).
    val reduceMotion = LocalReduceMotion.current
    // Arm the popup on VERTICAL movement only: key it on the focused row, so nudging left/right
    // within a row doesn't raise it. When disabled (home lists) pass -1 so it never arms.
    val armRow = if (sectionPopupEnabled && focusedGameIndex >= 0) focusedGameIndex / columns else -1
    val section = rememberSectionIndicatorState(
        focusedIndex = armRow,
        reduceMotion = reduceMotion,
        isScrollInProgress = { gridState.isScrollInProgress }
    )
    val indicatorAlpha = section.alpha

    val blurRadius by animateDpAsState(
        targetValue   = if (reduceMotion && section.active) 4.dp else 0.dp,
        animationSpec = tween(160),
        label = "gridScrollBlur"
    )

    Box(modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns               = GridCells.Fixed(columns),
            state                 = gridState,
            // Extra top padding so a focused top-row card (which scales 1.16× and bobs upward) clears
            // the header instead of being clipped under it.
            contentPadding        = PaddingValues(start = 8.dp, end = 8.dp, top = 30.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement   = Arrangement.spacedBy(8.dp),
            modifier              = Modifier
                .fillMaxSize()
                // Modifier.blur no-ops on API < 31; the effect only runs where RenderEffect exists.
                .then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(blurRadius) else Modifier)
        ) {
            itemsIndexed(games, key = { _, g -> g.id }) { index, game ->
                GridGameCard(
                    game           = game,
                    media          = mediaForGames[game.id],
                    isFocused      = index == focusedGameIndex,
                    aspectRatio    = uniformAspectRatio ?: boxArtAspectRatio(game.platformId),
                    // Pass the stable callback straight through — the card builds its own click
                    // lambda internally, so no per-item allocation happens here.
                    onGameClick    = onGameClick
                )
            }
        }

        // Section token, sitting crisply on top of the (blurred) grid while scrolling.
        if (indicatorAlpha > 0f) {
            ScrollSectionIndicator(
                label    = games.getOrNull(focusedGameIndex)?.sectionLabel(gameSort).orEmpty(),
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer { alpha = indicatorAlpha }
            )
        }
    }
}
