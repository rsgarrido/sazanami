package io.github.rsgarrido.sazanami.player.equalizer

import kotlin.math.ceil

/**
 * Single-producer, allocation-free timing window for the Media3 audio thread.
 *
 * Snapshot sorting and immutable-object creation happen on the coordinator
 * thread. Timing is disabled by default and is enabled explicitly from
 * diagnostics or by deterministic tests.
 */
internal class EqualizerProcessorPerformanceTelemetry(
    windowCapacity: Int = DEFAULT_WINDOW_CAPACITY
) {
    private val durationsNanos = LongArray(windowCapacity)
    private val realTimeFactorsPpm = LongArray(windowCapacity)

    @Volatile
    private var sequence = 0L

    private var nextIndex = 0
    private var windowCount = 0
    private var totalCallCount = 0L
    private var totalFrameCount = 0L
    private var deadlineMissCount = 0L
    private var exactBypassCallCount = 0L
    private var equalizedCallCount = 0L
    private var transitionCallCount = 0L
    private var limiterCallCount = 0L
    private var configurePreparationNanos = 0L
    private var flushPreparationNanos = 0L
    private var configurePreparationCount = 0L
    private var flushPreparationCount = 0L
    private var firstConfigurationVersion = -1L
    private var firstConfigurationMode: EqualizerMode? = null
    private var firstConfigurationValidFilterCount = -1
    private var firstConfigurationSampleRateHz = 0
    private var firstConfigurationChannelCount = 0
    private var firstConfigurationLimiterActive = false
    private var lastConfigurationVersion = -1L
    private var lastConfigurationMode: EqualizerMode? = null
    private var lastConfigurationValidFilterCount = -1
    private var lastConfigurationSampleRateHz = 0
    private var lastConfigurationChannelCount = 0
    private var lastConfigurationLimiterActive = false
    private var measuredConfigurationChangeCount = 0L

    init {
        require(windowCapacity > 0) {
            "windowCapacity must be positive"
        }
    }

    fun recordProcessingCall(
        durationNanos: Long,
        frameCount: Int,
        sampleRateHz: Int,
        exactBypass: Boolean,
        equalized: Boolean,
        transitioning: Boolean,
        limiterActive: Boolean,
        configurationVersion: Long = -1L,
        configurationMode: EqualizerMode? = null,
        validFilterCount: Int = -1,
        channelCount: Int = 0
    ) {
        if (
            durationNanos < 0L ||
            frameCount <= 0 ||
            sampleRateHz <= 0
        ) {
            return
        }
        val audioDurationNanos =
            frameCount.toDouble() * NANOS_PER_SECOND / sampleRateHz
        val realTimeFactorPpm =
            (durationNanos / audioDurationNanos * PARTS_PER_MILLION)
                .toLong()
                .coerceAtLeast(0L)

        beginWrite()
        durationsNanos[nextIndex] = durationNanos
        realTimeFactorsPpm[nextIndex] = realTimeFactorPpm
        nextIndex = (nextIndex + 1) % durationsNanos.size
        windowCount = minOf(windowCount + 1, durationsNanos.size)
        totalCallCount++
        totalFrameCount += frameCount
        if (durationNanos > audioDurationNanos) {
            deadlineMissCount++
        }
        if (exactBypass) exactBypassCallCount++
        if (equalized) equalizedCallCount++
        if (transitioning) transitionCallCount++
        if (limiterActive) limiterCallCount++
        recordMeasuredConfiguration(
            configurationVersion = configurationVersion,
            configurationMode = configurationMode,
            validFilterCount = validFilterCount,
            sampleRateHz = sampleRateHz,
            channelCount = channelCount,
            limiterActive = limiterActive
        )
        endWrite()
    }

    fun recordConfigurePreparation(durationNanos: Long) {
        if (durationNanos < 0L) return
        beginWrite()
        configurePreparationNanos = durationNanos
        configurePreparationCount++
        endWrite()
    }

    fun recordFlushPreparation(durationNanos: Long) {
        if (durationNanos < 0L) return
        beginWrite()
        flushPreparationNanos = durationNanos
        flushPreparationCount++
        endWrite()
    }

    fun reset() {
        beginWrite()
        durationsNanos.fill(0L)
        realTimeFactorsPpm.fill(0L)
        nextIndex = 0
        windowCount = 0
        totalCallCount = 0L
        totalFrameCount = 0L
        deadlineMissCount = 0L
        exactBypassCallCount = 0L
        equalizedCallCount = 0L
        transitionCallCount = 0L
        limiterCallCount = 0L
        configurePreparationNanos = 0L
        flushPreparationNanos = 0L
        configurePreparationCount = 0L
        flushPreparationCount = 0L
        firstConfigurationVersion = -1L
        firstConfigurationMode = null
        firstConfigurationValidFilterCount = -1
        firstConfigurationSampleRateHz = 0
        firstConfigurationChannelCount = 0
        firstConfigurationLimiterActive = false
        lastConfigurationVersion = -1L
        lastConfigurationMode = null
        lastConfigurationValidFilterCount = -1
        lastConfigurationSampleRateHz = 0
        lastConfigurationChannelCount = 0
        lastConfigurationLimiterActive = false
        measuredConfigurationChangeCount = 0L
        endWrite()
    }

    fun snapshot(): EqualizerProcessorPerformanceSnapshot {
        repeat(MAXIMUM_SNAPSHOT_RETRIES) {
            val startSequence = sequence
            if (startSequence and 1L != 0L) return@repeat

            val observedWindowCount = windowCount
            val observedTotalCallCount = totalCallCount
            val observedTotalFrameCount = totalFrameCount
            val observedDeadlineMissCount = deadlineMissCount
            val observedExactBypassCallCount = exactBypassCallCount
            val observedEqualizedCallCount = equalizedCallCount
            val observedTransitionCallCount = transitionCallCount
            val observedLimiterCallCount = limiterCallCount
            val observedConfigurePreparationNanos =
                configurePreparationNanos
            val observedFlushPreparationNanos =
                flushPreparationNanos
            val observedConfigurePreparationCount =
                configurePreparationCount
            val observedFlushPreparationCount =
                flushPreparationCount
            val observedFirstConfiguration =
                measuredConfiguration(
                    version = firstConfigurationVersion,
                    mode = firstConfigurationMode,
                    validFilterCount =
                        firstConfigurationValidFilterCount,
                    sampleRateHz =
                        firstConfigurationSampleRateHz,
                    channelCount =
                        firstConfigurationChannelCount,
                    limiterActive =
                        firstConfigurationLimiterActive
                )
            val observedLastConfiguration =
                measuredConfiguration(
                    version = lastConfigurationVersion,
                    mode = lastConfigurationMode,
                    validFilterCount =
                        lastConfigurationValidFilterCount,
                    sampleRateHz =
                        lastConfigurationSampleRateHz,
                    channelCount =
                        lastConfigurationChannelCount,
                    limiterActive =
                        lastConfigurationLimiterActive
                )
            val observedConfigurationChangeCount =
                measuredConfigurationChangeCount
            val observedDurations =
                durationsNanos.copyOf(observedWindowCount)
            val observedRealTimeFactors =
                realTimeFactorsPpm.copyOf(observedWindowCount)

            if (startSequence == sequence) {
                observedDurations.sort()
                observedRealTimeFactors.sort()
                return EqualizerProcessorPerformanceSnapshot(
                    windowSampleCount = observedWindowCount,
                    totalCallCount = observedTotalCallCount,
                    totalFrameCount = observedTotalFrameCount,
                    deadlineMissCount = observedDeadlineMissCount,
                    exactBypassCallCount =
                        observedExactBypassCallCount,
                    equalizedCallCount = observedEqualizedCallCount,
                    transitionCallCount =
                        observedTransitionCallCount,
                    limiterCallCount = observedLimiterCallCount,
                    medianProcessingMillis =
                        observedDurations.percentileMillis(0.50),
                    p90ProcessingMillis =
                        observedDurations.percentileMillis(0.90),
                    p95ProcessingMillis =
                        observedDurations.percentileMillis(0.95),
                    p99ProcessingMillis =
                        observedDurations.percentileMillis(0.99),
                    maximumProcessingMillis =
                        observedDurations.lastOrNull()
                            ?.toMillis()
                            ?: 0.0,
                    medianRealTimeFactor =
                        observedRealTimeFactors.percentilePpm(0.50),
                    p95RealTimeFactor =
                        observedRealTimeFactors.percentilePpm(0.95),
                    p99RealTimeFactor =
                        observedRealTimeFactors.percentilePpm(0.99),
                    maximumRealTimeFactor =
                        observedRealTimeFactors.lastOrNull()
                            ?.toDouble()
                            ?.div(PARTS_PER_MILLION)
                            ?: 0.0,
                    configurePreparationMillis =
                        observedConfigurePreparationNanos.toMillis(),
                    flushPreparationMillis =
                        observedFlushPreparationNanos.toMillis(),
                    configurePreparationCount =
                        observedConfigurePreparationCount,
                    synchronousFormatPreparationCount =
                        observedFlushPreparationCount,
                    firstMeasuredConfiguration =
                        observedFirstConfiguration,
                    lastMeasuredConfiguration =
                        observedLastConfiguration,
                    measuredConfigurationChangeCount =
                        observedConfigurationChangeCount
                )
            }
        }
        return EqualizerProcessorPerformanceSnapshot()
    }

    private fun beginWrite() {
        sequence++
    }

    private fun recordMeasuredConfiguration(
        configurationVersion: Long,
        configurationMode: EqualizerMode?,
        validFilterCount: Int,
        sampleRateHz: Int,
        channelCount: Int,
        limiterActive: Boolean
    ) {
        if (
            configurationVersion < 0L ||
            configurationMode == null ||
            validFilterCount < 0 ||
            channelCount <= 0
        ) {
            return
        }
        if (firstConfigurationVersion < 0L) {
            firstConfigurationVersion = configurationVersion
            firstConfigurationMode = configurationMode
            firstConfigurationValidFilterCount = validFilterCount
            firstConfigurationSampleRateHz = sampleRateHz
            firstConfigurationChannelCount = channelCount
            firstConfigurationLimiterActive = limiterActive
        } else if (
            configurationVersion != lastConfigurationVersion ||
            configurationMode != lastConfigurationMode ||
            validFilterCount != lastConfigurationValidFilterCount ||
            sampleRateHz != lastConfigurationSampleRateHz ||
            channelCount != lastConfigurationChannelCount ||
            limiterActive != lastConfigurationLimiterActive
        ) {
            measuredConfigurationChangeCount++
        }
        lastConfigurationVersion = configurationVersion
        lastConfigurationMode = configurationMode
        lastConfigurationValidFilterCount = validFilterCount
        lastConfigurationSampleRateHz = sampleRateHz
        lastConfigurationChannelCount = channelCount
        lastConfigurationLimiterActive = limiterActive
    }

    private fun measuredConfiguration(
        version: Long,
        mode: EqualizerMode?,
        validFilterCount: Int,
        sampleRateHz: Int,
        channelCount: Int,
        limiterActive: Boolean
    ): EqualizerProcessorMeasuredConfiguration? {
        if (
            version < 0L ||
            mode == null ||
            validFilterCount < 0 ||
            sampleRateHz <= 0 ||
            channelCount <= 0
        ) {
            return null
        }
        return EqualizerProcessorMeasuredConfiguration(
            version = version,
            mode = mode,
            validFilterCount = validFilterCount,
            sampleRateHz = sampleRateHz,
            channelCount = channelCount,
            limiterActive = limiterActive
        )
    }

    private fun endWrite() {
        sequence++
    }

    private fun LongArray.percentileMillis(percentile: Double): Double {
        if (isEmpty()) return 0.0
        return this[percentileIndex(size, percentile)].toMillis()
    }

    private fun LongArray.percentilePpm(percentile: Double): Double {
        if (isEmpty()) return 0.0
        return this[percentileIndex(size, percentile)]
            .toDouble() / PARTS_PER_MILLION
    }

    private fun percentileIndex(
        size: Int,
        percentile: Double
    ): Int {
        return (ceil(percentile * size).toInt() - 1)
            .coerceIn(0, size - 1)
    }

    private fun Long.toMillis(): Double =
        this / NANOS_PER_MILLISECOND

    private companion object {
        const val DEFAULT_WINDOW_CAPACITY = 256
        const val MAXIMUM_SNAPSHOT_RETRIES = 4
        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val NANOS_PER_MILLISECOND = 1_000_000.0
        const val PARTS_PER_MILLION = 1_000_000.0
    }
}

data class EqualizerProcessorPerformanceSnapshot(
    val windowSampleCount: Int = 0,
    val totalCallCount: Long = 0L,
    val totalFrameCount: Long = 0L,
    val deadlineMissCount: Long = 0L,
    val exactBypassCallCount: Long = 0L,
    val equalizedCallCount: Long = 0L,
    val transitionCallCount: Long = 0L,
    val limiterCallCount: Long = 0L,
    val medianProcessingMillis: Double = 0.0,
    val p90ProcessingMillis: Double = 0.0,
    val p95ProcessingMillis: Double = 0.0,
    val p99ProcessingMillis: Double = 0.0,
    val maximumProcessingMillis: Double = 0.0,
    val medianRealTimeFactor: Double = 0.0,
    val p95RealTimeFactor: Double = 0.0,
    val p99RealTimeFactor: Double = 0.0,
    val maximumRealTimeFactor: Double = 0.0,
    val configurePreparationMillis: Double = 0.0,
    val flushPreparationMillis: Double = 0.0,
    val configurePreparationCount: Long = 0L,
    val synchronousFormatPreparationCount: Long = 0L,
    val firstMeasuredConfiguration:
        EqualizerProcessorMeasuredConfiguration? = null,
    val lastMeasuredConfiguration:
        EqualizerProcessorMeasuredConfiguration? = null,
    val measuredConfigurationChangeCount: Long = 0L
)

data class EqualizerProcessorMeasuredConfiguration(
    val version: Long,
    val mode: EqualizerMode,
    val validFilterCount: Int,
    val sampleRateHz: Int,
    val channelCount: Int,
    val limiterActive: Boolean
)
