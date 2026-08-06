package com.example.cdplaya.data.listening

import com.example.cdplaya.data.local.ListeningEndReason
import com.example.cdplaya.data.local.ListeningQualificationReason
import com.example.cdplaya.data.local.ListeningSource
import com.example.cdplaya.data.local.ListeningTimestampEvidence
import com.example.cdplaya.data.local.ListeningQualificationPolicy
import com.example.cdplaya.data.local.ListeningCompletionClassification
import com.example.cdplaya.data.local.ListeningEventPublicationState

/**
 * Pure state machine for one native playback attempt at a time.
 *
 * Callers provide explicit active/suspended/discontinuity commands. Media position is deliberately
 * absent: elapsed listening evidence comes only from [monotonicClock]. Every command is scoped by
 * playback session ID so a delayed callback from an old session cannot mutate a newer session.
 */
class ListeningSessionRecorder(
    private val monotonicClock: MonotonicClock,
    private val wallClock: WallClock,
    private val eventUuidGenerator: ListeningEventUuidGenerator
) {
    private data class ActiveSession(
        val request: ListeningSessionStart,
        val startedAt: Long,
        var committedListenedMs: Long = 0L,
        var playingSegmentStartedAt: Long? = null,
        var qualificationReason: ListeningQualificationReason = ListeningQualificationReason.NONE
    )

    private var activeSession: ActiveSession? = null
    private var lastFinalizedPlaybackSessionId: String? = null
    private var lastObservedMonotonicMs: Long? = null

    fun startSession(request: ListeningSessionStart): StartListeningSessionResult {
        val current = activeSession
        if (current != null) {
            if (current.request == request) {
                return StartListeningSessionResult.AlreadyActive(snapshotOf(current))
            }

            val reason = if (current.request.playbackSessionId == request.playbackSessionId) {
                StartListeningSessionRejection.CONFLICTING_SAME_SESSION
            } else {
                StartListeningSessionRejection.DIFFERENT_SESSION_ACTIVE
            }
            return StartListeningSessionResult.Rejected(
                reason = reason,
                activePlaybackSessionId = current.request.playbackSessionId
            )
        }
        if (lastFinalizedPlaybackSessionId == request.playbackSessionId) {
            return StartListeningSessionResult.AlreadyFinalized
        }

        val started = ActiveSession(
            request = request,
            startedAt = wallClock.currentTimeMillis()
        )
        activeSession = started
        return StartListeningSessionResult.Started(snapshotOf(started))
    }

    fun onPlaybackStarted(playbackSessionId: String): ListeningSessionCommandResult {
        val session = matchingSession(playbackSessionId) ?: return missingSessionResult()
        if (session.playingSegmentStartedAt != null) return ListeningSessionCommandResult.DUPLICATE

        session.playingSegmentStartedAt = monotonicNow()
        return ListeningSessionCommandResult.APPLIED
    }

    /** Pause and buffering both map here because neither represents genuinely active audio. */
    fun onPlaybackSuspended(playbackSessionId: String): ListeningSessionCommandResult {
        val session = matchingSession(playbackSessionId) ?: return missingSessionResult()
        if (session.playingSegmentStartedAt == null) return ListeningSessionCommandResult.DUPLICATE

        closePlayingSegment(session, monotonicNow())
        return ListeningSessionCommandResult.APPLIED
    }

    /**
     * Commits the segment up to the discontinuity and immediately reopens it when logically playing.
     * No media-position value is accepted or used.
     */
    fun onPositionDiscontinuity(playbackSessionId: String): ListeningSessionCommandResult {
        val session = matchingSession(playbackSessionId) ?: return missingSessionResult()
        if (session.playingSegmentStartedAt == null) return ListeningSessionCommandResult.DUPLICATE

        val now = monotonicNow()
        closePlayingSegment(session, now)
        session.playingSegmentStartedAt = now
        return ListeningSessionCommandResult.APPLIED
    }

    fun finalizeSession(
        playbackSessionId: String,
        endReason: ListeningEndReason
    ): FinalizeListeningSessionResult {
        val session = activeSession
        if (session == null) {
            return if (lastFinalizedPlaybackSessionId == playbackSessionId) {
                FinalizeListeningSessionResult.AlreadyFinalized
            } else {
                FinalizeListeningSessionResult.NoActiveSession
            }
        }
        if (session.request.playbackSessionId != playbackSessionId) {
            return FinalizeListeningSessionResult.SessionMismatch(
                activePlaybackSessionId = session.request.playbackSessionId
            )
        }

        if (session.playingSegmentStartedAt != null) {
            closePlayingSegment(session, monotonicNow())
        } else {
            updateTimeQualification(session, session.committedListenedMs)
        }
        if (endReason == ListeningEndReason.NATURAL_END) {
            session.qualificationReason = ListeningQualificationReason.NATURAL_END
        }

        val finalWallTime = wallClock.currentTimeMillis()
        val endedAt = maxOf(session.startedAt, finalWallTime)
        val eventUuid = eventUuidGenerator.newUuid()
        val draft = FinalizedListeningEventDraft(
            eventUuid = eventUuid,
            source = ListeningSource.CDPLAYA,
            trackIdentityId = session.request.trackIdentityId,
            localTrackBindingId = session.request.localTrackBindingId,
            playbackSessionId = session.request.playbackSessionId,
            startedAt = session.startedAt,
            endedAt = endedAt,
            attributionAt = session.startedAt,
            timestampEvidence = ListeningTimestampEvidence.NATIVE_EXACT,
            listenedMs = session.committedListenedMs,
            trackDurationMs = session.request.trackDurationMs,
            qualifiedAsPlay = session.qualificationReason != ListeningQualificationReason.NONE,
            qualificationReason = session.qualificationReason,
            qualificationRuleVersion = ListeningQualificationRuleV1.VERSION,
            qualificationPolicy = ListeningQualificationPolicy.CDPLAYA,
            endReason = endReason,
            completionClassification = if (endReason == ListeningEndReason.NATURAL_END) {
                ListeningCompletionClassification.NATIVE_NATURAL
            } else ListeningCompletionClassification.NONE,
            publicationState = ListeningEventPublicationState.NATIVE,
            sourceEventKey = null,
            importBatchId = null,
            createdAt = endedAt
        )

        activeSession = null
        lastFinalizedPlaybackSessionId = playbackSessionId
        return FinalizeListeningSessionResult.Finalized(draft)
    }

    /** Snapshot listening time includes, but does not commit, the open playing segment. */
    fun snapshot(): ListeningSessionSnapshot? = activeSession?.let(::snapshotOf)

    private fun snapshotOf(session: ActiveSession): ListeningSessionSnapshot {
        val listenedMs = session.playingSegmentStartedAt?.let { segmentStart ->
            saturatingAdd(session.committedListenedMs, nonNegativeDelta(monotonicNow(), segmentStart))
        } ?: session.committedListenedMs
        updateTimeQualification(session, listenedMs)

        return ListeningSessionSnapshot(
            playbackSessionId = session.request.playbackSessionId,
            trackIdentityId = session.request.trackIdentityId,
            localTrackBindingId = session.request.localTrackBindingId,
            startedAt = session.startedAt,
            trackDurationMs = session.request.trackDurationMs,
            accumulatedListenedMs = listenedMs,
            isPlaying = session.playingSegmentStartedAt != null,
            qualifiedAsPlay = session.qualificationReason != ListeningQualificationReason.NONE,
            qualificationReason = session.qualificationReason,
            qualificationRuleVersion = ListeningQualificationRuleV1.VERSION
        )
    }

    private fun closePlayingSegment(session: ActiveSession, now: Long) {
        val segmentStart = session.playingSegmentStartedAt ?: return
        val elapsed = nonNegativeDelta(now, segmentStart)
        session.committedListenedMs = saturatingAdd(session.committedListenedMs, elapsed)
        session.playingSegmentStartedAt = null
        updateTimeQualification(session, session.committedListenedMs)
    }

    private fun updateTimeQualification(session: ActiveSession, listenedMs: Long) {
        if (session.qualificationReason == ListeningQualificationReason.NONE &&
            ListeningQualificationRuleV1.isTimeQualified(session.request.trackDurationMs, listenedMs)
        ) {
            session.qualificationReason = ListeningQualificationReason.TIME_THRESHOLD
        }
    }

    private fun matchingSession(playbackSessionId: String): ActiveSession? =
        activeSession?.takeIf { it.request.playbackSessionId == playbackSessionId }

    private fun missingSessionResult(): ListeningSessionCommandResult {
        val activeId = activeSession?.request?.playbackSessionId
        return if (activeId == null) {
            ListeningSessionCommandResult.NO_ACTIVE_SESSION
        } else {
            ListeningSessionCommandResult.SESSION_MISMATCH
        }
    }

    /** Faulty backward readings are clamped to the process-local monotonic high-water mark. */
    private fun monotonicNow(): Long {
        val raw = monotonicClock.elapsedRealtimeMs()
        val previous = lastObservedMonotonicMs
        val safe = if (previous == null || raw >= previous) raw else previous
        lastObservedMonotonicMs = safe
        return safe
    }

    private fun nonNegativeDelta(end: Long, start: Long): Long {
        if (end <= start) return 0L
        return try {
            Math.subtractExact(end, start)
        } catch (_: ArithmeticException) {
            Long.MAX_VALUE
        }
    }

    private fun saturatingAdd(left: Long, right: Long): Long {
        if (right <= 0L) return left
        return if (left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right
    }
}
