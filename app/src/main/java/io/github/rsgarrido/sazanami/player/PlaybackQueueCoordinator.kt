package io.github.rsgarrido.sazanami.player

import androidx.media3.common.MediaItem
import io.github.rsgarrido.sazanami.data.PlaybackQueueEntryDraft
import io.github.rsgarrido.sazanami.data.PlaybackQueueRepository
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.membershipKey
import io.github.rsgarrido.sazanami.data.toSongReference
import io.github.rsgarrido.sazanami.data.local.PersistedQueueRepeatMode
import io.github.rsgarrido.sazanami.data.local.PlaybackQueueEntity
import io.github.rsgarrido.sazanami.data.local.PlaybackQueueEntryEntity
import io.github.rsgarrido.sazanami.data.local.PlaybackQueueWithEntries
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

internal data class LivePlaybackQueueItem(
    val entryId: String,
    val evidence: ListeningMediaItemEvidence
)

internal data class LivePlaybackQueueSnapshot(
    val entries: List<LivePlaybackQueueItem>,
    val baseEntryIds: List<String> = entries.map(LivePlaybackQueueItem::entryId),
    val currentEntryId: String?,
    val currentPositionMs: Long,
    val shouldPlay: Boolean,
    val shuffleEnabled: Boolean,
    val repeatMode: PersistedQueueRepeatMode
)

internal data class IdentifiedLivePlaybackQueueItem(
    val liveItem: LivePlaybackQueueItem,
    val trackIdentityId: Long,
    val localTrackBindingId: Long?
)

internal data class ResolvedPlaybackQueueItem(
    val persistedEntry: PlaybackQueueEntryEntity,
    val song: Song
)

internal data class PlaybackQueueRestoration(
    val queueId: String,
    val entries: List<ResolvedPlaybackQueueItem>,
    val currentEntryId: String,
    val currentPositionMs: Long,
    val shouldPlay: Boolean,
    val shuffleEnabled: Boolean,
    val repeatMode: PersistedQueueRepeatMode
)

internal data class BackupRestorePlaybackSnapshot(
    val mediaItems: List<MediaItem>,
    val currentIndex: Int,
    val currentPositionMs: Long,
    val shouldPlay: Boolean,
    val shuffleEnabled: Boolean,
    val repeatMode: Int
)

internal data class PlaybackQueueEntryRemoval(
    val queueId: String,
    val entry: PlaybackQueueEntryEntity,
    val resolvedItem: ResolvedPlaybackQueueItem?,
    val wasActive: Boolean,
    val originalCurrentEntryId: String?,
    val originalCurrentPositionMs: Long
)

/** Maps a new source context's canonical occurrence order onto its freshly built live timeline. */
internal fun captureCanonicalBaseEntryIds(
    canonicalReferenceKeys: List<String>,
    playbackEntries: List<Pair<String, String>>
): List<String>? {
    if (
        canonicalReferenceKeys.any(String::isBlank) ||
        playbackEntries.isEmpty() ||
        playbackEntries.any { (entryId, referenceKey) ->
            entryId.isBlank() || referenceKey.isBlank()
        } ||
        playbackEntries.map { entry -> entry.first }.distinct().size != playbackEntries.size
    ) {
        return null
    }

    val unmatchedPlaybackEntries = playbackEntries.toMutableList()
    val canonicalEntryIds = mutableListOf<String>()
    canonicalReferenceKeys.forEach { referenceKey ->
        val matchingIndex = unmatchedPlaybackEntries.indexOfFirst { (_, candidateKey) ->
            candidateKey == referenceKey
        }
        if (matchingIndex < 0) return null
        canonicalEntryIds += unmatchedPlaybackEntries.removeAt(matchingIndex).first
    }
    canonicalEntryIds += unmatchedPlaybackEntries.map { entry -> entry.first }
    return canonicalEntryIds
}

internal interface PlaybackQueueRuntime {
    /** Checked again after suspended resolution, before activation has any playback side effects. */
    fun canReplaceTimeline(): Boolean = true
    fun canActivate(restoration: PlaybackQueueRestoration): Boolean = canReplaceTimeline()
    fun canSelectEntry(entryId: String): Boolean = true
    fun captureSnapshot(): LivePlaybackQueueSnapshot?
    fun replaceTimeline(restoration: PlaybackQueueRestoration)
    /** Direct acknowledgment must come from its native request, never a Media3 placeholder. */
    suspend fun replaceTimelineAndAwait(restoration: PlaybackQueueRestoration): Boolean {
        replaceTimeline(restoration)
        return true
    }
    suspend fun seekToEntryAndAwait(entryId: String): Boolean = seekToEntry(entryId)
    suspend fun restoreColdTimeline(restoration: PlaybackQueueRestoration): Boolean = replaceTimelineAndAwait(restoration)
    fun canCheckpoint(): Boolean = true
    fun usesDirectOutput(): Boolean = false
    fun captureSelectionSnapshot(): LivePlaybackQueueSnapshot? = captureSnapshot()
    fun requestedPlaying(): Boolean = captureSelectionSnapshot()?.shouldPlay ?: false
    fun captureBaseSongs(): List<Song> = emptyList()
    fun restoreBaseSongs(songs: List<Song>,shuffle: Boolean) {}
    fun canRestoreBackup(): Boolean = true
    fun captureForBackupRestore(): BackupRestorePlaybackSnapshot? = null
    fun clearForBackupRestore() {}
    fun restoreAfterFailedBackupRestore(snapshot: BackupRestorePlaybackSnapshot) {}
    fun completeBackupRestore() {}
    fun removeEntry(entryId: String): Boolean = false
    fun moveEntry(entryId: String, toPlaybackOrder: Int): Boolean = false
    fun seekToEntry(entryId: String): Boolean = false
    fun insertEntry(item: ResolvedPlaybackQueueItem, playbackOrder: Int): Boolean = false
}

/** Keeps the existing pre-Direct Android snapshot and its saved queue identity together. */
internal interface PlaybackQueueOutputObserver {
    fun onDirectRequested(): Boolean
    fun onDirectAbandoned() {}
    suspend fun beforeDirectRelease()
    fun onAndroidRequested()
    suspend fun beforeAndroidRestore()
    suspend fun afterAndroidRestore()
    fun onAndroidRestored() {}
}

internal interface PlaybackQueueTrackAccess {
    suspend fun identify(
        items: List<LivePlaybackQueueItem>
    ): List<IdentifiedLivePlaybackQueueItem>

    suspend fun resolve(
        entries: List<PlaybackQueueEntryEntity>
    ): List<ResolvedPlaybackQueueItem>
}

internal interface PlaybackQueuePersistence {
    suspend fun listQueues(): List<PlaybackQueueEntity>
    suspend fun getActiveQueueId(): String?
    suspend fun setActiveQueue(queueId: String?)
    suspend fun loadQueue(queueId: String): PlaybackQueueWithEntries?

    suspend fun createQueue(
        queueId: String,
        displayName: String,
        entries: List<PlaybackQueueEntryDraft>,
        currentEntryId: String?,
        currentPositionMs: Long,
        shuffleEnabled: Boolean,
        repeatMode: PersistedQueueRepeatMode
    ): PlaybackQueueWithEntries

    suspend fun replaceEntries(
        queueId: String,
        entries: List<PlaybackQueueEntryDraft>,
        currentEntryId: String?,
        currentPositionMs: Long
    ): PlaybackQueueWithEntries

    suspend fun updateSavedPlaybackState(
        queueId: String,
        currentEntryId: String?,
        currentPositionMs: Long,
        shuffleEnabled: Boolean,
        repeatMode: PersistedQueueRepeatMode
    )

    suspend fun duplicateQueue(
        sourceQueueId: String,
        displayName: String
    ): PlaybackQueueWithEntries

    suspend fun appendEntries(
        queueId: String,
        entries: List<PlaybackQueueEntryDraft>
    ): PlaybackQueueWithEntries = error("Appending queue entries is not supported")

    suspend fun removeEntry(
        queueId: String,
        entryId: String
    ): PlaybackQueueWithEntries? = error("Removing queue entries is not supported")

    suspend fun reorderEntry(
        queueId: String,
        entryId: String,
        toPlaybackOrder: Int,
        updateBaseOrder: Boolean
    ): PlaybackQueueWithEntries? = error("Reordering queue entries is not supported")

    suspend fun restoreEntry(
        queueId: String,
        entry: PlaybackQueueEntryEntity,
        restoredCurrentEntryId: String? = null,
        restoredCurrentPositionMs: Long? = null
    ): PlaybackQueueWithEntries? = error("Restoring queue entries is not supported")
}

internal class RepositoryPlaybackQueuePersistence(
    private val repository: PlaybackQueueRepository
) : PlaybackQueuePersistence {
    override suspend fun listQueues(): List<PlaybackQueueEntity> = repository.listQueues()

    override suspend fun getActiveQueueId(): String? = repository.getActiveQueueId()

    override suspend fun setActiveQueue(queueId: String?) = repository.setActiveQueue(queueId)

    override suspend fun loadQueue(queueId: String): PlaybackQueueWithEntries? =
        repository.loadQueue(queueId)

    override suspend fun createQueue(
        queueId: String,
        displayName: String,
        entries: List<PlaybackQueueEntryDraft>,
        currentEntryId: String?,
        currentPositionMs: Long,
        shuffleEnabled: Boolean,
        repeatMode: PersistedQueueRepeatMode
    ): PlaybackQueueWithEntries = repository.createQueue(
        queueId = queueId,
        displayName = displayName,
        entries = entries,
        currentEntryId = currentEntryId,
        currentPositionMs = currentPositionMs,
        shuffleEnabled = shuffleEnabled,
        repeatMode = repeatMode
    )

    override suspend fun replaceEntries(
        queueId: String,
        entries: List<PlaybackQueueEntryDraft>,
        currentEntryId: String?,
        currentPositionMs: Long
    ): PlaybackQueueWithEntries = repository.replaceEntries(
        queueId = queueId,
        entries = entries,
        currentEntryId = currentEntryId,
        currentPositionMs = currentPositionMs
    )

    override suspend fun updateSavedPlaybackState(
        queueId: String,
        currentEntryId: String?,
        currentPositionMs: Long,
        shuffleEnabled: Boolean,
        repeatMode: PersistedQueueRepeatMode
    ) = repository.updateSavedPlaybackState(
        queueId = queueId,
        currentEntryId = currentEntryId,
        currentPositionMs = currentPositionMs,
        shuffleEnabled = shuffleEnabled,
        repeatMode = repeatMode
    )

    override suspend fun duplicateQueue(
        sourceQueueId: String,
        displayName: String
    ): PlaybackQueueWithEntries = repository.duplicateQueue(sourceQueueId, displayName)

    override suspend fun appendEntries(
        queueId: String,
        entries: List<PlaybackQueueEntryDraft>
    ): PlaybackQueueWithEntries = repository.appendEntries(queueId, entries)

    override suspend fun removeEntry(
        queueId: String,
        entryId: String
    ): PlaybackQueueWithEntries? = repository.removeEntry(queueId, entryId)

    override suspend fun reorderEntry(
        queueId: String,
        entryId: String,
        toPlaybackOrder: Int,
        updateBaseOrder: Boolean
    ): PlaybackQueueWithEntries? = repository.reorderEntry(
        queueId = queueId,
        entryId = entryId,
        toPlaybackOrder = toPlaybackOrder,
        updateBaseOrder = updateBaseOrder
    )

    override suspend fun restoreEntry(
        queueId: String,
        entry: PlaybackQueueEntryEntity,
        restoredCurrentEntryId: String?,
        restoredCurrentPositionMs: Long?
    ): PlaybackQueueWithEntries? = repository.restoreEntry(
        queueId,
        entry,
        restoredCurrentEntryId = restoredCurrentEntryId,
        restoredCurrentPositionMs = restoredCurrentPositionMs
    )
}

/** Owns the mapping between the one live Media3 timeline and inactive Room-backed queues. */
internal class PlaybackQueueCoordinator(
    private val persistence: PlaybackQueuePersistence,
    private val trackAccess: PlaybackQueueTrackAccess,
    private val runtime: PlaybackQueueRuntime,
    private val generatedQueueName: (Int) -> String,
    private val queueIdFactory: () -> String = { java.util.UUID.randomUUID().toString() },
    private val onActiveQueueChanged: (String?) -> Unit = {}
) : PlaybackQueueOutputObserver {
    private val mutex = Mutex()
    private var operationSerial=0L
    private var outputEpoch=0L
    private var pendingOperation: QueueOperation?=null
    private var queueStateConfirmed=true
    private var outputCheckpointFrozen=false
    private var androidQueue: OutputQueueSnapshot?=null
    private var leavingDirectSnapshot: LivePlaybackQueueSnapshot?=null
    private data class QueueOperation(val serial: Long,val epoch: Long,val activation: Boolean)
    private data class OutputQueueSnapshot(var queueId: String?,val live: LivePlaybackQueueSnapshot?,
        val baseSongs: List<Song>,var signature: LiveQueueSignature?,var baseIds: List<String>)
    @Volatile
    private var activeQueueId: String? = null
    @Volatile
    private var lastLiveSignature: LiveQueueSignature? = null
    @Volatile
    private var rememberedBaseEntryIds: List<String> = emptyList()
    @Volatile
    private var pendingCanonicalBaseEntryIds: List<String> = emptyList()
    @Volatile
    private var backupRestoreInProgress = false
    private var backupRestorePlayback: BackupRestorePlaybackSnapshot? = null

    suspend fun beginBackupRestore() = mutex.withLock {
        check(!backupRestoreInProgress) { "Backup restore is already in progress" }
        check(pendingOperation==null && !outputCheckpointFrozen && runtime.canRestoreBackup()) {
            "Backup restoration is unavailable during Direct playback or a queue transition"
        }
        val playback = runtime.captureForBackupRestore()
        val previousActiveQueueId = activeQueueId
        backupRestoreInProgress = true
        backupRestorePlayback = playback
        activeQueueId = null
        onActiveQueueChanged(null)
        try {
            runtime.clearForBackupRestore()
        } catch (failure: Throwable) {
            activeQueueId = previousActiveQueueId
            onActiveQueueChanged(previousActiveQueueId)
            try {
                if (playback != null) {
                    runtime.restoreAfterFailedBackupRestore(playback)
                } else if (previousActiveQueueId != null) {
                    restoreIntoEmptyRuntime(previousActiveQueueId)
                }
            } catch (recoveryFailure: Throwable) {
                failure.addSuppressed(recoveryFailure)
            } finally {
                backupRestoreInProgress = false
                backupRestorePlayback = null
            }
            throw failure
        }
    }

    suspend fun finishBackupRestore(databaseCommitted: Boolean) = mutex.withLock {
        if (!backupRestoreInProgress) return@withLock
        try {
            if (databaseCommitted) {
                runtime.completeBackupRestore()
                activeQueueId = null
                lastLiveSignature = null
                rememberedBaseEntryIds = emptyList()
                pendingCanonicalBaseEntryIds = emptyList()
            } else {
                activeQueueId = persistence.getActiveQueueId()
                backupRestorePlayback?.let(runtime::restoreAfterFailedBackupRestore)
                if (backupRestorePlayback == null && activeQueueId != null) {
                    restoreIntoEmptyRuntime(requireNotNull(activeQueueId))
                }
                lastLiveSignature = null
            }
            onActiveQueueChanged(activeQueueId)
        } finally {
            backupRestorePlayback = null
            backupRestoreInProgress = false
        }
    }

    suspend fun initialize(): String? = mutex.withLock {
        if (backupRestoreInProgress) return@withLock null
        activeQueueId = persistence.getActiveQueueId()
        val activeId = activeQueueId
        if (activeId != null) {
            val live = runtime.captureSnapshot()
            if (live == null) {
                restoreIntoEmptyRuntime(activeId)
            } else {
                val persisted = persistence.loadQueue(activeId)
                val liveEntryIds = live.entries.mapTo(linkedSetOf()) { item -> item.entryId }
                val persistedBaseIds = persisted?.entries.orEmpty()
                    .sortedBy(PlaybackQueueEntryEntity::baseOrder)
                    .map(PlaybackQueueEntryEntity::entryId)
                val rememberedBaseIds = buildList {
                    addAll(persistedBaseIds.filter(liveEntryIds::contains))
                    addAll(live.baseEntryIds.filter { entryId ->
                        entryId in liveEntryIds && entryId !in this
                    })
                    addAll(liveEntryIds.filterNot { entryId -> entryId in this })
                }
                rememberedBaseEntryIds = rememberedBaseIds
            }
        }
        onActiveQueueChanged(activeQueueId)
        activeQueueId
    }

    fun getActiveQueueId(): String? = activeQueueId

    fun captureActiveQueueSnapshot(): LivePlaybackQueueSnapshot? =
        if(pendingOperation!=null || !queueStateConfirmed || outputCheckpointFrozen) null
        else runtime.captureSnapshot()?.withAuthoritativeBaseOrder()

    fun prepareNewPlaybackContext(baseEntryIds: List<String>) {
        if (backupRestoreInProgress) return
        require(baseEntryIds.isNotEmpty()) { "A new playback context must not be empty" }
        require(baseEntryIds.none(String::isBlank)) { "Queue entry IDs must not be blank" }
        require(baseEntryIds.distinct().size == baseEntryIds.size) {
            "Queue entry IDs must identify distinct occurrences"
        }
        pendingCanonicalBaseEntryIds = baseEntryIds.toList()
    }

    suspend fun persistActiveQueueSnapshot(): String? = mutex.withLock {
        persistActiveQueueSnapshotLocked()
    }

    suspend fun createQueueFromCurrent(): PlaybackQueueWithEntries? = mutex.withLock {
        if (backupRestoreInProgress || pendingOperation!=null || !queueStateConfirmed ||
            outputCheckpointFrozen || !runtime.canCheckpoint()) return@withLock null
        val sourceQueueId = persistActiveQueueSnapshotLocked() ?: return@withLock null
        val source = persistence.loadQueue(sourceQueueId) ?: return@withLock null
        if (source.entries.isEmpty()) return@withLock null
        val displayName = nextDefaultQueueNameLocked()
        persistence.duplicateQueue(
            sourceQueueId = sourceQueueId,
            displayName = displayName
        )
    }

    suspend fun createAndActivateQueue(
        displayName: String,
        songs: List<Song>
    ): PlaybackQueueWithEntries? {
        val (created,restoration,operation)=mutex.withLock {
        if (backupRestoreInProgress || outputCheckpointFrozen || !runtime.canReplaceTimeline()) return null
        if (songs.isEmpty()) return null
        val epoch=outputEpoch
        persistActiveQueueSnapshotLocked()
        if (!runtime.canReplaceTimeline() || epoch!=outputEpoch) return null
        val creation = createPopulatedQueueLocked(displayName, songs, activationRequired = true)
            ?: return null
        if (!runtime.canReplaceTimeline() || epoch!=outputEpoch) return null
        val created = creation.queue
        val drafts = creation.drafts
        val identified = creation.identified
        val queueId = created.queue.queueId
        val restorationEntries = identified.mapIndexed { index, item ->
            ResolvedPlaybackQueueItem(
                persistedEntry = drafts[index].toEntity(queueId),
                song = songs[index]
            )
        }
        val restoration=PlaybackQueueRestoration(
                queueId = queueId,
                entries = restorationEntries,
                currentEntryId = drafts.first().entryId,
                currentPositionMs = 0L,
                shouldPlay = true,
                shuffleEnabled = false,
                repeatMode = PersistedQueueRepeatMode.OFF
            )
        if(!runtime.canActivate(restoration)) return null
        Triple(created,restoration,beginQueueOperation(activation=true))
        }
        try {
        if(!runtime.replaceTimelineAndAwait(restoration)) return null
        return mutex.withLock {
        if(!owns(operation) || !runtime.canCheckpoint()) return@withLock null
        val queueId=created.queue.queueId
        val accepted=runtime.captureSnapshot() ?: return@withLock null
        persistence.updateSavedPlaybackState(queueId,accepted.currentEntryId,accepted.currentPositionMs,
            restoration.shuffleEnabled,restoration.repeatMode)
        if(!owns(operation)) return@withLock null
        if(!setActiveQueueWhileOwned(queueId,operation)) return@withLock null
        activeQueueId = queueId
        lastLiveSignature = LiveQueueSignature(
            queueId = queueId,
            entries = restoration.entries.map { it.persistedEntry.entryId to it.song.membershipKey() },
            baseEntryIds = restoration.entries.map { it.persistedEntry.entryId }
        )
        rememberedBaseEntryIds = lastLiveSignature?.baseEntryIds.orEmpty()
        pendingOperation=null;queueStateConfirmed=true
        onActiveQueueChanged(queueId)
        created
        }
        } finally { abandon(operation) }
    }

    suspend fun createInactiveQueue(
        songs: List<Song>
    ): PlaybackQueueWithEntries? = mutex.withLock {
        if (backupRestoreInProgress) return@withLock null
        createPopulatedQueueLocked(displayName = "", songs = songs)?.queue
    }

    /** The existing library/context path supplies its already-established explicit occurrence order. */
    suspend fun replacePlaybackContext(songs: List<Song>,items: List<MediaItem>,index: Int,positionMs: Long,
        playing: Boolean,baseEntryIds: List<String>,shuffle: Boolean,repeat: PersistedQueueRepeatMode): Boolean {
        val (restoration,operation)=mutex.withLock {
            if(backupRestoreInProgress || outputCheckpointFrozen || !runtime.canReplaceTimeline() ||
                songs.size!=items.size || index !in items.indices) return false
            val epoch=outputEpoch
            val live=items.map { item ->
                val evidence=item.listeningEvidence() ?: return false
                LivePlaybackQueueItem(evidence.itemInstanceId,evidence)
            }
            val ids=live.map { it.entryId }
            if(ids.distinct().size!=ids.size || baseEntryIds.size!=ids.size || baseEntryIds.toSet()!=ids.toSet()) return false
            val identified=trackAccess.identify(live)
            if(identified.size!=items.size || epoch!=outputEpoch) return false
            val baseOrders=baseEntryIds.withIndex().associate { it.value to it.index }
            val entries=identified.mapIndexed { order,item ->
                ResolvedPlaybackQueueItem(item.toDraft(checkNotNull(baseOrders[item.liveItem.entryId]),order)
                    .toEntity(activeQueueId ?: ""),songs[order])
            }
            val restoration=PlaybackQueueRestoration(activeQueueId ?: "",entries,ids[index],positionMs,playing,shuffle,repeat)
            if(!runtime.canActivate(restoration)) return false
            persistActiveQueueSnapshotLocked()
            if(epoch!=outputEpoch || !runtime.canActivate(restoration)) return false
            restoration to beginQueueOperation(activation=true)
        }
        try {
            if(!runtime.replaceTimelineAndAwait(restoration)) return false
            return mutex.withLock {
                if(!owns(operation) || !runtime.canCheckpoint() || runtime.captureSnapshot()?.currentEntryId!=restoration.currentEntryId) return@withLock false
                pendingCanonicalBaseEntryIds=baseEntryIds.toList()
                val accepted=runtime.captureSnapshot() ?: return@withLock false
                persistActiveQueueSnapshotLocked(accepted,frozenOutputSnapshot=true,operation=operation)
                if(!owns(operation)) return@withLock false
                pendingOperation=null;queueStateConfirmed=true
                onActiveQueueChanged(activeQueueId)
                true
            }
        } finally { abandon(operation) }
    }

    suspend fun appendToInactiveQueue(
        queueId: String,
        songs: List<Song>
    ): PlaybackQueueWithEntries? = mutex.withLock {
        if (backupRestoreInProgress) return@withLock null
        if (songs.isEmpty() || queueId == activeQueueId) return@withLock null
        val identified = trackAccess.identify(songs.map { it.toLivePlaybackQueueItem() })
        if (identified.size != songs.size) return@withLock null
        persistence.appendEntries(
            queueId = queueId,
            entries = identified.mapIndexed { index, item ->
                item.toDraft(baseOrder = index, playbackOrder = index)
            }
        )
    }

    suspend fun removeEntry(queueId: String, entryId: String): Boolean = mutex.withLock {
        removeEntryForUndoLocked(queueId, entryId) != null
    }

    suspend fun removeEntryForUndo(
        queueId: String,
        entryId: String
    ): PlaybackQueueEntryRemoval? = mutex.withLock {
        removeEntryForUndoLocked(queueId, entryId)
    }

    private suspend fun removeEntryForUndoLocked(
        queueId: String,
        entryId: String
    ): PlaybackQueueEntryRemoval? {
        if (backupRestoreInProgress) return null
        val persisted = persistence.loadQueue(queueId) ?: return null
        val removedEntry = persisted.entries.firstOrNull { it.entryId == entryId } ?: return null
        val wasActive = queueId == activeQueueId
        val resolvedItem = if (wasActive) {
            trackAccess.resolve(listOf(removedEntry)).firstOrNull() ?: return null
        } else {
            null
        }
        if (queueId != activeQueueId) {
            persistence.removeEntry(queueId, entryId) ?: return null
            return PlaybackQueueEntryRemoval(
                queueId,
                removedEntry,
                null,
                false,
                persisted.queue.currentEntryId,
                persisted.queue.currentPositionMs
            )
        }
        val before = runtime.captureSnapshot() ?: return null
        if (before.entries.none { it.entryId == entryId }) return null
        if (!runtime.removeEntry(entryId)) return null
        try {
            if (before.entries.size == 1) {
                persistence.replaceEntries(queueId, emptyList(), null, 0L)
                lastLiveSignature = null
                rememberedBaseEntryIds = emptyList()
            } else {
                persistActiveQueueSnapshotLocked()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Media3 is authoritative for the active queue. Keep the successful live removal
            // and force the next checkpoint to reconcile Room instead of reporting a false
            // removal failure after the item has already disappeared from playback.
            lastLiveSignature = null
        }
        return PlaybackQueueEntryRemoval(
            queueId,
            removedEntry,
            resolvedItem,
            true,
            persisted.queue.currentEntryId,
            persisted.queue.currentPositionMs
        )
    }

    suspend fun undoRemoveEntry(removal: PlaybackQueueEntryRemoval): Boolean = mutex.withLock {
        if (backupRestoreInProgress) return@withLock false
        val queue = persistence.loadQueue(removal.queueId) ?: return@withLock false
        if (removal.queueId != activeQueueId) {
            if (queue.entries.any { entry -> entry.entryId == removal.entry.entryId }) {
                return@withLock true
            }
            return@withLock persistence.restoreEntry(
                removal.queueId,
                removal.entry,
                removal.originalCurrentEntryId,
                removal.originalCurrentPositionMs
            ) != null
        }
        val liveAlreadyContainsEntry = runtime.captureSnapshot()?.entries?.any { item ->
            item.entryId == removal.entry.entryId
        } == true
        if (liveAlreadyContainsEntry) return@withLock true
        val resolved = removal.resolvedItem ?: return@withLock false
        if (!runtime.insertEntry(resolved, removal.entry.playbackOrder)) return@withLock false
        persistActiveQueueSnapshotLocked()
        val restored = persistence.restoreEntry(removal.queueId, removal.entry)
            ?: return@withLock false
        rememberedBaseEntryIds = restored.entries
            .sortedBy(PlaybackQueueEntryEntity::baseOrder)
            .map(PlaybackQueueEntryEntity::entryId)
        lastLiveSignature = null
        true
    }

    suspend fun playEntry(queueId: String, entryId: String): Boolean {
        val operation=mutex.withLock {
            if(backupRestoreInProgress || outputCheckpointFrozen || pendingOperation?.activation==true || queueId!=activeQueueId) return false
            val snapshot=runtime.captureSelectionSnapshot() ?: return false
            if(snapshot.currentEntryId==entryId && pendingOperation==null && queueStateConfirmed) return true
            if(snapshot.entries.none { it.entryId==entryId }) return false
            if(!runtime.canSelectEntry(entryId)) return false
            beginQueueOperation(activation=false)
        }
        try {
            if(!runtime.seekToEntryAndAwait(entryId)) return false
            return mutex.withLock {
                if(!owns(operation) || !runtime.canCheckpoint() || runtime.captureSnapshot()?.currentEntryId!=entryId) return@withLock false
                val accepted=runtime.captureSnapshot() ?: return@withLock false
                persistActiveQueueSnapshotLocked(accepted,frozenOutputSnapshot=true)
                if(!owns(operation)) return@withLock false
                pendingOperation=null;queueStateConfirmed=true
                onActiveQueueChanged(activeQueueId)
                true
            }
        } finally { abandon(operation) }
    }

    suspend fun reorderEntry(
        queueId: String,
        entryId: String,
        toPlaybackOrder: Int
    ): Boolean = mutex.withLock {
        if (backupRestoreInProgress) return@withLock false
        if (queueId != activeQueueId) {
            val queue = persistence.loadQueue(queueId) ?: return@withLock false
            return@withLock persistence.reorderEntry(
                queueId,
                entryId,
                toPlaybackOrder,
                updateBaseOrder = !queue.queue.shuffleEnabled
            ) != null
        }
        val before = runtime.captureSnapshot() ?: return@withLock false
        val currentIndex = before.entries.indexOfFirst { item ->
            item.entryId == before.currentEntryId
        }
        val fromIndex = before.entries.indexOfFirst { item -> item.entryId == entryId }
        if (currentIndex >= 0 && (fromIndex <= currentIndex || toPlaybackOrder <= currentIndex)) {
            return@withLock false
        }
        if (!runtime.moveEntry(entryId, toPlaybackOrder)) return@withLock false
        val after = runtime.captureSnapshot() ?: return@withLock false
        if (before.shuffleEnabled) {
            // Capture the edited playback order through the remembered canonical base.
            persistActiveQueueSnapshotLocked(suppliedSnapshot = after)
            return@withLock true
        }
        persistActiveQueueSnapshotLocked(
            suppliedSnapshot = after.copy(
                baseEntryIds = after.entries.map(LivePlaybackQueueItem::entryId)
            ),
            useSuppliedBaseOrder = true
        )
        persistence.reorderEntry(
            queueId = queueId,
            entryId = entryId,
            toPlaybackOrder = toPlaybackOrder,
            updateBaseOrder = true
        )
        true
    }

    suspend fun switchToQueue(queueId: String): Boolean {
        val (restoration,operation)=mutex.withLock {
        if (backupRestoreInProgress || outputCheckpointFrozen || !runtime.canReplaceTimeline()) return false
        val epoch=outputEpoch
        require(queueId.isNotBlank()) { "Queue ID cannot be blank" }
        if (queueId == activeQueueId && pendingOperation==null && queueStateConfirmed &&
            runtime.captureSnapshot()?.entries?.isNotEmpty()==true) return true

        val outgoing = runtime.captureSnapshot()
        val shouldPlay = runtime.requestedPlaying()
        persistActiveQueueSnapshotLocked(outgoing)

        val target = persistence.loadQueue(queueId) ?: return false
        val resolved = trackAccess.resolve(target.entries)
            .sortedBy { item -> item.persistedEntry.playbackOrder }
        if (resolved.isEmpty()) return false

        val selected = selectRestoredCurrentEntry(target, resolved)
        val savedCurrentResolved = target.queue.currentEntryId ==
            selected.persistedEntry.entryId
        val restoredPosition = if (savedCurrentResolved) {
            target.queue.currentPositionMs.coerceAtLeast(0L)
        } else {
            0L
        }
        val restoration = PlaybackQueueRestoration(
            queueId = queueId,
            entries = resolved,
            currentEntryId = selected.persistedEntry.entryId,
            currentPositionMs = restoredPosition,
            shouldPlay = shouldPlay,
            shuffleEnabled = target.queue.shuffleEnabled,
            repeatMode = target.queue.repeatMode
        )

        if (!runtime.canActivate(restoration) || epoch!=outputEpoch) return false
        restoration to beginQueueOperation(activation=true)
        }
        try {
        if(!runtime.replaceTimelineAndAwait(restoration)) return false
        return mutex.withLock {
        if(!owns(operation) || !runtime.canCheckpoint()) return@withLock false
        val resolved=restoration.entries
        val position=runtime.captureSnapshot()?.currentPositionMs ?: return@withLock false
        persistence.updateSavedPlaybackState(
            queueId = queueId,
            currentEntryId = restoration.currentEntryId,
            currentPositionMs = position,
            shuffleEnabled = restoration.shuffleEnabled,
            repeatMode = restoration.repeatMode
        )
        if(!owns(operation)) return@withLock false
        if(!setActiveQueueWhileOwned(queueId,operation)) return@withLock false
        activeQueueId = queueId
        lastLiveSignature = LiveQueueSignature(
            queueId = queueId,
            entries = resolved.map { item ->
                item.persistedEntry.entryId to item.song.membershipKey()
            },
            baseEntryIds = resolved.sortedBy { item -> item.persistedEntry.baseOrder }
                .map { item -> item.persistedEntry.entryId }
        )
        rememberedBaseEntryIds = lastLiveSignature?.baseEntryIds.orEmpty()
        pendingOperation=null;queueStateConfirmed=true
        onActiveQueueChanged(queueId)
        true
        }
        } finally { abandon(operation) }
    }

    private fun beginQueueOperation(activation: Boolean): QueueOperation =
        QueueOperation(++operationSerial,outputEpoch,activation).also {
            pendingOperation=it;queueStateConfirmed=false
        }
    private fun owns(operation: QueueOperation)=pendingOperation==operation && operation.epoch==outputEpoch
    private fun abandon(operation: QueueOperation) { if(owns(operation)) pendingOperation=null }
    private suspend fun setActiveQueueWhileOwned(queueId: String,operation: QueueOperation?=null): Boolean {
        val epoch=outputEpoch
        try { persistence.setActiveQueue(queueId) }
        catch(cancelled: CancellationException) {
            withContext(NonCancellable) { persistence.setActiveQueue(activeQueueId) }
            throw cancelled
        }
        if(epoch!=outputEpoch || !currentCoroutineContext().isActive || (operation!=null && !owns(operation))) {
            withContext(NonCancellable) { persistence.setActiveQueue(activeQueueId) }
            return false
        }
        return true
    }

    override fun onDirectRequested(): Boolean {
        if(backupRestoreInProgress || pendingOperation!=null || outputCheckpointFrozen || !queueStateConfirmed) return false
        androidQueue=OutputQueueSnapshot(activeQueueId,runtime.captureSnapshot()?.withAuthoritativeBaseOrder(),
            runtime.captureBaseSongs(),lastLiveSignature,rememberedBaseEntryIds)
        outputCheckpointFrozen=true
        return true
    }
    override suspend fun beforeDirectRelease() = mutex.withLock {
        val original=androidQueue ?: return@withLock
        outputEpoch++ // Drain earlier Android persistence before changing output transaction authority.
        original.live?.let { persistActiveQueueSnapshotLocked(it,frozenOutputSnapshot=true) }
        original.queueId=activeQueueId;original.signature=lastLiveSignature;original.baseIds=rememberedBaseEntryIds
        queueStateConfirmed=true;outputCheckpointFrozen=false
    }
    override fun onDirectAbandoned() {
        androidQueue=null;outputCheckpointFrozen=false
    }
    override fun onAndroidRequested() {
        leavingDirectSnapshot=if(pendingOperation==null && queueStateConfirmed)
            runtime.captureSnapshot()?.withAuthoritativeBaseOrder() else null
        outputEpoch++;operationSerial++;pendingOperation=null
        outputCheckpointFrozen=true;queueStateConfirmed=false
    }
    override suspend fun beforeAndroidRestore() = mutex.withLock {
        leavingDirectSnapshot?.let { persistActiveQueueSnapshotLocked(it,frozenOutputSnapshot=true) }
        leavingDirectSnapshot=null
    }
    override suspend fun afterAndroidRestore() = mutex.withLock {
        val original=androidQueue ?: run { outputCheckpointFrozen=false;queueStateConfirmed=true;return@withLock }
        // An inactive queue may have been edited/deleted while Direct used another queue.
        // Reuse its identity only while it still owns the frozen Android occurrence/order snapshot.
        val saved=original.queueId?.let { persistence.loadQueue(it) }
        val originalIds=original.live?.entries?.map { it.entryId }.orEmpty()
        val sameStructure=saved!=null && saved.entries.sortedBy { it.playbackOrder }.map { it.entryId }==originalIds &&
            saved.entries.sortedBy { it.baseOrder }.map { it.entryId }==original.baseIds
        activeQueueId=original.queueId.takeIf { sameStructure }
        if(sameStructure) original.live?.let { restored ->
            persistence.updateSavedPlaybackState(requireNotNull(activeQueueId),restored.currentEntryId,
                restored.currentPositionMs,restored.shuffleEnabled,restored.repeatMode)
        }
        persistence.setActiveQueue(activeQueueId)
        lastLiveSignature=original.signature.takeIf { sameStructure }
        rememberedBaseEntryIds=original.baseIds;pendingCanonicalBaseEntryIds=emptyList()
        runtime.restoreBaseSongs(original.baseSongs,original.live?.shuffleEnabled ?: false)
        outputCheckpointFrozen=false;queueStateConfirmed=sameStructure || original.live==null;androidQueue=null
        onActiveQueueChanged(activeQueueId)
        // With changed/deleted saved ownership, retain the approved physical snapshot unassigned.
        // Its old occurrence IDs must not be inserted into or overwrite a different saved queue.
    }
    override fun onAndroidRestored() {
        // The route now exposes Android, so subscribers can capture its authoritative timeline.
        onActiveQueueChanged(activeQueueId)
    }

    private suspend fun persistActiveQueueSnapshotLocked(
        suppliedSnapshot: LivePlaybackQueueSnapshot? = null,
        useSuppliedBaseOrder: Boolean = false,
        frozenOutputSnapshot: Boolean = false,
        operation: QueueOperation? = null
    ): String? {
        if(operation!=null && !owns(operation)) return activeQueueId
        if(!frozenOutputSnapshot && (pendingOperation!=null || outputCheckpointFrozen || !runtime.canCheckpoint())) return activeQueueId
        if (backupRestoreInProgress) return null
        val captured = suppliedSnapshot ?: runtime.captureSnapshot() ?: return activeQueueId
        val snapshotEpoch=outputEpoch
        if(!frozenOutputSnapshot && !queueStateConfirmed) {
            // Only a fresh, accepted Android context may adopt an unassigned restored snapshot.
            if(runtime.usesDirectOutput() || captured.matchingPendingCanonicalBaseOrder()==null) return activeQueueId
            queueStateConfirmed=true
        }
        val pendingCanonicalBase = captured.matchingPendingCanonicalBaseOrder()
        val capturesNewPlaybackContext = !useSuppliedBaseOrder && pendingCanonicalBase != null
        val snapshot = when {
            useSuppliedBaseOrder -> captured
            capturesNewPlaybackContext -> captured.copy(baseEntryIds = pendingCanonicalBase)
            else -> captured.withRememberedBaseOrder()
        }
        if (snapshot.entries.isEmpty()) return activeQueueId

        // A different Direct context must not replace the record owning the frozen Android queue.
        // Allocate its saved identity only after that context has passed native acknowledgment.
        val original=androidQueue
        val queueId = activeQueueId.takeUnless { id ->
            id!=null && runtime.usesDirectOutput() && original?.queueId==id &&
                original?.live?.entries?.map { it.entryId }!=snapshot.entries.map { it.entryId }
        }
        val rawSignature = LiveQueueSignature(
            queueId = queueId,
            entries = snapshot.entries.map { item ->
                item.entryId to item.evidence.referenceKey
            },
            baseEntryIds = normalizedLiveBaseEntryIds(snapshot)
        )
        if (queueId != null && rawSignature == lastLiveSignature) {
            val currentEntryId = snapshot.currentEntryId
                ?.takeIf { id -> snapshot.entries.any { it.entryId == id } }
            persistence.updateSavedPlaybackState(
                queueId = queueId,
                currentEntryId = currentEntryId,
                currentPositionMs = snapshot.currentPositionMs.takeIf {
                    currentEntryId != null
                } ?: 0L,
                shuffleEnabled = snapshot.shuffleEnabled,
                repeatMode = snapshot.repeatMode
            )
            return queueId
        }

        val identified = trackAccess.identify(snapshot.entries)
        if (identified.isEmpty()) return activeQueueId
        val identifiedByLiveId = identified.associateBy { it.liveItem.entryId }
        val currentEntryId = snapshot.currentEntryId
            ?.takeIf(identifiedByLiveId::containsKey)
        val position = snapshot.currentPositionMs.takeIf { currentEntryId != null } ?: 0L

        if (queueId == null) {
            val newQueueId = queueIdFactory()
            val baseOrderById = normalizedBaseEntryIds(snapshot, identified)
                .withIndex()
                .associate { indexed -> indexed.value to indexed.index }
            val drafts = identified.mapIndexed { playbackOrder, item ->
                item.toDraft(
                    baseOrder = checkNotNull(baseOrderById[item.liveItem.entryId]),
                    playbackOrder = playbackOrder
                )
            }
            persistence.createQueue(
                queueId = newQueueId,
                displayName = generatedQueueName(1),
                entries = drafts,
                currentEntryId = currentEntryId,
                currentPositionMs = position,
                shuffleEnabled = snapshot.shuffleEnabled,
                repeatMode = snapshot.repeatMode
            )
            if(snapshotEpoch!=outputEpoch || !setActiveQueueWhileOwned(newQueueId,operation)) return activeQueueId
            activeQueueId = newQueueId
            lastLiveSignature = rawSignature.copy(queueId = newQueueId)
            rememberedBaseEntryIds = rawSignature.baseEntryIds
            clearPendingCanonicalBaseOrder(pendingCanonicalBase)
            onActiveQueueChanged(newQueueId)
            return newQueueId
        }

        val existing = persistence.loadQueue(queueId)
        val drafts = buildReplacementDrafts(
            liveItems = identified,
            existingEntries = if (capturesNewPlaybackContext) {
                emptyList()
            } else {
                existing?.entries.orEmpty()
            },
            preferredBaseEntryIds = snapshot.baseEntryIds
        )
        persistence.replaceEntries(
            queueId = queueId,
            entries = drafts,
            currentEntryId = currentEntryId,
            currentPositionMs = position
        )
        persistence.updateSavedPlaybackState(
            queueId = queueId,
            currentEntryId = currentEntryId,
            currentPositionMs = position,
            shuffleEnabled = snapshot.shuffleEnabled,
            repeatMode = snapshot.repeatMode
        )
        lastLiveSignature = rawSignature.copy(
            baseEntryIds = drafts.sortedBy(PlaybackQueueEntryDraft::baseOrder)
                .map(PlaybackQueueEntryDraft::entryId)
        )
        rememberedBaseEntryIds = lastLiveSignature?.baseEntryIds.orEmpty()
        clearPendingCanonicalBaseOrder(pendingCanonicalBase)
        return queueId
    }

    private fun LivePlaybackQueueSnapshot.withAuthoritativeBaseOrder(): LivePlaybackQueueSnapshot {
        val pendingCanonicalBase = matchingPendingCanonicalBaseOrder()
        return if (pendingCanonicalBase != null) {
            copy(baseEntryIds = pendingCanonicalBase)
        } else {
            withRememberedBaseOrder()
        }
    }

    private fun LivePlaybackQueueSnapshot.matchingPendingCanonicalBaseOrder(): List<String>? {
        val pending = pendingCanonicalBaseEntryIds
        if (pending.isEmpty()) return null
        val liveEntryIds = entries.map(LivePlaybackQueueItem::entryId)
        return pending.takeIf { baseEntryIds ->
            baseEntryIds.size == liveEntryIds.size && baseEntryIds.toSet() == liveEntryIds.toSet()
        }
    }

    private fun clearPendingCanonicalBaseOrder(captured: List<String>?) {
        if (captured != null && pendingCanonicalBaseEntryIds == captured) {
            pendingCanonicalBaseEntryIds = emptyList()
        }
    }

    private fun LivePlaybackQueueSnapshot.withRememberedBaseOrder(): LivePlaybackQueueSnapshot {
        val remembered = rememberedBaseEntryIds
        if (remembered.isEmpty()) return this

        val liveEntryIds = entries.mapTo(linkedSetOf()) { item -> item.entryId }
        val normalized = buildList {
            addAll(remembered.filter(liveEntryIds::contains))
            addAll(baseEntryIds.filter { entryId ->
                entryId in liveEntryIds && entryId !in this
            })
            addAll(liveEntryIds.filterNot { entryId -> entryId in this })
        }
        return copy(baseEntryIds = normalized)
    }

    private suspend fun restoreIntoEmptyRuntime(queueId: String) {
        val epoch=outputEpoch
        if (!runtime.canReplaceTimeline()) return
        val target = persistence.loadQueue(queueId) ?: return
        val resolved = trackAccess.resolve(target.entries)
            .sortedBy { item -> item.persistedEntry.playbackOrder }
        if (resolved.isEmpty()) return
        // A controller may restore its already-live session while exact Room resolution is
        // suspended. In that race, adopting the live session must win over rebuilding it.
        if (epoch!=outputEpoch || runtime.captureSnapshot() != null || !runtime.canReplaceTimeline()) return
        val selected = selectRestoredCurrentEntry(target, resolved)
        val savedCurrentResolved = target.queue.currentEntryId ==
            selected.persistedEntry.entryId
        val position = if (savedCurrentResolved) {
            target.queue.currentPositionMs.coerceAtLeast(0L)
        } else {
            0L
        }
        if(!runtime.restoreColdTimeline(
            PlaybackQueueRestoration(
                queueId = queueId,
                entries = resolved,
                currentEntryId = selected.persistedEntry.entryId,
                currentPositionMs = position,
                shouldPlay = false,
                shuffleEnabled = target.queue.shuffleEnabled,
                repeatMode = target.queue.repeatMode
            )
        )) return
        lastLiveSignature = LiveQueueSignature(
            queueId = queueId,
            entries = resolved.map { item ->
                item.persistedEntry.entryId to item.song.membershipKey()
            },
            baseEntryIds = resolved.sortedBy { item -> item.persistedEntry.baseOrder }
                .map { item -> item.persistedEntry.entryId }
        )
        rememberedBaseEntryIds = lastLiveSignature?.baseEntryIds.orEmpty()
        persistence.updateSavedPlaybackState(
            queueId = queueId,
            currentEntryId = selected.persistedEntry.entryId,
            currentPositionMs = position,
            shuffleEnabled = target.queue.shuffleEnabled,
            repeatMode = target.queue.repeatMode
        )
    }

    private suspend fun createPopulatedQueueLocked(
        displayName: String,
        songs: List<Song>,
        activationRequired: Boolean = false
    ): PopulatedQueueCreation? {
        if (songs.isEmpty()) return null
        val identified = trackAccess.identify(songs.map { song ->
            song.toLivePlaybackQueueItem()
        })
        if (identified.size != songs.size) return null
        val queueId = queueIdFactory()
        val resolvedDisplayName = displayName.trim().takeIf(String::isNotEmpty)
            ?: nextDefaultQueueNameLocked()
        val drafts = identified.mapIndexed { index, item ->
            item.toDraft(baseOrder = index, playbackOrder = index)
        }
        if (activationRequired && !runtime.canReplaceTimeline()) return null
        val created = persistence.createQueue(
            queueId = queueId,
            displayName = resolvedDisplayName,
            entries = drafts,
            currentEntryId = drafts.first().entryId,
            currentPositionMs = 0L,
            shuffleEnabled = false,
            repeatMode = PersistedQueueRepeatMode.OFF
        )
        return PopulatedQueueCreation(created, drafts, identified)
    }

    private fun buildReplacementDrafts(
        liveItems: List<IdentifiedLivePlaybackQueueItem>,
        existingEntries: List<PlaybackQueueEntryEntity>,
        preferredBaseEntryIds: List<String>
    ): List<PlaybackQueueEntryDraft> {
        val unusedExisting = existingEntries.toMutableList()
        val matchedBaseOrder = linkedMapOf<String, Int>()

        liveItems.forEach { live ->
            val exactIndex = unusedExisting.indexOfFirst { existing ->
                existing.entryId == live.liveItem.entryId &&
                    existing.trackIdentityId == live.trackIdentityId
            }
            val identityIndex = if (exactIndex >= 0) exactIndex else {
                unusedExisting.indexOfFirst { existing ->
                    existing.trackIdentityId == live.trackIdentityId
                }
            }
            if (identityIndex >= 0) {
                matchedBaseOrder[live.liveItem.entryId] =
                    unusedExisting.removeAt(identityIndex).baseOrder
            }
        }

        val baseOrderedLiveIds = buildList {
            addAll(matchedBaseOrder.entries.sortedBy { entry -> entry.value }.map { it.key })
            addAll(
                preferredBaseEntryIds.filter { entryId ->
                    entryId !in matchedBaseOrder &&
                        liveItems.any { item -> item.liveItem.entryId == entryId }
                }
            )
            addAll(
                liveItems.map { it.liveItem.entryId }.filterNot { entryId ->
                    entryId in this
                }
            )
        }
        val baseOrderById = baseOrderedLiveIds.withIndex().associate { it.value to it.index }

        return liveItems.mapIndexed { playbackOrder, item ->
            item.toDraft(
                baseOrder = checkNotNull(baseOrderById[item.liveItem.entryId]),
                playbackOrder = playbackOrder
            )
        }
    }

    private fun normalizedBaseEntryIds(
        snapshot: LivePlaybackQueueSnapshot,
        identified: List<IdentifiedLivePlaybackQueueItem>
    ): List<String> = buildList {
        val identifiedIds = identified.mapTo(linkedSetOf()) { item -> item.liveItem.entryId }
        addAll(snapshot.baseEntryIds.filter(identifiedIds::contains))
        addAll(identifiedIds.filterNot { entryId -> entryId in this })
    }

    private fun normalizedLiveBaseEntryIds(
        snapshot: LivePlaybackQueueSnapshot
    ): List<String> = buildList {
        val liveIds = snapshot.entries.mapTo(linkedSetOf()) { item -> item.entryId }
        addAll(snapshot.baseEntryIds.filter(liveIds::contains))
        addAll(liveIds.filterNot { entryId -> entryId in this })
    }

    private fun selectRestoredCurrentEntry(
        target: PlaybackQueueWithEntries,
        resolved: List<ResolvedPlaybackQueueItem>
    ): ResolvedPlaybackQueueItem {
        val savedCurrentId = target.queue.currentEntryId
        resolved.firstOrNull { item ->
            item.persistedEntry.entryId == savedCurrentId
        }?.let { return it }

        val savedOrder = target.entries.firstOrNull { entry ->
            entry.entryId == savedCurrentId
        }?.playbackOrder ?: 0
        return resolved.minWith(
            compareBy<ResolvedPlaybackQueueItem> {
                kotlin.math.abs(it.persistedEntry.playbackOrder - savedOrder)
            }.thenBy { item ->
                if (item.persistedEntry.playbackOrder >= savedOrder) 0 else 1
            }.thenBy { item -> item.persistedEntry.playbackOrder }
        )
    }

    private fun IdentifiedLivePlaybackQueueItem.toDraft(
        baseOrder: Int,
        playbackOrder: Int
    ) = PlaybackQueueEntryDraft(
        entryId = liveItem.entryId,
        trackIdentityId = trackIdentityId,
        localTrackBindingId = localTrackBindingId,
        baseOrder = baseOrder,
        playbackOrder = playbackOrder
    )

    private fun PlaybackQueueEntryDraft.toEntity(queueId: String) = PlaybackQueueEntryEntity(
        entryId = entryId,
        queueId = queueId,
        trackIdentityId = trackIdentityId,
        localTrackBindingId = localTrackBindingId,
        baseOrder = baseOrder,
        playbackOrder = playbackOrder
    )

    private data class LiveQueueSignature(
        val queueId: String?,
        val entries: List<Pair<String, String>>,
        val baseEntryIds: List<String>
    )

    private data class PopulatedQueueCreation(
        val queue: PlaybackQueueWithEntries,
        val drafts: List<PlaybackQueueEntryDraft>,
        val identified: List<IdentifiedLivePlaybackQueueItem>
    )

    private suspend fun nextDefaultQueueNameLocked(): String {
        val existingNames = persistence.listQueues().map(PlaybackQueueEntity::displayName)
        return generatedQueueName(nextDefaultQueueNumber(existingNames, generatedQueueName))
    }
}

internal fun nextDefaultQueueNumber(
    existingNames: List<String>,
    generatedQueueName: (Int) -> String
): Int {
    // Compatibility only: old persisted English queue names were the sole numbering record.
    val legacyPattern = Regex("Queue (\\d+)")
    val highestLegacyNumber = existingNames
        .mapNotNull(legacyPattern::matchEntire)
        .mapNotNull { match -> match.groupValues[1].toIntOrNull() }
        .maxOrNull()
        ?: 0
    var number = maxOf(existingNames.size + 1, highestLegacyNumber + 1)
    val existing = existingNames.toSet()
    while (generatedQueueName(number) in existing) number += 1
    return number
}

private fun Song.toLivePlaybackQueueItem(): LivePlaybackQueueItem {
    val entryId = java.util.UUID.randomUUID().toString()
    return LivePlaybackQueueItem(
        entryId = entryId,
        evidence = ListeningMediaItemEvidence(
            itemInstanceId = entryId,
            referenceKey = membershipKey(),
            reference = toSongReference()
        )
    )
}
