package io.github.rsgarrido.sazanami.player

import androidx.media3.common.MediaItem

/** Synchronous main-looper observations, separate from the conflated UI state bridge. */
internal interface DirectPlaybackListeningObserver {
    fun onActivated()
    fun onObservation(item: MediaItem?, state: IntegratedDirectPlaybackState, terminalConfirmed: Boolean)
    fun onDeactivated()
}

/** Service-owned ingress into the existing recorder; this binding does no listening accounting. */
internal class DirectPlaybackListeningBinding(
    private val adapter: PlaybackServiceListeningAdapter
) : DirectPlaybackListeningObserver {
    private val translator = DirectListeningEventTranslator()
    val isActive: Boolean get() = translator.isActive

    override fun onActivated() {
        if (isActive) return
        // The Android renderers and their bindings have already stopped/released. Drain any attempt
        // whose physical STATE_IDLE callback was detached before delivery, before Direct can start.
        adapter.onStopped()
        translator.activate()
    }

    override fun onObservation(
        item: MediaItem?,
        state: IntegratedDirectPlaybackState,
        terminalConfirmed: Boolean
    ) {
        val itemInstanceId = item?.listeningEvidence()?.itemInstanceId
        translator.observe(itemInstanceId, state, terminalConfirmed).forEach { event ->
            when (event) {
                DirectListeningEvent.PLAYING -> adapter.onIsPlayingChanged(item, true)
                DirectListeningEvent.SUSPENDED -> adapter.onIsPlayingChanged(item, false)
                DirectListeningEvent.DISCONTINUITY -> adapter.onPositionDiscontinuity(item)
                DirectListeningEvent.NATURAL_END -> adapter.onNaturalEnd(item)
                DirectListeningEvent.TRANSITION -> adapter.onMediaItemTransition(
                    item, ListeningMediaTransitionReason.SEEK, false)
                DirectListeningEvent.STOPPED -> adapter.onStopped()
                DirectListeningEvent.ERROR -> adapter.onError()
            }
        }
    }

    override fun onDeactivated() = translator.deactivate()
}

internal enum class DirectListeningEvent {
    PLAYING, SUSPENDED, DISCONTINUITY, NATURAL_END, TRANSITION, STOPPED, ERROR
}

/** Only translates lifecycle edges. Durations, qualification and session IDs belong to the recorder. */
internal class DirectListeningEventTranslator {
    var isActive = false
        private set
    private var generation: Long? = null
    private var itemInstanceId: String? = null
    private var playing = false
    private var attemptStarted = false
    private var finalized = false
    private var confirmedSeekSerial = 0L

    fun activate() {
        if (isActive) return
        isActive = true
        reset()
    }

    fun deactivate() {
        isActive = false
        reset()
    }

    fun observe(
        observedItemInstanceId: String?,
        state: IntegratedDirectPlaybackState,
        terminalConfirmed: Boolean = false
    ): List<DirectListeningEvent> {
        if (!isActive || observedItemInstanceId == null) return emptyList()
        val currentGeneration = generation
        if (currentGeneration != null && state.generation < currentGeneration) return emptyList()
        if (currentGeneration != state.generation) {
            // A successor cannot steal an attempt that is still awaiting its joined result.
            if (attemptStarted && !finalized) return emptyList()
            reset()
            generation = state.generation
            itemInstanceId = observedItemInstanceId
        }
        if (itemInstanceId != observedItemInstanceId || finalized) return emptyList()

        val events = mutableListOf<DirectListeningEvent>()
        val seekSerial = state.nativePauseCounters["confirmedSeekSerial"] ?: 0L
        if (seekSerial > confirmedSeekSerial && seekSerial >= state.pendingSeekSerial) {
            confirmedSeekSerial = seekSerial
            if (attemptStarted) events += DirectListeningEvent.DISCONTINUITY
        }

        // Matches the Direct Media3 active contract: READY + playWhenReady. Optimistic resume
        // intent, seek/drain, and provisional STOPPING do not represent active playback.
        val nowPlaying = state.phase == IntegratedDirectPhase.PLAYING && state.playWhenReady
        if (nowPlaying != playing) {
            playing = nowPlaying
            if (nowPlaying) {
                attemptStarted = true
                events += DirectListeningEvent.PLAYING
            } else if (attemptStarted) {
                events += DirectListeningEvent.SUSPENDED
            }
        }

        val terminal = if (terminalConfirmed) when (state.phase) {
            IntegratedDirectPhase.ENDED -> DirectListeningEvent.NATURAL_END
            IntegratedDirectPhase.STOPPED -> if (state.stoppedForReplacement)
                DirectListeningEvent.TRANSITION else DirectListeningEvent.STOPPED
            IntegratedDirectPhase.ERROR -> DirectListeningEvent.ERROR
            else -> null
        } else null
        if (terminal != null) {
            finalized = true
            if (attemptStarted) events += terminal
        }
        return events
    }

    private fun reset() {
        generation = null
        itemInstanceId = null
        playing = false
        attemptStarted = false
        finalized = false
        confirmedSeekSerial = 0L
    }
}
