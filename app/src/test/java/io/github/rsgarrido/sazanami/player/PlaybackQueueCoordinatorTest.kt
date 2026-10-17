package io.github.rsgarrido.sazanami.player

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import io.github.rsgarrido.sazanami.data.PLAYBACK_QUEUE_WORKING_SOURCE_TYPE
import io.github.rsgarrido.sazanami.data.PlaybackQueueEntryDraft
import io.github.rsgarrido.sazanami.data.PlaybackQueueWorkingOutput
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.SongReference
import io.github.rsgarrido.sazanami.data.workingOutput
import io.github.rsgarrido.sazanami.data.local.PersistedQueueRepeatMode
import io.github.rsgarrido.sazanami.data.local.PlaybackQueueEntity
import io.github.rsgarrido.sazanami.data.local.PlaybackQueueEntryEntity
import io.github.rsgarrido.sazanami.data.local.PlaybackQueueWithEntries
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class PlaybackQueueCoordinatorTest {
    @Test fun pendingNativeAcknowledgmentCannotCommitOrCheckpointAPlaceholder() = runBlocking {
        val persistence=FakePersistence("A").apply {
            seed(queue("A",listOf(spec("a",1,0,0)),"a"))
            seed(queue("B",listOf(spec("b",2,0,0)),"b",position=3000))
        }
        val runtime=FakeRuntime(liveSnapshot(listOf("a" to 1L),"a",1500,true))
        val coordinator=coordinator(persistence,runtime);coordinator.initialize()
        val started=CompletableDeferred<Unit>();val ready=CompletableDeferred<Boolean>()
        runtime.beforeAcknowledgment={ restoration ->
            runtime.snapshot=liveSnapshot(listOf("b" to 2L),"b",restoration.currentPositionMs,true)
            runtime.checkpointAllowed=false;started.complete(Unit)
            ready.await().also { runtime.checkpointAllowed=it }
        }
        val activation=async { coordinator.switchToQueue("B") };started.await()
        assertFalse(activation.isCompleted);assertEquals("A",coordinator.getActiveQueueId())
        assertNull(coordinator.captureActiveQueueSnapshot())
        coordinator.persistActiveQueueSnapshot()
        assertEquals("a",persistence.queue("A").queue.currentEntryId)
        assertEquals(1500L,persistence.queue("A").queue.currentPositionMs)
        ready.complete(true);assertTrue(activation.await())
        assertEquals("B",persistence.activeQueueId)
        assertEquals("b",coordinator.captureActiveQueueSnapshot()?.currentEntryId)
    }

    @Test fun failedNativeActivationRetainsSavedIdentityAndBlocksFailedTargetCheckpoint() = runBlocking {
        val persistence=FakePersistence("A").apply {
            seed(queue("A",listOf(spec("a",1,0,0)),"a"))
            seed(queue("B",listOf(spec("b",2,0,0)),"b"))
        }
        val runtime=FakeRuntime(liveSnapshot(listOf("a" to 1L),"a",1500,true))
        val coordinator=coordinator(persistence,runtime);coordinator.initialize()
        runtime.beforeAcknowledgment={
            runtime.snapshot=liveSnapshot(listOf("b" to 2L),"b",0,false)
            runtime.checkpointAllowed=false;false
        }
        assertFalse(coordinator.switchToQueue("B"))
        assertEquals("A",coordinator.getActiveQueueId());assertNull(coordinator.captureActiveQueueSnapshot())
        runtime.checkpointAllowed=true // Even later player snapshots cannot retroactively commit failure.
        coordinator.persistActiveQueueSnapshot()
        assertEquals(listOf("a"),persistence.queue("A").entries.map { it.entryId })
        assertEquals("A",persistence.activeQueueId)
        assertFalse(coordinator.onDirectRequested())
    }

    @Test fun newerAcceptedActivationOwnsCommitEvenWhenOlderAcknowledgmentArrivesFirst() = runBlocking {
        val persistence=FakePersistence("A").apply {
            for((id,track) in listOf("A" to 1L,"B" to 2L,"C" to 3L))
                seed(queue(id,listOf(spec(id.lowercase(),track,0,0)),id.lowercase()))
        }
        val runtime=FakeRuntime(liveSnapshot(listOf("a" to 1L),"a",0,true))
        val coordinator=coordinator(persistence,runtime);coordinator.initialize()
        val started=Channel<String>(Channel.UNLIMITED)
        val readyB=CompletableDeferred<Boolean>();val readyC=CompletableDeferred<Boolean>()
        runtime.beforeAcknowledgment={ restoration ->
            runtime.checkpointAllowed=false;started.send(restoration.queueId)
            (if(restoration.queueId=="B") readyB else readyC).await().also { runtime.checkpointAllowed=it }
        }
        val first=async { coordinator.switchToQueue("B") };assertEquals("B",started.receive())
        val last=async { coordinator.switchToQueue("C") };assertEquals("C",started.receive())
        readyB.complete(true);assertFalse(first.await());assertEquals("A",persistence.activeQueueId)
        readyC.complete(true);assertTrue(last.await());assertEquals("C",persistence.activeQueueId)
    }

    @Test fun cancellationDoesNotCommitOrLeaveCoordinatorMutexHeld() = runBlocking {
        val persistence=FakePersistence("A").apply {
            seed(queue("A",listOf(spec("a",1,0,0)),"a"))
            seed(queue("B",listOf(spec("b",2,0,0)),"b"))
        }
        val runtime=FakeRuntime(liveSnapshot(listOf("a" to 1L),"a",0,false))
        val coordinator=coordinator(persistence,runtime);coordinator.initialize()
        val started=CompletableDeferred<Unit>();val ready=CompletableDeferred<Boolean>()
        runtime.beforeAcknowledgment={ started.complete(Unit);ready.await() }
        val activation=async { coordinator.switchToQueue("B") };started.await()
        activation.cancel();activation.join()
        assertEquals("A",persistence.activeQueueId)
        runtime.beforeAcknowledgment=null
        assertTrue(coordinator.switchToQueue("B"));assertEquals("B",persistence.activeQueueId)
    }
    @Test
    fun unavailableQueueActivationLeavesSourceAndActiveIdentityUntouched() = runBlocking {
        val persistence = FakePersistence(activeQueueId = "A").apply {
            seed(queue("A", listOf(spec("native-source", 1L, 0, 0)), "native-source"))
            seed(queue("B", listOf(spec("other", 2L, 0, 0)), "other"))
        }
        val original = liveSnapshot(listOf("native-source" to 1L), "native-source", 1234L, true)
        val runtime = FakeRuntime(original)
        val coordinator = coordinator(persistence, runtime)
        coordinator.initialize()
        runtime.activationAllowed = false // The Direct player has no queue replacement command.
        val savedQueues = persistence.queues.toMap()

        assertFalse(coordinator.switchToQueue("B"))
        assertNull(coordinator.createAndActivateQueue("Rejected", listOf(song(3L))))
        assertEquals("A", coordinator.getActiveQueueId())
        assertEquals("A", persistence.activeQueueId)
        assertEquals(savedQueues, persistence.queues)
        assertEquals(original, runtime.snapshot)
        assertEquals(0, runtime.replaceCount)
        assertEquals(0, runtime.prepareCount)
        assertEquals(0, runtime.playCount)

        // Independent saved-queue management remains available on the same runtime.
        val saved = requireNotNull(coordinator.createInactiveQueue(listOf(song(3L))))
        assertTrue(coordinator.appendToInactiveQueue(saved.queue.queueId, listOf(song(4L))) != null)
        assertTrue(coordinator.reorderEntry(saved.queue.queueId, saved.entries.first().entryId, 1))
        assertTrue(coordinator.removeEntry(saved.queue.queueId, saved.entries.first().entryId))
        assertEquals("A", persistence.activeQueueId)
        assertEquals(original, runtime.snapshot)
        assertEquals(0, runtime.replaceCount)

        runtime.activationAllowed = true // Android can activate the original saved target normally.
        assertTrue(coordinator.switchToQueue("B"))
        assertEquals("B", persistence.activeQueueId)
        assertEquals("other", runtime.snapshot?.currentEntryId)
        assertEquals(1, runtime.replaceCount)
    }

    @Test
    fun activationRechecksBackendAfterSuspendedTargetResolution() = runBlocking {
        val persistence = FakePersistence(activeQueueId = "A").apply {
            seed(queue("A", listOf(spec("source", 1L, 0, 0)), "source"))
            seed(queue("B", listOf(spec("other", 2L, 0, 0)), "other"))
        }
        val original = liveSnapshot(listOf("source" to 1L), "source", 1234L, true)
        val runtime = FakeRuntime(original)
        val access = FakeTrackAccess()
        val coordinator = coordinator(persistence, runtime, access)
        coordinator.initialize()
        access.beforeResolve = { runtime.activationAllowed = false }

        assertFalse(coordinator.switchToQueue("B"))
        assertEquals("A", coordinator.getActiveQueueId())
        assertEquals("A", persistence.activeQueueId)
        assertEquals(original, runtime.snapshot)
        assertEquals(0, runtime.replaceCount)
        assertEquals(0, runtime.prepareCount)
        assertEquals(0, runtime.playCount)
    }

    @Test
    fun createAndActivateRechecksBackendBeforeSavingTheNewQueue() = runBlocking {
        val persistence = FakePersistence(activeQueueId = "A").apply {
            seed(queue("A", listOf(spec("source", 1L, 0, 0)), "source"))
        }
        val original = liveSnapshot(listOf("source" to 1L), "source", 1234L, true)
        val runtime = FakeRuntime(original)
        val access = FakeTrackAccess()
        val coordinator = coordinator(persistence, runtime, access)
        coordinator.initialize()
        access.beforeIdentify = { runtime.activationAllowed = false }

        assertNull(coordinator.createAndActivateQueue("Rejected", listOf(song(2L))))
        assertEquals("A", persistence.activeQueueId)
        assertEquals(setOf("A"), persistence.queues.keys)
        assertEquals(original, runtime.snapshot)
        assertEquals(0, runtime.replaceCount)
        assertEquals(0, runtime.prepareCount)
        assertEquals(0, runtime.playCount)
    }

    @Test
    fun backupRestoreSuppressesSnapshotsAndReconcilesRolledBackQueues() = runBlocking {
        val persistence = FakePersistence(activeQueueId = "A").apply {
            seed(queue("A", listOf(spec("one", 1L, 0, 0)), "one"))
        }
        val runtime = FakeRuntime(liveSnapshot(listOf("one" to 1L), "one", 25L, true))
        val coordinator = coordinator(persistence, runtime)
        coordinator.initialize()

        coordinator.beginBackupRestore()
        assertNull(runtime.snapshot)
        assertNull(coordinator.persistActiveQueueSnapshot())
        assertNull(coordinator.createQueueFromCurrent())
        assertEquals(1, persistence.queues.size)

        coordinator.finishBackupRestore(databaseCommitted = false)
        assertEquals("A", coordinator.getActiveQueueId())
        assertEquals("one", runtime.snapshot?.currentEntryId)
        assertEquals(1, persistence.queues.size)
    }

    @Test
    fun failedBackupRestoreRestoresLivePlaybackCheckpoint() = runBlocking {
        val persistence = FakePersistence(activeQueueId = "A").apply {
            seed(queue("A", listOf(spec("one", 1L, 0, 0)), "one"))
        }
        val original = liveSnapshot(listOf("one" to 1L), "one", 25L, true)
        val runtime = FakeRuntime(original).apply { captureBackupRestoreCheckpoint = true }
        val coordinator = coordinator(persistence, runtime)
        coordinator.initialize()

        coordinator.beginBackupRestore()
        assertNull(runtime.snapshot)
        coordinator.finishBackupRestore(databaseCommitted = false)

        assertEquals(original, runtime.snapshot)
        assertEquals("A", coordinator.getActiveQueueId())
    }

    @Test
    fun completedBackupRestoreDoesNotRecreateQueueFromLateCheckpoint() = runBlocking {
        val persistence = FakePersistence(activeQueueId = "A").apply {
            seed(queue("A", listOf(spec("one", 1L, 0, 0)), "one"))
        }
        val runtime = FakeRuntime(liveSnapshot(listOf("one" to 1L), "one", 25L, true))
        val coordinator = coordinator(persistence, runtime)
        coordinator.initialize()

        coordinator.beginBackupRestore()
        persistence.queues.clear()
        persistence.activeQueueId = null
        coordinator.finishBackupRestore(databaseCommitted = true)

        assertNull(coordinator.getActiveQueueId())
        assertNull(coordinator.persistActiveQueueSnapshot())
        assertTrue(persistence.queues.isEmpty())
    }

    @Test
    fun activeEntrySelectionSeeksExactStableEntryWithoutTimelineReplacement() = runBlocking {
        val persistence = FakePersistence(activeQueueId = "A").apply {
            seed(queue("A", listOf(spec("dup-1", 1L, 0, 0), spec("dup-2", 1L, 1, 1)), "dup-1"))
        }
        val runtime = FakeRuntime(
            liveSnapshot(listOf("dup-1" to 1L, "dup-2" to 1L), "dup-1", 99L, true)
        )
        val coordinator = coordinator(persistence, runtime)
        coordinator.initialize()

        assertTrue(coordinator.playEntry("A", "dup-2"))
        assertEquals("dup-2", runtime.seekedEntryId)
        assertEquals("dup-2", persistence.queue("A").queue.currentEntryId)
        assertEquals(listOf("dup-1", "dup-2"), persistence.queue("A").entries.map { it.entryId })
        assertEquals(0, runtime.replaceCount)
        assertTrue(coordinator.playEntry("A", "dup-1"))
        assertEquals("dup-1", runtime.seekedEntryId)
        assertEquals(2, runtime.seekCount)
        assertFalse(coordinator.playEntry("B", "dup-2"))
        assertEquals(2, runtime.seekCount)
    }

    @Test
    fun jumpCheckpointAndQueueRoundTripRestoreCurrentWithoutConsumingEarlierEntries() =
        runBlocking {
            val persistence = FakePersistence(activeQueueId = "A").apply {
                seed(queue(
                    "A",
                    listOf(
                        spec("A", 1L, 0, 0),
                        spec("B", 2L, 1, 1),
                        spec("C", 3L, 2, 2),
                        spec("D", 4L, 3, 3)
                    ),
                    current = "A",
                    position = 100L
                ))
                seed(queue("other", listOf(spec("other", 5L, 0, 0)), "other", 50L))
            }
            val runtime = FakeRuntime(
                liveSnapshot(
                    listOf("A" to 1L, "B" to 2L, "C" to 3L, "D" to 4L),
                    "A",
                    100L,
                    false
                )
            )
            val coordinator = coordinator(persistence, runtime)
            coordinator.initialize()

            assertTrue(coordinator.playEntry("A", "C"))
            runtime.snapshot = runtime.snapshot?.copy(currentPositionMs = 4_321L)
            coordinator.persistActiveQueueSnapshot()
            assertTrue(coordinator.switchToQueue("other"))
            assertTrue(coordinator.switchToQueue("A"))

            assertEquals("C", runtime.snapshot?.currentEntryId)
            assertEquals(4_321L, runtime.snapshot?.currentPositionMs)
            assertEquals(
                listOf("A", "B", "C", "D"),
                persistence.queue("A").entries.map { it.entryId }
            )
        }

    @Test
    fun activeRemovalUndoRestoresStableEntryAtPositionWithoutReplacement() = runBlocking {
        val persistence = FakePersistence(activeQueueId = "A").apply {
            seed(queue("A", listOf(spec("current", 1L, 0, 0), spec("removed", 2L, 1, 1), spec("last", 3L, 2, 2)), "current"))
        }
        val runtime = FakeRuntime(
            liveSnapshot(listOf("current" to 1L, "removed" to 2L, "last" to 3L), "current", 123L, true)
        )
        val coordinator = coordinator(persistence, runtime)
        coordinator.initialize()

        val removal = requireNotNull(coordinator.removeEntryForUndo("A", "removed"))
        assertTrue(coordinator.undoRemoveEntry(removal))

        assertEquals(listOf("current", "removed", "last"), runtime.snapshot?.entries?.map { it.entryId })
        assertEquals("current", runtime.snapshot?.currentEntryId)
        assertEquals(123L, runtime.snapshot?.currentPositionMs)
        assertEquals(0, runtime.replaceCount)
        assertEquals(listOf("current", "removed", "last"), persistence.queue("A").entries.map { it.entryId })
    }

    @Test
    fun inactiveRemovalUndoNeverTouchesLiveTimeline() = runBlocking {
        val persistence = FakePersistence(activeQueueId = "A").apply {
            seed(queue("A", listOf(spec("active", 1L, 0, 0)), "active"))
            seed(queue("B", listOf(spec("one", 2L, 0, 0), spec("two", 3L, 1, 1)), "one"))
        }
        val runtime = FakeRuntime(liveSnapshot(listOf("active" to 1L), "active", 88L, true))
        val coordinator = coordinator(persistence, runtime)
        coordinator.initialize()

        val removal = requireNotNull(coordinator.removeEntryForUndo("B", "two"))
        assertTrue(coordinator.undoRemoveEntry(removal))

        assertEquals(listOf("one", "two"), persistence.queue("B").entries.map { it.entryId })
        assertEquals(listOf("active"), runtime.snapshot?.entries?.map { it.entryId })
        assertEquals(0, runtime.insertCount)
        assertEquals(0, runtime.replaceCount)
    }

    @Test
    fun activeRuntimeRemovalRemainsSuccessfulWhenImmediatePersistenceCheckpointFails() =
        runBlocking {
            val persistence = FakePersistence(activeQueueId = "A").apply {
                seed(queue(
                    "A",
                    listOf(spec("current", 1L, 0, 0), spec("removed", 2L, 1, 1)),
                    "current"
                ))
                failNextReplace = true
            }
            val runtime = FakeRuntime(
                liveSnapshot(
                    listOf("current" to 1L, "removed" to 2L),
                    "current",
                    321L,
                    true
                )
            )
            val coordinator = coordinator(persistence, runtime)
            coordinator.initialize()

            val removal = coordinator.removeEntryForUndo("A", "removed")

            assertTrue(removal != null)
            assertEquals(listOf("current"), runtime.snapshot?.entries?.map { it.entryId })
            assertEquals("current", runtime.snapshot?.currentEntryId)
            assertEquals(321L, runtime.snapshot?.currentPositionMs)
            assertTrue(coordinator.undoRemoveEntry(requireNotNull(removal)))
            assertEquals(
                listOf("current", "removed"),
                runtime.snapshot?.entries?.map { it.entryId }
            )
            assertEquals(0, runtime.replaceCount)
        }
    @Test
    fun firstMeaningfulTimelineBootstrapsOneActiveQueueWithoutReplacingLiveSession() = runBlocking {
        val persistence = FakePersistence()
        val runtime = FakeRuntime(
            liveSnapshot(
                ids = listOf("one" to 1L, "two" to 2L),
                currentEntryId = "two",
                positionMs = 321L,
                shouldPlay = true,
                baseEntryIds = listOf("two", "one")
            )
        )
        val coordinator = coordinator(persistence, runtime)

        assertNull(coordinator.initialize())
        assertEquals(0, runtime.replaceCount)
        assertEquals("generated-queue", coordinator.persistActiveQueueSnapshot())
        assertEquals("generated-queue", persistence.activeQueueId)
        assertEquals("Queue 1", persistence.queues.getValue("generated-queue").queue.displayName)
        assertEquals(listOf("one", "two"), persistence.queue("generated-queue").entries.map { it.entryId })
        assertEquals(listOf(1, 0), persistence.queue("generated-queue").entries.map { it.baseOrder })
        assertEquals("two", persistence.queue("generated-queue").queue.currentEntryId)

        coordinator.persistActiveQueueSnapshot()
        assertEquals(1, persistence.createCount)
    }

    @Test
    fun initializationAdoptsAlreadyPlayingLiveTimelineInsteadOfRebuildingIt() = runBlocking {
        val persistence = FakePersistence(activeQueueId = "A").apply {
            seed(queue("A", listOf(spec("a", 1L, 0, 0)), current = "a"))
        }
        val runtime = FakeRuntime(
            liveSnapshot(listOf("live" to 9L), "live", 77L, shouldPlay = true)
        )

        coordinator(persistence, runtime).initialize()

        assertEquals(0, runtime.replaceCount)
        assertTrue(runtime.snapshot?.shouldPlay == true)
    }

    @Test
    fun liveSnapshotRetainsPersistedBaseIdentityForDuplicateOccurrences() = runBlocking {
        val persistence = FakePersistence(activeQueueId = "A").apply {
            seed(queue(
                id = "A",
                specs = listOf(
                    spec("duplicate-1", 1L, base = 0, playback = 2),
                    spec("middle", 2L, base = 1, playback = 1),
                    spec("duplicate-2", 1L, base = 2, playback = 0)
                ),
                current = "duplicate-2",
                shuffle = true
            ))
        }
        val runtime = FakeRuntime(
            liveSnapshot(
                ids = listOf("duplicate-2" to 1L, "middle" to 2L, "duplicate-1" to 1L),
                currentEntryId = "duplicate-2",
                positionMs = 2_345L,
                shouldPlay = true,
                shuffle = true,
                baseEntryIds = listOf("duplicate-2", "middle", "duplicate-1")
            )
        )
        val coordinator = coordinator(persistence, runtime)

        coordinator.initialize()

        assertEquals(
            listOf("duplicate-1", "middle", "duplicate-2"),
            coordinator.captureActiveQueueSnapshot()?.baseEntryIds
        )
        assertEquals(0, runtime.replaceCount)
    }

    @Test
    fun newAlbumContextPreservesLegacyOwnerAndReplacesWorkingCanonicalBaseOrder() = runBlocking {
        val persistence = FakePersistence(activeQueueId = "A").apply {
            seed(queue(
                id = "A",
                specs = listOf(
                    spec("old-c", 3L, base = 0, playback = 0),
                    spec("old-a", 1L, base = 1, playback = 1),
                    spec("old-d", 4L, base = 2, playback = 2),
                    spec("old-b", 2L, base = 3, playback = 3)
                ),
                current = "old-c",
                shuffle = true
            ))
        }
        val oldTimeline = liveSnapshot(
            ids = listOf("old-c" to 3L, "old-a" to 1L, "old-d" to 4L, "old-b" to 2L),
            currentEntryId = "old-c",
            positionMs = 500L,
            shouldPlay = true,
            shuffle = true,
            baseEntryIds = listOf("old-c", "old-a", "old-d", "old-b")
        )
        val runtime = FakeRuntime(oldTimeline)
        val coordinator = coordinator(persistence, runtime)
        coordinator.initialize()
        val legacy = persistence.queue("A")
        val canonicalBase = listOf("new-a", "new-b", "new-c", "new-d")

        coordinator.prepareNewPlaybackContext(canonicalBase)

        assertEquals(
            listOf("old-c", "old-a", "old-d", "old-b"),
            coordinator.captureActiveQueueSnapshot()?.baseEntryIds
        )
        runtime.snapshot = liveSnapshot(
            ids = listOf("new-c" to 3L, "new-a" to 1L, "new-d" to 4L, "new-b" to 2L),
            currentEntryId = "new-c",
            positionMs = 0L,
            shouldPlay = true,
            shuffle = true,
            baseEntryIds = listOf("new-c", "new-a", "new-d", "new-b")
        )

        assertEquals(canonicalBase, coordinator.captureActiveQueueSnapshot()?.baseEntryIds)
        coordinator.persistActiveQueueSnapshot()

        val workingId = requireNotNull(coordinator.getActiveQueueId())
        assertEquals("generated-queue", workingId)
        assertEquals(PlaybackQueueWorkingOutput.ANDROID, persistence.queue(workingId).queue.workingOutput)
        assertEquals(legacy, persistence.queue("A"))
        val shuffled = persistence.queue(workingId)
        assertEquals(
            canonicalBase,
            shuffled.entries.sortedBy(PlaybackQueueEntryEntity::baseOrder)
                .map(PlaybackQueueEntryEntity::entryId)
        )
        assertEquals(
            listOf("new-c", "new-a", "new-d", "new-b"),
            shuffled.entries.map(PlaybackQueueEntryEntity::entryId)
        )
        assertTrue(shuffled.queue.shuffleEnabled)

        runtime.snapshot = liveSnapshot(
            ids = listOf("new-a" to 1L, "new-b" to 2L, "new-c" to 3L, "new-d" to 4L),
            currentEntryId = "new-c",
            positionMs = 7_654L,
            shouldPlay = true,
            shuffle = false,
            baseEntryIds = canonicalBase
        )
        coordinator.persistActiveQueueSnapshot()

        val restored = persistence.queue(workingId)
        assertEquals(workingId, coordinator.getActiveQueueId())
        assertEquals(2, persistence.queues.size)
        assertEquals(legacy, persistence.queue("A"))
        assertEquals(canonicalBase, restored.entries.map(PlaybackQueueEntryEntity::entryId))
        assertEquals(
            canonicalBase,
            restored.entries.sortedBy(PlaybackQueueEntryEntity::baseOrder)
                .map(PlaybackQueueEntryEntity::entryId)
        )
        assertFalse(restored.queue.shuffleEnabled)
        assertEquals("new-c", restored.queue.currentEntryId)
        assertEquals(7_654L, restored.queue.currentPositionMs)
    }

    @Test
    fun albumPlayPublishesCanonicalBaseAndPlaybackOrderWithShuffleOff() = runBlocking {
        val persistence = FakePersistence(activeQueueId = "A").apply {
            seed(queue(
                "A",
                listOf(spec("old-b", 2L, 0, 0), spec("old-a", 1L, 1, 1)),
                current = "old-b",
                shuffle = true
            ))
        }
        val runtime = FakeRuntime(
            liveSnapshot(listOf("old-b" to 2L, "old-a" to 1L), "old-b", 20L, true, true)
        )
        val coordinator = coordinator(persistence, runtime)
        coordinator.initialize()
        val legacy = persistence.queue("A")
        val canonicalBase = listOf("new-a", "new-b", "new-c")
        coordinator.prepareNewPlaybackContext(canonicalBase)
        runtime.snapshot = liveSnapshot(
            ids = listOf("new-a" to 1L, "new-b" to 2L, "new-c" to 3L),
            currentEntryId = "new-a",
            positionMs = 0L,
            shouldPlay = true,
            shuffle = false,
            baseEntryIds = canonicalBase
        )

        coordinator.persistActiveQueueSnapshot()

        val workingId = requireNotNull(coordinator.getActiveQueueId())
        val albumPlay = persistence.queue(workingId)
        assertEquals(PlaybackQueueWorkingOutput.ANDROID, albumPlay.queue.workingOutput)
        assertEquals(legacy, persistence.queue("A"))
        assertEquals(2, persistence.queues.size)
        assertEquals(canonicalBase, albumPlay.entries.map(PlaybackQueueEntryEntity::entryId))
        assertEquals(listOf(0, 1, 2), albumPlay.entries.map(PlaybackQueueEntryEntity::baseOrder))
        assertEquals(listOf(0, 1, 2), albumPlay.entries.map(PlaybackQueueEntryEntity::playbackOrder))
        assertFalse(albumPlay.queue.shuffleEnabled)
    }

    @Test
    fun repeatedAndroidLibraryContextsReuseOneWorkingOwnerWithFreshDuplicateOccurrences() = runBlocking {
        val persistence = FakePersistence()
        val runtime = FakeRuntime(null)
        val coordinator = coordinator(persistence, runtime)
        coordinator.initialize()
        val contexts = listOf(
            listOf("album-first" to 1L, "album-second" to 2L),
            listOf("same-album-first" to 1L, "same-album-second" to 2L),
            listOf("other-album" to 3L, "other-album-last" to 4L),
            listOf("songs-tab" to 5L, "songs-tab-next" to 1L),
            listOf("artist-first" to 2L, "artist-duplicate" to 2L, "artist-last" to 6L)
        )

        contexts.forEachIndexed { index, entries ->
            val canonical = entries.map { it.first }
            coordinator.prepareNewPlaybackContext(canonical)
            runtime.snapshot = liveSnapshot(entries, entries.last().first, index * 100L, index % 2 == 0)

            assertEquals("generated-queue", coordinator.persistActiveQueueSnapshot())
            val working = persistence.queue("generated-queue")
            assertEquals(PlaybackQueueWorkingOutput.ANDROID, working.queue.workingOutput)
            assertEquals(canonical, working.entries.map { it.entryId })
            assertEquals(entries.map { it.second }, working.entries.map { it.trackIdentityId })
            assertEquals(entries.last().first, working.queue.currentEntryId)
            assertEquals(index * 100L, working.queue.currentPositionMs)
            assertEquals(1, persistence.queues.size)
            assertEquals(1, persistence.createCount)
            assertTrue(persistence.queues.values.none { it.queue.workingOutput == null })
        }
        assertEquals(0, runtime.replaceCount)
    }

    @Test
    fun managedWorkingOwnerRestoresAfterRestartAndSurvivesSavedActivationAndAnotherRestart() = runBlocking {
        val persistence = FakePersistence()
        val runtime = FakeRuntime(liveSnapshot(
            listOf("duplicate-first" to 1L, "duplicate-second" to 1L),
            "duplicate-second", 4321L, false, true, PersistedQueueRepeatMode.ALL,
            listOf("duplicate-second", "duplicate-first")
        ))
        val initial = coordinator(persistence, runtime)
        initial.initialize()
        val workingId = requireNotNull(initial.persistActiveQueueSnapshot())
        val working = persistence.queue(workingId)

        val restoredRuntime = FakeRuntime(null)
        val restored = coordinator(persistence, restoredRuntime)
        assertEquals(workingId, restored.initialize())
        assertEquals("duplicate-second", restoredRuntime.snapshot?.currentEntryId)
        assertEquals(4321L, restoredRuntime.snapshot?.currentPositionMs)
        assertEquals(working.entries.map { it.entryId }, restoredRuntime.snapshot?.entries?.map { it.entryId })
        assertEquals(PersistedQueueRepeatMode.ALL, restoredRuntime.snapshot?.repeatMode)
        assertEquals(workingId, persistence.activeQueueId)

        val saved = requireNotNull(restored.createQueueFromCurrent())
        assertEquals("Queue 2", saved.queue.displayName)
        assertNull(saved.queue.workingOutput)
        assertTrue(saved.entries.none { copy -> working.entries.any { it.entryId == copy.entryId } })
        assertTrue(restored.switchToQueue(saved.queue.queueId))
        val savedBeforeRestart = persistence.queue(saved.queue.queueId)

        val savedRuntime = FakeRuntime(null)
        val afterRestart = coordinator(persistence, savedRuntime)
        assertEquals(saved.queue.queueId, afterRestart.initialize())
        assertEquals(saved.queue.currentEntryId, savedRuntime.snapshot?.currentEntryId)
        afterRestart.prepareNewPlaybackContext(listOf("different-album"))
        savedRuntime.snapshot = liveSnapshot(listOf("different-album" to 9L), "different-album", 0L, true)

        assertEquals(workingId, afterRestart.persistActiveQueueSnapshot())
        assertEquals(savedBeforeRestart, persistence.queue(saved.queue.queueId))
        assertEquals(listOf("different-album"), persistence.queue(workingId).entries.map { it.entryId })
        assertEquals(2, persistence.queues.size)
        assertEquals(1, persistence.queues.values.count { it.queue.workingOutput == PlaybackQueueWorkingOutput.ANDROID })
        val secondSave = requireNotNull(afterRestart.createQueueFromCurrent())
        assertEquals("Queue 3", secondSave.queue.displayName)
        assertNull(secondSave.queue.workingOutput)
        assertEquals(workingId, persistence.activeQueueId)
        assertEquals(savedBeforeRestart, persistence.queue(saved.queue.queueId))
        assertEquals(3, persistence.queues.size)
    }

    @Test
    fun directLibrarySelectionCreatesWorkingOwnershipOnlyAfterNativeAcknowledgment() = runBlocking {
        val persistence = FakePersistence("saved").apply {
            seed(queue("saved", listOf(spec("saved-entry", 1L, 0, 0)), "saved-entry"))
        }
        val runtime = FakeRuntime(liveSnapshot(listOf("saved-entry" to 1L), "saved-entry", 0L, true)).apply {
            directOutput = true
        }
        val coordinator = coordinator(persistence, runtime)
        coordinator.initialize()
        val saved = persistence.queue("saved")
        val started = CompletableDeferred<Unit>()
        val acknowledgment = CompletableDeferred<Boolean>()
        runtime.beforeAcknowledgment = { started.complete(Unit); acknowledgment.await() }

        val selection = async { selectContext(coordinator, listOf("new-first" to 2L, "new-selected" to 2L), 1) }
        started.await()
        assertEquals(setOf("saved"), persistence.queues.keys)
        assertEquals("saved", persistence.activeQueueId)
        assertEquals(saved, persistence.queue("saved"))
        acknowledgment.complete(true)

        assertTrue(selection.await())
        val working = persistence.queue(requireNotNull(coordinator.getActiveQueueId()))
        assertEquals(PlaybackQueueWorkingOutput.DIRECT, working.queue.workingOutput)
        assertEquals("new-selected", working.queue.currentEntryId)
        assertEquals(listOf("new-first", "new-selected"), working.entries.map { it.entryId })
        assertEquals(listOf(2L, 2L), working.entries.map { it.trackIdentityId })
        assertEquals(saved, persistence.queue("saved"))
        assertEquals(2, persistence.queues.size)
    }

    @Test
    fun rejectedAndCancelledDirectSelectionsNeverAllocateWorkingRows() = runBlocking {
        val persistence = FakePersistence("saved").apply {
            seed(queue("saved", listOf(spec("saved-entry", 1L, 0, 0)), "saved-entry"))
        }
        val runtime = FakeRuntime(liveSnapshot(listOf("saved-entry" to 1L), "saved-entry", 0L, false)).apply {
            directOutput = true
            beforeAcknowledgment = { false }
        }
        val coordinator = coordinator(persistence, runtime)
        coordinator.initialize()
        val before = persistence.queues.toMap()
        assertFalse(selectContext(coordinator, listOf("rejected" to 2L), playing = false))
        assertEquals(before, persistence.queues)
        assertEquals("saved", persistence.activeQueueId)

        val started = CompletableDeferred<Unit>()
        val acknowledgment = CompletableDeferred<Boolean>()
        runtime.beforeAcknowledgment = { started.complete(Unit); acknowledgment.await() }
        val pending = async { selectContext(coordinator, listOf("cancelled" to 3L)) }
        started.await()
        pending.cancel()
        pending.join()

        assertEquals(before, persistence.queues)
        assertEquals("saved", persistence.activeQueueId)
        assertEquals(0, persistence.createCount)
        runtime.beforeAcknowledgment = null
        assertTrue(selectContext(coordinator, listOf("accepted-after-cancellation" to 4L)))
        assertEquals(2, persistence.queues.size)
    }

    @Test
    fun supersededDirectSelectionCannotCreateOrReplaceWorkingOwnership() = runBlocking {
        val persistence = FakePersistence("saved").apply {
            seed(queue("saved", listOf(spec("saved-entry", 1L, 0, 0)), "saved-entry"))
        }
        val runtime = FakeRuntime(liveSnapshot(listOf("saved-entry" to 1L), "saved-entry", 0L, true)).apply {
            directOutput = true
        }
        val coordinator = coordinator(persistence, runtime)
        coordinator.initialize()
        val saved = persistence.queue("saved")
        val started = Channel<String>(Channel.UNLIMITED)
        val firstReady = CompletableDeferred<Boolean>()
        val lastReady = CompletableDeferred<Boolean>()
        runtime.beforeAcknowledgment = { restoration ->
            started.send(restoration.currentEntryId)
            (if (restoration.currentEntryId == "obsolete") firstReady else lastReady).await()
        }
        val obsolete = async { selectContext(coordinator, listOf("obsolete" to 2L)) }
        assertEquals("obsolete", started.receive())
        val accepted = async { selectContext(coordinator, listOf("latest" to 3L), playing = false) }
        assertEquals("latest", started.receive())
        firstReady.complete(true)
        assertFalse(obsolete.await())
        assertEquals(setOf("saved"), persistence.queues.keys)
        lastReady.complete(true)

        assertTrue(accepted.await())
        assertEquals(listOf("latest"), persistence.queue("generated-queue").entries.map { it.entryId })
        assertEquals(saved, persistence.queue("saved"))
        assertEquals(2, persistence.queues.size)
        assertEquals(1, persistence.createCount)
        assertFalse(requireNotNull(runtime.snapshot).shouldPlay)
    }

    @Test
    fun librarySelectionSupersedesPendingSavedActivationAndRetainsBothSavedAndWorkingOwners() = runBlocking {
        val working = queue("working", listOf(spec("working-entry", 1L, 0, 0)), "working-entry")
            .asWorking(PlaybackQueueWorkingOutput.DIRECT)
        val saved = queue("saved", listOf(spec("saved-entry", 2L, 0, 0)), "saved-entry", 7654L)
        val persistence = FakePersistence("working").apply { seed(working); seed(saved) }
        val runtime = FakeRuntime(liveSnapshot(listOf("working-entry" to 1L), "working-entry", 0L, true)).apply {
            directOutput = true
        }
        val coordinator = coordinator(persistence, runtime)
        coordinator.initialize()
        val started = Channel<String>(Channel.UNLIMITED)
        val savedReady = CompletableDeferred<Boolean>()
        val libraryReady = CompletableDeferred<Boolean>()
        runtime.beforeAcknowledgment = { restoration ->
            started.send(restoration.currentEntryId)
            (if (restoration.currentEntryId == "saved-entry") savedReady else libraryReady).await()
        }

        val activation = async { coordinator.switchToQueue("saved") }
        assertEquals("saved-entry", started.receive())
        val selection = async { selectContext(coordinator, listOf("library-entry" to 3L)) }
        assertEquals("library-entry", started.receive())
        savedReady.complete(true)
        assertFalse(activation.await())
        assertEquals(saved, persistence.queue("saved"))
        assertEquals("working", persistence.activeQueueId)
        libraryReady.complete(true)

        assertTrue(selection.await())
        assertEquals(saved, persistence.queue("saved"))
        assertEquals("working", persistence.activeQueueId)
        assertEquals(listOf("library-entry"), persistence.queue("working").entries.map { it.entryId })
        assertEquals(PlaybackQueueWorkingOutput.DIRECT, persistence.queue("working").queue.workingOutput)
        assertEquals(2, persistence.queues.size)
        assertEquals(0, persistence.createCount)
    }

    @Test
    fun persistenceFailureAfterNativeAcknowledgmentRollsBackWorkingAllocationAndActiveIdentity() = runBlocking {
        val persistence = FakePersistence("saved").apply {
            seed(queue("saved", listOf(spec("saved-entry", 1L, 0, 0)), "saved-entry"))
            failNextWorkingCommit = true
        }
        val runtime = FakeRuntime(liveSnapshot(listOf("saved-entry" to 1L), "saved-entry", 0L, true)).apply {
            directOutput = true
        }
        val coordinator = coordinator(persistence, runtime)
        coordinator.initialize()
        val before = persistence.queues.toMap()

        val result = runCatching { selectContext(coordinator, listOf("acknowledged" to 2L)) }

        assertFalse(result.getOrDefault(false))
        assertEquals(1, runtime.replaceCount)
        assertEquals("acknowledged", runtime.snapshot?.currentEntryId)
        assertEquals(before, persistence.queues)
        assertEquals("saved", persistence.activeQueueId)
        assertEquals("saved", coordinator.getActiveQueueId())
        assertNull(coordinator.captureActiveQueueSnapshot())
        assertEquals(0, persistence.createCount)
    }

    @Test
    fun staleOutputEpochAtWorkingCommitRollsBackRowsMarkerAndActiveIdentity() = runBlocking {
        val persistence = FakePersistence("saved").apply {
            seed(queue("saved", listOf(spec("saved-entry", 1L, 0, 0)), "saved-entry"))
        }
        val runtime = FakeRuntime(liveSnapshot(listOf("saved-entry" to 1L), "saved-entry", 0L, true)).apply {
            directOutput = true
        }
        val coordinator = coordinator(persistence, runtime)
        coordinator.initialize()
        val before = persistence.queues.toMap()
        persistence.beforeWorkingCommitCompletion = { coordinator.onAndroidRequested() }

        assertFalse(selectContext(coordinator, listOf("obsolete-output" to 2L)))

        assertEquals(1, runtime.replaceCount)
        assertEquals(before, persistence.queues)
        assertEquals("saved", persistence.activeQueueId)
        assertEquals("saved", coordinator.getActiveQueueId())
        assertTrue(persistence.queues.values.none { it.queue.workingOutput != null })
        assertEquals(0, persistence.createCount)
    }

    @Test
    fun committedWorkingIdentityIsAdoptedWhenNewerIntentArrivesBeforePersistenceReturns() = runBlocking {
        val persistence = FakePersistence("saved").apply {
            seed(queue("saved", listOf(spec("saved-entry", 1L, 0, 0)), "saved-entry"))
        }
        val runtime = FakeRuntime(liveSnapshot(listOf("saved-entry" to 1L), "saved-entry", 0L, true)).apply {
            directOutput = true
        }
        val coordinator = coordinator(persistence, runtime)
        coordinator.initialize()
        val saved = persistence.queue("saved")
        persistence.afterWorkingCommit = {
            // The transaction already committed. This intent may supersede playback work,
            // but cannot make its durable active identity disappear from the coordinator.
            coordinator.prepareNewPlaybackContext(listOf("newest-entry"))
            persistence.afterWorkingCommit = null
        }

        assertFalse(selectContext(coordinator, listOf("committed-entry" to 2L)))

        assertEquals("generated-queue", persistence.activeQueueId)
        assertEquals(persistence.activeQueueId, coordinator.getActiveQueueId())
        assertEquals(listOf("committed-entry"), persistence.queue("generated-queue").entries.map { it.entryId })
        assertEquals(PlaybackQueueWorkingOutput.DIRECT, persistence.queue("generated-queue").queue.workingOutput)
        assertEquals(saved, persistence.queue("saved"))
        assertEquals(2, persistence.queues.size)

        assertTrue(selectContext(coordinator, listOf("newest-entry" to 3L)))

        assertEquals(persistence.activeQueueId, coordinator.getActiveQueueId())
        assertEquals("generated-queue", coordinator.getActiveQueueId())
        assertEquals(listOf("newest-entry"), persistence.queue("generated-queue").entries.map { it.entryId })
        assertEquals(saved, persistence.queue("saved"))
        assertEquals(2, persistence.queues.size)
        assertEquals(1, persistence.createCount)
    }

    @Test
    fun cancellationAfterWorkingCommitStillAdoptsDurableOwnerBeforeSelectionUnwinds() = runBlocking {
        val persistence = FakePersistence("saved").apply {
            seed(queue("saved", listOf(spec("saved-entry", 1L, 0, 0)), "saved-entry"))
        }
        val runtime = FakeRuntime(liveSnapshot(listOf("saved-entry" to 1L), "saved-entry", 0L, true)).apply {
            directOutput = true
        }
        val coordinator = coordinator(persistence, runtime)
        coordinator.initialize()
        val saved = persistence.queue("saved")
        val selection = async(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            selectContext(coordinator, listOf("committed-before-cancellation" to 2L))
        }
        persistence.afterWorkingCommit = {
            persistence.afterWorkingCommit = null
            selection.cancel()
        }

        selection.start()
        selection.join()

        assertTrue(selection.isCancelled)
        assertEquals("generated-queue", persistence.activeQueueId)
        assertEquals(persistence.activeQueueId, coordinator.getActiveQueueId())
        assertEquals(
            listOf("committed-before-cancellation"),
            persistence.queue("generated-queue").entries.map { it.entryId }
        )
        assertEquals(saved, persistence.queue("saved"))
        assertEquals(2, persistence.queues.size)

        assertTrue(selectContext(coordinator, listOf("next-accepted" to 3L)))
        assertEquals("generated-queue", coordinator.getActiveQueueId())
        assertEquals(persistence.activeQueueId, coordinator.getActiveQueueId())
        assertEquals(listOf("next-accepted"), persistence.queue("generated-queue").entries.map { it.entryId })
        assertEquals(saved, persistence.queue("saved"))
        assertEquals(2, persistence.queues.size)
        assertEquals(1, persistence.createCount)
    }

    @Test
    fun directOwnerColdRestoredOnAndroidBecomesProtectedWhileManagedRolesSwapWithoutAllocation() = runBlocking {
        val directId = "previous-direct"
        val androidId = "previous-android"
        val direct = queue(directId, listOf(spec("frozen-entry", 1L, 0, 0)), "frozen-entry", 1234L)
            .asWorking(PlaybackQueueWorkingOutput.DIRECT)
        val android = queue(androidId, listOf(spec("older-android-entry", 2L, 0, 0)), "older-android-entry")
            .asWorking(PlaybackQueueWorkingOutput.ANDROID)
        val persistence = FakePersistence(directId).apply { seed(direct); seed(android) }
        val runtime = FakeRuntime(null)
        val coordinator = coordinator(persistence, runtime)
        assertEquals(directId, coordinator.initialize())
        val frozenAndroid = requireNotNull(runtime.snapshot)
        assertEquals("frozen-entry", frozenAndroid.currentEntryId)
        assertTrue(coordinator.onDirectRequested())
        runtime.directOutput = true
        coordinator.beforeDirectRelease()

        assertTrue(selectContext(coordinator, listOf("new-direct-entry" to 3L)))

        assertEquals(androidId, persistence.activeQueueId)
        assertEquals(PlaybackQueueWorkingOutput.DIRECT, persistence.queue(androidId).queue.workingOutput)
        assertEquals(listOf("new-direct-entry"), persistence.queue(androidId).entries.map { it.entryId })
        assertEquals(direct.entries, persistence.queue(directId).entries)
        assertEquals("frozen-entry", persistence.queue(directId).queue.currentEntryId)
        assertEquals(1234L, persistence.queue(directId).queue.currentPositionMs)
        assertEquals(PlaybackQueueWorkingOutput.ANDROID, persistence.queue(directId).queue.workingOutput)
        assertEquals(2, persistence.queues.size)
        assertEquals(0, persistence.createCount)

        coordinator.onAndroidRequested()
        coordinator.beforeAndroidRestore()
        runtime.directOutput = false
        runtime.snapshot = frozenAndroid
        coordinator.afterAndroidRestore()

        assertEquals(directId, persistence.activeQueueId)
        assertEquals("frozen-entry", coordinator.captureActiveQueueSnapshot()?.currentEntryId)
        assertEquals(1234L, coordinator.captureActiveQueueSnapshot()?.currentPositionMs)
        assertEquals(listOf("new-direct-entry"), persistence.queue(androidId).entries.map { it.entryId })
        assertEquals(2, persistence.queues.size)
    }

    @Test
    fun restoredUnassignedAndroidSnapshotAllowsRepeatedActivationWithoutRewritingItsSavedOwner() = runBlocking {
        val persistence = FakePersistence("saved").apply {
            seed(queue("saved", listOf(spec("a", 1L, 0, 0), spec("b", 2L, 1, 1),
                spec("c", 3L, 2, 2)), "a", 1234L, repeat = PersistedQueueRepeatMode.ALL))
        }
        val original = liveSnapshot(listOf("a" to 1L, "b" to 2L, "c" to 3L),
            "a", 1234L, true, repeat = PersistedQueueRepeatMode.ALL)
        val runtime = FakeRuntime(original)
        val coordinator = coordinator(persistence, runtime)
        coordinator.initialize()
        assertTrue(coordinator.onDirectRequested())
        runtime.directOutput = true
        coordinator.beforeDirectRelease()
        // An admitted Direct Queue edit changes the saved structure, while Android retains
        // its approved physical snapshot. This is the production path into unassigned restore.
        assertTrue(coordinator.reorderEntry("saved", "c", 1))
        assertEquals(listOf("a", "c", "b"), persistence.queue("saved").entries.map { it.entryId })
        coordinator.onAndroidRequested()
        coordinator.beforeAndroidRestore()
        runtime.directOutput = false
        runtime.snapshot = original.copy(shouldPlay = false)
        coordinator.afterAndroidRestore()
        val protectedQueue = persistence.queue("saved")
        val replacements = persistence.replaceEntriesCount

        repeat(3) { cycle ->
            assertNull(coordinator.getActiveQueueId())
            assertNull(persistence.activeQueueId)
            assertNull(coordinator.captureActiveQueueSnapshot())
            // Ordinary Android playback can proceed without assigning these old occurrences
            // to the edited saved Queue or inserting them into a new managed owner.
            val android = original.copy(currentPositionMs = 2000L + cycle, shouldPlay = cycle % 2 == 0)
            runtime.snapshot = android
            assertNull(coordinator.persistActiveQueueSnapshot())
            assertNull(coordinator.createQueueFromCurrent())
            assertTrue(coordinator.onDirectRequested())
            runtime.directOutput = true
            coordinator.beforeDirectRelease() // Includes a supplied frozen Android checkpoint.
            assertNull(coordinator.persistActiveQueueSnapshot())
            assertEquals(protectedQueue, persistence.queue("saved"))
            coordinator.onAndroidRequested()
            coordinator.beforeAndroidRestore() // Includes the adopted Direct checkpoint.
            runtime.directOutput = false
            runtime.snapshot = android.copy(shouldPlay = false)
            coordinator.afterAndroidRestore()

            assertEquals(android.copy(shouldPlay = false), runtime.snapshot)
            assertEquals(protectedQueue, persistence.queue("saved"))
            assertEquals(replacements, persistence.replaceEntriesCount)
            assertEquals(0, persistence.createCount)
            assertEquals(setOf("saved"), persistence.queues.keys)
        }
        assertTrue(coordinator.onDirectRequested())
        coordinator.onDirectAbandoned()
    }

    @Test
    fun acknowledgedFreshDirectContextOwnsOneWorkingQueueWhileOriginalUnassignedRestoreStaysProtected() = runBlocking {
        val persistence = FakePersistence("saved").apply {
            seed(queue("saved", listOf(spec("a", 1L, 0, 0), spec("b", 2L, 1, 1),
                spec("c", 3L, 2, 2)), "a", 1234L, repeat = PersistedQueueRepeatMode.ONE))
        }
        val original = liveSnapshot(listOf("a" to 1L, "b" to 2L, "c" to 3L),
            "a", 1234L, false, repeat = PersistedQueueRepeatMode.ONE)
        val runtime = FakeRuntime(original)
        val coordinator = coordinator(persistence, runtime)
        coordinator.initialize()
        assertTrue(coordinator.onDirectRequested())
        runtime.directOutput = true
        coordinator.beforeDirectRelease()
        assertTrue(coordinator.reorderEntry("saved", "c", 1))
        coordinator.onAndroidRequested()
        coordinator.beforeAndroidRestore()
        runtime.directOutput = false
        runtime.snapshot = original
        coordinator.afterAndroidRestore()
        val protectedQueue = persistence.queue("saved")

        repeat(2) { cycle ->
            assertTrue(coordinator.onDirectRequested())
            runtime.directOutput = true
            coordinator.beforeDirectRelease()
            val started = CompletableDeferred<Unit>()
            val acknowledgment = CompletableDeferred<Boolean>()
            runtime.beforeAcknowledgment = { started.complete(Unit); acknowledgment.await() }
            val entryId = "fresh-$cycle"
            val selection = async { selectContext(coordinator, listOf(entryId to 2L), playing = cycle == 0) }
            started.await()
            assertNull(coordinator.captureActiveQueueSnapshot())
            assertNull(coordinator.getActiveQueueId())
            assertFalse(coordinator.onDirectRequested())
            assertEquals(protectedQueue, persistence.queue("saved"))
            assertEquals(cycle, persistence.queues.values.count { it.queue.workingOutput != null })
            acknowledgment.complete(true)
            assertTrue(selection.await())
            runtime.beforeAcknowledgment = null

            assertEquals("generated-queue", coordinator.getActiveQueueId())
            assertEquals(entryId, coordinator.captureActiveQueueSnapshot()?.currentEntryId)
            assertEquals(PlaybackQueueWorkingOutput.DIRECT, persistence.queue("generated-queue").queue.workingOutput)
            runtime.snapshot = requireNotNull(runtime.snapshot).copy(currentPositionMs = 3456L)
            assertEquals("generated-queue", coordinator.persistActiveQueueSnapshot())
            val direct = persistence.queue("generated-queue")
            assertEquals(3456L, direct.queue.currentPositionMs)
            assertEquals(protectedQueue, persistence.queue("saved"))

            coordinator.onAndroidRequested()
            coordinator.beforeAndroidRestore()
            runtime.directOutput = false
            runtime.snapshot = original
            coordinator.afterAndroidRestore()
            assertNull(coordinator.getActiveQueueId())
            assertNull(coordinator.captureActiveQueueSnapshot())
            assertNull(coordinator.persistActiveQueueSnapshot())
            assertEquals(original, runtime.snapshot)
            assertEquals(protectedQueue, persistence.queue("saved"))
            assertEquals(direct, persistence.queue("generated-queue"))
            assertEquals(1, persistence.createCount)
            assertEquals(1, persistence.queues.values.count { it.queue.workingOutput == null })
            assertEquals(2, persistence.queues.size)
        }
        assertTrue(coordinator.onDirectRequested())
        coordinator.onDirectAbandoned()
    }

    @Test
    fun staleOrCancelledActivationAfterOutputRecoveryCannotClearANewerPendingTransaction() = runBlocking {
        for (cancelOld in listOf(false, true)) {
            val persistence = FakePersistence("A").apply {
                seed(queue("A", listOf(spec("a", 1L, 0, 0)), "a", 1234L))
                seed(queue("B", listOf(spec("b", 2L, 0, 0)), "b"))
                seed(queue("C", listOf(spec("c", 3L, 0, 0)), "c"))
            }
            val original = liveSnapshot(listOf("a" to 1L), "a", 1234L, false)
            val runtime = FakeRuntime(original)
            val coordinator = coordinator(persistence, runtime)
            coordinator.initialize()
            assertTrue(coordinator.onDirectRequested())
            runtime.directOutput = true
            coordinator.beforeDirectRelease()
            val started = Channel<String>(Channel.UNLIMITED)
            val oldAcknowledgment = CompletableDeferred<Boolean>()
            val newAcknowledgment = CompletableDeferred<Boolean>()
            runtime.beforeAcknowledgment = { restoration ->
                started.send(restoration.queueId)
                (if (restoration.queueId == "B") oldAcknowledgment else newAcknowledgment).await()
            }
            val old = async { coordinator.switchToQueue("B") }
            assertEquals("B", started.receive())
            assertFalse(coordinator.onDirectRequested())
            coordinator.onAndroidRequested()
            coordinator.beforeAndroidRestore()
            runtime.directOutput = false
            runtime.snapshot = original
            coordinator.afterAndroidRestore()

            assertTrue(coordinator.onDirectRequested())
            runtime.directOutput = true
            coordinator.beforeDirectRelease()
            val newer = async { coordinator.switchToQueue("C") }
            assertEquals("C", started.receive())
            assertFalse(coordinator.onDirectRequested())
            if (cancelOld) {
                old.cancel()
                old.join()
                assertTrue(old.isCancelled)
            } else {
                oldAcknowledgment.complete(true)
                assertFalse(old.await())
            }
            assertFalse(newer.isCompleted)
            assertFalse(coordinator.onDirectRequested())
            assertNull(coordinator.captureActiveQueueSnapshot())
            assertEquals("A", persistence.activeQueueId)
            newAcknowledgment.complete(true)
            assertTrue(newer.await())
            assertEquals("C", coordinator.getActiveQueueId())
            assertEquals("c", coordinator.captureActiveQueueSnapshot()?.currentEntryId)
            runtime.beforeAcknowledgment = null

            coordinator.onAndroidRequested()
            coordinator.beforeAndroidRestore()
            runtime.directOutput = false
            runtime.snapshot = original
            coordinator.afterAndroidRestore()
            assertEquals("A", coordinator.getActiveQueueId())
            assertEquals(original, coordinator.captureActiveQueueSnapshot())
            assertTrue(coordinator.onDirectRequested())
            coordinator.onDirectAbandoned()
        }
    }

    @Test
    fun liveTimelineThatAppearsDuringStartupResolutionWinsTheInitializationRace() = runBlocking {
        val persistence = FakePersistence(activeQueueId = "A").apply {
            seed(queue("A", listOf(spec("a", 1L, 0, 0)), current = "a"))
        }
        val runtime = FakeRuntime(null)
        val live = liveSnapshot(listOf("live" to 9L), "live", 77L, shouldPlay = true)
        val access = FakeTrackAccess().apply {
            beforeResolve = { runtime.snapshot = live }
        }

        coordinator(persistence, runtime, access).initialize()

        assertEquals(0, runtime.replaceCount)
        assertEquals(live, runtime.snapshot)
    }

    @Test
    fun initializationRestoresPersistedActiveQueueIntoAnEmptyRuntimePaused() = runBlocking {
        val persistence = FakePersistence(activeQueueId = "A").apply {
            seed(queue(
                id = "A",
                specs = listOf(spec("a", 1L, 0, 0)),
                current = "a",
                position = 654L,
                shuffle = true,
                repeat = PersistedQueueRepeatMode.ONE
            ))
        }
        val runtime = FakeRuntime(null)

        assertEquals("A", coordinator(persistence, runtime).initialize())

        val restored = requireNotNull(runtime.lastRestoration)
        assertEquals("a", restored.currentEntryId)
        assertEquals(654L, restored.currentPositionMs)
        assertFalse(restored.shouldPlay)
        assertTrue(restored.shuffleEnabled)
        assertEquals(PersistedQueueRepeatMode.ONE, restored.repeatMode)
        assertEquals(1, runtime.replaceCount)
    }

    @Test
    fun switchingAwayAndBackRestoresPositionExactShuffleOrderBaseOrderRepeatAndDuplicates() =
        runBlocking {
            val queueA = queue(
                id = "A",
                specs = listOf(
                    spec("a0", 1L, base = 0, playback = 1),
                    spec("a1", 1L, base = 1, playback = 2),
                    spec("a2", 2L, base = 2, playback = 0)
                ),
                current = "a0",
                position = 12_345L,
                shuffle = true,
                repeat = PersistedQueueRepeatMode.ALL
            )
            val queueB = queue(
                id = "B",
                specs = listOf(spec("b0", 3L, 0, 0)),
                current = "b0",
                position = 55L,
                repeat = PersistedQueueRepeatMode.ONE
            )
            val persistence = FakePersistence(activeQueueId = "A").apply {
                seed(queueA)
                seed(queueB)
            }
            val runtime = FakeRuntime(
                liveSnapshot(
                    ids = listOf("a2" to 2L, "a0" to 1L, "a1" to 1L),
                    currentEntryId = "a0",
                    positionMs = 12_345L,
                    shouldPlay = true,
                    shuffle = true,
                    repeat = PersistedQueueRepeatMode.ALL
                )
            )
            val coordinator = coordinator(persistence, runtime)
            coordinator.initialize()

            assertTrue(coordinator.switchToQueue("B"))
            assertEquals(listOf("b0"), runtime.lastRestoration?.entries?.map {
                it.persistedEntry.entryId
            })
            assertEquals(PersistedQueueRepeatMode.ONE, runtime.lastRestoration?.repeatMode)
            assertTrue(coordinator.switchToQueue("A"))

            val restoredA = requireNotNull(runtime.lastRestoration)
            assertEquals(listOf("a2", "a0", "a1"), restoredA.entries.map {
                it.persistedEntry.entryId
            })
            assertEquals(listOf(2, 0, 1), restoredA.entries.map {
                it.persistedEntry.baseOrder
            })
            assertEquals("a0", restoredA.currentEntryId)
            assertEquals(12_345L, restoredA.currentPositionMs)
            assertTrue(restoredA.shuffleEnabled)
            assertEquals(PersistedQueueRepeatMode.ALL, restoredA.repeatMode)
            assertEquals(listOf(1L, 1L), restoredA.entries.filter {
                it.song.id == 1L
            }.map { it.song.id })
            assertEquals("A", persistence.activeQueueId)
            assertEquals(2, runtime.replaceCount)
            assertEquals(2, runtime.prepareCount)
            assertEquals(2, runtime.playCount)
        }

    @Test
    fun switchPreservesPausedAndPlayingIntentWithoutDuplicatePrepareOrPlay() = runBlocking {
        val pausedPersistence = FakePersistence(activeQueueId = "A").apply {
            seed(queue("A", listOf(spec("a", 1L, 0, 0)), current = "a"))
            seed(queue("B", listOf(spec("b", 2L, 0, 0)), current = "b"))
        }
        val pausedRuntime = FakeRuntime(
            liveSnapshot(listOf("a" to 1L), "a", 1L, shouldPlay = false)
        )
        val pausedCoordinator = coordinator(pausedPersistence, pausedRuntime)
        pausedCoordinator.initialize()
        assertTrue(pausedCoordinator.switchToQueue("B"))
        assertFalse(requireNotNull(pausedRuntime.snapshot).shouldPlay)
        assertEquals(1, pausedRuntime.prepareCount)
        assertEquals(0, pausedRuntime.playCount)

        val playingPersistence = FakePersistence(activeQueueId = "A").apply {
            seed(queue("A", listOf(spec("a", 1L, 0, 0)), current = "a"))
            seed(queue("B", listOf(spec("b", 2L, 0, 0)), current = "b"))
        }
        val playingRuntime = FakeRuntime(
            liveSnapshot(listOf("a" to 1L), "a", 1L, shouldPlay = true)
        )
        val playingCoordinator = coordinator(playingPersistence, playingRuntime)
        playingCoordinator.initialize()
        assertTrue(playingCoordinator.switchToQueue("B"))
        assertTrue(requireNotNull(playingRuntime.snapshot).shouldPlay)
        assertEquals(1, playingRuntime.prepareCount)
        assertEquals(1, playingRuntime.playCount)
    }

    @Test
    fun runtimeMutationsPersistPlaybackAndBaseOrderWhileStateOnlyCheckpointsStayLightweight() =
        runBlocking {
            val persistence = FakePersistence(activeQueueId = "A").apply {
                seed(queue(
                    id = "A",
                    specs = listOf(
                        spec("a", 1L, base = 0, playback = 0),
                        spec("b", 2L, base = 1, playback = 1),
                        spec("c", 3L, base = 2, playback = 2)
                    ),
                    current = "a"
                ))
            }
            val runtime = FakeRuntime(
                liveSnapshot(
                    ids = listOf("c" to 3L, "a" to 1L, "b" to 2L),
                    currentEntryId = "c",
                    positionMs = 400L,
                    shouldPlay = true,
                    shuffle = true,
                    repeat = PersistedQueueRepeatMode.ONE,
                    baseEntryIds = listOf("a", "b", "c")
                )
            )
            val coordinator = coordinator(persistence, runtime)
            coordinator.initialize()

            coordinator.persistActiveQueueSnapshot()

            assertEquals(listOf("c", "a", "b"), persistence.queue("A").entries.map {
                it.entryId
            })
            assertEquals(listOf(2, 0, 1), persistence.queue("A").entries.map {
                it.baseOrder
            })
            assertEquals("c", persistence.queue("A").queue.currentEntryId)
            assertEquals(400L, persistence.queue("A").queue.currentPositionMs)
            assertTrue(persistence.queue("A").queue.shuffleEnabled)
            assertEquals(PersistedQueueRepeatMode.ONE, persistence.queue("A").queue.repeatMode)
            assertEquals(1, persistence.replaceEntriesCount)

            runtime.snapshot = runtime.snapshot?.copy(
                currentPositionMs = 800L,
                shuffleEnabled = false,
                repeatMode = PersistedQueueRepeatMode.ALL
            )
            coordinator.persistActiveQueueSnapshot()

            assertEquals(1, persistence.replaceEntriesCount)
            assertEquals(800L, persistence.queue("A").queue.currentPositionMs)
            assertFalse(persistence.queue("A").queue.shuffleEnabled)
            assertEquals(PersistedQueueRepeatMode.ALL, persistence.queue("A").queue.repeatMode)

            runtime.snapshot = runtime.snapshot?.copy(
                entries = listOf("c" to 3L, "b" to 2L, "a" to 1L).map { (entryId, trackId) ->
                    LivePlaybackQueueItem(entryId, evidence(entryId, trackId))
                },
                baseEntryIds = listOf("a", "b", "c")
            )
            coordinator.persistActiveQueueSnapshot()

            assertEquals(listOf("c", "b", "a"), persistence.queue("A").entries.map {
                it.entryId
            })
            assertEquals(listOf(2, 1, 0), persistence.queue("A").entries.map {
                it.baseOrder
            })
            assertEquals(2, persistence.replaceEntriesCount)
        }

    @Test
    fun shuffleOffCheckpointRestoresPlaybackOrderToBaseWithoutChangingCurrentOrPosition() =
        runBlocking {
            val persistence = FakePersistence(activeQueueId = "A").apply {
                seed(queue(
                    id = "A",
                    specs = listOf(
                        spec("a", 1L, base = 0, playback = 2),
                        spec("b", 2L, base = 1, playback = 0),
                        spec("c", 3L, base = 2, playback = 1)
                    ),
                    current = "b",
                    position = 7_654L,
                    shuffle = true
                ))
            }
            val runtime = FakeRuntime(
                liveSnapshot(
                    ids = listOf("b" to 2L, "c" to 3L, "a" to 1L),
                    currentEntryId = "b",
                    positionMs = 7_654L,
                    shouldPlay = true,
                    shuffle = true,
                    baseEntryIds = listOf("a", "b", "c")
                )
            )
            val coordinator = coordinator(persistence, runtime)
            coordinator.initialize()

            runtime.snapshot = liveSnapshot(
                ids = listOf("a" to 1L, "b" to 2L, "c" to 3L),
                currentEntryId = "b",
                positionMs = 7_654L,
                shouldPlay = true,
                shuffle = false,
                baseEntryIds = listOf("a", "b", "c")
            )
            coordinator.persistActiveQueueSnapshot()

            val restored = persistence.queue("A")
            assertEquals(listOf("a", "b", "c"), restored.entries.map { it.entryId })
            assertEquals(listOf(0, 1, 2), restored.entries.map { it.baseOrder })
            assertEquals(listOf(0, 1, 2), restored.entries.map { it.playbackOrder })
            assertEquals("b", restored.queue.currentEntryId)
            assertEquals(7_654L, restored.queue.currentPositionMs)
            assertFalse(restored.queue.shuffleEnabled)
        }

    @Test
    fun newQueueFromCurrentCopiesStateWithoutChangingPlaybackOrActiveQueue() = runBlocking {
        val persistence = FakePersistence(activeQueueId = "A").apply {
            seed(queue(
                id = "A",
                specs = listOf(
                    spec("duplicate-1", 1L, base = 0, playback = 1),
                    spec("duplicate-2", 1L, base = 1, playback = 0)
                ),
                current = "duplicate-2",
                position = 987L,
                shuffle = true,
                repeat = PersistedQueueRepeatMode.ONE
            ))
        }
        val runtime = FakeRuntime(
            liveSnapshot(
                ids = listOf("duplicate-2" to 1L, "duplicate-1" to 1L),
                currentEntryId = "duplicate-2",
                positionMs = 987L,
                shouldPlay = true,
                shuffle = true,
                repeat = PersistedQueueRepeatMode.ONE,
                baseEntryIds = listOf("duplicate-1", "duplicate-2")
            )
        )
        val coordinator = coordinator(persistence, runtime)
        coordinator.initialize()

        val copied = requireNotNull(coordinator.createQueueFromCurrent())

        assertEquals("Queue 2", copied.queue.displayName)
        assertEquals("A", persistence.activeQueueId)
        assertEquals(0, runtime.replaceCount)
        assertEquals(listOf(1, 0), copied.entries.map { it.baseOrder })
        assertEquals(listOf(0, 1), copied.entries.map { it.playbackOrder })
        assertEquals(2, copied.entries.size)
        assertEquals(1, copied.entries.map { it.trackIdentityId }.distinct().size)
        assertTrue(copied.entries.none { copiedEntry ->
            persistence.queue("A").entries.any { sourceEntry ->
                sourceEntry.entryId == copiedEntry.entryId
            }
        })
        assertEquals(987L, copied.queue.currentPositionMs)
        assertTrue(copied.queue.shuffleEnabled)
        assertEquals(PersistedQueueRepeatMode.ONE, copied.queue.repeatMode)

        val secondCopy = requireNotNull(coordinator.createQueueFromCurrent())
        assertEquals("Queue 3", secondCopy.queue.displayName)
        assertEquals("A", persistence.activeQueueId)
        assertEquals(0, runtime.replaceCount)
    }

    @Test
    fun generatedQueueNamesAdvancePastRenamedAndExistingGeneratedQueues() {
        assertEquals(2, nextDefaultQueueNumber(listOf("Morning")) { "Queue $it" })
        assertEquals(4, nextDefaultQueueNumber(listOf("Queue 1", "Queue 3")) { "Queue $it" })
        assertEquals(4, nextDefaultQueueNumber(listOf("Queue 1", "Queue 3")) { "Localized $it" })
        assertEquals(4, nextDefaultQueueNumber(listOf("Morning", "Localized 3")) { "Localized $it" })
    }

    @Test
    fun localizedNewQueueNameDoesNotRenamePersistedEnglishQueue() = runBlocking {
        val persistence = FakePersistence(activeQueueId = "Queue 1").apply {
            seed(queue("Queue 1", listOf(spec("one", 1L, 0, 0)), current = "one"))
        }
        val runtime = FakeRuntime(liveSnapshot(
            ids = listOf("one" to 1L), currentEntryId = "one",
            positionMs = 0L, shouldPlay = false
        ))
        val coordinator = coordinator(persistence, runtime) { number -> "Localized $number" }

        coordinator.initialize()
        val created = requireNotNull(coordinator.createQueueFromCurrent())

        assertEquals("Queue 1", persistence.queue("Queue 1").queue.displayName)
        assertEquals("Localized 2", created.queue.displayName)
    }

    @Test
    fun unresolvedEntriesAreSkippedAndMissingCurrentFallsForwardDeterministically() = runBlocking {
        val persistence = FakePersistence(activeQueueId = "A").apply {
            seed(queue("A", listOf(spec("a", 1L, 0, 0)), current = "a"))
            seed(queue(
                id = "B",
                specs = listOf(
                    spec("before", 2L, 0, 0),
                    spec("missing-current", 99L, 1, 1),
                    spec("after", 3L, 2, 2),
                    spec("also-missing", 100L, 3, 3)
                ),
                current = "missing-current",
                position = 9_999L
            ))
        }
        val runtime = FakeRuntime(
            liveSnapshot(listOf("a" to 1L), "a", 10L, shouldPlay = false)
        )
        val access = FakeTrackAccess().apply {
            unresolvedIdentityIds += setOf(99L, 100L)
        }
        val coordinator = coordinator(persistence, runtime, access)
        coordinator.initialize()

        assertTrue(coordinator.switchToQueue("B"))

        val restored = requireNotNull(runtime.lastRestoration)
        assertEquals(listOf("before", "after"), restored.entries.map {
            it.persistedEntry.entryId
        })
        assertEquals("after", restored.currentEntryId)
        assertEquals(0L, restored.currentPositionMs)
        assertEquals("B", persistence.activeQueueId)
    }

    @Test
    fun completelyUnresolvableTargetLeavesLiveTimelineAndActiveQueueUntouched() = runBlocking {
        val persistence = FakePersistence(activeQueueId = "A").apply {
            seed(queue("A", listOf(spec("a", 1L, 0, 0)), current = "a"))
            seed(queue("B", listOf(spec("missing", 99L, 0, 0)), current = "missing"))
        }
        val original = liveSnapshot(listOf("a" to 1L), "a", 42L, shouldPlay = true)
        val runtime = FakeRuntime(original)
        val access = FakeTrackAccess().apply { unresolvedIdentityIds += 99L }
        val coordinator = coordinator(persistence, runtime, access)
        coordinator.initialize()

        assertFalse(coordinator.switchToQueue("B"))

        assertEquals(0, runtime.replaceCount)
        assertEquals(original, runtime.snapshot)
        assertEquals("A", persistence.activeQueueId)
    }

    @Test
    fun createInactiveQueuePreservesSelectionOrderAndLeavesActivePlaybackUntouched() = runBlocking {
        val persistence = FakePersistence(activeQueueId = "A").apply {
            seed(queue("A", listOf(spec("active", 1L, 0, 0)), "active", position = 900L))
        }
        val original = liveSnapshot(
            ids = listOf("active" to 1L),
            currentEntryId = "active",
            positionMs = 900L,
            shouldPlay = true,
            shuffle = true,
            repeat = PersistedQueueRepeatMode.ONE
        )
        val runtime = FakeRuntime(original)
        val coordinator = coordinator(persistence, runtime)
        coordinator.initialize()

        val created = requireNotNull(
            coordinator.createInactiveQueue(listOf(song(3L), song(2L), song(3L)))
        )

        assertEquals("Queue 2", created.queue.displayName)
        assertEquals(listOf(3L, 2L, 3L), created.entries.map { it.trackIdentityId })
        assertEquals(listOf(0, 1, 2), created.entries.map { it.baseOrder })
        assertEquals(listOf(0, 1, 2), created.entries.map { it.playbackOrder })
        assertEquals(3, created.entries.map { it.entryId }.distinct().size)
        assertEquals(created.entries.first().entryId, created.queue.currentEntryId)
        assertEquals(0L, created.queue.currentPositionMs)
        assertFalse(created.queue.shuffleEnabled)
        assertEquals(PersistedQueueRepeatMode.OFF, created.queue.repeatMode)
        assertEquals("A", persistence.activeQueueId)
        assertEquals(listOf("active"), persistence.queue("A").entries.map { it.entryId })
        assertEquals(900L, persistence.queue("A").queue.currentPositionMs)
        assertEquals(original, runtime.snapshot)
        assertEquals(0, runtime.replaceCount)
    }

    @Test
    fun failedInactiveQueueResolutionCreatesNothingAndLeavesPlaybackUntouched() = runBlocking {
        val persistence = FakePersistence(activeQueueId = "A").apply {
            seed(queue("A", listOf(spec("active", 1L, 0, 0)), "active"))
        }
        val original = liveSnapshot(listOf("active" to 1L), "active", 400L, true)
        val runtime = FakeRuntime(original)
        val access = FakeTrackAccess().apply { unresolvedIdentityIds += 2L }
        val coordinator = coordinator(persistence, runtime, access)
        coordinator.initialize()

        assertNull(coordinator.createInactiveQueue(listOf(song(2L))))

        assertEquals(listOf("A"), persistence.queues.keys.toList())
        assertEquals("A", persistence.activeQueueId)
        assertEquals(original, runtime.snapshot)
        assertEquals(0, runtime.replaceCount)
    }

    @Test
    fun playInNewQueuePreservesOldQueueActivatesNewQueueAndKeepsSongOrder() = runBlocking {
        val persistence = FakePersistence(activeQueueId = "A").apply {
            seed(queue("A", listOf(spec("a", 1L, 0, 0)), current = "a", position = 900L))
        }
        val runtime = FakeRuntime(
            liveSnapshot(listOf("a" to 1L), "a", 900L, shouldPlay = false)
        )
        val coordinator = coordinator(persistence, runtime)
        coordinator.initialize()

        val created = requireNotNull(
            coordinator.createAndActivateQueue("Album", listOf(song(3L), song(2L), song(3L)))
        )

        assertEquals("generated-queue", persistence.activeQueueId)
        assertEquals("Album", created.queue.displayName)
        assertEquals(listOf(3L, 2L, 3L), runtime.lastRestoration?.entries?.map { it.song.id })
        assertEquals(3, created.entries.map { it.entryId }.distinct().size)
        assertEquals(900L, persistence.queue("A").queue.currentPositionMs)
        assertEquals(listOf("a"), persistence.queue("A").entries.map { it.entryId })
        assertEquals(1, runtime.playCount)
    }

    @Test
    fun singleSongNewQueueUsesPredictableGeneratedName() = runBlocking {
        val persistence = FakePersistence()
        val runtime = FakeRuntime(null)

        val created = requireNotNull(
            coordinator(persistence, runtime).createAndActivateQueue("", listOf(song(7L)))
        )

        assertEquals("Queue 1", created.queue.displayName)
        assertEquals(listOf(7L), runtime.lastRestoration?.entries?.map { it.song.id })
        assertTrue(runtime.lastRestoration?.shouldPlay == true)
    }

    @Test
    fun playlistNewQueuePreservesPreparedCustomOrder() = runBlocking {
        val persistence = FakePersistence()
        val runtime = FakeRuntime(null)

        val created = requireNotNull(
            coordinator(persistence, runtime).createAndActivateQueue(
                "Favorites",
                listOf(song(9L), song(4L), song(6L))
            )
        )

        assertEquals("Favorites", created.queue.displayName)
        assertEquals(listOf(9L, 4L, 6L), runtime.lastRestoration?.entries?.map { it.song.id })
    }

    @Test
    fun addingDuplicatesToInactiveQueueAppendsWithoutSwitchingOrTouchingTimeline() = runBlocking {
        val persistence = FakePersistence(activeQueueId = "A").apply {
            seed(queue("A", listOf(spec("a", 1L, 0, 0)), current = "a"))
            seed(queue("B", listOf(spec("b", 2L, 0, 0)), current = "b", position = 77L))
        }
        val runtime = FakeRuntime(liveSnapshot(listOf("a" to 1L), "a", 42L, true))
        val coordinator = coordinator(persistence, runtime)
        coordinator.initialize()

        assertTrue(coordinator.appendToInactiveQueue("B", listOf(song(3L), song(3L))) != null)

        assertEquals("A", persistence.activeQueueId)
        assertEquals(0, runtime.replaceCount)
        assertEquals(listOf(2L, 3L, 3L), persistence.queue("B").entries.map { it.trackIdentityId })
        assertEquals(3, persistence.queue("B").entries.map { it.entryId }.distinct().size)
        assertEquals("b", persistence.queue("B").queue.currentEntryId)
        assertEquals(77L, persistence.queue("B").queue.currentPositionMs)
    }

    @Test
    fun activeRemovalAndValidUpcomingReorderUseEntryIdsWithoutReplacingTimeline() = runBlocking {
        val persistence = FakePersistence(activeQueueId = "A").apply {
            seed(queue(
                "A",
                listOf(
                    spec("before", 1L, 0, 0),
                    spec("current", 2L, 1, 1),
                    spec("first-upcoming", 3L, 2, 2),
                    spec("dup", 2L, 3, 3)
                ),
                current = "current",
                position = 4_000L
            ))
        }
        val runtime = FakeRuntime(
            liveSnapshot(
                listOf(
                    "before" to 1L,
                    "current" to 2L,
                    "first-upcoming" to 3L,
                    "dup" to 2L
                ),
                "current",
                4_000L,
                true
            )
        )
        val coordinator = coordinator(persistence, runtime)
        coordinator.initialize()

        assertTrue(coordinator.removeEntry("A", "before"))
        assertEquals("current", runtime.snapshot?.currentEntryId)
        assertEquals(4_000L, runtime.snapshot?.currentPositionMs)
        assertEquals(
            listOf("current", "first-upcoming", "dup"),
            runtime.snapshot?.entries?.map { it.entryId }
        )
        assertTrue(coordinator.reorderEntry("A", "dup", 1))

        assertEquals("current", runtime.snapshot?.currentEntryId)
        assertEquals(4_000L, runtime.snapshot?.currentPositionMs)
        assertEquals(
            listOf("current", "dup", "first-upcoming"),
            runtime.snapshot?.entries?.map { it.entryId }
        )
        assertEquals(0, runtime.replaceCount)
        assertEquals(
            listOf("current", "dup", "first-upcoming"),
            persistence.queue("A").entries.map { it.entryId }
        )
    }

    @Test
    fun activeShuffledReorderRejectsCurrentAndUpcomingMovesAcrossCurrentBoundary() = runBlocking {
        val persistence = FakePersistence(activeQueueId = "A").apply {
            seed(queue(
                "A",
                listOf(
                    spec("current", 1L, 0, 0),
                    spec("upcoming-1", 2L, 1, 1),
                    spec("upcoming-2", 3L, 2, 2)
                ),
                current = "current",
                shuffle = true
            ))
        }
        val runtime = FakeRuntime(
            liveSnapshot(
                listOf("current" to 1L, "upcoming-1" to 2L, "upcoming-2" to 3L),
                "current",
                44L,
                true,
                shuffle = true
            )
        )
        val coordinator = coordinator(persistence, runtime)
        coordinator.initialize()

        assertFalse(coordinator.reorderEntry("A", "upcoming-2", 0))
        assertFalse(coordinator.reorderEntry("A", "current", 2))
        assertEquals(0, runtime.moveCount)
        assertEquals(
            listOf("current", "upcoming-1", "upcoming-2"),
            runtime.snapshot?.entries?.map { it.entryId }
        )
    }

    @Test
    fun activeShuffledReorderChangesPlaybackOnlyAndTargetsExactDuplicateOccurrence() =
        runBlocking {
            val persistence = FakePersistence(activeQueueId = "A").apply {
                seed(queue(
                    "A",
                    listOf(
                        spec("a-duplicate", 1L, base = 0, playback = 2),
                        spec("b-duplicate", 1L, base = 1, playback = 4),
                        spec("current", 2L, base = 2, playback = 0),
                        spec("d", 3L, base = 3, playback = 3),
                        spec("e", 4L, base = 4, playback = 1)
                    ),
                    current = "current",
                    position = 12_345L,
                    shuffle = true
                ))
            }
            val runtime = FakeRuntime(
                liveSnapshot(
                    ids = listOf(
                        "current" to 2L,
                        "e" to 4L,
                        "a-duplicate" to 1L,
                        "d" to 3L,
                        "b-duplicate" to 1L
                    ),
                    currentEntryId = "current",
                    positionMs = 12_345L,
                    shouldPlay = true,
                    shuffle = true,
                    baseEntryIds = listOf("a-duplicate", "b-duplicate", "current", "d", "e")
                )
            )
            val coordinator = coordinator(persistence, runtime)
            coordinator.initialize()

            assertTrue(coordinator.reorderEntry("A", "b-duplicate", 2))

            val stored = persistence.queue("A")
            assertEquals(
                listOf("current", "e", "b-duplicate", "a-duplicate", "d"),
                runtime.snapshot?.entries?.map { it.entryId }
            )
            assertEquals("current", runtime.snapshot?.currentEntryId)
            assertEquals(12_345L, runtime.snapshot?.currentPositionMs)
            assertEquals(
                listOf("current", "e", "b-duplicate", "a-duplicate", "d"),
                stored.entries.map { it.entryId }
            )
            assertEquals(
                listOf("a-duplicate", "b-duplicate", "current", "d", "e"),
                stored.entries.sortedBy { it.baseOrder }.map { it.entryId }
            )
            assertTrue(stored.queue.shuffleEnabled)
            assertEquals(1, runtime.moveCount)
            assertEquals(0, runtime.replaceCount)
        }

    @Test
    fun shuffledManualOrderSurvivesQueueRoundTripAndOffCheckpointRestoresCanonicalBase() =
        runBlocking {
            val persistence = FakePersistence(activeQueueId = "A").apply {
                seed(queue(
                    "A",
                    listOf(
                        spec("a", 1L, base = 0, playback = 2),
                        spec("b", 2L, base = 1, playback = 3),
                        spec("c", 3L, base = 2, playback = 0),
                        spec("d", 4L, base = 3, playback = 1)
                    ),
                    current = "c",
                    position = 7_654L,
                    shuffle = true
                ))
                seed(queue("B", listOf(spec("other", 5L, 0, 0)), current = "other"))
            }
            val runtime = FakeRuntime(liveSnapshot(
                ids = listOf("c" to 3L, "d" to 4L, "a" to 1L, "b" to 2L),
                currentEntryId = "c",
                positionMs = 7_654L,
                shouldPlay = true,
                shuffle = true,
                baseEntryIds = listOf("a", "b", "c", "d")
            ))
            val coordinator = coordinator(persistence, runtime)
            coordinator.initialize()

            assertTrue(coordinator.reorderEntry("A", "b", 2))
            assertEquals(
                listOf("c", "d", "b", "a"),
                persistence.queue("A").entries.map { it.entryId }
            )

            assertTrue(coordinator.switchToQueue("B"))
            assertTrue(coordinator.switchToQueue("A"))
            assertEquals(
                listOf("c", "d", "b", "a"),
                runtime.snapshot?.entries?.map { it.entryId }
            )
            assertEquals("c", runtime.snapshot?.currentEntryId)
            assertEquals(7_654L, runtime.snapshot?.currentPositionMs)

            runtime.snapshot = liveSnapshot(
                ids = listOf("a" to 1L, "b" to 2L, "c" to 3L, "d" to 4L),
                currentEntryId = "c",
                positionMs = 7_654L,
                shouldPlay = true,
                shuffle = false,
                baseEntryIds = listOf("a", "b", "c", "d")
            )
            coordinator.persistActiveQueueSnapshot()

            val unshuffled = persistence.queue("A")
            assertEquals(listOf("a", "b", "c", "d"), unshuffled.entries.map { it.entryId })
            assertEquals(
                listOf("a", "b", "c", "d"),
                unshuffled.entries.sortedBy { it.baseOrder }.map { it.entryId }
            )
            assertFalse(unshuffled.queue.shuffleEnabled)
            assertEquals("c", unshuffled.queue.currentEntryId)
            assertEquals(7_654L, unshuffled.queue.currentPositionMs)
        }

    @Test
    fun activeShuffledDragLetsManualPlacementOverridePlayNextPosition() = runBlocking {
        val persistence = FakePersistence(activeQueueId = "A").apply {
            seed(queue(
                "A",
                listOf(
                    spec("current", 1L, base = 0, playback = 0),
                    spec("play-next", 2L, base = 1, playback = 1),
                    spec("ordinary", 3L, base = 2, playback = 2)
                ),
                current = "current",
                shuffle = true
            ))
        }
        val runtime = FakeRuntime(liveSnapshot(
            ids = listOf("current" to 1L, "play-next" to 2L, "ordinary" to 3L),
            currentEntryId = "current",
            positionMs = 500L,
            shouldPlay = true,
            shuffle = true,
            baseEntryIds = listOf("current", "play-next", "ordinary")
        ))
        val coordinator = coordinator(persistence, runtime)
        coordinator.initialize()

        assertTrue(coordinator.reorderEntry("A", "play-next", 2))

        assertEquals(
            listOf("current", "ordinary", "play-next"),
            runtime.snapshot?.entries?.map { it.entryId }
        )
        assertEquals(
            listOf("current", "play-next", "ordinary"),
            persistence.queue("A").entries.sortedBy { it.baseOrder }.map { it.entryId }
        )
    }

    @Test
    fun inactiveReorderPersistsWithoutChangingLiveTimeline() = runBlocking {
        val persistence = FakePersistence(activeQueueId = "A").apply {
            seed(queue("A", listOf(spec("a", 1L, 0, 0)), current = "a"))
            seed(queue(
                "B",
                listOf(spec("b1", 2L, 0, 0), spec("b2", 3L, 1, 1)),
                current = "b1",
                position = 333L
            ))
        }
        val original = liveSnapshot(listOf("a" to 1L), "a", 44L, true)
        val runtime = FakeRuntime(original)
        val coordinator = coordinator(persistence, runtime)
        coordinator.initialize()

        assertTrue(coordinator.reorderEntry("B", "b2", 0))

        assertEquals(original, runtime.snapshot)
        assertEquals(listOf("b2", "b1"), persistence.queue("B").entries.map { it.entryId })
        assertEquals("b1", persistence.queue("B").queue.currentEntryId)
        assertEquals(333L, persistence.queue("B").queue.currentPositionMs)
    }

    @Test
    fun inactiveShuffledReorderChangesPlaybackOnlyAndPreservesResumeState() = runBlocking {
        val persistence = FakePersistence(activeQueueId = "A").apply {
            seed(queue("A", listOf(spec("a", 1L, 0, 0)), current = "a"))
            seed(queue(
                "B",
                listOf(
                    spec("b1", 2L, base = 0, playback = 1),
                    spec("b2", 3L, base = 1, playback = 2),
                    spec("b3", 4L, base = 2, playback = 0)
                ),
                current = "b1",
                position = 9_999L,
                shuffle = true
            ))
        }
        val original = liveSnapshot(listOf("a" to 1L), "a", 44L, true)
        val runtime = FakeRuntime(original)
        val coordinator = coordinator(persistence, runtime)
        coordinator.initialize()

        assertTrue(coordinator.reorderEntry("B", "b2", 1))

        val stored = persistence.queue("B")
        assertEquals(original, runtime.snapshot)
        assertEquals(listOf("b3", "b2", "b1"), stored.entries.map { it.entryId })
        assertEquals(
            listOf("b1", "b2", "b3"),
            stored.entries.sortedBy { it.baseOrder }.map { it.entryId }
        )
        assertEquals("b1", stored.queue.currentEntryId)
        assertEquals(9_999L, stored.queue.currentPositionMs)
        assertTrue(stored.queue.shuffleEnabled)
    }

    @Test
    fun removingCurrentEntryChoosesNextThenPreviousAndResetsPosition() = runBlocking {
        val persistence = FakePersistence(activeQueueId = "A").apply {
            seed(queue(
                "A",
                listOf(spec("before", 1L, 0, 0), spec("current", 2L, 1, 1), spec("after", 3L, 2, 2)),
                current = "current",
                position = 2_500L
            ))
        }
        val runtime = FakeRuntime(
            liveSnapshot(
                listOf("before" to 1L, "current" to 2L, "after" to 3L),
                "current",
                2_500L,
                true
            )
        )
        val coordinator = coordinator(persistence, runtime)
        coordinator.initialize()

        assertTrue(coordinator.removeEntry("A", "current"))
        assertEquals("after", runtime.snapshot?.currentEntryId)
        assertEquals(0L, runtime.snapshot?.currentPositionMs)
        assertTrue(coordinator.removeEntry("A", "after"))
        assertEquals("before", runtime.snapshot?.currentEntryId)
        assertEquals(0L, runtime.snapshot?.currentPositionMs)
    }

    private suspend fun selectContext(
        coordinator: PlaybackQueueCoordinator,
        entries: List<Pair<String, Long>>,
        index: Int = 0,
        playing: Boolean = true
    ): Boolean = coordinator.replacePlaybackContext(
        songs = entries.map { song(it.second) },
        items = entries.map { selectionItem(it.first, it.second) },
        index = index,
        positionMs = 0L,
        playing = playing,
        baseEntryIds = entries.map { it.first },
        shuffle = false,
        repeat = PersistedQueueRepeatMode.OFF
    )

    private fun coordinator(
        persistence: FakePersistence,
        runtime: FakeRuntime,
        access: FakeTrackAccess = FakeTrackAccess(),
        queueIdFactory: () -> String = { "generated-queue" },
        generatedQueueName: (Int) -> String = { "Queue $it" }
    ) = PlaybackQueueCoordinator(
        persistence = persistence,
        trackAccess = access,
        runtime = runtime,
        generatedQueueName = generatedQueueName,
        queueIdFactory = queueIdFactory
    )

    private class FakeRuntime(
        var snapshot: LivePlaybackQueueSnapshot?
    ) : PlaybackQueueRuntime {
        var activationAllowed = true
        var directOutput = false
        var checkpointAllowed=true
        var beforeAcknowledgment: (suspend (PlaybackQueueRestoration)->Boolean)?=null
        override fun canReplaceTimeline(): Boolean = activationAllowed
        override fun usesDirectOutput(): Boolean = directOutput
        var captureBackupRestoreCheckpoint = false
        private var backupRestoreCheckpoint: LivePlaybackQueueSnapshot? = null
        var replaceCount = 0
        var prepareCount = 0
        var playCount = 0
        var lastRestoration: PlaybackQueueRestoration? = null
        var seekedEntryId: String? = null
        var seekCount = 0
        var insertCount = 0
        var moveCount = 0

        override fun captureSnapshot(): LivePlaybackQueueSnapshot? = if(checkpointAllowed) snapshot else null
        override fun captureSelectionSnapshot(): LivePlaybackQueueSnapshot? = snapshot
        override suspend fun replaceTimelineAndAwait(restoration: PlaybackQueueRestoration): Boolean {
            val gate=beforeAcknowledgment
            if(gate!=null && !gate(restoration)) return false
            replaceTimeline(restoration)
            return true
        }

        override fun captureForBackupRestore(): BackupRestorePlaybackSnapshot? {
            if (!captureBackupRestoreCheckpoint) return null
            val captured = snapshot ?: return null
            backupRestoreCheckpoint = captured
            return BackupRestorePlaybackSnapshot(
                mediaItems = emptyList(),
                currentIndex = 0,
                currentPositionMs = captured.currentPositionMs,
                shouldPlay = captured.shouldPlay,
                shuffleEnabled = captured.shuffleEnabled,
                repeatMode = 0
            )
        }

        override fun clearForBackupRestore() {
            snapshot = null
        }

        override fun restoreAfterFailedBackupRestore(snapshot: BackupRestorePlaybackSnapshot) {
            this.snapshot = backupRestoreCheckpoint
        }

        override fun replaceTimeline(restoration: PlaybackQueueRestoration) {
            replaceCount += 1
            prepareCount += 1
            if (restoration.shouldPlay) playCount += 1
            lastRestoration = restoration
            snapshot = liveSnapshot(
                ids = restoration.entries
                    .sortedBy { it.persistedEntry.playbackOrder }
                    .map { it.persistedEntry.entryId to it.song.id },
                currentEntryId = restoration.currentEntryId,
                positionMs = restoration.currentPositionMs,
                shouldPlay = restoration.shouldPlay,
                shuffle = restoration.shuffleEnabled,
                repeat = restoration.repeatMode
            )
        }

        override fun removeEntry(entryId: String): Boolean {
            val before = snapshot ?: return false
            val index = before.entries.indexOfFirst { it.entryId == entryId }
            if (index < 0) return false
            val remaining = before.entries.filterNot { it.entryId == entryId }
            val newCurrent = if (before.currentEntryId == entryId) {
                remaining.getOrNull(index)?.entryId ?: remaining.lastOrNull()?.entryId
            } else {
                before.currentEntryId
            }
            snapshot = before.copy(
                entries = remaining,
                baseEntryIds = before.baseEntryIds.filterNot { it == entryId },
                currentEntryId = newCurrent,
                currentPositionMs = before.currentPositionMs.takeIf {
                    newCurrent == before.currentEntryId
                } ?: 0L
            )
            return true
        }

        override fun moveEntry(entryId: String, toPlaybackOrder: Int): Boolean {
            val before = snapshot ?: return false
            val entries = before.entries.toMutableList()
            val from = entries.indexOfFirst { it.entryId == entryId }
            if (from < 0 || toPlaybackOrder !in entries.indices) return false
            entries.add(toPlaybackOrder, entries.removeAt(from))
            snapshot = before.copy(entries = entries)
            moveCount += 1
            return true
        }

        override fun seekToEntry(entryId: String): Boolean {
            val before = snapshot ?: return false
            if (before.entries.none { it.entryId == entryId }) return false
            if (before.currentEntryId != entryId) {
                seekedEntryId = entryId
                seekCount += 1
                snapshot = before.copy(currentEntryId = entryId, currentPositionMs = 0L)
            }
            return true
        }

        override fun insertEntry(
            item: ResolvedPlaybackQueueItem,
            playbackOrder: Int
        ): Boolean {
            val before = snapshot ?: return false
            val entries = before.entries.toMutableList()
            entries.add(playbackOrder.coerceIn(0, entries.size), LivePlaybackQueueItem(
                item.persistedEntry.entryId,
                evidence(item.persistedEntry.entryId, item.song.id)
            ))
            snapshot = before.copy(entries = entries)
            insertCount += 1
            return true
        }
    }

    private class FakeTrackAccess : PlaybackQueueTrackAccess {
        val unresolvedIdentityIds = mutableSetOf<Long>()
        var beforeResolve: (() -> Unit)? = null
        var beforeIdentify: (() -> Unit)? = null

        override suspend fun identify(
            items: List<LivePlaybackQueueItem>
        ): List<IdentifiedLivePlaybackQueueItem> {
            beforeIdentify?.invoke()
            return items.mapNotNull { item ->
                val id = item.evidence.reference.mediaStoreId
                    ?: item.evidence.referenceKey.substringAfter("track-").toLong()
                if (id in unresolvedIdentityIds) null else IdentifiedLivePlaybackQueueItem(
                    liveItem = item,
                    trackIdentityId = id,
                    localTrackBindingId = id * 10L
                )
            }
        }

        override suspend fun resolve(
            entries: List<PlaybackQueueEntryEntity>
        ): List<ResolvedPlaybackQueueItem> {
            beforeResolve?.invoke()
            return entries.mapNotNull { entry ->
                if (entry.trackIdentityId in unresolvedIdentityIds) null else {
                    ResolvedPlaybackQueueItem(entry, song(entry.trackIdentityId))
                }
            }
        }
    }

    private class FakePersistence(
        var activeQueueId: String? = null
    ) : PlaybackQueuePersistence {
        val queues = linkedMapOf<String, PlaybackQueueWithEntries>()
        var createCount = 0
        var replaceEntriesCount = 0
        var failNextReplace = false
        var failNextWorkingCommit = false
        var beforeWorkingCommitCompletion: (suspend () -> Unit)? = null
        var afterWorkingCommit: (suspend () -> Unit)? = null

        fun seed(queue: PlaybackQueueWithEntries) {
            queues[queue.queue.queueId] = queue
        }

        fun queue(id: String): PlaybackQueueWithEntries = requireNotNull(queues[id])

        override suspend fun listQueues(): List<PlaybackQueueEntity> =
            queues.values.map(PlaybackQueueWithEntries::queue)

        override suspend fun getActiveQueueId(): String? = activeQueueId

        override suspend fun setActiveQueue(queueId: String?) {
            activeQueueId = queueId
        }

        override suspend fun loadQueue(queueId: String): PlaybackQueueWithEntries? =
            queues[queueId]?.ordered()

        override suspend fun createQueue(
            queueId: String,
            displayName: String,
            entries: List<PlaybackQueueEntryDraft>,
            currentEntryId: String?,
            currentPositionMs: Long,
            shuffleEnabled: Boolean,
            repeatMode: PersistedQueueRepeatMode
        ): PlaybackQueueWithEntries {
            createCount += 1
            val queue = PlaybackQueueWithEntries(
                queue = entity(
                    id = queueId,
                    name = displayName,
                    current = currentEntryId,
                    position = currentPositionMs,
                    shuffle = shuffleEnabled,
                    repeat = repeatMode
                ),
                entries = entries.map { it.entity(queueId) }
            )
            queues[queueId] = queue
            return queue.ordered()
        }

        override suspend fun commitWorkingQueue(
            output: PlaybackQueueWorkingOutput,
            queueId: String,
            displayName: String,
            entries: List<PlaybackQueueEntryDraft>,
            currentEntryId: String?,
            currentPositionMs: Long,
            shuffleEnabled: Boolean,
            repeatMode: PersistedQueueRepeatMode,
            protectedQueueId: String?,
            canCommit: () -> Boolean
        ): PlaybackQueueWithEntries? {
            if (!canCommit()) return null
            val before = queues.toMap()
            val previousActiveId = activeQueueId
            val owners = queues.values.filter { it.queue.workingOutput == output }
            check(owners.size <= 1)
            val desiredOwner = owners.singleOrNull()
            val protectedOwner = desiredOwner?.takeIf { it.queue.queueId == protectedQueueId }
            val otherOutput = if (output == PlaybackQueueWorkingOutput.ANDROID)
                PlaybackQueueWorkingOutput.DIRECT else PlaybackQueueWorkingOutput.ANDROID
            val otherOwners = queues.values.filter { it.queue.workingOutput == otherOutput }
            check(otherOwners.size <= 1)
            val existing = if (protectedOwner != null) otherOwners.singleOrNull() else desiredOwner
            val workingId = existing?.queue?.queueId ?: queueId
            check(existing != null || queueId !in queues)
            val committed = PlaybackQueueWithEntries(
                queue = (existing?.queue ?: entity(
                    workingId, displayName, currentEntryId, currentPositionMs, shuffleEnabled, repeatMode
                )).copy(
                    sourceType = PLAYBACK_QUEUE_WORKING_SOURCE_TYPE,
                    sourceKey = output.storageKey,
                    currentEntryId = currentEntryId,
                    currentPositionMs = currentPositionMs,
                    shuffleEnabled = shuffleEnabled,
                    repeatMode = repeatMode
                ),
                entries = entries.map { it.entity(workingId) }
            )
            fun rollback() {
                queues.clear()
                queues.putAll(before)
                activeQueueId = previousActiveId
            }
            val result = try {
                if (protectedOwner != null) {
                    queues[protectedOwner.queue.queueId] = protectedOwner.copy(
                        queue = protectedOwner.queue.copy(sourceKey = otherOutput.storageKey)
                    )
                }
                queues[workingId] = committed
                activeQueueId = workingId
                beforeWorkingCommitCompletion?.invoke()
                if (failNextWorkingCommit) {
                    failNextWorkingCommit = false
                    error("working queue commit failed")
                }
                if (!canCommit()) {
                    rollback()
                    return null
                }
                if (existing == null) createCount += 1 else replaceEntriesCount += 1
                committed.ordered()
            } catch (failure: Throwable) {
                rollback()
                throw failure
            }
            afterWorkingCommit?.invoke()
            return result
        }

        override suspend fun replaceEntries(
            queueId: String,
            entries: List<PlaybackQueueEntryDraft>,
            currentEntryId: String?,
            currentPositionMs: Long
        ): PlaybackQueueWithEntries {
            replaceEntriesCount += 1
            if (failNextReplace) {
                failNextReplace = false
                error("checkpoint failed")
            }
            val old = queue(queueId)
            val replacement = PlaybackQueueWithEntries(
                queue = old.queue.copy(
                    currentEntryId = currentEntryId,
                    currentPositionMs = currentPositionMs
                ),
                entries = entries.map { it.entity(queueId) }
            )
            queues[queueId] = replacement
            return replacement.ordered()
        }

        override suspend fun updateSavedPlaybackState(
            queueId: String,
            currentEntryId: String?,
            currentPositionMs: Long,
            shuffleEnabled: Boolean,
            repeatMode: PersistedQueueRepeatMode
        ) {
            val old = queue(queueId)
            queues[queueId] = old.copy(
                queue = old.queue.copy(
                    currentEntryId = currentEntryId,
                    currentPositionMs = currentPositionMs,
                    shuffleEnabled = shuffleEnabled,
                    repeatMode = repeatMode
                )
            )
        }

        override suspend fun duplicateQueue(
            sourceQueueId: String,
            displayName: String
        ): PlaybackQueueWithEntries {
            val source = queue(sourceQueueId)
            val queueId = "copy-${queues.size}"
            val copiedIds = source.entries.associate { entry ->
                entry.entryId to "$queueId-${entry.entryId}"
            }
            val copied = PlaybackQueueWithEntries(
                queue = source.queue.copy(
                    queueId = queueId,
                    displayName = displayName,
                    sourceType = null,
                    sourceKey = null,
                    currentEntryId = source.queue.currentEntryId?.let(copiedIds::get)
                ),
                entries = source.entries.map { entry ->
                    entry.copy(
                        entryId = checkNotNull(copiedIds[entry.entryId]),
                        queueId = queueId
                    )
                }
            )
            queues[queueId] = copied
            return copied.ordered()
        }

        override suspend fun appendEntries(
            queueId: String,
            entries: List<PlaybackQueueEntryDraft>
        ): PlaybackQueueWithEntries {
            val old = queue(queueId)
            val start = old.entries.size
            val appended = old.copy(
                entries = old.entries + entries.mapIndexed { index, entry ->
                    entry.copy(baseOrder = start + index, playbackOrder = start + index)
                        .entity(queueId)
                }
            )
            queues[queueId] = appended
            return appended.ordered()
        }

        override suspend fun removeEntry(
            queueId: String,
            entryId: String
        ): PlaybackQueueWithEntries? {
            val old = queues[queueId] ?: return null
            val remaining = old.entries.filterNot { it.entryId == entryId }
                .mapIndexed { index, entry ->
                    entry.copy(baseOrder = index, playbackOrder = index)
                }
            return old.copy(entries = remaining).also { queues[queueId] = it }.ordered()
        }

        override suspend fun reorderEntry(
            queueId: String,
            entryId: String,
            toPlaybackOrder: Int,
            updateBaseOrder: Boolean
        ): PlaybackQueueWithEntries? {
            val old = queues[queueId] ?: return null
            val entries = old.entries.sortedBy { it.playbackOrder }.toMutableList()
            val from = entries.indexOfFirst { it.entryId == entryId }
            if (from < 0 || toPlaybackOrder !in entries.indices) return old
            entries.add(toPlaybackOrder, entries.removeAt(from))
            val updated = old.copy(entries = entries.mapIndexed { index, entry ->
                entry.copy(
                    playbackOrder = index,
                    baseOrder = if (updateBaseOrder) index else entry.baseOrder
                )
            })
            queues[queueId] = updated
            return updated.ordered()
        }

        override suspend fun restoreEntry(
            queueId: String,
            entry: PlaybackQueueEntryEntity,
            restoredCurrentEntryId: String?,
            restoredCurrentPositionMs: Long?
        ): PlaybackQueueWithEntries? {
            val old = queues[queueId] ?: return null
            val entries = old.entries.filterNot { it.entryId == entry.entryId }.toMutableList()
            entries.add(entry.playbackOrder.coerceIn(0, entries.size), entry)
            val updated = old.copy(
                queue = old.queue.copy(
                    currentEntryId = restoredCurrentEntryId ?: old.queue.currentEntryId,
                    currentPositionMs = restoredCurrentPositionMs ?: old.queue.currentPositionMs
                ),
                entries = entries.mapIndexed { index, candidate ->
                    candidate.copy(playbackOrder = index, baseOrder = index)
                }
            )
            queues[queueId] = updated
            return updated.ordered()
        }

        private fun PlaybackQueueWithEntries.ordered() = copy(
            entries = entries.sortedBy(PlaybackQueueEntryEntity::playbackOrder)
        )
    }

    private data class EntrySpec(
        val entryId: String,
        val trackId: Long,
        val base: Int,
        val playback: Int
    )

    private companion object {
        fun spec(entryId: String, trackId: Long, base: Int, playback: Int) =
            EntrySpec(entryId, trackId, base, playback)

        fun queue(
            id: String,
            specs: List<EntrySpec>,
            current: String?,
            position: Long = 0L,
            shuffle: Boolean = false,
            repeat: PersistedQueueRepeatMode = PersistedQueueRepeatMode.OFF
        ) = PlaybackQueueWithEntries(
            queue = entity(id, id, current, position, shuffle, repeat),
            entries = specs.map { spec ->
                PlaybackQueueEntryEntity(
                    entryId = spec.entryId,
                    queueId = id,
                    trackIdentityId = spec.trackId,
                    localTrackBindingId = spec.trackId * 10L,
                    baseOrder = spec.base,
                    playbackOrder = spec.playback
                )
            }.sortedBy(PlaybackQueueEntryEntity::playbackOrder)
        )

        fun PlaybackQueueWithEntries.asWorking(output: PlaybackQueueWorkingOutput) = copy(
            queue = queue.copy(
                sourceType = PLAYBACK_QUEUE_WORKING_SOURCE_TYPE,
                sourceKey = output.storageKey
            )
        )

        fun entity(
            id: String,
            name: String,
            current: String?,
            position: Long,
            shuffle: Boolean,
            repeat: PersistedQueueRepeatMode
        ) = PlaybackQueueEntity(
            queueId = id,
            displayName = name,
            createdAt = 1L,
            updatedAt = 1L,
            lastActiveAt = 1L,
            sourceType = null,
            sourceKey = null,
            currentEntryId = current,
            currentPositionMs = position,
            shuffleEnabled = shuffle,
            repeatMode = repeat
        )

        fun PlaybackQueueEntryDraft.entity(queueId: String) = PlaybackQueueEntryEntity(
            entryId = entryId,
            queueId = queueId,
            trackIdentityId = trackIdentityId,
            localTrackBindingId = localTrackBindingId,
            baseOrder = baseOrder,
            playbackOrder = playbackOrder
        )

        fun liveSnapshot(
            ids: List<Pair<String, Long>>,
            currentEntryId: String,
            positionMs: Long,
            shouldPlay: Boolean,
            shuffle: Boolean = false,
            repeat: PersistedQueueRepeatMode = PersistedQueueRepeatMode.OFF,
            baseEntryIds: List<String> = ids.map { pair -> pair.first }
        ) = LivePlaybackQueueSnapshot(
            entries = ids.map { (entryId, trackId) ->
                LivePlaybackQueueItem(entryId, evidence(entryId, trackId))
            },
            baseEntryIds = baseEntryIds,
            currentEntryId = currentEntryId,
            currentPositionMs = positionMs,
            shouldPlay = shouldPlay,
            shuffleEnabled = shuffle,
            repeatMode = repeat
        )

        fun evidence(entryId: String, trackId: Long) = ListeningMediaItemEvidence(
            itemInstanceId = entryId,
            referenceKey = "track-$trackId",
            reference = SongReference(
                mediaStoreId = null,
                volumeName = "external",
                contentUri = "content://track/$trackId",
                relativePath = "Music/",
                displayName = "$trackId.flac",
                fileSizeBytes = 1_000L,
                dateModifiedEpochSeconds = 1L,
                duration = 180_000L,
                title = "Track $trackId",
                artist = "Artist",
                album = "Album",
                albumArtist = "Artist",
                legacyStableKey = "legacy-$trackId",
                portableKey = "portable-$trackId",
                portableKeyVersion = 1
            )
        )

        fun selectionItem(entryId: String, trackId: Long): MediaItem {
            val extras = mock(Bundle::class.java)
            val values = mapOf(
                ListeningMediaItemMetadata.ITEM_INSTANCE_ID to entryId,
                ListeningMediaItemMetadata.REFERENCE_KEY to "track-$trackId",
                ListeningMediaItemMetadata.MEDIA_STORE_ID to trackId,
                ListeningMediaItemMetadata.FILE_SIZE_BYTES to 1_000L,
                ListeningMediaItemMetadata.DATE_MODIFIED_SECONDS to 1L,
                ListeningMediaItemMetadata.DURATION_MS to 180_000L,
                ListeningMediaItemMetadata.PORTABLE_KEY_VERSION to 1
            )
            org.mockito.Mockito.`when`(extras.get(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer { invocation -> values[invocation.getArgument<String>(0)] }
            val uri = mock(Uri::class.java)
            org.mockito.Mockito.`when`(uri.toString()).thenReturn("content://media/$trackId")
            return MediaItem.Builder()
                .setMediaId(trackId.toString())
                .setUri(uri)
                .setMediaMetadata(MediaMetadata.Builder().setExtras(extras).build())
                .build()
        }

        fun song(id: Long): Song {
            val uri = mock(Uri::class.java)
            org.mockito.Mockito.`when`(uri.toString()).thenReturn("content://media/$id")
            return Song(
                id = id,
                title = "Track $id",
                artist = "Artist",
                album = "Album",
                trackNumber = id.toInt(),
                duration = 180_000L,
                uri = uri,
                filePath = "/music/$id.flac",
                folderPath = "/music",
                albumArtUri = null,
                volumeName = "external",
                displayName = "$id.flac",
                relativePath = "Music/",
                fileSizeBytes = 1_000L,
                dateModifiedEpochSeconds = 1L
            )
        }
    }
}
