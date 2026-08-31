package io.github.rsgarrido.sazanami

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.rsgarrido.sazanami.data.PlaybackQueueEntryDraft
import io.github.rsgarrido.sazanami.data.PlaybackQueueRepository
import io.github.rsgarrido.sazanami.data.local.AppDatabase
import io.github.rsgarrido.sazanami.data.local.ListeningTrackIdentityEntity
import io.github.rsgarrido.sazanami.data.local.PersistedQueueRepeatMode
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
