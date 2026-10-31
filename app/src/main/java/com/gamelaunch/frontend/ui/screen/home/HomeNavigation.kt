package com.gamelaunch.frontend.ui.screen.home

import android.annotation.SuppressLint
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer

import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface

private fun visibleTabs(
    showFavorites: Boolean,
    showRecentlyPlayed: Boolean,
    showRetroAchievements: Boolean,
    showFriends: Boolean,
    showApps: Boolean,
) = tabSpecs.filter {
        (it.tab != TopTab.FAVORITES       || showFavorites) &&
        (it.tab != TopTab.RECENTLY_PLAYED  || showRecentlyPlayed) &&
        (it.tab != TopTab.RETROACHIEVEMENTS || showRetroAchievements) &&
        (it.tab != TopTab.FRIENDS || showFriends)
            && (it.tab != TopTab.APPS || showApps)
    }

private data class TabSpec(val tab: TopTab, val label: String, val icon: ImageVector)

private val tabSpecs = listOf(
    TabSpec(TopTab.GAMES, "Games", Icons.Default.SportsEsports),
    TabSpec(TopTab.FAVORITES, "Favorites", Icons.Default.Favorite),
    TabSpec(TopTab.RECENTLY_PLAYED, "Recent", Icons.Default.History),
    TabSpec(TopTab.APPS, "Apps", Icons.Default.Apps),
    TabSpec(TopTab.RETROACHIEVEMENTS, "RetroAchievements", Icons.Default.EmojiEvents),
    TabSpec(TopTab.FRIENDS, "Friends", Icons.Default.Group)
)

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun ModeTabBar(
    selected: TopTab,
    showFavorites: Boolean,
    showRecentlyPlayed: Boolean,
    showRetroAchievements: Boolean,
    showFriends: Boolean,
    showApps: Boolean,
    onSelect: (TopTab) -> Unit
) {
    val tabs = visibleTabs(showFavorites, showRecentlyPlayed, showRetroAchievements, showFriends, showApps)

    val bringSelectedIntoView = remember { BringIntoViewRequester() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { spec ->
            val isSel = spec.tab == selected
            LaunchedEffect(isSel) { if (isSel) bringSelectedIntoView.bringIntoView() }

            FilterChip(
                selected = isSel,
                onClick = { onSelect(spec.tab) },
                label = {
                    Text(
                        text = spec.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                    )
                },
                leadingIcon = {
                    Icon(
                        spec.icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                shape = RoundedCornerShape(50),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSel,
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                    selectedBorderColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.then(if (isSel) Modifier.bringIntoViewRequester(bringSelectedIntoView) else Modifier)
            )
        }
    }
}

// Studio can miss the scope usage through maxWidth; it determines item widths below.
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun HomeBottomNavigation(
    selected: TopTab,
    showFavorites: Boolean,
    showRecentlyPlayed: Boolean,
    showRetroAchievements: Boolean,
    showFriends: Boolean,
    showApps: Boolean,
    onSelect: (TopTab) -> Unit,
) {
    val tabs = visibleTabs(showFavorites, showRecentlyPlayed, showRetroAchievements, showFriends, showApps)
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = MaterialTheme.typography.labelMedium
    val colors = MaterialTheme.colorScheme
    val dividerColor = colors.outlineVariant.copy(alpha = 0.35f)
    Surface(
        color = colors.surfaceContainer.copy(alpha = 0.88f),
        modifier = Modifier.drawWithContent {
            drawContent()
            drawLine(dividerColor, Offset.Zero, Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
        },
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            val availableWidth = this.maxWidth
            val minimumWidths = tabs.map { spec ->
                with(density) {
                    maxOf(80.dp, textMeasurer.measure(spec.label, labelStyle).size.width.toDp() + 24.dp)
                }
            }
            val totalMinimum = minimumWidths.fold(0.dp) { total, width -> total + width }
            val equalWidth = availableWidth / tabs.size
            val equalItemsFit = minimumWidths.all { it <= equalWidth }
            val extraPerItem = maxOf(0.dp, availableWidth - totalMinimum) / tabs.size
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEachIndexed { index, spec ->
                    val requester = remember { BringIntoViewRequester() }
                    val isSelected = selected == spec.tab
                    LaunchedEffect(isSelected) {
                        if (isSelected) requester.bringIntoView()
                    }
                    // Each item gets its own finite Row: NavigationBarItem uses weight internally.
                    val itemWidth = if (equalItemsFit) equalWidth else minimumWidths[index] + extraPerItem
                    Row(Modifier.width(itemWidth).bringIntoViewRequester(requester)) {
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { onSelect(spec.tab) },
                            icon = { Icon(spec.icon, contentDescription = null, modifier = Modifier.size(28.dp)) },
                            label = { Text(spec.label, style = labelStyle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = colors.primary,
                                selectedTextColor = colors.primary,
                                indicatorColor = colors.primary.copy(alpha = 0.12f),
                                unselectedIconColor = colors.onSurfaceVariant.copy(alpha = 0.8f),
                                unselectedTextColor = colors.onSurfaceVariant.copy(alpha = 0.8f),
                            ),
                        )
                    }
                }
            }
        }
    }
}
