package io.github.rsgarrido.sazanami.ui.player.pocketdisc

import io.github.rsgarrido.sazanami.player.spectrum.SpectrumAvailability
import io.github.rsgarrido.sazanami.player.spectrum.SpectrumFrame
import io.github.rsgarrido.sazanami.ui.player.aggregateSpectrumRegionRms
import io.github.rsgarrido.sazanami.ui.player.calibrateSpectrumMeterLevel
import io.github.rsgarrido.sazanami.ui.player.contiguousMeterFillCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PocketDiscSpectrumPresentationTest {
    @Test
    fun moderateSpectrumLeavesHeadroomAndStrongerSpectrumFillsFurther() {
        val moderateFill = fillCount(availableFrame(FloatArray(28) { 0.55f }))
        val strongFill = fillCount(availableFrame(FloatArray(28) { 0.90f }))

        assertTrue(moderateFill in 1 until SEGMENT_COUNT)
        assertTrue(strongFill > moderateFill)
        assertTrue(strongFill <= SEGMENT_COUNT)
    }

    @Test
    fun bothVisibleRowsDeriveTheSameMonoFillCount() {
        val frame = availableFrame(FloatArray(28) { index -> (index % 5) / 4f })

        val leftFill = fillCount(frame)
        val rightFill = fillCount(frame)

        assertEquals(leftFill, rightFill)
    }

    @Test
    fun unavailableSpectrumClearsTheMeter() {
        assertEquals(0, fillCount(SpectrumFrame.unavailable()))
    }

    private fun fillCount(frame: SpectrumFrame): Int = contiguousMeterFillCount(
        calibrateSpectrumMeterLevel(
            aggregateSpectrumRegionRms(frame),
            POCKET_DISC_METER_DISPLAY_GAIN,
            POCKET_DISC_METER_RESPONSE_EXPONENT
        ),
        SEGMENT_COUNT
    )

    private fun availableFrame(bands: FloatArray) = SpectrumFrame(
        bands,
        null,
        null,
        48_000,
        SpectrumAvailability.AVAILABLE,
        1L
    )

    private companion object {
        const val SEGMENT_COUNT = 36
    }
}
