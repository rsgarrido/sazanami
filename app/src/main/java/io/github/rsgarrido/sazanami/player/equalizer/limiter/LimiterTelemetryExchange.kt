package io.github.rsgarrido.sazanami.player.equalizer.limiter

import java.util.concurrent.atomic.AtomicLong

/**
 * Allocation-free single-writer exchange from the audio thread to the
 * background diagnostics coordinator.
 */
internal class LimiterTelemetryExchange {
    @Volatile
    private var sequence = 0L

    private val pendingPreLimiterPeakBits =
        AtomicLong(
            LimiterMath.SILENCE_FLOOR_DBFS.toBits()
        )
    private val pendingPostLimiterPeakBits =
        AtomicLong(
            LimiterMath.SILENCE_FLOOR_DBFS.toBits()
        )
    private val pendingMaximumReductionBits =
        AtomicLong(0.0.toBits())
    private var currentGainReductionDb = 0.0
    private var overRangeSampleCount = 0L
    private var saturatedSampleCount = 0L
    private var limiterActiveFrameCount = 0L
    private var limiterReducedFrameCount = 0L

    fun publish(
        preLimiterPeakDbfs: Double,
        postLimiterPeakDbfs: Double,
        currentGainReductionDb: Double,
        maximumGainReductionDb: Double,
        overRangeSampleCount: Long,
        saturatedSampleCount: Long,
        limiterActiveFrameCount: Long,
        limiterReducedFrameCount: Long
    ) {
        updateMaximum(
            holder = pendingPreLimiterPeakBits,
            candidate = preLimiterPeakDbfs
        )
        updateMaximum(
            holder = pendingPostLimiterPeakBits,
            candidate = postLimiterPeakDbfs
        )
        updateMaximum(
            holder = pendingMaximumReductionBits,
            candidate = maximumGainReductionDb
        )
        sequence++
        this.currentGainReductionDb = currentGainReductionDb
        this.overRangeSampleCount = overRangeSampleCount
        this.saturatedSampleCount = saturatedSampleCount
        this.limiterActiveFrameCount = limiterActiveFrameCount
        this.limiterReducedFrameCount = limiterReducedFrameCount
        sequence++
    }

    fun publish(snapshot: LimiterMeterSnapshot) {
        publish(
            preLimiterPeakDbfs = snapshot.preLimiterPeakDbfs,
            postLimiterPeakDbfs = snapshot.postLimiterPeakDbfs,
            currentGainReductionDb =
                snapshot.currentGainReductionDb,
            maximumGainReductionDb =
                snapshot.maximumGainReductionDb,
            overRangeSampleCount =
                snapshot.overRangeSampleCount,
            saturatedSampleCount =
                snapshot.saturatedSampleCount,
            limiterActiveFrameCount =
                snapshot.limiterActiveFrameCount,
            limiterReducedFrameCount =
                snapshot.limiterReducedFrameCount
        )
    }

    fun snapshot(): LimiterTelemetryExchangeSnapshot {
        repeat(MAXIMUM_SNAPSHOT_RETRIES) {
            val startSequence = sequence
            if (startSequence and 1L != 0L) return@repeat
            val observedCurrentGainReductionDb =
                currentGainReductionDb
            val observedOverRangeSampleCount =
                overRangeSampleCount
            val observedSaturatedSampleCount =
                saturatedSampleCount
            val observedLimiterActiveFrameCount =
                limiterActiveFrameCount
            val observedLimiterReducedFrameCount =
                limiterReducedFrameCount
            if (startSequence == sequence) {
                val snapshot = LimiterMeterSnapshot(
                preLimiterPeakDbfs = Double.fromBits(
                    pendingPreLimiterPeakBits.getAndSet(
                        LimiterMath.SILENCE_FLOOR_DBFS.toBits()
                    )
                ),
                postLimiterPeakDbfs = Double.fromBits(
                    pendingPostLimiterPeakBits.getAndSet(
                        LimiterMath.SILENCE_FLOOR_DBFS.toBits()
                    )
                ),
                currentGainReductionDb =
                    observedCurrentGainReductionDb,
                maximumGainReductionDb = Double.fromBits(
                    pendingMaximumReductionBits.getAndSet(
                        0.0.toBits()
                    )
                ),
                overRangeSampleCount =
                    observedOverRangeSampleCount,
                saturatedSampleCount =
                    observedSaturatedSampleCount,
                limiterActiveFrameCount =
                    observedLimiterActiveFrameCount,
                limiterReducedFrameCount =
                    observedLimiterReducedFrameCount
                )
                return LimiterTelemetryExchangeSnapshot(
                    sequence = startSequence,
                    meter = snapshot
                )
            }
        }
        return LimiterTelemetryExchangeSnapshot()
    }

    fun reset() {
        pendingPreLimiterPeakBits.set(
            LimiterMath.SILENCE_FLOOR_DBFS.toBits()
        )
        pendingPostLimiterPeakBits.set(
            LimiterMath.SILENCE_FLOOR_DBFS.toBits()
        )
        pendingMaximumReductionBits.set(0.0.toBits())
        publish(LimiterMeterSnapshot())
    }

    private fun updateMaximum(
        holder: AtomicLong,
        candidate: Double
    ) {
        while (true) {
            val previousBits = holder.get()
            val previous = Double.fromBits(previousBits)
            if (candidate <= previous) return
            if (
                holder.compareAndSet(
                    previousBits,
                    candidate.toBits()
                )
            ) {
                return
            }
        }
    }

    private companion object {
        const val MAXIMUM_SNAPSHOT_RETRIES = 4
    }
}

internal data class LimiterTelemetryExchangeSnapshot(
    val sequence: Long = 0L,
    val meter: LimiterMeterSnapshot = LimiterMeterSnapshot()
)
