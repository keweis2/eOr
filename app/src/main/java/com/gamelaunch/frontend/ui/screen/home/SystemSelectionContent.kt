package com.gamelaunch.frontend.ui.screen.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import com.gamelaunch.frontend.ui.component.platformIcon
import com.gamelaunch.frontend.ui.component.platformPadIcon
import com.gamelaunch.frontend.ui.theme.BounceDurationMs
import com.gamelaunch.frontend.ui.theme.BounceEasing
import com.gamelaunch.frontend.ui.theme.IceWhite
import com.gamelaunch.frontend.ui.theme.LocalDarkMode
import com.gamelaunch.frontend.ui.theme.SteelGray
import com.gamelaunch.frontend.ui.theme.TileSub
import com.gamelaunch.frontend.ui.theme.TileText
import com.gamelaunch.frontend.ui.theme.glassTile
import com.gamelaunch.frontend.ui.theme.tileColor

@Composable
fun SystemSelectionContent(
    platforms: List<String>,
    counts: Map<String, Int>,
    focusedIndex: Int,
    previewArt: List<String> = emptyList(),
    onSystemFocused: (String) -> Unit = {},
    onSystemClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (platforms.isEmpty()) {
        val darkMode = LocalDarkMode.current
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No systems configured", color = if (darkMode) SteelGray else TileSub)
        }
        return
    }
    SystemCarousel(platforms, counts, focusedIndex, previewArt, onSystemFocused, onSystemClick, modifier)
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

    BoxWithConstraints(modifier.fillMaxSize()) {
        // Adapt to the screen's aspect ratio: derive the preview cover size and the carousel
        // card size from the actual space available, so the layout holds up on tall (3:4),
        // standard (4:3), square (1:1) and wide (16:9) displays instead of overflowing.
        val maxW = maxWidth
        val maxH = maxHeight
        val isWide = maxW > maxH

        // Carousel cards: a fraction of the shorter dimension, clamped to sane bounds.
        val cardSize = (minOf(maxW, maxH) * 0.34f).coerceIn(108.dp, 150.dp)
        // Preview covers: fill most of the band above the carousel, leaving room for the cards.
        val previewBandH = maxH - cardSize - 44.dp
        val coverHeight = (previewBandH * 0.94f).coerceIn(120.dp, 280.dp)
        // Fan spread scales a little with horizontal room — wide enough that the larger covers
        // still reveal plenty of each, without spreading across the whole screen.
        val spreadDp = (maxW.value * 0.115f).coerceIn(46f, 92f)

        Column(Modifier.fillMaxSize()) {

            // ── Preview (top): covers rise from the bottom and fan out, centre largest ──
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val covers = previewArt.take(5)
                val n = covers.size
                // one progress per cover-set; cards rise + fan as it goes 0 -> 1
                val progress = remember { Animatable(1f) }
                LaunchedEffect(previewArt) {
                    progress.snapTo(0f)
                    progress.animateTo(1f, tween(durationMillis = 640, easing = FastOutSlowInEasing))
                }
                // small hand-placed jitter so the fan doesn't look mechanical
                val jitter = listOf(-2.2f, 1.6f, -0.7f, 1.9f, -1.4f)
                covers.forEachIndexed { i, art ->
                    val rel = i - (n - 1) / 2f
                    val absRel = kotlin.math.abs(rel)
                    // stagger: centre leads, outer cards follow as it fans out
                    val stagger = 0.13f
                    val maxDelay = ((n - 1) / 2f) * stagger
                    val cp = ((progress.value - absRel * stagger) / (1f - maxDelay)).coerceIn(0f, 1f)
                    val perspective = 1f - absRel * 0.08f   // centre card biggest -> depth
                    Box(
                        modifier = Modifier
                            .zIndex(n - absRel)
                            .graphicsLayer {
                                transformOrigin = TransformOrigin(0.5f, 1.3f)
                                // fan opens out (rotation + horizontal spread) as the card rises
                                rotationZ = (rel * 7f + jitter[i % jitter.size]) * cp
                                translationX = rel * spreadDp.dp.toPx() * cp
                                // rise up from below to a slightly-lifted resting arc
                                val restY = (absRel * 10f - 12f).dp.toPx()
                                val startY = 130.dp.toPx()
                                translationY = startY + (restY - startY) * cp
                                scaleX = perspective
                                scaleY = perspective
                                alpha = (cp * 1.5f).coerceAtMost(1f)
                            }
                    ) {
                        AsyncGameArtwork(
                            localPath = art,
                            remoteUrl = art,
                            contentDescription = null,
                            modifier = Modifier
                                .height(coverHeight)
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
                contentPadding = PaddingValues(horizontal = if (isWide) 40.dp else 24.dp),
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
                        modifier = Modifier.width(cardSize).height(cardSize),
                        iconSize = 38,
                        onClick = { onSystemClick(platformId) }
                    )
                }
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
        targetValue = if (isFocused) 1.16f else 1f,
        animationSpec = tween(durationMillis = BounceDurationMs, easing = BounceEasing),
        label = "systemTileScale"
    )
    // A gentle, never-ending idle so the focused card feels alive — a soft tilt, a slow vertical
    // bob and a faint "breathing" pulse, each on its own off-beat period so the motion drifts
    // organically instead of ticking like a metronome.
    val idle = rememberInfiniteTransition(label = "systemIdle")
    val tilt by idle.animateFloat(
        -1f, 1f, infiniteRepeatable(tween(2300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "systemTilt"
    )
    val bob by idle.animateFloat(
        -1f, 1f, infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "systemBob"
    )
    val breath by idle.animateFloat(
        -1f, 1f, infiniteRepeatable(tween(2900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "systemBreath"
    )
    val darkMode = LocalDarkMode.current
    val textPrimary = if (darkMode) IceWhite else TileText
    val textSecondary = if (darkMode) SteelGray else TileSub

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .zIndex(if (isFocused) 1f else 0f)
            .graphicsLayer {
                val pulse = if (isFocused) breath * 0.012f else 0f
                scaleX = scale + pulse
                scaleY = scale + pulse
                rotationZ = if (isFocused) tilt * 0.9f else 0f
                translationY = if (isFocused) bob * 2.5.dp.toPx() else 0f
            }
            .glassTile(shape, color = color, selected = isFocused)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        val illustration = platformIcon(platformId)
        if (illustration != null) {
            Image(
                painter = painterResource(illustration),
                contentDescription = null,
                modifier = Modifier.size((iconSize + 12).dp)
            )
        } else {
            Icon(
                painter = painterResource(platformPadIcon(platformId)),
                contentDescription = null,
                tint = textPrimary,
                modifier = Modifier.size(iconSize.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = platformDisplayName(platformId),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "$count game${if (count == 1) "" else "s"}",
            style = MaterialTheme.typography.labelSmall,
            color = textSecondary
        )
    }
}
