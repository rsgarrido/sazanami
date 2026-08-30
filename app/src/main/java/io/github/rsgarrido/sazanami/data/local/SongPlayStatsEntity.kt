package io.github.rsgarrido.sazanami.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "song_play_stats",
    indices = [
        Index(value = ["songKey"]),
        Index(value = ["lastPlayedAt"]),
        Index(value = ["playCount"])
    ]
)
data class SongPlayStatsEntity(
    @PrimaryKey val referenceKey: String,
    val songKey: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val playCount: Int,
    val firstPlayedAt: Long,
    val lastPlayedAt: Long,
    val mediaStoreId: Long?,
    val volumeName: String,
    val contentUri: String,
    val relativePath: String,
    val displayName: String,
    val fileSizeBytes: Long,
    val dateModifiedEpochSeconds: Long,
    val albumArtist: String,
    val portableKey: String,
    val portableKeyVersion: Int
)
