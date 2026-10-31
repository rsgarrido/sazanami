package io.github.rsgarrido.sazanami.ui.player.pockethorizon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class PocketHorizonStyleTest {
    @Test
    fun normalTextRemainsReadableAcrossTheStaticBackdropAndTranslucentSurfaces() {
        val style = PocketHorizonStyle.Default
        val backgrounds = listOf(style.displayTop, style.displayBottom).flatMap { base ->
            val lit = style.horizon.copy(alpha = .5f).compositeOver(base)
            val horizon = style.horizon.copy(alpha = .12f).compositeOver(lit)
            listOf(base, lit, horizon, style.surface, style.surface.copy(alpha = .65f).compositeOver(horizon))
        }
        listOf(style.text, style.secondaryText).forEach { foreground ->
            backgrounds.forEach { background ->
                assertTrue("Text contrast must remain at least 4.5:1", contrast(foreground, background) >= 4.5f)
            }
        }
        assertTrue("Prominent transport icon contrast", contrast(style.onAccent, style.accent) >= 4.5f)
    }

    private fun contrast(a: Color, b: Color): Float {
        val first = a.luminance()
        val second = b.luminance()
        return (maxOf(first, second) + .05f) / (minOf(first, second) + .05f)
    }
}
