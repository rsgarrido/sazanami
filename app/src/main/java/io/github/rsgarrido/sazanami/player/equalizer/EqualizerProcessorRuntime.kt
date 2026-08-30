package io.github.rsgarrido.sazanami.player.equalizer

import io.github.rsgarrido.sazanami.player.equalizer.limiter.LimiterMeterSnapshot
import io.github.rsgarrido.sazanami.player.equalizer.limiter.LimiterPreparedConfiguration
import io.github.rsgarrido.sazanami.player.equalizer.limiter.LimiterTelemetryAccumulator

/** Processor-facing contract implemented independently for each physical audio pipeline. */
internal interface EqualizerProcessorRuntime {
    fun requestedSnapshot(): EqualizerRuntimeSnapshot
    fun publishProcessorFormat(format: EqualizerProcessorFormat?)
    fun prepareForProcessorFormat(
        format: EqualizerProcessorFormat
    ): PreparedEqualizerProcessingPath
    fun latestCompatiblePath(
        format: EqualizerProcessorFormat
    ): PreparedEqualizerProcessingPath?
    fun latestCompatibleLimiterConfiguration(
        format: EqualizerProcessorFormat
    ): LimiterPreparedConfiguration?
    fun isLimiterPreparationPending(format: EqualizerProcessorFormat): Boolean
    fun isEqualizerPreparationPending(format: EqualizerProcessorFormat): Boolean
    fun publishProcessorConfigured(configured: Boolean, bypassed: Boolean)
    fun publishAppliedPlan(
        plan: PreparedEqualizerPlan?,
        applicationMode: EqualizerPlanApplicationMode
    )
    fun publishTransitionStarted(totalFrameCount: Int, sampleRateHz: Int)
    fun publishTransitionInProgress(inProgress: Boolean)
    fun publishScratchBufferGrowthCount(growthCount: Int)
    fun publishLimiterProcessorState(
        effectivelyActive: Boolean,
        primed: Boolean,
        reprimeCount: Int
    )
    fun publishLimiterMeterSnapshot(snapshot: LimiterMeterSnapshot)
    fun publishLimiterTelemetry(accumulator: LimiterTelemetryAccumulator)
    fun limiterMeterResetVersion(): Long
    fun processorPerformanceTelemetryResetVersion(): Long
    fun performanceTelemetry(): EqualizerProcessorPerformanceTelemetry
    fun performanceTelemetryIfEnabled(): EqualizerProcessorPerformanceTelemetry?
    fun clearProcessorTelemetry()
}
