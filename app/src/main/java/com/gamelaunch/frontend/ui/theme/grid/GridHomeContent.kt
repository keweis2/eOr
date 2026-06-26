package com.gamelaunch.frontend.ui.theme.grid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gamelaunch.frontend.domain.model.Game

@Composable
fun GridHomeContent(
    games: List<Game>,
    onGameClick: (Long) -> Unit,
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

    // Scroll so the controller-focused card is always visible
    LaunchedEffect(focusedGameIndex) {
        if (focusedGameIndex in games.indices) {
            gridState.animateScrollToItem(focusedGameIndex)
        }
    }

    LazyVerticalGrid(
        columns               = GridCells.Adaptive(minSize = 110.dp),
        state                 = gridState,
        contentPadding        = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement   = Arrangement.spacedBy(8.dp),
        modifier              = modifier
    ) {
        items(games, key = { it.id }) { game ->
            val index     = games.indexOf(game)
            val isFocused = index == focusedGameIndex
            GridGameCard(
                game      = game,
                isFocused = isFocused,
                onClick   = { onGameClick(game.id) }
            )
        }
    }
}
