package io.github.rsgarrido.sazanami.player

import android.net.Uri
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rsgarrido.sazanami.data.local.PersistedQueueRepeatMode
import io.github.rsgarrido.sazanami.player.usb.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Stage B regressions retained against the shared production queue path. */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class DirectQueueIntegrationTest : DirectQueueTestFixture() {
    @Test fun outputAdoptsCompleteIdleTimelineWithoutPreparingNativeAudio()=check { f ->
        assertEquals(3,f.route.queuePlayer.mediaItemCount);assertEquals(0,f.engine.runs.size)
        assertEquals(2000L,f.route.queuePlayer.currentPosition);assertFalse(f.route.isPlaying)
        assertEquals(listOf("a-first","b","a-second"),f.runtime.captureSnapshot()?.entries?.map { it.entryId })
        assertEquals(listOf("a-first","a-second","b"),f.coordinator.captureActiveQueueSnapshot()?.baseEntryIds)
        f.forwarding { f.route.mediaItemCount==3 }
        assertEquals(0,f.route.currentMediaItemIndex)
    }

    @Test fun currentIdleOccurrenceIsANoOpWithoutClaimingNativeReadiness()=check { f ->
        val result=f.route.selectDirectQueueOccurrence(0).await()
        assertEquals(DirectSourceTransitionStatus.UNCHANGED,result.status);assertNull(result.nativeGeneration)
        assertTrue(PlaybackQueueRuntimeBridge.playQueueEntry(f.originalId,"a-first"))
        assertEquals(0,f.engine.runs.size);assertTrue(f.events.isEmpty());assertFalse(f.route.isPlaying)
    }

    @Test fun exactDuplicateSelectionWaitsForCleanupAndReadyBeforeCheckpointingAndNoOpDoesNotRestart()=check { f ->
        val old=f.start()
        val result=f.scope.async { PlaybackQueueRuntimeBridge.playQueueEntry(f.originalId,"a-second") }
        old.stopped.await();assertFalse(result.isCompleted)
        assertNull(f.coordinator.captureActiveQueueSnapshot())
        f.coordinator.persistActiveQueueSnapshot()
        assertEquals("a-first",f.repository.loadQueue(f.originalId)?.queue?.currentEntryId)
        old.terminal.complete(terminal(old))
        val next=f.engine.next();assertEquals(a.uri,next.uri)
        assertEquals(2,f.route.queuePlayer.currentMediaItemIndex);assertFalse(result.isCompleted)
        f.confirm(next);assertTrue(result.await())
        assertEquals("a-second",f.repository.loadQueue(f.originalId)?.queue?.currentEntryId)
        assertEquals(1,f.engine.maximumActive)
        assertEquals(1,f.events.count { it.second==DirectListeningEvent.TRANSITION })
        assertEquals("a-second",f.events.last().first)
        val count=f.engine.runs.size
        assertTrue(PlaybackQueueRuntimeBridge.playQueueEntry(f.originalId,"a-second"));assertEquals(count,f.engine.runs.size)
        f.forwarding { f.route.currentMediaItemIndex==2 }
        assertEquals("a-second",f.route.currentMediaItem?.listeningEvidence()?.itemInstanceId)
    }

    @Test fun pausedSelectionCannotSubmitAndLaterPlayUsesTheSelectedOccurrence()=check(playing=false) { f ->
        val old=f.start();val result=f.scope.async { f.coordinator.playEntry(f.originalId,"b") }
        old.stopped.await();old.terminal.complete(terminal(old))
        val next=f.engine.next();assertFalse(next.initial.play)
        try { next.submit(1);fail("Paused queue selection admitted PCM") } catch(_: IllegalStateException) {}
        f.confirm(next);assertTrue(result.await())
        assertFalse(f.events.any { it.second==DirectListeningEvent.PLAYING })
        f.forwarding { f.route.isCommandAvailable(Player.COMMAND_PLAY_PAUSE) }
        f.route.play();assertTrue(next.control.playWhenReady)
        next.submit(1);next.emit(UsbFullTransportPhase.PLAYING,1)
        assertEquals("b",f.events.last().first);assertEquals(b.uri,next.uri)
    }

    @Test fun savedActivationRestoresExactOrderPolicyPositionAndBothInitialIntents() {
        for(playing in listOf(false,true)) check(playing) { f ->
            val old=f.start();val saved=f.saved()
            val activation=f.scope.async { PlaybackQueueRuntimeBridge.switchActiveQueue(saved.queue.queueId) }
            old.stopped.await();assertEquals(f.originalId,f.repository.getActiveQueueId())
            old.terminal.complete(terminal(old));val next=f.engine.next()
            assertFalse(next.initial.play);assertEquals(144000L,next.initial.targetFrame)
            assertFalse(activation.isCompleted);assertEquals(0L,f.route.queuePlayer.currentPosition)
            assertNull(f.coordinator.captureActiveQueueSnapshot())
            f.confirm(next);assertTrue(activation.await())
            val accepted=checkNotNull(f.repository.loadQueue(saved.queue.queueId))
            assertEquals(saved.entries.map { it.entryId },f.runtime.captureSnapshot()?.entries?.map { it.entryId })
            assertEquals(saved.entries.sortedBy { it.baseOrder }.map { it.entryId },f.coordinator.captureActiveQueueSnapshot()?.baseEntryIds)
            assertEquals(saved.queue.currentEntryId,accepted.queue.currentEntryId)
            assertEquals(3000L,accepted.queue.currentPositionMs);assertTrue(accepted.queue.shuffleEnabled)
            assertEquals(PersistedQueueRepeatMode.ALL,accepted.queue.repeatMode)
            assertEquals(playing,next.control.playWhenReady);assertEquals(0,f.recreations)
            f.forwarding { f.route.currentMediaItem?.listeningEvidence()?.itemInstanceId==saved.queue.currentEntryId }
            assertEquals(Player.REPEAT_MODE_ALL,f.route.repeatMode);assertTrue(f.route.shuffleModeEnabled)
        }
    }

    @Test fun rapidSelectionsConvergeOnLastOccurrenceWithoutPreparingTheObsoleteTarget()=check { f ->
        val old=f.start()
        val first=f.scope.async { f.coordinator.playEntry(f.originalId,"b") };old.stopped.await()
        val last=f.scope.async { f.coordinator.playEntry(f.originalId,"a-second") }
        assertFalse(first.await());assertEquals(1,f.engine.runs.size)
        old.terminal.complete(terminal(old));val next=f.engine.next();f.confirm(next)
        assertTrue(last.await());assertEquals(a.uri,next.uri)
        assertEquals("a-second",f.repository.loadQueue(f.originalId)?.queue?.currentEntryId)
        old.emit(UsbFullTransportPhase.PLAYING)
        assertEquals("a-second",f.coordinator.captureActiveQueueSnapshot()?.currentEntryId)
    }

    @Test fun newerSavedActivationSupersedesPendingQueueWithoutCommittingIt()=check { f ->
        val old=f.start();val firstSaved=f.saved(position=1000);val lastSaved=f.saved(position=3000)
        val first=f.scope.async { f.coordinator.switchToQueue(firstSaved.queue.queueId) };old.stopped.await()
        val last=f.scope.async { f.coordinator.switchToQueue(lastSaved.queue.queueId) }
        assertFalse(first.await());assertEquals(f.originalId,f.repository.getActiveQueueId())
        old.terminal.complete(terminal(old));val next=f.engine.next();f.confirm(next)
        assertTrue(last.await());assertEquals(lastSaved.queue.queueId,f.repository.getActiveQueueId())
        assertEquals(2,f.engine.runs.size)
    }

    @Test fun failedTargetAfterAdmissionKeepsHonestErrorAndNeverOverwritesPreviousSavedQueue()=check { f ->
        val old=f.start();val saved=f.saved();f.engine.startupFailure="DIRECT_SOURCE_FAILED"
        val activation=f.scope.async { f.coordinator.switchToQueue(saved.queue.queueId) }
        old.stopped.await();old.terminal.complete(terminal(old));assertFalse(activation.await())
        assertEquals(f.originalId,f.repository.getActiveQueueId());assertEquals("DIRECT_SOURCE_FAILED",f.route.directState.error)
        assertEquals(saved.queue.currentEntryId,f.route.queuePlayer.currentMediaItem?.listeningEvidence()?.itemInstanceId)
        assertNull(f.coordinator.captureActiveQueueSnapshot());assertFalse(f.route.isPlaying)
        f.coordinator.persistActiveQueueSnapshot()
        assertEquals(listOf("a-first","b","a-second"),f.repository.loadQueue(f.originalId)?.entries?.map { it.entryId })
        assertEquals(1,f.engine.runs.size)
        f.engine.startupFailure=null
        val retry=f.scope.async { f.coordinator.switchToQueue(saved.queue.queueId) }
        val next=f.engine.next();f.confirm(next);assertTrue(retry.await())
    }

    @Test fun nonseekableSavedPositionFailsWithoutDiscardingItsCheckpoint()=check { f ->
        val old=f.start();val saved=f.saved();f.engine.seekable=false
        val activation=f.scope.async { f.coordinator.switchToQueue(saved.queue.queueId) }
        old.stopped.await();old.terminal.complete(terminal(old));val failed=f.engine.next()
        assertTrue(failed.control.stopped);assertEquals(0L,failed.frames)
        failed.terminal.complete(terminal(failed,status=UsbSilentStatus.FAILED));assertFalse(activation.await())
        assertEquals(f.originalId,f.repository.getActiveQueueId())
        assertEquals(3000L,f.repository.loadQueue(saved.queue.queueId)?.queue?.currentPositionMs)
        assertEquals("DIRECT_INITIAL_POSITION_UNSUPPORTED",f.route.directState.error)
    }

    @Test fun unsafeOldCleanupCannotActivateOrResetTheLatch()=check { f ->
        val old=f.start();val saved=f.saved()
        val activation=f.scope.async { f.coordinator.switchToQueue(saved.queue.queueId) }
        old.stopped.await();old.terminal.complete(terminal(old,safe=false));assertFalse(activation.await())
        assertFalse(f.route.directState.cleanupSafe);assertEquals(1,f.engine.runs.size)
        assertFalse(f.coordinator.switchToQueue(saved.queue.queueId));assertEquals(f.originalId,f.repository.getActiveQueueId())
    }

    @Test fun outputSwitchDuringActivationCancelsCommitAndJoinsBeforeAndroidRestoration()=check { f ->
        val old=f.start();val saved=f.saved()
        val activation=f.scope.async { f.coordinator.switchToQueue(saved.queue.queueId) };old.stopped.await()
        f.route.selectDirect(false);assertFalse(activation.await());assertEquals(0,f.recreations)
        old.terminal.complete(terminal(old));f.settled()
        assertEquals(1,f.recreations);assertEquals(f.originalId,f.repository.getActiveQueueId())
        assertEquals(2000L,f.route.currentPosition);assertFalse(f.route.playWhenReady)
        assertEquals(listOf("a-first","b","a-second"),f.runtime.captureSnapshot()?.entries?.map { it.entryId })
    }

    @Test fun returningAndroidRestoresOriginalIdentityAndRetainsAcceptedDirectQueueCheckpoint()=check { f ->
        f.hubOwns(f.originalId)
        val old=f.start();val saved=f.saved()
        val activation=f.scope.async { f.coordinator.switchToQueue(saved.queue.queueId) }
        old.stopped.await();old.terminal.complete(terminal(old));val next=f.engine.next();f.confirm(next);assertTrue(activation.await())
        next.frames=48000;next.emit(UsbFullTransportPhase.PLAYING,192000)
        f.route.selectDirect(false);assertEquals(0,f.recreations)
        next.terminal.complete(terminal(next));f.settled()
        assertEquals(f.originalId,f.repository.getActiveQueueId())
        assertEquals(4000L,f.repository.loadQueue(saved.queue.queueId)?.queue?.currentPositionMs)
        assertEquals(2000L,f.route.currentPosition);assertFalse(f.route.playWhenReady)
        assertEquals(listOf(a,a,b),f.baseSongs);assertEquals(1,f.engine.maximumActive)
        f.coordinator.persistActiveQueueSnapshot()
        assertEquals(saved.entries.map { it.entryId },f.repository.loadQueue(saved.queue.queueId)?.entries?.map { it.entryId })
        f.assertRestoredAndroid()
        next.emit(UsbFullTransportPhase.PLAYING,240000)
        f.assertRestoredAndroid()
        assertEquals(4000L,f.repository.loadQueue(saved.queue.queueId)?.queue?.currentPositionMs)
    }

    @Test fun newQueueActivationUsesEstablishedPlayingFirstZeroAndPolicyOffDefaults()=check(playing=false) { f ->
        val old=f.start()
        val creation=f.scope.async { f.coordinator.createAndActivateQueue("New",listOf(b,c,b)) }
        old.stopped.await();old.terminal.complete(terminal(old));val next=f.engine.next()
        assertEquals(b.uri,next.uri);assertTrue(next.initial.play);assertEquals(0L,next.initial.seeks)
        assertFalse(creation.isCompleted);f.confirm(next)
        val created=checkNotNull(creation.await())
        assertEquals(created.entries.first().entryId,created.queue.currentEntryId)
        assertEquals(0L,created.queue.currentPositionMs);assertFalse(created.queue.shuffleEnabled)
        assertEquals(PersistedQueueRepeatMode.OFF,created.queue.repeatMode)
        assertEquals(created.queue.queueId,f.repository.getActiveQueueId())
    }

    @Test fun commandsPermitTransportButKeepExternalExactSelectionAndBroadEditingRestricted()=check { f ->
        assertTrue(f.route.queuePlayer.isCommandAvailable(Player.COMMAND_SEEK_TO_MEDIA_ITEM))
        f.forwarding { f.route.isCommandAvailable(Player.COMMAND_SEEK_TO_MEDIA_ITEM) }
        assertTrue(allowsDirectCommand(Player.COMMAND_SEEK_TO_MEDIA_ITEM,true))
        assertFalse(allowsDirectCommand(Player.COMMAND_SEEK_TO_MEDIA_ITEM,false))
        for(command in listOf(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,Player.COMMAND_SET_REPEAT_MODE,Player.COMMAND_SET_SHUFFLE_MODE))
            assertTrue(f.route.queuePlayer.isCommandAvailable(command))
        assertFalse(f.route.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS))
        assertTrue(allowsDirectCommand(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,false))
        assertFalse(allowsDirectCommand(Player.COMMAND_SET_REPEAT_MODE,false))
        assertFalse(allowsDirectCommand(Player.COMMAND_CHANGE_MEDIA_ITEMS,true))
        assertFalse(f.coordinator.removeEntry(f.originalId,"a-first"))
        assertTrue(f.coordinator.removeEntry(f.originalId,"b"));assertEquals(0,f.engine.runs.size)
    }

    @Test fun coldDirectReconstructionRestoresPositionPausedWithoutCreatingANativeRun()=check { f ->
        val saved=f.saved()
        f.repository.setActiveQueue(saved.queue.queueId)
        f.route.restoreDirectIdleTimeline(emptyList(),0,0,false,Player.REPEAT_MODE_OFF)
        f.coordinator.initialize()
        assertEquals(saved.queue.queueId,f.coordinator.getActiveQueueId())
        assertEquals(saved.queue.currentEntryId,f.runtime.captureSnapshot()?.currentEntryId)
        assertEquals(3000L,f.route.queuePlayer.currentPosition);assertFalse(f.route.directRequestedPlaying)
        assertEquals(0,f.engine.runs.size);assertTrue(f.events.isEmpty())
        val run=f.start();assertFalse(run.initial.play);assertEquals(144000L,run.initial.targetFrame)
        assertFalse(f.route.queuePlayer.isPlaying)
    }

    @Test fun callerCancellationCannotAbandonCleanupOrCommitAnActivation()=check { f ->
        val old=f.start();val saved=f.saved()
        val activation=f.scope.async { f.coordinator.switchToQueue(saved.queue.queueId) };old.stopped.await()
        activation.cancel();activation.join()
        assertEquals(f.originalId,f.repository.getActiveQueueId());assertEquals(1,f.engine.runs.size)
        old.terminal.complete(terminal(old))
        val retry=f.scope.async { f.coordinator.switchToQueue(saved.queue.queueId) }
        val next=f.engine.next();f.confirm(next);assertTrue(retry.await());assertEquals(1,f.engine.maximumActive)
    }

    @Test fun detachDuringSuccessorPreparationCancelsActivationAndNeverStartsAndroidBeforeJoin()=check { f ->
        val old=f.start();val saved=f.saved()
        val activation=f.scope.async { f.coordinator.switchToQueue(saved.queue.queueId) }
        old.stopped.await();old.terminal.complete(terminal(old));val next=f.engine.next()
        f.attachmentFailure="DIRECT_USB_SELECTION_CHANGED";f.route.usbSelectionChanged()
        assertFalse(activation.await());assertEquals(0,f.recreations)
        assertEquals(f.originalId,f.repository.getActiveQueueId())
        next.terminal.complete(terminal(next));f.settled()
        assertEquals(1,f.recreations);assertEquals(1,f.engine.maximumActive)
        assertEquals(f.originalId,f.repository.getActiveQueueId())
    }

    @Test fun shutdownDuringActivationCancelsItsCommitAndWaitsForNativeCleanup()=check { f ->
        val old=f.start();val saved=f.saved()
        val activation=f.scope.async { f.coordinator.switchToQueue(saved.queue.queueId) };old.stopped.await()
        val shutdown=f.scope.async { f.route.shutdown() }
        assertFalse(activation.await());assertFalse(shutdown.isCompleted)
        old.terminal.complete(terminal(old));assertTrue(shutdown.await())
        assertEquals(f.originalId,f.repository.getActiveQueueId());assertEquals(0,f.recreations)
        assertEquals(DirectListeningEvent.STOPPED,f.events.last().second)
    }

    @Test fun editedOriginalSavedQueueCannotBeOverwrittenByAndroidSnapshotRestoration()=check { f ->
        f.hubOwns(f.originalId)
        val old=f.start();val saved=f.saved()
        val activation=f.scope.async { f.coordinator.switchToQueue(saved.queue.queueId) }
        old.stopped.await();old.terminal.complete(terminal(old));val next=f.engine.next();f.confirm(next);assertTrue(activation.await())
        assertTrue(f.coordinator.removeEntry(f.originalId,"b"))
        val edited=checkNotNull(f.repository.loadQueue(f.originalId))
        f.route.selectDirect(false);next.terminal.complete(terminal(next));f.settled()
        assertNull(f.repository.getActiveQueueId())
        f.hubOwns(null);assertFalse(f.hub.state.value.queues.any { it.isActive })
        assertEquals(3,f.route.mediaItemCount);assertEquals(2000L,f.route.currentPosition)
        f.coordinator.persistActiveQueueSnapshot()
        assertEquals(edited.entries,f.repository.loadQueue(f.originalId)?.entries)
        assertEquals(2,f.repository.listQueues().size)
        next.emit(UsbFullTransportPhase.PLAYING,240000)
        f.coordinator.persistActiveQueueSnapshot()
        assertNull(f.coordinator.getActiveQueueId());assertEquals(edited.entries,f.repository.loadQueue(f.originalId)?.entries)
        // Existing successful activation can reconcile the restored, unassigned snapshot safely.
        assertTrue(f.coordinator.switchToQueue(saved.queue.queueId))
        assertEquals(saved.queue.queueId,f.repository.getActiveQueueId())
    }

    @Test fun metadataOnlyArtworkUpdateKeepsTheCurrentRunAndEveryOccurrence()=check { f ->
        f.start();val before=f.route.queuePlayer.currentMediaItem!!
        f.route.updateArtwork(before,Uri.parse("content://art/new"))
        assertEquals(1,f.engine.runs.size);assertEquals(3,f.route.queuePlayer.mediaItemCount)
        assertEquals("a-first",f.route.queuePlayer.currentMediaItem?.listeningEvidence()?.itemInstanceId)
    }

    @Test fun nativeEofKeepsFullTimelineAndNaturalHistoryWhileAdvancing()=check { f ->
        val old=f.start()
        val created=f.scope.async { f.coordinator.createAndActivateQueue("EOF",listOf(b,c)) }
        old.stopped.await();old.terminal.complete(terminal(old));val next=f.engine.next();f.confirm(next)
        val queue=checkNotNull(created.await())
        val ended=naturalResult(next);assertTrue(ended.transport!!.verified);assertTrue(ended.integratedCleanupSafe())
        next.terminal.complete(ended)
        val successor=f.engine.next();f.confirm(successor)
        f.accepted(queue.entries.last().entryId)
        assertEquals(3,f.engine.runs.size);assertEquals(2,f.route.mediaItemCount);assertEquals(1,f.route.currentMediaItemIndex)
        assertEquals(1,f.events.count { it.second==DirectListeningEvent.NATURAL_END })
        assertEquals(1,f.events.count { it.second==DirectListeningEvent.TRANSITION })
    }

    @Test fun validEarlyEofAcknowledgesQueueActivationWithoutFabricatingPlayback()=check { f ->
        val created=f.scope.async { f.coordinator.createAndActivateQueue("Short",listOf(b,c)) }
        val next=f.engine.next();val ended=naturalResult(next)
        assertTrue(ended.transport!!.verified);next.terminal.complete(ended)
        val queue=checkNotNull(created.await())
        assertEquals(queue.queue.queueId,f.repository.getActiveQueueId())
        val successor=f.engine.next()
        assertEquals(c.uri,successor.uri);assertTrue(f.events.isEmpty())
        f.confirm(successor);f.accepted(queue.entries.last().entryId)
        assertEquals(2,f.engine.runs.size)
        assertEquals(2,f.route.queuePlayer.mediaItemCount)
    }

    @Test fun existingLibraryContextKeepsItsCompleteTimelineAndCanonicalDuplicateOrderAfterNativeAcknowledgment()=check { f ->
        val old=f.start()
        val songs=listOf(b,a,b)
        val items=listOf(b.toPlayableMediaItem("context-b1"),a.toPlayableMediaItem("context-a"),b.toPlayableMediaItem("context-b2"))
        val canonical=listOf("context-b1","context-b2","context-a")
        assertTrue(PlaybackQueueRuntimeBridge.requestPlaybackContext(songs,items,2,3000,true,canonical,true,PersistedQueueRepeatMode.ONE))
        val context=f.scope.async { f.contextResults.receive() }
        old.stopped.await();assertEquals(listOf("a-first","b","a-second"),f.repository.loadQueue(f.originalId)?.entries?.map { it.entryId })
        assertEquals(f.originalId,f.repository.getActiveQueueId());assertEquals(1,f.repository.listQueues().size)
        old.terminal.complete(terminal(old));val next=f.engine.next()
        assertEquals(b.uri,next.uri);assertFalse(context.isCompleted);assertEquals(144000L,next.initial.targetFrame)
        f.confirm(next);assertTrue(context.await())
        assertEquals(listOf("context-b1","context-a","context-b2"),f.runtime.captureSnapshot()?.entries?.map { it.entryId })
        assertEquals(canonical,f.coordinator.captureActiveQueueSnapshot()?.baseEntryIds)
        val directQueueId=checkNotNull(f.coordinator.getActiveQueueId())
        assertNotEquals(f.originalId,directQueueId)
        assertEquals("context-b2",f.repository.loadQueue(directQueueId)?.queue?.currentEntryId)
        assertEquals(listOf("a-first","b","a-second"),f.repository.loadQueue(f.originalId)?.entries?.map { it.entryId })
        assertEquals(2,f.route.queuePlayer.currentMediaItemIndex)
        f.hubOwns(directQueueId)
        next.emit(UsbFullTransportPhase.PLAYING,192000)
        f.route.selectDirect(false)
        f.route.selectDirect(true) // A request during the outstanding return cannot overtake cleanup.
        next.terminal.complete(terminal(next));f.settled();f.assertRestoredAndroid()
        assertEquals(4000L,f.repository.loadQueue(directQueueId)?.queue?.currentPositionMs)
        assertEquals(canonical,f.repository.loadQueue(directQueueId)?.entries?.sortedBy { it.baseOrder }?.map { it.entryId })
        next.emit(UsbFullTransportPhase.PLAYING,240000)
        f.coordinator.persistActiveQueueSnapshot();f.assertRestoredAndroid()
        f.route.selectDirect(true);f.settled();next.emit(UsbFullTransportPhase.PLAYING,288000)
        assertEquals("a-first",f.coordinator.captureActiveQueueSnapshot()?.currentEntryId)
        f.route.selectDirect(false);f.settled();f.assertRestoredAndroid()
        f.forwarding { f.route.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) }
        f.route.seekToNextMediaItem()
        f.forwarding { f.route.currentMediaItem?.listeningEvidence()?.itemInstanceId=="b" }
        f.coordinator.persistActiveQueueSnapshot()
        assertEquals("b",f.coordinator.captureActiveQueueSnapshot()?.currentEntryId)
        assertEquals(f.originalId,f.repository.getActiveQueueId())
        assertEquals(4000L,f.repository.loadQueue(directQueueId)?.queue?.currentPositionMs)
        assertEquals(2,f.repository.listQueues().size)
        val allEntries=f.repository.listQueues().flatMap { checkNotNull(f.repository.loadQueue(it.queueId)).entries }
        assertEquals(allEntries.size,allEntries.map { it.entryId }.distinct().size)
    }

    @Test fun deletedOriginalQueueRestoresAndroidUnassignedWithoutRecreatingItsOccurrences()=check { f ->
        f.hubOwns(f.originalId)
        val old=f.start();val saved=f.saved()
        val activation=f.scope.async { f.coordinator.switchToQueue(saved.queue.queueId) }
        old.stopped.await();old.terminal.complete(terminal(old));val next=f.engine.next()
        f.confirm(next);assertTrue(activation.await());f.hubOwns(saved.queue.queueId)
        assertTrue(f.repository.deleteInactiveQueueIfNotLast(f.originalId))
        next.emit(UsbFullTransportPhase.PLAYING,192000)
        f.route.selectDirect(false);next.terminal.complete(terminal(next));f.settled();f.hubOwns(null)
        assertFalse(f.hub.state.value.queues.any { it.isActive });assertFalse(f.route.playWhenReady)
        assertEquals(3,f.route.mediaItemCount);assertEquals(2000L,f.route.currentPosition)
        next.emit(UsbFullTransportPhase.PLAYING,288000);f.coordinator.persistActiveQueueSnapshot()
        assertNull(f.coordinator.getActiveQueueId());assertNull(f.repository.getActiveQueueId())
        assertNull(f.repository.loadQueue(f.originalId));assertEquals(1,f.repository.listQueues().size)
        assertEquals(saved.entries,f.repository.loadQueue(saved.queue.queueId)?.entries)
        assertEquals(4000L,f.repository.loadQueue(saved.queue.queueId)?.queue?.currentPositionMs)
    }

    @Test fun stoppedOutgoingQueueDoesNotRestoreStalePlayingIntent()=check { f ->
        val old=f.start();f.route.stopDirect();old.terminal.complete(terminal(old))
        withTimeout(5000) { while(f.route.directState.phase!=IntegratedDirectPhase.STOPPED) yield() }
        val saved=f.saved(position=0)
        val activation=f.scope.async { f.coordinator.switchToQueue(saved.queue.queueId) }
        val next=f.engine.next();assertFalse(next.initial.play);f.confirm(next);assertTrue(activation.await())
    }
}
