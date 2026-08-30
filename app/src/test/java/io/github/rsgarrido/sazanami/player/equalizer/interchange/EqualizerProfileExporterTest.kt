package io.github.rsgarrido.sazanami.player.equalizer.interchange

import io.github.rsgarrido.sazanami.player.equalizer.parametric.ParametricEqualizerState
import io.github.rsgarrido.sazanami.player.equalizer.parametric.ParametricFilter
import io.github.rsgarrido.sazanami.player.equalizer.parametric.gainDbOrNull
import io.github.rsgarrido.sazanami.player.equalizer.parametric.qOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualizerProfileExporterTest {
    @Test
    fun exportsEveryTypeDeterministicallyWithApplicableParametersOnly() {
        val state = everyTypeState()
        val first = EqualizerProfileExporter.exportText(state)
        val second = EqualizerProfileExporter.exportText(state)
        assertEquals(first, second)
        assertTrue(first.endsWith("\n"))
        assertTrue(first.contains("Preamp: -6.2 dB"))
        listOf(" PK ", " LSC ", " HSC ", " LPQ ", " HPQ ", " NO ", " BP ")
            .forEach { token -> assertTrue(first.contains(token)) }
        assertTrue(first.contains("Filter 2: OFF LSC"))
        assertFalse(first.contains("Limiter ceiling"))
        assertFalse(first.contains("sample rate", ignoreCase = true))
        assertFalse(first.contains("E-"))
        first.lines().filter { "LPQ" in it || "HPQ" in it ||
            " BP " in it || " NO " in it
        }.forEach { assertFalse("Gain" in it) }
    }

    @Test
    fun textRoundTripPreservesStoredCurveAndShelfResponse() {
        val state = everyTypeState()
        val parsed = EqualizerProfileParser.parse(
            EqualizerProfileExporter.exportText(state),
            idFactory = sequenceIds()
        )
        assertFalse(parsed.hasBlockingDiagnostics)
        assertEquals(state.preampDb, parsed.preampDb!!, 0.0)
        val restored = parsed.declarations.map { it.mappedFilter!! }
        assertEquals(state.filters.size, restored.size)
        state.filters.zip(restored).forEach { (expected, actual) ->
            assertEquals(expected.type, actual.type)
            assertEquals(expected.enabled, actual.enabled)
            assertEquals(expected.frequencyHz, actual.frequencyHz, 0.05)
            assertEquals(
                expected.gainDbOrNull,
                actual.gainDbOrNull
            )
            assertEquals(expected.qOrNull, actual.qOrNull)
            when {
                expected is ParametricFilter.LowShelf &&
                    actual is ParametricFilter.LowShelf ->
                    assertEquals(expected.slope, actual.slope, 1e-5)
                expected is ParametricFilter.HighShelf &&
                    actual is ParametricFilter.HighShelf ->
                    assertEquals(expected.slope, actual.slope, 1e-5)
            }
        }
    }

    private fun everyTypeState() = ParametricEqualizerState(
        preampDb = -6.2,
        automaticHeadroomEnabled = false,
        filters = listOf(
            ParametricFilter.Peaking("p", true, 142.0, -2.3, 0.3),
            ParametricFilter.LowShelf("ls", false, 105.0, 6.6, 0.98),
            ParametricFilter.HighShelf("hs", true, 10_000.0, -4.3, 0.75),
            ParametricFilter.LowPass("lp", true, 18_000.0, 0.71),
            ParametricFilter.HighPass("hp", true, 20.0, 0.71),
            ParametricFilter.Notch("no", true, 4_000.0, 4.0),
            ParametricFilter.BandPass("bp", true, 2_000.0, 1.0)
        )
    )

    private fun sequenceIds(): () -> String {
        var next = 0
        return { "imported-${next++}" }
    }
}
