package io.github.rsgarrido.sazanami.ui.equalizer

import io.github.rsgarrido.sazanami.player.equalizer.EqualizerMode
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerPreferencesState
import io.github.rsgarrido.sazanami.player.equalizer.interchange.EqualizerProfileParser
import io.github.rsgarrido.sazanami.player.equalizer.interchange.ImportedFilterStatus
import io.github.rsgarrido.sazanami.player.equalizer.parametric.ParametricFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualizerImportPreviewStateTest {
    @Test
    fun moreThanTenStartsEmptyAndNeverSilentlyTruncates() {
        val preview = EqualizerImportPreviewState.fromText(
            EqualizerProfileParser.parse(filters(12))
        )

        assertEquals(12, preview.parseResult.declarations.size)
        assertTrue(preview.selectedFilters.isEmpty())
        assertFalse(preview.canApply)

        val firstTen = preview.selectFirstTen()
        assertEquals(10, firstTen.selectedFilters.size)
        assertEquals(
            (1..10).toList(),
            firstTen.selectedDeclarations.map {
                it.sourceLineNumber
            }
        )
        assertTrue(firstTen.canApply)
        assertTrue(
            runCatching {
                firstTen.toggleSelection(11)
            }.isFailure
        )
    }

    @Test
    fun unsupportedAndSemanticLinesRequireSeparateConfirmations() {
        val preview = EqualizerImportPreviewState.fromText(
            EqualizerProfileParser.parse(
                "Include: other.txt\n" +
                    "Filter 1: ON LS Fc 100 Hz Gain 3 dB Q 1\n" +
                    "Filter 2: ON PK Fc 1000 Hz Gain 2 dB Q 1"
            )
        )

        assertTrue(preview.hasOverrideableSemanticBlocks)
        assertTrue(preview.hasUnsupportedDeclarations)
        assertFalse(preview.canApply)
        assertFalse(
            preview.copy(
                supportedOnlyOverrideConfirmed = true
            ).canApply
        )
        assertTrue(
            preview.copy(
                supportedOnlyOverrideConfirmed = true,
                unsupportedExclusionConfirmed = true
            ).canApply
        )
    }

    @Test
    fun invalidDeclarationCanOnlyBeSelectedAfterExplicitReplacement() {
        val original = EqualizerImportPreviewState.fromText(
            EqualizerProfileParser.parse(
                "Filter 1: ON PK Fc 1000 Hz Gain 3 dB"
            )
        )
        assertEquals(
            ImportedFilterStatus.INVALID,
            original.parseResult.declarations.single().status
        )
        assertTrue(original.selectedFilters.isEmpty())

        val replacement = ParametricFilter.Peaking(
            "replacement",
            true,
            1_000.0,
            3.0,
            1.0
        )
        val corrected = original.replaceDeclaration(1, replacement)

        assertEquals(listOf(replacement), corrected.selectedFilters)
        assertTrue(corrected.canApply)
    }

    @Test
    fun proposedApplyPreservesGlobalGraphicAndLimiterState() {
        val durable = EqualizerPreferencesState(
            enabled = true,
            preampDb = -2.0,
            automaticHeadroomEnabled = false,
            bandGainsDb = List(10) { it.toDouble() - 5.0 },
            mode = EqualizerMode.GRAPHIC,
            limiterEnabled = true,
            limiterCeilingDbfs = -2.5
        )
        val preview = EqualizerImportPreviewState.fromText(
            EqualizerProfileParser.parse(
                "Preamp: -6 dB\n" +
                    "Filter 1: ON PK Fc 1000 Hz Gain 3 dB Q 1"
            )
        )
        val proposed = preview.proposedPreferences(durable)

        assertEquals(EqualizerMode.PARAMETRIC, proposed.mode)
        assertTrue(proposed.enabled)
        assertTrue(proposed.limiterEnabled)
        assertEquals(-2.5, proposed.limiterCeilingDbfs, 0.0)
        assertEquals(durable.bandGainsDb, proposed.bandGainsDb)
        assertEquals(-6.0, proposed.parametricState.preampDb, 0.0)
        assertEquals(1, proposed.parametricState.filters.size)
    }

    @Test
    fun zeroFilterImportNeedsExplicitFlatIntent() {
        val preview = EqualizerImportPreviewState.fromText(
            EqualizerProfileParser.parse("Preamp: 0 dB")
        )
        assertFalse(preview.canApply)
        assertTrue(preview.copy(flatImportConfirmed = true).canApply)
    }

    private fun filters(count: Int): String =
        (1..count).joinToString("\n") { index ->
            "Filter $index: ON PK Fc ${100 + index} Hz " +
                "Gain 1 dB Q 1"
        }
}
