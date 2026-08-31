package io.github.rsgarrido.sazanami.player

import io.github.rsgarrido.sazanami.data.PlaybackQueueEntryDraft
import io.github.rsgarrido.sazanami.data.PlaybackQueueRepository
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.membershipKey
import io.github.rsgarrido.sazanami.data.local.PersistedQueueRepeatMode
import io.github.rsgarrido.sazanami.data.local.PlaybackQueueEntryEntity
import io.github.rsgarrido.sazanami.data.local.PlaybackQueueWithEntries
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

internal interface PlaybackQueueRuntime {
    fun captureSnapshot(): LivePlaybackQueueSnapshot?
    fun replaceTimeline(restoration: PlaybackQueueRestoration)
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
}

internal class RepositoryPlaybackQueuePersistence(
    private val repository: PlaybackQueueRepository
) : PlaybackQueuePersistence {
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
}

/** Owns the mapping between the one live Media3 timeline and inactive Room-backed queues. */
internal class PlaybackQueueCoordinator(
    private val persistence: PlaybackQueuePersistence,
    private val trackAccess: PlaybackQueueTrackAccess,
    private val runtime: PlaybackQueueRuntime,
    private val queueIdFactory: () -> String = { java.util.UUID.randomUUID().toString() },
    private val onActiveQueueChanged: (String?) -> Unit = {}
) {
    private val mutex = Mutex()
    @Volatile
    private var activeQueueId: String? = null
    private var lastLiveSignature: LiveQueueSignature? = null

    suspend fun initialize(): String? = mutex.withLock {
        activeQueueId = persistence.getActiveQueueId()
        val activeId = activeQueueId
        if (activeId != null && runtime.captureSnapshot() == null) {
            restoreIntoEmptyRuntime(activeId)
        }
        onActiveQueueChanged(activeQueueId)
        activeQueueId
    }

    fun getActiveQueueId(): String? = activeQueueId

    suspend fun persistActiveQueueSnapshot(): String? = mutex.withLock {
        persistActiveQueueSnapshotLocked()
    }

    suspend fun switchToQueue(queueId: String): Boolean = mutex.withLock {
        require(queueId.isNotBlank()) { "Queue ID cannot be blank" }
        if (queueId == activeQueueId) return@withLock true

        val outgoing = runtime.captureSnapshot()
        val shouldPlay = outgoing?.shouldPlay ?: false
        persistActiveQueueSnapshotLocked(outgoing)

        val target = persistence.loadQueue(queueId) ?: return@withLock false
        val resolved = trackAccess.resolve(target.entries)
            .sortedBy { item -> item.persistedEntry.playbackOrder }
        if (resolved.isEmpty()) return@withLock false

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

        runtime.replaceTimeline(restoration)
        persistence.updateSavedPlaybackState(
            queueId = queueId,
            currentEntryId = restoration.currentEntryId,
            currentPositionMs = restoration.currentPositionMs,
            shuffleEnabled = restoration.shuffleEnabled,
            repeatMode = restoration.repeatMode
        )
        persistence.setActiveQueue(queueId)
        activeQueueId = queueId
        lastLiveSignature = LiveQueueSignature(
            queueId = queueId,
            entries = resolved.map { item ->
                item.persistedEntry.entryId to item.song.membershipKey()
            },
            baseEntryIds = resolved.sortedBy { item -> item.persistedEntry.baseOrder }
                .map { item -> item.persistedEntry.entryId }
        )
        onActiveQueueChanged(queueId)
        true
    }

    private suspend fun persistActiveQueueSnapshotLocked(
        suppliedSnapshot: LivePlaybackQueueSnapshot? = null
    ): String? {
        val snapshot = suppliedSnapshot ?: runtime.captureSnapshot() ?: return activeQueueId
        if (snapshot.entries.isEmpty()) return activeQueueId

        val queueId = activeQueueId
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
                displayName = DEFAULT_QUEUE_NAME,
                entries = drafts,
                currentEntryId = currentEntryId,
                currentPositionMs = position,
                shuffleEnabled = snapshot.shuffleEnabled,
                repeatMode = snapshot.repeatMode
            )
            persistence.setActiveQueue(newQueueId)
            activeQueueId = newQueueId
            lastLiveSignature = rawSignature.copy(queueId = newQueueId)
            onActiveQueueChanged(newQueueId)
            return newQueueId
        }

        val existing = persistence.loadQueue(queueId)
        val drafts = buildReplacementDrafts(
            liveItems = identified,
            existingEntries = existing?.entries.orEmpty(),
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
        lastLiveSignature = rawSignature
        return queueId
    }

    private suspend fun restoreIntoEmptyRuntime(queueId: String) {
        val target = persistence.loadQueue(queueId) ?: return
        val resolved = trackAccess.resolve(target.entries)
            .sortedBy { item -> item.persistedEntry.playbackOrder }
        if (resolved.isEmpty()) return
        // A controller may restore its already-live session while exact Room resolution is
        // suspended. In that race, adopting the live session must win over rebuilding it.
        if (runtime.captureSnapshot() != null) return
        val selected = selectRestoredCurrentEntry(target, resolved)
        val savedCurrentResolved = target.queue.currentEntryId ==
            selected.persistedEntry.entryId
        val position = if (savedCurrentResolved) {
            target.queue.currentPositionMs.coerceAtLeast(0L)
        } else {
            0L
        }
        runtime.replaceTimeline(
            PlaybackQueueRestoration(
                queueId = queueId,
                entries = resolved,
                currentEntryId = selected.persistedEntry.entryId,
                currentPositionMs = position,
                shouldPlay = false,
                shuffleEnabled = target.queue.shuffleEnabled,
                repeatMode = target.queue.repeatMode
            )
        )
        lastLiveSignature = LiveQueueSignature(
            queueId = queueId,
            entries = resolved.map { item ->
                item.persistedEntry.entryId to item.song.membershipKey()
            },
            baseEntryIds = resolved.sortedBy { item -> item.persistedEntry.baseOrder }
                .map { item -> item.persistedEntry.entryId }
        )
        persistence.updateSavedPlaybackState(
            queueId = queueId,
            currentEntryId = selected.persistedEntry.entryId,
            currentPositionMs = position,
            shuffleEnabled = target.queue.shuffleEnabled,
            repeatMode = target.queue.repeatMode
        )
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

    private data class LiveQueueSignature(
        val queueId: String?,
        val entries: List<Pair<String, String>>,
        val baseEntryIds: List<String>
    )

    companion object {
        private const val DEFAULT_QUEUE_NAME = "Queue 1"
    }
}
