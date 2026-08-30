package io.github.rsgarrido.sazanami.player

import io.github.rsgarrido.sazanami.data.NativeListeningTrack
import io.github.rsgarrido.sazanami.data.listening.FinalizeListeningSessionResult
import io.github.rsgarrido.sazanami.data.listening.FinalizedListeningEventDraft
import io.github.rsgarrido.sazanami.data.listening.ListeningSessionRecorder
import io.github.rsgarrido.sazanami.data.listening.ListeningSessionStart
import io.github.rsgarrido.sazanami.data.listening.MonotonicClock
import io.github.rsgarrido.sazanami.data.listening.WallClock
import io.github.rsgarrido.sazanami.data.local.ListeningEndReason

data class PlaybackCallbackTimestamp(
    val monotonicMs: Long,
    val wallClockMs: Long
)

fun interface PlaybackSessionIdGenerator {
    fun newId(): String
}

fun interface NativeListeningTrackResolution {
    suspend fun resolve(evidence: ListeningMediaItemEvidence): NativeListeningTrack
}

enum class ListeningMediaTransitionReason {
    REPEAT,
    AUTOMATIC,
    SEEK,
    PLAYLIST_CHANGED
}

/**
 * Serialized, Media3-free callback reducer. Item-instance attempts may overlap, while each
 * underlying recorder retains its single-attempt invariant. Callback timestamps are installed
 * before recorder commands, preserving elapsed time even when identity resolution suspended.
 */
class PlaybackListeningCoordinator(
    private val recorder: ListeningSessionRecorder,
    private val callbackClock: PlaybackCallbackClock,
    private val trackResolution: NativeListeningTrackResolution,
    private val sessionIdGenerator: PlaybackSessionIdGenerator,
    private val onFinalized: (FinalizedListeningEventDraft) -> Unit,
    private val onFailure: (Throwable) -> Unit = {},
    private val additionalRecorderFactory: (() -> ListeningSessionRecorder)? = null
) {
    private data class Attempt(
        val itemInstanceId: String,
        val playbackSessionId: String,
        val recorder: ListeningSessionRecorder
    )
    private data class TransitionSignature(
        val from: String?,
        val to: String?,
        val reason: ListeningMediaTransitionReason,
        val monotonicMs: Long
    )

    private val recorders = mutableListOf(recorder)
    private val attempts = linkedMapOf<String, Attempt>()
    private var logicalItemInstanceId: String? = null
    private var lastTransition: TransitionSignature? = null

    suspend fun onIsPlayingChanged(
        evidence: ListeningMediaItemEvidence?,
        isPlaying: Boolean,
        timestamp: PlaybackCallbackTimestamp
    ) {
        if (!isPlaying) {
            val active = if (evidence != null) {
                attempts[evidence.itemInstanceId]
            } else {
                logicalItemInstanceId?.let(attempts::get)
            }
                ?: return
            at(timestamp) {
                active.recorder.onPlaybackSuspended(active.playbackSessionId)
            }
            return
        }

        val playable = evidence ?: return
        if (
            logicalItemInstanceId != null &&
            logicalItemInstanceId != playable.itemInstanceId
        ) {
            finalizeAllExcept(
                preservedItemInstanceId = playable.itemInstanceId,
                reason = ListeningEndReason.TRANSITION,
                timestamp = timestamp
            )
        }
        logicalItemInstanceId = playable.itemInstanceId
        onAudibleStarted(playable, timestamp)
    }

    suspend fun onAudibleStarted(
        evidence: ListeningMediaItemEvidence,
        timestamp: PlaybackCallbackTimestamp
    ) {
        val active = attempts[evidence.itemInstanceId]
            ?: startAttempt(evidence, timestamp)
            ?: return
        at(timestamp) { active.recorder.onPlaybackStarted(active.playbackSessionId) }
    }

    fun onAudibleEnded(
        evidence: ListeningMediaItemEvidence?,
        reason: ListeningEndReason,
        timestamp: PlaybackCallbackTimestamp
    ) {
        val itemInstanceId = evidence?.itemInstanceId ?: return
        finalizeAttempt(itemInstanceId, reason, timestamp)
    }

    fun onLogicalHandoff(evidence: ListeningMediaItemEvidence?) {
        val itemInstanceId = evidence?.itemInstanceId ?: return
        logicalItemInstanceId = itemInstanceId
    }

    suspend fun onMediaItemTransition(
        evidence: ListeningMediaItemEvidence?,
        reason: ListeningMediaTransitionReason,
        isPlaying: Boolean,
        timestamp: PlaybackCallbackTimestamp
    ) {
        val active = logicalItemInstanceId?.let(attempts::get)
        val signature = TransitionSignature(
            from = active?.itemInstanceId,
            to = evidence?.itemInstanceId,
            reason = reason,
            monotonicMs = timestamp.monotonicMs
        )
        if (lastTransition?.let { previous ->
                previous.to == signature.to && previous.reason == signature.reason &&
                    previous.monotonicMs == signature.monotonicMs
            } == true
        ) return
        lastTransition = signature

        val sameItem = active != null && evidence?.itemInstanceId == active.itemInstanceId
        val preservesAttempt = reason == ListeningMediaTransitionReason.PLAYLIST_CHANGED && sameItem
        if (active != null && !preservesAttempt) {
            val endReason = when (reason) {
                ListeningMediaTransitionReason.REPEAT,
                ListeningMediaTransitionReason.AUTOMATIC -> ListeningEndReason.NATURAL_END
                ListeningMediaTransitionReason.SEEK,
                ListeningMediaTransitionReason.PLAYLIST_CHANGED -> ListeningEndReason.TRANSITION
            }
            finalizeAll(endReason, timestamp)
        }

        if (isPlaying && evidence != null) {
            onIsPlayingChanged(evidence, true, timestamp)
        }
    }

    fun onPositionDiscontinuity(
        evidence: ListeningMediaItemEvidence?,
        timestamp: PlaybackCallbackTimestamp
    ) {
        val active = evidence?.itemInstanceId?.let(attempts::get)
            ?: logicalItemInstanceId?.let(attempts::get)
            ?: return
        if (evidence?.itemInstanceId != active.itemInstanceId) return
        at(timestamp) {
            active.recorder.onPositionDiscontinuity(active.playbackSessionId)
        }
    }

    fun onNaturalEnd(
        evidence: ListeningMediaItemEvidence?,
        timestamp: PlaybackCallbackTimestamp
    ) {
        onAudibleEnded(evidence, ListeningEndReason.NATURAL_END, timestamp)
    }

    fun onError(timestamp: PlaybackCallbackTimestamp) {
        val logical = logicalItemInstanceId ?: return
        finalizeAttempt(logical, ListeningEndReason.ERROR, timestamp)
    }

    fun onStopped(timestamp: PlaybackCallbackTimestamp) {
        finalizeAll(ListeningEndReason.STOPPED, timestamp)
    }

    fun onServiceDestroyed(timestamp: PlaybackCallbackTimestamp) {
        finalizeAll(ListeningEndReason.STOPPED, timestamp)
    }

    private suspend fun startAttempt(
        evidence: ListeningMediaItemEvidence,
        timestamp: PlaybackCallbackTimestamp
    ): Attempt? {
        val resolved = try {
            trackResolution.resolve(evidence)
        } catch (error: Throwable) {
            onFailure(error)
            return null
        }
        val attemptRecorder = acquireRecorder() ?: return null
        val sessionId = sessionIdGenerator.newId()
        at(timestamp) {
            attemptRecorder.startSession(
                ListeningSessionStart(
                    playbackSessionId = sessionId,
                    trackIdentityId = resolved.trackIdentityId,
                    localTrackBindingId = resolved.localTrackBindingId,
                    trackDurationMs = evidence.reference.duration.takeIf { it > 0L }
                )
            )
        }
        return Attempt(evidence.itemInstanceId, sessionId, attemptRecorder).also {
            attempts[evidence.itemInstanceId] = it
        }
    }

    private fun acquireRecorder(): ListeningSessionRecorder? {
        val activeRecorders = attempts.values.mapTo(mutableSetOf()) { it.recorder }
        recorders.firstOrNull { it !in activeRecorders }?.let { return it }
        val created = additionalRecorderFactory?.invoke()
        if (created == null) {
            onFailure(IllegalStateException("Overlapping listening requires another recorder."))
            return null
        }
        recorders += created
        return created
    }

    private fun finalizeAttempt(
        itemInstanceId: String,
        reason: ListeningEndReason,
        timestamp: PlaybackCallbackTimestamp
    ) {
        val active = attempts.remove(itemInstanceId) ?: return
        if (logicalItemInstanceId == itemInstanceId) logicalItemInstanceId = null
        val result = at(timestamp) {
            active.recorder.finalizeSession(active.playbackSessionId, reason)
        }
        if (result is FinalizeListeningSessionResult.Finalized) {
            runCatching { onFinalized(result.draft) }.onFailure(onFailure)
        }
    }

    private fun finalizeAll(
        reason: ListeningEndReason,
        timestamp: PlaybackCallbackTimestamp
    ) {
        attempts.keys.toList().forEach { itemInstanceId ->
            finalizeAttempt(itemInstanceId, reason, timestamp)
        }
    }

    private fun finalizeAllExcept(
        preservedItemInstanceId: String,
        reason: ListeningEndReason,
        timestamp: PlaybackCallbackTimestamp
    ) {
        attempts.keys.filter { it != preservedItemInstanceId }.forEach { itemInstanceId ->
            finalizeAttempt(itemInstanceId, reason, timestamp)
        }
    }

    private inline fun <T> at(timestamp: PlaybackCallbackTimestamp, block: () -> T): T {
        callbackClock.set(timestamp)
        return block()
    }
}

class PlaybackCallbackClock(
    initial: PlaybackCallbackTimestamp = PlaybackCallbackTimestamp(0L, 0L)
) : MonotonicClock, WallClock {
    private var current = initial

    fun set(timestamp: PlaybackCallbackTimestamp) {
        current = timestamp
    }

    override fun elapsedRealtimeMs(): Long = current.monotonicMs
    override fun currentTimeMillis(): Long = current.wallClockMs
}
