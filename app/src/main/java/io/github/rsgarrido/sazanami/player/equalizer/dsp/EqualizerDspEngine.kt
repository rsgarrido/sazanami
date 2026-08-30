package io.github.rsgarrido.sazanami.player.equalizer.dsp

/**
 * Pure Kotlin boundary for normalized, interleaved floating-point PCM.
 */
internal interface EqualizerDspEngine {
    fun configure(
        configuration: EqualizerConfiguration,
        sampleRateHz: Int,
        channelCount: Int,
        automaticHeadroomDb: Double = 0.0
    )

    fun processInterleaved(
        input: FloatArray,
        inputOffset: Int,
        output: FloatArray,
        outputOffset: Int,
        frameCount: Int
    )

    fun reset()
}
