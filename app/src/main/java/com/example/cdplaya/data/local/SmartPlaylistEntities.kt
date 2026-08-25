package com.example.cdplaya.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "smart_playlist_definitions",
    primaryKeys = ["playlistId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["playlistId"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["dependencyMask"])]
)
data class SmartPlaylistDefinitionEntity(
    val playlistId: Long,
    val matchMode: String,
    val rulesJson: String,
    val sortField: String,
    val sortDirection: String,
    val resultLimit: Int?,
    val definitionVersion: Int,
    val dependencyMask: Int,
    val updatedAt: Long
)

@Entity(
    tableName = "smart_playlist_resolution_states",
    primaryKeys = ["playlistId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["playlistId"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SmartPlaylistResolutionStateEntity(
    val playlistId: Long,
    @ColumnInfo(defaultValue = "1") val isDirty: Boolean = true,
    val resolvedAt: Long? = null,
    val validUntil: Long? = null,
    @ColumnInfo(defaultValue = "0") val resultCount: Int = 0
)

/** Explicitly derived live-membership cache; never exported and never used by manual playlists. */
@Entity(
    tableName = "smart_playlist_cached_songs",
    primaryKeys = ["playlistId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["playlistId"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["mediaStoreId", "volumeName"])]
)
data class SmartPlaylistCachedSongEntity(
    val playlistId: Long,
    val position: Int,
    val mediaStoreId: Long,
    val volumeName: String
)

@Entity(
    tableName = "generated_playlist_states",
    primaryKeys = ["playlistId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["playlistId"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["templateKey"])]
)
data class GeneratedPlaylistStateEntity(
    val playlistId: Long,
    val templateKey: String,
    @ColumnInfo(defaultValue = "'snapshot'") val membershipMode: String = "snapshot",
    val refreshPolicy: String,
    val refreshIntervalMillis: Long?,
    val lastRefreshedAt: Long?,
    val snapshotVersion: Int
)

@Entity(
    tableName = "generated_playlist_songs",
    primaryKeys = ["playlistId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["playlistId"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["songKey"])]
)
data class GeneratedPlaylistSongEntity(
    val playlistId: Long,
    val position: Int,
    val songKey: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
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
