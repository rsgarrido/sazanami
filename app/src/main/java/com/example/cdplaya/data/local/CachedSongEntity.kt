package com.example.cdplaya.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cached_songs",
    indices = [
        Index(value = ["folderPath"]),
        Index(value = ["title"])
    ]
)
data class CachedSongEntity(
    @PrimaryKey
    val mediaStoreId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val trackNumber: Int,
    val duration: Long,
    val uriString: String,
    val filePath: String,
    val folderPath: String,
    val albumArtUriString: String?,
    val albumArtist: String,
    val volumeName: String,
    val displayName: String,
    val relativePath: String,
    val fileSizeBytes: Long,
    val dateAddedEpochSeconds: Long,
    val dateModifiedEpochSeconds: Long,
    val year: Int? = null,
    val artworkEnrichmentVersion: Int,
    val cachedAt: Long
)
