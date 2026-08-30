package io.github.rsgarrido.sazanami.player.equalizer

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Pcm16SampleConversionTest {
    @Test
    fun pcmEndpointsAndZeroMapToExpectedNormalizedValues() {
        assertEquals(
            -1.0f,
            Pcm16SampleConversion.toNormalizedFloat(Short.MIN_VALUE),
            0.0f
        )
        assertEquals(
            32_767.0f / 32_768.0f,
            Pcm16SampleConversion.toNormalizedFloat(Short.MAX_VALUE),
            0.0f
        )
        assertEquals(
            0.0f,
            Pcm16SampleConversion.toNormalizedFloat(0),
            0.0f
        )
    }

    @Test
    fun signedValuesRoundTripWithinOnePcmUnit() {
        val samples = shortArrayOf(
            Short.MIN_VALUE,
            -30_000,
            -1,
            0,
            1,
            12_345,
            Short.MAX_VALUE
        )

        samples.forEach { sample ->
            val roundTrip = Pcm16SampleConversion.fromNormalizedFloat(
                Pcm16SampleConversion.toNormalizedFloat(sample)
            )
            assertTrue(
                "round trip for $sample",
                kotlin.math.abs(roundTrip.toInt() - sample.toInt()) <= 1
            )
        }
    }

    @Test
    fun valuesOutsideFullScaleSaturateWithoutWraparound() {
        assertEquals(
            Short.MAX_VALUE,
            Pcm16SampleConversion.fromNormalizedFloat(1.0f)
        )
        assertEquals(
            Short.MAX_VALUE,
            Pcm16SampleConversion.fromNormalizedFloat(25.0f)
        )
        assertEquals(
            Short.MIN_VALUE,
            Pcm16SampleConversion.fromNormalizedFloat(-1.0f)
        )
        assertEquals(
            Short.MIN_VALUE,
            Pcm16SampleConversion.fromNormalizedFloat(-25.0f)
        )
    }

    @Test
    fun byteBufferConversionPreservesInterleavedOrderAndNativeByteOrder() {
        val samples = shortArrayOf(
            1_000,
            -1_000,
            2_000,
            -2_000,
            3_000,
            -3_000
        )
        val input = ByteBuffer
            .allocateDirect(samples.size * Short.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
        samples.forEach(input::putShort)
        input.flip()
        val normalized = FloatArray(samples.size)
        val output = ByteBuffer
            .allocateDirect(samples.size * Short.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())

        Pcm16SampleConversion.decode(
            input = input,
            output = normalized,
            sampleCount = samples.size
        )
        Pcm16SampleConversion.encode(
            input = normalized,
            output = output,
            sampleCount = samples.size
        )
        output.flip()

        assertEquals(ByteOrder.nativeOrder(), output.order())
        samples.forEach { expected ->
            assertEquals(expected, output.short)
        }
    }
}
