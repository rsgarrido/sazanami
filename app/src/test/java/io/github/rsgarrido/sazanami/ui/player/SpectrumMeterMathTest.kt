package io.github.rsgarrido.sazanami.ui.player

import io.github.rsgarrido.sazanami.player.spectrum.SpectrumAvailability
import io.github.rsgarrido.sazanami.player.spectrum.SpectrumFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpectrumMeterMathTest {
    @Test
    fun regionRmsIsBoundedAndInvalidValuesCannotPropagate() {
        val frame = availableFrame(
            floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 0.5f, 2f)
        )

        val level = aggregateSpectrumRegionRms(frame)

        assertTrue(level.isFinite())
        assertTrue(level in 0f..1f)
    }

    @Test
    fun emptyInvalidAndUnavailableRangesAreSafe() {
        val available = availableFrame(FloatArray(28) { 0.5f })

        assertEquals(0f, aggregateSpectrumRegionRms(available, 12, 12), 0f)
        assertEquals(0f, aggregateSpectrumRegionRms(available, 50, -20), 0f)
        assertEquals(0f, aggregateSpectrumRegionRms(SpectrumFrame.unavailable()), 0f)
    }

    @Test
    fun fixedCalibrationPreservesAbsoluteDynamicsAndHeadroom() {
        val moderate = calibrateSpectrumMeterLevel(0.60f, 1.05f, 1.8f)
        val strong = calibrateSpectrumMeterLevel(0.90f, 1.05f, 1.8f)

        assertTrue(moderate >= 0f && moderate < 1f)
        assertTrue(strong > moderate)
        assertTrue(strong <= 1f)
        assertEquals(0f, calibrateSpectrumMeterLevel(Float.NaN, 1f, 2f), 0f)
    }

    @Test
    fun segmentCountDefinesContiguousOriginFill() {
        val filledCount = contiguousMeterFillCount(0.5f, 24)
        val active = BooleanArray(24) { index -> index < filledCount }

        assertEquals(12, filledCount)
        assertTrue(active.take(filledCount).all { it })
        assertTrue(active.drop(filledCount).none { it })
        assertEquals(0, contiguousMeterFillCount(Float.NaN, 24))
        assertEquals(0, contiguousMeterFillCount(0.5f, 0))
    }

    private fun availableFrame(bands: FloatArray) = SpectrumFrame(
        bands,
        null,
        null,
        48_000,
        SpectrumAvailability.AVAILABLE,
        1L
    )
}
