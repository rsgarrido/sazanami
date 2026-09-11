package io.github.rsgarrido.sazanami.ui.player

import io.github.rsgarrido.sazanami.player.spectrum.SpectrumAvailability
import io.github.rsgarrido.sazanami.player.spectrum.SpectrumFrame
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpectrumVisualAdaptersTest {
    @Test
    fun retroRack_maps28BandsTo18OrderedBoundedColumns() {
        val frame = availableFrame(FloatArray(28) { index -> index / 27f })
        val first = FloatArray(18)
        val second = FloatArray(18)

        assertTrue(fillRetroRackSpectrum(frame, first))
        assertTrue(fillRetroRackSpectrum(frame, second))

        assertArrayEquals(first, second, 0f)
        assertEquals(18, first.size)
        assertTrue(first.all { value -> value.isFinite() && value in 0f..1f })
        assertTrue(
            (1 until first.size).all { index ->
                first[index - 1] <= first[index]
            }
        )
    }

    @Test
    fun retroRack_keepsLocalizedLowAndHighEnergyAtOppositeEdges() {
        val lowColumns = FloatArray(18)
        val highColumns = FloatArray(18)

        fillRetroRackSpectrum(availableFrame(energyAt(0)), lowColumns)
        fillRetroRackSpectrum(availableFrame(energyAt(27)), highColumns)

        assertTrue(lowColumns.first() > 0f)
        assertTrue(lowColumns.drop(2).all { it == 0f })
        assertTrue(highColumns.last() > 0f)
        assertTrue(highColumns.dropLast(2).all { it == 0f })
    }

    @Test
    fun unavailableSpectrum_clearsRetroRackBuffer() {
        val rack = FloatArray(18) { 1f }

        assertFalse(fillRetroRackSpectrum(SpectrumFrame.unavailable(), rack))
        assertTrue(rack.all { it == 0f })
    }

    @Test
    fun retroRack_fixedTiltReducesTypicalLowFrequencyDominanceWithoutReordering() {
        val decliningBands = FloatArray(28) { index -> 1f - index * 0.025f }
        val uncalibrated = FloatArray(18)
        val calibrated = FloatArray(18)
        val frame = availableFrame(decliningBands)

        fillSpectrumRange(frame, uncalibrated)
        fillRetroRackSpectrum(frame, calibrated)

        val rawImbalance = uncalibrated.take(6).average() -
                uncalibrated.takeLast(6).average()
        val calibratedImbalance = calibrated.take(6).average() -
                calibrated.takeLast(6).average()
        assertTrue(calibratedImbalance < rawImbalance)
        assertTrue(calibrated.first() > 0f)
        assertTrue(calibrated.all { value -> value in 0f..1f })
    }

    @Test
    fun availableSpectrum_sanitizesAndBoundsUnexpectedBandValues() {
        val bands = FloatArray(28) { index ->
            when (index % 4) {
                0 -> Float.NaN
                1 -> -1f
                2 -> 2f
                else -> 0.5f
            }
        }
        val output = FloatArray(18)

        assertTrue(fillRetroRackSpectrum(availableFrame(bands), output))
        assertTrue(output.all { value -> value.isFinite() && value in 0f..1f })
    }

    @Test
    fun successiveFramesReplaceValuesInTheSameScratchBuffer() {
        val output = FloatArray(18)

        fillRetroRackSpectrum(availableFrame(energyAt(0)), output)
        val firstSnapshot = output.copyOf()
        fillRetroRackSpectrum(availableFrame(energyAt(27)), output)

        assertTrue(firstSnapshot.first() > 0f)
        assertTrue(firstSnapshot.last() == 0f)
        assertTrue(output.first() == 0f)
        assertTrue(output.last() > 0f)
        assertFalse(firstSnapshot.contentEquals(output))
    }

    private fun availableFrame(bands: FloatArray) = SpectrumFrame(
        bands,
        null,
        null,
        48_000,
        SpectrumAvailability.AVAILABLE,
        1L
    )

    private fun energyAt(index: Int) = FloatArray(28).also { bands -> bands[index] = 1f }
}
