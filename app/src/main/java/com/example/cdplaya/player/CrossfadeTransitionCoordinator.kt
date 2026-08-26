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
    val crossfadeDurationMillis: Long,
    val standbyPrepared: Boolean,
    val targetMatches: Boolean,
    val incomingBaselineExact: Boolean,
    val outgoingBaselineExact: Boolean,
    val repeatOne: Boolean,
    val shuffleEnabled: Boolean,
    val pipelinesValid: Boolean,
    val cancelledByInteraction: Boolean,
    val preserveNaturalAlbumTransition: Boolean
)

internal object CrossfadeEligibility {
    // Album preservation remains an eligibility concern; scheduling and role ownership stay
    // independent of the metadata rule that produced preserveNaturalAlbumTransition.
    fun isEligible(input: CrossfadeEligibilityInput): Boolean =
        input.crossfadeDurationMillis in
            CrossfadeTransitionCoordinator.MIN_CROSSFADE_DURATION_MILLIS..
            CrossfadeTransitionCoordinator.MAX_CROSSFADE_DURATION_MILLIS &&
            input.durationMillis > input.crossfadeDurationMillis &&
            input.standbyPrepared &&
            input.targetMatches &&
            input.incomingBaselineExact &&
            input.outgoingBaselineExact &&
            !input.repeatOne &&
            !input.shuffleEnabled &&
            input.pipelinesValid &&
            !input.cancelledByInteraction &&
            !input.preserveNaturalAlbumTransition
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
    durationMillis: Long = DEFAULT_CROSSFADE_DURATION_MILLIS,
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

    val currentDurationMillis: Long
        get() = activeDurationMillis ?: configuredDurationMillis

    private var scheduledFrame: CrossfadeCancellation? = null
    private var released = false
    private var lastPositionMillis = 0L
    private var lastPositionChangeRealtimeMillis = 0L
    private var crossfadeStartRealtimeMillis = 0L
    private var incomingHasProgressed = false
    private var starting = false
    private var configuredDurationMillis = normalizeDuration(durationMillis)
    private var activeDurationMillis: Long? = null

    fun updateDuration(durationMillis: Long) {
        if (released) return
        configuredDurationMillis = normalizeDuration(durationMillis)
    }

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
            if (!snapshot.eligible) {
                cancel(permanent = true, traceReason = "eligibility_changed")
                return
            }
            if (!snapshot.outgoingProgressing) {
                cancel(permanent = true, traceReason = "outgoing_not_progressing")
                return
            }
            if (outgoingPositionStalled(snapshot.positionMillis)) {
                cancel(permanent = true, traceReason = "outgoing_position_stalled")
                return
            }
            if (!snapshot.incomingProgressing) {
                val stillStarting =
                    !incomingHasProgressed &&
                        clock.elapsedRealtimeMillis() - crossfadeStartRealtimeMillis <
                        INCOMING_START_GRACE_MILLIS
                if (stillStarting) {
                    scheduleNextFrame(frameIntervalMillis)
                } else {
                    cancel(permanent = true, traceReason = "incoming_not_progressing")
                }
                return
            }
            if (!incomingHasProgressed) {
                CrossfadeTrace.log("INCOMING_PROGRESSING")
            }
            incomingHasProgressed = true
            applyPosition(snapshot)
            return
        }

        if (!snapshot.eligible) {
            cancelScheduledFrame()
            state = CrossfadeTransitionState.IDLE
            return
        }

        val previousState = state
        state = CrossfadeTransitionState.STANDBY_PREPARED
        val transitionDurationMillis = currentDurationMillis
        val startPositionMillis = snapshot.durationMillis - transitionDurationMillis
        if (snapshot.positionMillis >= startPositionMillis) {
            if (!snapshot.outgoingProgressing) {
                if (previousState != CrossfadeTransitionState.SCHEDULED) {
                    CrossfadeTrace.log("SCHEDULED reason=waiting_for_outgoing_progress")
                }
                state = CrossfadeTransitionState.SCHEDULED
                scheduleNextFrame(SCHEDULE_REEVALUATION_MILLIS)
                return
            }
            CrossfadeTrace.log(
                "ENTER_WINDOW positionMs=${snapshot.positionMillis} " +
                    "durationMs=${snapshot.durationMillis} crossfadeDurationMs=" +
                    transitionDurationMillis
            )
            state = CrossfadeTransitionState.CROSSFADING
            activeDurationMillis = configuredDurationMillis
            starting = true
            CrossfadeTrace.log("START_REQUESTED durationMs=$activeDurationMillis")
            val started = try {
                output.onCrossfadeStart()
            } finally {
                starting = false
            }
            if (!started) {
                activeDurationMillis = null
                state = CrossfadeTransitionState.CANCELLED
                CrossfadeTrace.log("FALLBACK reason=start_rejected")
                return
            }
            CrossfadeTrace.log("CROSSFADING")
            lastPositionMillis = snapshot.positionMillis
            lastPositionChangeRealtimeMillis = clock.elapsedRealtimeMillis()
            crossfadeStartRealtimeMillis = lastPositionChangeRealtimeMillis
            incomingHasProgressed = false
            reevaluate()
        } else {
            if (previousState != CrossfadeTransitionState.SCHEDULED) {
                CrossfadeTrace.log(
                    "SCHEDULED startPositionMs=$startPositionMillis " +
                        "positionMs=${snapshot.positionMillis}"
                )
            }
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
        resolveAsLogicallyHandedOff: Boolean = logicallyHandedOff,
        traceReason: String = "explicit"
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
        CrossfadeTrace.log(
            "CANCEL reason=$traceReason started=$wasStarted " +
                "logicallyHandedOff=$resolveAsLogicallyHandedOff permanent=$permanent"
        )
        activeDurationMillis = null
        crossfadeStartRealtimeMillis = 0L
        incomingHasProgressed = false
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
        crossfadeStartRealtimeMillis = 0L
        incomingHasProgressed = false
        activeDurationMillis = null
    }

    fun release() {
        if (released) return
        released = true
        cancelScheduledFrame()
        crossfadeStartRealtimeMillis = 0L
        incomingHasProgressed = false
        activeDurationMillis = null
        state = CrossfadeTransitionState.CANCELLED
    }

    private fun applyPosition(snapshot: CrossfadePlaybackSnapshot) {
        if (!hasStarted) return
        val transitionDurationMillis = currentDurationMillis
        val startPositionMillis = snapshot.durationMillis - transitionDurationMillis
        val progress = (
            (snapshot.positionMillis - startPositionMillis).toFloat() /
                transitionDurationMillis.toFloat()
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
            cancel(permanent = true, traceReason = "logical_midpoint_failed")
            return false
        }
        state = CrossfadeTransitionState.LOGICALLY_HANDED_OFF
        CrossfadeTrace.log("MIDPOINT")
        return true
    }

    private fun complete(): Boolean {
        cancelScheduledFrame()
        state = CrossfadeTransitionState.COMPLETING
        if (!output.onCrossfadeComplete()) {
            cancel(permanent = true, traceReason = "completion_failed")
            return false
        }
        crossfadeStartRealtimeMillis = 0L
        incomingHasProgressed = false
        activeDurationMillis = null
        state = CrossfadeTransitionState.IDLE
        CrossfadeTrace.log("COMPLETE")
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
        const val MIN_CROSSFADE_DURATION_MILLIS = 1_000L
        const val MAX_CROSSFADE_DURATION_MILLIS = 12_000L
        const val DEFAULT_CROSSFADE_DURATION_MILLIS = 5_000L
        const val FRAME_INTERVAL_MILLIS = 50L
        const val SCHEDULE_REEVALUATION_MILLIS = 500L
        const val MAX_POSITION_STALL_MILLIS = 750L
        const val INCOMING_START_GRACE_MILLIS = 750L
        const val MIDPOINT = 0.5f

        fun smoothstep(progress: Float): Float {
            val value = progress.coerceIn(0f, 1f)
            return value * value * (3f - 2f * value)
        }

        private fun normalizeDuration(durationMillis: Long): Long =
            durationMillis.coerceIn(
                MIN_CROSSFADE_DURATION_MILLIS,
                MAX_CROSSFADE_DURATION_MILLIS
            )
    }
}
