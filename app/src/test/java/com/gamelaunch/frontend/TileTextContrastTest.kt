package com.gamelaunch.frontend

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.gamelaunch.frontend.ui.theme.CardColorConfig
import com.gamelaunch.frontend.ui.theme.CardColorScheme
import com.gamelaunch.frontend.ui.theme.TilePalette
import com.gamelaunch.frontend.ui.theme.prefersDarkText
import com.gamelaunch.frontend.ui.theme.tilePalette
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TileTextContrastTest {

    // Mirrors tileContainerColor in dark mode (it's @Composable, so not callable here).
    private fun darkFocused(c: Color) = lerp(c, Color.Black, 0.10f)
    private fun darkUnfocused(c: Color) = lerp(c, Color.Black, 0.55f)

    private val greys = tilePalette(CardColorConfig(CardColorScheme.BLACK_WHITE))

    @Test fun `light focused tiles get dark text`() {
        assertTrue(prefersDarkText(darkFocused(Color(0xFFCFD3DB))))   // lightest B&W grey
        assertTrue(prefersDarkText(darkFocused(Color(0xFFFFC04D))))   // Default's amber tile
        assertTrue(prefersDarkText(darkFocused(Color(0xFFB5D82A))))   // lime (Game Boy-like)
        assertTrue(prefersDarkText(Color.White))
    }

    @Test fun `dark tiles keep light text`() {
        assertFalse(prefersDarkText(Color.Black))
        assertFalse(prefersDarkText(darkFocused(Color(0xFF7C4DFF))))  // violet
    }

    @Test fun `unfocused dark-mode tiles are unchanged - still light text`() {
        (TilePalette + greys).forEach { c ->
            assertFalse("unfocused ${c} should keep light text", prefersDarkText(darkUnfocused(c)))
        }
    }
}
