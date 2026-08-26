package com.example.cdplaya.player

import kotlin.math.max

internal enum class CrossfadeTransitionState {
    IDLE,
    STANDBY_PREPARED,
    SCHEDULED,
    CROSSFADING,
    LOGICALLY_HANDED_OFF,
    COMPLETING,
    CANCELLED
}

internal fun interface CrossfadeCancellation {
    fun cancel()
}

internal fun interface CrossfadeScheduler {
    fun schedule(delayMillis: Long, action: () -> Unit): CrossfadeCancellation
}

internal fun interface CrossfadeClock {
    fun elapsedRealtimeMillis(): Long
}

internal data class CrossfadePlaybackSnapshot(
    val eligible: Boolean,
    val durationMillis: Long,
    val positionMillis: Long,
    val outgoingProgressing: Boolean,
    val incomingProgressing: Boolean
)

internal data class CrossfadeEligibilityInput(
    val durationMillis: Long,
    val standbyPrepared: Boolean,
    val targetMatches: Boolean,
    val incomingBaselineExact: Boolean,
    val outgoingBaselineExact: Boolean,
    val repeatOne: Boolean,
    val shuffleEnabled: Boolean,
    val pipelinesValid: Boolean,
    val cancelledByInteraction: Boolean
)

internal object CrossfadeEligibility {
    // The next integration session can add its same-album/live-album policy here without
    // changing scheduling, gain progression, or physical role ownership.
    fun isEligible(input: CrossfadeEligibilityInput): Boolean =
        input.durationMillis > CrossfadeTransitionCoordinator.CROSSFADE_DURATION_MILLIS &&
            input.standbyPrepared &&
            input.targetMatches &&
            input.incomingBaselineExact &&
            input.outgoingBaselineExact &&
            !input.repeatOne &&
            !input.shuffleEnabled &&
            input.pipelinesValid &&
            !input.cancelledByInteraction
}

internal interface CrossfadeTransitionOutput {
    fun snapshot(): CrossfadePlaybackSnapshot
    fun onCrossfadeStart(): Boolean
    fun onCrossfadeEnvelope(
        outgoingEnvelope: Float,
        incomingEnvelope: Float,
        progress: Float
    )
    fun onLogicalMidpoint(): Boolean
    fun onCrossfadeComplete(): Boolean
    fun onCrossfadeCancelled(logicallyHandedOff: Boolean)
}

/** Position-driven fixed-window crossfade state machine with replaceable timing. */
internal class CrossfadeTransitionCoordinator(
    private val output: CrossfadeTransitionOutput,
    private val clock: CrossfadeClock,
    private val scheduler: CrossfadeScheduler,
    private val durationMillis: Long = CROSSFADE_DURATION_MILLIS,
    private val frameIntervalMillis: Long = FRAME_INTERVAL_MILLIS
) {
    var state: CrossfadeTransitionState = CrossfadeTransitionState.IDLE
        private set

    val hasStarted: Boolean
        get() = state == CrossfadeTransitionState.CROSSFADING ||
            state == CrossfadeTransitionState.LOGICALLY_HANDED_OFF ||
            state == CrossfadeTransitionState.COMPLETING

    val logicallyHandedOff: Boolean
        get() = state == CrossfadeTransitionState.LOGICALLY_HANDED_OFF ||
            state == CrossfadeTransitionState.COMPLETING

    private var scheduledFrame: CrossfadeCancellation? = null
    private var released = false
    private var lastPositionMillis = 0L
    private var lastPositionChangeRealtimeMillis = 0L
    private var starting = false

    fun reevaluate() {
        if (
            released ||
            starting ||
            state == CrossfadeTransitionState.CANCELLED ||
            state == CrossfadeTransitionState.COMPLETING
        ) {
            return
        }
        val snapshot = output.snapshot()
        if (hasStarted) {
            if (
                !snapshot.eligible ||
                !snapshot.outgoingProgressing ||
                !snapshot.incomingProgressing ||
                outgoingPositionStalled(snapshot.positionMillis)
            ) {
                cancel(permanent = true)
                return
            }
            applyPosition(snapshot)
            return
        }

        if (!snapshot.eligible) {
            cancelScheduledFrame()
            state = CrossfadeTransitionState.IDLE
            return
        }

        state = CrossfadeTransitionState.STANDBY_PREPARED
        val startPositionMillis = snapshot.durationMillis - durationMillis
        if (snapshot.positionMillis >= startPositionMillis) {
            if (!snapshot.outgoingProgressing) {
                state = CrossfadeTransitionState.SCHEDULED
                scheduleNextFrame(SCHEDULE_REEVALUATION_MILLIS)
                return
            }
            state = CrossfadeTransitionState.CROSSFADING
            starting = true
            val started = try {
                output.onCrossfadeStart()
            } finally {
                starting = false
            }
            if (!started) {
                state = CrossfadeTransitionState.CANCELLED
                return
            }
            lastPositionMillis = snapshot.positionMillis
            lastPositionChangeRealtimeMillis = clock.elapsedRealtimeMillis()
            applyPosition(output.snapshot())
        } else {
            state = CrossfadeTransitionState.SCHEDULED
            scheduleNextFrame(
                minOf(
                    SCHEDULE_REEVALUATION_MILLIS,
                    max(1L, startPositionMillis - snapshot.positionMillis)
                )
            )
        }
    }

    fun cancel(
        permanent: Boolean,
        resolveAsLogicallyHandedOff: Boolean = logicallyHandedOff
    ) {
        if (released) return
        val wasStarted = hasStarted
        cancelScheduledFrame()
        state = if (permanent) {
            CrossfadeTransitionState.CANCELLED
        } else {
            CrossfadeTransitionState.IDLE
        }
        if (wasStarted) {
            output.onCrossfadeCancelled(resolveAsLogicallyHandedOff)
        }
    }

    fun completeAtNaturalEnd(): Boolean {
        if (!hasStarted || released) return false
        cancelScheduledFrame()
        output.onCrossfadeEnvelope(0f, 1f, 1f)
        if (!logicallyHandedOff && !performLogicalMidpoint()) return false
        return complete()
    }

    fun reset() {
        if (released) return
        cancelScheduledFrame()
        state = CrossfadeTransitionState.IDLE
        lastPositionMillis = 0L
        lastPositionChangeRealtimeMillis = 0L
    }

    fun release() {
        if (released) return
        released = true
        cancelScheduledFrame()
        state = CrossfadeTransitionState.CANCELLED
    }

    private fun applyPosition(snapshot: CrossfadePlaybackSnapshot) {
        if (!hasStarted) return
        val startPositionMillis = snapshot.durationMillis - durationMillis
        val progress = (
            (snapshot.positionMillis - startPositionMillis).toFloat() /
                durationMillis.toFloat()
            ).coerceIn(0f, 1f)
        val incomingEnvelope = smoothstep(progress)
        output.onCrossfadeEnvelope(
            outgoingEnvelope = 1f - incomingEnvelope,
            incomingEnvelope = incomingEnvelope,
            progress = progress
        )
        if (progress >= MIDPOINT && !logicallyHandedOff) {
            if (!performLogicalMidpoint()) return
        }
        if (progress >= 1f) {
            complete()
        } else {
            scheduleNextFrame(frameIntervalMillis)
        }
    }

    private fun performLogicalMidpoint(): Boolean {
        if (!output.onLogicalMidpoint()) {
            cancel(permanent = true)
            return false
        }
        state = CrossfadeTransitionState.LOGICALLY_HANDED_OFF
        return true
    }

    private fun complete(): Boolean {
        cancelScheduledFrame()
        state = CrossfadeTransitionState.COMPLETING
        if (!output.onCrossfadeComplete()) {
            cancel(permanent = true)
            return false
        }
        state = CrossfadeTransitionState.IDLE
        return true
    }

    private fun scheduleNextFrame(delayMillis: Long) {
        cancelScheduledFrame()
        scheduledFrame = scheduler.schedule(delayMillis) {
            scheduledFrame = null
            reevaluate()
        }
    }

    private fun outgoingPositionStalled(positionMillis: Long): Boolean {
        val now = clock.elapsedRealtimeMillis()
        if (positionMillis > lastPositionMillis) {
            lastPositionMillis = positionMillis
            lastPositionChangeRealtimeMillis = now
            return false
        }
        return now - lastPositionChangeRealtimeMillis >= MAX_POSITION_STALL_MILLIS
    }

    private fun cancelScheduledFrame() {
        scheduledFrame?.cancel()
        scheduledFrame = null
    }

    internal companion object {
        const val CROSSFADE_DURATION_MILLIS = 5_000L
        const val FRAME_INTERVAL_MILLIS = 50L
        const val SCHEDULE_REEVALUATION_MILLIS = 500L
        const val MAX_POSITION_STALL_MILLIS = 750L
        const val MIDPOINT = 0.5f

        fun smoothstep(progress: Float): Float {
            val value = progress.coerceIn(0f, 1f)
            return value * value * (3f - 2f * value)
        }
    }
}
