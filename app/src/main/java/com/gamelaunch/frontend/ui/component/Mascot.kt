package com.gamelaunch.frontend.ui.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gamelaunch.frontend.R
import com.gamelaunch.frontend.ui.theme.BounceEasing
import com.gamelaunch.frontend.ui.theme.BrandBlue
import com.gamelaunch.frontend.ui.theme.ElectricBlue

/** Otto's mood drives his little animation. */
enum class MascotMood { IDLE, THINKING, CHEER }

/**
 * "Otto" — eOr's donkey mascot. Idle bob, a thinking tilt, and an excited bounce when cheering.
 * Reuses the donkey silhouette drawable and the app's BounceEasing.
 */
@Composable
fun Mascot(mood: MascotMood, modifier: Modifier = Modifier, size: Dp = 96.dp) {
    val infinite = rememberInfiniteTransition(label = "mascot")
    val bob by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (mood == MascotMood.CHEER) 430 else 950), RepeatMode.Reverse),
        label = "bob"
    )
    val cheer = if (mood == MascotMood.CHEER) 1f else 0f
    val cheerPulse by infinite.animateFloat(
        initialValue = 1f, targetValue = 1f + 0.12f * cheer,
        animationSpec = infiniteRepeatable(tween(430, easing = BounceEasing), RepeatMode.Reverse),
        label = "cheer"
    )
    Icon(
        painter = painterResource(R.drawable.ic_donkey_silhouette),
        contentDescription = "Otto",
        tint = BrandBlue,
        modifier = modifier.size(size).graphicsLayer {
            translationY = -bob * (if (mood == MascotMood.CHEER) 14.dp else 7.dp).toPx()
            scaleX = cheerPulse
            scaleY = cheerPulse
            rotationZ = when (mood) {
                MascotMood.THINKING -> -8f
                MascotMood.CHEER -> (bob - 0.5f) * 10f
                else -> 0f
            }
        }
    )
}

/** A rounded speech bubble for Otto's lines. */
@Composable
fun SpeechBubble(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
            .border(1.5.dp, ElectricBlue.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
