package io.github.rsgarrido.sazanami.player.equalizer.interchange

import io.github.rsgarrido.sazanami.player.equalizer.parametric.ParametricFilter
import io.github.rsgarrido.sazanami.player.equalizer.parametric.gainDbOrNull
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualizerProfileParserTest {
    @Test
    fun parsesRepresentativeAutoEqTextAndEverySupportedType() {
        val input = "\uFEFFPreamp: -6.2 dB\r\n" +
            "# comment\r\n" +
            "Filter 1: ON LSC Fc 105 Hz Gain 6.6 dB Q 0.70\r\n" +
            "Filter 2: ON PK Fc 142 Hz Gain -2.3 dB Q 0.30\r\n" +
            "Filter: OFF PEQ Fc 1000 Hz Gain 1 dB Q 1.4\r\n" +
            "Filter 4: ON HSC Fc 10000 Hz Gain -4.3 dB Q 0.70\r\n" +
            "Filter 5: ON LPQ Fc 18000 Hz Q 0.71\r\n" +
            "Filter 6: ON HPQ Fc 20 Hz Q 0.71\r\n" +
            "Filter 7: ON BP Fc 2000 Hz Q 1\r\n" +
            "Filter 8: ON NO Fc 4000 Hz Q 4\r\n"

        val result = EqualizerProfileParser.parse(
            input,
            idFactory = sequenceIds()
        )

        assertEquals(-6.2, result.preampDb!!, 0.0)
        assertEquals(8, result.declarations.size)
        assertTrue(result.declarations.all {
            it.status == ImportedFilterStatus.VALID
        })
        assertTrue(result.declarations[0].mappedFilter is ParametricFilter.LowShelf)
        assertTrue(result.declarations[1].mappedFilter is ParametricFilter.Peaking)
        assertFalse(result.declarations[2].mappedFilter!!.enabled)
        assertTrue(result.declarations[3].mappedFilter is ParametricFilter.HighShelf)
        assertTrue(result.declarations[4].mappedFilter is ParametricFilter.LowPass)
        assertTrue(result.declarations[5].mappedFilter is ParametricFilter.HighPass)
        assertTrue(result.declarations[6].mappedFilter is ParametricFilter.BandPass)
        assertTrue(result.declarations[7].mappedFilter is ParametricFilter.Notch)
        assertFalse(result.hasBlockingDiagnostics)
    }

    @Test
    fun normalizesSafeDecimalCommasButRejectsAmbiguousThousands() {
        val safe = EqualizerProfileParser.parse(
            "Preamp: -6,5 dB\n" +
                "Filter 1: ON PK Fc 1000 Hz Gain -3,5 dB Q 0,71"
        )
        assertEquals(-6.5, safe.preampDb!!, 0.0)
        assertTrue(safe.allDiagnostics.any {
            it.code ==
                EqualizerProfileDiagnosticCode.DECIMAL_COMMA_NORMALIZED
        })

        val ambiguous = EqualizerProfileParser.parse(
            "Filter 1: ON PK Fc 1,000 Hz Gain 3 dB Q 1"
        )
        assertTrue(ambiguous.hasBlockingDiagnostics)
        assertEquals(
            EqualizerProfileDiagnosticCode.INVALID_NUMBER,
            ambiguous.declarations.single().diagnostics.single().code
        )
    }

    @Test
    fun sumsGlobalPreampsWithoutClamping() {
        val valid = EqualizerProfileParser.parse(
            "Preamp: -5 dB\nPreamp: +2 dB"
        )
        assertEquals(-3.0, valid.preampDb!!, 0.0)
        assertTrue(valid.diagnostics.any {
            it.code ==
                EqualizerProfileDiagnosticCode.MULTIPLE_PREAMPS_COMBINED
        })

        val invalid = EqualizerProfileParser.parse(
            "Preamp: -10 dB\nPreamp: -10 dB"
        )
        assertEquals(-20.0, invalid.preampDb!!, 0.0)
        assertTrue(invalid.hasBlockingDiagnostics)
    }

    @Test
    fun blocksUnsupportedCommandsUnknownCommandsAndMalformedFilters() {
        val input = """
            Include: another-file.txt
            Device: Headphones
            Channel: L
            GraphicEQ: 20 0; 1000 2
            Something: value
            Filter 1: ON PK Fc 1000 Hz Gain 3 dB
        """.trimIndent()
        val result = EqualizerProfileParser.parse(input)
        assertEquals(
            EqualizerProfileFormat.EQUALIZER_APO_SUBSET,
            result.detectedFormat
        )
        assertTrue(result.hasBlockingDiagnostics)
        assertEquals(4, result.diagnostics.count {
            it.code ==
                EqualizerProfileDiagnosticCode.UNSUPPORTED_COMMAND
        })
        assertTrue(result.diagnostics.any {
            it.code == EqualizerProfileDiagnosticCode.UNKNOWN_COMMAND
        })
        assertEquals(
            EqualizerProfileDiagnosticCode.MISSING_PARAMETER,
            result.declarations.single().diagnostics.single().code
        )
    }

    @Test
    fun everyDocumentProcessingCommandIsBlocking() {
        val commands = listOf(
            "Include", "Device", "Channel", "Stage", "GraphicEQ",
            "Convolution", "Delay", "Copy", "If", "ElseIf",
            "Else", "EndIf", "Eval", "Expression", "IIR"
        )
        val result = EqualizerProfileParser.parse(
            commands.joinToString("\n") { "$it: ignored" }
        )

        assertEquals(commands.size, result.diagnostics.size)
        assertTrue(result.diagnostics.all {
            it.code ==
                EqualizerProfileDiagnosticCode.UNSUPPORTED_COMMAND &&
                it.severity ==
                EqualizerProfileDiagnosticSeverity.BLOCKING
        })
    }

    @Test
    fun scopedSemanticsDoNotPretendPreampsAreGlobal() {
        val result = EqualizerProfileParser.parse(
            "Preamp: -6 dB\n" +
                "Channel: L\n" +
                "Preamp: 2 dB\n" +
                "gainVariable = 3\n" +
                "Filter 1: ON PK Fc 1000 Hz Gain 3 dB Q 1"
        )

        assertEquals(null, result.preampDb)
        assertTrue(result.hasBlockingDiagnostics)
        assertFalse(result.diagnostics.any {
            it.code ==
                EqualizerProfileDiagnosticCode.MULTIPLE_PREAMPS_COMBINED
        })
        assertTrue(result.diagnostics.any {
            it.code ==
                EqualizerProfileDiagnosticCode.UNSUPPORTED_COMMAND &&
                it.lineNumber == 4
        })
    }

    @Test
    fun unsupportedFiltersAreRetainedAndNeverApproximated() {
        listOf(
            "LS", "HS", "LS 6dB", "LS 12dB", "HS 6dB",
            "HS 12dB", "LP", "HP", "AP", "Modal"
        ).forEachIndexed { index, type ->
            val result = EqualizerProfileParser.parse(
                "Filter ${index + 1}: ON $type Fc 1000 Hz Gain 3 dB Q 1"
            )
            val declaration = result.declarations.single()
            assertEquals(ImportedFilterStatus.UNSUPPORTED, declaration.status)
            assertEquals(null, declaration.mappedFilter)
            assertEquals(
                EqualizerProfileDiagnosticCode.UNSUPPORTED_FILTER_TYPE,
                declaration.diagnostics.single().code
            )
        }
        val bandwidth = EqualizerProfileParser.parse(
            "Filter 1: ON PK Fc 1000 Hz Gain 3 dB BW Oct 1"
        ).declarations.single()
        assertEquals(ImportedFilterStatus.UNSUPPORTED, bandwidth.status)
        assertEquals(null, bandwidth.mappedFilter)
    }

    @Test
    fun rejectsDuplicateMissingExtraUnitsAndTrailingTokens() {
        val lines = listOf(
            "Filter 1: ON PK Gain 3 dB Q 1",
            "Filter 1: ON PK Fc 1000 Hz Gain 3 dB Q 1 Fc 2 Hz",
            "Filter 1: ON LPQ Fc 1000 Hz Gain 3 dB Q 1",
            "Filter 1: ON PK Fc 1000 kHz Gain 3 dB Q 1",
            "Filter 1: ON PK Fc 1000 Hz Gain 3 dB Q 1 execute"
        )
        lines.forEach { line ->
            val result = EqualizerProfileParser.parse(line)
            assertTrue(result.hasBlockingDiagnostics)
            assertEquals(ImportedFilterStatus.INVALID,
                result.declarations.single().status)
        }
    }

    @Test
    fun malformedNumbersStatesColonsAndUnitsNeverPartiallyMap() {
        val lines = listOf(
            "Filter 1 ON PK Fc 1000 Hz Gain 3 dB Q 1",
            "Filter 1: MAYBE PK Fc 1000 Hz Gain 3 dB Q 1",
            "Filter 1: ON PK Fc NaN Hz Gain 3 dB Q 1",
            "Filter 1: ON PK Fc Infinity Hz Gain 3 dB Q 1",
            "Filter 1: ON PK Fc + Hz Gain 3 dB Q 1",
            "Filter 1: ON PK Fc 1000 Hz Gain --3 dB Q 1",
            "Filter 1: ON PK Fc 1000 Hz Gain 3 dB Q",
            "Filter 1: ON PK Fc 1000 Hz Gain 3 dbx Q 1",
            "Filter 1: ON PK Fc 1000 Hz Gain 3 dB Q 1 Q 2"
        )
        lines.forEach { line ->
            val result = EqualizerProfileParser.parse(line)
            assertTrue(result.hasBlockingDiagnostics ||
                result.diagnostics.any {
                    it.code ==
                        EqualizerProfileDiagnosticCode.UNRECOGNIZED_TEXT
                })
            assertTrue(result.declarations.none {
                it.mappedFilter != null
            })
        }
    }

    @Test
    fun retainsMoreThanTenWithoutSelectingOrTruncating() {
        val input = (1..12).joinToString("\n") { index ->
            "Filter $index: ON PK Fc ${100 + index} Hz Gain 1 dB Q 1"
        }
        val result = EqualizerProfileParser.parse(input)
        assertEquals(12, result.declarations.size)
        assertTrue(result.diagnostics.any {
            it.code ==
                EqualizerProfileDiagnosticCode.FILTER_LIMIT_EXCEEDED
        })
    }

    @Test
    fun enforcesAllDefensiveInputLimits() {
        val nul = EqualizerProfileParser.parse("Preamp: 0 dB\u0000")
        assertEquals(
            EqualizerProfileDiagnosticCode.NUL_CHARACTER,
            nul.diagnostics.single().code
        )
        val long = EqualizerProfileParser.parse("x".repeat(4_097))
        assertEquals(
            EqualizerProfileDiagnosticCode.LINE_TOO_LONG,
            long.diagnostics.single().code
        )
        val lines = EqualizerProfileParser.parse(
            List(2_001) { "" }.joinToString("\n")
        )
        assertEquals(
            EqualizerProfileDiagnosticCode.TOO_MANY_LINES,
            lines.diagnostics.single().code
        )
        val huge = EqualizerProfileParser.parse(
            "x".repeat(EqualizerProfileLimits.MAX_INPUT_BYTES + 1)
        )
        assertEquals(
            EqualizerProfileDiagnosticCode.INPUT_TOO_LARGE,
            huge.diagnostics.single().code
        )
    }

    @Test
    fun validatesEndpointsAndRejectsOutOfRangeWithoutClamp() {
        val valid = EqualizerProfileParser.parse(
            "Preamp: +6 dB\n" +
                "Filter 1: ON PK Fc 20 Hz Gain -15 dB Q 0.10\n" +
                "Filter 2: ON PK Fc 20000 Hz Gain 15 dB Q 20"
        )
        assertFalse(valid.hasBlockingDiagnostics)
        assertEquals(2, valid.declarations.size)

        val invalid = EqualizerProfileParser.parse(
            "Filter 1: ON PK Fc 19.9 Hz Gain 15.1 dB Q 20.1"
        )
        assertTrue(invalid.hasBlockingDiagnostics)
        assertNotNull(invalid.declarations.single().diagnostics.single())
    }

    @Test
    fun phaseFRealisticFixtureMapsToTenAudibleFilters() {
        val fixture = listOf(
            File("docs/performance/phase-f-realistic-profile.txt"),
            File("../docs/performance/phase-f-realistic-profile.txt")
        ).firstOrNull(File::isFile)
        requireNotNull(fixture) {
            "Phase F realistic profile fixture is missing."
        }

        val result = EqualizerProfileParser.parse(
            fixture.readText(),
            idFactory = sequenceIds()
        )
        val mapped = result.declarations.mapNotNull {
            it.mappedFilter
        }

        assertFalse(result.hasBlockingDiagnostics)
        assertEquals(10, result.declarations.size)
        assertTrue(result.declarations.all {
            it.status == ImportedFilterStatus.VALID
        })
        assertEquals(10, mapped.size)
        assertTrue(mapped.all { it.enabled })
        assertTrue(mapped.all {
            it.gainDbOrNull?.let { gain -> gain != 0.0 } == true
        })
        assertTrue(mapped[0] is ParametricFilter.LowShelf)
        assertTrue(mapped[4] is ParametricFilter.HighShelf)
    }

    private fun sequenceIds(): () -> String {
        var next = 0
        return { "filter-${next++}" }
    }
}
