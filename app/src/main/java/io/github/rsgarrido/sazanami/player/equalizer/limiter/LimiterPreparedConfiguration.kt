package io.github.rsgarrido.sazanami.player.equalizer.limiter

internal data class LimiterPreparedConfiguration(
    val enabled: Boolean,
    val ceilingDbfs: Double,
    val sampleRateHz: Int,
    val channelCount: Int,
    val lookaheadFrames: Int,
    val releaseCoefficient: Double,
    val ceilingLinear: Double,
    val configurationVersion: Long
) {
    init {
        require(ceilingDbfs.isFinite()) {
            "Prepared limiter ceiling must be finite"
        }
        require(
            ceilingDbfs in
                MIN_LIMITER_CEILING_DBFS..MAX_LIMITER_CEILING_DBFS
        ) {
            "Prepared limiter ceiling is out of range"
        }
        require(sampleRateHz > 0) {
            "Prepared limiter sample rate must be positive"
        }
        require(channelCount > 0) {
            "Prepared limiter channel count must be positive"
        }
        require(lookaheadFrames > 0) {
            "Prepared limiter lookahead must be positive"
        }
        require(
            releaseCoefficient.isFinite() &&
                releaseCoefficient in 0.0..1.0
        ) {
            "Prepared limiter release coefficient is invalid"
        }
        require(
            ceilingLinear.isFinite() &&
                ceilingLinear > 0.0 &&
                ceilingLinear <= 1.0
        ) {
            "Prepared limiter ceiling amplitude is invalid"
        }
        require(configurationVersion >= 0L) {
            "Prepared limiter version must be non-negative"
        }
    }

    companion object {
        fun prepare(
            configuration: LimiterConfiguration,
            sampleRateHz: Int,
            channelCount: Int,
            configurationVersion: Long
        ): LimiterPreparedConfiguration {
            val ceiling = configuration.ceilingDbfs
            return LimiterPreparedConfiguration(
                enabled = configuration.enabled,
                ceilingDbfs = ceiling,
                sampleRateHz = sampleRateHz,
                channelCount = channelCount,
                lookaheadFrames =
                    LimiterMath.lookaheadFrames(sampleRateHz),
                releaseCoefficient =
                    LimiterMath.releaseCoefficient(sampleRateHz),
                ceilingLinear = LimiterMath.dbToLinear(ceiling),
                configurationVersion = configurationVersion
            )
        }
    }
}
