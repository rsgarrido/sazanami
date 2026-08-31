package io.github.rsgarrido.sazanami.controller

import io.github.rsgarrido.sazanami.data.PlaybackQueueRepository
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.membershipKey
import io.github.rsgarrido.sazanami.data.local.PlaybackQueueEntity
import io.github.rsgarrido.sazanami.data.local.PlaybackQueueEntryEntity
import io.github.rsgarrido.sazanami.player.PlaybackController
import io.github.rsgarrido.sazanami.player.RoomPlaybackQueueTrackAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class PlaybackQueueCardUiState(
    val queueId: String,
    val name: String,
    val entryCount: Int,
    val currentPosition: Int?,
    val currentTrack: Song?,
    val representativeTrack: Song?,
    val lastActiveAt: Long,
    val isActive: Boolean,
    val isSelected: Boolean
) {
    val stateLabel: String?
        get() = when {
            isActive -> "PLAYING"
            isSelected -> "VIEWING"
            else -> null
        }
}

data class PlaybackQueueEntryUiState(
    val entryId: String,
    val song: Song?,
    val isCurrent: Boolean
)

data class PlaybackQueueHubUiState(
    val isLoading: Boolean = true,
    val queues: List<PlaybackQueueCardUiState> = emptyList(),
    val activeQueueId: String? = null,
    val selectedQueueId: String? = null,
    val selectedEntries: List<PlaybackQueueEntryUiState> = emptyList(),
    val selectedQueueEntryCount: Int = 0,
    val isSwitching: Boolean = false,
    val isCreating: Boolean = false,
    val message: String? = null
) {
    val selectedQueue: PlaybackQueueCardUiState?
        get() = queues.firstOrNull { queue -> queue.queueId == selectedQueueId }
}

internal data class LoadedQueueForUi(
    val queue: PlaybackQueueEntity,
    val entries: List<LoadedQueueEntryForUi>
)

internal data class LoadedQueueEntryForUi(
    val entry: PlaybackQueueEntryEntity,
    val song: Song?
)

internal data class LiveActiveQueueForUi(
    val songs: List<Song>
)

internal interface PlaybackQueueUiOperations {
    fun observeQueues(): Flow<List<PlaybackQueueEntity>>
    fun observeLiveActiveQueue(): Flow<LiveActiveQueueForUi?>
    suspend fun listQueues(): List<PlaybackQueueEntity>
    suspend fun getActiveQueueId(): String?
    suspend fun loadQueue(queueId: String): LoadedQueueForUi?
    suspend fun switchQueue(queueId: String): Boolean
    suspend fun createQueueFromCurrent(): String?
    suspend fun renameQueue(queueId: String, name: String): Boolean
    suspend fun deleteQueue(queueId: String): Boolean
}

internal class RoomPlaybackQueueUiOperations(
    private val repository: PlaybackQueueRepository,
    private val playbackController: PlaybackController,
    database: io.github.rsgarrido.sazanami.data.local.AppDatabase,
    catalogSongs: suspend () -> List<Song>
) : PlaybackQueueUiOperations {
    private val trackAccess = RoomPlaybackQueueTrackAccess(database, catalogSongs)

    override fun observeQueues(): Flow<List<PlaybackQueueEntity>> = repository.observeQueues()

    override fun observeLiveActiveQueue(): Flow<LiveActiveQueueForUi?> =
        playbackController.uiState
            .map { state ->
                val currentSong = state.currentSong
                if (!state.isConnected || currentSong == null) {
                    null
                } else {
                    LiveActiveQueueForUi(
                        songs = buildList {
                            add(currentSong)
                            addAll(state.queuedSongs)
                            addAll(state.upcomingSongs)
                        }
                    )
                }
            }
            .distinctUntilChanged()

    override suspend fun listQueues(): List<PlaybackQueueEntity> = repository.listQueues()

    override suspend fun getActiveQueueId(): String? = repository.getActiveQueueId()

    override suspend fun loadQueue(queueId: String): LoadedQueueForUi? {
        val persisted = repository.loadQueue(queueId) ?: return null
        val resolvedByEntryId = trackAccess.resolve(persisted.entries)
            .associate { resolved -> resolved.persistedEntry.entryId to resolved.song }
        return LoadedQueueForUi(
            queue = persisted.queue,
            entries = persisted.entries.map { entry ->
                LoadedQueueEntryForUi(entry, resolvedByEntryId[entry.entryId])
            }
        )
    }

    override suspend fun switchQueue(queueId: String): Boolean =
        playbackController.switchActiveQueue(queueId)

    override suspend fun createQueueFromCurrent(): String? =
        playbackController.createQueueFromCurrent()

    override suspend fun renameQueue(queueId: String, name: String): Boolean =
        repository.renameQueue(queueId, name)

    override suspend fun deleteQueue(queueId: String): Boolean =
        repository.deleteInactiveQueueIfNotLast(queueId)
}

internal class PlaybackQueueUiController(
    private val operations: PlaybackQueueUiOperations,
    private val scope: CoroutineScope
) {
    private val selectedQueueId = MutableStateFlow<String?>(null)
    private var latestLiveActiveQueue: LiveActiveQueueForUi? = null
    private var latestLoadedQueues: List<LoadedQueueForUi> = emptyList()
    private var latestActiveQueueId: String? = null
    private var hasLoadedPersistedQueues = false
    private val _state = MutableStateFlow(PlaybackQueueHubUiState())
    val state: StateFlow<PlaybackQueueHubUiState> = _state.asStateFlow()

    init {
        scope.launch {
            try {
                operations.observeQueues().collectLatest { queues ->
                    refresh(queues, latestLiveActiveQueue)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    message = "Unable to load saved queues."
                )
            }
        }
        scope.launch {
            try {
                operations.observeLiveActiveQueue().collectLatest { liveQueue ->
                    latestLiveActiveQueue = liveQueue
                    publishCachedState()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value = _state.value.copy(
                    message = "Live queue updates are temporarily unavailable."
                )
            }
        }
    }

    fun selectQueue(queueId: String) {
        if (_state.value.queues.none { queue -> queue.queueId == queueId }) return
        selectedQueueId.value = queueId
        _state.value = _state.value.copy(isLoading = true, message = null)
        scope.launch { refreshSelection() }
    }

    fun switchSelectedQueue(): Job = scope.launch {
        val selected = _state.value.selectedQueue ?: return@launch
        if (selected.isActive || _state.value.isSwitching) return@launch
        _state.value = _state.value.copy(isSwitching = true, message = null)
        val switched = try {
            operations.switchQueue(selected.queueId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
        _state.value = _state.value.copy(
            isSwitching = false,
            message = if (switched) null else "Unable to switch queues. Current playback was kept."
        )
        if (switched) {
            latestLiveActiveQueue = null
            refreshSelection()
        }
    }

    fun createQueueFromCurrent(): Job = scope.launch {
        if (_state.value.isCreating) return@launch
        _state.value = _state.value.copy(isCreating = true, message = null)
        val createdQueueId = try {
            operations.createQueueFromCurrent()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        if (createdQueueId != null) selectedQueueId.value = createdQueueId
        _state.value = _state.value.copy(
            isCreating = false,
            message = if (createdQueueId == null) {
                "Start playback before creating a queue from the current session."
            } else {
                null
            }
        )
        refreshSelection()
    }

    fun renameQueue(queueId: String, proposedName: String): Job = scope.launch {
        val validated = validatedQueueName(proposedName)
        if (validated == null) {
            _state.value = _state.value.copy(message = "Queue name cannot be blank.")
            return@launch
        }
        val renamed = try {
            operations.renameQueue(queueId, validated)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
        _state.value = _state.value.copy(
            message = if (renamed) null else "Unable to rename queue."
        )
    }

    fun deleteQueue(queueId: String): Job = scope.launch {
        val current = _state.value
        val queues: List<PlaybackQueueEntity>
        val activeQueueId: String?
        try {
            queues = operations.listQueues()
            activeQueueId = operations.getActiveQueueId()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            _state.value = current.copy(message = "Unable to delete queue.")
            return@launch
        }
        when {
            queues.size <= 1 -> {
                _state.value = current.copy(message = "The only remaining queue cannot be deleted.")
            }
            queueId == activeQueueId -> {
                _state.value = current.copy(message = "The active queue cannot be deleted.")
            }
            else -> {
                val deleted = try {
                    operations.deleteQueue(queueId)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    false
                }
                _state.value = _state.value.copy(
                    message = if (deleted) null else "Unable to delete queue."
                )
            }
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private suspend fun refresh(
        queues: List<PlaybackQueueEntity>,
        liveActiveQueue: LiveActiveQueueForUi?
    ) {
        val activeQueueId = operations.getActiveQueueId()
        val selectedId = selectedQueueId.value
            ?.takeIf { candidate -> queues.any { queue -> queue.queueId == candidate } }
            ?: activeQueueId?.takeIf { candidate -> queues.any { queue -> queue.queueId == candidate } }
            ?: queues.firstOrNull()?.queueId
        selectedQueueId.value = selectedId

        var loadFailed = false
        val loaded = queues.mapNotNull { queue ->
            try {
                operations.loadQueue(queue.queueId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                loadFailed = true
                null
            }
        }
        val loadedById = loaded.associateBy { item -> item.queue.queueId }
        latestLoadedQueues = queues.mapNotNull { queue -> loadedById[queue.queueId] }
        latestActiveQueueId = activeQueueId
        hasLoadedPersistedQueues = true
        _state.value = buildState(
            loadedQueues = latestLoadedQueues,
            activeQueueId = latestActiveQueueId,
            selectedQueueId = selectedId,
            liveActiveQueue = liveActiveQueue,
            previous = _state.value
        ).let { state ->
            if (loadFailed && state.message == null) {
                state.copy(message = "Some queues could not be loaded.")
            } else {
                state
            }
        }
    }

    private suspend fun refreshSelection() {
        refresh(operations.listQueues(), latestLiveActiveQueue)
    }

    private fun publishCachedState() {
        if (!hasLoadedPersistedQueues) return
        _state.value = buildState(
            loadedQueues = latestLoadedQueues,
            activeQueueId = latestActiveQueueId,
            selectedQueueId = selectedQueueId.value,
            liveActiveQueue = latestLiveActiveQueue,
            previous = _state.value
        )
    }
}

internal fun validatedQueueName(proposedName: String): String? =
    proposedName.trim().takeIf(String::isNotEmpty)

internal fun buildState(
    loadedQueues: List<LoadedQueueForUi>,
    activeQueueId: String?,
    selectedQueueId: String?,
    liveActiveQueue: LiveActiveQueueForUi? = null,
    previous: PlaybackQueueHubUiState = PlaybackQueueHubUiState()
): PlaybackQueueHubUiState {
    val selected = loadedQueues.firstOrNull { loaded ->
        loaded.queue.queueId == selectedQueueId
    }
    val selectedCurrentEntryId = selected?.queue?.currentEntryId
    val cards = loadedQueues.map { loaded ->
        val isActive = loaded.queue.queueId == activeQueueId
        val liveSongs = liveActiveQueue?.songs.takeIf { isActive }
        val currentIndex = loaded.entries.indexOfFirst { item ->
            item.entry.entryId == loaded.queue.currentEntryId
        }.takeIf { index -> index >= 0 }
        val currentSong = liveSongs?.firstOrNull()
            ?: currentIndex?.let { index -> loaded.entries[index].song }
        PlaybackQueueCardUiState(
            queueId = loaded.queue.queueId,
            name = loaded.queue.displayName,
            entryCount = liveSongs?.size ?: loaded.entries.size,
            currentPosition = if (liveSongs != null) 1 else currentIndex?.plus(1),
            currentTrack = currentSong,
            representativeTrack = currentSong
                ?: loaded.entries.firstNotNullOfOrNull { it.song },
            lastActiveAt = loaded.queue.lastActiveAt,
            isActive = isActive,
            isSelected = loaded.queue.queueId == selectedQueueId
        )
    }
    val liveSelectedEntries = liveActiveQueue?.takeIf {
        selected?.queue?.queueId == activeQueueId
    }?.let { liveQueue ->
        buildLiveActiveQueueEntries(
            persistedEntries = selected?.entries.orEmpty(),
            liveSongs = liveQueue.songs
        )
    }
    val selectedEntries = liveSelectedEntries ?: selected?.entries.orEmpty().map { item ->
        PlaybackQueueEntryUiState(
            entryId = item.entry.entryId,
            song = item.song,
            isCurrent = item.entry.entryId == selectedCurrentEntryId
        )
    }
    return previous.copy(
        isLoading = false,
        queues = cards,
        activeQueueId = activeQueueId,
        selectedQueueId = selected?.queue?.queueId,
        selectedEntries = selectedEntries,
        selectedQueueEntryCount = selectedEntries.size
    )
}

internal fun buildLiveActiveQueueEntries(
    persistedEntries: List<LoadedQueueEntryForUi>,
    liveSongs: List<Song>
): List<PlaybackQueueEntryUiState> {
    val unmatchedPersistedEntries = persistedEntries.toMutableList()
    return liveSongs.mapIndexed { index, song ->
        val membershipKey = song.membershipKey()
        val persistedIndex = unmatchedPersistedEntries.indexOfFirst { item ->
            item.song?.membershipKey() == membershipKey
        }
        val entryId = if (persistedIndex >= 0) {
            unmatchedPersistedEntries.removeAt(persistedIndex).entry.entryId
        } else {
            "live:$index:$membershipKey"
        }
        PlaybackQueueEntryUiState(
            entryId = entryId,
            song = song,
            isCurrent = index == 0
        )
    }
}
