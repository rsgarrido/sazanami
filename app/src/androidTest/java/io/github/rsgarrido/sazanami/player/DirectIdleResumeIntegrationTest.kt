package io.github.rsgarrido.sazanami.player

import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rsgarrido.sazanami.player.usb.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class DirectIdleResumeIntegrationTest : DirectQueueTestFixture() {
    @Test fun initialActivationOrdinaryControllerPlaySeeksBeforeReadinessAndKeepsIdentity()=check { f -> f.withController { controller ->
        f.forwarding { f.route.isCommandAvailable(Player.COMMAND_PLAY_PAUSE) }
        assertEquals(IntegratedDirectPhase.IDLE,f.route.directState.phase)
        assertFalse(f.route.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS))
        assertFalse(f.route.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
        assertFalse(allowsDirectCommand(Player.COMMAND_SET_MEDIA_ITEM,false))
        assertFalse(f.route.isPlaying);assertFalse(f.route.playWhenReady);assertTrue(f.engine.runs.isEmpty())
        val adopted=checkNotNull(f.coordinator.captureActiveQueueSnapshot())
        controller.togglePlayPause()
        val run=f.engine.next()
        assertEquals(a.uri,run.uri);assertEquals(96000L,run.initial.targetFrame)
        assertFalse(run.initial.play)
        assertFalse(f.route.directQueueCheckpointAllowed)
        assertNull(f.coordinator.captureActiveQueueSnapshot())
        assertTrue(f.events.isEmpty())
        run.emit(UsbFullTransportPhase.PAUSED,run.initial.targetFrame,1,run.initial.seekSerial)
        assertTrue(run.control.playWhenReady)
        assertFalse(f.route.directQueueCheckpointAllowed)
        assertTrue(f.events.isEmpty())
        run.emit(UsbFullTransportPhase.PLAYING)
        f.accepted("a-first");f.forwarding { f.route.isPlaying }
        val accepted=checkNotNull(f.coordinator.captureActiveQueueSnapshot())
        assertEquals(adopted.entries,accepted.entries);assertEquals(adopted.baseEntryIds,accepted.baseEntryIds)
        assertEquals(adopted.currentEntryId,accepted.currentEntryId)
        assertEquals(2000L,accepted.currentPositionMs);assertEquals(2000L,f.route.currentPosition)
        assertEquals(f.originalId,f.repository.getActiveQueueId())
        assertEquals(2000L,f.repository.loadQueue(f.originalId)?.queue?.currentPositionMs)
        assertEquals(listOf("a-first" to DirectListeningEvent.PLAYING),f.events)
        assertTrue(f.route.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))
        assertEquals(1,f.engine.maximumActive);assertEquals(0,f.recreations)
    } }

    @Test fun zeroPositionOrdinaryMedia3PlayStartsWithoutAnInitialSeek()=check { f ->
        f.route.restoreDirectIdleTimeline(f.items,1,0,false,Player.REPEAT_MODE_ALL)
        f.forwarding { f.route.isCommandAvailable(Player.COMMAND_PLAY_PAUSE) }
        f.route.play()
        val run=f.engine.next();assertEquals(b.uri,run.uri)
        assertTrue(run.initial.play);assertEquals(0L,run.initial.seekSerial)
        f.confirm(run);f.accepted("b");f.forwarding { f.route.isPlaying }
        assertEquals(0L,f.route.currentPosition);assertEquals(Player.REPEAT_MODE_ALL,f.route.repeatMode)
        assertEquals(f.originalId,f.repository.getActiveQueueId());assertEquals(1,f.repository.listQueues().size)
    }

    @Test fun reactivationAdoptsAndroidAndResumesItsPositionWhileSavedDirectQueueRemainsAvailable()=check { f -> f.withController { controller ->
        val first=f.startAt()
        controller.playSelectedSong(b,listOf(b,c))
        first.stopped.await();first.terminal.complete(terminal(first))
        val direct=f.engine.next();f.confirm(direct)
        assertTrue(withTimeout(5000) { f.contextResults.receive() })
        val savedDirectId=checkNotNull(f.coordinator.getActiveQueueId())
        val directEntries=checkNotNull(f.repository.loadQueue(savedDirectId)).entries
        direct.emit(UsbFullTransportPhase.PLAYING,144000)
        f.route.selectDirect(false);direct.stopped.await();direct.terminal.complete(terminal(direct))
        f.settled();f.assertRestoredAndroid()
        f.route.selectDirect(true);f.settled()
        f.forwarding { f.route.isCommandAvailable(Player.COMMAND_PLAY_PAUSE) && f.route.currentMediaItem?.mediaId=="1" }
        assertEquals(2,f.engine.runs.size);assertFalse(f.route.isPlaying)
        assertEquals(2000L,f.route.currentPosition);assertEquals(f.originalId,f.coordinator.getActiveQueueId())
        controller.togglePlayPause()
        val adopted=f.engine.next();assertEquals(a.uri,adopted.uri)
        assertEquals(96000L,adopted.initial.targetFrame);assertFalse(adopted.initial.play)
        f.confirm(adopted);f.accepted("a-first")
        assertEquals(directEntries,f.repository.loadQueue(savedDirectId)?.entries)
        assertEquals(3000L,f.repository.loadQueue(savedDirectId)?.queue?.currentPositionMs)
        val activation=f.scope.async { f.coordinator.switchToQueue(savedDirectId) }
        adopted.stopped.await();adopted.terminal.complete(terminal(adopted))
        val restored=f.engine.next();assertEquals(b.uri,restored.uri)
        assertEquals(144000L,restored.initial.targetFrame)
        f.confirm(restored);assertTrue(activation.await())
        assertEquals(savedDirectId,f.repository.getActiveQueueId())
        assertEquals(1,f.engine.maximumActive);assertEquals(1,f.recreations)
    } }

    @Test fun repeatedMedia3PlayDuringPreparationCreatesOnlyOneNativeRun()=check { f ->
        f.forwarding { f.route.isCommandAvailable(Player.COMMAND_PLAY_PAUSE) }
        f.route.play();val run=f.engine.next()
        f.forwarding { f.route.playbackState==Player.STATE_BUFFERING && f.route.playWhenReady }
        repeat(3) { f.route.play() }
        assertEquals(1,f.engine.runs.size);assertFalse(run.control.playWhenReady)
        f.confirm(run);f.accepted("a-first");f.forwarding { f.route.isPlaying }
        assertEquals(1,f.events.count { it.second==DirectListeningEvent.PLAYING })
        assertEquals(1,f.engine.maximumActive)
    }

    @Test fun pauseDuringInitialSeekWinsOverOriginalPlayAndLaterResumesSameRun()=check { f -> f.withController { controller ->
        f.forwarding { f.route.isCommandAvailable(Player.COMMAND_PLAY_PAUSE) }
        controller.togglePlayPause();val run=f.engine.next()
        controller.pausePlayback()
        assertFalse(run.control.playWhenReady)
        f.confirm(run);f.accepted("a-first")
        f.forwarding { f.route.playbackState==Player.STATE_READY && !f.route.playWhenReady }
        assertEquals(IntegratedDirectPhase.PAUSED,f.route.directState.phase)
        assertFalse(f.route.isPlaying);assertTrue(f.events.isEmpty())
        assertEquals(2000L,f.repository.loadQueue(f.originalId)?.queue?.currentPositionMs)
        controller.togglePlayPause();assertTrue(run.control.playWhenReady)
        run.emit(UsbFullTransportPhase.PLAYING);f.forwarding { f.route.isPlaying }
        assertEquals(1,f.engine.runs.size)
        assertEquals(listOf("a-first" to DirectListeningEvent.PLAYING),f.events)
    } }

    @Test fun newerExactOccurrenceSupersedesIdlePreparationWithoutCheckpointingOldReadiness()=check { f ->
        f.forwarding { f.route.isCommandAvailable(Player.COMMAND_PLAY_PAUSE) }
        f.route.play();val preparing=f.engine.next()
        val exact=f.scope.async { f.coordinator.playEntry(f.originalId,"b") }
        preparing.stopped.await();preparing.terminal.complete(terminal(preparing))
        val selected=f.engine.next();assertEquals(b.uri,selected.uri)
        preparing.emit(UsbFullTransportPhase.PLAYING)
        f.confirm(selected);assertTrue(exact.await());f.accepted("b")
        assertEquals(f.originalId,f.repository.getActiveQueueId())
        assertEquals("b",f.repository.loadQueue(f.originalId)?.queue?.currentEntryId)
        assertEquals(listOf("b" to DirectListeningEvent.PLAYING),f.events)
        assertEquals(1,f.engine.maximumActive)
    }

    @Test fun newerLibraryContextSupersedesIdlePreparationWithoutCreatingAnOldAttempt()=check { f -> f.withController { controller ->
        f.forwarding { f.route.isCommandAvailable(Player.COMMAND_PLAY_PAUSE) }
        controller.togglePlayPause();val preparing=f.engine.next()
        controller.playSelectedSong(c,listOf(c,b))
        preparing.stopped.await();preparing.terminal.complete(terminal(preparing))
        val selected=f.engine.next();assertEquals(c.uri,selected.uri)
        preparing.emit(UsbFullTransportPhase.PLAYING)
        f.confirm(selected);assertTrue(withTimeout(5000) { f.contextResults.receive() })
        assertNotEquals(f.originalId,f.repository.getActiveQueueId())
        assertEquals("a-first",f.repository.loadQueue(f.originalId)?.queue?.currentEntryId)
        assertEquals(1,f.events.size);assertEquals(DirectListeningEvent.PLAYING,f.events.single().second)
        assertEquals(1,f.engine.maximumActive)
    } }

    @Test fun outputSwitchAndDisconnectDuringPreparationWaitForCleanupAndRestoreAndroidPaused() {
        for(disconnect in listOf(false,true)) check { f ->
            f.forwarding { f.route.isCommandAvailable(Player.COMMAND_PLAY_PAUSE) }
            f.route.play();val preparing=f.engine.next()
            if(disconnect) { f.attachmentFailure="DIRECT_USB_SELECTION_CHANGED";f.route.usbSelectionChanged() }
            else f.route.selectDirect(false)
            preparing.stopped.await()
            f.forwarding { !f.route.isCommandAvailable(Player.COMMAND_PLAY_PAUSE) }
            assertEquals(0,f.recreations)
            assertFalse(f.route.queuePlayer.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))
            preparing.terminal.complete(terminal(preparing));f.settled();f.assertRestoredAndroid()
            preparing.emit(UsbFullTransportPhase.PLAYING)
            assertEquals(1,f.engine.runs.size);assertEquals(0,f.engine.active)
            assertFalse(f.route.isPlaying);assertTrue(f.events.isEmpty())
            assertEquals(1,f.engine.maximumActive);assertEquals(1,f.recreations)
        }
    }

    @Test fun startupAndNativePreparationFailureDoNotAcceptPlaybackOrFabricateHistory() {
        for(startup in listOf(true,false)) check { f ->
            f.forwarding { f.route.isCommandAvailable(Player.COMMAND_PLAY_PAUSE) }
            if(startup) f.engine.startupFailure="UNSUPPORTED_TRACK"
            f.route.play()
            if(!startup) { val run=f.engine.next();run.terminal.complete(terminal(run,status=UsbSilentStatus.FAILED)) }
            f.forwarding { f.route.playerError!=null }
            assertEquals(IntegratedDirectPhase.ERROR,f.route.directState.phase)
            assertFalse(f.route.isPlaying);assertFalse(f.route.playWhenReady)
            assertFalse(f.route.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))
            assertFalse(f.route.directQueueCheckpointAllowed)
            assertTrue(f.route.isDirectSelected);assertEquals(0,f.recreations);assertEquals(0,f.engine.active)
            assertTrue(f.events.isEmpty());assertEquals(1,f.repository.listQueues().size)
            assertEquals("a-first",f.repository.loadQueue(f.originalId)?.queue?.currentEntryId)
            assertEquals(2000L,f.repository.loadQueue(f.originalId)?.queue?.currentPositionMs)
        }
    }

    @Test fun failedInitialSeekNeverReleasesPlaybackOrAcceptsAQueueCheckpoint()=check { f ->
        f.engine.seekable=false
        f.forwarding { f.route.isCommandAvailable(Player.COMMAND_PLAY_PAUSE) }
        f.route.play();val run=f.engine.next()
        assertFalse(run.control.playWhenReady)
        run.terminal.complete(terminal(run,status=UsbSilentStatus.FAILED))
        f.forwarding { f.route.playerError!=null }
        assertEquals("DIRECT_INITIAL_POSITION_UNSUPPORTED",f.route.directState.error)
        assertFalse(f.route.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))
        assertFalse(f.route.isPlaying);assertFalse(f.route.directQueueCheckpointAllowed)
        assertEquals(2000L,f.repository.loadQueue(f.originalId)?.queue?.currentPositionMs)
        assertTrue(f.events.isEmpty());assertEquals(0,f.recreations)
    }

    @Test fun stopAndShutdownDuringPreparationCannotBeUndoneByLateProgress() {
        for(shutdown in listOf(false,true)) check { f ->
            f.forwarding { f.route.isCommandAvailable(Player.COMMAND_PLAY_PAUSE) }
            f.route.play();val preparing=f.engine.next()
            val closing=if(shutdown) f.scope.async { f.route.shutdown() } else { f.route.stop();null }
            preparing.stopped.await()
            f.forwarding { !f.route.isCommandAvailable(Player.COMMAND_PLAY_PAUSE) }
            assertFalse(f.route.queuePlayer.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))
            preparing.terminal.complete(terminal(preparing))
            if(closing!=null) assertTrue(closing.await())
            else {
                withTimeout(5000) { IntegratedDirectPlaybackBridge.state.first { it.phase==IntegratedDirectPhase.STOPPED } }
                f.forwarding { f.route.playbackState==Player.STATE_IDLE && !f.route.playWhenReady }
            }
            preparing.emit(UsbFullTransportPhase.PLAYING)
            assertFalse(f.route.directState.playWhenReady);assertEquals(0,f.engine.active)
            assertEquals(1,f.engine.runs.size);assertEquals(0,f.recreations);assertTrue(f.events.isEmpty())
        }
    }

    @Test fun idlePlayIsUnavailableWithoutSourceOrCurrentUsbAuthorization()=check { f ->
        f.route.restoreDirectIdleTimeline(emptyList(),0,0,false,Player.REPEAT_MODE_OFF)
        f.forwarding { !f.route.isCommandAvailable(Player.COMMAND_PLAY_PAUSE) }
        f.route.play();assertTrue(f.engine.runs.isEmpty())
        f.route.restoreDirectIdleTimeline(f.items,0,2000,false,Player.REPEAT_MODE_OFF)
        f.forwarding { f.route.isCommandAvailable(Player.COMMAND_PLAY_PAUSE) }
        f.attachmentFailure="DIRECT_USB_PERMISSION_FAILED"
        f.route.usbSelectionChanged();f.settled();f.assertRestoredAndroid()
        assertTrue(f.engine.runs.isEmpty());assertFalse(f.route.isPlaying)
    }
}
