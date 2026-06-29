package com.gamelaunch.frontend.ui.screen.retroachievements

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.gamelaunch.frontend.domain.model.RaProfile
import com.gamelaunch.frontend.domain.model.RaRecentGame
import com.gamelaunch.frontend.ui.theme.BrandBlue
import com.gamelaunch.frontend.ui.theme.IceWhite
import com.gamelaunch.frontend.ui.theme.tileColor
import com.gamelaunch.frontend.ui.theme.LocalDarkMode
import com.gamelaunch.frontend.ui.theme.NavySurface
import com.gamelaunch.frontend.ui.theme.SteelGray
import com.gamelaunch.frontend.ui.theme.TileSub
import com.gamelaunch.frontend.ui.theme.TileText
import com.gamelaunch.frontend.ui.theme.glassTile

private val Gold  = Color(0xFFFFD700)
private val Green = Color(0xFF4CAF50)

@Composable
fun RetroAchievementsScreen(
    onGoToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RetroAchievementsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val darkMode = LocalDarkMode.current
    val textPrimary   = if (darkMode) IceWhite  else TileText
    val textSecondary = if (darkMode) SteelGray else TileSub

    Box(modifier.fillMaxSize()) {
        when (val s = state) {
            is RaScreenState.NotConfigured -> NotConfiguredContent(textPrimary, textSecondary, onGoToSettings)
            is RaScreenState.Loading       -> LoadingContent()
            is RaScreenState.Error         -> ErrorContent(s.message, textPrimary, textSecondary, viewModel::refresh)
            is RaScreenState.Loaded        -> LoadedContent(s.profile, s.recentGames, textPrimary, textSecondary, darkMode, viewModel::refresh)
        }
    }
}

@Composable
private fun NotConfiguredContent(textPrimary: Color, textSecondary: Color, onGoToSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Gold, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text("RetroAchievements", style = MaterialTheme.typography.headlineSmall, color = textPrimary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Enter your RetroAchievements username and API key in Settings to track your progress.",
            style = MaterialTheme.typography.bodyMedium,
            color = textSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(BrandBlue)
                .clickable { onGoToSettings() }
                .padding(horizontal = 24.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Go to Settings", color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = BrandBlue)
    }
}

@Composable
private fun ErrorContent(message: String, textPrimary: Color, textSecondary: Color, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Failed to load", style = MaterialTheme.typography.titleMedium, color = textPrimary)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = textSecondary)
        Spacer(Modifier.height(16.dp))
        IconButton(onClick = onRetry) {
            Icon(Icons.Default.Refresh, contentDescription = "Retry", tint = BrandBlue, modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
private fun LoadedContent(
    profile: RaProfile,
    games: List<RaRecentGame>,
    textPrimary: Color,
    textSecondary: Color,
    darkMode: Boolean,
    onRefresh: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { ProfileHeader(profile, textPrimary, textSecondary, onRefresh) }
        item { Spacer(Modifier.height(4.dp)) }
        if (games.isEmpty()) {
            item {
                Text("No recently played games found.", color = textSecondary, modifier = Modifier.padding(8.dp))
            }
        } else {
            item {
                Text("Recently Played", style = MaterialTheme.typography.labelLarge, color = BrandBlue, modifier = Modifier.padding(start = 4.dp))
            }
            items(games.size, key = { games[it].gameId }) { i ->
                GameRow(games[i], textPrimary, textSecondary, darkMode, i)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ProfileHeader(profile: RaProfile, textPrimary: Color, textSecondary: Color, onRefresh: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassTile(shape, color = BrandBlue)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model             = profile.avatarUrl,
            contentDescription = profile.username,
            contentScale      = ContentScale.Crop,
            modifier          = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(NavySurface)
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(profile.username, style = MaterialTheme.typography.titleLarge, color = textPrimary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatChip(label = "Points", value = "%,d".format(profile.totalPoints), color = Gold)
                if (profile.rank != null && profile.rank > 0) {
                    StatChip(label = "Rank", value = "#${"%,d".format(profile.rank)}", color = BrandBlue)
                }
                if (profile.truePoints > 0) {
                    StatChip(label = "True", value = "%,d".format(profile.truePoints), color = Green)
                }
            }
            profile.status?.let { status ->
                Spacer(Modifier.height(4.dp))
                Text(status, style = MaterialTheme.typography.labelSmall, color = textSecondary)
            }
        }
        IconButton(onClick = onRefresh) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = textSecondary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(label, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.7f))
    }
}

@Composable
private fun GameRow(game: RaRecentGame, textPrimary: Color, textSecondary: Color, darkMode: Boolean, index: Int = 0) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassTile(shape, color = tileColor(index))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model             = game.iconUrl,
            contentDescription = game.title,
            contentScale      = ContentScale.Crop,
            modifier          = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(NavySurface)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    game.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (game.isMastered) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.Star, contentDescription = "Mastered", tint = Gold, modifier = Modifier.size(14.dp))
                }
            }
            Text(game.consoleName, style = MaterialTheme.typography.labelSmall, color = textSecondary)
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress          = { game.completionPercent },
                modifier          = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)),
                color             = if (game.isMastered) Gold else BrandBlue,
                trackColor        = if (darkMode) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f),
                strokeCap         = StrokeCap.Round
            )
            Spacer(Modifier.height(3.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "${game.numEarned} / ${game.numAchievements} achievements",
                    style = MaterialTheme.typography.labelSmall,
                    color = textSecondary
                )
                if (game.maxScore > 0) {
                    Text(
                        "${game.scoreEarned} / ${game.maxScore} pts",
                        style = MaterialTheme.typography.labelSmall,
                        color = textSecondary
                    )
                }
            }
        }
    }
}
