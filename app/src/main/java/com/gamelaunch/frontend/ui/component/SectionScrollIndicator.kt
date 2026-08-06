package com.gamelaunch.frontend.ui.component

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamelaunch.frontend.ui.theme.IceWhite
import com.gamelaunch.frontend.ui.theme.LocalDarkMode
import com.gamelaunch.frontend.ui.theme.TileText
import com.gamelaunch.frontend.ui.theme.glassChip
import kotlinx.coroutines.delay

// How long a continuous hold must be sustained before the section popup (and, on lite, the grid
// blur) appears. Both builds now wait the same, longer beat: the popup is a power-scroll aid, so it
// should only surface once the user is clearly holding through the list — not on a few quick nudges.
// (Lite used to appear sooner to mask its laggy grid; lite scroll is on par with full now.) Kept as
// two constants so the lite delay can diverge again if needed.
private const val HOLD_SHOW_MS_LITE = 900L
private const val HOLD_SHOW_MS_FULL = 900L
// Gaps larger than this between focus steps start a fresh hold (so separate presses don't accrue).
private const val MAX_GAP_MS = 400L
// Once the cursor has been still this long (and the list has finished catching up), fade the popup.
private const val HIDE_MS = 450L

/** The visible state of the fast-scroll section popup: an animated alpha plus the raw "is holding". */
data class SectionIndicatorState(val alpha: Float, val active: Boolean)

/**
 * Shared hold-detection for the fast-scroll "you are here" section popup, used by both the grid and
 * list layouts. The popup is raised only once a continuous hold outlasts a short delay (sooner on the
 * low-power build), and fades once the cursor stops and the scroll settles. [isScrollInProgress] is a
 * read of the backing list/grid scroll state so the fade waits for the scroll to land.
 *
 * Draw-only: callers use [SectionIndicatorState.alpha] for the token and may use [active] for extra
 * effects (the grid blurs while active). It deliberately touches no scroll or input handling.
 */
@Composable
fun rememberSectionIndicatorState(
    focusedIndex: Int,
    reduceMotion: Boolean,
    isScrollInProgress: () -> Boolean
): SectionIndicatorState {
    val showAfterMs = if (reduceMotion) HOLD_SHOW_MS_LITE else HOLD_SHOW_MS_FULL
    var scrolling by remember { mutableStateOf(false) }
    var lastChangeMs by remember { mutableLongStateOf(0L) }
    var holdStartMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(focusedIndex) {
        if (focusedIndex < 0) return@LaunchedEffect
        val now = SystemClock.uptimeMillis()
        // A big gap (or the first move) begins a fresh hold; small gaps continue the current one.
        if (now - lastChangeMs > MAX_GAP_MS) holdStartMs = now
        lastChangeMs = now
        // Reveal only once this hold has lasted long enough — so quick nudges stay hidden.
        if (now - holdStartMs >= showAfterMs) scrolling = true
        // This effect restarts on every move, so reaching past the delay means the cursor idled;
        // then wait for the scroll to land before hiding.
        delay(HIDE_MS)
        while (isScrollInProgress()) delay(16)
        scrolling = false
    }

    val alpha by animateFloatAsState(
        targetValue   = if (scrolling) 1f else 0f,
        animationSpec = tween(140),
        label = "sectionIndicatorAlpha"
    )
    return SectionIndicatorState(alpha = alpha, active = scrolling)
}

/** Big frosted "you are here" token (a letter like "S", "★", or a short bucket like "This Week"). */
@Composable
fun ScrollSectionIndicator(
    label: String,
    modifier: Modifier = Modifier
) {
    if (label.isBlank()) return
    val dark = LocalDarkMode.current
    val primary = if (dark) IceWhite else TileText
    // Single letters/★ get the big splashy treatment; multi-word buckets shrink to fit on one line.
    val fontSize = if (label.length <= 2) 60.sp else 30.sp
    Box(
        modifier = modifier
            .glassChip(RoundedCornerShape(22.dp))
            .defaultMinSize(minWidth = 104.dp, minHeight = 104.dp)
            .padding(horizontal = 28.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text       = label,
            color      = primary,
            fontSize   = fontSize,
            fontWeight = FontWeight.Bold,
            maxLines   = 1
        )
    }
}
