package com.gamelaunch.frontend.ui.screen.home

import com.gamelaunch.frontend.ui.component.boxArtAspectRatio
import kotlin.math.roundToInt

internal data class HomeLayoutSizing(
    val gameGridColumns: Int,
    val recentGridColumns: Int,
    val appColumns: Int,
    val minGameCols: Int,
    val maxGameCols: Int,
)

internal fun homeLayoutSizing(state: HomeUiState, screenWidthDp: Int): HomeLayoutSizing {
    // Match LazyVerticalGrid's column maths exactly so D-pad navigation lands on the right cell.
    fun gridColumns(minCellDp: Int, paddingDp: Int, spacingDp: Int) =
        maxOf(1, (screenWidthDp - 2 * paddingDp + spacingDp) / (minCellDp + spacingDp))
    // Cover tiles take the selected system's box shape, so scale the min cell *width* by that
    // aspect to keep tile height roughly constant across systems — otherwise landscape boxes
    // (SNES, N64) end up as short, tiny strips at the portrait column count. ~150dp tall target
    // (matches the old 110dp / 0.72 portrait tile).
    val gameCellMinDp = (150f * boxArtAspectRatio(state.selectedPlatform ?: ""))
        .roundToInt().coerceIn(96, 240)
    val autoGameCols = gridColumns(minCellDp = gameCellMinDp, paddingDp = 8, spacingDp = 8)
    // Bounds for the Select-menu grid-size slider. Every integer column count fills the row exactly
    // (GridCells.Fixed), so each slider step "resizes perfectly".
    val minGameCols = 3
    val maxGameCols = gridColumns(minCellDp = 92, paddingDp = 8, spacingDp = 8).coerceIn(minGameCols + 1, 12)
    // User-chosen column count (from the Select menu) overrides the auto-fit; 0 = auto.
    val gameGridColumns = (state.gameGridColumns.takeIf { it > 0 } ?: autoGameCols)
        .coerceIn(minGameCols, maxGameCols)
    // Recently-played mixes systems, so keep the portrait default there.
    val recentGridColumns = gridColumns(minCellDp = 110, paddingDp = 8, spacingDp = 8)
    val appColumns      = gridColumns(minCellDp = 96, paddingDp = 16, spacingDp = 12)

    return HomeLayoutSizing(gameGridColumns, recentGridColumns, appColumns, minGameCols, maxGameCols)
}
