package com.gamelaunch.frontend.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gamelaunch.frontend.ui.theme.AmbientBackground
import com.gamelaunch.frontend.ui.theme.CardColorScheme
import com.gamelaunch.frontend.ui.theme.EorTheme
import com.gamelaunch.frontend.ui.theme.FocusRing
import com.gamelaunch.frontend.ui.theme.IceWhite
import com.gamelaunch.frontend.ui.theme.LocalCardColorScheme
import com.gamelaunch.frontend.ui.theme.LocalDarkMode
import com.gamelaunch.frontend.ui.theme.LocalEorTheme
import com.gamelaunch.frontend.ui.theme.ThemeDraft
import com.gamelaunch.frontend.ui.theme.TileText
import com.gamelaunch.frontend.ui.theme.glassChip
import com.gamelaunch.frontend.ui.theme.glassTile
import com.gamelaunch.frontend.ui.theme.prefersDarkText
import com.gamelaunch.frontend.ui.theme.tileColor
import com.gamelaunch.frontend.ui.theme.tileContainerColor
import com.gamelaunch.frontend.ui.theme.tileTextPrimary
import com.gamelaunch.frontend.ui.theme.tileTextSecondary
import kotlin.math.roundToInt

/** The colours the editor lets you change, in screen order. */
private enum class ColorField(val label: String) {
    ACCENT("Accent"),
    ACCENT2("Second accent"),
    ACCENT3("Highlight"),
    BACKGROUND("Background (dark mode)"),
    TILE("Tile colour"),
    FOCUS_DARK("Focus outline (dark mode)"),
    FOCUS_LIGHT("Focus outline (light mode)");

    fun get(d: ThemeDraft): Color = when (this) {
        ACCENT -> d.accent
        ACCENT2 -> d.accent2
        ACCENT3 -> d.accent3
        BACKGROUND -> d.background
        TILE -> d.tileColor
        FOCUS_DARK -> d.focusDark
        FOCUS_LIGHT -> d.focusLight
    }

    fun set(d: ThemeDraft, c: Color): ThemeDraft = when (this) {
        ACCENT -> d.copy(accent = c)
        ACCENT2 -> d.copy(accent2 = c)
        ACCENT3 -> d.copy(accent3 = c)
        BACKGROUND -> d.copy(background = c)
        TILE -> d.copy(tileColor = c)
        FOCUS_DARK -> d.copy(focusDark = c)
        FOCUS_LIGHT -> d.copy(focusLight = c)
    }
}

/**
 * Theme editor: the controls on the left, a live mini home screen on the right (stacked on narrow
 * screens). The whole screen is drawn in the draft theme, so buttons and focus rings change as you
 * edit. Everything is a d-pad focus target and pickers open inline (no dialogs), so it works with
 * a controller.
 */
@Composable
fun ThemeEditorScreen(onBack: () -> Unit, viewModel: ThemeEditorViewModel) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.saved) { if (state.saved) onBack() }
    val preview = state.preview

    CompositionLocalProvider(
        LocalEorTheme provides preview,
        LocalCardColorScheme provides preview.cards
    ) {
        SettingsDetailScaffold(
            title = if (state.isNew) "New theme" else "Edit theme",
            onBack = onBack,
            scrollable = false
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                if (maxWidth >= 640.dp) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(
                            Modifier.weight(1.1f).fillMaxSize().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) { EditorControls(state, viewModel) }
                        PreviewPane(preview, Modifier.weight(1f))
                    }
                } else {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PreviewPane(preview, Modifier.fillMaxWidth())
                        EditorControls(state, viewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.EditorControls(state: ThemeEditorState, viewModel: ThemeEditorViewModel) {
    val draft = state.draft
    var open by rememberSaveable { mutableStateOf<ColorField?>(null) }

    GatedTextField(
        label = "Name",
        value = draft.name,
        onValueChange = { name -> viewModel.update { it.copy(name = name.take(32)) } },
        modifier = Modifier.fillMaxWidth()
    )
    if (state.isNew) {
        Text(
            "Saved as a new theme — the built-in one stays as it is.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    SettingsSectionHeader("Colours")
    SettingsCard {
        listOf(ColorField.ACCENT, ColorField.ACCENT2, ColorField.ACCENT3, ColorField.BACKGROUND).forEach { field ->
            ColorRow(field, draft, open == field, onToggle = { open = if (open == field) null else field }) { c ->
                viewModel.update { field.set(it, c) }
            }
        }
    }

    SettingsSectionHeader("Home tiles")
    SettingsCard {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(
                CardColorScheme.RAINBOW to "Rainbow",
                CardColorScheme.BLACK_WHITE to "Greys",
                CardColorScheme.MONOCHROME to "One colour"
            ).forEach { (scheme, label) ->
                BackgroundModeChip(
                    label = label,
                    selected = draft.tiles == scheme,
                    onClick = { viewModel.update { it.copy(tiles = scheme) } },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        if (draft.tiles == CardColorScheme.MONOCHROME) {
            Spacer(Modifier.height(8.dp))
            ColorRow(ColorField.TILE, draft, open == ColorField.TILE, onToggle = {
                open = if (open == ColorField.TILE) null else ColorField.TILE
            }) { c -> viewModel.update { it.copy(tileColor = c) } }
        }
        Spacer(Modifier.height(4.dp))
        CardSwitchRow(
            label = if (state.base.wallpaper != null) "Colour glows over the image" else "Colour glows in dark mode",
            checked = draft.glows,
            onCheckedChange = { on -> viewModel.update { it.copy(glows = on) } }
        )
    }

    SettingsSectionHeader("Focus outline")
    SettingsCard {
        listOf(ColorField.FOCUS_DARK, ColorField.FOCUS_LIGHT).forEach { field ->
            ColorRow(field, draft, open == field, onToggle = { open = if (open == field) null else field }) { c ->
                viewModel.update { field.set(it, c) }
            }
        }
    }

    state.error?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        GradientFillButton(
            text = if (state.isNew) "Save as new theme" else "Save",
            onClick = viewModel::save,
            modifier = Modifier.weight(1f),
            loading = state.saving
        )
        GradientOutlineButton(
            text = "Reset",
            onClick = viewModel::reset,
            modifier = Modifier.weight(1f)
        )
    }
    if (state.base.wallpaper != null) {
        Text(
            "The background image comes along — change it under Appearance → Background image.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * A text field the d-pad can pass over: it shows as a plain row until selected (tap or A), and
 * only then becomes an editable field with the keyboard. Moving focus onto an always-live field
 * pops the keyboard and traps the controller, and this screen opens with one at the top.
 */
@Composable
private fun GatedTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false
) {
    var editing by remember { mutableStateOf(false) }
    if (editing) {
        val focus = remember { FocusRequester() }
        val keyboard = LocalSoftwareKeyboardController.current
        var hadFocus by remember { mutableStateOf(false) }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            isError = isError,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); editing = false }),
            modifier = modifier
                .focusRequester(focus)
                .onFocusChanged {
                    if (it.isFocused) hadFocus = true
                    else if (hadFocus) editing = false
                }
        )
        LaunchedEffect(Unit) { focus.requestFocus() }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .border(1.dp, if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                .dpadFocusable(shape = RoundedCornerShape(6.dp)) { editing = true }
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            }
            Icon(Icons.Default.Edit, contentDescription = "Edit $label", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

/** A colour setting: label + swatch + hex; selecting it opens the picker inline underneath. */
@Composable
private fun ColorRow(
    field: ColorField,
    draft: ThemeDraft,
    expanded: Boolean,
    onToggle: () -> Unit,
    onChange: (Color) -> Unit
) {
    val color = field.get(draft)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .dpadFocusable(onClick = onToggle)
            .padding(horizontal = 6.dp, vertical = 8.dp)
    ) {
        Text(
            field.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            hex(color),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(10.dp))
        Swatch(color, size = 28)
    }
    if (expanded) {
        ColorPicker(color, onChange)
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun Swatch(color: Color, size: Int, selected: Boolean = false) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                if (selected) 3.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                CircleShape
            )
    )
}

private val Presets = listOf(
    0xFFFF5C5C, 0xFFFF8A3D, 0xFFFFC04D, 0xFFE6E04A, 0xFF8BC34A, 0xFF2ECC8F, 0xFF26C6DA, 0xFF3E8BFF,
    0xFF4C8DF6, 0xFF7C4DFF, 0xFFA17CFF, 0xFFFF6BCB, 0xFFFF4D8D, 0xFF8D6E63, 0xFF9AA0AC, 0xFFFFFFFF,
    0xFF000000, 0xFF111318, 0xFF14081F, 0xFF0F2A0F, 0xFF07121F, 0xFF2E3440, 0xFF282A36, 0xFF1A1F2B
).map { Color(it) }

/**
 * Preset swatches, then hue / saturation / brightness sliders and a hex field — every part
 * reachable and adjustable with a d-pad (sliders take left/right).
 */
@Composable
private fun ColorPicker(color: Color, onChange: (Color) -> Unit) {
    // Kept as HSV so hue survives dragging saturation to grey and back.
    var hsv by remember { mutableStateOf(toHsv(color)) }
    LaunchedEffect(color) { if (fromHsv(hsv) != color) hsv = toHsv(color) }
    fun setHsv(h: Float = hsv[0], s: Float = hsv[1], v: Float = hsv[2]) {
        hsv = floatArrayOf(h, s, v)
        onChange(fromHsv(hsv))
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Presets.forEach { p ->
                Box(Modifier.dpadFocusable(shape = CircleShape) { onChange(p) }) {
                    Swatch(p, size = 32, selected = p == color)
                }
            }
        }
        PickerSlider("Hue", "${hsv[0].roundToInt()}°", hsv[0], 0f..360f, step = 6f) { setHsv(h = it) }
        PickerSlider("Saturation", "${(hsv[1] * 100).roundToInt()}%", hsv[1], 0f..1f, step = 0.04f) { setHsv(s = it) }
        PickerSlider("Brightness", "${(hsv[2] * 100).roundToInt()}%", hsv[2], 0f..1f, step = 0.04f) { setHsv(v = it) }
        var text by remember(color) { mutableStateOf(hex(color)) }
        GatedTextField(
            label = "Hex",
            value = text,
            onValueChange = { v ->
                text = v.take(7)
                parseHex(text)?.let(onChange)
            },
            isError = parseHex(text) == null,
            modifier = Modifier.width(200.dp)
        )
    }
}

@Composable
private fun PickerSlider(
    label: String,
    value: String,
    current: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    onChange: (Float) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(84.dp))
        Slider(
            value = current,
            onValueChange = onChange,
            valueRange = range,
            // Left/right nudge the value instead of moving focus (the screen's key handler would
            // otherwise take them) — this is how the slider is used with a d-pad.
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focused = it.hasFocus }
                .onPreviewKeyEvent { e ->
                    val dir = when (e.key) {
                        Key.DirectionLeft -> -1
                        Key.DirectionRight -> 1
                        else -> return@onPreviewKeyEvent false
                    }
                    if (e.type == KeyEventType.KeyDown) onChange((current + dir * step).coerceIn(range.start, range.endInclusive))
                    true
                }
                .border(2.dp, if (focused) FocusRing else Color.Transparent, RoundedCornerShape(10.dp))
                .padding(horizontal = 6.dp),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
        Text(value, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(44.dp).padding(start = 6.dp))
    }
}

/** Mini home screen in the draft theme, with a dark / light switch of its own. */
@Composable
private fun PreviewPane(theme: EorTheme, modifier: Modifier) {
    var dark by rememberSaveable { mutableStateOf(true) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            BackgroundModeChip(label = "Dark", selected = dark, onClick = { dark = true }, modifier = Modifier.weight(1f))
            BackgroundModeChip(label = "Light", selected = !dark, onClick = { dark = false }, modifier = Modifier.weight(1f))
        }
        CompositionLocalProvider(LocalDarkMode provides dark) {
            AmbientBackground(Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(16.dp))) {
                MiniHome(theme, dark)
            }
        }
    }
}

@Composable
private fun MiniHome(theme: EorTheme, dark: Boolean) {
    val textColor = if (dark) IceWhite else TileText
    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("e", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = theme.accent)
            Text("Or", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = textColor)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Games", "Favorites", "Recent").forEachIndexed { i, label ->
                val selected = i == 0
                Box(
                    Modifier
                        .glassChip(RoundedCornerShape(50), selected = selected, accent = theme.accent)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = when {
                            !selected -> textColor.copy(alpha = 0.8f)
                            prefersDarkText(theme.accent) -> TileText
                            else -> Color.White
                        }
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("Atari 2600", "NES", "Arcade").forEachIndexed { i, label ->
                val color = tileColor(i)
                val focused = i == 0
                val fill = tileContainerColor(color, selected = focused)
                Column(
                    Modifier
                        .weight(1f)
                        .height(86.dp)
                        .glassTile(RoundedCornerShape(14.dp), color, selected = focused)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = tileTextPrimary(fill), maxLines = 1)
                    Text("${(i + 1) * 87} games", style = MaterialTheme.typography.labelSmall, color = tileTextSecondary(fill))
                }
            }
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(theme.accent, theme.accent2)))
                .padding(horizontal = 18.dp, vertical = 8.dp)
        ) {
            Text("Play", style = MaterialTheme.typography.labelLarge, color = Color.White)
        }
    }
}

private fun hex(c: Color) = "#%06X".format(c.toArgb() and 0xFFFFFF)

private fun parseHex(s: String): Color? {
    val t = s.trim().removePrefix("#")
    if (t.length != 6 || !t.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
    return Color(0xFF000000 or t.toLong(16))
}

private fun toHsv(c: Color): FloatArray = FloatArray(3).also { android.graphics.Color.colorToHSV(c.toArgb(), it) }

private fun fromHsv(hsv: FloatArray): Color = Color(android.graphics.Color.HSVToColor(hsv))
