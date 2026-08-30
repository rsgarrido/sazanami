package io.github.rsgarrido.sazanami.player.equalizer

import io.github.rsgarrido.sazanami.player.equalizer.dsp.EqualizerConfiguration
import io.github.rsgarrido.sazanami.player.equalizer.limiter.LimiterConfiguration

internal data class EqualizerRuntimeSnapshot(
    val version: Long,
    val configuration: EqualizerConfiguration,
    val automaticHeadroomEnabled: Boolean,
    val mode: EqualizerMode = EqualizerMode.GRAPHIC,
    val limiterConfiguration: LimiterConfiguration =
        LimiterConfiguration()
) {
    init {
        require(version >= 0L) {
            "version must be non-negative"
        }
    }

    companion object {
        val DEFAULT = EqualizerRuntimeSnapshot(
            version = 0L,
            configuration = EqualizerConfiguration(
                enabled = false,
                preampDb = 0.0,
                filters = emptyList()
            ),
            automaticHeadroomEnabled = false,
            mode = EqualizerMode.GRAPHIC,
            limiterConfiguration = LimiterConfiguration()
        )
    }
}
