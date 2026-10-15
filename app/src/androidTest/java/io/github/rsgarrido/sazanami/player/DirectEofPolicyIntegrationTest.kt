package io.github.rsgarrido.sazanami.player

import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rsgarrido.sazanami.data.local.PersistedQueueRepeatMode
import io.github.rsgarrido.sazanami.player.usb.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class DirectEofPolicyIntegrationTest : DirectQueueTestFixture() {
    @Test fun confirmedNaturalEndAdvancesAndFinalizesBeforeSuccessorAttempt()=check { f ->
        val first=f.startAt();val generation=f.route.directState.generation
        first.terminal.complete(naturalResult(first))
        val next=f.engine.next();assertEquals(b.uri,next.uri)
        assertEquals(DirectListeningEvent.NATURAL_END,f.events.last().second)
        f.confirm(next);f.accepted("b")
        assertEquals(listOf(DirectListeningEvent.PLAYING,DirectListeningEvent.SUSPENDED,
            DirectListeningEvent.NATURAL_END,DirectListeningEvent.PLAYING),f.events.map { it.second })
        assertEquals("b",f.repository.loadQueue(f.originalId)?.queue?.currentEntryId)
        f.coordinator.onDirectNavigation(DIRECT_NATURAL_ADVANCE,generation)
        f.coordinator.onDirectNavigation(DIRECT_NATURAL_ADVANCE,generation)
        first.emit(UsbFullTransportPhase.PLAYING)
        assertEquals(2,f.engine.runs.size);assertEquals(1,f.engine.maximumActive)
        assertEquals(0,f.recreations);assertTrue(f.route.isDirectSelected)
    }

    @Test fun finalRepeatOffItemEndsNormallyAndCheckpointsWithoutAnotherAttempt()=check { f ->
        val last=f.startAt(2)
        last.terminal.complete(naturalResult(last))
        f.accepted("a-second")
        f.forwarding { f.route.playbackState==Player.STATE_ENDED }
        f.coordinator.persistActiveQueueSnapshot()
        assertEquals(1,f.engine.runs.size);assertEquals(0,f.recreations)
        assertTrue(f.route.isDirectSelected);assertFalse(f.route.playWhenReady)
        assertEquals("a-second",f.repository.loadQueue(f.originalId)?.queue?.currentEntryId)
        assertEquals(10_000L,f.repository.loadQueue(f.originalId)?.queue?.currentPositionMs)
        assertEquals(1,f.events.count { it.second==DirectListeningEvent.NATURAL_END })
        assertEquals(1,f.events.count { it.second==DirectListeningEvent.PLAYING })
    }

    @Test fun repeatOneCreatesFreshRunsForSameOccurrenceAndManualNextEscapes()=check { f ->
        val first=f.startAt(repeat=Player.REPEAT_MODE_ONE)
        val originalGeneration=f.route.directState.generation
        first.terminal.complete(naturalResult(first))
        val replay=f.engine.next();assertEquals(a.uri,replay.uri)
        assertTrue(f.route.directState.generation>originalGeneration)
        assertEquals(0L,replay.initial.seeks);assertTrue(replay.initial.play)
        f.confirm(replay);f.accepted("a-first")
        f.coordinator.onDirectNavigation(DIRECT_NATURAL_ADVANCE,originalGeneration)
        assertEquals(2,f.engine.runs.size)
        val next=f.navigate(replay);f.accepted("b")
        assertEquals(b.uri,next.uri)
        assertEquals(1,f.events.count { it.second==DirectListeningEvent.NATURAL_END })
        assertEquals(1,f.events.count { it.second==DirectListeningEvent.TRANSITION })
        assertEquals(3,f.events.count { it.second==DirectListeningEvent.PLAYING })
        assertEquals(1,f.engine.maximumActive)
    }

    @Test fun repeatAllWrapsWithoutChangingOccurrenceOrder()=check { f ->
        val last=f.startAt(2,Player.REPEAT_MODE_ALL)
        val before=f.runtime.captureSnapshot()!!.entries.map { it.entryId }
        last.terminal.complete(naturalResult(last))
        val wrapped=f.engine.next();f.confirm(wrapped);f.accepted("a-first")
        assertEquals(a.uri,wrapped.uri)
        assertEquals(before,f.runtime.captureSnapshot()?.entries?.map { it.entryId })
        assertEquals(PersistedQueueRepeatMode.ALL,f.repository.loadQueue(f.originalId)?.queue?.repeatMode)
    }

    @Test fun repeatChangeImmediatelyBeforeEofAffectsSuccessorWithoutRestartingCurrentRun()=check { f ->
        val last=f.startAt(2)
        f.coordinator.onDirectRepeat(Player.REPEAT_MODE_ONE)
        assertFalse(last.control.stopped);assertEquals(1,f.engine.runs.size)
        assertEquals(PersistedQueueRepeatMode.ONE,f.repository.loadQueue(f.originalId)?.queue?.repeatMode)
        last.terminal.complete(naturalResult(last))
        val replay=f.engine.next();f.confirm(replay);f.accepted("a-second")
        assertEquals(2,f.engine.runs.size)
        f.coordinator.onDirectRepeat(Player.REPEAT_MODE_OFF)
        replay.terminal.complete(naturalResult(replay))
        f.forwarding { f.route.playbackState==Player.STATE_ENDED }
        assertEquals(2,f.engine.runs.size)
        assertEquals(2,f.events.count { it.second==DirectListeningEvent.NATURAL_END })
    }

    @Test fun naturalEofDuringPendingExactSelectionCannotAdvanceAgain()=check { f ->
        val first=f.startAt();val generation=f.route.directState.generation
        val manual=f.scope.async { f.coordinator.playEntry(f.originalId,"a-second") }
        first.stopped.await();first.terminal.complete(naturalResult(first))
        val intended=f.engine.next();f.confirm(intended);assertTrue(manual.await());f.accepted("a-second")
        f.coordinator.onDirectNavigation(DIRECT_NATURAL_ADVANCE,generation)
        assertEquals(2,f.engine.runs.size);assertEquals(a.uri,intended.uri)
        assertEquals(1,f.events.count { it.second==DirectListeningEvent.NATURAL_END })
        assertFalse(f.events.any { it.second==DirectListeningEvent.TRANSITION })
    }

    @Test fun savedActivationWinsWhenOutgoingRunFinishesNaturally()=check { f ->
        val first=f.startAt();val saved=f.saved(position=0)
        val activation=f.scope.async { f.coordinator.switchToQueue(saved.queue.queueId) }
        first.stopped.await();first.terminal.complete(naturalResult(first))
        val target=f.engine.next();f.confirm(target);assertTrue(activation.await())
        f.accepted(saved.queue.currentEntryId!!)
        assertEquals(2,f.engine.runs.size);assertEquals(saved.queue.queueId,f.repository.getActiveQueueId())
        assertEquals(1,f.events.count { it.second==DirectListeningEvent.NATURAL_END })
    }

    @Test fun eofDuringSuspendedSavedResolutionCannotOverrideSuccessfulOrFailedUserIntent() {
        for(unavailable in listOf(false,true)) check { f ->
            val first=f.startAt();val saved=f.saved(position=0)
            val resolving=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
            f.resolutionUnavailable=unavailable
            f.beforeResolve={ resolving.complete(Unit);release.await() }
            val activation=f.scope.async { f.coordinator.switchToQueue(saved.queue.queueId) }
            resolving.await()
            first.terminal.complete(naturalResult(first))
            f.forwarding { f.route.playbackState==Player.STATE_ENDED }
            release.complete(Unit)
            if(unavailable) {
                assertFalse(activation.await())
                assertEquals(1,f.engine.runs.size);assertEquals(f.originalId,f.repository.getActiveQueueId())
                assertEquals(IntegratedDirectPhase.ENDED,f.route.directState.phase)
            } else {
                val selected=f.engine.next();f.confirm(selected);assertTrue(activation.await())
                f.accepted(saved.queue.currentEntryId!!);assertEquals(2,f.engine.runs.size)
            }
            assertTrue(f.route.isDirectSelected);assertEquals(0,f.recreations)
            assertEquals(1,f.events.count { it.second==DirectListeningEvent.NATURAL_END })
        }
    }

    @Test fun manualSelectionDuringAutomaticPreparationSupersedesUnstartedAttempt()=check { f ->
        val first=f.startAt();first.terminal.complete(naturalResult(first))
        val automatic=f.engine.next()
        val manual=f.scope.async { f.coordinator.playEntry(f.originalId,"a-second") }
        automatic.stopped.await();automatic.terminal.complete(terminal(automatic))
        val selected=f.engine.next();f.confirm(selected);assertTrue(manual.await());f.accepted("a-second")
        assertEquals(a.uri,selected.uri);assertEquals(1,f.engine.maximumActive)
        assertEquals(2,f.events.count { it.second==DirectListeningEvent.PLAYING })
        assertEquals(1,f.events.count { it.second==DirectListeningEvent.NATURAL_END })
        assertFalse(f.events.any { it.second==DirectListeningEvent.TRANSITION })
    }

    @Test fun earlyEofBeforeReadyAdvancesWithoutFabricatedHistory()=check { f ->
        f.route.restoreDirectIdleTimeline(f.items,0,0,false,Player.REPEAT_MODE_OFF)
        f.route.prepare();val short=f.engine.next()
        short.terminal.complete(naturalResult(short))
        val next=f.engine.next();assertEquals(b.uri,next.uri);assertTrue(f.events.isEmpty())
        f.confirm(next);f.accepted("b")
        assertEquals(listOf("b" to DirectListeningEvent.PLAYING),f.events)
    }

    @Test fun unsupportedAutomaticSuccessorStopsWithoutSkippingOrAndroidFallback()=check { f ->
        val first=f.startAt()
        f.engine.startupFailure="UNSUPPORTED_TRACK"
        first.terminal.complete(naturalResult(first))
        withTimeout(5000) { while(f.route.directState.phase!=IntegratedDirectPhase.ERROR) yield() }
        assertEquals("UNSUPPORTED_TRACK",f.route.directState.error)
        assertEquals("b",f.route.queuePlayer.currentMediaItem?.listeningEvidence()?.itemInstanceId)
        assertEquals(3,f.route.queuePlayer.mediaItemCount);assertTrue(f.route.isDirectSelected)
        assertEquals(1,f.engine.runs.size);assertEquals(0,f.recreations)
        f.coordinator.persistActiveQueueSnapshot()
        assertEquals(listOf("a-first","b","a-second"),f.repository.loadQueue(f.originalId)?.entries?.map { it.entryId })
        assertFalse(f.events.any { it.second==DirectListeningEvent.ERROR }) // Unsupported preparation was never audible.
        assertEquals(1,f.events.count { it.second==DirectListeningEvent.NATURAL_END })
    }

    @Test fun outputSwitchDuringAutomaticPreparationWaitsForCleanupAndRestoresAndroid()=check { f ->
        val first=f.startAt();first.terminal.complete(naturalResult(first))
        val automatic=f.engine.next();f.route.selectDirect(false)
        automatic.stopped.await();assertEquals(0,f.recreations)
        automatic.terminal.complete(terminal(automatic));f.settled();f.assertRestoredAndroid()
        assertEquals(1,f.engine.maximumActive);assertEquals(1,f.recreations)
    }

    @Test fun eofCoincidingWithOutputSwitchCannotAdvanceOrRebindHistoryAfterAndroidRestore()=check { f ->
        val first=f.startAt();val generation=f.route.directState.generation
        f.route.selectDirect(false);first.stopped.await()
        first.terminal.complete(naturalResult(first));f.settled();f.assertRestoredAndroid()
        f.coordinator.onDirectNavigation(DIRECT_NATURAL_ADVANCE,generation)
        assertEquals(1,f.engine.runs.size);assertEquals(1,f.recreations)
        assertEquals(1,f.events.count { it.second==DirectListeningEvent.NATURAL_END })
        assertTrue(f.route.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS))
        assertTrue(f.route.isCommandAvailable(Player.COMMAND_SET_REPEAT_MODE))
    }

    @Test fun successorNativeFailureFinalizesErrorAndDoesNotAdvanceToAnotherTrack()=check { f ->
        val first=f.startAt();val firstGeneration=f.route.directState.generation
        first.terminal.complete(naturalResult(first))
        val next=f.engine.next();assertEquals(b.uri,next.uri)
        assertFalse(f.route.directQueueCheckpointAllowed)
        assertEquals("a-first",f.repository.loadQueue(f.originalId)?.queue?.currentEntryId)
        f.confirm(next);f.accepted("b")
        f.forwarding { f.route.playbackState==Player.STATE_READY && f.route.isPlaying }
        assertTrue(f.route.playWhenReady);assertTrue(f.route.directQueueCheckpointAllowed)
        val failedGeneration=f.route.directState.generation
        next.terminal.complete(terminal(next,status=UsbSilentStatus.FAILED))
        withTimeout(5000) { while(f.route.directState.phase!=IntegratedDirectPhase.ERROR) yield() }
        // Native evidence is published before ForwardingSimpleBasePlayer receives onEvents.
        // Await the public error state before asserting the session player's playback state.
        f.forwarding { f.route.playerError!=null }
        assertEquals("DIRECT_TRANSPORT_FAILED",f.route.directState.error)
        assertEquals(UsbSilentStatus.FAILED,f.route.directState.engine?.status)
        assertTrue(f.route.directState.cleanupSafe);assertTrue(f.route.directState.normalReleased)
        assertFalse(f.route.directState.playWhenReady);assertFalse(f.route.directRequestedPlaying)
        assertEquals(Player.STATE_IDLE,f.route.playbackState)
        assertEquals("DIRECT_TRANSPORT_FAILED",f.route.playerError?.message)
        assertTrue(f.route.isDirectSelected);assertFalse(f.route.isPlaying)
        assertFalse(f.route.playWhenReady);assertFalse(f.route.directQueueCheckpointAllowed)
        assertTrue(f.route.canReplaceDirectTimeline()) // USB selection remains owned and safely reusable.

        first.emit(UsbFullTransportPhase.PLAYING)
        f.coordinator.onDirectNavigation(DIRECT_NATURAL_ADVANCE,firstGeneration)
        f.coordinator.onDirectNavigation(DIRECT_NATURAL_ADVANCE,failedGeneration)
        f.coordinator.persistActiveQueueSnapshot()
        assertEquals(IntegratedDirectPhase.ERROR,f.route.directState.phase)
        assertEquals(failedGeneration,f.route.directState.generation)
        assertEquals(2,f.engine.runs.size);assertEquals(0,f.engine.active)
        assertEquals(1,f.engine.maximumActive);assertEquals(0,f.recreations)
        assertEquals("b",f.route.currentMediaItem?.listeningEvidence()?.itemInstanceId)
        assertEquals(1,f.events.count { it.second==DirectListeningEvent.NATURAL_END })
        assertEquals(1,f.events.count { it.second==DirectListeningEvent.ERROR })
        assertEquals(listOf("a-first" to DirectListeningEvent.PLAYING,
            "a-first" to DirectListeningEvent.SUSPENDED,"a-first" to DirectListeningEvent.NATURAL_END,
            "b" to DirectListeningEvent.PLAYING,"b" to DirectListeningEvent.SUSPENDED,
            "b" to DirectListeningEvent.ERROR),f.events)
        assertEquals("b",f.repository.loadQueue(f.originalId)?.queue?.currentEntryId)
    }

    @Test fun detachAndShutdownDuringAutomaticPreparationNeverOverlapRuns() {
        for(detach in listOf(true,false)) check { f ->
            val first=f.startAt();first.terminal.complete(naturalResult(first))
            val preparing=f.engine.next()
            val shutdown=if(detach) {
                f.attachmentFailure="DIRECT_USB_SELECTION_CHANGED";f.route.usbSelectionChanged();null
            } else f.scope.async { f.route.shutdown() }
            preparing.stopped.await();assertEquals(0,f.recreations)
            preparing.terminal.complete(terminal(preparing))
            if(detach) { f.settled();f.assertRestoredAndroid() } else assertTrue(shutdown!!.await())
            assertEquals(1,f.engine.maximumActive)
            assertEquals(1,f.events.count { it.second==DirectListeningEvent.NATURAL_END })
        }
    }
}
