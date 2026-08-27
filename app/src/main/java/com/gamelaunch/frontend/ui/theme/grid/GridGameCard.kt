package com.gamelaunch.frontend.ui.theme.grid

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.model.GameMedia
import com.gamelaunch.frontend.ui.component.AsyncGameArtwork
import com.gamelaunch.frontend.ui.component.boxArtAspectRatio
import com.gamelaunch.frontend.ui.theme.BounceDurationMs
import com.gamelaunch.frontend.ui.theme.BounceEasing
import com.gamelaunch.frontend.ui.perf.LocalReduceMotion
import com.gamelaunch.frontend.ui.perf.rememberSelectionScale
import com.gamelaunch.frontend.ui.theme.ElectricBlue
import com.gamelaunch.frontend.ui.theme.NeonPurple

@Composable
fun GridGameCard(
    game: Game,
    media: GameMedia? = null,
    isFocused: Boolean = false,
    // Container shape. Defaults to the system's real box proportions; callers showing a mixed-system
    // list (e.g. Recently played) pass a fixed value to keep every tile the same rectangle.
    aspectRatio: Float = boxArtAspectRatio(game.platformId),
    // Stable click callback taking the game id. Kept as a (Long) -> Unit — rather than a pre-built
    // () -> Unit — so the parent doesn't allocate a fresh lambda per item per recomposition, which
    // would make this card un-skippable and recompose every visible tile on any media-map change.
    onGameClick: (Long) -> Unit,
    // True while the grid is actively scrolling: hold off starting a cover load for a not-yet-loaded
    // tile so fast scrolls don't flood the decoder with covers that are only passing through.
    pauseArtLoad: Boolean = false
) {
    val shape = RoundedCornerShape(12.dp)

    // No staggered entrance: the per-card rise gated each tile's alpha behind an index-based delay
    // (delay(index * 28)), so covers faded in one-at-a-time top-to-bottom — which on the full build
    // read as the grid "loading in slowly", tile by tile. Cards (and their art) now appear together
    // as soon as they compose, matching how the lite build already behaves. The focus pop and idle
    // motion below are untouched.

    // Focused card pops with the same bounce-scale as the console cards — but snaps instantly under
    // reduced (lite build / performance mode) instead of animating every d-pad step.
    val scale = rememberSelectionScale(
        active = isFocused,
        activeScale = 1.16f,
        fullSpec = tween(durationMillis = BounceDurationMs, easing = BounceEasing),
        label = "gridGameScale"
    )
    // The perpetual "hover float" (idle tilt/bob/breath on the focused card) was removed from the
    // grid: it ran the main thread every frame while adding nothing to selection clarity, and Coil
    // applies each decoded cover on that same thread — so the float competed with box-art painting and
    // covers trickled in one-at-a-time while browsing. The focused card now just pops in scale (below)
    // and carries the glow/border. reduceMotion is still read for the glow shadow.
    val reduceMotion = LocalReduceMotion.current

    Box(
        modifier = Modifier
            .zIndex(if (isFocused) 1f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                when {
                    // Reduced: the colored elevation shadow is GPU-costly per frame; drop it entirely
                    // and let the ElectricBlue border below carry the selection affordance.
                    isFocused && reduceMotion -> Modifier
                    isFocused -> Modifier.shadow(
                        28.dp,
                        shape,
                        spotColor = ElectricBlue,
                        ambientColor = NeonPurple.copy(alpha = 0.5f)
                    )
                    else -> Modifier.shadow(8.dp, shape)
                }
            )
            .clip(shape)
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .then(if (isFocused) Modifier.border(2.dp, ElectricBlue, shape) else Modifier)
            .clickable { onGameClick(game.id) }
    ) {
        AsyncGameArtwork(
            localPath          = media?.boxArtLocalPath,
            remoteUrl          = media?.boxArtRemoteUrl,
            contentDescription = game.title,
            modifier           = Modifier.fillMaxSize(),
            packageName        = if (game.platformId == "android") game.romFilename else null,
            pauseLoad          = pauseArtLoad
        )

        // Glass title strip at bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.35f to Color.Black.copy(alpha = 0.55f),
                        1f to Color.Black.copy(alpha = 0.88f)
                    )
                )
                .padding(horizontal = 8.dp, vertical = 10.dp),
            contentAlignment = Alignment.BottomStart
        ) {
            Text(
                text     = game.title,
                style    = MaterialTheme.typography.labelSmall,
                color    = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
