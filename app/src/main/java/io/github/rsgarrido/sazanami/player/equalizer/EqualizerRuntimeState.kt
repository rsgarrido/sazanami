package io.github.rsgarrido.sazanami.player.equalizer

enum class EqualizerPlanApplicationMode {
    NONE,
    CROSSFADE,
    DIRECT_AFTER_FLUSH,
    DIRECT_BYPASS
}

data class EqualizerRuntimeState(
    val processorConfigured: Boolean = false,
    val requestedEnabled: Boolean = false,
    val effectivelyActive: Boolean = false,
    val bypassed: Boolean = true,
    val transitionInProgress: Boolean = false,
    val comparisonSessionActive: Boolean = false,
    val comparisonBypassed: Boolean = false,
    val requestedMode: EqualizerMode = EqualizerMode.GRAPHIC,
    val activeMode: EqualizerMode = EqualizerMode.GRAPHIC,
    val parametricFilterCount: Int = 0,
    val parametricEnabledFilterCount: Int = 0,
    val configurationVersion: Long = 0L,
    val preparedPlanVersion: Long? = null,
    val appliedPlanVersion: Long? = null,
    val planPreparationLatencyMillis: Long? = null,
    val planApplicationLatencyMillis: Long? = null,
    val lastPlanApplicationMode: EqualizerPlanApplicationMode =
        EqualizerPlanApplicationMode.NONE,
    val lastTransitionFrameCount: Int = 0,
    val lastTransitionDurationMillis: Double = 0.0,
    val lastTransitionSampleRateHz: Int? = null,
    val sampleRateHz: Int? = null,
    val channelCount: Int? = null,
    val validFilterCount: Int = 0,
    val ignoredFilterCount: Int = 0,
    val automaticHeadroomDb: Double = 0.0,
    val requiresDecodedPcm: Boolean = false,
    val scratchBufferGrowthCount: Int = 0,
    val stalePreparedPlanDiscardCount: Long = 0L,
    val processorPerformanceTelemetryEnabled: Boolean = false,
    val processorPerformance:
        EqualizerProcessorPerformanceSnapshot =
        EqualizerProcessorPerformanceSnapshot(),
    val limiterRequestedEnabled: Boolean = false,
    val limiterEffectivelyActive: Boolean = false,
    val limiterCeilingDbfs: Double = -1.0,
    val limiterLookaheadFrames: Int = 0,
    val limiterLookaheadMilliseconds: Double = 0.0,
    val limiterReleaseMilliseconds: Double = 100.0,
    val limiterPrimed: Boolean = false,
    val preLimiterPeakDbfs: Double = -120.0,
    val postLimiterPeakDbfs: Double = -120.0,
    val currentGainReductionDb: Double = 0.0,
    val maximumRecentGainReductionDb: Double = 0.0,
    val overRangeSampleCount: Long = 0L,
    val saturatedSampleCount: Long = 0L,
    val limiterActiveFrameCount: Long = 0L,
    val limiterReducedFrameCount: Long = 0L,
    val limiterReprimeCount: Int = 0
)
