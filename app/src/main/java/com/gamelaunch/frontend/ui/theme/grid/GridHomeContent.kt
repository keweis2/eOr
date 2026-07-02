package com.gamelaunch.frontend.ui.theme.grid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.model.GameMedia
import kotlinx.coroutines.delay

@Composable
fun GridHomeContent(
    games: List<Game>,
    onGameClick: (Long) -> Unit,
    columns: Int,
    mediaForGames: Map<Long, GameMedia> = emptyMap(),
    focusedGameIndex: Int = -1,
    modifier: Modifier = Modifier
) {
    if (games.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No games found")
        }
        return
    }

    val gridState = rememberLazyGridState()

    // Scroll so the controller-focused card is always visible. Anchor the focused card to the
    // second visible row (one row of context above it) instead of pinning it to the top row —
    // scrolling only kicks in once focus moves past the second row.
    LaunchedEffect(focusedGameIndex, columns) {
        if (focusedGameIndex in games.indices) {
            gridState.animateScrollToItem((focusedGameIndex - columns).coerceAtLeast(0))
        }
    }

    // The entrance animation should fire once, when we load into a system — not every time a
    // card is recycled into view on scroll. Keep a short window open right after the games list
    // changes; cards composed during it animate, cards composed later appear instantly.
    var entranceWindowOpen by remember(games) { mutableStateOf(true) }
    LaunchedEffect(games) {
        entranceWindowOpen = true
        delay(900L)   // long enough for the staggered rise of the initially-visible cards
        entranceWindowOpen = false
    }

    LazyVerticalGrid(
        columns               = GridCells.Fixed(columns),
        state                 = gridState,
        contentPadding        = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement   = Arrangement.spacedBy(8.dp),
        modifier              = modifier
    ) {
        itemsIndexed(games, key = { _, g -> g.id }) { index, game ->
            GridGameCard(
                game           = game,
                index          = index,
                media          = mediaForGames[game.id],
                isFocused      = index == focusedGameIndex,
                animateOnEntry = entranceWindowOpen,
                onClick        = { onGameClick(game.id) }
            )
        }
    }
}
