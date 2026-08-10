package com.gamelaunch.frontend.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.gamelaunch.frontend.domain.model.GameSort
import com.gamelaunch.frontend.ui.theme.BrandBlue
import com.gamelaunch.frontend.ui.theme.ElectricBlue
import com.gamelaunch.frontend.ui.theme.glassTile
import com.gamelaunch.frontend.ui.theme.tileTextPrimary
import com.gamelaunch.frontend.ui.theme.tileTextSecondary
import kotlin.math.roundToInt

/** Rows in the quick menu: index 0 is the grid-size slider, then one row per [GameSort]. */
val gameSortOptions: List<GameSort> = GameSort.entries.toList()
// size slider + one row per sort choice + the "Scrape artwork" action row (always last).
val GAME_OPTIONS_ROWS: Int = 1 + gameSortOptions.size + 1
/** Focus index of the "Scrape artwork" action row (the last row). */
val GAME_OPTIONS_SCRAPE_ROW: Int = GAME_OPTIONS_ROWS - 1

/**
 * A small controller-first quick menu shown over the game grid (opened with Select). Row focus and
 * the actual key handling live in HomeScreen; this is presentational and also touch-friendly.
 *
 * @param columns      current effective column count
 * @param minColumns   fewest columns allowed (largest tiles)
 * @param maxColumns   most columns allowed (smallest tiles)
 * @param focusIndex   0 = size slider, 1..4 = sort rows
 */
@Composable
fun GameViewOptions(
    sort: GameSort,
    columns: Int,
    minColumns: Int,
    maxColumns: Int,
    focusIndex: Int,
    onSetColumns: (Int) -> Unit,
    onPickSort: (GameSort) -> Unit,
    onScrapeArtwork: () -> Unit,
    onClose: () -> Unit
) {
    // The panel is a coloured glass tile, so use the tile-aware text colours for readable contrast
    // in dark mode instead of the on-background greys.
    val textPrimary = tileTextPrimary()
    val textSecondary = tileTextSecondary()

    // Rendered inside HomeScreen's root Box so its focusable keeps receiving controller keys.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(10f)
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
            // The panel itself swallows clicks so tapping inside doesn't dismiss.
            Column(
                modifier = Modifier
                    .width(460.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .glassTile(RoundedCornerShape(24.dp), color = BrandBlue)
                    .clickable(enabled = false) {}
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.GridView, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("View options", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = textPrimary)
                }
                Spacer(Modifier.height(16.dp))

                // ── Grid size ──────────────────────────────────────────────
                OptionRow(focused = focusIndex == 0) {
                    Column(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Grid size", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = textPrimary)
                            Text("$columns per row", style = MaterialTheme.typography.labelMedium, color = textSecondary)
                        }
                        // Bigger tiles (fewer columns) to the right, so the slider reads small → large.
                        val span = (maxColumns - minColumns).coerceAtLeast(1)
                        Slider(
                            value = (maxColumns - columns).toFloat(),
                            onValueChange = { v -> onSetColumns(maxColumns - v.roundToInt()) },
                            valueRange = 0f..span.toFloat(),
                            steps = (span - 1).coerceAtLeast(0),
                            colors = SliderDefaults.colors(
                                thumbColor = ElectricBlue,
                                activeTrackColor = ElectricBlue,
                            )
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Smaller", style = MaterialTheme.typography.labelSmall, color = textSecondary)
                            Text("Larger", style = MaterialTheme.typography.labelSmall, color = textSecondary)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))

                Text("Sort by", style = MaterialTheme.typography.labelLarge, color = BrandBlue, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
                gameSortOptions.forEachIndexed { i, option ->
                    OptionRow(focused = focusIndex == i + 1, onClick = { onPickSort(option) }) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                option.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = textPrimary,
                                fontWeight = if (option == sort) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            if (option == sort) {
                                Icon(Icons.Default.Check, contentDescription = "Selected", tint = ElectricBlue, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }

                // ── Scrape artwork for just this system ─────────────────────
                Spacer(Modifier.height(6.dp))
                OptionRow(focused = focusIndex == GAME_OPTIONS_SCRAPE_ROW, onClick = onScrapeArtwork) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = null,
                            tint = ElectricBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Scrape artwork",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = textPrimary
                            )
                            Text(
                                "Fetch missing box art & media for this system",
                                style = MaterialTheme.typography.labelSmall,
                                color = textSecondary
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "↑↓ Move   ←→ Size   Ⓐ Select   Ⓑ Close",
                    style = MaterialTheme.typography.labelSmall,
                    color = textSecondary,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                )
            }
        }
    }

@Composable
private fun OptionRow(
    focused: Boolean,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    val base = Modifier
        .fillMaxWidth()
        .clip(shape)
        .then(
            if (focused) Modifier.background(ElectricBlue.copy(alpha = 0.18f))
            else Modifier
        )
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 12.dp, vertical = 10.dp)
    Box(base) { content() }
}
