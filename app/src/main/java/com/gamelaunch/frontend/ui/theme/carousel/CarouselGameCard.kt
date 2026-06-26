package com.gamelaunch.frontend.ui.theme.carousel

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.model.GameMedia
import com.gamelaunch.frontend.ui.component.AsyncGameArtwork
import com.gamelaunch.frontend.ui.theme.ElectricBlue
import com.gamelaunch.frontend.ui.theme.NeonPurple

@Composable
fun CarouselGameCard(
    game: Game,
    media: GameMedia?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue    = if (isSelected) 1.13f else 0.87f,
        animationSpec  = spring(dampingRatio = 0.65f, stiffness = 280f),
        label          = "card_scale"
    )
    val shape = RoundedCornerShape(12.dp)

    AsyncGameArtwork(
        localPath          = media?.boxArtLocalPath,
        remoteUrl          = media?.boxArtRemoteUrl,
        contentDescription = game.title,
        modifier = Modifier
            .width(118.dp)
            .height(160.dp)
            .scale(scale)
            .then(
                if (isSelected)
                    Modifier
                        .shadow(
                            elevation    = 28.dp,
                            shape        = shape,
                            spotColor    = ElectricBlue,
                            ambientColor = NeonPurple.copy(alpha = 0.5f)
                        )
                        .border(2.dp, ElectricBlue, shape)
                else
                    Modifier.shadow(8.dp, shape)
            )
            .clip(shape)
            .clickable(onClick = onClick)
    )
}
