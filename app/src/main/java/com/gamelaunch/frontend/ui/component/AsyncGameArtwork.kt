package com.gamelaunch.frontend.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import java.io.File

@Composable
fun AsyncGameArtwork(
    localPath: String?,
    remoteUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    // Prefer a local file that actually exists; fall back to remote URL
    val data = remember(localPath, remoteUrl) {
        val file = localPath?.let { File(it) }
        when {
            file != null && file.exists() && file.length() > 0 -> file
            remoteUrl != null -> remoteUrl
            file != null -> file  // last resort: let Coil try (shows error if truly missing)
            else -> null
        }
    }

    // A stable, size-independent memory-cache key so a cover decoded once (e.g. prewarmed behind
    // the splash, or seen at a different tile size) is reused everywhere. Without this, Coil's
    // default key includes the request size, so the same art re-decodes per size and flashes the
    // grey placeholder before crossfading in.
    val cacheKey = remember(data) {
        when (data) {
            is File   -> data.absolutePath
            is String -> data
            else      -> null
        }
    }

    SubcomposeAsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(data)
            .memoryCacheKey(cacheKey)
            .crossfade(true)
            .build(),
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        loading = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        error = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.SportsEsports,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}
