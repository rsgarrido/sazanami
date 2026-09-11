package io.github.rsgarrido.sazanami.ui.player.pocketflip

import io.github.rsgarrido.sazanami.player.spectrum.SpectrumAvailability
import io.github.rsgarrido.sazanami.player.spectrum.SpectrumFrame
import io.github.rsgarrido.sazanami.ui.player.aggregateSpectrumRegionRms
import io.github.rsgarrido.sazanami.ui.player.calibrateSpectrumMeterLevel
import io.github.rsgarrido.sazanami.ui.player.contiguousMeterFillCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PocketFlipSpectrumPresentationTest {
    @Test
    fun lowerAndUpperRegionsProduceIndependentProgressiveLevels() {
        val lowerFrame = availableFrame(FloatArray(28).also { bands -> bands[7] = 0.8f })
        val upperFrame = availableFrame(FloatArray(28).also { bands -> bands[22] = 0.8f })

        assertTrue(lowerLevel(lowerFrame) > upperLevel(lowerFrame))
        assertTrue(upperLevel(upperFrame) > lowerLevel(upperFrame))
    }

    @Test
    fun modestRegionEnergyLeavesHeadroomAndStrongerEnergyFillsMoreSegments() {
        val modest = availableFrame(FloatArray(28) { 0.55f })
        val strong = availableFrame(FloatArray(28) { 0.90f })
        val modestFill = contiguousMeterFillCount(
            lowerLevel(modest),
            POCKET_FLIP_METER_SEGMENT_COUNT
        )
        val strongFill = contiguousMeterFillCount(
            lowerLevel(strong),
            POCKET_FLIP_METER_SEGMENT_COUNT
        )

        assertTrue(modestFill in 1 until POCKET_FLIP_METER_SEGMENT_COUNT)
        assertTrue(strongFill > modestFill)
        assertTrue(strongFill <= POCKET_FLIP_METER_SEGMENT_COUNT)
    }

    @Test
    fun unavailableSpectrumProducesNoActiveSegments() {
        assertEquals(
            0,
            contiguousMeterFillCount(
                lowerLevel(SpectrumFrame.unavailable()),
                POCKET_FLIP_METER_SEGMENT_COUNT
            )
        )
    }

    private fun lowerLevel(frame: SpectrumFrame): Float = calibrateSpectrumMeterLevel(
        aggregateSpectrumRegionRms(
            frame,
            POCKET_FLIP_LOWER_START_BAND,
            POCKET_FLIP_LOWER_END_BAND_EXCLUSIVE
        ),
        POCKET_FLIP_METER_DISPLAY_GAIN,
        POCKET_FLIP_METER_RESPONSE_EXPONENT
    )

    private fun upperLevel(frame: SpectrumFrame): Float = calibrateSpectrumMeterLevel(
        aggregateSpectrumRegionRms(
            frame,
            POCKET_FLIP_UPPER_START_BAND,
            frame.bandCount
        ),
        POCKET_FLIP_METER_DISPLAY_GAIN,
        POCKET_FLIP_METER_RESPONSE_EXPONENT
    )

    private fun availableFrame(bands: FloatArray) = SpectrumFrame(
        bands,
        null,
        null,
        48_000,
        SpectrumAvailability.AVAILABLE,
        1L
    )
}
