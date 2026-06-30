package com.gamelaunch.frontend.ui.theme.grid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.gamelaunch.frontend.ui.theme.BounceDurationMs
import com.gamelaunch.frontend.ui.theme.BounceEasing
import com.gamelaunch.frontend.ui.theme.ElectricBlue
import com.gamelaunch.frontend.ui.theme.NeonPurple
import kotlinx.coroutines.delay

@Composable
fun GridGameCard(
    game: Game,
    index: Int = 0,
    media: GameMedia? = null,
    isFocused: Boolean = false,
    animateOnEntry: Boolean = true,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)

    // Entrance: rise up from the bottom with a slight spring bounce, staggered by position.
    // Only the cards present when the system loads should animate — capture that decision at
    // first composition so cards recycled into view while scrolling appear instantly instead
    // of re-playing the rise.
    val shouldAnimate = remember { animateOnEntry }
    val enter = remember { Animatable(if (shouldAnimate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (shouldAnimate) {
            delay(index.coerceAtMost(18) * 28L)
            enter.animateTo(1f, spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessMediumLow))
        }
    }

    // Focused card pops with the same bounce-scale as the console cards.
    val scale by animateFloatAsState(
        targetValue   = if (isFocused) 1.16f else 1f,
        animationSpec = tween(durationMillis = BounceDurationMs, easing = BounceEasing),
        label = "gridGameScale"
    )
    // Slow, never-ending slight wiggle on the focused card.
    val wiggle = rememberInfiniteTransition(label = "gridWiggle")
    val wiggleRot by wiggle.animateFloat(
        initialValue = -1.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gridWiggleRot"
    )

    Box(
        modifier = Modifier
            .zIndex(if (isFocused) 1f else 0f)
            .graphicsLayer {
                translationY = (1f - enter.value) * 72.dp.toPx()
                alpha = enter.value.coerceIn(0f, 1f)
                scaleX = scale
                scaleY = scale
                rotationZ = if (isFocused) wiggleRot else 0f
            }
            .then(
                if (isFocused)
                    Modifier.shadow(28.dp, shape, spotColor = ElectricBlue, ambientColor = NeonPurple.copy(alpha = 0.5f))
                else
                    Modifier.shadow(8.dp, shape)
            )
            .clip(shape)
            .fillMaxWidth()
            .aspectRatio(0.75f)
            .then(if (isFocused) Modifier.border(2.dp, ElectricBlue, shape) else Modifier)
            .clickable(onClick = onClick)
    ) {
        AsyncGameArtwork(
            localPath          = media?.boxArtLocalPath,
            remoteUrl          = media?.boxArtRemoteUrl,
            contentDescription = game.title,
            modifier           = Modifier.fillMaxSize()
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
