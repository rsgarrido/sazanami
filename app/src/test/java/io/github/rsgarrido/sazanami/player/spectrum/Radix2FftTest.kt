package io.github.rsgarrido.sazanami.player.spectrum

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Radix2FftTest {
    @Test
    fun `known bin-centered sine has deterministic dominant bin`() {
        val size = 1_024
        val expectedBin = 32
        val real = FloatArray(size) { index ->
            sin(2.0 * PI * expectedBin * index / size).toFloat()
        }
        val imaginary = FloatArray(size)

        Radix2Fft(size).transform(real, imaginary)

        var dominantBin = 1
        var dominantMagnitude = 0.0
        for (bin in 1 until size / 2) {
            val magnitude = hypot(real[bin].toDouble(), imaginary[bin].toDouble())
            if (magnitude > dominantMagnitude) {
                dominantMagnitude = magnitude
                dominantBin = bin
            }
        }
        assertEquals(expectedBin, dominantBin)
        assertTrue(dominantMagnitude > 500.0)
        assertTrue(abs(real[0]) < 0.001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non power of two size is rejected`() {
        Radix2Fft(1_000)
    }
}
