package io.github.rsgarrido.sazanami.ui.player.pocketadvance

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import io.github.rsgarrido.sazanami.data.PocketAdvanceAppearance
import io.github.rsgarrido.sazanami.data.PocketAdvanceConsoleFinish
import io.github.rsgarrido.sazanami.data.PocketAdvanceControlsFinish
import io.github.rsgarrido.sazanami.data.PocketAdvanceLcdPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PocketAdvancePaletteTest {
    @Test
    fun allPresetIdsAreStableUniqueAndRoundTripWithoutOrdinals() {
        assertEquals(listOf("original_purple", "graphite", "ocean_blue", "forest_green", "berry"),
            PocketAdvanceConsoleFinish.entries.map { it.id })
        assertEquals(listOf("original", "classic_green", "warm_amber"), PocketAdvanceLcdPalette.entries.map { it.id })
        assertEquals(listOf("original_dark", "light_gray", "deep_navy"), PocketAdvanceControlsFinish.entries.map { it.id })
        PocketAdvanceConsoleFinish.entries.forEach { assertEquals(it, PocketAdvanceConsoleFinish.fromId(it.id)) }
        PocketAdvanceLcdPalette.entries.forEach { assertEquals(it, PocketAdvanceLcdPalette.fromId(it.id)) }
        PocketAdvanceControlsFinish.entries.forEach { assertEquals(it, PocketAdvanceControlsFinish.fromId(it.id)) }
    }

    @Test
    fun originalSelectionsAndIndependentUnknownIdFallbackAreExplicit() {
        val original = PocketAdvanceAppearance(PocketAdvanceConsoleFinish.ORIGINAL_PURPLE,
            PocketAdvanceLcdPalette.ORIGINAL, PocketAdvanceControlsFinish.ORIGINAL_DARK)
        assertEquals(original, PocketAdvanceAppearance())
        assertEquals(original, PocketAdvanceAppearance.Default)
        for (invalid in listOf(null, "", "future_preset", "0", "ORIGINAL")) {
            assertEquals(original.consoleFinish, PocketAdvanceConsoleFinish.fromId(invalid))
            assertEquals(original.lcdPalette, PocketAdvanceLcdPalette.fromId(invalid))
            assertEquals(original.controlsFinish, PocketAdvanceControlsFinish.fromId(invalid))
            val partial = PocketAdvanceAppearance(PocketAdvanceConsoleFinish.fromId(invalid),
                PocketAdvanceLcdPalette.fromId("warm_amber"), PocketAdvanceControlsFinish.fromId("light_gray"))
            assertEquals(original.copy(lcdPalette = PocketAdvanceLcdPalette.WARM_AMBER,
                controlsFinish = PocketAdvanceControlsFinish.LIGHT_GRAY), partial)
        }
    }

    @Test
    fun originalPaletteExactlyMatchesAllFourteenApprovedColors() {
        val original = PocketAdvancePalette.from(PocketAdvanceAppearance.Default)
        val approved = listOf(
            Color(0xFF6653A5) to original.Shell,
            Color(0xFF9C87D2) to original.ShellHighlight,
            Color(0xFF392C65) to original.ShellShade,
            Color(0xFF282639) to original.Bezel,
            Color(0xFFDFE5CC) to original.Screen,
            Color(0xFFC8D2B1) to original.ScreenShade,
            Color(0xFF263C38) to original.Ink,
            Color(0xFF52655A) to original.MutedInk,
            Color(0xFF547B64) to original.ScreenAccent,
            Color(0xFF383443) to original.Button,
            Color(0xFF686173) to original.ButtonHighlight,
            Color(0xFFF4EDF9) to original.ButtonInk,
            Color(0xFFE1D8F3) to original.Label,
            Color(0xFFA8E68D) to original.Led
        )
        approved.forEachIndexed { index, (expected, actual) -> assertEquals("Approved color $index", expected, actual) }
        assertEquals(original.Led, original.ActiveButtonInk)
        assertEquals(original, PocketAdvanceDefaultPalette)
    }

    @Test
    fun eachCategoryChangesOnlyItsOwnRenderingColors() {
        val original = PocketAdvanceDefaultPalette
        PocketAdvanceConsoleFinish.entries.forEach { finish ->
            val palette = PocketAdvancePalette.from(PocketAdvanceAppearance(consoleFinish = finish))
            assertEquals(lcdColors(original), lcdColors(palette))
            assertEquals(controlColors(original), controlColors(palette))
            if (finish != PocketAdvanceConsoleFinish.ORIGINAL_PURPLE) assertNotEquals(consoleColors(original), consoleColors(palette))
        }
        PocketAdvanceLcdPalette.entries.forEach { lcd ->
            val palette = PocketAdvancePalette.from(PocketAdvanceAppearance(lcdPalette = lcd))
            assertEquals(consoleColors(original), consoleColors(palette))
            assertEquals(controlColors(original), controlColors(palette))
            assertEquals(lcd, palette.lcdPalette)
            if (lcd != PocketAdvanceLcdPalette.ORIGINAL) assertNotEquals(lcdColors(original), lcdColors(palette))
        }
        PocketAdvanceControlsFinish.entries.forEach { finish ->
            val palette = PocketAdvancePalette.from(PocketAdvanceAppearance(controlsFinish = finish))
            assertEquals(consoleColors(original), consoleColors(palette))
            assertEquals(lcdColors(original), lcdColors(palette))
            if (finish != PocketAdvanceControlsFinish.ORIGINAL_DARK) assertNotEquals(controlColors(original), controlColors(palette))
        }
    }

    @Test
    fun curatedShellStopsRemainOrderedAndKeepTheBezelAndPowerLampFixed() {
        for (appearance in appearances()) {
            val palette = PocketAdvancePalette.from(appearance)
            assertTrue("Highlight must remain brighter for $appearance", palette.ShellHighlight.luminance() > palette.Shell.luminance())
            assertTrue("Shade must remain darker for $appearance", palette.ShellShade.luminance() < palette.Shell.luminance())
            assertEquals(Color(0xFF282639), palette.Bezel)
            assertEquals(Color(0xFFA8E68D), palette.Led)
            (consoleColors(palette) + lcdColors(palette) + controlColors(palette)).forEach { assertEquals(1f, it.alpha, 0f) }
        }
    }

    @Test
    fun originalShellGradientInterpolationKeepsTheApprovedExpansionRelationships() {
        val original = PocketAdvanceDefaultPalette
        for (progress in listOf(0f, .25f, .5f, .75f, 1f)) {
            // These are the approved shell renderer's existing .48/.42 interpolation relationships.
            assertEquals(lerp(Color(0xFF6653A5), Color(0xFF9C87D2), progress * .48f),
                lerp(original.Shell, original.ShellHighlight, progress * .48f))
            assertEquals(lerp(Color(0xFF6653A5), Color(0xFF392C65), progress * .42f),
                lerp(original.Shell, original.ShellShade, progress * .42f))
            if (progress > 0f) {
                assertTrue(lerp(original.Shell, original.ShellHighlight, progress * .48f).luminance() > original.Shell.luminance())
                assertTrue(lerp(original.Shell, original.ShellShade, progress * .42f).luminance() < original.Shell.luminance())
            }
        }
    }

    @Test
    fun everyLcdPaletteKeepsNormalSelectedAndMutedTextReadable() {
        PocketAdvanceLcdPalette.entries.forEach { lcd ->
            val palette = PocketAdvancePalette.from(PocketAdvanceAppearance(lcdPalette = lcd))
            assertContrast("$lcd primary text", palette.Ink, palette.Screen, 4.5f)
            assertContrast("$lcd primary text on panel", palette.Ink, palette.ScreenShade, 4.5f)
            assertContrast("$lcd selected text", palette.Screen, palette.Ink, 4.5f)
            assertContrast("$lcd muted text", palette.MutedInk, palette.Screen, 4.5f)
            assertContrast("$lcd progress accent", palette.ScreenAccent, palette.Screen, 3f)
        }
    }

    @Test
    fun everyHardwareCombinationKeepsLabelsAndControlIconsReadable() {
        for (appearance in appearances()) {
            val palette = PocketAdvancePalette.from(appearance)
            assertContrast("$appearance shell label", palette.Label, palette.Shell, 4.5f)
            assertContrast("$appearance control icon", palette.ButtonInk, palette.Button, 4.5f)
            assertContrast("$appearance control icon on highlight", palette.ButtonInk, palette.ButtonHighlight, 4.5f)
            assertContrast("$appearance active control icon", palette.ActiveButtonInk, palette.Button, 4.5f)
        }
        assertNotEquals(PocketAdvanceDefaultPalette.ActiveButtonInk,
            PocketAdvancePalette.from(PocketAdvanceAppearance(controlsFinish = PocketAdvanceControlsFinish.LIGHT_GRAY)).ActiveButtonInk)
    }

    private fun consoleColors(palette: PocketAdvancePalette) = listOf(palette.Shell, palette.ShellHighlight, palette.ShellShade, palette.Label)
    private fun lcdColors(palette: PocketAdvancePalette) = listOf(palette.Screen, palette.ScreenShade, palette.Ink, palette.MutedInk, palette.ScreenAccent)
    private fun controlColors(palette: PocketAdvancePalette) = listOf(palette.Button, palette.ButtonHighlight, palette.ButtonInk, palette.ActiveButtonInk)
    private fun appearances() = PocketAdvanceConsoleFinish.entries.flatMap { console ->
        PocketAdvanceLcdPalette.entries.flatMap { lcd -> PocketAdvanceControlsFinish.entries.map { controls ->
            PocketAdvanceAppearance(console, lcd, controls)
        } }
    }
    private fun assertContrast(description: String, foreground: Color, background: Color, minimum: Float) {
        val lighter = maxOf(foreground.luminance(), background.luminance())
        val darker = minOf(foreground.luminance(), background.luminance())
        val contrast = (lighter + .05f) / (darker + .05f)
        assertTrue("$description contrast $contrast must be at least $minimum", contrast >= minimum)
    }
}
