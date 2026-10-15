package io.github.rsgarrido.sazanami.player

import android.net.Uri
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.rsgarrido.sazanami.data.local.PersistedQueueRepeatMode
import io.github.rsgarrido.sazanami.player.usb.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real phone-controller commands, Room ownership, Media3 forwarding and acknowledged native runs. */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class DirectLibrarySelectionIntegrationTest : DirectQueueTestFixture() {
    @Test fun libraryTapWhilePlayingAndPausedStartsCompleteContextAfterCleanup() {
        for(playing in listOf(true,false)) check(playing) { f -> f.withController { controller ->
            val old=f.startAt()
            controller.playSelectedSong(b,listOf(a,b,a))
            old.stopped.await()
            assertEquals(1,f.engine.runs.size)
            assertEquals(f.originalId,f.repository.getActiveQueueId())
            old.terminal.complete(terminal(old))
            val selected=f.engine.next()
            assertEquals(b.uri,selected.uri);assertTrue(selected.initial.play)
            assertFalse(f.route.directQueueCheckpointAllowed)
            assertEquals(f.originalId,f.repository.getActiveQueueId())
            f.confirm(selected);assertTrue(withTimeout(5000) { f.contextResults.receive() })
            val snapshot=checkNotNull(f.coordinator.captureActiveQueueSnapshot())
            assertEquals(listOf("2","1","1"),snapshot.entries.map { it.evidence.reference.mediaStoreId.toString() })
            assertEquals(3,snapshot.entries.map { it.entryId }.distinct().size)
            assertEquals(snapshot.entries.first().entryId,snapshot.currentEntryId)
            assertEquals(listOf("1","2","1"),snapshot.baseEntryIds.map { id ->
                snapshot.entries.single { it.entryId==id }.evidence.reference.mediaStoreId.toString()
            })
            assertTrue(snapshot.shouldPlay)
            f.forwarding { f.route.isPlaying && f.route.currentMediaItem?.mediaId=="2" }
            assertNotEquals(f.originalId,f.repository.getActiveQueueId())
            assertEquals(listOf("a-first","b","a-second"),f.repository.loadQueue(f.originalId)?.entries?.map { it.entryId })
            assertEquals(if(playing) 1 else 0,f.events.count { it.second==DirectListeningEvent.TRANSITION })
            assertEquals(1,f.engine.maximumActive);assertEquals(0,f.recreations)
        } }
    }

    @Test fun publicMusicPlayerPreservesSuppliedIndexPositionAndCanonicalDuplicateOrder()=check { f -> f.withController {
        val old=f.startAt()
        f.coordinator.onDirectRepeat(Player.REPEAT_MODE_ONE)
        f.coordinator.onDirectShuffle(true)
        val music=MusicPlayer(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            music.playSong(b,startPosition=2500,playlist=listOf(a,b,a),canonicalBaseSongs=listOf(a,a,b))
            old.stopped.await();old.terminal.complete(terminal(old))
            val selected=f.engine.next()
            assertEquals(b.uri,selected.uri);assertEquals(120000L,selected.initial.targetFrame)
            assertFalse(selected.initial.play)
            assertFalse(f.route.directQueueCheckpointAllowed)
            f.confirm(selected);assertTrue(withTimeout(5000) { f.contextResults.receive() })
            val snapshot=checkNotNull(f.coordinator.captureActiveQueueSnapshot())
            val ids=snapshot.entries.map { it.entryId }
            assertEquals(listOf(ids[0],ids[2],ids[1]),snapshot.baseEntryIds)
            assertEquals(ids[1],snapshot.currentEntryId);assertEquals(2500L,snapshot.currentPositionMs)
            assertTrue(snapshot.shuffleEnabled);assertEquals(PersistedQueueRepeatMode.ONE,snapshot.repeatMode)
            assertTrue(snapshot.shouldPlay);assertEquals(1,f.engine.maximumActive)
        } finally { music.release() }
    } }

    @Test fun libraryTapAfterPausingAnActualAttemptFinalizesTransitionOnce()=check { f -> f.withController { controller ->
        val old=f.startAt()
        controller.pausePlayback();old.emit(UsbFullTransportPhase.PAUSED)
        f.forwarding { f.route.playbackState==Player.STATE_READY && !f.route.playWhenReady }
        controller.playSelectedSong(b,listOf(b,c))
        old.stopped.await();old.terminal.complete(terminal(old))
        val selected=f.engine.next();assertTrue(selected.initial.play);f.confirm(selected)
        assertTrue(withTimeout(5000) { f.contextResults.receive() })
        assertEquals(listOf(DirectListeningEvent.PLAYING,DirectListeningEvent.SUSPENDED,
            DirectListeningEvent.TRANSITION,DirectListeningEvent.PLAYING),f.events.map { it.second })
        assertEquals(1,f.engine.maximumActive);assertEquals(0,f.recreations)
    } }

    @Test fun successiveLibraryContextsPreserveBothSavedDirectQueuesAndFrozenAndroid()=check { f -> f.withController { controller ->
        val first=f.startAt()
        controller.playSelectedSong(b,listOf(a,b,a))
        first.stopped.await();first.terminal.complete(terminal(first))
        val second=f.engine.next();f.confirm(second)
        assertTrue(withTimeout(5000) { f.contextResults.receive() })
        val secondId=checkNotNull(f.coordinator.getActiveQueueId())
        val secondEntries=checkNotNull(f.repository.loadQueue(secondId)).entries.map { it.entryId }
        second.emit(UsbFullTransportPhase.PLAYING,96000)
        controller.playSelectedSong(c,listOf(b,c))
        second.stopped.await();second.terminal.complete(terminal(second))
        val third=f.engine.next();f.confirm(third)
        assertTrue(withTimeout(5000) { f.contextResults.receive() })
        val thirdId=checkNotNull(f.coordinator.getActiveQueueId())
        assertEquals(3,setOf(f.originalId,secondId,thirdId).size)
        assertEquals(secondEntries,f.repository.loadQueue(secondId)?.entries?.map { it.entryId })
        assertEquals(2000L,f.repository.loadQueue(secondId)?.queue?.currentPositionMs)
        val thirdEntries=checkNotNull(f.repository.loadQueue(thirdId)).entries
        assertTrue(secondEntries.none { id -> thirdEntries.any { it.entryId==id } })
        f.route.selectDirect(false);third.stopped.await();third.terminal.complete(terminal(third))
        f.settled();f.assertRestoredAndroid()
        assertEquals(3,f.repository.listQueues().size)
        assertEquals(secondEntries,f.repository.loadQueue(secondId)?.entries?.map { it.entryId })
    } }

    @Test fun rapidLibrarySelectionSupersedesPreparationWithoutFalseHistoryOrCheckpoint()=check { f -> f.withController { controller ->
        val first=f.startAt()
        controller.playSelectedSong(b,listOf(a,b))
        first.stopped.await();first.terminal.complete(terminal(first))
        val preparing=f.engine.next()
        controller.playSelectedSong(c,listOf(c,b))
        preparing.stopped.await();preparing.terminal.complete(terminal(preparing))
        val latest=f.engine.next();assertEquals(c.uri,latest.uri)
        preparing.emit(UsbFullTransportPhase.PLAYING)
        f.confirm(latest)
        val results=List(2) { withTimeout(5000) { f.contextResults.receive() } }
        assertEquals(1,results.count { it })
        assertEquals("3",f.route.queuePlayer.currentMediaItem?.mediaId)
        assertEquals(2,f.repository.listQueues().size)
        assertEquals(1,f.events.count { it.second==DirectListeningEvent.TRANSITION })
        assertEquals(2,f.events.count { it.second==DirectListeningEvent.PLAYING })
        assertEquals(1,f.engine.maximumActive);assertEquals(0,f.recreations)
    } }

    @Test fun newerLibraryIntentDuringRoomResolutionPreventsObsoleteNativeAdmission()=check { f -> f.withController { controller ->
        val first=f.startAt()
        val resolving=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        f.beforeIdentify={ resolving.complete(Unit);release.await() }
        controller.playSelectedSong(b,listOf(b,a));resolving.await()
        controller.playSelectedSong(c,listOf(c,a))
        release.complete(Unit)
        first.stopped.await();first.terminal.complete(terminal(first))
        val latest=f.engine.next();assertEquals(c.uri,latest.uri);f.confirm(latest)
        val results=List(2) { withTimeout(5000) { f.contextResults.receive() } }
        assertEquals(1,results.count { it });assertEquals(2,f.engine.runs.size)
        assertEquals(2,f.repository.listQueues().size)
    } }

    @Test fun controllerKeepsNativeUiEvidenceWhileNewLibraryContextIsResolving()=check { f -> f.withController { controller ->
        val first=f.startAt();first.emit(UsbFullTransportPhase.PLAYING,96000)
        withTimeout(5000) { controller.progressState.first { it.currentPosition==2000 } }
        val resolving=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        f.beforeIdentify={ resolving.complete(Unit);release.await() }
        controller.playSelectedSong(b,listOf(b,c));resolving.await()
        assertEquals(a,controller.uiState.value.currentSong)
        assertTrue(controller.uiState.value.isPlaying)
        assertEquals(2000,controller.progressState.value.currentPosition)
        assertEquals(f.originalId,f.repository.getActiveQueueId())
        release.complete(Unit);first.stopped.await();first.terminal.complete(terminal(first))
        val selected=f.engine.next();f.confirm(selected)
        assertTrue(withTimeout(5000) { f.contextResults.receive() })
        f.forwarding { f.route.isPlaying && f.route.currentMediaItem?.mediaId=="2" }
        withTimeout(5000) { controller.uiState.first { it.currentSong==b } }
        assertEquals(b,controller.uiState.value.currentSong)
    } }

    @Test fun ordinaryStopDuringLibraryResolutionCannotBeUndoneByLateAdmission()=check { f -> f.withController { controller ->
        val old=f.startAt()
        val resolving=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        f.beforeIdentify={ resolving.complete(Unit);release.await() }
        controller.playSelectedSong(b,listOf(b,c));resolving.await()
        f.route.stop();old.stopped.await();old.terminal.complete(terminal(old))
        withTimeout(5000) { IntegratedDirectPlaybackBridge.state.first { it.phase==IntegratedDirectPhase.STOPPED } }
        release.complete(Unit)
        assertFalse(withTimeout(5000) { f.contextResults.receive() })
        assertEquals(1,f.engine.runs.size);assertEquals(0,f.engine.active)
        assertFalse(f.route.isPlaying);assertEquals(f.originalId,f.repository.getActiveQueueId())
        assertEquals(1,f.repository.listQueues().size)
        assertEquals(1,f.events.count { it.second==DirectListeningEvent.STOPPED })
    } }

    @Test fun newerPauseDuringLibraryResolutionOverridesEarlierImmediatePlayIntent()=check { f -> f.withController { controller ->
        val old=f.startAt()
        val resolving=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        f.beforeIdentify={ resolving.complete(Unit);release.await() }
        controller.playSelectedSong(b,listOf(b,c));resolving.await()
        controller.pausePlayback();old.emit(UsbFullTransportPhase.PAUSED)
        release.complete(Unit);old.stopped.await();old.terminal.complete(terminal(old))
        val selected=f.engine.next();assertFalse(selected.initial.play);f.confirm(selected)
        assertTrue(withTimeout(5000) { f.contextResults.receive() })
        f.forwarding { f.route.playbackState==Player.STATE_READY && !f.route.playWhenReady }
        assertEquals(b.uri,selected.uri);assertFalse(f.route.isPlaying)
        assertFalse(checkNotNull(f.coordinator.captureActiveQueueSnapshot()).shouldPlay)
        assertEquals(1,f.events.count { it.second==DirectListeningEvent.PLAYING })
        assertEquals(1,f.events.count { it.second==DirectListeningEvent.TRANSITION })
    } }

    @Test fun librarySelectionSupersedesPendingNextAndSavedActivation() {
        for(savedActivation in listOf(false,true)) check { f -> f.withController { controller ->
            val first=f.startAt()
            val saved=if(savedActivation) f.saved(position=0) else null
            val activation=saved?.let { f.scope.async { f.coordinator.switchToQueue(it.queue.queueId) } }
            if(!savedActivation) f.route.seekToNextMediaItem()
            first.stopped.await()
            val obsoleteRequest=f.route.directState.sourceTransition?.requestId
            controller.playSelectedSong(c,listOf(c,a))
            // Library dispatch still has Room work ahead of Stage A. Keep cleanup pending until
            // the new source request actually owns replacement, rather than racing that I/O.
            val intended=withTimeout(5000) { IntegratedDirectPlaybackBridge.state.first { state ->
                state.sourceTransition?.let { it.requestId!=obsoleteRequest && it.mediaId==c.id.toString() &&
                    it.status==DirectSourceTransitionStatus.WAITING_FOR_CLEANUP }==true
            } }.sourceTransition!!
            assertEquals(1,f.engine.runs.size)
            assertFalse(f.route.directQueueCheckpointAllowed)
            assertEquals(f.originalId,f.repository.getActiveQueueId())
            assertNull(f.coordinator.captureActiveQueueSnapshot())
            first.terminal.complete(terminal(first))
            val latest=f.engine.next();assertEquals(c.uri,latest.uri);assertTrue(latest.initial.play)
            val generation=f.route.directState.generation
            assertEquals(intended.requestId,f.route.directState.sourceTransition?.requestId)
            assertFalse(f.route.directQueueCheckpointAllowed)
            assertTrue(f.contextResults.tryReceive().isFailure)
            first.emit(UsbFullTransportPhase.PLAYING)
            assertEquals(generation,f.route.directState.generation)
            f.confirm(latest)
            assertTrue(withTimeout(5000) { f.contextResults.receive() })
            if(activation!=null) assertFalse(activation.await())
            assertTrue(f.route.directQueueCheckpointAllowed)
            assertEquals(DirectSourceTransitionStatus.READY,f.route.directState.sourceTransition?.status)
            assertEquals(c.id,f.route.directState.libraryId)
            val accepted=checkNotNull(f.coordinator.captureActiveQueueSnapshot())
            assertEquals(listOf(3L,1L),accepted.entries.map { it.evidence.reference.mediaStoreId })
            assertEquals(intended.itemInstanceId,accepted.currentEntryId)
            assertEquals(2,accepted.entries.map { it.entryId }.distinct().size)
            assertTrue(accepted.shouldPlay)
            val queueId=checkNotNull(f.repository.getActiveQueueId())
            assertNotEquals(f.originalId,queueId)
            assertEquals(queueId,f.coordinator.getActiveQueueId())
            assertEquals(queueId,PlaybackQueueRuntimeBridge.getActiveQueueId())
            assertEquals(accepted.currentEntryId,f.repository.loadQueue(queueId)?.queue?.currentEntryId)
            f.accepted(checkNotNull(accepted.currentEntryId))
            first.emit(UsbFullTransportPhase.PLAYING)
            assertEquals(generation,f.route.directState.generation)
            assertEquals(intended.itemInstanceId,f.route.currentMediaItem?.listeningEvidence()?.itemInstanceId)
            assertEquals(queueId,f.repository.getActiveQueueId())
            assertEquals(listOf("a-first","b","a-second"),f.repository.loadQueue(f.originalId)?.entries?.map { it.entryId })
            if(saved!=null) assertEquals(saved.entries,f.repository.loadQueue(saved.queue.queueId)?.entries)
            assertEquals(2,f.engine.runs.size);assertEquals(1,f.engine.maximumActive);assertEquals(0,f.recreations)
            f.route.selectDirect(false);latest.stopped.await();latest.terminal.complete(terminal(latest))
            f.settled();f.assertRestoredAndroid()
        } }
    }

    @Test fun librarySelectionSupersedesAutomaticPreparationAfterConfirmedEof()=check { f -> f.withController { controller ->
        val first=f.startAt();val generation=f.route.directState.generation
        first.terminal.complete(naturalResult(first))
        val automatic=f.engine.next()
        controller.playSelectedSong(c,listOf(c,a))
        automatic.stopped.await();automatic.terminal.complete(terminal(automatic))
        val selected=f.engine.next();f.confirm(selected)
        assertTrue(withTimeout(5000) { f.contextResults.receive() })
        f.coordinator.onDirectNavigation(DIRECT_NATURAL_ADVANCE,generation)
        assertEquals(c.uri,selected.uri);assertEquals(3,f.engine.runs.size)
        assertEquals(1,f.events.count { it.second==DirectListeningEvent.NATURAL_END })
        assertEquals(0,f.events.count { it.second==DirectListeningEvent.TRANSITION })
        assertEquals(2,f.events.count { it.second==DirectListeningEvent.PLAYING })
        assertEquals(1,f.engine.maximumActive)
    } }

    @Test fun libraryTapDuringOutputSwitchIsRejectedWithoutStartingAndroidPlayback()=check { f -> f.withController { controller ->
        val first=f.startAt();f.route.selectDirect(false);first.stopped.await()
        controller.playSelectedSong(c,listOf(c,a))
        assertTrue(f.contextResults.tryReceive().isFailure)
        first.terminal.complete(terminal(first));f.settled();f.assertRestoredAndroid()
        assertEquals(1,f.engine.runs.size);assertFalse(f.route.playWhenReady)
        assertEquals(1,f.repository.listQueues().size)
    } }

    @Test fun outputSwitchDuringLibraryPreparationCancelsUnacknowledgedContext()=check { f -> f.withController { controller ->
        val old=f.startAt()
        controller.playSelectedSong(b,listOf(b,c))
        old.stopped.await();old.terminal.complete(terminal(old))
        val preparing=f.engine.next()
        f.route.selectDirect(false);preparing.stopped.await()
        assertEquals(0,f.recreations)
        preparing.terminal.complete(terminal(preparing))
        assertFalse(withTimeout(5000) { f.contextResults.receive() })
        f.settled();f.assertRestoredAndroid()
        preparing.emit(UsbFullTransportPhase.PLAYING)
        assertEquals(1,f.repository.listQueues().size);assertEquals(2,f.engine.runs.size)
        assertEquals(1,f.engine.maximumActive);assertFalse(f.route.isPlaying)
        assertEquals(1,f.events.count { it.second==DirectListeningEvent.PLAYING })
    } }

    @Test fun unsupportedLibrarySourcesFailWithoutFallbackSkipOrAcceptedQueue() {
        for(extension in listOf("wav","mp3","m4a")) check { f -> f.withController { controller ->
            val unsupported=c.copy(uri=Uri.parse("content://test/source.$extension"))
            PlaybackLibraryBridge.updateSongs(listOf(a,b,unsupported))
            controller.setLibrarySongs(listOf(a,b,unsupported))
            val first=f.startAt();f.engine.startupFailure="UNSUPPORTED_TRACK"
            controller.playSelectedSong(unsupported,listOf(unsupported,b))
            first.stopped.await();first.terminal.complete(terminal(first))
            assertFalse(withTimeout(5000) { f.contextResults.receive() })
            f.forwarding { f.route.playerError!=null }
            assertEquals("UNSUPPORTED_TRACK",f.route.directState.error)
            assertFalse(f.route.isPlaying);assertTrue(f.route.isDirectSelected)
            assertEquals(1,f.engine.runs.size);assertEquals(0,f.engine.active)
            assertEquals(0,f.recreations);assertEquals(1,f.repository.listQueues().size)
            assertEquals("a-first",f.repository.loadQueue(f.originalId)?.queue?.currentEntryId)
            assertEquals(1,f.events.count { it.second==DirectListeningEvent.TRANSITION })
            assertFalse(f.events.any { it.second==DirectListeningEvent.ERROR })
        } }
    }

    @Test fun unsafeCleanupAndLostAuthorizationRejectLibraryAdmission() {
        for(unsafeCleanup in listOf(true,false)) check { f -> f.withController { controller ->
            val first=f.startAt()
            if(unsafeCleanup) {
                first.terminal.complete(terminal(first,safe=false,status=UsbSilentStatus.FAILED))
                f.forwarding { f.route.playerError!=null }
            } else f.attachmentFailure="DIRECT_USB_PERMISSION_FAILED"
            controller.playSelectedSong(c,listOf(c,a))
            f.forwarding { !f.route.isCommandAvailable(Player.COMMAND_PLAY_PAUSE) }
            assertFalse(f.route.queuePlayer.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))
            f.route.play()
            assertTrue(f.contextResults.tryReceive().isFailure)
            assertEquals(1,f.engine.runs.size);assertEquals(1,f.repository.listQueues().size)
            assertTrue(f.route.isDirectSelected);assertEquals(0,f.recreations)
        } }
    }

    @Test fun controllerLibraryTapCarriesCurrentDirectShuffleAndRepeatPolicies()=check { f -> f.withController { controller ->
        val old=f.startAt(repeat=Player.REPEAT_MODE_ONE)
        f.coordinator.onDirectShuffle(true)
        controller.playSelectedSong(b,listOf(a,b,c,a))
        old.stopped.await();old.terminal.complete(terminal(old))
        val selected=f.engine.next();f.confirm(selected)
        assertTrue(withTimeout(5000) { f.contextResults.receive() })
        val snapshot=checkNotNull(f.coordinator.captureActiveQueueSnapshot())
        assertTrue(snapshot.shuffleEnabled);assertEquals(PersistedQueueRepeatMode.ONE,snapshot.repeatMode)
        assertEquals(listOf(1L,1L,2L,3L),snapshot.entries.map { it.evidence.reference.mediaStoreId!! }.sorted())
        assertEquals(listOf(1L,2L,3L,1L),snapshot.baseEntryIds.map { id ->
            snapshot.entries.single { it.entryId==id }.evidence.reference.mediaStoreId
        })
        assertEquals(b.uri,selected.uri);assertTrue(snapshot.shouldPlay)
    } }

    @Test fun libraryNativeFailureBeforeAndAfterReadinessKeepsTruthfulHistoryAndLastAcceptedQueue() {
        for(ready in listOf(false,true)) check { f -> f.withController { controller ->
            val old=f.startAt()
            controller.playSelectedSong(b,listOf(b,c))
            old.stopped.await();old.terminal.complete(terminal(old))
            val selected=f.engine.next()
            if(ready) {
                f.confirm(selected);assertTrue(withTimeout(5000) { f.contextResults.receive() })
            }
            val acceptedId=f.repository.getActiveQueueId()
            selected.terminal.complete(terminal(selected,status=UsbSilentStatus.FAILED))
            if(!ready) assertFalse(withTimeout(5000) { f.contextResults.receive() })
            f.forwarding { f.route.playerError!=null }
            assertFalse(f.route.isPlaying);assertTrue(f.route.isDirectSelected)
            assertEquals(acceptedId,f.repository.getActiveQueueId())
            assertEquals(if(ready) 2 else 1,f.repository.listQueues().size)
            assertEquals(if(ready) 1 else 0,f.events.count { it.second==DirectListeningEvent.ERROR })
            assertEquals(1,f.events.count { it.second==DirectListeningEvent.TRANSITION })
            assertEquals(2,f.engine.runs.size);assertEquals(0,f.engine.active)
            assertEquals(0,f.recreations);assertEquals(1,f.engine.maximumActive)
        } }
    }
}
