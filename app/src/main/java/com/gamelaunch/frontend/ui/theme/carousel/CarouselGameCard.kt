package com.gamelaunch.frontend.ui.theme.carousel

import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.model.GameMedia
import com.gamelaunch.frontend.ui.component.AsyncGameArtwork
import com.gamelaunch.frontend.ui.component.boxArtAspectRatio
import com.gamelaunch.frontend.ui.perf.rememberSelectionScale
import com.gamelaunch.frontend.ui.theme.ElectricBlue

@Composable
fun CarouselGameCard(
    game: Game,
    media: GameMedia?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale = rememberSelectionScale(
        active = isSelected,
        restScale = 0.87f,
        activeScale = 1.13f,
        fullSpec = spring(dampingRatio = 0.65f, stiffness = 280f),
        label = "card_scale"
    )
    val shape = RoundedCornerShape(12.dp)

    val aspect = boxArtAspectRatio(game.platformId)
    val baseWidth = 118.dp
    val maxCardHeight = baseWidth / 0.72f
    val rawHeight = baseWidth / aspect
    val cardHeight = if (rawHeight > maxCardHeight) maxCardHeight else rawHeight
    val cardWidth  = if (rawHeight > maxCardHeight) maxCardHeight * aspect else baseWidth

    ElevatedCard(
        onClick = onClick,
        shape = shape,
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (isSelected) 16.dp else 4.dp
        ),
        modifier = Modifier
            .width(cardWidth)
            .height(cardHeight)
            .scale(scale)
            .then(if (isSelected) Modifier.border(2.5.dp, ElectricBlue, shape) else Modifier)
    ) {
        AsyncGameArtwork(
            localPath          = media?.boxArtLocalPath,
            remoteUrl          = media?.boxArtRemoteUrl,
            contentDescription = game.title,
            modifier           = Modifier.height(cardHeight).width(cardWidth),
            packageName        = if (game.platformId == "android") game.romFilename else null
        )
    }
}
