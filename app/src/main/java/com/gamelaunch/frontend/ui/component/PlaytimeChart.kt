package com.gamelaunch.frontend.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.TimeUnit

/** "2h 15m", "45m", "<1m" — compact play time for info rows and charts. */
fun formatPlaytime(ms: Long): String {
    val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        totalMinutes < 1 -> "<1m"
        hours == 0L -> "${minutes}m"
        minutes == 0L -> "${hours}h"
        else -> "${hours}h ${minutes}m"
    }
}

/**
 * Seven slim bars, one per day (oldest left, today right), scaled to the busiest day.
 * [daily] comes from PlaytimeRepository.dailyPlaytime.
 */
@Composable
fun PlaytimeWeekChart(daily: List<Long>, modifier: Modifier = Modifier) {
    val max = daily.maxOrNull()?.takeIf { it > 0 } ?: return
    val today = LocalDate.now()
    val total = daily.sum()
    Column(
        modifier = modifier.semantics {
            contentDescription = "Played ${formatPlaytime(total)} in the last ${daily.size} days"
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            daily.forEach { ms ->
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    val fraction = ms.toFloat() / max
                    Box(
                        Modifier
                            .fillMaxWidth()
                            // A sliver for days with any play so they read differently from zero.
                            .fillMaxHeight(if (ms > 0) fraction.coerceAtLeast(0.06f) else 0.02f)
                            .background(
                                if (ms > 0) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
                            )
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            daily.indices.forEach { i ->
                val day = today.minusDays((daily.size - 1 - i).toLong())
                Text(
                    text = day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (i == daily.lastIndex) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
