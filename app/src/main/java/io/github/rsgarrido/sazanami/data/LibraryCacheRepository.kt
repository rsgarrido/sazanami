package io.github.rsgarrido.sazanami.data

import android.net.Uri
import io.github.rsgarrido.sazanami.data.local.CachedSongDao
import io.github.rsgarrido.sazanami.data.local.CachedSongEntity
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
    val decodedGenres = decodeCachedGenres(genresJson)
    val decodedComposers = decodeCachedStringList(composersJson)
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
        artworkEnrichmentVersion = artworkEnrichmentVersion,
        genres = decodedGenres.orEmpty(),
        composers = decodedComposers.orEmpty(),
        publisher = publisher,
        bpm = bpm,
        discNumber = discNumber,
        discTotal = discTotal,
        embeddedMetadataEnrichmentVersion = if (decodedGenres != null && decodedComposers != null) {
            embeddedMetadataEnrichmentVersion
        } else {
            0
        }
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
        genresJson = cachedSongJson.encodeToString(genres),
        normalizedGenresJson = cachedSongJson.encodeToString(normalizedKnownGenreKeys(genres)),
        composersJson = cachedSongJson.encodeToString(composers),
        composerText = composers.joinToString("; ") { it.trim() }.trim(),
        publisher = publisher.trim(),
        bpm = bpm,
        discNumber = discNumber,
        discTotal = discTotal,
        embeddedMetadataEnrichmentVersion = embeddedMetadataEnrichmentVersion,
        cachedAt = cachedAt
    )
}

private val cachedSongJson = Json { ignoreUnknownKeys = true }

private fun decodeCachedGenres(encoded: String): List<String>? = runCatching {
    cachedSongJson.decodeFromString<List<String>>(encoded)
}.getOrNull()

private fun decodeCachedStringList(encoded: String): List<String>? = runCatching {
    cachedSongJson.decodeFromString<List<String>>(encoded)
}.getOrNull()
