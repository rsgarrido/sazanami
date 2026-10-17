package io.github.rsgarrido.sazanami

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.rsgarrido.sazanami.data.PLAYBACK_QUEUE_WORKING_SOURCE_TYPE
import io.github.rsgarrido.sazanami.data.PlaybackQueueEntryDraft
import io.github.rsgarrido.sazanami.data.PlaybackQueueRepository
import io.github.rsgarrido.sazanami.data.PlaybackQueueWorkingOutput
import io.github.rsgarrido.sazanami.data.local.AppDatabase
import io.github.rsgarrido.sazanami.data.local.ListeningTrackIdentityEntity
import io.github.rsgarrido.sazanami.data.local.PersistedQueueRepeatMode
import io.github.rsgarrido.sazanami.data.workingOutput
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackQueueRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: PlaybackQueueRepository
    private var now = 1_000L

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = PlaybackQueueRepository(database) { now++ }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun createsMultipleIndependentQueuesAndPreservesDuplicateSongsAsEntries() = runBlocking {
        val firstTrack = seedIdentity("First")
        val secondTrack = seedIdentity("Second")
        val firstQueue = repository.createQueue(
            queueId = "queue-one",
            displayName = "Morning",
            entries = listOf(
                entry("duplicate-a", firstTrack, base = 0, playback = 0),
                entry("duplicate-b", firstTrack, base = 1, playback = 1)
            )
        )
        val secondQueue = repository.createQueue(
            queueId = "queue-two",
            displayName = "Evening",
            entries = listOf(entry("other", secondTrack, base = 0, playback = 0))
        )

        assertEquals(2, firstQueue.entries.size)
        assertEquals(setOf(firstTrack), firstQueue.entries.map { it.trackIdentityId }.toSet())
        assertNotEquals(firstQueue.entries[0].entryId, firstQueue.entries[1].entryId)
        assertEquals(listOf("other"), secondQueue.entries.map { it.entryId })
        assertEquals(setOf("queue-one", "queue-two"), repository.listQueues().map { it.queueId }.toSet())
        assertEquals(2, repository.observeQueues().first().size)
        assertEquals(
            0,
            database.listeningTrackIdentityDao().deleteUnreferenced(listOf(firstTrack))
        )
    }

    @Test
    fun retrievalUsesPlaybackOrderWithoutLosingBaseOrder() = runBlocking {
        val track = seedIdentity("Shuffled")
        repository.createQueue(
            queueId = "shuffled",
            displayName = "Shuffled",
            shuffleEnabled = true,
            entries = listOf(
                entry("base-0", track, base = 0, playback = 2),
                entry("base-1", track, base = 1, playback = 0),
                entry("base-2", track, base = 2, playback = 1)
            )
        )

        val loaded = checkNotNull(repository.loadQueue("shuffled"))
        assertEquals(listOf("base-1", "base-2", "base-0"), loaded.entries.map { it.entryId })
        assertEquals(listOf(1, 2, 0), loaded.entries.map { it.baseOrder })
        assertEquals(listOf(0, 1, 2), loaded.entries.map { it.playbackOrder })
    }

    @Test
    fun replacingEntriesAndUpdatingSavedPlaybackStateAreAtomicQueueChanges() = runBlocking {
        val firstTrack = seedIdentity("Before")
        val secondTrack = seedIdentity("After")
        repository.createQueue(
            queueId = "resumable",
            displayName = "Before rename",
            entries = listOf(entry("before", firstTrack, base = 0, playback = 0))
        )

        val replaced = repository.replaceEntries(
            queueId = "resumable",
            entries = listOf(
                entry("after-0", secondTrack, base = 0, playback = 1),
                entry("after-1", firstTrack, base = 1, playback = 0)
            ),
            currentEntryId = "after-1",
            currentPositionMs = 4_321L
        )
        assertEquals(listOf("after-1", "after-0"), replaced.entries.map { it.entryId })
        assertEquals("after-1", replaced.queue.currentEntryId)
        assertEquals(4_321L, replaced.queue.currentPositionMs)

        repository.updateSavedPlaybackState(
            queueId = "resumable",
            currentEntryId = "after-0",
            currentPositionMs = 98_765L,
            shuffleEnabled = true,
            repeatMode = PersistedQueueRepeatMode.ALL
        )
        assertTrue(repository.renameQueue("resumable", "Renamed"))

        val updated = checkNotNull(repository.loadQueue("resumable")).queue
        assertEquals("Renamed", updated.displayName)
        assertEquals("after-0", updated.currentEntryId)
        assertEquals(98_765L, updated.currentPositionMs)
        assertTrue(updated.shuffleEnabled)
        assertEquals(PersistedQueueRepeatMode.ALL, updated.repeatMode)
    }

    @Test
    fun activeQueueCanChangeAndDeletingOneQueueDoesNotAffectAnother() = runBlocking {
        val track = seedIdentity("Shared")
        repository.createQueue(
            queueId = "first",
            displayName = "First",
            entries = listOf(entry("first-entry", track, base = 0, playback = 0))
        )
        repository.createQueue(
            queueId = "second",
            displayName = "Second",
            entries = listOf(entry("second-entry", track, base = 0, playback = 0))
        )

        repository.setActiveQueue("first")
        assertEquals("first", repository.getActiveQueueId())
        repository.setActiveQueue("second")
        assertEquals("second", repository.getActiveQueueId())

        assertTrue(repository.deleteQueue("first"))
        assertNull(repository.loadQueue("first"))
        assertEquals(listOf("second-entry"), repository.loadQueue("second")?.entries?.map { it.entryId })
        assertEquals("second", repository.getActiveQueueId())

        assertTrue(repository.deleteQueue("second"))
        assertNull(repository.getActiveQueueId())
    }

    @Test
    fun clearingQueueKeepsSessionAndClearsResumePoint() = runBlocking {
        val track = seedIdentity("Clear")
        repository.createQueue(
            queueId = "clear-me",
            displayName = "Keep me",
            entries = listOf(entry("entry", track, base = 0, playback = 0)),
            currentEntryId = "entry",
            currentPositionMs = 200L
        )

        val cleared = repository.clearQueue("clear-me")

        assertEquals("clear-me", cleared.queue.queueId)
        assertTrue(cleared.entries.isEmpty())
        assertNull(cleared.queue.currentEntryId)
        assertEquals(0L, cleared.queue.currentPositionMs)
    }

    @Test
    fun duplicatingQueueCreatesIndependentInactiveEntriesAndPreservesResumeMetadata() = runBlocking {
        val track = seedIdentity("Duplicate")
        repository.createQueue(
            queueId = "source",
            displayName = "Queue 1",
            entries = listOf(
                entry("source-a", track, base = 0, playback = 1),
                entry("source-b", track, base = 1, playback = 0)
            ),
            currentEntryId = "source-b",
            currentPositionMs = 4_200L,
            shuffleEnabled = true,
            repeatMode = PersistedQueueRepeatMode.ONE
        )
        repository.setActiveQueue("source")
        val copiedIds = ArrayDeque(listOf("copy-a", "copy-b"))

        val copy = repository.duplicateQueue(
            sourceQueueId = "source",
            displayName = " Queue 2 ",
            queueId = "copy",
            entryIdFactory = { copiedIds.removeFirst() }
        )

        assertEquals("Queue 2", copy.queue.displayName)
        assertEquals(listOf("copy-a", "copy-b"), copy.entries.map { it.entryId })
        assertEquals(listOf(1, 0), copy.entries.map { it.baseOrder })
        assertEquals("copy-a", copy.queue.currentEntryId)
        assertEquals(4_200L, copy.queue.currentPositionMs)
        assertTrue(copy.queue.shuffleEnabled)
        assertEquals(PersistedQueueRepeatMode.ONE, copy.queue.repeatMode)
        assertEquals("source", repository.getActiveQueueId())
        assertEquals(listOf("source-b", "source-a"), repository.loadQueue("source")?.entries?.map {
            it.entryId
        })
    }

    @Test
    fun guardedDeletionRejectsActiveAndOnlyRemainingQueue() = runBlocking {
        val track = seedIdentity("Guarded")
        repository.createQueue(
            queueId = "active",
            displayName = "Active",
            entries = listOf(entry("active-entry", track, 0, 0))
        )
        repository.createQueue(
            queueId = "inactive",
            displayName = "Inactive",
            entries = listOf(entry("inactive-entry", track, 0, 0))
        )
        repository.setActiveQueue("active")

        assertTrue(!repository.deleteInactiveQueueIfNotLast("active"))
        assertTrue(repository.deleteInactiveQueueIfNotLast("inactive"))
        assertTrue(!repository.deleteInactiveQueueIfNotLast("active"))
        assertEquals("active", repository.getActiveQueueId())
    }

    @Test
    fun appendingToInactiveQueuePreservesResumeStateAndCreatesDuplicateEntries() = runBlocking {
        val track = seedIdentity("Append")
        repository.createQueue(
            queueId = "target",
            displayName = "Target",
            entries = listOf(entry("current", track, 0, 0)),
            currentEntryId = "current",
            currentPositionMs = 8_500L
        )

        val updated = repository.appendEntries(
            "target",
            listOf(
                entry("duplicate-one", track, 0, 0),
                entry("duplicate-two", track, 1, 1)
            )
        )

        assertEquals(listOf("current", "duplicate-one", "duplicate-two"), updated.entries.map { it.entryId })
        assertEquals(listOf(0, 1, 2), updated.entries.map { it.baseOrder })
        assertEquals("current", updated.queue.currentEntryId)
        assertEquals(8_500L, updated.queue.currentPositionMs)
    }

    @Test
    fun removingInactiveEntriesUsesEntryIdAndDeterministicCurrentFallback() = runBlocking {
        val track = seedIdentity("Remove")
        repository.createQueue(
            queueId = "saved",
            displayName = "Saved",
            entries = listOf(
                entry("duplicate-before", track, 0, 0),
                entry("duplicate-current", track, 1, 1),
                entry("after", track, 2, 2)
            ),
            currentEntryId = "duplicate-current",
            currentPositionMs = 6_000L
        )

        val duplicateRemoved = checkNotNull(repository.removeEntry("saved", "duplicate-before"))
        assertEquals(listOf("duplicate-current", "after"), duplicateRemoved.entries.map { it.entryId })
        assertEquals("duplicate-current", duplicateRemoved.queue.currentEntryId)
        assertEquals(6_000L, duplicateRemoved.queue.currentPositionMs)

        val currentRemoved = checkNotNull(repository.removeEntry("saved", "duplicate-current"))
        assertEquals(listOf("after"), currentRemoved.entries.map { it.entryId })
        assertEquals("after", currentRemoved.queue.currentEntryId)
        assertEquals(0L, currentRemoved.queue.currentPositionMs)
    }

    @Test
    fun undoRestorePreservesStableEntryAndIndependentBaseAndPlaybackPositions() = runBlocking {
        val track = seedIdentity("Undo")
        repository.createQueue(
            queueId = "saved",
            displayName = "Saved",
            entries = listOf(
                entry("current", track, base = 0, playback = 0),
                entry("removed", track, base = 2, playback = 1),
                entry("middle", track, base = 1, playback = 2)
            ),
            currentEntryId = "current",
            currentPositionMs = 4_321L
        )
        val removedEntry = checkNotNull(repository.loadQueue("saved"))
            .entries.first { it.entryId == "removed" }

        repository.removeEntry("saved", "removed")
        val restored = checkNotNull(repository.restoreEntry("saved", removedEntry))

        assertEquals(listOf("current", "removed", "middle"), restored.entries.map { it.entryId })
        assertEquals(2, restored.entries.first { it.entryId == "removed" }.baseOrder)
        assertEquals(1, restored.entries.first { it.entryId == "removed" }.playbackOrder)
        assertEquals("current", restored.queue.currentEntryId)
        assertEquals(4_321L, restored.queue.currentPositionMs)
    }

    @Test
    fun inactiveManualReorderUpdatesPlaybackAndBaseOrderButPreservesResumeState() = runBlocking {
        val track = seedIdentity("Reorder")
        repository.createQueue(
            queueId = "saved",
            displayName = "Saved",
            entries = listOf(
                entry("one", track, 0, 0),
                entry("two", track, 1, 1),
                entry("three", track, 2, 2)
            ),
            currentEntryId = "two",
            currentPositionMs = 9_999L
        )

        val reordered = checkNotNull(
            repository.reorderEntry("saved", "three", 0, updateBaseOrder = true)
        )

        assertEquals(listOf("three", "one", "two"), reordered.entries.map { it.entryId })
        assertEquals(listOf(0, 1, 2), reordered.entries.map { it.playbackOrder })
        assertEquals(listOf(0, 1, 2), reordered.entries.map { it.baseOrder })
        assertEquals("two", reordered.queue.currentEntryId)
        assertEquals(9_999L, reordered.queue.currentPositionMs)
    }

    @Test
    fun shuffledManualReorderUpdatesPlaybackOnlyAndPreservesCanonicalBaseAndResumeState() =
        runBlocking {
            val track = seedIdentity("Shuffled reorder")
            repository.createQueue(
                queueId = "shuffled",
                displayName = "Shuffled",
                entries = listOf(
                    entry("one", track, base = 0, playback = 1),
                    entry("two", track, base = 1, playback = 2),
                    entry("three", track, base = 2, playback = 0)
                ),
                currentEntryId = "two",
                currentPositionMs = 9_999L,
                shuffleEnabled = true
            )

            val reordered = checkNotNull(
                repository.reorderEntry(
                    "shuffled",
                    "two",
                    1,
                    updateBaseOrder = false
                )
            )

            assertEquals(listOf("three", "two", "one"), reordered.entries.map { it.entryId })
            assertEquals(
                listOf("one", "two", "three"),
                reordered.entries.sortedBy { it.baseOrder }.map { it.entryId }
            )
            assertEquals("two", reordered.queue.currentEntryId)
            assertEquals(9_999L, reordered.queue.currentPositionMs)
            assertTrue(reordered.queue.shuffleEnabled)
        }

    @Test
    fun workingOwnersSurviveDatabaseReopenAndSavedActivationWithoutGrowingQueueCount() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val databaseName = "working-queue-${UUID.randomUUID()}.db"
            database.close()
            database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build()
            repository = PlaybackQueueRepository(database) { now++ }
            try {
                val track = seedIdentity("Working restart")
                val android = checkNotNull(
                    commitWorking(
                        output = PlaybackQueueWorkingOutput.ANDROID,
                        queueId = "android-working",
                        displayName = "Queue 1",
                        entries = listOf(entry("android-entry", track, 0, 0)),
                        currentPositionMs = 4_321L
                    )
                )
                repeat(4) { index ->
                    val direct = checkNotNull(
                        commitWorking(
                            output = PlaybackQueueWorkingOutput.DIRECT,
                            queueId = "direct-candidate-$index",
                            displayName = "Queue ${index + 2}",
                            entries = listOf(
                                entry("direct-$index-first", track, 0, 1),
                                entry("direct-$index-duplicate", track, 1, 0)
                            ),
                            currentEntryId = "direct-$index-duplicate",
                            currentPositionMs = 200L + index,
                            shuffleEnabled = true,
                            repeatMode = PersistedQueueRepeatMode.ALL
                        )
                    )
                    assertEquals("direct-candidate-0", direct.queue.queueId)
                    assertEquals("Queue 2", direct.queue.displayName)
                    assertEquals(
                        listOf("direct-$index-duplicate", "direct-$index-first"),
                        direct.entries.map { it.entryId }
                    )
                    assertEquals(listOf(1, 0), direct.entries.map { it.baseOrder })
                    assertEquals(2, repository.listQueues().size)
                }
                val saved = repository.duplicateQueue(
                    sourceQueueId = "direct-candidate-0",
                    displayName = "Queue 3",
                    queueId = "explicit-save"
                )
                repository.setActiveQueue(saved.queue.queueId)
                val savedBeforeRestart = repository.loadQueue(saved.queue.queueId)
                database.close()
                database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build()
                repository = PlaybackQueueRepository(database) { now++ }

                assertEquals("explicit-save", repository.getActiveQueueId())
                assertEquals(android, repository.loadQueue("android-working"))
                assertEquals(savedBeforeRestart, repository.loadQueue("explicit-save"))
                assertEquals(
                    PlaybackQueueWorkingOutput.DIRECT,
                    repository.loadQueue("direct-candidate-0")?.queue?.workingOutput
                )
                val resumed = checkNotNull(
                    commitWorking(
                        output = PlaybackQueueWorkingOutput.DIRECT,
                        queueId = "unused-after-restart",
                        displayName = "Queue 4",
                        entries = listOf(entry("after-restart", track, 0, 0)),
                        currentPositionMs = 9_000L
                    )
                )

                assertEquals("direct-candidate-0", resumed.queue.queueId)
                assertEquals("after-restart", resumed.queue.currentEntryId)
                assertEquals(9_000L, resumed.queue.currentPositionMs)
                assertEquals(PersistedQueueRepeatMode.OFF, resumed.queue.repeatMode)
                assertTrue(!resumed.queue.shuffleEnabled)
                assertEquals("direct-candidate-0", repository.getActiveQueueId())
                assertEquals(android, repository.loadQueue("android-working"))
                assertEquals(savedBeforeRestart, repository.loadQueue("explicit-save"))
                assertEquals(3, repository.listQueues().size)
                assertEquals(2, repository.listQueues().count { it.workingOutput != null })
                assertEquals(1, repository.listQueues().count { it.workingOutput == null })
                assertNull(repository.loadQueue("unused-after-restart"))
            } finally {
                database.close()
                context.deleteDatabase(databaseName)
            }
        }

    @Test
    fun explicitCopiesClearManagedOwnershipAndPreserveOtherSourceMetadata() = runBlocking {
        val track = seedIdentity("Saved ownership")
        val working = checkNotNull(
            commitWorking(
                output = PlaybackQueueWorkingOutput.DIRECT,
                queueId = "working",
                entries = listOf(entry("working-entry", track, 0, 0))
            )
        )
        val saved = repository.duplicateQueue("working", "Queue 2", queueId = "saved")
        assertNull(saved.queue.workingOutput)
        assertNull(saved.queue.sourceType)
        assertNull(saved.queue.sourceKey)
        assertNotEquals(working.entries.single().entryId, saved.entries.single().entryId)
        assertEquals(working, repository.loadQueue("working"))

        repository.createQueue(
            queueId = "album-source",
            displayName = "Album source",
            sourceType = "album",
            sourceKey = "album:42",
            entries = listOf(entry("album-entry", track, 0, 0))
        )
        val albumCopy = repository.duplicateQueue("album-source", "Album copy", queueId = "album-copy")
        assertNull(albumCopy.queue.workingOutput)
        assertEquals("album", albumCopy.queue.sourceType)
        assertEquals("album:42", albumCopy.queue.sourceKey)
    }

    @Test
    fun legacyRowsAndUnrecognizedMetadataArePreservedWithoutAdoptionOrRelabeling() = runBlocking {
        val track = seedIdentity("Legacy")
        repository.createQueue(
            queueId = "legacy-duplicate-one",
            displayName = "Queue 1",
            entries = listOf(entry("legacy-first", track, 0, 0))
        )
        repository.createQueue(
            queueId = "legacy-duplicate-two",
            displayName = "Queue 1",
            entries = listOf(entry("legacy-second", track, 0, 0))
        )
        repository.createQueue(
            queueId = "unrecognized-key",
            displayName = "Queue 1",
            sourceType = PLAYBACK_QUEUE_WORKING_SOURCE_TYPE,
            sourceKey = "unknown-output"
        )
        repository.createQueue(
            queueId = "unrecognized-marker",
            displayName = "Queue 1",
            sourceType = "sazanami:working:v10",
            sourceKey = PlaybackQueueWorkingOutput.DIRECT.storageKey
        )
        repository.setActiveQueue("legacy-duplicate-one")
        val legacy = repository.listQueues().associate { it.queueId to repository.loadQueue(it.queueId) }
        assertTrue(legacy.values.all { it?.queue?.workingOutput == null })
        assertTrue(
            runCatching {
                commitWorking(
                    output = PlaybackQueueWorkingOutput.DIRECT,
                    queueId = "legacy-duplicate-one",
                    entries = listOf(entry("must-not-relabel", track, 0, 0))
                )
            }.exceptionOrNull() is IllegalArgumentException
        )

        repeat(3) { index ->
            PlaybackQueueWorkingOutput.entries.forEach { output ->
                commitWorking(
                    output = output,
                    queueId = "${output.storageKey}-$index",
                    displayName = "Queue ${index + 2}",
                    entries = listOf(entry("${output.storageKey}-entry-$index", track, 0, 0))
                )
            }
        }

        legacy.forEach { (id, before) -> assertEquals(before, repository.loadQueue(id)) }
        assertEquals(6, repository.listQueues().size)
        assertEquals(2, repository.listQueues().count { it.workingOutput != null })
        assertEquals(1, repository.listQueues().count { it.workingOutput == PlaybackQueueWorkingOutput.ANDROID })
        assertEquals(1, repository.listQueues().count { it.workingOutput == PlaybackQueueWorkingOutput.DIRECT })
    }

    @Test
    fun supersededWorkingCreationRollsBackOwnerEntriesAndActiveIdentity() = runBlocking {
        val track = seedIdentity("Stale creation")
        repository.createQueue(queueId = "saved", displayName = "Saved")
        repository.setActiveQueue("saved")
        val before = repository.listQueues()
        val candidate = listOf(entry("candidate-entry", track, 0, 0))

        assertNull(
            commitWorking(
                output = PlaybackQueueWorkingOutput.DIRECT,
                queueId = "stale-working",
                entries = candidate,
                canCommit = { false }
            )
        )
        var guardCalls = 0
        assertNull(
            commitWorking(
                output = PlaybackQueueWorkingOutput.DIRECT,
                queueId = "stale-working",
                entries = candidate,
                canCommit = { ++guardCalls == 1 }
            )
        )

        assertEquals(2, guardCalls)
        assertEquals(before, repository.listQueues())
        assertEquals("saved", repository.getActiveQueueId())
        assertNull(repository.loadQueue("stale-working"))
        assertTrue(database.playbackQueueDao().getEntriesInPlaybackOrder("stale-working").isEmpty())
        assertEquals(
            "stale-working",
            commitWorking(
                output = PlaybackQueueWorkingOutput.DIRECT,
                queueId = "stale-working",
                entries = candidate
            )?.queue?.queueId
        )
    }

    @Test
    fun supersededWorkingReplacementRestoresPreviousContentsPoliciesAndActiveQueue() = runBlocking {
        val track = seedIdentity("Stale replacement")
        val original = checkNotNull(
            commitWorking(
                output = PlaybackQueueWorkingOutput.DIRECT,
                queueId = "working",
                entries = listOf(entry("original", track, 0, 0)),
                currentPositionMs = 8_000L,
                repeatMode = PersistedQueueRepeatMode.ONE
            )
        )
        repository.createQueue(queueId = "saved", displayName = "Saved")
        repository.setActiveQueue("saved")
        val saved = repository.loadQueue("saved")
        var guardCalls = 0

        val rejected = commitWorking(
            output = PlaybackQueueWorkingOutput.DIRECT,
            queueId = "unused-candidate",
            entries = listOf(entry("obsolete", track, 0, 0)),
            currentPositionMs = 1_000L,
            shuffleEnabled = true,
            repeatMode = PersistedQueueRepeatMode.ALL,
            canCommit = { ++guardCalls == 1 }
        )

        assertNull(rejected)
        assertEquals(2, guardCalls)
        assertEquals(original, repository.loadQueue("working"))
        assertEquals(saved, repository.loadQueue("saved"))
        assertEquals("saved", repository.getActiveQueueId())
        assertEquals(2, repository.listQueues().size)
        assertNull(repository.loadQueue("unused-candidate"))
    }

    @Test
    fun invalidWorkingReferencesLeaveLegacyAndWorkingRowsUntouched() = runBlocking {
        val track = seedIdentity("Reference validation")
        repository.createQueue(queueId = "legacy", displayName = "Queue 1")
        repository.setActiveQueue("legacy")
        val before = repository.listQueues()
        val invalidEntries = listOf(entry("invalid", Long.MAX_VALUE, 0, 0))

        assertTrue(
            runCatching {
                commitWorking(PlaybackQueueWorkingOutput.DIRECT, "new-invalid", invalidEntries)
            }.exceptionOrNull() is IllegalArgumentException
        )
        assertEquals(before, repository.listQueues())
        assertEquals("legacy", repository.getActiveQueueId())
        val working = checkNotNull(
            commitWorking(
                output = PlaybackQueueWorkingOutput.DIRECT,
                queueId = "working",
                entries = listOf(entry("valid", track, 0, 0))
            )
        )
        repository.setActiveQueue("legacy")

        assertTrue(
            runCatching {
                commitWorking(PlaybackQueueWorkingOutput.DIRECT, "unused", invalidEntries)
            }.exceptionOrNull() is IllegalArgumentException
        )
        assertTrue(
            runCatching {
                commitWorking(
                    output = PlaybackQueueWorkingOutput.DIRECT,
                    queueId = "unused",
                    entries = listOf(
                        entry("invalid-binding", track, 0, 0).copy(localTrackBindingId = Long.MAX_VALUE)
                    )
                )
            }.exceptionOrNull() is IllegalArgumentException
        )
        assertEquals(working, repository.loadQueue("working"))
        assertEquals("legacy", repository.getActiveQueueId())
        assertEquals(2, repository.listQueues().size)
    }

    @Test
    fun failedWorkingEntryInsertionRollsBackBothCreationAndReplacement() = runBlocking {
        val track = seedIdentity("Insertion rollback")
        repository.createQueue(
            queueId = "saved",
            displayName = "Saved",
            entries = listOf(entry("protected-entry", track, 0, 0))
        )
        repository.setActiveQueue("saved")
        val saved = repository.loadQueue("saved")
        val conflictingEntries = listOf(entry("protected-entry", track, 0, 0))

        assertTrue(
            runCatching {
                commitWorking(PlaybackQueueWorkingOutput.DIRECT, "failed-owner", conflictingEntries)
            }.isFailure
        )
        assertEquals(1, repository.listQueues().size)
        assertEquals("saved", repository.getActiveQueueId())
        assertEquals(saved, repository.loadQueue("saved"))
        assertNull(repository.loadQueue("failed-owner"))
        val working = checkNotNull(
            commitWorking(
                output = PlaybackQueueWorkingOutput.DIRECT,
                queueId = "working",
                entries = listOf(entry("working-entry", track, 0, 0)),
                currentPositionMs = 2_500L
            )
        )
        repository.setActiveQueue("saved")
        // Reactivation legitimately updates timestamps before the replacement transaction.
        val savedBeforeReplacement = checkNotNull(repository.loadQueue("saved"))

        assertTrue(
            runCatching {
                commitWorking(PlaybackQueueWorkingOutput.DIRECT, "unused", conflictingEntries)
            }.isFailure
        )
        assertEquals(working, repository.loadQueue("working"))
        assertEquals(savedBeforeReplacement, repository.loadQueue("saved"))
        assertEquals("saved", repository.getActiveQueueId())
        assertEquals(2, repository.listQueues().size)
    }

    @Test
    fun frozenManagedOwnerExchangesOutputRolesWithoutChangingItsContentsOrAllocatingRows() =
        runBlocking {
            val track = seedIdentity("Frozen managed owner")
            val previousAndroid = checkNotNull(
                commitWorking(
                    output = PlaybackQueueWorkingOutput.ANDROID,
                    queueId = "previous-android",
                    entries = listOf(entry("previous-android-entry", track, 0, 0))
                )
            )
            val frozen = checkNotNull(
                commitWorking(
                    output = PlaybackQueueWorkingOutput.DIRECT,
                    queueId = "restored-on-android",
                    entries = listOf(entry("frozen-entry", track, 0, 0)),
                    currentPositionMs = 7_000L,
                    repeatMode = PersistedQueueRepeatMode.ONE
                )
            )

            val direct = checkNotNull(
                commitWorking(
                    output = PlaybackQueueWorkingOutput.DIRECT,
                    queueId = "unused-candidate",
                    entries = listOf(entry("direct-replacement", track, 0, 0)),
                    protectedQueueId = frozen.queue.queueId
                )
            )

            assertEquals(previousAndroid.queue.queueId, direct.queue.queueId)
            assertEquals(PlaybackQueueWorkingOutput.DIRECT, direct.queue.workingOutput)
            assertEquals(
                frozen.copy(queue = frozen.queue.copy(sourceKey = PlaybackQueueWorkingOutput.ANDROID.storageKey)),
                repository.loadQueue(frozen.queue.queueId)
            )
            assertEquals(direct.queue.queueId, repository.getActiveQueueId())
            assertEquals(2, repository.listQueues().size)
            assertNull(repository.loadQueue("unused-candidate"))
            assertEquals(1, repository.listQueues().count { it.workingOutput == PlaybackQueueWorkingOutput.ANDROID })
            assertEquals(1, repository.listQueues().count { it.workingOutput == PlaybackQueueWorkingOutput.DIRECT })
        }

    @Test
    fun frozenManagedOwnerGetsOppositeRoleAndCreatesOnlyOneMissingOutputOwner() = runBlocking {
        val track = seedIdentity("Missing Android owner")
        val frozen = checkNotNull(
            commitWorking(
                output = PlaybackQueueWorkingOutput.DIRECT,
                queueId = "frozen",
                entries = listOf(entry("frozen-entry", track, 0, 0)),
                currentPositionMs = 3_000L
            )
        )
        repeat(3) { index ->
            val direct = checkNotNull(
                commitWorking(
                    output = PlaybackQueueWorkingOutput.DIRECT,
                    queueId = "candidate-$index",
                    entries = listOf(entry("direct-$index", track, 0, 0)),
                    protectedQueueId = frozen.queue.queueId
                )
            )
            assertEquals("candidate-0", direct.queue.queueId)
            assertEquals(2, repository.listQueues().size)
        }
        assertEquals(
            frozen.copy(queue = frozen.queue.copy(sourceKey = PlaybackQueueWorkingOutput.ANDROID.storageKey)),
            repository.loadQueue("frozen")
        )
    }

    @Test
    fun supersededOrFailedOutputRoleExchangeRollsBackMetadataEntriesAndActiveState() = runBlocking {
        val track = seedIdentity("Output role rollback")
        val android = checkNotNull(
            commitWorking(
                output = PlaybackQueueWorkingOutput.ANDROID,
                queueId = "android",
                entries = listOf(entry("android-entry", track, 0, 0)),
                currentPositionMs = 2_000L
            )
        )
        val frozen = checkNotNull(
            commitWorking(
                output = PlaybackQueueWorkingOutput.DIRECT,
                queueId = "frozen",
                entries = listOf(entry("frozen-entry", track, 0, 0)),
                currentPositionMs = 4_000L
            )
        )
        var guardCalls = 0
        assertNull(
            commitWorking(
                output = PlaybackQueueWorkingOutput.DIRECT,
                queueId = "unused",
                entries = listOf(entry("superseded", track, 0, 0)),
                protectedQueueId = frozen.queue.queueId,
                canCommit = { ++guardCalls == 1 }
            )
        )
        assertEquals(2, guardCalls)
        assertEquals(android, repository.loadQueue("android"))
        assertEquals(frozen, repository.loadQueue("frozen"))
        assertEquals("frozen", repository.getActiveQueueId())
        assertTrue(
            runCatching {
                commitWorking(
                    output = PlaybackQueueWorkingOutput.DIRECT,
                    queueId = "unused",
                    entries = listOf(entry("frozen-entry", track, 0, 0)),
                    protectedQueueId = frozen.queue.queueId
                )
            }.isFailure
        )
        assertEquals(android, repository.loadQueue("android"))
        assertEquals(frozen, repository.loadQueue("frozen"))
        assertEquals("frozen", repository.getActiveQueueId())
        assertEquals(2, repository.listQueues().size)
    }

    private suspend fun commitWorking(
        output: PlaybackQueueWorkingOutput,
        queueId: String,
        entries: List<PlaybackQueueEntryDraft>,
        displayName: String = "Queue 1",
        currentEntryId: String? = entries.firstOrNull()?.entryId,
        currentPositionMs: Long = 0L,
        shuffleEnabled: Boolean = false,
        repeatMode: PersistedQueueRepeatMode = PersistedQueueRepeatMode.OFF,
        protectedQueueId: String? = null,
        canCommit: () -> Boolean = { true }
    ) = repository.commitWorkingQueue(
        output = output,
        queueId = queueId,
        displayName = displayName,
        entries = entries,
        currentEntryId = currentEntryId,
        currentPositionMs = currentPositionMs,
        shuffleEnabled = shuffleEnabled,
        repeatMode = repeatMode,
        protectedQueueId = protectedQueueId,
        canCommit = canCommit
    )

    private suspend fun seedIdentity(label: String): Long {
        return database.listeningTrackIdentityDao().insert(
            ListeningTrackIdentityEntity(
                titleSnapshot = label,
                artistSnapshot = "Artist",
                albumSnapshot = "Album",
                albumArtistSnapshot = null,
                durationMsSnapshot = 180_000L,
                normalizedTitle = label.lowercase(),
                normalizedArtist = "artist",
                normalizedAlbum = "album",
                metadataKey = "portable:$label",
                metadataKeyVersion = 1,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    private fun entry(
        entryId: String,
        trackIdentityId: Long,
        base: Int,
        playback: Int
    ) = PlaybackQueueEntryDraft(
        entryId = entryId,
        trackIdentityId = trackIdentityId,
        baseOrder = base,
        playbackOrder = playback
    )
}
