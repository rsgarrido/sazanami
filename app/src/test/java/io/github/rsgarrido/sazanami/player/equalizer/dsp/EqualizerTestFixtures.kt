package io.github.rsgarrido.sazanami.player.equalizer.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals

internal const val COEFFICIENT_TOLERANCE = 1e-12
internal const val RESPONSE_DB_TOLERANCE = 0.03
internal const val SIGNAL_TOLERANCE = 2e-5f

internal val TEST_SAMPLE_RATES = intArrayOf(
    32_000,
    44_100,
    48_000,
    88_200,
    96_000,
    192_000
)

internal fun flatConfiguration(
    enabled: Boolean = true
): EqualizerConfiguration {
    return EqualizerConfiguration(
        enabled = enabled,
        preampDb = 0.0,
        filters = emptyList()
    )
}
internal fun assertFloatArraysClose(
    expected: FloatArray,
    actual: FloatArray,
    tolerance: Float = SIGNAL_TOLERANCE
) {
    assertEquals("array length", expected.size, actual.size)
    expected.indices.forEach { index ->
        assertEquals(
            "sample $index",
            expected[index],
            actual[index],
            tolerance
        )
    }
}

internal fun sineWave(
    frequencyHz: Double,
    sampleRateHz: Int,
    frameCount: Int,
    channelCount: Int = 1,
    amplitude: Double = 0.25
): FloatArray {
    return FloatArray(frameCount * channelCount) { sampleIndex ->
        val frameIndex = sampleIndex / channelCount
        (amplitude * sin(2.0 * PI * frequencyHz * frameIndex / sampleRateHz))
            .toFloat()
    }
}

internal fun rms(samples: FloatArray): Double {
    var squareSum = 0.0
    samples.forEach { sample ->
        squareSum += sample * sample
    }
    return sqrt(squareSum / samples.size)
}

/**
 * Test-only direct DFT of an impulse response. The finite impulse introduces
 * small truncation error, so callers use a response tolerance wider than the
 * coefficient-level tolerance.
 */
internal fun measuredMagnitudeDb(
    impulseResponse: FloatArray,
    frequencyHz: Double,
    sampleRateHz: Int
): Double {
    var real = 0.0
    var imaginary = 0.0
    impulseResponse.forEachIndexed { sampleIndex, sample ->
        val omega =
            2.0 * PI * frequencyHz * sampleIndex / sampleRateHz
        real += sample * cos(omega)
        imaginary -= sample * sin(omega)
    }
    return 20.0 * log10(sqrt(real * real + imaginary * imaginary))
}
