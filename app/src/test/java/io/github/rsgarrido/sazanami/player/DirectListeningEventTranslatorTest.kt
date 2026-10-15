package io.github.rsgarrido.sazanami.player

import io.github.rsgarrido.sazanami.data.NativeListeningTrack
import io.github.rsgarrido.sazanami.data.SongReference
import io.github.rsgarrido.sazanami.data.listening.FinalizedListeningEventDraft
import io.github.rsgarrido.sazanami.data.listening.ListeningSessionRecorder
import io.github.rsgarrido.sazanami.data.local.ListeningEndReason
import io.github.rsgarrido.sazanami.data.local.ListeningQualificationReason
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DirectListeningEventTranslatorTest {
    @Test fun naturalRepeatOfSameOccurrenceCreatesDistinctRecorderAttemptsWithoutDuplicateFinalization() = runBlocking {
        val fixture=Fixture()
        val translator=DirectListeningEventTranslator().apply { activate() }
        for(generation in 1L..2L) {
            val start=(generation-1)*10_000L
            fixture.consume(translator.observe("item",state(IntegratedDirectPhase.PLAYING,generation)),start)
            val end=state(IntegratedDirectPhase.ENDED,generation)
            fixture.consume(translator.observe("item",end,true),start+1_000)
            assertTrue(translator.observe("item",end,true).isEmpty())
        }
        assertEquals(listOf(ListeningEndReason.NATURAL_END,ListeningEndReason.NATURAL_END),fixture.drafts.map { it.endReason })
        assertEquals(2,fixture.drafts.map { it.playbackSessionId }.distinct().size)
        assertTrue(fixture.drafts.all { it.qualifiedAsPlay && it.qualificationReason==ListeningQualificationReason.NATURAL_END })
        assertEquals(listOf(1_000L,1_000L),fixture.drafts.map { it.listenedMs })
    }
    @Test fun manualReplacementFinalizesOnlyAfterJoinedStopAndAdmitsSuccessorOnce() = runBlocking {
        val fixture = Fixture()
        val translator = DirectListeningEventTranslator().apply { activate() }
        fixture.consume(translator.observe("item", state(IntegratedDirectPhase.PLAYING)), 0)
        fixture.consume(translator.observe("item", state(IntegratedDirectPhase.STOPPING)), 10_000)
        assertTrue(fixture.drafts.isEmpty())
        assertTrue(translator.observe("item", state(IntegratedDirectPhase.PLAYING, 2)).isEmpty())
        val terminal = state(IntegratedDirectPhase.STOPPED).copy(stoppedForReplacement = true)
        val events = translator.observe("item", terminal, true)
        assertEquals(listOf(DirectListeningEvent.TRANSITION), events)
        fixture.consume(events, 20_000)
        assertEquals(ListeningEndReason.TRANSITION, fixture.drafts.single().endReason)
        assertEquals(10_000L, fixture.drafts.single().listenedMs)
        assertTrue(translator.observe("item", terminal, true).isEmpty())
        assertTrue(translator.observe("item", state(IntegratedDirectPhase.PAUSED, 2)).isEmpty())
        fixture.consume(translator.observe("item", state(IntegratedDirectPhase.PLAYING, 2)), 25_000)
        assertTrue(translator.observe("item", state(IntegratedDirectPhase.ERROR), true).isEmpty())
        fixture.consume(translator.observe("item", state(IntegratedDirectPhase.STOPPING, 2)), 30_000)
        fixture.consume(translator.observe("item", state(IntegratedDirectPhase.STOPPED, 2), true), 35_000)
        assertEquals(listOf(ListeningEndReason.TRANSITION, ListeningEndReason.STOPPED), fixture.drafts.map { it.endReason })
        assertEquals(listOf(10_000L, 5_000L), fixture.drafts.map { it.listenedMs })
        assertEquals(2, fixture.drafts.map { it.playbackSessionId }.distinct().size)
    }

    @Test fun replacementIntentCannotRelabelConfirmedNaturalEndOrNativeFailure() {
        for ((phase, event) in mapOf(IntegratedDirectPhase.ENDED to DirectListeningEvent.NATURAL_END,
            IntegratedDirectPhase.ERROR to DirectListeningEvent.ERROR)) {
            val translator = DirectListeningEventTranslator().apply { activate() }
            translator.observe("item", state(IntegratedDirectPhase.PLAYING))
            assertEquals(listOf(DirectListeningEvent.SUSPENDED, event), translator.observe("item",
                state(phase).copy(stoppedForReplacement = true), true))
            assertTrue(translator.observe("item", state(IntegratedDirectPhase.STOPPED).copy(stoppedForReplacement = true), true).isEmpty())
        }
    }

    @Test fun selectionPreparationAndUnownedOrMissingIdentityStartNothing() {
        val translator = DirectListeningEventTranslator()
        assertTrue(translator.observe("item", state(IntegratedDirectPhase.PLAYING)).isEmpty())
        translator.activate()
        assertTrue(translator.observe(null, state(IntegratedDirectPhase.PLAYING)).isEmpty())
        assertTrue(translator.observe("item", state(IntegratedDirectPhase.IDLE)).isEmpty())
        assertTrue(translator.observe("item", state(IntegratedDirectPhase.PREPARING)).isEmpty())
        assertTrue(translator.observe("item", state(IntegratedDirectPhase.RESUMING)).isEmpty())
        assertEquals(listOf(DirectListeningEvent.PLAYING),
            translator.observe("item", state(IntegratedDirectPhase.PLAYING)))
        assertTrue(translator.observe("item", state(IntegratedDirectPhase.PLAYING)).isEmpty())
    }

    @Test fun provisionalIdleWaitsForEachConfirmedOutcomeAndFinalizesOnce() {
        val outcomes = mapOf(
            IntegratedDirectPhase.ENDED to DirectListeningEvent.NATURAL_END,
            IntegratedDirectPhase.STOPPED to DirectListeningEvent.STOPPED,
            IntegratedDirectPhase.ERROR to DirectListeningEvent.ERROR
        )
        outcomes.forEach { (phase, event) ->
            val translator = DirectListeningEventTranslator().apply { activate() }
            translator.observe("item", state(IntegratedDirectPhase.PLAYING))
            assertEquals(listOf(DirectListeningEvent.SUSPENDED),
                translator.observe("item", state(IntegratedDirectPhase.STOPPING)))
            assertTrue(translator.observe("item", state(IntegratedDirectPhase.STOPPING)).isEmpty())
            assertEquals(listOf(event), translator.observe("item", state(phase), terminalConfirmed = true))
            assertTrue(translator.observe("item", state(phase), terminalConfirmed = true).isEmpty())
            assertTrue(translator.observe("item", state(IntegratedDirectPhase.PLAYING)).isEmpty())
            assertTrue(translator.observe("item", state(IntegratedDirectPhase.STOPPED), true).isEmpty())
        }
    }

    @Test fun provisionalErrorSuspendsButOnlyConfirmedErrorFinalizes() {
        val translator = DirectListeningEventTranslator().apply { activate() }
        translator.observe("item", state(IntegratedDirectPhase.PLAYING))
        assertEquals(listOf(DirectListeningEvent.SUSPENDED),
            translator.observe("item", state(IntegratedDirectPhase.ERROR)))
        assertEquals(listOf(DirectListeningEvent.ERROR),
            translator.observe("item", state(IntegratedDirectPhase.ERROR), true))
    }

    @Test fun oldGenerationWrongItemAndDetachedBackendCannotSendGlobalTerminals() {
        val translator = DirectListeningEventTranslator().apply { activate() }
        translator.observe("old", state(IntegratedDirectPhase.PLAYING, generation = 1))
        translator.observe("old", state(IntegratedDirectPhase.STOPPED, generation = 1), true)
        assertEquals(listOf(DirectListeningEvent.PLAYING),
            translator.observe("new", state(IntegratedDirectPhase.PLAYING, generation = 2)))
        assertTrue(translator.observe("old", state(IntegratedDirectPhase.ERROR, generation = 1), true).isEmpty())
        assertTrue(translator.observe("old", state(IntegratedDirectPhase.STOPPED, generation = 2), true).isEmpty())
        assertTrue(translator.observe("newer", state(IntegratedDirectPhase.PLAYING, generation = 3)).isEmpty())
        translator.deactivate()
        assertTrue(translator.observe("new", state(IntegratedDirectPhase.ERROR, generation = 2), true).isEmpty())
    }

    @Test fun acceptedSeekConfirmationsAreForwardedOnceIncludingWhilePaused() {
        val translator = DirectListeningEventTranslator().apply { activate() }
        translator.observe("item", state(IntegratedDirectPhase.PLAYING))
        assertEquals(listOf(DirectListeningEvent.SUSPENDED),
            translator.observe("item", seek(IntegratedDirectPhase.SEEKING, confirmed = 0, pending = 2)))
        // A superseded native confirmation does not yet publish the selected playhead.
        assertTrue(translator.observe("item", seek(IntegratedDirectPhase.SEEKING, 1, 2)).isEmpty())
        val resumed = seek(IntegratedDirectPhase.PLAYING, 2, 2)
        assertEquals(listOf(DirectListeningEvent.DISCONTINUITY, DirectListeningEvent.PLAYING),
            translator.observe("item", resumed))
        assertTrue(translator.observe("item", resumed).isEmpty())
        translator.observe("item", state(IntegratedDirectPhase.PAUSED))
        assertEquals(listOf(DirectListeningEvent.DISCONTINUITY),
            translator.observe("item", seek(IntegratedDirectPhase.PAUSED, 3, 3)))
    }

    @Test fun androidAndDirectPauseSeekSequencesUseTheSameRecorderOutcomes() = runBlocking {
        val android = Fixture()
        val direct = Fixture()
        val translator = DirectListeningEventTranslator().apply { activate() }
        suspend fun directAt(phase: IntegratedDirectPhase, at: Long, serial: Long = 0, terminal: Boolean = false) {
            direct.consume(translator.observe("item", seek(phase, serial, serial), terminal), at)
        }
        android.coordinator.onIsPlayingChanged(android.evidence, true, android.at(0))
        directAt(IntegratedDirectPhase.PLAYING, 0)
        android.coordinator.onIsPlayingChanged(android.evidence, false, android.at(2_000))
        directAt(IntegratedDirectPhase.PAUSING, 2_000)
        directAt(IntegratedDirectPhase.PAUSED, 3_000)
        android.coordinator.onPositionDiscontinuity(android.evidence, android.at(4_000))
        directAt(IntegratedDirectPhase.PAUSED, 4_000, serial = 1)
        directAt(IntegratedDirectPhase.RESUMING, 6_000)
        android.coordinator.onIsPlayingChanged(android.evidence, true, android.at(7_000))
        directAt(IntegratedDirectPhase.PLAYING, 7_000, serial = 1)
        android.coordinator.onIsPlayingChanged(android.evidence, false, android.at(9_000))
        directAt(IntegratedDirectPhase.SEEKING, 9_000, serial = 1)
        android.coordinator.onPositionDiscontinuity(android.evidence, android.at(10_000))
        android.coordinator.onIsPlayingChanged(android.evidence, true, android.at(10_000))
        directAt(IntegratedDirectPhase.PLAYING, 10_000, serial = 2)
        android.coordinator.onIsPlayingChanged(android.evidence, false, android.at(11_000))
        directAt(IntegratedDirectPhase.STOPPING, 11_000, serial = 2)
        assertTrue(direct.drafts.isEmpty())
        android.coordinator.onStopped(android.at(20_000))
        directAt(IntegratedDirectPhase.STOPPED, 20_000, serial = 2, terminal = true)
        assertEquals(android.drafts.single(), direct.drafts.single())
        assertEquals(5_000L, direct.drafts.single().listenedMs)
        assertFalse(direct.drafts.single().qualifiedAsPlay)
    }

    @Test fun existingFloorThresholdAndNaturalCompletionApplyAfterTranslation() = runBlocking {
        suspend fun finalizeAfter(listened: Long, phase: IntegratedDirectPhase, duration: Long): Fixture {
            val fixture = Fixture(duration)
            val translator = DirectListeningEventTranslator().apply { activate() }
            fixture.consume(translator.observe("item", state(IntegratedDirectPhase.PLAYING)), 0)
            fixture.consume(translator.observe("item", state(IntegratedDirectPhase.STOPPING)), listened)
            assertTrue(fixture.drafts.isEmpty())
            fixture.consume(translator.observe("item", state(phase), true), listened + 1_000)
            return fixture
        }
        assertTrue(finalizeAfter(4_999, IntegratedDirectPhase.STOPPED, 60_000).drafts.isEmpty())
        val stopped = finalizeAfter(5_000, IntegratedDirectPhase.STOPPED, 60_000).drafts.single()
        assertEquals(ListeningEndReason.STOPPED, stopped.endReason)
        assertFalse(stopped.qualifiedAsPlay)
        val error = finalizeAfter(30_000, IntegratedDirectPhase.ERROR, 60_000).drafts.single()
        assertEquals(ListeningEndReason.ERROR, error.endReason)
        assertEquals(ListeningQualificationReason.TIME_THRESHOLD, error.qualificationReason)
        val natural = finalizeAfter(100, IntegratedDirectPhase.ENDED, 60_000).drafts.single()
        assertEquals(ListeningEndReason.NATURAL_END, natural.endReason)
        assertEquals(ListeningQualificationReason.NATURAL_END, natural.qualificationReason)
        assertEquals(100L, natural.listenedMs)
    }

    @Test fun destructionUsesExistingStoppedFinalizationAfterSuspension() = runBlocking {
        val fixture = Fixture()
        val translator = DirectListeningEventTranslator().apply { activate() }
        fixture.consume(translator.observe("item", state(IntegratedDirectPhase.PLAYING)), 0)
        fixture.consume(translator.observe("item", state(IntegratedDirectPhase.STOPPING)), 5_000)
        translator.deactivate()
        fixture.coordinator.onServiceDestroyed(fixture.at(9_000))
        fixture.coordinator.onServiceDestroyed(fixture.at(10_000))
        assertEquals(5_000L, fixture.drafts.single().listenedMs)
        assertEquals(ListeningEndReason.STOPPED, fixture.drafts.single().endReason)
    }

    @Test fun backendHandoffAndLateDirectTerminalLeaveRestoredAndroidAttemptIntact() = runBlocking {
        val fixture = Fixture()
        fixture.coordinator.onIsPlayingChanged(fixture.evidence, true, fixture.at(0))
        // Same stop ingress used when the service binding takes ownership after Android release.
        fixture.coordinator.onStopped(fixture.at(5_000))
        val translator = DirectListeningEventTranslator().apply { activate() }
        fixture.consume(translator.observe("item", state(IntegratedDirectPhase.PLAYING)), 6_000)
        fixture.consume(translator.observe("item", state(IntegratedDirectPhase.ENDED), true), 7_000)
        translator.deactivate()
        fixture.coordinator.onIsPlayingChanged(fixture.evidence, true, fixture.at(10_000))
        fixture.consume(translator.observe("item", state(IntegratedDirectPhase.ERROR), true), 11_000)
        fixture.coordinator.onStopped(fixture.at(15_000))
        assertEquals(listOf(ListeningEndReason.STOPPED, ListeningEndReason.NATURAL_END, ListeningEndReason.STOPPED),
            fixture.drafts.map { it.endReason })
        assertEquals(listOf(5_000L, 1_000L, 5_000L), fixture.drafts.map { it.listenedMs })
        assertEquals(3, fixture.drafts.map { it.playbackSessionId }.distinct().size)
    }

    private fun state(phase: IntegratedDirectPhase, generation: Long = 1) =
        IntegratedDirectPlaybackState(generation = generation, phase = phase)

    private fun seek(phase: IntegratedDirectPhase, confirmed: Long, pending: Long) =
        state(phase).copy(pendingSeekSerial = pending,
            nativePauseCounters = mapOf("confirmedSeekSerial" to confirmed))

    private class Fixture(duration: Long = 60_000) {
        private val clock = PlaybackCallbackClock()
        private var session = 0
        private var event = 0
        val drafts = mutableListOf<FinalizedListeningEventDraft>()
        val evidence = ListeningMediaItemEvidence("item", "local:original", SongReference(
            duration = duration, title = "Original", artist = "Artist", album = "Album"))
        val coordinator = PlaybackListeningCoordinator(
            recorder = ListeningSessionRecorder(clock, clock) { "event-${++event}" },
            callbackClock = clock,
            trackResolution = NativeListeningTrackResolution { NativeListeningTrack(12, 34) },
            sessionIdGenerator = PlaybackSessionIdGenerator { "session-${++session}" },
            onFinalized = { drafts += it }
        )

        fun at(time: Long) = PlaybackCallbackTimestamp(time, 100_000 + time)

        suspend fun consume(events: List<DirectListeningEvent>, time: Long) {
            events.forEach { event ->
                when (event) {
                    DirectListeningEvent.PLAYING -> coordinator.onIsPlayingChanged(evidence, true, at(time))
                    DirectListeningEvent.SUSPENDED -> coordinator.onIsPlayingChanged(evidence, false, at(time))
                    DirectListeningEvent.DISCONTINUITY -> coordinator.onPositionDiscontinuity(evidence, at(time))
                    DirectListeningEvent.NATURAL_END -> coordinator.onNaturalEnd(evidence, at(time))
                    DirectListeningEvent.TRANSITION -> coordinator.onMediaItemTransition(
                        evidence, ListeningMediaTransitionReason.SEEK, false, at(time))
                    DirectListeningEvent.STOPPED -> coordinator.onStopped(at(time))
                    DirectListeningEvent.ERROR -> coordinator.onError(at(time))
                }
            }
        }
    }
}
