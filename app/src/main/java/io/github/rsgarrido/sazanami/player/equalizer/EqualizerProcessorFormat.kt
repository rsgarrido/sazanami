package io.github.rsgarrido.sazanami.player.equalizer

internal data class EqualizerProcessorFormat(
    val sampleRateHz: Int,
    val channelCount: Int,
    val pcmEncoding: Int
) {
    init {
        require(sampleRateHz > 0) {
            "sampleRateHz must be greater than 0"
        }
        require(channelCount > 0) {
            "channelCount must be greater than 0"
        }
    }
}
