package io.github.rsgarrido.sazanami.controller

import android.net.Uri
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.local.PersistedQueueRepeatMode
import io.github.rsgarrido.sazanami.data.local.PlaybackQueueEntity
import io.github.rsgarrido.sazanami.data.local.PlaybackQueueEntryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class PlaybackQueueUiControllerTest {
    @Test
    fun queueListMapsActiveCurrentAndSelectedStateIndependently() {
        val operations = FakeOperations(activeQueueId = "A").apply {
            add(queue("A", "Morning", "a", song(1L)))
            add(queue("B", "Evening", "b", song(2L)))
        }
        val controller = controller(operations)

        assertEquals("A", controller.state.value.activeQueueId)
        assertEquals("A", controller.state.value.selectedQueueId)
        assertTrue(controller.state.value.queues.first { it.queueId == "A" }.isActive)
        assertEquals(
            "PLAYING",
            controller.state.value.queues.first { it.queueId == "A" }.stateLabel
        )
        assertEquals(1, controller.state.value.queues.first { it.queueId == "A" }.currentPosition)
        assertEquals("Song 1", controller.state.value.queues.first { it.queueId == "A" }.currentTrack?.title)

        controller.selectQueue("B")

        assertEquals("A", controller.state.value.activeQueueId)
        assertEquals("B", controller.state.value.selectedQueueId)
        assertFalse(controller.state.value.selectedQueue?.isActive == true)
        assertEquals("VIEWING", controller.state.value.selectedQueue?.stateLabel)
        assertEquals(0, operations.switchCount)
    }

    @Test
    fun structuralLiveQueueMutationRefreshesTheActiveQueueWithoutARoomEmission() {
        val first = song(1L)
        val added = song(2L)
        val operations = FakeOperations(activeQueueId = "A").apply {
            add(queue("A", "A", "a", first))
            publishLive(first)
        }
        val controller = controller(operations)

        operations.publishLive(first, added)

        assertEquals(2, controller.state.value.selectedQueueEntryCount)
        assertEquals(
            listOf("Song 1", "Song 2"),
            controller.state.value.selectedEntries.mapNotNull { entry -> entry.song?.title }
        )
        assertTrue(controller.state.value.selectedEntries.first().isCurrent)
    }

    @Test
    fun explicitResumeSwitchesExactlyOnceAndSelectionAloneNeverSwitches() = runBlocking {
        val operations = FakeOperations(activeQueueId = "A").apply {
            add(queue("A", "A", "a", song(1L)))
            add(queue("B", "B", "b", song(2L)))
        }
        val controller = controller(operations)
        controller.selectQueue("B")
        assertEquals(0, operations.switchCount)

        controller.switchSelectedQueue().join()

        assertEquals(1, operations.switchCount)
        assertEquals("B", operations.activeQueueId)
        assertTrue(controller.state.value.selectedQueue?.isActive == true)
    }

    @Test
    fun failedSwitchKeepsTheActiveQueueAndSurfacesLightweightFeedback() = runBlocking {
        val operations = FakeOperations(activeQueueId = "A").apply {
            add(queue("A", "A", "a", song(1L)))
            add(queue("B", "B", "b", song(2L)))
            switchResult = false
        }
        val controller = controller(operations)
        controller.selectQueue("B")

        controller.switchSelectedQueue().join()

        assertEquals(1, operations.switchCount)
        assertEquals("A", controller.state.value.activeQueueId)
        assertEquals("B", controller.state.value.selectedQueueId)
        assertEquals(
            "Unable to switch queues. Current playback was kept.",
            controller.state.value.message
        )
    }

    @Test
    fun createSelectsNewIndependentInactiveQueue() = runBlocking {
        val operations = FakeOperations(activeQueueId = "A").apply {
            add(queue("A", "Queue 1", "a", song(1L)))
            createResult = queue("B", "Queue 2", "b", song(1L))
        }
        val controller = controller(operations)

        controller.createQueueFromCurrent().join()

        assertEquals(1, operations.createCount)
        assertEquals("A", controller.state.value.activeQueueId)
        assertEquals("B", controller.state.value.selectedQueueId)
        assertFalse(controller.state.value.selectedQueue?.isActive == true)
    }

    @Test
    fun renameTrimsInputAndRejectsBlankNames() = runBlocking {
        val operations = FakeOperations(activeQueueId = "A").apply {
            add(queue("A", "Queue 1", "a", song(1L)))
        }
        val controller = controller(operations)

        controller.renameQueue("A", "  Driving  ").join()
        assertEquals("Driving", operations.renamedName)
        controller.renameQueue("A", "   ").join()

        assertEquals(1, operations.renameCount)
        assertEquals("Queue name cannot be blank.", controller.state.value.message)
        assertEquals("Trimmed", validatedQueueName("  Trimmed "))
        assertNull(validatedQueueName("  "))
    }

    @Test
    fun deleteAllowsInactiveButProtectsActiveAndSoleQueue() = runBlocking {
        val operations = FakeOperations(activeQueueId = "A").apply {
            add(queue("A", "A", "a", song(1L)))
            add(queue("B", "B", "b", song(2L)))
        }
        val controller = controller(operations)

        controller.deleteQueue("A").join()
        assertEquals(0, operations.deleteCount)
        assertEquals("The active queue cannot be deleted.", controller.state.value.message)

        controller.deleteQueue("B").join()
        assertEquals(1, operations.deleteCount)
        assertEquals(listOf("A"), controller.state.value.queues.map { it.queueId })

        controller.deleteQueue("A").join()
        assertEquals(1, operations.deleteCount)
        assertEquals("The only remaining queue cannot be deleted.", controller.state.value.message)
    }

    @Test
    fun selectionFallsBackToActiveQueueWhenSelectedQueueDisappears() = runBlocking {
        val operations = FakeOperations(activeQueueId = "A").apply {
            add(queue("A", "A", "a", song(1L)))
            add(queue("B", "B", "b", song(2L)))
        }
        val controller = controller(operations)
        controller.selectQueue("B")

        controller.deleteQueue("B").join()

        assertEquals("A", controller.state.value.selectedQueueId)
        assertEquals("A", controller.state.value.activeQueueId)
    }

    private fun controller(operations: FakeOperations) = PlaybackQueueUiController(
        operations = operations,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    )

    private class FakeOperations(
        var activeQueueId: String?
    ) : PlaybackQueueUiOperations {
        private val queueFlow = MutableStateFlow<List<PlaybackQueueEntity>>(emptyList())
        private val liveQueueFlow = MutableStateFlow<LiveActiveQueueForUi?>(null)
        private val loaded = linkedMapOf<String, LoadedQueueForUi>()
        var switchCount = 0
        var createCount = 0
        var renameCount = 0
        var deleteCount = 0
        var renamedName: String? = null
        var createResult: LoadedQueueForUi? = null
        var switchResult = true

        fun add(queue: LoadedQueueForUi) {
            loaded[queue.queue.queueId] = queue
            publish()
        }

        fun publishLive(vararg songs: Song) {
            liveQueueFlow.value = LiveActiveQueueForUi(songs.toList())
        }

        override fun observeQueues(): Flow<List<PlaybackQueueEntity>> = queueFlow

        override fun observeLiveActiveQueue(): Flow<LiveActiveQueueForUi?> = liveQueueFlow

        override suspend fun listQueues(): List<PlaybackQueueEntity> = queueFlow.value

        override suspend fun getActiveQueueId(): String? = activeQueueId

        override suspend fun loadQueue(queueId: String): LoadedQueueForUi? = loaded[queueId]

        override suspend fun switchQueue(queueId: String): Boolean {
            switchCount += 1
            if (!switchResult) return false
            activeQueueId = queueId
            publish()
            return true
        }

        override suspend fun createQueueFromCurrent(): String? {
            createCount += 1
            val created = createResult ?: return null
            add(created)
            return created.queue.queueId
        }

        override suspend fun renameQueue(queueId: String, name: String): Boolean {
            renameCount += 1
            renamedName = name
            val existing = loaded[queueId] ?: return false
            loaded[queueId] = existing.copy(queue = existing.queue.copy(displayName = name))
            publish()
            return true
        }

        override suspend fun deleteQueue(queueId: String): Boolean {
            deleteCount += 1
            val removed = loaded.remove(queueId) != null
            publish()
            return removed
        }

        private fun publish() {
            queueFlow.value = loaded.values.map(LoadedQueueForUi::queue)
        }
    }

    private companion object {
        fun queue(id: String, name: String, currentEntry: String, song: Song) =
            LoadedQueueForUi(
                queue = PlaybackQueueEntity(
                    queueId = id,
                    displayName = name,
                    createdAt = 1L,
                    updatedAt = 2L,
                    lastActiveAt = 3L,
                    sourceType = null,
                    sourceKey = null,
                    currentEntryId = currentEntry,
                    currentPositionMs = 400L,
                    shuffleEnabled = false,
                    repeatMode = PersistedQueueRepeatMode.OFF
                ),
                entries = listOf(
                    LoadedQueueEntryForUi(
                        entry = PlaybackQueueEntryEntity(
                            entryId = currentEntry,
                            queueId = id,
                            trackIdentityId = song.id,
                            localTrackBindingId = null,
                            baseOrder = 0,
                            playbackOrder = 0
                        ),
                        song = song
                    )
                )
            )

        fun song(id: Long): Song {
            val uri = mock(Uri::class.java)
            `when`(uri.toString()).thenReturn("content://media/$id")
            return Song(
                id = id,
                title = "Song $id",
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
