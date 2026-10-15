package io.github.rsgarrido.sazanami.player

import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rsgarrido.sazanami.player.usb.UsbFullTransportPhase
import io.github.rsgarrido.sazanami.player.usb.UsbFullProgress
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class DirectNavigationIntegrationTest : DirectQueueTestFixture() {
    @Test fun nextThroughSessionPlayerPreservesBothIntentsAndDuplicateOccurrences() {
        for(playing in listOf(true,false)) check(playing) { f ->
            val first=f.startAt()
            val second=f.navigate(first,Player.COMMAND_SEEK_TO_NEXT);f.accepted("b")
            assertEquals(b.uri,second.uri);assertEquals(playing,second.control.playWhenReady)
            assertEquals(1,f.route.currentMediaItemIndex)
            val duplicate=f.navigate(second);f.accepted("a-second")
            assertEquals(a.uri,duplicate.uri);assertEquals(playing,duplicate.control.playWhenReady)
            assertEquals(2,f.route.currentMediaItemIndex)
            assertEquals("a-second",f.repository.loadQueue(f.originalId)?.queue?.currentEntryId)
            assertEquals(if(playing) 2 else 0,f.events.count { it.second==DirectListeningEvent.TRANSITION })
            assertEquals(1,f.engine.maximumActive);assertEquals(0,f.recreations)
        }
    }

    @Test fun previousAtAndBelowThresholdUsesAdjacentOccurrence() {
        for(position in listOf(2_999L,3_000L)) check { f ->
            val current=f.startAt(1)
            current.emit(UsbFullTransportPhase.PLAYING,position*48)
            val previous=f.navigate(current,Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            f.accepted("a-first")
            assertEquals(a.uri,previous.uri);assertTrue(previous.control.playWhenReady)
            assertEquals(1,f.events.count { it.second==DirectListeningEvent.TRANSITION })
        }
    }

    @Test fun previousAboveThresholdSeeksWithinSameAttemptPlayingAndPaused() {
        for(playing in listOf(true,false)) check(playing) { f ->
            val current=f.startAt(1)
            val phase=if(playing) UsbFullTransportPhase.PLAYING else UsbFullTransportPhase.PAUSED
            current.emit(phase,3_001L*48)
            f.forwarding { f.route.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM) }
            f.route.seekToPreviousMediaItem()
            assertNull(f.coordinator.captureActiveQueueSnapshot())
            assertEquals(1L,current.control.intent().seeks);assertEquals(0L,current.control.intent().targetFrame)
            assertFalse(current.control.stopped);assertEquals(1,f.engine.runs.size)
            current.emit(phase,0,1,current.control.intent().seekSerial)
            f.accepted("b")
            assertEquals(0L,f.repository.loadQueue(f.originalId)?.queue?.currentPositionMs)
            assertEquals(playing,current.control.playWhenReady)
            assertFalse(f.events.any { it.second==DirectListeningEvent.TRANSITION })
            assertEquals(if(playing) 1 else 0,f.events.count { it.second==DirectListeningEvent.DISCONTINUITY })
        }
    }

    @Test fun repeatOffBoundariesLeaveRunAndSelectionAlone()=check { f ->
        val first=f.startAt()
        f.forwarding { f.route.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM) }
        f.route.seekToPreviousMediaItem();f.accepted("a-first")
        assertFalse(first.control.stopped);assertEquals(1,f.engine.runs.size)
        val selecting=f.scope.async { f.coordinator.playEntry(f.originalId,"a-second") }
        first.stopped.await();first.terminal.complete(terminal(first))
        val last=f.engine.next();f.confirm(last);assertTrue(selecting.await())
        f.forwarding { f.route.currentMediaItemIndex==2 }
        f.route.seekToNextMediaItem();f.accepted("a-second")
        assertFalse(last.control.stopped);assertEquals(2,f.engine.runs.size)
    }

    @Test fun genericNotificationPreviousRestartsFirstItemUsingNativeSeek()=check { f ->
        val first=f.startAt()
        first.emit(UsbFullTransportPhase.PLAYING,1_000L*48)
        f.forwarding { f.route.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS) }
        f.route.seekToPrevious()
        assertEquals(1L,first.control.intent().seeks);assertEquals(0L,first.control.intent().targetFrame)
        assertEquals(1,f.engine.runs.size);assertFalse(first.control.stopped)
        first.emit(UsbFullTransportPhase.PLAYING,0,1,first.control.intent().seekSerial)
        f.accepted("a-first")
        assertFalse(f.events.any { it.second==DirectListeningEvent.TRANSITION })
    }

    @Test fun unavailableNativeSeekCannotTurnPreviousRestartIntoANewListeningAttempt()=check { f ->
        f.engine.seekable=false
        val first=f.startAt(1)
        first.progress(UsbFullProgress(0,480000,48000,UsbFullTransportPhase.PLAYING,
            mapOf("sourcePositionFrame" to 3_001L*48,"sourceSeekCapable" to 0L)))
        f.route.requestDirectNavigation(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
        f.accepted("b")
        assertEquals(1,f.engine.runs.size);assertFalse(first.control.stopped)
        assertFalse(f.events.any { it.second==DirectListeningEvent.TRANSITION })
    }

    @Test fun secondPreviousDuringRestartUsesPendingZeroPositionAndSelectsPreviousOccurrence()=check { f ->
        val current=f.startAt(1)
        current.emit(UsbFullTransportPhase.PLAYING,3_001L*48)
        f.route.requestDirectNavigation(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
        assertEquals(1L,current.control.intent().seeks)
        f.route.requestDirectNavigation(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
        current.stopped.await();current.terminal.complete(terminal(current))
        val previous=f.engine.next();f.confirm(previous);f.accepted("a-first")
        assertEquals(a.uri,previous.uri);assertEquals(2,f.engine.runs.size)
        assertEquals(1,f.events.count { it.second==DirectListeningEvent.TRANSITION })
    }

    @Test fun threeRapidNextCommandsIncludeTheEndBoundaryWithoutLosingCheckpoint()=check { f ->
        val first=f.startAt()
        f.route.requestDirectNavigation(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        first.stopped.await()
        f.route.requestDirectNavigation(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        f.route.requestDirectNavigation(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        assertEquals(1,f.engine.runs.size)
        first.terminal.complete(terminal(first))
        val last=f.engine.next();f.confirm(last);f.accepted("a-second")
        assertEquals(a.uri,last.uri);assertEquals(2,f.engine.runs.size)
        assertEquals("a-second",f.repository.loadQueue(f.originalId)?.queue?.currentEntryId)
    }

    @Test fun repeatedNextWrapsForEveryPressUnderRepeatAll()=check { f ->
        val first=f.startAt(repeat=Player.REPEAT_MODE_ALL)
        repeat(3) { f.route.requestDirectNavigation(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) }
        first.stopped.await();first.terminal.complete(terminal(first))
        val wrapped=f.engine.next();f.confirm(wrapped);f.accepted("a-first")
        assertEquals(2,f.engine.runs.size);assertEquals(a.uri,wrapped.uri)
        assertEquals(1,f.engine.maximumActive)
    }

    @Test fun rapidPreviousAndOpposingCommandsResolveFromPendingIntent() {
        val sequences=listOf(
            listOf(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM) to "a-first",
            listOf(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) to "a-second",
            listOf(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM) to "b"
        )
        sequences.forEachIndexed { index,(commands,expected) -> check { f ->
            val first=f.startAt(if(index==2) 1 else 2)
            commands.forEach { f.route.requestDirectNavigation(it) }
            first.stopped.await();first.terminal.complete(terminal(first))
            val last=f.engine.next();f.confirm(last);f.accepted(expected)
            assertEquals(expected,f.repository.loadQueue(f.originalId)?.queue?.currentEntryId)
            assertEquals(1,f.engine.maximumActive)
        } }
    }

    @Test fun exactSelectionSupersedesRelativePreparationAndOldProgress()=check { f ->
        val first=f.startAt()
        f.route.requestDirectNavigation(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        first.stopped.await();first.terminal.complete(terminal(first))
        val preparing=f.engine.next()
        val exact=f.scope.async { f.coordinator.playEntry(f.originalId,"a-second") }
        preparing.stopped.await();preparing.terminal.complete(terminal(preparing))
        val last=f.engine.next();f.confirm(last);assertTrue(exact.await());f.accepted("a-second")
        preparing.emit(UsbFullTransportPhase.PLAYING)
        assertEquals("a-second",f.coordinator.captureActiveQueueSnapshot()?.currentEntryId)
        assertEquals(1,f.engine.maximumActive)
        assertEquals(1,f.events.count { it.second==DirectListeningEvent.TRANSITION })
    }

    @Test fun savedQueueNavigationKeepsBothQueueIdentities()=check { f ->
        val first=f.startAt();val saved=f.saved(position=0)
        val activation=f.scope.async { f.coordinator.switchToQueue(saved.queue.queueId) }
        first.stopped.await();first.terminal.complete(terminal(first))
        val selected=f.engine.next();f.confirm(selected);assertTrue(activation.await())
        val next=f.navigate(selected);f.accepted(saved.entries[1].entryId)
        assertEquals(saved.queue.queueId,f.repository.getActiveQueueId())
        f.route.selectDirect(false);next.terminal.complete(terminal(next));f.settled();f.assertRestoredAndroid()
        assertEquals(saved.entries[1].entryId,f.repository.loadQueue(saved.queue.queueId)?.queue?.currentEntryId)
    }

    @Test fun relativePressesDuringSavedActivationWaitForOwnershipAndCountEveryPress()=check { f ->
        val first=f.startAt();val saved=f.saved(position=0)
        val activation=f.scope.async { f.coordinator.switchToQueue(saved.queue.queueId) }
        first.stopped.await()
        repeat(3) { f.route.requestDirectNavigation(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) }
        first.terminal.complete(terminal(first))
        val activated=f.engine.next();f.confirm(activated);assertTrue(activation.await())
        activated.stopped.await();activated.terminal.complete(terminal(activated))
        val selected=f.engine.next();f.confirm(selected);f.accepted(saved.queue.currentEntryId!!)
        assertEquals(saved.queue.queueId,f.repository.getActiveQueueId())
        assertEquals(3,f.engine.runs.size);assertEquals(1,f.engine.maximumActive)
        assertEquals(saved.queue.currentEntryId,f.repository.loadQueue(saved.queue.queueId)?.queue?.currentEntryId)
    }

    @Test fun navigatingUnsavedIdleContextAllocatesOwnershipOnlyAfterNativeAcceptance()=check { f ->
        val items=listOf(a.toPlayableMediaItem("unsaved-a"),b.toPlayableMediaItem("unsaved-b"))
        f.route.restoreDirectIdleTimeline(items,0,0,false,Player.REPEAT_MODE_OFF)
        f.route.requestDirectNavigation(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        val selected=f.engine.next()
        assertNull(f.coordinator.captureActiveQueueSnapshot())
        assertEquals(1,f.repository.listQueues().size)
        assertFalse(selected.initial.play)
        f.confirm(selected);f.accepted("unsaved-b")
        val directId=f.coordinator.getActiveQueueId()!!
        assertNotEquals(f.originalId,directId);assertEquals(2,f.repository.listQueues().size)
        assertEquals("unsaved-b",f.repository.loadQueue(directId)?.queue?.currentEntryId)
        assertEquals(listOf("a-first","b","a-second"),f.repository.loadQueue(f.originalId)?.entries?.map { it.entryId })
    }
}
