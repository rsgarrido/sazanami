package com.example.cdplaya.player

import com.example.cdplaya.data.NativeListeningTrack
import com.example.cdplaya.data.SongReference
import com.example.cdplaya.data.listening.FinalizedListeningEventDraft
import com.example.cdplaya.data.listening.ListeningEventUuidGenerator
import com.example.cdplaya.data.listening.ListeningSessionRecorder
import com.example.cdplaya.data.local.ListeningEndReason
import com.example.cdplaya.data.local.ListeningQualificationReason
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackListeningCoordinatorTest {
    @Test
    fun preloadStartsNothingAndFirstPlayIsIdempotent() = runBlocking {
        val fixture = fixture()
        assertNull(fixture.recorder.snapshot())

        fixture.coordinator.onIsPlayingChanged(fixture.a, true, at(0))
        fixture.coordinator.onIsPlayingChanged(fixture.a, true, at(100))
        fixture.coordinator.onStopped(at(1_000))

        assertEquals(1, fixture.drafts.size)
        assertEquals(1_000L, fixture.drafts.single().listenedMs)
        assertEquals("session-1", fixture.drafts.single().playbackSessionId)
    }

    @Test
    fun pauseBufferingResumeAndSeeksCountOnlyActiveMonotonicTime() = runBlocking {
        val fixture = fixture()
        fixture.coordinator.onIsPlayingChanged(fixture.a, true, at(0))
        fixture.coordinator.onPositionDiscontinuity(fixture.a, at(1_000))
        fixture.coordinator.onIsPlayingChanged(fixture.a, false, at(2_000))
        fixture.coordinator.onPositionDiscontinuity(fixture.a, at(50_000))
        fixture.coordinator.onIsPlayingChanged(fixture.a, false, at(60_000)) // buffering duplicate
        fixture.coordinator.onIsPlayingChanged(fixture.a, true, at(70_000))
        fixture.coordinator.onPositionDiscontinuity(fixture.a, at(71_000))
        fixture.coordinator.onStopped(at(72_000))

        assertEquals(4_000L, fixture.drafts.single().listenedMs)
    }

    @Test
    fun automaticNextAndFinalEndedEachPersistOneNaturalCompletion() = runBlocking {
        val fixture = fixture()
        fixture.coordinator.onIsPlayingChanged(fixture.a, true, at(0))
        fixture.coordinator.onMediaItemTransition(
            fixture.b, ListeningMediaTransitionReason.AUTOMATIC, true, at(1_000)
        )
        fixture.coordinator.onNaturalEnd(fixture.b, at(2_500))

        assertEquals(listOf(1_000L, 1_500L), fixture.drafts.map { it.listenedMs })
        assertTrue(fixture.drafts.all { it.endReason == ListeningEndReason.NATURAL_END })
        assertTrue(fixture.drafts.all { it.qualificationReason == ListeningQualificationReason.NATURAL_END })
    }

    @Test
    fun directSelectionAndQueueReplacementFinalizeButQueueEditPreservingItemDoesNot() = runBlocking {
        val fixture = fixture()
        fixture.coordinator.onIsPlayingChanged(fixture.a, true, at(0))
        fixture.coordinator.onMediaItemTransition(
            fixture.a, ListeningMediaTransitionReason.PLAYLIST_CHANGED, true, at(500)
        )
        assertEquals(0, fixture.drafts.size)

        fixture.coordinator.onMediaItemTransition(
            fixture.a2, ListeningMediaTransitionReason.PLAYLIST_CHANGED, true, at(1_000)
        )
        fixture.coordinator.onMediaItemTransition(
            fixture.b, ListeningMediaTransitionReason.SEEK, true, at(2_000)
        )
        fixture.coordinator.onStopped(at(3_000))

        assertEquals(3, fixture.drafts.size)
        assertTrue(fixture.drafts.all { it.endReason == ListeningEndReason.TRANSITION || it.endReason == ListeningEndReason.STOPPED })
        assertNotEquals(fixture.drafts[0].playbackSessionId, fixture.drafts[1].playbackSessionId)
    }

    @Test
    fun repeatOneCreatesOneIndependentEventPerLoop() = runBlocking {
        val fixture = fixture()
        fixture.coordinator.onIsPlayingChanged(fixture.a, true, at(0))
        fixture.coordinator.onMediaItemTransition(
            fixture.a, ListeningMediaTransitionReason.REPEAT, true, at(1_000)
        )
        fixture.coordinator.onMediaItemTransition(
            fixture.a, ListeningMediaTransitionReason.REPEAT, true, at(2_000)
        )
        fixture.coordinator.onNaturalEnd(fixture.a, at(3_000))

        assertEquals(3, fixture.drafts.size)
        assertEquals(3, fixture.drafts.map { it.playbackSessionId }.distinct().size)
        assertTrue(fixture.drafts.all { it.endReason == ListeningEndReason.NATURAL_END })
    }

    @Test
    fun errorsStopsAndServiceDestructionFinalizeAtMostOnce() = runBlocking {
        val error = fixture()
        error.coordinator.onIsPlayingChanged(error.a, true, at(0))
        error.coordinator.onError(at(100))
        error.coordinator.onStopped(at(100))
        assertEquals(ListeningEndReason.ERROR, error.drafts.single().endReason)

        val stopped = fixture()
        stopped.coordinator.onIsPlayingChanged(stopped.a, true, at(0))
        stopped.coordinator.onStopped(at(200))
        stopped.coordinator.onServiceDestroyed(at(300))
        assertEquals(ListeningEndReason.STOPPED, stopped.drafts.single().endReason)

        val destroyed = fixture()
        destroyed.coordinator.onIsPlayingChanged(destroyed.a, true, at(0))
        destroyed.coordinator.onServiceDestroyed(at(400))
        assertEquals(400L, destroyed.drafts.single().listenedMs)
    }

    @Test
    fun duplicateAndStaleCallbacksCannotFinalizeNewAttempt() = runBlocking {
        val fixture = fixture()
        fixture.coordinator.onIsPlayingChanged(fixture.a, true, at(0))
        fixture.coordinator.onMediaItemTransition(
            fixture.b, ListeningMediaTransitionReason.AUTOMATIC, true, at(1_000)
        )
        fixture.coordinator.onMediaItemTransition(
            fixture.b, ListeningMediaTransitionReason.AUTOMATIC, true, at(1_000)
        )
        fixture.coordinator.onNaturalEnd(fixture.a, at(1_100)) // stale ended from old session
        fixture.coordinator.onPositionDiscontinuity(fixture.a, at(1_200))
        fixture.coordinator.onStopped(at(2_000))

        assertEquals(2, fixture.drafts.size)
        assertEquals(listOf(1_000L, 1_000L), fixture.drafts.map { it.listenedMs })
    }

    @Test
    fun endedThenTransitionAndDispatchFailureDoNotBreakNextSession() = runBlocking {
        var failFirstDispatch = true
        val fixture = fixture(onDraft = { draft, drafts ->
            if (failFirstDispatch) {
                failFirstDispatch = false
                error("synthetic persistence dispatch failure")
            }
            drafts += draft
        })
        fixture.coordinator.onIsPlayingChanged(fixture.a, true, at(0))
        fixture.coordinator.onNaturalEnd(fixture.a, at(500))
        fixture.coordinator.onMediaItemTransition(
            fixture.b, ListeningMediaTransitionReason.AUTOMATIC, true, at(500)
        )
        fixture.coordinator.onStopped(at(1_500))

        assertEquals(1, fixture.drafts.size)
        assertEquals("session-2", fixture.drafts.single().playbackSessionId)
    }

    @Test
    fun missingMetadataOrResolutionFailureCreatesNoEventAndLaterPlaybackStillWorks() = runBlocking {
        var failA = true
        val fixture = fixture(resolve = { evidence ->
            if (evidence.itemInstanceId == "a" && failA) {
                failA = false
                error("synthetic lookup failure")
            }
            NativeListeningTrack(20L, null)
        })
        fixture.coordinator.onIsPlayingChanged(null, true, at(0))
        fixture.coordinator.onIsPlayingChanged(fixture.a, true, at(100))
        fixture.coordinator.onIsPlayingChanged(fixture.b, true, at(200))
        fixture.coordinator.onStopped(at(1_200))

        assertEquals(1, fixture.drafts.size)
        assertEquals(20L, fixture.drafts.single().trackIdentityId)
        assertNull(fixture.drafts.single().localTrackBindingId)
    }

    @Test
    fun callbackTimestampPreservesInitialPlaybackAcrossAsyncResolutionBoundary() = runBlocking {
        val fixture = fixture()
        fixture.coordinator.onIsPlayingChanged(fixture.a, true, at(10_000, 100_000))
        fixture.coordinator.onIsPlayingChanged(fixture.a, false, at(12_500, 500_000))
        fixture.coordinator.onStopped(at(20_000, 600_000))

        assertEquals(2_500L, fixture.drafts.single().listenedMs)
        assertEquals(100_000L, fixture.drafts.single().startedAt)
    }

    @Test
    fun crossfadeAccountsBothAudibleIntervalsIndependentOfLogicalMidpoint() = runBlocking {
        val fixture = fixture()
        fixture.coordinator.onIsPlayingChanged(fixture.a, true, at(0))
        fixture.coordinator.onAudibleStarted(fixture.b, at(5_000))
        fixture.coordinator.onLogicalHandoff(fixture.b)

        assertTrue(fixture.drafts.isEmpty())

        fixture.coordinator.onAudibleEnded(
            fixture.a,
            ListeningEndReason.NATURAL_END,
            at(10_000)
        )
        fixture.coordinator.onIsPlayingChanged(fixture.b, true, at(11_000))
        fixture.coordinator.onStopped(at(12_000))

        assertEquals(listOf(10_000L, 7_000L), fixture.drafts.map { it.listenedMs })
        assertEquals(
            listOf(ListeningEndReason.NATURAL_END, ListeningEndReason.STOPPED),
            fixture.drafts.map { it.endReason }
        )
        assertEquals(2, fixture.drafts.map { it.playbackSessionId }.distinct().size)
    }

    @Test
    fun crossfadeCancellationBeforeAndAfterMidpointRetainsHeardIntervals() = runBlocking {
        val before = fixture()
        before.coordinator.onIsPlayingChanged(before.a, true, at(0))
        before.coordinator.onAudibleStarted(before.b, at(5_000))
        before.coordinator.onAudibleEnded(
            before.b,
            ListeningEndReason.TRANSITION,
            at(6_500)
        )
        before.coordinator.onLogicalHandoff(before.a)
        before.coordinator.onStopped(at(8_000))
        assertEquals(listOf(1_500L, 8_000L), before.drafts.map { it.listenedMs })

        val after = fixture()
        after.coordinator.onIsPlayingChanged(after.a, true, at(0))
        after.coordinator.onAudibleStarted(after.b, at(5_000))
        after.coordinator.onLogicalHandoff(after.b)
        after.coordinator.onAudibleEnded(
            after.a,
            ListeningEndReason.TRANSITION,
            at(8_000)
        )
        after.coordinator.onStopped(at(10_000))
        assertEquals(listOf(8_000L, 5_000L), after.drafts.map { it.listenedMs })
    }

    @Test
    fun repeatedOverlapsReuseRecordersWithoutLeakingAttempts() = runBlocking {
        val fixture = fixture()
        fixture.coordinator.onIsPlayingChanged(fixture.a, true, at(0))
        fixture.coordinator.onAudibleStarted(fixture.b, at(5_000))
        fixture.coordinator.onLogicalHandoff(fixture.b)
        fixture.coordinator.onAudibleEnded(
            fixture.a,
            ListeningEndReason.NATURAL_END,
            at(10_000)
        )
        fixture.coordinator.onAudibleStarted(fixture.a2, at(15_000))
        fixture.coordinator.onLogicalHandoff(fixture.a2)
        fixture.coordinator.onAudibleEnded(
            fixture.b,
            ListeningEndReason.NATURAL_END,
            at(20_000)
        )
        fixture.coordinator.onStopped(at(25_000))

        assertEquals(3, fixture.drafts.size)
        assertEquals(listOf(10_000L, 15_000L, 10_000L), fixture.drafts.map { it.listenedMs })
        assertEquals(3, fixture.drafts.map { it.playbackSessionId }.distinct().size)
    }

    private fun fixture(
        resolve: suspend (ListeningMediaItemEvidence) -> NativeListeningTrack = {
            NativeListeningTrack(trackIdentityId = kotlin.math.abs(it.referenceKey.hashCode().toLong()) + 1L, localTrackBindingId = 9L)
        },
        onDraft: (FinalizedListeningEventDraft, MutableList<FinalizedListeningEventDraft>) -> Unit = { draft, drafts -> drafts += draft }
    ): Fixture {
        val clock = PlaybackCallbackClock()
        val drafts = mutableListOf<FinalizedListeningEventDraft>()
        var session = 0
        var event = 0
        fun newRecorder() = ListeningSessionRecorder(
            monotonicClock = clock,
            wallClock = clock,
            eventUuidGenerator = ListeningEventUuidGenerator { "event-${++event}" }
        )
        val recorder = newRecorder()
        val coordinator = PlaybackListeningCoordinator(
            recorder = recorder,
            callbackClock = clock,
            trackResolution = NativeListeningTrackResolution(resolve),
            sessionIdGenerator = PlaybackSessionIdGenerator { "session-${++session}" },
            onFinalized = { onDraft(it, drafts) },
            additionalRecorderFactory = { newRecorder() }
        )
        return Fixture(coordinator, recorder, drafts, evidence("a", "local:a"), evidence("a2", "local:a"), evidence("b", "local:b"))
    }

    private fun evidence(instance: String, referenceKey: String) = ListeningMediaItemEvidence(
        itemInstanceId = instance,
        referenceKey = referenceKey,
        reference = SongReference(
            mediaStoreId = kotlin.math.abs(instance.hashCode().toLong()) + 1L,
            volumeName = "external",
            contentUri = "content://media/$instance",
            duration = 60_000L,
            title = "Same title",
            artist = "Same artist",
            album = "Same album",
            portableKeyVersion = 1
        )
    )

    private fun at(monotonic: Long, wall: Long = monotonic + 10_000L) =
        PlaybackCallbackTimestamp(monotonic, wall)

    private data class Fixture(
        val coordinator: PlaybackListeningCoordinator,
        val recorder: ListeningSessionRecorder,
        val drafts: MutableList<FinalizedListeningEventDraft>,
        val a: ListeningMediaItemEvidence,
        val a2: ListeningMediaItemEvidence,
        val b: ListeningMediaItemEvidence
    )
}
