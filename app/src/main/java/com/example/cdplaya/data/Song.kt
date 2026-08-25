package com.example.cdplaya.data

import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val trackNumber: Int,
    val duration: Long,
    val uri: Uri,
    val filePath: String,
    val folderPath: String,
    val albumArtUri: Uri?,
    val albumArtist: String = "",
    val volumeName: String = "",
    val displayName: String = "",
    val relativePath: String = "",
    val fileSizeBytes: Long = 0L,
    val dateAddedEpochSeconds: Long = 0L,
    val dateModifiedEpochSeconds: Long = 0L,
    val year: Int? = null,
    val artworkEnrichmentVersion: Int = 0
) {
    /** Warmed by [SongReferenceIndex] off the main thread for cheap UI membership checks. */
    internal val cachedIdentity: SongIdentity by lazy(LazyThreadSafetyMode.PUBLICATION) {
        buildSongIdentity(this)
    }
}
