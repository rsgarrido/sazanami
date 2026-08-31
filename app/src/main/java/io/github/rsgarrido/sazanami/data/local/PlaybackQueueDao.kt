package io.github.rsgarrido.sazanami.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackQueueDao {
    @Query(
        "SELECT * FROM playback_queues " +
            "ORDER BY lastActiveAt DESC, updatedAt DESC, queueId ASC"
    )
    fun observeQueues(): Flow<List<PlaybackQueueEntity>>

    @Query(
        "SELECT * FROM playback_queues " +
            "ORDER BY lastActiveAt DESC, updatedAt DESC, queueId ASC"
    )
    suspend fun getQueues(): List<PlaybackQueueEntity>

    @Query("SELECT * FROM playback_queues WHERE queueId = :queueId")
    suspend fun getQueue(queueId: String): PlaybackQueueEntity?

    @Query(
        "SELECT * FROM playback_queue_entries WHERE queueId = :queueId " +
            "ORDER BY playbackOrder ASC, entryId ASC"
    )
    suspend fun getEntriesInPlaybackOrder(queueId: String): List<PlaybackQueueEntryEntity>

    @Query(
        "SELECT EXISTS(SELECT 1 FROM playback_queue_entries " +
            "WHERE queueId = :queueId AND entryId = :entryId)"
    )
    suspend fun containsEntry(queueId: String, entryId: String): Boolean

    @Insert
    suspend fun insertQueue(queue: PlaybackQueueEntity)

    @Insert
    suspend fun insertEntries(entries: List<PlaybackQueueEntryEntity>)

    @Query("DELETE FROM playback_queue_entries WHERE queueId = :queueId")
    suspend fun deleteEntries(queueId: String): Int

    @Query(
        "UPDATE playback_queues SET currentEntryId = :currentEntryId, " +
            "currentPositionMs = :currentPositionMs, updatedAt = :updatedAt " +
            "WHERE queueId = :queueId"
    )
    suspend fun finishReplacingEntries(
        queueId: String,
        currentEntryId: String?,
        currentPositionMs: Long,
        updatedAt: Long
    ): Int

    @Query(
        "UPDATE playback_queues SET currentEntryId = :currentEntryId, " +
            "currentPositionMs = :currentPositionMs, shuffleEnabled = :shuffleEnabled, " +
            "repeatMode = :repeatMode, updatedAt = :updatedAt, lastActiveAt = :lastActiveAt " +
            "WHERE queueId = :queueId"
    )
    suspend fun updateSavedPlaybackState(
        queueId: String,
        currentEntryId: String?,
        currentPositionMs: Long,
        shuffleEnabled: Boolean,
        repeatMode: PersistedQueueRepeatMode,
        updatedAt: Long,
        lastActiveAt: Long
    ): Int

    @Query(
        "UPDATE playback_queues SET displayName = :displayName, updatedAt = :updatedAt " +
            "WHERE queueId = :queueId"
    )
    suspend fun renameQueue(queueId: String, displayName: String, updatedAt: Long): Int

    @Query(
        "UPDATE playback_queues SET lastActiveAt = :lastActiveAt, updatedAt = :updatedAt " +
            "WHERE queueId = :queueId"
    )
    suspend fun markActive(queueId: String, lastActiveAt: Long, updatedAt: Long): Int

    @Query("DELETE FROM playback_queues WHERE queueId = :queueId")
    suspend fun deleteQueue(queueId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setQueueState(state: PlaybackQueueStateEntity)

    @Query(
        "SELECT activeQueueId FROM playback_queue_state " +
            "WHERE id = ${PlaybackQueueStateEntity.SINGLETON_ID}"
    )
    suspend fun getActiveQueueId(): String?
}
