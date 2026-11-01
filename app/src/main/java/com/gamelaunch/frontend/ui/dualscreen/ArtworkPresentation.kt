package com.gamelaunch.frontend.ui.dualscreen

import android.app.Presentation
import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Resources
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.gamelaunch.frontend.R
import com.gamelaunch.frontend.domain.model.GameMedia
import com.gamelaunch.frontend.ui.component.DeviceStatusIcons
import com.gamelaunch.frontend.ui.screen.home.AppsViewModel
import com.gamelaunch.frontend.ui.screen.home.SystemPreviewFan
import com.gamelaunch.frontend.ui.theme.AmbientBackground
import com.gamelaunch.frontend.ui.theme.AppTheme
import com.gamelaunch.frontend.ui.theme.BrandBlue
import java.io.File

/**
 * A [Presentation] that renders eOr's game artwork full-bleed on a second physical display (the
 * top panel of a dual-screen handheld). Artwork is passive; settings temporarily allow window
 * focus for touch/IME input, while controller events remain routed through the menu Activity.
 *
 * The content is Compose hosted in a [ComposeView]. Because a Presentation is not an Activity, the
 * ComposeView's ViewTree owners must be wired up manually — we reuse the host [activity] as the
 * lifecycle / view-model-store / saved-state owner. State comes from [ArtworkBus].
 */
class ArtworkPresentation(
    private val activity: ComponentActivity,
    display: Display,
    private val artworkBus: ArtworkBus,
    private val settingsPanels: SettingsPanels,
) : Presentation(activity, display) {

    // The menu owns navigation even when this window hosts interactive settings.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        activity.dispatchKeyEvent(event)

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        activity.dispatchGenericMotionEvent(event)

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

        val composeView = ComposeView(SettingsDisplayContext(activity, context, display)).apply {
            // Reuse the Activity as the owner of the Compose ViewTree (a Presentation has no owners
            // of its own). Without these, ComposeView throws when it composes.
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setContent {
                val settingsVisible = settingsPanels.pane != null
                SideEffect {
                    if (settingsVisible) window?.clearFlags(
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                    )
                    else window?.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                }
                ArtworkScreen(artworkBus, ViewModelProvider(activity)[AppsViewModel::class.java], settingsPanels)
            }
        }
        setContentView(composeView)
    }
}

@Composable
internal fun ArtworkScreen(artworkBus: ArtworkBus, appsViewModel: AppsViewModel, panels: SettingsPanels? = null) {
    val pane = panels?.pane
    if (pane != null) {
        SettingsControls(panels, pane)
        return
    }
    // Collect for the composition's lifetime: artwork can stay visible while a game stops the menu.
    val state by artworkBus.state.collectAsState()
    // Live light/dark so the ambient gradient below re-themes when the user flips the setting,
    // without rebuilding the Presentation. AppTheme lives inside the observed scope for that reason.
    val darkMode by artworkBus.darkMode.collectAsState()
    val settingsActive by artworkBus.settingsActive.collectAsState()
    val theme by artworkBus.theme.collectAsState()
    val showStatusIcons by artworkBus.showStatusIcons.collectAsState()

    AppTheme(darkMode = darkMode, theme = theme) {
        AmbientBackground(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_donkey_silhouette),
                        contentDescription = null,
                        tint = BrandBlue,
                        modifier = Modifier.size(26.dp).padding(end = 6.dp),
                    )
                    Text("e", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                        color = BrandBlue, letterSpacing = 2.sp)
                    Text("Or", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground, letterSpacing = 2.sp)
                    Spacer(Modifier.weight(1f))
                    if (showStatusIcons) {
                        DeviceStatusIcons(tint = MaterialTheme.colorScheme.onBackground)
                    }
                }
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    when {
                        settingsActive -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = null,
                                tint = if (darkMode) Color.White else Color.Black,
                                modifier = Modifier.fillMaxSize(0.34f),
                            )
                        }
                        state.mode == ArtworkMode.IDLE && state.idleSection == TopScreenSection.APPS ->
                            TopScreenApps(appsViewModel)
                        state.mode == ArtworkMode.GAME -> GameTopArtwork(
                            media = state.media,
                            title = state.title,
                            type = state.topImageType,
                            darkMode = darkMode,
                            modifier = Modifier.fillMaxSize(),
                        )
                        state.mode == ArtworkMode.SYSTEM_GRID -> SystemPreviewFan(
                            previewArt = state.systemPreviewArt,
                            selectedIndex = state.fanIndex,
                            active = state.fanActive,
                            selectedTitle = state.fanTitle,
                            onSelect = { index, action ->
                                state.focusedPlatformId?.let {
                                    artworkBus.touchFan(FanTouchAction(it, index, action))
                                }
                            },
                            focusedPlatformId = state.focusedPlatformId,
                            modifier = Modifier.fillMaxSize().padding(20.dp),
                        )
                        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            val sectionIcon = sectionIcon(state.idleSection)
                            if (sectionIcon != null) {
                                Icon(
                                    sectionIcon,
                                    contentDescription = null,
                                    tint = if (darkMode) Color.White else Color.Black,
                                    modifier = Modifier.fillMaxSize(0.34f),
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
}

/** Resolve each selection independently so absent artwork immediately falls back to its title. */
@Composable
private fun GameTopArtwork(
    media: GameMedia?,
    title: String?,
    type: TopScreenImage,
    darkMode: Boolean,
    modifier: Modifier = Modifier
) {
    val resolved = remember(media, type) { resolveTopArt(media, type) }
    val shown = resolved

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

/** Preserve the display's resources/window services while keeping an Activity in the base-context
 * chain for Hilt. Presentation's own context ultimately wraps a ContextImpl, not its host Activity. */
internal class SettingsDisplayContext(
    activity: ComponentActivity,
    val displayContext: Context,
    val controlsDisplay: Display,
) : ContextWrapper(activity) {
    override fun getResources(): Resources = displayContext.resources
    override fun getAssets(): AssetManager = displayContext.assets
    override fun getTheme(): Resources.Theme = displayContext.theme
    override fun getSystemService(name: String): Any? =
        if (name == Context.LAYOUT_INFLATER_SERVICE) {
            LayoutInflater.from(displayContext).cloneInContext(this)
        } else displayContext.getSystemService(name)

    @RequiresApi(Build.VERSION_CODES.R)
    override fun getDisplay(): Display? = displayContext.display
}
