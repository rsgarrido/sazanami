package io.github.rsgarrido.sazanami.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "favorite_songs",
    indices = [Index(value = ["songKey"])]
)
data class FavoriteSongEntity(
    @PrimaryKey val referenceKey: String,
    val songKey: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val createdAt: Long,
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
