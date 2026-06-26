package com.gamelaunch.frontend.ui.theme.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.model.GameMedia
import com.gamelaunch.frontend.ui.component.AsyncGameArtwork
import com.gamelaunch.frontend.ui.theme.ElectricBlue
import com.gamelaunch.frontend.ui.theme.NeonPurple

@Composable
fun GridGameCard(
    game: Game,
    media: GameMedia? = null,
    isFocused: Boolean = false,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = Modifier
            .then(
                if (isFocused)
                    Modifier.shadow(28.dp, shape, spotColor = ElectricBlue, ambientColor = NeonPurple.copy(alpha = 0.5f))
                else
                    Modifier.shadow(8.dp, shape)
            )
            .clip(shape)
            .fillMaxWidth()
            .aspectRatio(0.75f)
            .then(if (isFocused) Modifier.border(2.dp, ElectricBlue, shape) else Modifier)
            .clickable(onClick = onClick)
    ) {
        AsyncGameArtwork(
            localPath          = media?.boxArtLocalPath,
            remoteUrl          = media?.boxArtRemoteUrl,
            contentDescription = game.title,
            modifier           = Modifier.fillMaxSize()
        )

        // Glass title strip at bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.35f to Color.Black.copy(alpha = 0.55f),
                        1f to Color.Black.copy(alpha = 0.88f)
                    )
                )
                .padding(horizontal = 8.dp, vertical = 10.dp),
            contentAlignment = Alignment.BottomStart
        ) {
            Text(
                text     = game.title,
                style    = MaterialTheme.typography.labelSmall,
                color    = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
