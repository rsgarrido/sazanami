package io.github.rsgarrido.sazanami.player.equalizer.dsp

/**
 * Convenience wrapper for non-real-time normalized floating-point PCM.
 */
internal class OfflineEqualizerProcessor {
    fun process(
        input: FloatArray,
        sampleRateHz: Int,
        channelCount: Int,
        configuration: EqualizerConfiguration,
        automaticHeadroomDb: Double = 0.0
    ): FloatArray {
        require(channelCount > 0) {
            "channelCount must be greater than 0"
        }
        require(input.size % channelCount == 0) {
            "input sample count must be divisible by channelCount"
        }

        val output = FloatArray(input.size)
        val engine = KotlinEqualizerDspEngine()
        engine.configure(
            configuration = configuration,
            sampleRateHz = sampleRateHz,
            channelCount = channelCount,
            automaticHeadroomDb = automaticHeadroomDb
        )
        engine.processInterleaved(
            input = input,
            inputOffset = 0,
            output = output,
            outputOffset = 0,
            frameCount = input.size / channelCount
        )
        return output
    }
}
