package com.gamelaunch.frontend.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import com.gamelaunch.frontend.ui.theme.TilePalette
import kotlin.random.Random

private data class ConfettiPiece(
    val startX: Float, val driftX: Float, val speed: Float,
    val spin: Float, val color: Color, val w: Float, val h: Float
) {
    companion object {
        fun random() = ConfettiPiece(
            startX = Random.nextFloat(),
            driftX = Random.nextFloat() * 2f - 1f,
            speed = 0.7f + Random.nextFloat() * 0.5f,
            spin = Random.nextFloat() * 2f - 1f,
            color = TilePalette.random(),
            w = 8f + Random.nextFloat() * 8f,
            h = 12f + Random.nextFloat() * 10f
        )
    }
}

/** A one-shot confetti burst that rains down when [play] flips true. */
@Composable
fun Confetti(play: Boolean, modifier: Modifier = Modifier, pieces: Int = 70) {
    if (!play) return
    val confetti = remember { List(pieces) { ConfettiPiece.random() } }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(play) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(2200))
    }
    Canvas(modifier.fillMaxSize()) {
        val t = progress.value
        confetti.forEach { p ->
            val x = size.width * p.startX + p.driftX * t * size.width * 0.35f
            val y = -40f + (size.height + 120f) * (t * p.speed)
            val alpha = (1f - t * 0.9f).coerceIn(0f, 1f)
            rotate(degrees = p.spin * t * 720f, pivot = Offset(x, y)) {
                drawRect(
                    color = p.color,
                    topLeft = Offset(x - p.w / 2f, y - p.h / 2f),
                    size = Size(p.w, p.h),
                    alpha = alpha
                )
            }
        }
    }
}
