package io.github.rsgarrido.sazanami.player.equalizer.limiter

internal data class LimiterMeterSnapshot(
    val preLimiterPeakDbfs: Double =
        LimiterMath.SILENCE_FLOOR_DBFS,
    val postLimiterPeakDbfs: Double =
        LimiterMath.SILENCE_FLOOR_DBFS,
    val currentGainReductionDb: Double = 0.0,
    val maximumGainReductionDb: Double = 0.0,
    val overRangeSampleCount: Long = 0L,
    val saturatedSampleCount: Long = 0L,
    val limiterActiveFrameCount: Long = 0L,
    val limiterReducedFrameCount: Long = 0L
)
