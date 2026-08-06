package com.gamelaunch.frontend.ui.perf

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue

/**
 * The focused/selected card's pop-scale — reduce-aware. In full mode it animates with
 * [fullSpec] (the caller's existing bounce/spring). When running reduced (lite build /
 * performance mode) the scale SNAPS instantly, so a weak chipset does no per-frame scale
 * work while browsing. Mirrors [rememberIdleMotion]'s "focused-only, skip-when-reduced"
 * pattern — reads [LocalReduceMotion] here so callers don't repeat the branch.
 *
 * [animateFloatAsState] is always called and only the spec is conditional, so the call
 * structure stays constant when reduce toggles at runtime (full build via performance-mode /
 * dual-screen).
 */
@Composable
fun rememberSelectionScale(
    active: Boolean,
    activeScale: Float,
    restScale: Float = 1f,
    fullSpec: FiniteAnimationSpec<Float>,
    label: String = "selectionScale"
): Float {
    val reduce = LocalReduceMotion.current
    val scale by animateFloatAsState(
        targetValue = if (active) activeScale else restScale,
        animationSpec = if (reduce) snap() else fullSpec,
        label = label
    )
    return scale
}
