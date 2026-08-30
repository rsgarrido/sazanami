package io.github.rsgarrido.sazanami.player.equalizer.limiter

import kotlin.math.abs
import kotlin.math.max

/**
 * Audio-thread-owned accumulator. Counts are updated as ordinary fields and
 * are copied without allocation to a bounded-rate exchange for diagnostics.
 */
internal class LimiterTelemetryAccumulator {
    private var callPrePeak = 0.0
    private var callPostPeak = 0.0
    private var callMinimumLinearGain = 1.0
    private var publicationPrePeak = 0.0
    private var publicationPostPeak = 0.0
    private var publicationMinimumLinearGain = 1.0
    private var currentLinearGain = 1.0
    private var overRangeSampleCount = 0L
    private var saturatedSampleCount = 0L
    private var limiterActiveFrameCount = 0L
    private var limiterReducedFrameCount = 0L

    fun beginProcessingCall() {
        callPrePeak = 0.0
        callPostPeak = 0.0
        callMinimumLinearGain = 1.0
    }

    fun observePreLimiterSample(sample: Float) {
        val magnitude = abs(sample.toDouble())
        observePreLimiterBlock(
            peakMagnitude = magnitude,
            overRangeSamples = if (magnitude > 1.0) 1L else 0L
        )
    }

    fun observePostLimiterSample(sample: Float) {
        observePostLimiterBlock(abs(sample.toDouble()))
    }

    fun observePreLimiterBlock(
        peakMagnitude: Double,
        overRangeSamples: Long
    ) {
        callPrePeak = max(callPrePeak, peakMagnitude)
        publicationPrePeak = max(publicationPrePeak, peakMagnitude)
        overRangeSampleCount += overRangeSamples
    }

    fun observePostLimiterBlock(peakMagnitude: Double) {
        callPostPeak = max(callPostPeak, peakMagnitude)
        publicationPostPeak =
            max(publicationPostPeak, peakMagnitude)
    }

    fun observeLimiterFrame(linearGain: Double) {
        observeLimiterBlock(
            finalLinearGain = linearGain,
            minimumLinearGain = linearGain,
            activeFrames = 1L,
            reducedFrames =
                if (isMeaningfullyReduced(linearGain)) 1L else 0L
        )
    }

    fun observeLimiterBlock(
        finalLinearGain: Double,
        minimumLinearGain: Double,
        activeFrames: Long,
        reducedFrames: Long
    ) {
        if (activeFrames <= 0L) return
        limiterActiveFrameCount += activeFrames
        limiterReducedFrameCount += reducedFrames
        currentLinearGain = finalLinearGain
        callMinimumLinearGain =
            minOf(callMinimumLinearGain, minimumLinearGain)
        publicationMinimumLinearGain =
            minOf(publicationMinimumLinearGain, minimumLinearGain)
    }

    fun observeLimiterInactive() {
        currentLinearGain = 1.0
    }

    fun observeSaturatedSample() {
        saturatedSampleCount++
    }

    fun observeSaturatedSamples(sampleCount: Long) {
        saturatedSampleCount += sampleCount
    }

    fun snapshot(): LimiterMeterSnapshot = LimiterMeterSnapshot(
        preLimiterPeakDbfs =
            LimiterMath.linearToDbfs(callPrePeak),
        postLimiterPeakDbfs =
            LimiterMath.linearToDbfs(callPostPeak),
        currentGainReductionDb =
            LimiterMath.gainReductionDb(currentLinearGain),
        maximumGainReductionDb =
            LimiterMath.gainReductionDb(callMinimumLinearGain),
        overRangeSampleCount = overRangeSampleCount,
        saturatedSampleCount = saturatedSampleCount,
        limiterActiveFrameCount = limiterActiveFrameCount,
        limiterReducedFrameCount = limiterReducedFrameCount
    )

    fun publishTo(exchange: LimiterTelemetryExchange) {
        exchange.publish(
            preLimiterPeakDbfs =
                LimiterMath.linearToDbfs(publicationPrePeak),
            postLimiterPeakDbfs =
                LimiterMath.linearToDbfs(publicationPostPeak),
            currentGainReductionDb =
                LimiterMath.gainReductionDb(currentLinearGain),
            maximumGainReductionDb =
                LimiterMath.gainReductionDb(
                    publicationMinimumLinearGain
                ),
            overRangeSampleCount = overRangeSampleCount,
            saturatedSampleCount = saturatedSampleCount,
            limiterActiveFrameCount = limiterActiveFrameCount,
            limiterReducedFrameCount = limiterReducedFrameCount
        )
        publicationPrePeak = 0.0
        publicationPostPeak = 0.0
        publicationMinimumLinearGain = 1.0
    }

    fun reset() {
        callPrePeak = 0.0
        callPostPeak = 0.0
        callMinimumLinearGain = 1.0
        publicationPrePeak = 0.0
        publicationPostPeak = 0.0
        publicationMinimumLinearGain = 1.0
        currentLinearGain = 1.0
        overRangeSampleCount = 0L
        saturatedSampleCount = 0L
        limiterActiveFrameCount = 0L
        limiterReducedFrameCount = 0L
    }

    companion object {
        private const val REDUCTION_EPSILON_DB = 1.0e-6
        private val REDUCTION_THRESHOLD_LINEAR_GAIN =
            LimiterMath.dbToLinear(-REDUCTION_EPSILON_DB)

        fun isMeaningfullyReduced(linearGain: Double): Boolean =
            linearGain < REDUCTION_THRESHOLD_LINEAR_GAIN
    }
}
