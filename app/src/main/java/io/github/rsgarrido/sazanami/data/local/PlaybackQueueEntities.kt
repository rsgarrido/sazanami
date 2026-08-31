package io.github.rsgarrido.sazanami.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

enum class PersistedQueueRepeatMode(val storageValue: String) {
    OFF("off"),
    ALL("all"),
    ONE("one");

    companion object {
        fun fromStorageValue(value: String): PersistedQueueRepeatMode =
            entries.firstOrNull { it.storageValue == value }
                ?: error("Unknown persisted queue repeat mode: $value")
    }
}

class PlaybackQueueTypeConverters {
    @TypeConverter
    fun repeatModeToString(value: PersistedQueueRepeatMode): String = value.storageValue

    @TypeConverter
    fun stringToRepeatMode(value: String): PersistedQueueRepeatMode =
        PersistedQueueRepeatMode.fromStorageValue(value)
}

@Entity(
    tableName = "playback_queues",
    indices = [
        Index(value = ["lastActiveAt"]),
        Index(value = ["updatedAt"]),
        Index(value = ["currentEntryId"]),
        Index(value = ["sourceType", "sourceKey"])
    ]
)
data class PlaybackQueueEntity(
    @PrimaryKey
    val queueId: String,
    val displayName: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastActiveAt: Long,
    val sourceType: String?,
    val sourceKey: String?,
    val currentEntryId: String?,
    val currentPositionMs: Long,
    val shuffleEnabled: Boolean,
    val repeatMode: PersistedQueueRepeatMode
)

@Entity(
    tableName = "playback_queue_entries",
    foreignKeys = [
        ForeignKey(
            entity = PlaybackQueueEntity::class,
            parentColumns = ["queueId"],
            childColumns = ["queueId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ListeningTrackIdentityEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackIdentityId"],
            onDelete = ForeignKey.NO_ACTION
        ),
        ForeignKey(
            entity = LocalTrackBindingEntity::class,
            parentColumns = ["id"],
            childColumns = ["localTrackBindingId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["queueId", "baseOrder"], unique = true),
        Index(value = ["queueId", "playbackOrder"], unique = true),
        Index(value = ["trackIdentityId"]),
        Index(value = ["localTrackBindingId"])
    ]
)
data class PlaybackQueueEntryEntity(
    @PrimaryKey
    val entryId: String,
    val queueId: String,
    val trackIdentityId: Long,
    val localTrackBindingId: Long?,
    val baseOrder: Int,
    val playbackOrder: Int
)

@Entity(
    tableName = "playback_queue_state",
    foreignKeys = [
        ForeignKey(
            entity = PlaybackQueueEntity::class,
            parentColumns = ["queueId"],
            childColumns = ["activeQueueId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["activeQueueId"])]
)
data class PlaybackQueueStateEntity(
    @PrimaryKey
    val id: Int = SINGLETON_ID,
    val activeQueueId: String?
) {
    init {
        require(id == SINGLETON_ID) { "Playback queue state must use the singleton ID" }
    }

    companion object {
        const val SINGLETON_ID = 1
    }
}

data class PlaybackQueueWithEntries(
    val queue: PlaybackQueueEntity,
    val entries: List<PlaybackQueueEntryEntity>
)
