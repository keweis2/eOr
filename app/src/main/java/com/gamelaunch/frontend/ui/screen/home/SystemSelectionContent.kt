package com.gamelaunch.frontend.ui.screen.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gamelaunch.frontend.ui.component.AsyncGameArtwork
import com.gamelaunch.frontend.ui.component.platformDisplayName
import com.gamelaunch.frontend.ui.component.platformPadIcon
import com.gamelaunch.frontend.ui.theme.BounceDurationMs
import com.gamelaunch.frontend.ui.theme.BounceEasing
import com.gamelaunch.frontend.ui.theme.TileSub
import com.gamelaunch.frontend.ui.theme.TileText
import com.gamelaunch.frontend.ui.theme.glassTile
import com.gamelaunch.frontend.ui.theme.tileColor
import com.gamelaunch.frontend.ui.theme.LayoutMode

@Composable
fun SystemSelectionContent(
    platforms: List<String>,
    counts: Map<String, Int>,
    focusedIndex: Int,
    layoutMode: LayoutMode,
    previewArt: List<String> = emptyList(),
    onSystemFocused: (String) -> Unit = {},
    onSystemClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (platforms.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No systems configured", color = TileSub)
        }
        return
    }

    if (layoutMode == LayoutMode.CAROUSEL)
        SystemCarousel(platforms, counts, focusedIndex, previewArt, onSystemFocused, onSystemClick, modifier)
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
                color = tileColor(index),
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
    previewArt: List<String>,
    onSystemFocused: (String) -> Unit,
    onSystemClick: (String) -> Unit,
    modifier: Modifier
) {
    val focused = platforms.getOrNull(focusedIndex)

    // Load the preview covers for whichever system is focused.
    LaunchedEffect(focusedIndex) { focused?.let(onSystemFocused) }

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

    Column(modifier.fillMaxSize()) {

        // ── Preview (top): an organic fan of box art that slides in on change ──
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val covers = previewArt.take(5)
            // subtle slide + fade whenever the focused system changes
            val slide = remember { Animatable(0f) }
            LaunchedEffect(focused) {
                slide.snapTo(1f)
                slide.animateTo(0f, tween(durationMillis = 360, easing = FastOutSlowInEasing))
            }
            // small hand-placed jitter so the fan doesn't look mechanical
            val jitter = listOf(-2.2f, 1.6f, -0.7f, 1.9f, -1.4f)
            covers.forEachIndexed { i, art ->
                val n = covers.size
                val rel = i - (n - 1) / 2f
                Box(
                    modifier = Modifier
                        .zIndex(n - kotlin.math.abs(rel))
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0.5f, 1.4f)
                            rotationZ = rel * 9f + jitter[i % jitter.size]
                            translationX = rel * 86.dp.toPx() + slide.value * 60f
                            translationY = (kotlin.math.abs(rel) * 11f).dp.toPx()
                            alpha = 1f - slide.value * 0.85f
                        }
                ) {
                    AsyncGameArtwork(
                        localPath = art,
                        remoteUrl = art,
                        contentDescription = null,
                        modifier = Modifier
                            .height(184.dp)
                            .aspectRatio(0.72f)
                            .shadow(14.dp, RoundedCornerShape(10.dp))
                            .clip(RoundedCornerShape(10.dp))
                    )
                }
            }
        }

        // ── System carousel (bottom) — smaller cards ──
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 22.dp)
        ) {
            itemsIndexed(platforms, key = { _, id -> id }) { index, platformId ->
                SystemCard(
                    platformId = platformId,
                    count = counts[platformId] ?: 0,
                    isFocused = index == focusedIndex,
                    color = tileColor(index),
                    modifier = Modifier.width(132.dp).height(132.dp),
                    iconSize = 38,
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
    color: Color,
    modifier: Modifier,
    onClick: () -> Unit,
    iconSize: Int = 44
) {
    val shape = RoundedCornerShape(24.dp)
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.07f else 1f,
        animationSpec = tween(durationMillis = BounceDurationMs, easing = BounceEasing),
        label = "systemTileScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .glassTile(shape, color = color, selected = isFocused)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Icon(
            painter = painterResource(platformPadIcon(platformId)),
            contentDescription = null,
            tint = TileText,
            modifier = Modifier.size(iconSize.dp)
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = platformDisplayName(platformId),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = TileText,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "$count game${if (count == 1) "" else "s"}",
            style = MaterialTheme.typography.labelSmall,
            color = TileSub
        )
    }
}
