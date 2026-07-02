package com.gamelaunch.frontend.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.gamelaunch.frontend.R
import java.io.File

@Composable
fun VideoPlayer(
    videoPath: String?,
    shouldPlay: Boolean,
    isMuted: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    if (videoPath == null) {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.BrokenImage,
                contentDescription = "No video",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = ExoPlayer.REPEAT_MODE_ONE
            volume = if (isMuted) 0f else 1f
        }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    LaunchedEffect(videoPath) {
        val uri = if (videoPath.startsWith("http")) {
            android.net.Uri.parse(videoPath)
        } else {
            android.net.Uri.fromFile(File(videoPath))
        }
        exoPlayer.setMediaItem(MediaItem.fromUri(uri))
        exoPlayer.prepare()
    }

    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    LaunchedEffect(shouldPlay) {
        if (shouldPlay) exoPlayer.play() else exoPlayer.pause()
    }

    AndroidView(
        factory = { ctx ->
            // Inflated from XML because surface_type (texture_view) has no programmatic setter.
            (LayoutInflater.from(ctx).inflate(R.layout.texture_player_view, null) as PlayerView).apply {
                player = exoPlayer
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                // Don't steal D-pad / button key focus from the Compose detail screen — otherwise
                // its onKeyEvent (B = back) never fires while a preview video is playing.
                isFocusable = false
                isFocusableInTouchMode = false
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            }
        },
        onRelease = { view ->
            // Detach the player from the view before release so ExoPlayer never has to wait on
            // a surface that is being torn down mid-navigation.
            view.player = null
        },
        modifier = modifier
    )
}
