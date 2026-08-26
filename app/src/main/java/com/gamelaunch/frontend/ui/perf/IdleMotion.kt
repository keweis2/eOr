package com.gamelaunch.frontend.ui.perf

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * The gentle "alive" idle for a focused card — a soft tilt, a slow vertical bob and a faint
 * breathing pulse, each on its own off-beat period so the motion drifts organically.
 *
 * [rememberIdleMotion] must be called **only for the focused card** (and skipped when reduced), so
 * the app runs at most one infinite animation instead of one per visible card — the previous
 * per-card `rememberInfiniteTransition` kept every tile's clock ticking every frame even though only
 * the focused tile used the values. Non-focused / reduced callers use [IdleMotion.None] (all zero).
 */
data class IdleMotion(val tilt: Float, val bob: Float, val breath: Float) {
    companion object {
        val None = IdleMotion(0f, 0f, 0f)
    }
}

/**
 * How long a focused card stays perfectly still before its idle motion begins. Coil always applies a
 * decoded cover to the Compose UI on the main thread, so a focused-card animation cycling the main
 * thread at ~60fps throttles those applies to roughly one cover per frame — the "covers trickle in
 * one-at-a-time" the full build showed and the lite build (no idle animation) never did. Holding the
 * animation off until the screen settles lets a whole screenful of art land at once on a free main
 * thread — matching the lite build — then the motion resumes. Because [rememberIdleMotion] is
 * composed only for the focused card, this delay re-arms whenever focus moves or a new grid opens, so
 * the main thread also stays free during active browsing/scrolling, when fresh art is loading.
 */
private const val IDLE_START_DELAY_MS = 900L

@Composable
fun rememberIdleMotion(): IdleMotion {
    // Stay at rest until the screen has settled (see [IDLE_START_DELAY_MS]) so box art applies on a
    // free main thread, just like the lite build.
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(IDLE_START_DELAY_MS)
        started = true
    }
    if (!started) return IdleMotion.None

    val idle = rememberInfiniteTransition(label = "idle")
    val tilt by idle.animateFloat(
        -1f, 1f, infiniteRepeatable(tween(2300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "idleTilt"
    )
    val bob by idle.animateFloat(
        -1f, 1f, infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "idleBob"
    )
    val breath by idle.animateFloat(
        -1f, 1f, infiniteRepeatable(tween(2900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "idleBreath"
    )
    return IdleMotion(tilt, bob, breath)
}
