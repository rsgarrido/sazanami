package com.example.cdplaya.player

import kotlin.math.abs
import kotlin.math.roundToLong

internal enum class SmoothPlaybackTransitionState {
    FULLY_AUDIBLE,
    FADING_OUT,
    PAUSED_SILENT,
    WAITING_FOR_AUDIBLE,
    FADING_IN
}

internal fun interface TransitionCancellation {
    fun cancel()
}

internal fun interface PlaybackTransitionScheduler {
    fun schedule(delayMillis: Long, action: () -> Unit): TransitionCancellation
}

internal fun interface PlaybackTransitionClock {
    fun elapsedRealtimeMillis(): Long
}

internal interface SmoothPlaybackTransitionOutput {
    fun setPhysicalPlayWhenReady(playWhenReady: Boolean)
    fun setEffectiveVolume(volume: Float)
}

/**
 * Owns the cosmetic gain envelope around explicit play/pause requests.
 *
 * The coordinator has no Android or Media3 dependency so timing and command forwarding can be
 * driven deterministically in local tests. All calls are expected on the playback looper.
 */
internal class SmoothPlaybackTransitionCoordinator(
    private val output: SmoothPlaybackTransitionOutput,
    private val clock: PlaybackTransitionClock,
    private val scheduler: PlaybackTransitionScheduler,
    initialPhysicalPlayWhenReady: Boolean,
    initialAudible: Boolean,
    initialBaselineVolume: Float,
    initiallyEnabled: Boolean = true,
    private val durationMillis: Long = DEFAULT_DURATION_MILLIS,
    private val frameIntervalMillis: Long = DEFAULT_FRAME_INTERVAL_MILLIS
) {
    var logicalPlayWhenReady: Boolean = initialPhysicalPlayWhenReady
        private set

    var baselineVolume: Float = initialBaselineVolume.coerceIn(0f, 1f)
        private set

    var envelope: Float = 1f
        private set

    var state: SmoothPlaybackTransitionState = when {
        !initialPhysicalPlayWhenReady -> SmoothPlaybackTransitionState.PAUSED_SILENT
        initialAudible -> SmoothPlaybackTransitionState.FULLY_AUDIBLE
        else -> SmoothPlaybackTransitionState.WAITING_FOR_AUDIBLE
    }
        private set

    private var enabled = initiallyEnabled
    private var physicalPlayWhenReady = initialPhysicalPlayWhenReady
    private var audible = initialAudible
    private var scheduledFrame: TransitionCancellation? = null
    private var transitionStartMillis = 0L
    private var transitionDurationMillis = 0L
    private var transitionStartEnvelope = envelope
    private var transitionTargetEnvelope = envelope
    private var released = false

    fun requestPlay() {
        if (released) return
        logicalPlayWhenReady = true

        if (!enabled) {
            cancelScheduledFrame()
            setEnvelope(1f)
            setPhysicalPlayWhenReadyIfChanged(true)
            state = if (audible) {
                SmoothPlaybackTransitionState.FULLY_AUDIBLE
            } else {
                SmoothPlaybackTransitionState.WAITING_FOR_AUDIBLE
            }
            return
        }

        // Silence is installed before asking ExoPlayer to resume, so a buffered stream cannot
        // briefly escape at the ReplayGain baseline.
        if (!physicalPlayWhenReady) {
            setEnvelope(0f)
            setPhysicalPlayWhenReadyIfChanged(true)
        }

        if (audible) {
            beginFade(
                targetEnvelope = 1f,
                transitionState = SmoothPlaybackTransitionState.FADING_IN
            )
        } else {
            cancelScheduledFrame()
            setEnvelope(0f)
            state = SmoothPlaybackTransitionState.WAITING_FOR_AUDIBLE
        }
    }

    fun requestPause() {
        if (released) return
        logicalPlayWhenReady = false

        if (!enabled) {
            resolveDisabledState()
            return
        }

        if (!audible || baselineVolume <= ENVELOPE_EPSILON || envelope <= ENVELOPE_EPSILON) {
            pauseImmediatelyAtSilence()
            return
        }

        beginFade(
            targetEnvelope = 0f,
            transitionState = SmoothPlaybackTransitionState.FADING_OUT
        )
    }

    fun setBaselineVolume(volume: Float) {
        if (released) return
        baselineVolume = volume.coerceIn(0f, 1f)
        applyEffectiveVolume()
    }

    fun setEnabled(enabled: Boolean) {
        if (released || this.enabled == enabled) return
        this.enabled = enabled

        if (!enabled) {
            resolveDisabledState()
        } else if (logicalPlayWhenReady && !audible) {
            cancelScheduledFrame()
            setEnvelope(0f)
            state = SmoothPlaybackTransitionState.WAITING_FOR_AUDIBLE
        }
    }

    fun onAudibilityChanged(isAudible: Boolean) {
        if (released || audible == isAudible) return
        audible = isAudible

        if (!enabled) {
            state = if (logicalPlayWhenReady && isAudible) {
                SmoothPlaybackTransitionState.FULLY_AUDIBLE
            } else if (logicalPlayWhenReady) {
                SmoothPlaybackTransitionState.WAITING_FOR_AUDIBLE
            } else {
                SmoothPlaybackTransitionState.PAUSED_SILENT
            }
            return
        }

        if (!isAudible) {
            if (logicalPlayWhenReady) {
                cancelScheduledFrame()
                setEnvelope(0f)
                state = SmoothPlaybackTransitionState.WAITING_FOR_AUDIBLE
            } else {
                pauseImmediatelyAtSilence()
            }
        } else if (logicalPlayWhenReady) {
            beginFade(
                targetEnvelope = 1f,
                transitionState = SmoothPlaybackTransitionState.FADING_IN
            )
        }
    }

    fun onPhysicalPlayWhenReadyChanged(playWhenReady: Boolean) {
        physicalPlayWhenReady = playWhenReady
    }

    /** Synchronizes a non-user Media3 play-intent change, such as audio-focus/noisy handling. */
    fun onSystemPlayWhenReadyChanged(playWhenReady: Boolean) {
        if (released) return
        physicalPlayWhenReady = playWhenReady
        logicalPlayWhenReady = playWhenReady
        cancelScheduledFrame()

        if (playWhenReady) {
            if (enabled) {
                setEnvelope(0f)
                state = SmoothPlaybackTransitionState.WAITING_FOR_AUDIBLE
            } else {
                setEnvelope(1f)
                state = if (audible) {
                    SmoothPlaybackTransitionState.FULLY_AUDIBLE
                } else {
                    SmoothPlaybackTransitionState.WAITING_FOR_AUDIBLE
                }
            }
        } else {
            setEnvelope(if (enabled) 0f else 1f)
            state = SmoothPlaybackTransitionState.PAUSED_SILENT
        }
    }

    /** Cancels a cosmetic transition without changing Media3's focus/suppression policy. */
    fun bypassForSafety() {
        if (released) return
        cancelScheduledFrame()
        if (enabled) {
            setEnvelope(0f)
        } else {
            setEnvelope(1f)
        }

        if (!logicalPlayWhenReady) {
            setPhysicalPlayWhenReadyIfChanged(false)
            state = SmoothPlaybackTransitionState.PAUSED_SILENT
        } else {
            state = SmoothPlaybackTransitionState.WAITING_FOR_AUDIBLE
        }
    }

    fun onImmediateStop() {
        if (released) return
        cancelScheduledFrame()
        setEnvelope(if (enabled) 0f else 1f)
        state = if (logicalPlayWhenReady) {
            SmoothPlaybackTransitionState.WAITING_FOR_AUDIBLE
        } else {
            SmoothPlaybackTransitionState.PAUSED_SILENT
        }
    }

    fun release() {
        if (released) return
        released = true
        cancelScheduledFrame()
    }

    private fun resolveDisabledState() {
        cancelScheduledFrame()
        setPhysicalPlayWhenReadyIfChanged(logicalPlayWhenReady)
        setEnvelope(1f)
        state = if (logicalPlayWhenReady && audible) {
            SmoothPlaybackTransitionState.FULLY_AUDIBLE
        } else if (logicalPlayWhenReady) {
            SmoothPlaybackTransitionState.WAITING_FOR_AUDIBLE
        } else {
            SmoothPlaybackTransitionState.PAUSED_SILENT
        }
    }

    private fun pauseImmediatelyAtSilence() {
        cancelScheduledFrame()
        setEnvelope(0f)
        setPhysicalPlayWhenReadyIfChanged(false)
        state = SmoothPlaybackTransitionState.PAUSED_SILENT
    }

    private fun beginFade(
        targetEnvelope: Float,
        transitionState: SmoothPlaybackTransitionState
    ) {
        cancelScheduledFrame()
        val target = targetEnvelope.coerceIn(0f, 1f)
        val distance = abs(target - envelope)
        if (distance <= ENVELOPE_EPSILON) {
            setEnvelope(target)
            completeFade(target)
            return
        }

        transitionStartMillis = clock.elapsedRealtimeMillis()
        transitionStartEnvelope = envelope
        transitionTargetEnvelope = target
        transitionDurationMillis = (durationMillis * distance)
            .roundToLong()
            .coerceAtLeast(1L)
        state = transitionState
        scheduleNextFrame()
    }

    private fun scheduleNextFrame() {
        scheduledFrame = scheduler.schedule(frameIntervalMillis) {
            scheduledFrame = null
            updateTransition()
        }
    }

    private fun updateTransition() {
        if (released) return
        val elapsed = (clock.elapsedRealtimeMillis() - transitionStartMillis).coerceAtLeast(0L)
        val progress = (elapsed.toFloat() / transitionDurationMillis.toFloat()).coerceIn(0f, 1f)
        val curvedProgress = smoothstep(progress)
        setEnvelope(
            transitionStartEnvelope +
                (transitionTargetEnvelope - transitionStartEnvelope) * curvedProgress
        )

        if (progress >= 1f) {
            completeFade(transitionTargetEnvelope)
        } else {
            scheduleNextFrame()
        }
    }

    private fun completeFade(targetEnvelope: Float) {
        setEnvelope(targetEnvelope)
        if (targetEnvelope <= ENVELOPE_EPSILON) {
            setPhysicalPlayWhenReadyIfChanged(false)
            state = SmoothPlaybackTransitionState.PAUSED_SILENT
        } else {
            state = SmoothPlaybackTransitionState.FULLY_AUDIBLE
        }
    }

    private fun setPhysicalPlayWhenReadyIfChanged(playWhenReady: Boolean) {
        if (physicalPlayWhenReady == playWhenReady) return
        physicalPlayWhenReady = playWhenReady
        output.setPhysicalPlayWhenReady(playWhenReady)
    }

    private fun setEnvelope(value: Float) {
        envelope = value.coerceIn(0f, 1f)
        applyEffectiveVolume()
    }

    private fun applyEffectiveVolume() {
        output.setEffectiveVolume((baselineVolume * envelope).coerceIn(0f, 1f))
    }

    private fun cancelScheduledFrame() {
        scheduledFrame?.cancel()
        scheduledFrame = null
    }

    private fun smoothstep(value: Float): Float = value * value * (3f - 2f * value)

    private companion object {
        const val DEFAULT_DURATION_MILLIS = 200L
        const val DEFAULT_FRAME_INTERVAL_MILLIS = 16L
        const val ENVELOPE_EPSILON = 0.0001f
    }
}
