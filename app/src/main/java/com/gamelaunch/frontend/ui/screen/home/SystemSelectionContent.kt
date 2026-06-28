package com.gamelaunch.frontend.ui.screen.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gamelaunch.frontend.ui.component.platformDisplayName
import com.gamelaunch.frontend.ui.component.platformPadIcon
import com.gamelaunch.frontend.ui.theme.IceWhite
import com.gamelaunch.frontend.ui.theme.glass
import com.gamelaunch.frontend.ui.theme.LayoutMode

@Composable
fun SystemSelectionContent(
    platforms: List<String>,
    counts: Map<String, Int>,
    focusedIndex: Int,
    layoutMode: LayoutMode,
    onSystemClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (platforms.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No systems configured", color = Color.White.copy(alpha = 0.7f))
        }
        return
    }

    if (layoutMode == LayoutMode.CAROUSEL)
        SystemCarousel(platforms, counts, focusedIndex, onSystemClick, modifier)
    else
        SystemGrid(platforms, counts, focusedIndex, onSystemClick, modifier)
}

@Composable
private fun SystemGrid(
    platforms: List<String>,
    counts: Map<String, Int>,
    focusedIndex: Int,
    onSystemClick: (String) -> Unit,
    modifier: Modifier
) {
    val gridState = rememberLazyGridState()
    LaunchedEffect(focusedIndex) {
        if (focusedIndex in platforms.indices) gridState.animateScrollToItem(focusedIndex)
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        state = gridState,
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
    ) {
        gridItemsIndexed(platforms, key = { _, id -> id }) { index, platformId ->
            SystemCard(
                platformId = platformId,
                count = counts[platformId] ?: 0,
                isFocused = index == focusedIndex,
                modifier = Modifier.height(132.dp).fillMaxWidth(),
                onClick = { onSystemClick(platformId) }
            )
        }
    }
}

@Composable
private fun SystemCarousel(
    platforms: List<String>,
    counts: Map<String, Int>,
    focusedIndex: Int,
    onSystemClick: (String) -> Unit,
    modifier: Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(focusedIndex) {
        if (focusedIndex !in platforms.indices) return@LaunchedEffect
        val layout   = listState.layoutInfo
        val viewport = layout.viewportEndOffset - layout.viewportStartOffset
        val itemSize = layout.visibleItemsInfo.firstOrNull { it.index == focusedIndex }?.size
            ?: layout.visibleItemsInfo.firstOrNull()?.size ?: 0
        val center   = ((viewport - itemSize) / 2).coerceAtLeast(0)
        listState.animateScrollToItem(focusedIndex, -center)
    }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            itemsIndexed(platforms, key = { _, id -> id }) { index, platformId ->
                SystemCard(
                    platformId = platformId,
                    count = counts[platformId] ?: 0,
                    isFocused = index == focusedIndex,
                    modifier = Modifier.width(210.dp).height(210.dp),
                    iconSize = 64,
                    onClick = { onSystemClick(platformId) }
                )
            }
        }
    }
}

@Composable
private fun SystemCard(
    platformId: String,
    count: Int,
    isFocused: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    iconSize: Int = 44
) {
    val shape = RoundedCornerShape(24.dp)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .glass(shape, selected = isFocused)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Icon(
            painter = painterResource(platformPadIcon(platformId)),
            contentDescription = null,
            tint = if (isFocused) Color.White else IceWhite.copy(alpha = 0.85f),
            modifier = Modifier.size(iconSize.dp)
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = platformDisplayName(platformId),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (isFocused) Color.White else IceWhite.copy(alpha = 0.92f),
            textAlign = TextAlign.Center,
            maxLines = 2
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "$count game${if (count == 1) "" else "s"}",
            style = MaterialTheme.typography.labelSmall,
            color = if (isFocused) Color.White.copy(alpha = 0.85f) else IceWhite.copy(alpha = 0.5f)
        )
    }
}
