package com.gamelaunch.frontend.ui.screen.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.gamelaunch.frontend.domain.friends.Friend
import com.gamelaunch.frontend.domain.model.raAvatarUrl
import com.gamelaunch.frontend.domain.platform.PlatformDefinitions
import com.gamelaunch.frontend.ui.theme.BrandBlue
import com.gamelaunch.frontend.ui.theme.IceWhite
import com.gamelaunch.frontend.ui.theme.LocalDarkMode
import com.gamelaunch.frontend.ui.theme.SteelGray
import com.gamelaunch.frontend.ui.theme.TileSub
import com.gamelaunch.frontend.ui.theme.TileText
import com.gamelaunch.frontend.ui.theme.glassTile
import com.gamelaunch.frontend.ui.theme.tileColor

private val Gold = Color(0xFFFFC04D)

@Composable
fun FriendsScreen(
    onGoToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FriendsViewModel = hiltViewModel()
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refresh() }

    if (ui.active.isEmpty() && ui.incoming.isEmpty()) {
        EmptyFriends(onGoToSettings, modifier)
        return
    }

    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        if (ui.incoming.isNotEmpty()) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .glassTile(RoundedCornerShape(14.dp), color = BrandBlue)
                        .clickable(onClick = onGoToSettings)
                        .padding(14.dp)
                ) {
                    Text(
                        "${ui.incoming.size} friend request${if (ui.incoming.size == 1) "" else "s"} — open Settings ▸ Friends to accept",
                        style = MaterialTheme.typography.bodyMedium,
                        color = IceWhite
                    )
                }
            }
        }
        itemsIndexed(ui.active) { index, friend -> FriendCard(friend, index) }
    }
}

@Composable
private fun FriendCard(friend: Friend, index: Int) {
    val dark = LocalDarkMode.current
    val textPrimary = if (dark) IceWhite else TileText
    val textSecondary = if (dark) SteelGray else TileSub

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassTile(RoundedCornerShape(16.dp), color = tileColor(index))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar: RA profile pic if available, else a generic badge.
        if (friend.ra != null) {
            AsyncImage(
                model = raAvatarUrl(friend.ra.username),
                contentDescription = null,
                modifier = Modifier.size(46.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f))
            )
        } else {
            Box(
                modifier = Modifier.size(46.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Group, contentDescription = null, tint = textPrimary)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                friend.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            val lp = friend.lastPlayed
            if (lp != null) {
                val platform = PlatformDefinitions.byId[lp.platform]?.displayName ?: lp.platform
                Text(
                    "Playing ${lp.title}${if (platform.isNotBlank()) " · $platform" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text("No games played yet", style = MaterialTheme.typography.bodySmall, color = textSecondary)
            }
            Text(lastSeen(friend.profileUpdatedAt), style = MaterialTheme.typography.labelSmall, color = textSecondary)
        }
        if (friend.ra != null) {
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Gold, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${friend.ra.points}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = textPrimary)
                }
                Text("points", style = MaterialTheme.typography.labelSmall, color = textSecondary)
            }
        }
    }
}

@Composable
private fun EmptyFriends(onGoToSettings: () -> Unit, modifier: Modifier) {
    val dark = LocalDarkMode.current
    val textPrimary = if (dark) IceWhite else TileText
    val textSecondary = if (dark) SteelGray else TileSub
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(48.dp), tint = textSecondary)
            Spacer(Modifier.height(12.dp))
            Text("No friends yet", style = MaterialTheme.typography.titleMedium, color = textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "Add friends from Settings ▸ Friends to see their last-played game and RetroAchievements score.",
                style = MaterialTheme.typography.bodySmall,
                color = textSecondary
            )
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .glassTile(RoundedCornerShape(12.dp), color = BrandBlue)
                    .clickable(onClick = onGoToSettings)
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text("Open Settings", color = IceWhite, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Human-friendly "updated Xm ago" from a friend's own profile timestamp. */
private fun lastSeen(updatedAt: Long?): String {
    if (updatedAt == null || updatedAt <= 0) return "Not synced yet"
    val mins = (System.currentTimeMillis() - updatedAt) / 60_000
    return when {
        mins < 1 -> "Updated just now"
        mins < 60 -> "Updated ${mins}m ago"
        mins < 1440 -> "Updated ${mins / 60}h ago"
        else -> "Updated ${mins / 1440}d ago"
    }
}
