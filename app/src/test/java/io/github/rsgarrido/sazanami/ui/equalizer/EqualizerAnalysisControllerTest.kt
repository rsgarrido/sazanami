package io.github.rsgarrido.sazanami.ui.equalizer

import io.github.rsgarrido.sazanami.player.equalizer.EqualizerPreferencesState
import io.github.rsgarrido.sazanami.player.equalizer.GraphicEqualizerPresets
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerMode
import io.github.rsgarrido.sazanami.player.equalizer.applyPreset
import io.github.rsgarrido.sazanami.player.equalizer.parametric.ParametricEqualizerState
import io.github.rsgarrido.sazanami.player.equalizer.parametric.ParametricFilter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualizerAnalysisControllerTest {
    @Test
    fun identicalRuntimeUpdatesDoNotRestartAnalysis() =
        runBlocking {
            val calculationCount = AtomicInteger()
            val calculationStarted = CountDownLatch(1)
            val releaseCalculation = CountDownLatch(1)
            val scope = CoroutineScope(
                SupervisorJob() + Dispatchers.Default
            )
            val controller = EqualizerAnalysisController(
                scope = scope,
                calculate = { request ->
                    calculationCount.incrementAndGet()
                    calculationStarted.countDown()
                    releaseCalculation.await(3, TimeUnit.SECONDS)
                    EqualizerAnalysisResult(
                        sampleRateHz =
                            requireNotNull(
                                request.currentSampleRateHz
                            ),
                        usesFallbackSampleRate = false
                    )
                }
            )
            val preferences = EqualizerPreferencesState()
            try {
                controller.submit(preferences, 44_100)
                assertTrue(
                    calculationStarted.await(
                        3,
                        TimeUnit.SECONDS
                    )
                )
                repeat(100) {
                    controller.submit(preferences, 44_100)
                }
                assertEquals(1, calculationCount.get())

                releaseCalculation.countDown()
                repeat(100) {
                    if (
                        controller.state.value.sampleRateHz ==
                        44_100
                    ) {
                        return@repeat
                    }
                    delay(10)
                }
                controller.submit(preferences, 44_100)
                delay(50)
                assertEquals(1, calculationCount.get())

                controller.submit(preferences, 96_000)
                repeat(100) {
                    if (calculationCount.get() == 2) {
                        return@repeat
                    }
                    delay(10)
                }
                assertEquals(2, calculationCount.get())
            } finally {
                releaseCalculation.countDown()
                controller.release()
                scope.cancel()
            }
        }

    @Test
    fun staleCalculationCannotReplaceNewerPublishedResult() =
        runBlocking {
            val oldStarted = CountDownLatch(1)
            val releaseOld = CountDownLatch(1)
            val scope = CoroutineScope(
                SupervisorJob() + Dispatchers.Default
            )
            val controller = EqualizerAnalysisController(
                scope = scope,
                calculate = { request ->
                    if (request.currentSampleRateHz == 32_000) {
                        oldStarted.countDown()
                        releaseOld.await(3, TimeUnit.SECONDS)
                    }
                    EqualizerAnalysisResult(
                        sampleRateHz =
                            requireNotNull(request.currentSampleRateHz),
                        usesFallbackSampleRate = false
                    )
                }
            )
            try {
                controller.submit(
                    EqualizerPreferencesState(),
                    32_000
                )
                assertTrue(oldStarted.await(3, TimeUnit.SECONDS))
                controller.submit(
                    EqualizerPreferencesState(),
                    96_000
                )
                repeat(100) {
                    if (controller.state.value.sampleRateHz == 96_000) {
                        return@repeat
                    }
                    delay(10)
                }
                assertEquals(
                    96_000,
                    controller.state.value.sampleRateHz
                )
                releaseOld.countDown()
                delay(100)
                assertEquals(
                    96_000,
                    controller.state.value.sampleRateHz
                )
            } finally {
                releaseOld.countDown()
                controller.release()
                scope.cancel()
            }
        }

    @Test
    fun flatResponseUsesPhaseACalculationAndStaysAtZero() {
        val result = calculate(
            EqualizerPreferencesState(enabled = true),
            48_000
        )

        assertEquals(160, result.filterResponse.size)
        assertTrue(
            result.filterResponse.all { point ->
                kotlin.math.abs(point.magnitudeDb) < 1e-9
            }
        )
        assertTrue(
            result.effectiveResponse.all { point ->
                kotlin.math.abs(point.magnitudeDb) < 1e-9
            }
        )
        assertEquals(0.0, result.predictedMaximumDb, 1e-9)
        assertEquals(
            0.0,
            result.automaticHeadroom.attenuationDb,
            1e-9
        )
    }

    @Test
    fun bassAndTreblePresetsRaiseTheirIntendedRegions() {
        val bass = calculate(
            EqualizerPreferencesState()
                .applyPreset(
                    GraphicEqualizerPresets.builtIns[1]
                ),
            48_000
        )
        val treble = calculate(
            EqualizerPreferencesState()
                .applyPreset(
                    GraphicEqualizerPresets.builtIns[2]
                ),
            48_000
        )

        assertTrue(
            bass.filterResponse.nearest(62.0).magnitudeDb >
                bass.filterResponse.nearest(8_000.0).magnitudeDb
        )
        assertTrue(
            treble.filterResponse.nearest(8_000.0).magnitudeDb >
                treble.filterResponse.nearest(62.0).magnitudeDb
        )
        assertTrue(bass.predictedMaximumDb > 0.0)
        assertTrue(
            bass.effectiveResponse.maxOf { it.magnitudeDb } <
                0.0
        )
    }

    @Test
    fun currentNyquistDeterminesIgnoredBandsWithoutLosingValues() {
        val state = EqualizerPreferencesState()
            .withBandGainDb(9, 7.0)
        val lowRate = calculate(state, 32_000)
        val standardRate = calculate(state, 44_100)

        assertTrue(9 in lowRate.ignoredBandIndices)
        assertFalse(9 in standardRate.ignoredBandIndices)
        assertEquals(7.0, state.bandGainsDb[9], 0.0)
        assertTrue(
            lowRate.effectiveResponse.last().frequencyHz <
                16_000.0
        )
        assertTrue(
            standardRate.effectiveResponse.last().frequencyHz <
                22_050.0
        )
    }

    @Test
    fun disablingHeadroomLeavesUserPreampUnattenuated() {
        val state = EqualizerPreferencesState(
            automaticHeadroomEnabled = false
        ).withBandGainDb(4, 8.0)
        val result = calculate(state, 48_000)

        assertTrue(result.predictedMaximumDb > 0.0)
        assertEquals(
            0.0,
            result.automaticHeadroom.attenuationDb,
            0.0
        )
        assertEquals(
            state.preampDb,
            result.automaticHeadroom.effectivePreampDb,
            0.0
        )
    }

    @Test
    fun parametricAnalysisUsesActualCascadeAndReportsIgnoredIndex() {
        val filters = listOf(
            ParametricFilter.Peaking(
                "peak", true, 1_000.0, 6.0, 10.0
            ),
            ParametricFilter.LowPass(
                "ignored", true, 18_000.0, 0.71
            ),
            ParametricFilter.HighShelf(
                "shelf", true, 8_000.0, -3.0, 1.0
            )
        )
        val state = EqualizerPreferencesState(
            enabled = true,
            mode = EqualizerMode.PARAMETRIC,
            parametricState = ParametricEqualizerState(
                preampDb = 1.0,
                automaticHeadroomEnabled = true,
                filters = filters
            )
        )

        val lowRate = calculate(state, 32_000)
        val highRate = calculate(state, 48_000)

        assertEquals(setOf(1), lowRate.ignoredFilterIndices)
        assertTrue(highRate.ignoredFilterIndices.isEmpty())
        assertTrue(
            lowRate.filterResponse.nearest(1_000.0).magnitudeDb >
                lowRate.filterResponse.nearest(100.0).magnitudeDb
        )
        assertTrue(lowRate.automaticHeadroom.attenuationDb > 0.0)
        assertEquals(filters, state.parametricState.filters)
    }

    private fun calculate(
        state: EqualizerPreferencesState,
        sampleRateHz: Int
    ): EqualizerAnalysisResult {
        return EqualizerAnalysisCalculator.calculate(
            EqualizerAnalysisRequest(state, sampleRateHz)
        )
    }
}

private fun List<
    io.github.rsgarrido.sazanami.player.equalizer.dsp.EqualizerResponsePoint
>.nearest(frequencyHz: Double) = minBy { point ->
    kotlin.math.abs(point.frequencyHz - frequencyHz)
}
