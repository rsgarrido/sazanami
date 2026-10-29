package io.github.rsgarrido.sazanami.ui.player.pocketadvance

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import io.github.rsgarrido.sazanami.data.PocketAdvanceAppearance
import io.github.rsgarrido.sazanami.data.PocketAdvanceConsoleFinish
import io.github.rsgarrido.sazanami.data.PocketAdvanceLcdPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PocketAdvanceOverlayColorsTest {
    private val inherited = darkColorScheme()

    @Test
    fun originalLcdAndInactivePresentationsRetainTheExactInheritedSchemeAndLegacyLyrics() {
        PocketAdvanceLcdPalette.entries.forEach { lcd ->
            val palette = PocketAdvancePalette.from(PocketAdvanceAppearance(lcdPalette = lcd))
            assertSame(inherited, pocketAdvanceOverlayColorScheme(inherited, palette, active = false))
            assertNull(pocketAdvanceLyricsColors(palette, active = false))
        }
        val originalLcd = PocketAdvancePalette.from(PocketAdvanceAppearance(consoleFinish = PocketAdvanceConsoleFinish.OCEAN_BLUE))
        assertSame(inherited, pocketAdvanceOverlayColorScheme(inherited, originalLcd, active = true))
        assertSame(inherited, pocketAdvanceOverlayColorScheme(inherited, originalLcd, active = true, inverse = true))
        assertNull(pocketAdvanceLyricsColors(originalLcd, active = true))
    }

    @Test
    fun customQueueSurfacesAndAccentsUseTheSelectedLcdWithoutChangingErrorColors() {
        listOf(PocketAdvanceLcdPalette.CLASSIC_GREEN, PocketAdvanceLcdPalette.WARM_AMBER).forEach { lcd ->
            val palette = PocketAdvancePalette.from(PocketAdvanceAppearance(lcdPalette = lcd))
            val colors = pocketAdvanceOverlayColorScheme(inherited, palette, active = true)
            assertEquals(palette.Screen, colors.surface)
            assertEquals(palette.ScreenShade, colors.surfaceContainerHigh)
            assertEquals(palette.Ink, colors.onSurface)
            assertEquals(palette.MutedInk, colors.onSurfaceVariant)
            assertEquals(palette.Ink, colors.primary)
            assertEquals(palette.Screen, colors.onPrimary)
            assertEquals(inherited.error, colors.error)
            assertEquals(inherited.onErrorContainer, colors.onErrorContainer)
        }
    }

    @Test
    fun customLyricsUseCoordinatedInverseColorsAndKeepReturnActionReadable() {
        listOf(PocketAdvanceLcdPalette.CLASSIC_GREEN, PocketAdvanceLcdPalette.WARM_AMBER).forEach { lcd ->
            val palette = PocketAdvancePalette.from(PocketAdvanceAppearance(lcdPalette = lcd))
            val lyricColors = pocketAdvanceLyricsColors(palette, active = true)
            assertNotNull(lyricColors)
            assertEquals(palette.Ink, lyricColors!!.background)
            assertEquals(palette.Screen, lyricColors.foreground)
            assertEquals(palette.ScreenShade, lyricColors.returnContainer)
            assertEquals(palette.Ink, lyricColors.returnContent)
            val colors = pocketAdvanceOverlayColorScheme(inherited, palette, active = true, inverse = true)
            assertEquals(palette.Ink, colors.surface)
            assertEquals(palette.Screen, colors.onSurface)
            assertEquals(palette.Screen, colors.primary)
            assertEquals(palette.Ink, colors.onPrimary)
        }
    }

    @Test
    fun customLyricEmphasisRemainsReadableOverBrightAndDarkArtworkAtEveryBackdropStop() {
        listOf(PocketAdvanceLcdPalette.CLASSIC_GREEN, PocketAdvanceLcdPalette.WARM_AMBER).forEach { lcd ->
            val palette = PocketAdvancePalette.from(PocketAdvanceAppearance(lcdPalette = lcd))
            val colors = requireNotNull(pocketAdvanceLyricsColors(palette, active = true))
            assertTrue(colors.pastLineAlpha < 1f)
            assertTrue(colors.upcomingLineAlpha < colors.pastLineAlpha)
            val scrims = listOf(colors.backdropTopAlpha, colors.backdropMiddleAlpha, colors.backdropBottomAlpha)
            assertEquals(0.82f, colors.secondaryTextAlpha)
            val emphasis = mapOf(
                "active" to 1f, "past" to colors.pastLineAlpha, "upcoming" to colors.upcomingLineAlpha,
                "header/details" to (colors.secondaryTextAlpha ?: 0.7f),
                "unsynced label/unavailable candidate" to (colors.secondaryTextAlpha ?: 0.62f),
                "loading" to (colors.secondaryTextAlpha ?: 0.76f),
                "unsynced text" to (colors.secondaryTextAlpha ?: 0.78f)
            )
            listOf(Color.White, Color.Black).forEach { artwork ->
                scrims.forEach { scrim ->
                    val backdrop = colors.background.copy(alpha = scrim).compositeOver(artwork)
                    emphasis.forEach { (state, alpha) ->
                        val text = colors.foreground.copy(alpha = alpha).compositeOver(backdrop)
                        val lighter = maxOf(text.luminance(), backdrop.luminance())
                        val darker = minOf(text.luminance(), backdrop.luminance())
                        val contrast = (lighter + 0.05f) / (darker + 0.05f)
                        assertTrue(
                            "$lcd lyrics contrast $contrast must be at least 4.5 for artwork=$artwork scrim=$scrim state=$state emphasis=$alpha",
                            contrast >= 4.5f
                        )
                    }
                }
            }
        }
    }
}
