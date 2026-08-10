package com.gamelaunch.frontend.ui.dualscreen

import android.app.Presentation
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.gamelaunch.frontend.domain.model.GameMedia
import com.gamelaunch.frontend.ui.screen.home.SystemPreviewFan
import com.gamelaunch.frontend.ui.theme.AmbientBackground
import com.gamelaunch.frontend.ui.theme.AppTheme
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File

/**
 * A [Presentation] that renders eOr's game artwork full-bleed on a second physical display (the
 * top panel of a dual-screen handheld). It is passive: [FLAG_NOT_FOCUSABLE] guarantees it never
 * steals key/controller focus from the interactive menu Activity on the other screen.
 *
 * The content is Compose hosted in a [ComposeView]. Because a Presentation is not an Activity, the
 * ComposeView's ViewTree owners must be wired up manually — we reuse the host [activity] as the
 * lifecycle / view-model-store / saved-state owner. State comes from [ArtworkBus].
 */
class ArtworkPresentation(
    private val activity: ComponentActivity,
    display: Display,
    private val artworkBus: ArtworkBus
) : Presentation(activity, display) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Passive display: black backdrop, never focusable so the menu Activity keeps input focus.
        window?.apply {
            setBackgroundDrawable(ColorDrawable(AndroidColor.BLACK))
            addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            // Full-bleed: no title, cover the whole panel.
            decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        }

        val composeView = ComposeView(context).apply {
            // Reuse the Activity as the owner of the Compose ViewTree (a Presentation has no owners
            // of its own). Without these, ComposeView throws when it composes.
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setContent {
                ArtworkScreen(artworkBus)
            }
        }
        setContentView(composeView)
    }
}

@Composable
private fun ArtworkScreen(artworkBus: ArtworkBus) {
    val state by artworkBus.state.collectAsState()
    // Live light/dark so the ambient gradient below re-themes when the user flips the setting,
    // without rebuilding the Presentation. AppTheme lives inside the observed scope for that reason.
    val darkMode by artworkBus.darkMode.collectAsState()
    val settingsActive by artworkBus.settingsActive.collectAsState()

    AppTheme(darkMode = darkMode) {
        // Continue the app's ambient gradient onto this screen so both panels read as one surface,
        // instead of a flat black fill.
        AmbientBackground(Modifier.fillMaxSize()) {
            when {
                // In the Settings area the top panel shows a gear over the gradient, not the last
                // game's art — mirrors the gear the user just selected on the bottom screen.
                settingsActive -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        // Pure black on light, pure white on dark, so the gear reads cleanly against
                        // the ambient gradient in either theme.
                        tint = if (darkMode) Color.White else Color.Black,
                        modifier = Modifier.fillMaxSize(0.34f)
                    )
                }
                else -> when (state.mode) {
                // Game select + game detail: show the game's art on the top panel. Which art depends
                // on the user's dual-screen "top image" choice — marquee (wheel-logo, the default),
                // screenshot, or miximage — each falling back through the others, then to the title.
                ArtworkMode.GAME -> GameTopArtwork(
                    media = state.media,
                    title = state.title,
                    type = state.topImageType,
                    darkMode = darkMode,
                    modifier = Modifier.fillMaxSize()
                )

                // The fanned box-art preview for the focused system — the same one the single-screen
                // menu shows, now given the whole top panel.
                ArtworkMode.SYSTEM_GRID -> SystemPreviewFan(
                    previewArt = state.systemPreviewArt,
                    focusedPlatformId = state.focusedPlatformId,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                )

                    ArtworkMode.IDLE -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        val sectionIcon = sectionIcon(state.idleSection)
                        if (sectionIcon != null) {
                            // Mirror the bottom screen's active tab with its icon, themed like the
                            // Settings gear: solid white on dark, solid black on light.
                            Icon(
                                imageVector = sectionIcon,
                                contentDescription = null,
                                tint = if (darkMode) Color.White else Color.Black,
                                modifier = Modifier.fillMaxSize(0.34f)
                            )
                        } else {
                            BrandPlaceholder()
                        }
                    }
                }
            }
        }
    }
}

private enum class TopArtKind { LOGO, PHOTO }
private data class TopArt(val path: String, val kind: TopArtKind)

/**
 * Resolve which image the top panel should show for [type], falling back through the other media
 * so a game missing (say) a miximage still shows something. LOGO art (the marquee) gets the centred
 * glow treatment; PHOTO art (screenshot / miximage) is shown full-bleed.
 */
private fun resolveTopArt(media: GameMedia?, type: TopScreenImage): TopArt? {
    val logo = marqueeImage(media)?.let { TopArt(it, TopArtKind.LOGO) }
    val shot = media?.effectiveScreenshot?.takeIf { it.isNotBlank() }?.let { TopArt(it, TopArtKind.PHOTO) }
    val mix  = media?.effectiveMiximage?.takeIf { it.isNotBlank() }?.let { TopArt(it, TopArtKind.PHOTO) }
    return when (type) {
        TopScreenImage.MARQUEE    -> logo ?: mix ?: shot
        TopScreenImage.SCREENSHOT -> shot ?: mix ?: logo
        TopScreenImage.MIXIMAGE   -> mix ?: shot ?: logo
    }
}

/**
 * The selected game's art for the top panel, per the chosen [type]. The last successfully-resolved
 * image is held while the selection changes, so switching systems (which briefly clears the media)
 * never flashes the panel back to a blank/title state — it only ever crossfades to the next real art.
 */
@Composable
private fun GameTopArtwork(
    media: GameMedia?,
    title: String?,
    type: TopScreenImage,
    darkMode: Boolean,
    modifier: Modifier = Modifier
) {
    val resolved = remember(media, type) { resolveTopArt(media, type) }
    var lastShown by remember { mutableStateOf<TopArt?>(null) }
    LaunchedEffect(resolved) { if (resolved != null) lastShown = resolved }
    val shown = resolved ?: lastShown

    when {
        shown == null ->
            MarqueeArtwork(marquee = null, title = title, darkMode = darkMode, modifier = modifier)
        shown.kind == TopArtKind.LOGO ->
            MarqueeArtwork(marquee = shown.path, title = title, darkMode = darkMode, modifier = modifier)
        else ->
            PhotoArtwork(path = shown.path, modifier = modifier)
    }
}

/** A screenshot or miximage shown fitted over the ambient gradient. */
@Composable
private fun PhotoArtwork(path: String, modifier: Modifier = Modifier) {
    val data: Any = if (path.startsWith("http")) path else File(path)
    val request = ImageRequest.Builder(LocalContext.current)
        .data(data)
        .crossfade(true)
        .build()
    Box(modifier.padding(16.dp), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * The game's marquee (a.k.a. wheel logo) rendered centred over the ambient gradient, or the game
 * title as text when no marquee is on disk. Only a local file is used — never a remote URL — so the
 * top panel never streams over the network (important on low-power/dual-screen).
 */
@Composable
private fun MarqueeArtwork(
    marquee: String?,
    title: String?,
    darkMode: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier.padding(32.dp), contentAlignment = Alignment.Center) {
        if (marquee != null) {
            // A local file when imported/cached, or a remote URL (small logo PNG) as a fallback.
            val data: Any = if (marquee.startsWith("http")) marquee else File(marquee)
            val request = ImageRequest.Builder(LocalContext.current)
                .data(data)
                .crossfade(true)
                .build()

            // Shadow / glow: a blurred, tinted copy of the same logo drawn behind the crisp one, so
            // the effect follows the logo's alpha shape rather than its bounding box. Light mode gets
            // a soft dark drop shadow offset downward; dark mode gets a subtle centred outer glow.
            // blur() is a no-op below API 31, so we skip the layer there to avoid a hard silhouette.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (darkMode) {
                    AsyncImage(
                        model = request,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        colorFilter = ColorFilter.tint(Color.White),
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(26.dp, BlurredEdgeTreatment.Unbounded)
                            .alpha(0.30f)
                    )
                } else {
                    AsyncImage(
                        model = request,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        colorFilter = ColorFilter.tint(Color.Black),
                        modifier = Modifier
                            .fillMaxSize()
                            .offset(y = 6.dp)
                            .blur(14.dp, BlurredEdgeTreatment.Unbounded)
                            .alpha(0.45f)
                    )
                }
            }

            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else if (!title.isNullOrBlank()) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge.copy(
                    shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 3f), 12f)
                ),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * The game's marquee (a.k.a. wheel logo): the local file if it's on disk, otherwise the remote URL
 * as a fallback. ES-DE's `downloaded_media/marquees` and ScreenScraper's `wheel` both feed
 * [GameMedia.wheelLogoLocalPath]/[GameMedia.wheelLogoRemoteUrl].
 */
private fun marqueeImage(media: GameMedia?): String? {
    val m = media ?: return null
    val local = m.wheelLogoLocalPath
        ?.takeIf { it.isNotBlank() && File(it).let { f -> f.exists() && f.length() > 0 } }
    return local ?: m.wheelLogoRemoteUrl?.takeIf { it.isNotBlank() }
}

/**
 * The icon mirroring a non-game home tab on the top panel — matched to the bottom screen's tab bar
 * (see HomeScreen's tabSpecs). [TopScreenSection.BRANDING] returns null so the neutral brand
 * placeholder is shown instead.
 */
private fun sectionIcon(section: TopScreenSection): ImageVector? = when (section) {
    TopScreenSection.FAVORITES        -> Icons.Default.Favorite
    TopScreenSection.RECENTLY_PLAYED  -> Icons.Default.History
    TopScreenSection.APPS             -> Icons.Default.Apps
    TopScreenSection.RETROACHIEVEMENTS -> Icons.Default.EmojiEvents
    TopScreenSection.FRIENDS          -> Icons.Default.Group
    TopScreenSection.BRANDING         -> null
}

@Composable
private fun BrandPlaceholder() {
    Icon(
        imageVector = Icons.Default.SportsEsports,
        contentDescription = null,
        tint = Color.White.copy(alpha = 0.25f),
        modifier = Modifier.padding(48.dp)
    )
}
