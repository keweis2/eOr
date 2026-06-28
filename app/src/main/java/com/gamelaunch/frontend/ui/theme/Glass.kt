package com.gamelaunch.frontend.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

// ── Liquid-glass + 3DS palette ──────────────────────────────────────────────
val InkBg        = Color(0xFF070A18)   // deep base the glows sit on
val GlowCyan     = Color(0xFF35E8FF)
val GlowPurple   = Color(0xFF9B6BFF)
val GlowBlue     = Color(0xFF4D7FFF)

/**
 * Soft, colourful ambient backdrop — the light the frosted glass refracts. Drawn as a few wide
 * radial glows over a deep ink base, giving the playful 3DS depth without any loud gradients.
 */
@Composable
fun AmbientBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier
            .fillMaxSize()
            .background(InkBg)
            .drawBehind {
                fun glow(color: Color, cx: Float, cy: Float, r: Float) = drawRect(
                    Brush.radialGradient(
                        colors = listOf(color, Color.Transparent),
                        center = Offset(size.width * cx, size.height * cy),
                        radius = size.minDimension * r
                    )
                )
                glow(GlowCyan.copy(alpha = 0.22f),   0.08f, 0.02f, 0.95f)
                glow(GlowPurple.copy(alpha = 0.24f), 0.98f, 0.10f, 1.05f)
                glow(GlowBlue.copy(alpha = 0.18f),   0.50f, 1.05f, 1.10f)
                glow(GlowPurple.copy(alpha = 0.10f), 0.15f, 0.95f, 0.70f)
            },
        content = content
    )
}

/**
 * Frosted-glass surface: a translucent milky fill that picks up the colour behind it, a bright
 * specular highlight along the top edge, and a soft floating shadow (accent-tinted when focused)
 * so tiles feel like they hover, 3DS-style.
 */
fun Modifier.glass(
    shape: Shape,
    selected: Boolean = false,
    accent: Color = GlowCyan
): Modifier = this
    .shadow(
        elevation = if (selected) 16.dp else 6.dp,
        shape = shape,
        ambientColor = if (selected) accent else Color.Black,
        spotColor = if (selected) accent else Color.Black,
        clip = false
    )
    .clip(shape)
    .background(
        Brush.verticalGradient(
            if (selected) listOf(Color.White.copy(alpha = 0.24f), Color.White.copy(alpha = 0.08f))
            else listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.025f))
        )
    )
    .border(
        width = if (selected) 1.5.dp else 1.dp,
        brush = Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = if (selected) 0.65f else 0.28f),
                Color.White.copy(alpha = if (selected) 0.14f else 0.05f)
            )
        ),
        shape = shape
    )
