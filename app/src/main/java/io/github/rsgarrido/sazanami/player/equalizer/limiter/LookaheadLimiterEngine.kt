package io.github.rsgarrido.sazanami.player.equalizer.limiter

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Channel-linked sample-peak lookahead limiter.
 *
 * Each newly detected peak contributes a linear attack-rate constraint whose
 * deadline is exactly one lookahead window later. The envelope keeps the
 * steepest active constraint, so a stronger later peak cannot make an earlier
 * deadline unsafe. A final delayed-frame safety clamp protects against
 * floating-point rounding. Once no future peak requires stronger reduction,
 * the shared gain recovers exponentially using the fixed release coefficient.
 */
internal class LookaheadLimiterEngine(
    preparedConfiguration: LimiterPreparedConfiguration,
    private val telemetry: LimiterTelemetryAccumulator? =
        LimiterTelemetryAccumulator()
) {
    private var prepared = requireEnabled(preparedConfiguration)
    private val channelCount = prepared.channelCount
    private val lookaheadFrames = prepared.lookaheadFrames
    private val ringFrameCapacity = lookaheadFrames + 1
    private val audioDelay =
        FloatArray(ringFrameCapacity * channelCount)
    private val detectorIndices =
        LongArray(ringFrameCapacity + 1)
    private val detectorPeaks =
        DoubleArray(ringFrameCapacity + 1)

    private var detectorHead = 0
    private var detectorSize = 0
    private var receivedFrameCount = 0L
    private var outputFrameCount = 0L
    private var currentGain = 1.0
    private var attackStep = 0.0
    private var telemetryPostPeak = 0.0
    private var telemetryMinimumGain = 1.0
    private var telemetryFinalGain = 1.0
    private var telemetryActiveFrames = 0L
    private var telemetryReducedFrames = 0L

    val pendingFrameCount: Int
        get() = (receivedFrameCount - outputFrameCount)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()

    val isPrimed: Boolean
        get() = receivedFrameCount > lookaheadFrames

    val isDrained: Boolean
        get() = outputFrameCount == receivedFrameCount

    val currentLinearGain: Double
        get() = currentGain

    fun capacitySnapshot(): LookaheadLimiterCapacity {
        return LookaheadLimiterCapacity(
            ringFrameCapacity = ringFrameCapacity,
            channelCount = channelCount,
            audioDelayIdentity =
                System.identityHashCode(audioDelay),
            detectorIndicesIdentity =
                System.identityHashCode(detectorIndices),
            detectorPeaksIdentity =
                System.identityHashCode(detectorPeaks)
        )
    }

    fun updateCeiling(configuration: LimiterPreparedConfiguration) {
        require(configuration.enabled) {
            "Active limiter requires an enabled configuration"
        }
        require(configuration.sampleRateHz == prepared.sampleRateHz) {
            "Ceiling update cannot change sample rate"
        }
        require(configuration.channelCount == channelCount) {
            "Ceiling update cannot change channel count"
        }
        require(configuration.lookaheadFrames == lookaheadFrames) {
            "Ceiling update cannot change lookahead"
        }
        prepared = configuration
    }

    fun process(
        input: FloatArray,
        inputOffset: Int,
        frameCount: Int,
        output: FloatArray,
        outputOffset: Int
    ): Int {
        require(frameCount >= 0) { "Frame count must be non-negative" }
        require(
            inputOffset >= 0 &&
                inputOffset + frameCount * channelCount <= input.size
        ) {
            "Input range is invalid"
        }
        require(
            outputOffset >= 0 &&
                outputOffset + frameCount * channelCount <= output.size
        ) {
            "Output range is invalid"
        }

        beginOutputTelemetryBlock()
        var preLimiterPeak = 0.0
        var overRangeSampleCount = 0L
        var producedFrames = 0
        var frameIndex = 0
        while (frameIndex < frameCount) {
            val sourceFrameIndex = receivedFrameCount
            val sourceSampleOffset =
                inputOffset + frameIndex * channelCount
            val delaySlot =
                (sourceFrameIndex % ringFrameCapacity).toInt()
            val delaySampleOffset = delaySlot * channelCount
            var linkedPeak = 0.0
            var channelIndex = 0
            while (channelIndex < channelCount) {
                val sample = input[sourceSampleOffset + channelIndex]
                audioDelay[delaySampleOffset + channelIndex] = sample
                val magnitude = abs(sample.toDouble())
                linkedPeak = max(linkedPeak, magnitude)
                preLimiterPeak = max(preLimiterPeak, magnitude)
                if (magnitude > 1.0) {
                    overRangeSampleCount++
                }
                channelIndex++
            }
            addDetectorPeak(sourceFrameIndex, linkedPeak)
            scheduleAttackFor(linkedPeak)
            receivedFrameCount++

            if (receivedFrameCount > lookaheadFrames) {
                produceDelayedFrame(
                    output = output,
                    outputSampleOffset =
                        outputOffset + producedFrames * channelCount
                )
                producedFrames++
            }
            frameIndex++
        }
        telemetry?.observePreLimiterBlock(
            peakMagnitude = preLimiterPeak,
            overRangeSamples = overRangeSampleCount
        )
        completeOutputTelemetryBlock()
        return producedFrames
    }

    fun drain(
        output: FloatArray,
        outputOffset: Int,
        maximumFrameCount: Int
    ): Int {
        require(maximumFrameCount >= 0) {
            "Maximum drain frame count must be non-negative"
        }
        require(
            outputOffset >= 0 &&
                outputOffset + maximumFrameCount * channelCount <= output.size
        ) {
            "Drain output range is invalid"
        }
        beginOutputTelemetryBlock()
        var producedFrames = 0
        while (
            producedFrames < maximumFrameCount &&
            outputFrameCount < receivedFrameCount
        ) {
            produceDelayedFrame(
                output = output,
                outputSampleOffset =
                    outputOffset + producedFrames * channelCount
            )
            producedFrames++
        }
        completeOutputTelemetryBlock()
        return producedFrames
    }

    fun reset() {
        audioDelay.fill(0.0f)
        detectorIndices.fill(0L)
        detectorPeaks.fill(0.0)
        detectorHead = 0
        detectorSize = 0
        receivedFrameCount = 0L
        outputFrameCount = 0L
        currentGain = 1.0
        attackStep = 0.0
    }

    private fun scheduleAttackFor(linkedPeak: Double) {
        if (linkedPeak <= prepared.ceilingLinear) return
        val requiredGain =
            (prepared.ceilingLinear / linkedPeak).coerceIn(0.0, 1.0)
        if (requiredGain >= currentGain) return
        val requiredStep =
            (requiredGain - currentGain) / (lookaheadFrames + 1.0)
        attackStep = min(attackStep, requiredStep)
    }

    private fun produceDelayedFrame(
        output: FloatArray,
        outputSampleOffset: Int
    ) {
        val delayedFrameIndex = outputFrameCount
        removeDetectorFramesBefore(delayedFrameIndex)
        val detectedPeak = detectorMaximumPeak()
        val requiredWindowGain = if (detectedPeak > 0.0) {
            min(1.0, prepared.ceilingLinear / detectedPeak)
        } else {
            1.0
        }

        if (currentGain > requiredWindowGain) {
            if (attackStep >= 0.0) {
                val peakDistance =
                    (detectorMaximumIndex() - delayedFrameIndex)
                        .coerceAtLeast(0L)
                attackStep =
                    (requiredWindowGain - currentGain) /
                        (peakDistance + 1.0)
            }
            currentGain =
                max(requiredWindowGain, currentGain + attackStep)
        } else {
            attackStep = 0.0
            currentGain =
                requiredWindowGain -
                    (
                        requiredWindowGain - currentGain
                        ) * prepared.releaseCoefficient
            currentGain =
                currentGain.coerceIn(0.0, requiredWindowGain)
        }

        val delaySlot =
            (delayedFrameIndex % ringFrameCapacity).toInt()
        val delaySampleOffset = delaySlot * channelCount
        var delayedFramePeak = 0.0
        var channelIndex = 0
        while (channelIndex < channelCount) {
            delayedFramePeak = max(
                delayedFramePeak,
                abs(audioDelay[delaySampleOffset + channelIndex].toDouble())
            )
            channelIndex++
        }
        val frameSafeGain = if (delayedFramePeak > 0.0) {
            min(1.0, prepared.ceilingLinear / delayedFramePeak)
        } else {
            1.0
        }
        currentGain = min(currentGain, frameSafeGain)

        channelIndex = 0
        while (channelIndex < channelCount) {
            val limited =
                audioDelay[delaySampleOffset + channelIndex] *
                    currentGain.toFloat()
            output[outputSampleOffset + channelIndex] = limited
            telemetryPostPeak = max(
                telemetryPostPeak,
                abs(limited.toDouble())
            )
            channelIndex++
        }
        telemetryMinimumGain =
            min(telemetryMinimumGain, currentGain)
        telemetryFinalGain = currentGain
        telemetryActiveFrames++
        if (
            LimiterTelemetryAccumulator.isMeaningfullyReduced(
                currentGain
            )
        ) {
            telemetryReducedFrames++
        }
        outputFrameCount++
    }

    private fun beginOutputTelemetryBlock() {
        telemetryPostPeak = 0.0
        telemetryMinimumGain = 1.0
        telemetryFinalGain = currentGain
        telemetryActiveFrames = 0L
        telemetryReducedFrames = 0L
    }

    private fun completeOutputTelemetryBlock() {
        telemetry?.observePostLimiterBlock(telemetryPostPeak)
        telemetry?.observeLimiterBlock(
            finalLinearGain = telemetryFinalGain,
            minimumLinearGain = telemetryMinimumGain,
            activeFrames = telemetryActiveFrames,
            reducedFrames = telemetryReducedFrames
        )
    }

    private fun addDetectorPeak(frameIndex: Long, peak: Double) {
        while (detectorSize > 0) {
            val tailPosition =
                detectorPosition(detectorSize - 1)
            if (detectorPeaks[tailPosition] > peak) break
            detectorSize--
        }
        val insertionPosition = detectorPosition(detectorSize)
        detectorIndices[insertionPosition] = frameIndex
        detectorPeaks[insertionPosition] = peak
        detectorSize++
    }

    private fun removeDetectorFramesBefore(frameIndex: Long) {
        while (
            detectorSize > 0 &&
            detectorIndices[detectorHead] < frameIndex
        ) {
            detectorHead =
                (detectorHead + 1) % detectorIndices.size
            detectorSize--
        }
    }

    private fun detectorMaximumPeak(): Double =
        if (detectorSize == 0) 0.0 else detectorPeaks[detectorHead]

    private fun detectorMaximumIndex(): Long =
        if (detectorSize == 0) {
            outputFrameCount
        } else {
            detectorIndices[detectorHead]
        }

    private fun detectorPosition(relativeIndex: Int): Int =
        (detectorHead + relativeIndex) % detectorIndices.size

    private fun requireEnabled(
        configuration: LimiterPreparedConfiguration
    ): LimiterPreparedConfiguration {
        require(configuration.enabled) {
            "Lookahead limiter engine requires enabled configuration"
        }
        return configuration
    }
}

internal data class LookaheadLimiterCapacity(
    val ringFrameCapacity: Int,
    val channelCount: Int,
    val audioDelayIdentity: Int,
    val detectorIndicesIdentity: Int,
    val detectorPeaksIdentity: Int
)
