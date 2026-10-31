package io.github.rsgarrido.sazanami.ui.player.pockethorizon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import io.github.rsgarrido.sazanami.data.*
import org.junit.Assert.*
import org.junit.Test

class PocketHorizonAppearanceTest {
    @Test fun stableIdsAndIndependentFallbacks() {
        assertEquals(listOf("graphite", "silver", "midnight_blue", "deep_burgundy"), PocketHorizonConsoleFinish.entries.map { it.id })
        assertEquals(listOf("blue_violet", "emerald_teal", "warm_amber"), PocketHorizonLcdPalette.entries.map { it.id })
        assertEquals(listOf("standard", "subtle", "off"), PocketHorizonWaveTreatment.entries.map { it.id })
        PocketHorizonConsoleFinish.entries.forEach { assertEquals(it, PocketHorizonConsoleFinish.fromId(it.id)) }
        PocketHorizonLcdPalette.entries.forEach { assertEquals(it, PocketHorizonLcdPalette.fromId(it.id)) }
        PocketHorizonWaveTreatment.entries.forEach { assertEquals(it, PocketHorizonWaveTreatment.fromId(it.id)) }
        for (invalid in listOf(null, "", "future", "SILVER")) {
            assertEquals(PocketHorizonConsoleFinish.GRAPHITE, PocketHorizonConsoleFinish.fromId(invalid))
            assertEquals(PocketHorizonLcdPalette.BLUE_VIOLET, PocketHorizonLcdPalette.fromId(invalid))
            assertEquals(PocketHorizonWaveTreatment.STANDARD, PocketHorizonWaveTreatment.fromId(invalid))
        }
    }

    @Test fun defaultMatchesTheAcceptedTokensAndAllHardwareRoles() {
        val s = PocketHorizonStyle.from(PocketHorizonAppearance.Default)
        assertEquals(PocketHorizonStyle.Default, s)
        assertEquals(listOf(0xFF171D27, 0xFF35404F, 0xFF080E18, 0xFF142A43, 0xFF201C38,
            0xFF425B78, 0xFF25344D, 0xFF61708A, 0xFFF4F5F4, 0xFFCAD3E2, 0xFFA6E7DA, 0xFF102E30, 0xFF778295).map { Color(it) },
            listOf(s.shell, s.shellHighlight, s.bezel, s.displayTop, s.displayBottom, s.horizon, s.surface,
                s.outline, s.text, s.secondaryText, s.accent, s.onAccent, s.disabled))
        assertEquals(s.text, s.hardware.ink)
        assertEquals(s.outline, s.hardware.outline)
        assertEquals(s.accent, s.hardware.pressed)
        assertEquals(s.bezel, s.hardware.shade)
    }

    @Test fun all36CombinationsKeepDisplayTextAndHardwareLetteringReadable() {
        var combinations = 0
        for (finish in PocketHorizonConsoleFinish.entries) for (lcd in PocketHorizonLcdPalette.entries)
            for (wave in PocketHorizonWaveTreatment.entries) {
                val choice = PocketHorizonAppearance(finish, lcd, wave)
                val s = PocketHorizonStyle.from(choice)
                assertEquals(s, PocketHorizonStyle.from(choice))
                assertEquals(PocketHorizonStyle.from(choice.copy(lcdPalette = PocketHorizonLcdPalette.BLUE_VIOLET)).hardware, s.hardware)
                val stops = listOf(s.displayTop, s.displayBottom)
                for (base in stops) for (amount in listOf(0f, .5f, 1f)) {
                    val gradient = lerp(base, s.displayBottom, amount)
                    val glow = s.horizon.copy(alpha = .5f * s.waveIntensity).compositeOver(gradient)
                    val background = s.horizon.copy(alpha = .12f * s.waveIntensity).compositeOver(glow)
                    for (panel in listOf(background, s.surface, s.surface.copy(alpha = .45f).compositeOver(background))) {
                        readable(s.text, panel, 4.5f); readable(s.secondaryText, panel, 4.5f)
                        readable(s.accent, panel, 3f)
                    }
                }
                readable(s.onAccent, s.accent, 4.5f)
                // Label glyphs occupy the lower middle of these gradients, not the brighter bevel at the edge.
                for (surface in listOf(s.hardware.controlHighlight, s.hardware.pressed)) {
                    readable(s.hardware.ink, lerp(surface, s.hardware.shade, .6f), 4.5f)
                }
                assertEquals(when (wave) { PocketHorizonWaveTreatment.STANDARD -> 1f
                    PocketHorizonWaveTreatment.SUBTLE -> .35f; PocketHorizonWaveTreatment.OFF -> 0f }, s.waveIntensity)
                combinations++
            }
        assertEquals(36, combinations)
    }

    private fun readable(foreground: Color, background: Color, minimum: Float) {
        val a = foreground.compositeOver(background).luminance(); val b = background.luminance()
        assertTrue("$foreground on $background", (maxOf(a,b) + .05f) / (minOf(a,b) + .05f) >= minimum)
    }
}
