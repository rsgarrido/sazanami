package io.github.rsgarrido.sazanami.player.equalizer

import androidx.media3.common.C
import io.github.rsgarrido.sazanami.player.equalizer.dsp.EqualizerFilterSpec
import io.github.rsgarrido.sazanami.player.equalizer.parametric.ParametricEqualizerState
import io.github.rsgarrido.sazanami.player.equalizer.parametric.ParametricFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParametricRuntimeConversionTest {
    @Test
    fun everyTypeMapsInOrderWithoutMeaninglessParameters() {
        val state = preferences(
            filters = listOf(
                ParametricFilter.Peaking(
                    "peak", true, 1_000.0, 3.0, 1.2
                ),
                ParametricFilter.LowShelf(
                    "ls", true, 100.0, 4.0, 0.8
                ),
                ParametricFilter.HighShelf(
                    "hs", false, 8_000.0, -2.0, 1.0
                ),
                ParametricFilter.LowPass(
                    "lp", true, 18_000.0, 0.71
                ),
                ParametricFilter.HighPass(
                    "hp", true, 30.0, 0.8
                ),
                ParametricFilter.Notch(
                    "notch", true, 2_000.0, 8.0
                ),
                ParametricFilter.BandPass(
                    "bp", true, 500.0, 2.0
                )
            )
        )

        val configuration = state.toDspConfiguration()

        assertEquals(
            listOf(
                EqualizerFilterSpec.Peaking::class,
                EqualizerFilterSpec.LowShelf::class,
                EqualizerFilterSpec.HighShelf::class,
                EqualizerFilterSpec.LowPass::class,
                EqualizerFilterSpec.HighPass::class,
                EqualizerFilterSpec.Notch::class,
                EqualizerFilterSpec.BandPass::class
            ),
            configuration.filters.map { it::class }
        )
        assertFalse(configuration.filters[2].enabled)
        assertEquals(-2.5, configuration.preampDb, 0.0)
    }

    @Test
    fun disabledAndUnityFiltersDoNotCreatePreparedSections() {
        val state = preferences(
            filters = listOf(
                ParametricFilter.Peaking(
                    "unity", true, 1_000.0, 0.0, 1.0
                ),
                ParametricFilter.Notch(
                    "disabled", false, 2_000.0, 4.0
                ),
                ParametricFilter.HighPass(
                    "active", true, 80.0, 0.71
                )
            ),
            preampDb = 0.0
        )

        val plan = prepare(state, 48_000)

        assertEquals(1, plan.validFilterCount)
        assertFalse(plan.bypassed)
    }

    @Test
    fun sourceIncompatibleFilterIsIgnoredWithoutMutatingStoredState() {
        val filter = ParametricFilter.LowPass(
            "near-nyquist", true, 18_000.0, 0.71
        )
        val state = preferences(
            filters = listOf(filter),
            preampDb = 0.0
        )

        val lowRate = prepare(state, 32_000)
        val highRate = prepare(state, 48_000)

        assertTrue(lowRate.bypassed)
        assertEquals(0, lowRate.validFilterCount)
        assertEquals(0, lowRate.ignoredFilters.single().sourceIndex)
        assertEquals(
            IgnoredEqualizerFilterReason.AT_OR_ABOVE_NYQUIST,
            lowRate.ignoredFilters.single().reason
        )
        assertEquals(1, highRate.validFilterCount)
        assertEquals(filter, state.parametricState.filters.single())
    }

    @Test
    fun selectedModeControlsIndependentCurveAndHeadroomFlag() {
        val graphic = EqualizerPreferencesState(
            enabled = true,
            mode = EqualizerMode.GRAPHIC,
            preampDb = -1.0,
            automaticHeadroomEnabled = false,
            parametricState = ParametricEqualizerState(
                preampDb = -5.0,
                automaticHeadroomEnabled = true,
                filters = listOf(
                    ParametricFilter.Notch(
                        "notch", true, 1_000.0, 4.0
                    )
                )
            )
        )
        val parametric = graphic.withMode(EqualizerMode.PARAMETRIC)

        assertEquals(-1.0, graphic.toDspConfiguration().preampDb, 0.0)
        assertFalse(graphic.activeAutomaticHeadroomEnabled)
        assertEquals(
            -5.0,
            parametric.toDspConfiguration().preampDb,
            0.0
        )
        assertTrue(parametric.activeAutomaticHeadroomEnabled)
        assertEquals(
            graphic.parametricState,
            parametric.parametricState
        )
        assertEquals(graphic.bandGainsDb, parametric.bandGainsDb)
        assertEquals(graphic.enabled, parametric.enabled)
    }

    private fun preferences(
        filters: List<ParametricFilter>,
        preampDb: Double = -2.5
    ) = EqualizerPreferencesState(
        enabled = true,
        mode = EqualizerMode.PARAMETRIC,
        parametricState = ParametricEqualizerState(
            preampDb = preampDb,
            automaticHeadroomEnabled = false,
            filters = filters
        )
    )

    private fun prepare(
        state: EqualizerPreferencesState,
        sampleRateHz: Int
    ): PreparedEqualizerPlan = EqualizerPlanPreparer.prepare(
        snapshot = EqualizerRuntimeSnapshot(
            version = 1L,
            mode = state.mode,
            configuration = state.toDspConfiguration(),
            automaticHeadroomEnabled =
                state.activeAutomaticHeadroomEnabled
        ),
        processorFormat = EqualizerProcessorFormat(
            sampleRateHz = sampleRateHz,
            channelCount = 2,
            pcmEncoding = C.ENCODING_PCM_16BIT
        )
    )
}
