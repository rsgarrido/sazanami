package io.github.rsgarrido.sazanami.data

import androidx.room.withTransaction
import io.github.rsgarrido.sazanami.data.local.AppDatabase
import io.github.rsgarrido.sazanami.data.local.PersistedQueueRepeatMode
import io.github.rsgarrido.sazanami.data.local.PlaybackQueueEntity
import io.github.rsgarrido.sazanami.data.local.PlaybackQueueEntryEntity
import io.github.rsgarrido.sazanami.data.local.PlaybackQueueStateEntity
import io.github.rsgarrido.sazanami.data.local.PlaybackQueueWithEntries
import java.util.UUID
import kotlinx.coroutines.flow.Flow

data class PlaybackQueueEntryDraft(
    val trackIdentityId: Long,
    val localTrackBindingId: Long? = null,
    val baseOrder: Int,
    val playbackOrder: Int,
    val entryId: String = UUID.randomUUID().toString()
)

class PlaybackQueueRepository(
    private val database: AppDatabase,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private val dao = database.playbackQueueDao()

    fun observeQueues(): Flow<List<PlaybackQueueEntity>> = dao.observeQueues()

    suspend fun listQueues(): List<PlaybackQueueEntity> = dao.getQueues()

    suspend fun loadQueue(queueId: String): PlaybackQueueWithEntries? =
        database.withTransaction { loadQueueWithinTransaction(queueId) }

    suspend fun createQueue(
        displayName: String,
        entries: List<PlaybackQueueEntryDraft> = emptyList(),
        sourceType: String? = null,
        sourceKey: String? = null,
        currentEntryId: String? = null,
        currentPositionMs: Long = 0L,
        shuffleEnabled: Boolean = false,
        repeatMode: PersistedQueueRepeatMode = PersistedQueueRepeatMode.OFF,
        queueId: String = UUID.randomUUID().toString()
    ): PlaybackQueueWithEntries = database.withTransaction {
        val safeName = requireDisplayName(displayName)
        require(queueId.isNotBlank()) { "Queue ID cannot be blank" }
        require(dao.getQueue(queueId) == null) { "Queue $queueId already exists" }
        validateEntries(entries, currentEntryId, currentPositionMs)
        validateTrackReferences(entries)

        val now = nowMillis()
        dao.insertQueue(
            PlaybackQueueEntity(
                queueId = queueId,
                displayName = safeName,
                createdAt = now,
                updatedAt = now,
                lastActiveAt = now,
                sourceType = sourceType.normalizedOptionalValue(),
                sourceKey = sourceKey.normalizedOptionalValue(),
                currentEntryId = currentEntryId,
                currentPositionMs = currentPositionMs,
                shuffleEnabled = shuffleEnabled,
                repeatMode = repeatMode
            )
        )
        dao.insertEntries(entries.map { it.toEntity(queueId) })
        checkNotNull(loadQueueWithinTransaction(queueId))
    }

    suspend fun duplicateQueue(
        sourceQueueId: String,
        displayName: String,
        queueId: String = UUID.randomUUID().toString(),
        entryIdFactory: () -> String = { UUID.randomUUID().toString() }
    ): PlaybackQueueWithEntries = database.withTransaction {
        val source = requireNotNull(loadQueueWithinTransaction(sourceQueueId)) {
            "Queue $sourceQueueId does not exist"
        }
        val safeName = requireDisplayName(displayName)
        require(queueId.isNotBlank()) { "Queue ID cannot be blank" }
        require(dao.getQueue(queueId) == null) { "Queue $queueId already exists" }

        val copiedEntryIds = source.entries.associate { entry ->
            entry.entryId to entryIdFactory().also { copiedId ->
                require(copiedId.isNotBlank()) { "Entry IDs cannot be blank" }
            }
        }
        require(copiedEntryIds.values.distinct().size == copiedEntryIds.size) {
            "Copied entry IDs must be unique"
        }
        val now = nowMillis()
        dao.insertQueue(
            source.queue.copy(
                queueId = queueId,
                displayName = safeName,
                createdAt = now,
                updatedAt = now,
                currentEntryId = source.queue.currentEntryId?.let(copiedEntryIds::get)
            )
        )
        dao.insertEntries(
            source.entries.map { entry ->
                entry.copy(
                    entryId = checkNotNull(copiedEntryIds[entry.entryId]),
                    queueId = queueId
                )
            }
        )
        checkNotNull(loadQueueWithinTransaction(queueId))
    }

    suspend fun replaceEntries(
        queueId: String,
        entries: List<PlaybackQueueEntryDraft>,
        currentEntryId: String? = null,
        currentPositionMs: Long = 0L
    ): PlaybackQueueWithEntries = database.withTransaction {
        requireQueue(queueId)
        validateEntries(entries, currentEntryId, currentPositionMs)
        validateTrackReferences(entries)

        dao.deleteEntries(queueId)
        dao.insertEntries(entries.map { it.toEntity(queueId) })
        check(
            dao.finishReplacingEntries(
                queueId = queueId,
                currentEntryId = currentEntryId,
                currentPositionMs = currentPositionMs,
                updatedAt = nowMillis()
            ) == 1
        )
        checkNotNull(loadQueueWithinTransaction(queueId))
    }

    suspend fun appendEntries(
        queueId: String,
        entries: List<PlaybackQueueEntryDraft>
    ): PlaybackQueueWithEntries = database.withTransaction {
        val existing = requireNotNull(loadQueueWithinTransaction(queueId)) {
            "Queue $queueId does not exist"
        }
        if (entries.isEmpty()) return@withTransaction existing
        require(entries.all { it.entryId.isNotBlank() }) { "Entry IDs cannot be blank" }
        require(entries.map { it.entryId }.distinct().size == entries.size) {
            "Entry IDs must be unique"
        }
        require(entries.none { addition ->
            existing.entries.any { current -> current.entryId == addition.entryId }
        }) { "Entry IDs must be unique within a queue" }
        validateTrackReferences(entries)

        val nextBaseOrder = existing.entries.size
        val nextPlaybackOrder = existing.entries.size
        dao.insertEntries(
            entries.mapIndexed { index, entry ->
                entry.copy(
                    baseOrder = nextBaseOrder + index,
                    playbackOrder = nextPlaybackOrder + index
                ).toEntity(queueId)
            }
        )
        check(dao.touchQueue(queueId, nowMillis()) == 1)
        checkNotNull(loadQueueWithinTransaction(queueId))
    }

    suspend fun removeEntry(
        queueId: String,
        entryId: String
    ): PlaybackQueueWithEntries? = database.withTransaction {
        val existing = loadQueueWithinTransaction(queueId) ?: return@withTransaction null
        val removed = existing.entries.firstOrNull { it.entryId == entryId }
            ?: return@withTransaction existing
        val remaining = existing.entries.filterNot { it.entryId == entryId }
        val fallbackCurrentEntryId = if (existing.queue.currentEntryId == entryId) {
            remaining
                .filter { it.playbackOrder > removed.playbackOrder }
                .minByOrNull { it.playbackOrder }
                ?.entryId
                ?: remaining.maxByOrNull { it.playbackOrder }?.entryId
        } else {
            existing.queue.currentEntryId?.takeIf { currentId ->
                remaining.any { it.entryId == currentId }
            }
        }
        replaceEntriesWithinTransaction(
            queue = existing,
            entries = remaining.renumberOrders(),
            currentEntryId = fallbackCurrentEntryId,
            currentPositionMs = existing.queue.currentPositionMs.takeIf {
                fallbackCurrentEntryId != null && existing.queue.currentEntryId != entryId
            } ?: 0L
        )
    }

    suspend fun restoreEntry(
        queueId: String,
        entry: PlaybackQueueEntryEntity,
        baseOrder: Int = entry.baseOrder,
        playbackOrder: Int = entry.playbackOrder,
        restoredCurrentEntryId: String? = null,
        restoredCurrentPositionMs: Long? = null
    ): PlaybackQueueWithEntries? = database.withTransaction {
        val existing = loadQueueWithinTransaction(queueId) ?: return@withTransaction null
        require(entry.queueId == queueId) { "The restored entry must belong to the queue" }

        val withoutEntry = existing.entries.filterNot { candidate ->
            candidate.entryId == entry.entryId
        }
        val playbackOrdered = withoutEntry.sortedBy { it.playbackOrder }.toMutableList().apply {
            add(playbackOrder.coerceIn(0, size), entry)
        }
        val baseOrdered = withoutEntry.sortedBy { it.baseOrder }.toMutableList().apply {
            add(baseOrder.coerceIn(0, size), entry)
        }
        val playbackOrderById = playbackOrdered.withIndex()
            .associate { indexed -> indexed.value.entryId to indexed.index }
        val baseOrderById = baseOrdered.withIndex()
            .associate { indexed -> indexed.value.entryId to indexed.index }
        val restoredEntries = playbackOrdered.map { candidate ->
            candidate.copy(
                baseOrder = checkNotNull(baseOrderById[candidate.entryId]),
                playbackOrder = checkNotNull(playbackOrderById[candidate.entryId])
            )
        }
        replaceEntriesWithinTransaction(
            queue = existing,
            entries = restoredEntries,
            currentEntryId = restoredCurrentEntryId ?: existing.queue.currentEntryId,
            currentPositionMs = restoredCurrentPositionMs ?: existing.queue.currentPositionMs
        )
    }

    suspend fun reorderEntry(
        queueId: String,
        entryId: String,
        toPlaybackOrder: Int,
        updateBaseOrder: Boolean
    ): PlaybackQueueWithEntries? = database.withTransaction {
        val existing = loadQueueWithinTransaction(queueId) ?: return@withTransaction null
        val playbackOrdered = existing.entries.sortedBy { it.playbackOrder }.toMutableList()
        val fromIndex = playbackOrdered.indexOfFirst { it.entryId == entryId }
        if (fromIndex < 0 || toPlaybackOrder !in playbackOrdered.indices) {
            return@withTransaction existing
        }
        if (fromIndex != toPlaybackOrder) {
            val moved = playbackOrdered.removeAt(fromIndex)
            playbackOrdered.add(toPlaybackOrder, moved)
        } else if (!updateBaseOrder) {
            return@withTransaction existing
        }
        val playbackOrderById = playbackOrdered.withIndex().associate { it.value.entryId to it.index }
        val baseOrderById = if (updateBaseOrder) {
            playbackOrderById
        } else {
            existing.entries.sortedBy { it.baseOrder }
                .withIndex()
                .associate { it.value.entryId to it.index }
        }
        val reordered = existing.entries.map { entry ->
            entry.copy(
                baseOrder = checkNotNull(baseOrderById[entry.entryId]),
                playbackOrder = checkNotNull(playbackOrderById[entry.entryId])
            )
        }
        replaceEntriesWithinTransaction(
            queue = existing,
            entries = reordered,
            currentEntryId = existing.queue.currentEntryId,
            currentPositionMs = existing.queue.currentPositionMs
        )
    }

    suspend fun updateSavedPlaybackState(
        queueId: String,
        currentEntryId: String?,
        currentPositionMs: Long,
        shuffleEnabled: Boolean,
        repeatMode: PersistedQueueRepeatMode
    ) = database.withTransaction {
        requireQueue(queueId)
        require(currentPositionMs >= 0L) { "Playback position cannot be negative" }
        require(currentEntryId != null || currentPositionMs == 0L) {
            "A queue without a current entry cannot have a saved position"
        }
        if (currentEntryId != null) {
            require(dao.containsEntry(queueId, currentEntryId)) {
                "Entry $currentEntryId does not belong to queue $queueId"
            }
        }
        val now = nowMillis()
        check(
            dao.updateSavedPlaybackState(
                queueId = queueId,
                currentEntryId = currentEntryId,
                currentPositionMs = currentPositionMs,
                shuffleEnabled = shuffleEnabled,
                repeatMode = repeatMode,
                updatedAt = now,
                lastActiveAt = now
            ) == 1
        )
    }

    suspend fun renameQueue(queueId: String, displayName: String): Boolean =
        dao.renameQueue(queueId, requireDisplayName(displayName), nowMillis()) == 1

    suspend fun setActiveQueue(queueId: String?) = database.withTransaction {
        if (queueId != null) {
            requireQueue(queueId)
            val now = nowMillis()
            check(dao.markActive(queueId, lastActiveAt = now, updatedAt = now) == 1)
        }
        dao.setQueueState(
            PlaybackQueueStateEntity(activeQueueId = queueId)
        )
    }

    suspend fun getActiveQueueId(): String? = dao.getActiveQueueId()

    suspend fun deleteQueue(queueId: String): Boolean = database.withTransaction {
        dao.deleteQueue(queueId) == 1
    }

    suspend fun deleteInactiveQueueIfNotLast(queueId: String): Boolean =
        database.withTransaction {
            if (dao.getQueues().size <= 1) return@withTransaction false
            if (dao.getActiveQueueId() == queueId) return@withTransaction false
            dao.deleteQueue(queueId) == 1
        }

    suspend fun clearQueue(queueId: String): PlaybackQueueWithEntries =
        database.withTransaction {
            requireQueue(queueId)
            dao.deleteEntries(queueId)
            check(
                dao.finishReplacingEntries(
                    queueId = queueId,
                    currentEntryId = null,
                    currentPositionMs = 0L,
                    updatedAt = nowMillis()
                ) == 1
            )
            checkNotNull(loadQueueWithinTransaction(queueId))
        }

    private suspend fun loadQueueWithinTransaction(queueId: String): PlaybackQueueWithEntries? {
        val queue = dao.getQueue(queueId) ?: return null
        return PlaybackQueueWithEntries(
            queue = queue,
            entries = dao.getEntriesInPlaybackOrder(queueId)
        )
    }

    private suspend fun replaceEntriesWithinTransaction(
        queue: PlaybackQueueWithEntries,
        entries: List<PlaybackQueueEntryEntity>,
        currentEntryId: String?,
        currentPositionMs: Long
    ): PlaybackQueueWithEntries {
        dao.deleteEntries(queue.queue.queueId)
        dao.insertEntries(entries)
        check(
            dao.finishReplacingEntries(
                queueId = queue.queue.queueId,
                currentEntryId = currentEntryId,
                currentPositionMs = currentPositionMs,
                updatedAt = nowMillis()
            ) == 1
        )
        return checkNotNull(loadQueueWithinTransaction(queue.queue.queueId))
    }

    private suspend fun requireQueue(queueId: String): PlaybackQueueEntity =
        requireNotNull(dao.getQueue(queueId)) { "Queue $queueId does not exist" }

    private suspend fun validateTrackReferences(entries: List<PlaybackQueueEntryDraft>) {
        if (entries.isEmpty()) return
        val identityIds = entries.mapTo(linkedSetOf()) { it.trackIdentityId }
        val existingIdentityIds = database.listeningTrackIdentityDao()
            .getExistingIds(identityIds.toList())
            .toSet()
        require(existingIdentityIds == identityIds) {
            "Every queue entry must reference an existing durable track identity"
        }
        entries.forEach { entry ->
            val bindingId = entry.localTrackBindingId ?: return@forEach
            val binding = database.localTrackBindingDao().getById(bindingId)
            require(binding?.trackIdentityId == entry.trackIdentityId) {
                "Local binding $bindingId does not belong to track identity ${entry.trackIdentityId}"
            }
        }
    }

    private fun validateEntries(
        entries: List<PlaybackQueueEntryDraft>,
        currentEntryId: String?,
        currentPositionMs: Long
    ) {
        require(currentPositionMs >= 0L) { "Playback position cannot be negative" }
        require(currentEntryId != null || currentPositionMs == 0L) {
            "A queue without a current entry cannot have a saved position"
        }
        require(entries.all { it.entryId.isNotBlank() }) { "Entry IDs cannot be blank" }
        require(entries.map { it.entryId }.distinct().size == entries.size) {
            "Entry IDs must be unique"
        }
        require(entries.all { it.trackIdentityId > 0L }) {
            "Track identity IDs must be positive"
        }
        require(entries.all { it.localTrackBindingId == null || it.localTrackBindingId > 0L }) {
            "Local track binding IDs must be positive"
        }
        val expectedOrder = entries.indices.toList()
        require(entries.map { it.baseOrder }.sorted() == expectedOrder) {
            "Base order must contain each position from zero exactly once"
        }
        require(entries.map { it.playbackOrder }.sorted() == expectedOrder) {
            "Playback order must contain each position from zero exactly once"
        }
        require(currentEntryId == null || entries.any { it.entryId == currentEntryId }) {
            "The current entry must belong to the queue"
        }
    }

    private fun PlaybackQueueEntryDraft.toEntity(queueId: String) = PlaybackQueueEntryEntity(
        entryId = entryId,
        queueId = queueId,
        trackIdentityId = trackIdentityId,
        localTrackBindingId = localTrackBindingId,
        baseOrder = baseOrder,
        playbackOrder = playbackOrder
    )
}

private fun List<PlaybackQueueEntryEntity>.renumberOrders(): List<PlaybackQueueEntryEntity> {
    val baseOrderById = sortedBy { it.baseOrder }
        .withIndex()
        .associate { it.value.entryId to it.index }
    val playbackOrderById = sortedBy { it.playbackOrder }
        .withIndex()
        .associate { it.value.entryId to it.index }
    return map { entry ->
        entry.copy(
            baseOrder = checkNotNull(baseOrderById[entry.entryId]),
            playbackOrder = checkNotNull(playbackOrderById[entry.entryId])
        )
    }
}

private fun requireDisplayName(displayName: String): String = displayName.trim().also {
    require(it.isNotBlank()) { "Queue display name cannot be blank" }
}

private fun String?.normalizedOptionalValue(): String? = this?.trim()?.takeIf(String::isNotEmpty)
