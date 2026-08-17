package com.example.cdplaya.data.listening

import com.example.cdplaya.data.local.ListeningEndReason
import com.example.cdplaya.data.local.ListeningQualificationReason
import com.example.cdplaya.data.local.ListeningSource
import com.example.cdplaya.data.local.ListeningTimestampEvidence
import com.example.cdplaya.data.local.ListeningQualificationPolicy
import com.example.cdplaya.data.local.ListeningCompletionClassification
import com.example.cdplaya.data.local.ListeningEventPublicationState
import com.example.cdplaya.data.local.toEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningSessionRecorderTest {
    private val monotonicClock = FakeMonotonicClock()
    private val wallClock = FakeWallClock(10_000L)
    private var uuidNumber = 0
    private val recorder = ListeningSessionRecorder(
        monotonicClock = monotonicClock,
        wallClock = wallClock,
        eventUuidGenerator = ListeningEventUuidGenerator { "event-${++uuidNumber}" }
    )

    @Test
    fun sessionStartsAtZeroAndDoesNotAccumulateBeforePlaybackBegins() {
        val result = recorder.startSession(start())
        assertTrue(result is StartListeningSessionResult.Started)

        monotonicClock.advance(5_000L)
        val snapshot = requireNotNull(recorder.snapshot())
        assertEquals(0L, snapshot.accumulatedListenedMs)
        assertFalse(snapshot.isPlaying)
        assertFalse(snapshot.qualifiedAsPlay)
        assertEquals(10_000L, snapshot.startedAt)
    }

    @Test
    fun activePlaybackSnapshotIncludesOpenSegmentWithoutCommittingIt() {
        recorder.startSession(start())
        assertEquals(ListeningSessionCommandResult.APPLIED, recorder.onPlaybackStarted("session-1"))
        monotonicClock.advance(12_345L)

        assertEquals(12_345L, recorder.snapshot()?.accumulatedListenedMs)
        assertEquals(12_345L, recorder.snapshot()?.accumulatedListenedMs)
        assertTrue(requireNotNull(recorder.snapshot()).isPlaying)
    }

    @Test
    fun pauseBufferingAndResumeExcludeSuspendedTimeAcrossMultipleCycles() {
        recorder.startSession(start())
        recorder.onPlaybackStarted("session-1")
        monotonicClock.advance(1_000L)
        assertEquals(ListeningSessionCommandResult.APPLIED, recorder.onPlaybackSuspended("session-1"))
        assertEquals(ListeningSessionCommandResult.DUPLICATE, recorder.onPlaybackSuspended("session-1"))

        monotonicClock.advance(20_000L) // paused/buffering time
        assertEquals(1_000L, recorder.snapshot()?.accumulatedListenedMs)
        recorder.onPlaybackStarted("session-1")
        monotonicClock.advance(2_000L)
        recorder.onPlaybackSuspended("session-1")
        monotonicClock.advance(30_000L) // another suspended interval
        recorder.onPlaybackStarted("session-1")
        monotonicClock.advance(3_000L)
        recorder.onPlaybackSuspended("session-1")

        assertEquals(6_000L, recorder.snapshot()?.accumulatedListenedMs)
    }

    @Test
    fun duplicatePlayingCommandDoesNotRestartOrDoubleCountSegment() {
        recorder.startSession(start())
        recorder.onPlaybackStarted("session-1")
        monotonicClock.advance(1_000L)
        assertEquals(ListeningSessionCommandResult.DUPLICATE, recorder.onPlaybackStarted("session-1"))
        monotonicClock.advance(2_000L)
        recorder.onPlaybackSuspended("session-1")

        assertEquals(3_000L, recorder.snapshot()?.accumulatedListenedMs)
    }

    @Test
    fun seekWhilePlayingCommitsAndReopensExactlyOnce() {
        recorder.startSession(start())
        recorder.onPlaybackStarted("session-1")
        monotonicClock.advance(1_000L)
        assertEquals(
            ListeningSessionCommandResult.APPLIED,
            recorder.onPositionDiscontinuity("session-1")
        )
        // An equivalent duplicate callback at the same monotonic instant has a zero delta.
        recorder.onPositionDiscontinuity("session-1")
        monotonicClock.advance(500L)
        recorder.onPlaybackSuspended("session-1")

        assertEquals(1_500L, recorder.snapshot()?.accumulatedListenedMs)
    }

    @Test
    fun seekWhilePausedAddsNoTimeAndMediaPositionCannotCauseQualification() {
        recorder.startSession(start(durationMs = 60_000L))
        monotonicClock.advance(999_999L)

        assertEquals(
            ListeningSessionCommandResult.DUPLICATE,
            recorder.onPositionDiscontinuity("session-1")
        )
        val snapshot = requireNotNull(recorder.snapshot())
        assertEquals(0L, snapshot.accumulatedListenedMs)
        assertFalse(snapshot.qualifiedAsPlay)
    }

    @Test
    fun backwardSeekPreservesTimeAndReplayedPortionsMayExceedDuration() {
        recorder.startSession(start(durationMs = 1_000L))
        recorder.onPlaybackStarted("session-1")
        monotonicClock.advance(800L)
        recorder.onPositionDiscontinuity("session-1") // direction/position deliberately not supplied
        monotonicClock.advance(800L)
        recorder.onPlaybackSuspended("session-1")

        val snapshot = requireNotNull(recorder.snapshot())
        assertEquals(1_600L, snapshot.accumulatedListenedMs)
        assertTrue(snapshot.qualifiedAsPlay)
    }

    @Test
    fun recorderQualificationHonorsBelowExactAndAboveThreshold() {
        fun resultAt(elapsedMs: Long, sessionId: String): Boolean {
            recorder.startSession(start(sessionId = sessionId, durationMs = 60_000L))
            recorder.onPlaybackStarted(sessionId)
            monotonicClock.advance(elapsedMs)
            recorder.onPlaybackSuspended(sessionId)
            val qualified = requireNotNull(recorder.snapshot()).qualifiedAsPlay
            recorder.finalizeSession(sessionId, ListeningEndReason.STOPPED)
            return qualified
        }

        assertFalse(resultAt(29_999L, "below"))
        assertTrue(resultAt(30_000L, "exact"))
        assertTrue(resultAt(30_001L, "above"))
    }

    @Test
    fun invalidDurationsDoNotTimeQualifyButNaturalEndAlwaysDoes() {
        listOf<Long?>(null, 0L, -10L).forEachIndexed { index, duration ->
            val sessionId = "invalid-$index"
            recorder.startSession(start(sessionId = sessionId, durationMs = duration))
            recorder.onPlaybackStarted(sessionId)
            monotonicClock.advance(500_000L)
            val stopped = finalized(recorder.finalizeSession(sessionId, ListeningEndReason.STOPPED))
            assertFalse(stopped.qualifiedAsPlay)
            assertEquals(ListeningQualificationReason.NONE, stopped.qualificationReason)

            val naturalId = "natural-$index"
            recorder.startSession(start(sessionId = naturalId, durationMs = duration))
            val natural = finalized(
                recorder.finalizeSession(naturalId, ListeningEndReason.NATURAL_END)
            )
            assertTrue(natural.qualifiedAsPlay)
            assertEquals(ListeningQualificationReason.NATURAL_END, natural.qualificationReason)
        }
    }

    @Test
    fun naturalCompletionBeforeThresholdQualifiesAndUpgradesTimeReason() {
        recorder.startSession(start(sessionId = "early", durationMs = 60_000L))
        recorder.onPlaybackStarted("early")
        monotonicClock.advance(1_000L)
        val early = finalized(recorder.finalizeSession("early", ListeningEndReason.NATURAL_END))
        assertTrue(early.qualifiedAsPlay)
        assertEquals(ListeningQualificationReason.NATURAL_END, early.qualificationReason)

        recorder.startSession(start(sessionId = "upgrade", durationMs = 2_000L))
        recorder.onPlaybackStarted("upgrade")
        monotonicClock.advance(1_000L)
        assertEquals(
            ListeningQualificationReason.TIME_THRESHOLD,
            recorder.snapshot()?.qualificationReason
        )
        val upgraded = finalized(
            recorder.finalizeSession("upgrade", ListeningEndReason.NATURAL_END)
        )
        assertEquals(ListeningQualificationReason.NATURAL_END, upgraded.qualificationReason)
    }

    @Test
    fun completionClassificationTracksNaturalEndIndependentlyFromQualification() {
        recorder.startSession(start(sessionId = "natural", durationMs = 60_000L))
        val natural = finalized(recorder.finalizeSession("natural", ListeningEndReason.NATURAL_END))
        assertEquals(ListeningCompletionClassification.NATIVE_NATURAL, natural.completionClassification)
        assertTrue(natural.qualifiedAsPlay)

        recorder.startSession(start(sessionId = "stopped", durationMs = 2_000L))
        recorder.onPlaybackStarted("stopped")
        monotonicClock.advance(1_000L)
        val stopped = finalized(recorder.finalizeSession("stopped", ListeningEndReason.STOPPED))
        assertEquals(ListeningCompletionClassification.NONE, stopped.completionClassification)
        assertTrue(stopped.qualifiedAsPlay)
    }

    @Test
    fun thresholdQualificationIsStickyThroughPauseSeekStopAndError() {
        recorder.startSession(start(sessionId = "stop", durationMs = 2_000L))
        recorder.onPlaybackStarted("stop")
        monotonicClock.advance(1_000L)
        recorder.onPlaybackSuspended("stop")
        recorder.onPositionDiscontinuity("stop")
        val stopped = finalized(recorder.finalizeSession("stop", ListeningEndReason.STOPPED))
        assertTrue(stopped.qualifiedAsPlay)
        assertEquals(ListeningQualificationReason.TIME_THRESHOLD, stopped.qualificationReason)

        recorder.startSession(start(sessionId = "error", durationMs = 2_000L))
        recorder.onPlaybackStarted("error")
        monotonicClock.advance(1_000L)
        val errored = finalized(recorder.finalizeSession("error", ListeningEndReason.ERROR))
        assertTrue(errored.qualifiedAsPlay)
        assertEquals(ListeningQualificationReason.TIME_THRESHOLD, errored.qualificationReason)
    }

    @Test
    fun stoppedAndErroredBeforeThresholdDoNotQualify() {
        listOf(ListeningEndReason.STOPPED, ListeningEndReason.ERROR).forEachIndexed { index, reason ->
            val id = "short-$index"
            recorder.startSession(start(sessionId = id, durationMs = 60_000L))
            recorder.onPlaybackStarted(id)
            monotonicClock.advance(29_999L)
            val draft = finalized(recorder.finalizeSession(id, reason))
            assertFalse(draft.qualifiedAsPlay)
            assertEquals(ListeningQualificationReason.NONE, draft.qualificationReason)
        }
    }

    @Test
    fun finalizationClosesActiveSegmentOnceAndDuplicateEmitsNoEvent() {
        recorder.startSession(start())
        recorder.onPlaybackStarted("session-1")
        monotonicClock.advance(4_000L)
        val first = finalized(recorder.finalizeSession("session-1", ListeningEndReason.TRANSITION))
        assertEquals(4_000L, first.listenedMs)
        assertEquals(
            FinalizeListeningSessionResult.AlreadyFinalized,
            recorder.finalizeSession("session-1", ListeningEndReason.TRANSITION)
        )
        assertEquals(1, uuidNumber)
        assertNull(recorder.snapshot())
        assertEquals(
            StartListeningSessionResult.AlreadyFinalized,
            recorder.startSession(start())
        )
        assertNull(recorder.snapshot())
    }

    @Test
    fun identicalStartIsIdempotentButConflictsAreRejected() {
        val original = start()
        recorder.startSession(original)
        recorder.onPlaybackStarted("session-1")
        monotonicClock.advance(500L)

        val identical = recorder.startSession(original)
        assertTrue(identical is StartListeningSessionResult.AlreadyActive)
        assertEquals(500L, (identical as StartListeningSessionResult.AlreadyActive).snapshot.accumulatedListenedMs)

        val conflict = recorder.startSession(original.copy(trackDurationMs = 123L))
        assertEquals(
            StartListeningSessionRejection.CONFLICTING_SAME_SESSION,
            (conflict as StartListeningSessionResult.Rejected).reason
        )
        val different = recorder.startSession(start(sessionId = "session-2"))
        assertEquals(
            StartListeningSessionRejection.DIFFERENT_SESSION_ACTIVE,
            (different as StartListeningSessionResult.Rejected).reason
        )
        assertEquals("session-1", recorder.snapshot()?.playbackSessionId)
    }

    @Test
    fun finalizingWithoutActiveSessionCreatesNothing() {
        assertEquals(
            FinalizeListeningSessionResult.NoActiveSession,
            recorder.finalizeSession("missing", ListeningEndReason.UNKNOWN)
        )
        assertEquals(0, uuidNumber)
    }

    @Test
    fun newSessionCanStartAfterFinalizationAndOldCallbacksCannotMutateIt() {
        recorder.startSession(start(sessionId = "old"))
        recorder.finalizeSession("old", ListeningEndReason.TRANSITION)
        recorder.startSession(start(sessionId = "new"))

        assertEquals(ListeningSessionCommandResult.SESSION_MISMATCH, recorder.onPlaybackStarted("old"))
        assertEquals(ListeningSessionCommandResult.SESSION_MISMATCH, recorder.onPlaybackSuspended("old"))
        assertEquals(
            ListeningSessionCommandResult.SESSION_MISMATCH,
            recorder.onPositionDiscontinuity("old")
        )
        val staleFinalize = recorder.finalizeSession("old", ListeningEndReason.STOPPED)
        assertTrue(staleFinalize is FinalizeListeningSessionResult.SessionMismatch)
        assertEquals(0L, recorder.snapshot()?.accumulatedListenedMs)
        assertEquals("new", recorder.snapshot()?.playbackSessionId)
    }

    @Test
    fun commandsWithoutActiveSessionAreExplicitlyIgnored() {
        assertEquals(ListeningSessionCommandResult.NO_ACTIVE_SESSION, recorder.onPlaybackStarted("none"))
        assertEquals(ListeningSessionCommandResult.NO_ACTIVE_SESSION, recorder.onPlaybackSuspended("none"))
        assertEquals(
            ListeningSessionCommandResult.NO_ACTIVE_SESSION,
            recorder.onPositionDiscontinuity("none")
        )
    }

    @Test
    fun wallClockJumpsDoNotAffectListeningAndBackwardEndIsClamped() {
        recorder.startSession(start())
        recorder.onPlaybackStarted("session-1")
        monotonicClock.advance(2_500L)
        wallClock.now = -50_000L

        val draft = finalized(recorder.finalizeSession("session-1", ListeningEndReason.STOPPED))
        assertEquals(2_500L, draft.listenedMs)
        assertEquals(10_000L, draft.startedAt)
        assertEquals(10_000L, draft.endedAt)
        assertEquals(10_000L, draft.createdAt)
    }

    @Test
    fun backwardMonotonicReadIsClampedUntilClockPassesHighWaterMark() {
        monotonicClock.now = 1_000L
        recorder.startSession(start())
        recorder.onPlaybackStarted("session-1")
        monotonicClock.now = 2_000L
        assertEquals(1_000L, recorder.snapshot()?.accumulatedListenedMs)

        monotonicClock.now = 1_500L
        assertEquals(1_000L, recorder.snapshot()?.accumulatedListenedMs)
        recorder.onPositionDiscontinuity("session-1")
        monotonicClock.now = 2_500L
        recorder.onPlaybackSuspended("session-1")
        assertEquals(1_500L, recorder.snapshot()?.accumulatedListenedMs)
    }

    @Test
    fun finalizedDraftPreservesNativeFieldsAndConvertsToRoomEntity() {
        wallClock.now = 50_000L
        recorder.startSession(
            ListeningSessionStart(
                playbackSessionId = "final-fields",
                trackIdentityId = 42L,
                localTrackBindingId = null,
                trackDurationMs = 7_000L
            )
        )
        recorder.onPlaybackStarted("final-fields")
        monotonicClock.advance(3_500L)
        wallClock.now = 60_000L

        val draft = finalized(
            recorder.finalizeSession("final-fields", ListeningEndReason.TRANSITION)
        )
        assertEquals("event-1", draft.eventUuid)
        assertEquals(ListeningSource.CDPLAYA, draft.source)
        assertEquals(42L, draft.trackIdentityId)
        assertNull(draft.localTrackBindingId)
        assertEquals("final-fields", draft.playbackSessionId)
        assertEquals(50_000L, draft.startedAt)
        assertEquals(60_000L, draft.endedAt)
        assertEquals(50_000L, draft.attributionAt)
        assertEquals(ListeningTimestampEvidence.NATIVE_EXACT, draft.timestampEvidence)
        assertEquals(3_500L, draft.listenedMs)
        assertEquals(7_000L, draft.trackDurationMs)
        assertTrue(draft.qualifiedAsPlay)
        assertEquals(ListeningQualificationReason.TIME_THRESHOLD, draft.qualificationReason)
        assertEquals(1, draft.qualificationRuleVersion)
        assertEquals(ListeningQualificationPolicy.CDPLAYA, draft.qualificationPolicy)
        assertEquals(ListeningEndReason.TRANSITION, draft.endReason)
        assertEquals(ListeningCompletionClassification.NONE, draft.completionClassification)
        assertEquals(ListeningEventPublicationState.NATIVE, draft.publicationState)
        assertNull(draft.sourceEventKey)
        assertNull(draft.importBatchId)
        assertEquals(60_000L, draft.createdAt)

        val entity = draft.toEntity()
        assertEquals(draft.eventUuid, entity.eventUuid)
        assertEquals(draft.playbackSessionId, entity.playbackSessionId)
        assertEquals(draft.listenedMs, entity.listenedMs)
        assertEquals(draft.qualificationReason, entity.qualificationReason)
    }

    @Test
    fun nullableBindingAndNonNullBindingAreBothPreserved() {
        recorder.startSession(start(sessionId = "bound", bindingId = 99L))
        val bound = finalized(recorder.finalizeSession("bound", ListeningEndReason.STOPPED))
        assertEquals(99L, bound.localTrackBindingId)

        recorder.startSession(start(sessionId = "unbound", bindingId = null))
        val unbound = finalized(recorder.finalizeSession("unbound", ListeningEndReason.STOPPED))
        assertNull(unbound.localTrackBindingId)
    }

    private fun start(
        sessionId: String = "session-1",
        durationMs: Long? = 60_000L,
        bindingId: Long? = 2L
    ) = ListeningSessionStart(
        playbackSessionId = sessionId,
        trackIdentityId = 1L,
        localTrackBindingId = bindingId,
        trackDurationMs = durationMs
    )

    private fun finalized(result: FinalizeListeningSessionResult): FinalizedListeningEventDraft {
        assertTrue("Expected finalized result but was $result", result is FinalizeListeningSessionResult.Finalized)
        return (result as FinalizeListeningSessionResult.Finalized).draft
    }

    private class FakeMonotonicClock(var now: Long = 0L) : MonotonicClock {
        override fun elapsedRealtimeMs(): Long = now

        fun advance(deltaMs: Long) {
            now += deltaMs
        }
    }

    private class FakeWallClock(var now: Long) : WallClock {
        override fun currentTimeMillis(): Long = now
    }
}
