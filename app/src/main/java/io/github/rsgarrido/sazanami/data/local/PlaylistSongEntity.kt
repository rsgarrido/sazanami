package io.github.rsgarrido.sazanami.data.local

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playlist_songs",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["playlistId"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["playlistId"]),
        Index(value = ["songKey"])
    ]
)
data class PlaylistSongEntity(
    @PrimaryKey(autoGenerate = true)
    val playlistSongId: Long = 0,
    val playlistId: Long,
    val songKey: String,
    val position: Int,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val addedAt: Long,
    val mediaStoreId: Long?,
    @ColumnInfo(defaultValue = "") val volumeName: String,
    @ColumnInfo(defaultValue = "") val contentUri: String,
    @ColumnInfo(defaultValue = "") val relativePath: String,
    @ColumnInfo(defaultValue = "") val displayName: String,
    @ColumnInfo(defaultValue = "0") val fileSizeBytes: Long,
    @ColumnInfo(defaultValue = "0") val dateModifiedEpochSeconds: Long,
    @ColumnInfo(defaultValue = "") val albumArtist: String,
    @ColumnInfo(defaultValue = "") val portableKey: String,
    @ColumnInfo(defaultValue = "1") val portableKeyVersion: Int
)
