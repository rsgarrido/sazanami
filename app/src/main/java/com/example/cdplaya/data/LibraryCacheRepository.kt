package com.example.cdplaya.data

import android.net.Uri
import com.example.cdplaya.data.local.CachedSongDao
import com.example.cdplaya.data.local.CachedSongEntity

class LibraryCacheRepository(
    private val cachedSongDao: CachedSongDao
) {
    suspend fun hasCachedSongs(): Boolean {
        return cachedSongDao.getCachedSongCount() > 0
    }

    suspend fun getCachedLibraryData(
        selectedFolders: Set<String> = emptySet()
    ): MusicLibraryData {
        val allSongs = cachedSongDao
            .getAllCachedSongs()
            .map { cachedSong ->
                cachedSong.toSong()
            }

        return buildMusicLibraryData(
            allSongs = allSongs,
            selectedFolders = selectedFolders
        )
    }

    suspend fun getAllCachedSongs(): List<Song> {
        return cachedSongDao.getAllCachedSongs().map { it.toSong() }
    }

    suspend fun replaceCachedSongs(songs: List<Song>) {
        val cachedAt = System.currentTimeMillis()

        cachedSongDao.replaceCachedSongs(
            songs.map { song ->
                song.toCachedSongEntity(cachedAt = cachedAt)
            }
        )
    }

    suspend fun clearCachedSongs() {
        cachedSongDao.clearCachedSongs()
    }
}

fun CachedSongEntity.toSong(): Song {
    return Song(
        id = mediaStoreId,
        title = title,
        artist = artist,
        album = album,
        trackNumber = trackNumber,
        duration = duration,
        uri = Uri.parse(uriString),
        filePath = filePath,
        folderPath = folderPath,
        albumArtUri = albumArtUriString
            ?.takeIf { uriString ->
                uriString.isNotBlank()
            }
            ?.let { uriString ->
                Uri.parse(uriString)
            },
        albumArtist = albumArtist,
        volumeName = volumeName,
        displayName = displayName,
        relativePath = relativePath,
        fileSizeBytes = fileSizeBytes,
        dateAddedEpochSeconds = dateAddedEpochSeconds,
        dateModifiedEpochSeconds = dateModifiedEpochSeconds,
        year = year,
        artworkEnrichmentVersion = artworkEnrichmentVersion
    )
}

fun Song.toCachedSongEntity(cachedAt: Long): CachedSongEntity {
    return CachedSongEntity(
        mediaStoreId = id,
        title = title,
        artist = artist,
        album = album,
        trackNumber = trackNumber,
        duration = duration,
        uriString = uri.toString(),
        filePath = filePath,
        folderPath = folderPath,
        albumArtUriString = albumArtUri?.toString(),
        albumArtist = albumArtist,
        volumeName = volumeName,
        displayName = displayName,
        relativePath = relativePath,
        fileSizeBytes = fileSizeBytes,
        dateAddedEpochSeconds = dateAddedEpochSeconds,
        dateModifiedEpochSeconds = dateModifiedEpochSeconds,
        year = year,
        artworkEnrichmentVersion = artworkEnrichmentVersion,
        cachedAt = cachedAt
    )
}
