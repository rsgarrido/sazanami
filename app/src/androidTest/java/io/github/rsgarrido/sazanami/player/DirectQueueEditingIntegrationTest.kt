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
internal class DirectQueueEditingIntegrationTest : DirectQueueTestFixture() {
    @Test fun shuffleAnchorsCurrentKeepsDuplicateIdsAndRestoresCanonicalOrderWithoutRestart()=check { f ->
        val current=f.startAt(1)
        val base=f.coordinator.captureActiveQueueSnapshot()!!.baseEntryIds
        val generation=f.route.directState.generation
        f.coordinator.onDirectShuffle(true)
        val shuffled=f.coordinator.captureActiveQueueSnapshot()!!
        assertTrue(shuffled.shuffleEnabled);assertEquals("b",shuffled.currentEntryId)
        assertEquals("b",shuffled.entries.first().entryId)
        assertEquals(base.toSet(),shuffled.entries.map { it.entryId }.toSet())
        assertEquals(3,shuffled.entries.map { it.entryId }.distinct().size)
        val order=shuffled.entries.map { it.entryId }
        f.coordinator.onDirectShuffle(true)
        assertEquals(order,f.coordinator.captureActiveQueueSnapshot()?.entries?.map { it.entryId })
        f.coordinator.onDirectShuffle(false)
        val restored=f.coordinator.captureActiveQueueSnapshot()!!
        assertEquals(base,restored.entries.map { it.entryId });assertEquals("b",restored.currentEntryId)
        assertFalse(restored.shuffleEnabled);assertEquals(generation,f.route.directState.generation)
        assertFalse(current.control.stopped);assertEquals(1,f.engine.runs.size)
        assertEquals(listOf("b" to DirectListeningEvent.PLAYING),f.events)
        assertEquals(base,f.repository.loadQueue(f.originalId)?.entries?.map { it.entryId })
    }

    @Test fun playNextAppendShuffleAndEofUseOneExplicitOrder()=check { f ->
        val first=f.startAt()
        assertTrue(f.coordinator.enqueueSongs(listOf(c,b),playNext=true))
        val inserted=f.coordinator.captureActiveQueueSnapshot()!!.entries.drop(1).take(2).map { it.entryId }
        assertTrue(f.coordinator.enqueueSongs(listOf(a),playNext=false))
        val snapshot=f.coordinator.captureActiveQueueSnapshot()!!
        val queued=f.runtime.queuedEntryIds(snapshot)
        assertEquals(3,queued.size);assertEquals(inserted,queued.take(2))
        f.coordinator.onDirectShuffle(true)
        val shuffled=f.coordinator.captureActiveQueueSnapshot()!!
        assertEquals(queued,shuffled.entries.drop(1).take(3).map { it.entryId })
        f.coordinator.onDirectShuffle(false)
        assertEquals(queued,f.coordinator.captureActiveQueueSnapshot()!!.entries.drop(1).take(3).map { it.entryId })
        assertFalse(first.control.stopped);assertEquals(1,f.engine.runs.size)
        first.terminal.complete(naturalResult(first))
        val next=f.engine.next();assertEquals(c.uri,next.uri)
        f.confirm(next);f.accepted(queued.first())
        assertEquals(1,f.engine.maximumActive)
    }

    @Test fun repeatAllWrapsInShuffledOrderWithoutRegeneratingIt()=check { f ->
        val first=f.startAt()
        f.coordinator.onDirectShuffle(true);f.coordinator.onDirectRepeat(Player.REPEAT_MODE_ALL)
        val order=f.coordinator.captureActiveQueueSnapshot()!!.entries.map { it.entryId }
        val last=f.scope.async { f.coordinator.playEntry(f.originalId,order.last()) }
        first.stopped.await();first.terminal.complete(terminal(first))
        val selected=f.engine.next();f.confirm(selected);assertTrue(last.await())
        selected.terminal.complete(naturalResult(selected))
        val wrapped=f.engine.next();f.confirm(wrapped);f.accepted(order.first())
        assertEquals(order,f.coordinator.captureActiveQueueSnapshot()?.entries?.map { it.entryId })
        assertEquals(PersistedQueueRepeatMode.ALL,f.repository.loadQueue(f.originalId)?.queue?.repeatMode)
        assertTrue(f.repository.loadQueue(f.originalId)!!.queue.shuffleEnabled)
    }

    @Test fun shuffleAndRepeatDuringRelativePreparationUpdatePendingIntentWithoutAnotherRun()=check { f ->
        val first=f.startAt()
        f.route.requestDirectNavigation(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        first.stopped.await()
        f.coordinator.onDirectShuffle(true);f.coordinator.onDirectRepeat(Player.REPEAT_MODE_ONE)
        first.terminal.complete(terminal(first))
        val next=f.engine.next();f.confirm(next);f.accepted("b")
        val snapshot=f.coordinator.captureActiveQueueSnapshot()!!
        assertEquals("b",snapshot.entries.first().entryId);assertTrue(snapshot.shuffleEnabled)
        assertEquals(PersistedQueueRepeatMode.ONE,snapshot.repeatMode)
        assertEquals(2,f.engine.runs.size);assertEquals(1,f.engine.maximumActive)
        assertEquals("b",f.repository.loadQueue(f.originalId)?.queue?.currentEntryId)
    }

    @Test fun upcomingRemovalUndoAndReorderPreserveNativeAttemptForBothIntents() {
        for(playing in listOf(true,false)) check(playing) { f ->
            val first=f.startAt();val generation=f.route.directState.generation
            assertTrue(f.coordinator.reorderEntry(f.originalId,"a-second",1))
            assertEquals(listOf("a-first","a-second","b"),f.coordinator.captureActiveQueueSnapshot()?.entries?.map { it.entryId })
            val removal=checkNotNull(f.coordinator.removeEntryForUndo(f.originalId,"a-second"))
            assertEquals(listOf("a-first","b"),f.coordinator.captureActiveQueueSnapshot()?.entries?.map { it.entryId })
            assertTrue(f.coordinator.undoRemoveEntry(removal))
            assertEquals(listOf("a-first","a-second","b"),f.coordinator.captureActiveQueueSnapshot()?.entries?.map { it.entryId })
            assertEquals(3,f.repository.loadQueue(f.originalId)?.entries?.size)
            assertEquals(generation,f.route.directState.generation);assertEquals(playing,first.control.playWhenReady)
            assertFalse(first.control.stopped);assertEquals(1,f.engine.runs.size)
            assertFalse(f.events.any { it.second==DirectListeningEvent.TRANSITION })
            assertFalse(f.route.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS))
        }
    }

    @Test fun editsDuringPreparationAndCurrentRemovalKeepSafetyGuardsAndSavedRows()=check { f ->
        val first=f.startAt()
        val before=f.repository.loadQueue(f.originalId)!!.entries
        assertFalse(f.coordinator.removeEntry(f.originalId,"a-first"))
        assertFalse(f.coordinator.reorderEntry(f.originalId,"a-first",2))
        f.route.requestDirectNavigation(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        first.stopped.await()
        assertFalse(f.coordinator.removeEntry(f.originalId,"a-second"))
        assertFalse(f.coordinator.reorderEntry(f.originalId,"a-second",1))
        assertFalse(f.coordinator.enqueueSongs(listOf(c),true))
        assertEquals(before,f.repository.loadQueue(f.originalId)?.entries)
        first.terminal.complete(terminal(first));val next=f.engine.next();f.confirm(next);f.accepted("b")
        assertEquals(before,f.repository.loadQueue(f.originalId)?.entries)
    }

    @Test fun editsInSeparateDirectQueueCheckpointAndRestoreOriginalAndroidIdentity()=check { f ->
        val first=f.startAt();val saved=f.saved(position=0,shuffle=false,repeat=PersistedQueueRepeatMode.OFF)
        val activating=f.scope.async { f.coordinator.switchToQueue(saved.queue.queueId) }
        first.stopped.await();first.terminal.complete(terminal(first))
        val current=f.engine.next();f.confirm(current);assertTrue(activating.await())
        assertTrue(f.coordinator.enqueueSongs(listOf(c),true))
        val added=f.coordinator.captureActiveQueueSnapshot()!!.entries[1].entryId
        assertTrue(f.coordinator.removeEntry(saved.queue.queueId,saved.entries.last().entryId))
        f.coordinator.onDirectShuffle(true)
        val accepted=f.coordinator.captureActiveQueueSnapshot()!!
        assertEquals(added,accepted.entries[1].entryId)
        assertEquals(accepted.entries.map { it.entryId },f.repository.loadQueue(saved.queue.queueId)?.entries?.map { it.entryId })
        f.route.selectDirect(false);current.terminal.complete(terminal(current));f.settled();f.assertRestoredAndroid()
        assertEquals(accepted.entries.map { it.entryId },f.repository.loadQueue(saved.queue.queueId)?.entries?.map { it.entryId })
        assertEquals(2,f.repository.listQueues().size)
    }

    @Test fun editingInheritedQueueCannotCopyOrStealGloballyUniqueOccurrenceIds()=check { f ->
        val current=f.startAt()
        assertTrue(f.coordinator.enqueueSongs(listOf(c),true))
        assertEquals(1,f.repository.listQueues().size)
        val edited=f.repository.loadQueue(f.originalId)!!
        assertEquals(4,edited.entries.size)
        assertTrue(edited.entries.all { it.queueId==f.originalId })
        f.route.selectDirect(false);current.terminal.complete(terminal(current));f.settled()
        assertNull(f.repository.getActiveQueueId())
        assertEquals(edited.entries,f.repository.loadQueue(f.originalId)?.entries)
        assertEquals(3,f.route.mediaItemCount);assertEquals(2000L,f.route.currentPosition)
        assertFalse(f.route.playWhenReady)
    }
}
