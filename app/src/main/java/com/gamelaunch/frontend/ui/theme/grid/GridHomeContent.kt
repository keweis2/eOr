package com.gamelaunch.frontend.ui.theme.grid

import android.os.Build
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Scale
import com.gamelaunch.frontend.BuildConfig
import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.model.GameMedia
import com.gamelaunch.frontend.domain.model.GameSort
import com.gamelaunch.frontend.domain.model.sectionLabel
import com.gamelaunch.frontend.image.boxArtThumbnail
import com.gamelaunch.frontend.ui.component.BOX_ART_TILE_PX
import com.gamelaunch.frontend.ui.component.ScrollSectionIndicator
import com.gamelaunch.frontend.ui.component.boxArtAspectRatio
import com.gamelaunch.frontend.ui.component.rememberSectionIndicatorState
import com.gamelaunch.frontend.ui.perf.LocalReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun GridHomeContent(
    games: List<Game>,
    onGameClick: (Long) -> Unit,
    columns: Int,
    mediaForGames: Map<Long, GameMedia> = emptyMap(),
    focusedGameIndex: Int = -1,
    onPageSizeChange: (Int) -> Unit = {},
    // Drives the fast-scroll section popup ("A", "★", "This Week"…) so the token matches the order
    // the games are actually in. Defaults to alphabetical.
    gameSort: GameSort = GameSort.ALPHABETICAL,
    // When set, every tile uses this fixed aspect ratio instead of its system's box shape — used by
    // mixed-system lists (Recently played) so the grid stays a uniform rectangle.
    uniformAspectRatio: Float? = null,
    // The fast-scroll section popup + hold-scroll blur only belong on the per-system game grid.
    // The home lists (Recently played, Favorites) pass false so neither appears there.
    sectionPopupEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (games.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No games found")
        }
        return
    }

    val gridState = rememberLazyGridState()

    // Are we in a FAST controller scroll? Profiling showed the real cause of the "scroll to F trickles"
    // symptom: a fast d-pad scroll animates through every row on the way, and each row that flashes
    // through the viewport (plus its look-ahead prefetch) fires a cover decode — a flood of hundreds of
    // throwaway decodes that buries the screenful you actually stop on. isScrollInProgress can't gate
    // this: it flickers false between discrete d-pad steps and the flood slips through the gaps. So key
    // off the focused index instead — if it keeps advancing in quick succession we're fast-scrolling.
    // A single deliberate nudge (slow browsing) has a long gap so it stays false and loads immediately;
    // the first focus after a grid opens is a long gap too, so the opening screenful is unaffected. Both
    // the visible-tile loads (pauseArtLoad below) and the look-ahead prefetch are held while this is on.
    var lastFocusMoveMs by remember { mutableStateOf(0L) }
    var fastScrolling by remember { mutableStateOf(false) }
    LaunchedEffect(focusedGameIndex) {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastFocusMoveMs < 140L) fastScrolling = true
        lastFocusMoveMs = now
        delay(200)
        fastScrolling = false
    }

    // Row/column gap. Kept in one place because the scroll-anchor math below needs the same value to
    // work out how many whole rows fit in the viewport.
    val gridSpacing = 8.dp
    val gridSpacingPx = with(LocalDensity.current) { gridSpacing.roundToPx() }

    // Report a "page" (whole rows currently on screen × columns) up to the caller so L2/R2 can jump
    // the selection by a screenful at a time.
    LaunchedEffect(gridState, columns) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.size }
            .collect { visible ->
                val rows = (visible / columns).coerceAtLeast(1)
                onPageSizeChange(rows * columns)
            }
    }

    // Prefetch box art below the fold so covers are decoded and in the memory cache before they
    // scroll into view — the grid then paints them the instant a tile composes instead of showing a
    // blank cell that fills in a beat later. This runs the frontier forward: on open it warms the
    // first screenful-plus-lookahead, and as the last visible item advances it keeps a few rows ahead
    // primed. Prefetch requests carry no on-screen target, so the work is pure background decode on
    // Coil's dispatchers — it never touches the main thread and so can't reintroduce the apply-time
    // trickle. Decoding at the visible tile size (and reusing AsyncGameArtwork's size-independent
    // memoryCacheKey) means the tile gets a straight memory-cache hit. Keyed on the last visible index
    // + tile size via distinctUntilChanged, so it fires as the frontier moves, not every frame.
    val context = LocalContext.current
    val imageLoader = context.imageLoader
    LaunchedEffect(gridState, games, mediaForGames, columns) {
        // How far below the fold to keep primed — about two screenfuls. Deliberately a bounded number
        // of ROWS, not a percentage of the list: what prevents a blank cell is staying ahead of the
        // viewport, and that's independent of whether the system has 64 games or 733. A percentage
        // would over-decode huge libraries on open (competing with the visible covers for decoder
        // threads, and risking evicting the very covers on screen), while a fixed row look-ahead costs
        // the same ~15–30 covers regardless of list size.
        val lookaheadRows = 6
        // Prefetch requests from the PREVIOUS frontier position. They're disposed the instant the
        // frontier moves, so a fast jump (e.g. holding down from A to S) never leaves a backlog of
        // look-ahead decodes for the rows you flew past clogging Coil's queue — otherwise the covers
        // for the row you actually land on sit at the back of that backlog and take forever to appear.
        // Cancelling keeps only the current region decoding, so the landing row's own tiles (requested
        // by their composables) and its look-ahead get the decode threads immediately. On-screen tiles
        // that scroll out cancel themselves (Coil ties those requests to composition); only these
        // targetless prefetches need cancelling by hand.
        var inFlight: List<coil.request.Disposable> = emptyList()
        snapshotFlow {
            val info = gridState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            val tile = info.visibleItemsInfo.firstOrNull()?.size
            // Fold fastScrolling in so the flow also fires when the scroll settles (to warm ahead then)
            // and when it starts (to cancel any in-flight look-ahead immediately).
            listOf(lastVisible, tile?.width ?: 0, tile?.height ?: 0, if (fastScrolling) 1 else 0)
        }
            .distinctUntilChanged()
            .collect { (lastVisible, tileW, tileH, fast) ->
                // Cancel the PREVIOUS frontier's look-ahead the instant the frontier moves. Without
                // this, a long scroll down a big library (e.g. PlayStation) piles up hundreds of
                // uncancellable look-ahead decodes, and the screenful you finally land on queues behind
                // all of them — the covers then take many seconds to appear. Cancelling keeps only the
                // current region decoding, so the landing row's own tiles (loaded on compose) and its
                // look-ahead get the decode threads immediately. This applies on BOTH builds — dropping
                // it on full was the regression that made large systems take ~30s to fill.
                inFlight.forEach { it.dispose() }
                inFlight = emptyList()
                // Don't prefetch mid-fast-scroll: the look-ahead would just pile decodes onto rows
                // you're blowing past and bury the landing screenful. Warm ahead once it settles.
                if (fast == 1 || lastVisible < 0 || tileW == 0 || tileH == 0) return@collect
                val end = (lastVisible + lookaheadRows * columns).coerceAtMost(games.lastIndex)
                val batch = ArrayList<coil.request.Disposable>()
                for (i in (lastVisible + 1)..end) {
                    val media = mediaForGames[games[i].id] ?: continue
                    val data = media.boxArtLocalPath ?: media.boxArtRemoteUrl ?: continue
                    val builder = ImageRequest.Builder(context)
                        .data(data)
                        .memoryCacheKey(data)
                    // Full warms at the compact tile size the tiles read (a straight cache hit when they
                    // scroll in); lite keeps its original exact-tile-pixel prefetch.
                    if (BuildConfig.LOW_POWER) builder.size(tileW, tileH)
                    else builder.size(BOX_ART_TILE_PX, BOX_ART_TILE_PX).scale(Scale.FILL).boxArtThumbnail()
                    batch += imageLoader.enqueue(builder.build())
                }
                inFlight = batch
            }
    }

    // Scroll so the controller-focused card is always visible. When at least 2 WHOLE rows fit in the
    // viewport we anchor the focused card to the SECOND row (one row of context above it), so
    // scrolling only kicks in once focus moves past the second row. But a short viewport that can't
    // fit 2 whole rows has no room to spare a context row — anchoring to the second row pushes the
    // focused card off the bottom edge (e.g. tall arcade art at a large grid size shows barely one
    // full row) — so there we pin the focused card's row to the TOP instead, keeping it fully on
    // screen. Note this is viewport CAPACITY (how many whole rows fit), not how many rows happen to
    // be peeking through right now — a partially-clipped row does not count toward the 2.
    LaunchedEffect(focusedGameIndex, columns) {
        if (focusedGameIndex in games.indices) {
            val info = gridState.layoutInfo
            val rowHeightPx = info.visibleItemsInfo.firstOrNull()?.size?.height ?: 0
            val rowPitchPx = rowHeightPx + gridSpacingPx
            // Space available to lay out rows, inside the content padding.
            val contentHeightPx = info.viewportSize.height - info.beforeContentPadding - info.afterContentPadding
            // n whole rows occupy n*rowHeight + (n-1)*spacing, i.e. n*rowPitch - spacing.
            val wholeRowsThatFit =
                if (rowPitchPx > 0) (contentHeightPx + gridSpacingPx) / rowPitchPx else 0
            val target = if (wholeRowsThatFit >= 2) {
                (focusedGameIndex - columns).coerceAtLeast(0)   // second-row anchor (context above)
            } else {
                (focusedGameIndex / columns) * columns          // top-row anchor (focused row first)
            }
            gridState.animateScrollToItem(target)
        }
    }

    // ── Scroll section popup (+ blur) ────────────────────────────────────────────────────────
    // Once a hold has been sustained past the show delay, show a big "you are here" section token —
    // the letter/bucket the focused game sorts under — so the user can aim for a part of the list
    // while the cards behind it are still catching up. On the low-power (lite) build we also blur the
    // grid: the RK3568 can't repaint as fast as the cursor moves, so a hold-scroll otherwise shows
    // half-decoded, popping cards — the blur masks that and buys the grid time to settle. A single
    // nudge never raises it; it appears partway into a continuous hold (sooner on lite) and fades out
    // once the cursor stops and the grid catches up. Draw-only overlay: it deliberately does not
    // touch scroll mechanics or input handling (the source of the reverted double-move regression).
    val reduceMotion = LocalReduceMotion.current
    // Arm the popup on VERTICAL movement only: key it on the focused row, so nudging left/right
    // within a row doesn't raise it. When disabled (home lists) pass -1 so it never arms.
    val armRow = if (sectionPopupEnabled && focusedGameIndex >= 0) focusedGameIndex / columns else -1
    val section = rememberSectionIndicatorState(
        focusedIndex = armRow,
        reduceMotion = reduceMotion,
        isScrollInProgress = { gridState.isScrollInProgress }
    )
    val indicatorAlpha = section.alpha

    val blurRadius by animateDpAsState(
        targetValue   = if (reduceMotion && section.active) 4.dp else 0.dp,
        animationSpec = tween(160),
        label = "gridScrollBlur"
    )

    // While the grid is scrolling — AND for the brief burst right after it opens — run the cards as if
    // reduced, exactly what the lite build does all the time. Coil applies every decoded cover on the
    // main thread, so the focused card's full-build extras (the 420ms bounce-scale, the GPU-costly
    // colored spot shadow, the idle animation) compete with those applies and make covers trickle in
    // one-at-a-time. That competition hits hardest in two moments: while scrolling in new rows, and on
    // the very first screenful when a system opens (the focused card's entrance pop fires right as a
    // dozen covers are trying to paint — the "systems load slowly" trickle). Suppressing the extras in
    // both windows frees the main thread so the covers land together — matching lite.
    //
    // openSettled flips true a short beat after the grid first composes; until then the cards are
    // reduced so the opening screenful paints together. Because the focus scale is SNAPPED to its
    // target during that window, when full motion returns animateFloatAsState sees no target change and
    // doesn't fire a late bounce — the selected card is simply already popped. Full polish (bounce on
    // later selection changes, glow, idle) resumes normally afterward.
    var openSettled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(450)
        openSettled = true
    }

    // Keep the grid reduced for a short beat AFTER scrolling stops, not just during it. When you fast-
    // scroll to a far region (e.g. jump to the "F" games), you land on a screenful of covers that
    // haven't been prefetched yet — you outran the look-ahead — so they decode fresh right as the
    // scroll settles. If full motion resumed the instant scrolling stopped, the focused card's pop
    // would fire straight into those still-arriving applies and throttle them into the same one-at-a-
    // time trickle. Holding reduced ~450ms past scroll-stop lets the landing covers paint together
    // first; because the scale stays snapped through the window, no late bounce fires when it lifts.
    val scrolling = gridState.isScrollInProgress
    var scrollSettling by remember { mutableStateOf(false) }
    LaunchedEffect(scrolling) {
        if (scrolling) {
            scrollSettling = true
        } else {
            delay(450)
            scrollSettling = false
        }
    }
    // Read here rather than deeper so ONLY the cards see it — the section-popup blur above still keys
    // off the real build flag, so the full build never picks up lite's hold-scroll blur.
    val gridReduceMotion = reduceMotion || scrolling || scrollSettling || !openSettled

    Box(modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalReduceMotion provides gridReduceMotion) {
        LazyVerticalGrid(
            columns               = GridCells.Fixed(columns),
            state                 = gridState,
            // Extra top padding so a focused top-row card (which scales 1.16× and bobs upward) clears
            // the header instead of being clipped under it.
            contentPadding        = PaddingValues(start = 8.dp, end = 8.dp, top = 30.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(gridSpacing),
            verticalArrangement   = Arrangement.spacedBy(gridSpacing),
            modifier              = Modifier
                .fillMaxSize()
                // Modifier.blur no-ops on API < 31; the effect only runs where RenderEffect exists.
                .then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(blurRadius) else Modifier)
        ) {
            itemsIndexed(games, key = { _, g -> g.id }) { index, game ->
                GridGameCard(
                    game           = game,
                    media          = mediaForGames[game.id],
                    isFocused      = index == focusedGameIndex,
                    aspectRatio    = uniformAspectRatio ?: boxArtAspectRatio(game.platformId),
                    // Pass the stable callback straight through — the card builds its own click
                    // lambda internally, so no per-item allocation happens here.
                    onGameClick    = onGameClick,
                    // Hold cover loads during a fast scroll so the rows flying past don't flood the
                    // decoder and bury the landing screenful. Loads resume ~200ms after you stop.
                    pauseArtLoad   = fastScrolling
                )
            }
        }
        }

        // Section token, sitting crisply on top of the (blurred) grid while scrolling.
        if (indicatorAlpha > 0f) {
            ScrollSectionIndicator(
                label    = games.getOrNull(focusedGameIndex)?.sectionLabel(gameSort).orEmpty(),
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer { alpha = indicatorAlpha }
            )
        }
    }
}
