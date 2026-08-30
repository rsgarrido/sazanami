package io.github.rsgarrido.sazanami.player.equalizer

import java.nio.ByteBuffer
import kotlin.math.roundToInt

internal object Pcm16SampleConversion {
    private const val PCM16_SCALE = 32_768.0f
    private const val PCM16_MINIMUM = -32_768
    private const val PCM16_MAXIMUM = 32_767

    fun toNormalizedFloat(sample: Short): Float {
        return sample.toInt() / PCM16_SCALE
    }

    /**
     * Uses nearest-integer rounding followed by saturating PCM16 quantization.
     * No dither is applied.
     */
    fun fromNormalizedFloat(sample: Float): Short {
        return (sample * PCM16_SCALE)
            .roundToInt()
            .coerceIn(PCM16_MINIMUM, PCM16_MAXIMUM)
            .toShort()
    }

    fun decode(
        input: ByteBuffer,
        output: FloatArray,
        sampleCount: Int
    ) {
        var sampleIndex = 0
        while (sampleIndex < sampleCount) {
            output[sampleIndex] = toNormalizedFloat(input.short)
            sampleIndex++
        }
    }

    fun encode(
        input: FloatArray,
        output: ByteBuffer,
        sampleCount: Int
    ) {
        var sampleIndex = 0
        while (sampleIndex < sampleCount) {
            output.putShort(fromNormalizedFloat(input[sampleIndex]))
            sampleIndex++
        }
    }
}
