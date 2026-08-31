package com.gamelaunch.frontend.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import coil.size.Scale
import com.gamelaunch.frontend.BuildConfig
import com.gamelaunch.frontend.image.boxArtThumbnail

/**
 * Canonical decode size for box-art tiles on the full build. Box art is always a local file and
 * Coil never disk-caches local files, so the in-memory cache is the ONLY thing keeping a cover from
 * re-decoding off disk on every scroll. Decoding covers at this bounded size (instead of the source's
 * full resolution) makes each cached bitmap ~100–130 KB in RGB_565 rather than ~1 MB, so a whole
 * system's covers stay resident together and never reload when you scroll away and back. A tile is
 * only a few hundred px wide, so 256 is already sharper than it renders. The game-detail hero uses a
 * separate full-res path (see [fullRes]) so this compact size never blurs the big art.
 *
 * Lite (LOW_POWER) is deliberately left on Coil's ORIGINAL-size path — see the plan's hard constraint
 * that the lite build stay byte-for-byte unchanged.
 */
const val BOX_ART_TILE_PX = 256

@Composable
fun AsyncGameArtwork(
    localPath: String?,
    remoteUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    packageName: String? = null,
    // The game-detail hero shows box art large, so it opts out of the compact tile size and decodes
    // full-res under a distinct memory-cache key — keeping the grid's tiny shared thumbnail from
    // being upscaled into the detail view (and vice-versa).
    fullRes: Boolean = false,
    // When true, DON'T kick off a new cover load — used while the grid is actively scrolling so the
    // rows flying past the viewport don't flood the decode pipeline with covers the user never stops
    // on (which would queue the landing screenful behind hundreds of throwaway decodes and make it
    // trickle in). A tile that has ALREADY loaded keeps showing its art; only not-yet-loaded tiles
    // hold at the placeholder until the scroll settles, then load. Prefetch keeps the near-viewport
    // covers warm so a settle is usually a straight cache hit anyway.
    pauseLoad: Boolean = false
) {
    // Prefer a local file, falling back to the remote URL. We deliberately do NOT probe the
    // filesystem here (File.exists()/length()): that was main-thread I/O running on every tile
    // (re)composition while scrolling, and boxArtLocalPath is only written to Room after a
    // successful download, so a present-but-missing local file is a rare edge (e.g. the user
    // cleared their media dir) that the error state below handles gracefully.
    val data = remember(localPath, remoteUrl) { localPath ?: remoteUrl }

    // A stable, size-independent memory-cache key so a cover decoded once (e.g. prewarmed behind
    // the splash, or seen at a different tile size) is reused everywhere. Without this, Coil's
    // default key includes the request size, so the same art re-decodes per size and flashes the
    // grey placeholder before crossfading in.
    val context = LocalContext.current
    val request = remember(data, fullRes) {
        val builder = ImageRequest.Builder(context)
            .data(data)
            .crossfade(true)
        when {
            // Detail hero: full resolution, under its own key so it never shares (or evicts) the
            // grid's compact thumbnail.
            fullRes -> builder
                .memoryCacheKey(data?.let { "$it#full" })
            // Full build tiles: decode once at the compact tile size and cache under the plain path
            // key so every warmer (prewarm, neighbour prefetch) and every surface (grid/list/carousel)
            // shares the one resident bitmap — the fix for covers reloading on scroll-back.
            !BuildConfig.LOW_POWER -> builder
                .size(BOX_ART_TILE_PX, BOX_ART_TILE_PX)
                .scale(Scale.FILL)
                .memoryCacheKey(data)
                .boxArtThumbnail()
            // Lite: unchanged — Coil's default (ORIGINAL) size, keyed by the plain path.
            else -> builder
                .memoryCacheKey(data)
        }.build()
    }

    // Plain AsyncImage (not SubcomposeAsyncImage): subcomposition per tile is a well-known
    // LazyGrid scroll-jank source on weak hardware. We render the loading/error visuals from a
    // lightweight onState-driven overlay instead of subcomposed slots — a single recomposition per
    // tile on load, versus subcomposing every tile every frame.
    var state by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }

    // Once a tile has painted its art, keep feeding it the real request so a scroll never blanks an
    // already-loaded cover. Only a tile that hasn't loaded yet is held back while the grid scrolls.
    var hasLoaded by remember(data) { mutableStateOf(false) }
    val model = if (pauseLoad && !hasLoaded) null else request

    Box(modifier) {
        AsyncImage(
            model              = model,
            contentDescription = contentDescription,
            contentScale       = contentScale,
            modifier           = Modifier.fillMaxSize(),
            onState            = {
                if (it is AsyncImagePainter.State.Success) hasLoaded = true
                state = it
            }
        )

        when (state) {
            is AsyncImagePainter.State.Success -> Unit  // art is drawn by AsyncImage
            is AsyncImagePainter.State.Error   -> ArtworkFallback(packageName)
            else -> Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    }
}

/** Shown when box art fails to load: the app's launcher icon for Android apps, else a generic pad. */
@Composable
private fun ArtworkFallback(packageName: String?) {
    val context = LocalContext.current
    val appIconBitmap = remember(packageName) {
        if (packageName != null) {
            runCatching {
                context.packageManager.getApplicationIcon(packageName)
                    .toBitmap(width = 144, height = 144)
                    .asImageBitmap()
            }.getOrNull()
        } else null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (appIconBitmap != null) {
            Image(
                bitmap = appIconBitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(0.5f)
            )
        } else {
            Icon(
                Icons.Default.SportsEsports,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
