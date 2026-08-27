package com.example.cdplaya.data

import android.content.ContentUris
import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import java.io.File
import com.example.cdplaya.performance.PerformanceTraceNames
import com.example.cdplaya.performance.tracePerformance


class MusicRepository(private val context: Context) {
    private val embeddedMetadataReader = EmbeddedMetadataReader()

    fun getLibraryData(selectedFolders: Set<String> = emptySet()): MusicLibraryData {
        return buildMusicLibraryData(
            allSongs = refreshLibrary(emptyList()).songs,
            selectedFolders = selectedFolders
        )
    }

    fun getSongs(selectedFolders: Set<String> = emptySet()): List<Song> {
        return getLibraryData(selectedFolders).songs
    }

    fun queryLibraryIndex(): List<Song>? = try {
        tracePerformance(PerformanceTraceNames.MEDIASTORE_INDEX_QUERY) { querySongIndex() }
    } catch (exception: SecurityException) {
        throw MediaLibraryAccessException(exception)
    }

    fun refreshLibrary(
        cachedSongs: List<Song>,
        forceArtworkRefreshIds: Set<Long> = emptySet(),
        indexSongsOverride: List<Song>? = null,
        folderArtworkTreeUri: Uri? = null
    ): LibraryRefreshResult {
        val indexSongs = indexSongsOverride ?: queryLibraryIndex()
        LibraryRefreshEngine.fallbackForIncompleteScan(cachedSongs, indexSongs)?.let { return it }
        checkNotNull(indexSongs)
        val embeddedArtworkResolver = EmbeddedArtworkResolver(context)
        val folderArtworkResolver = FolderArtworkResolver(context, folderArtworkTreeUri)
        tracePerformance(PerformanceTraceNames.ARTWORK_REPAIR_BATCH) {
            cachedSongs.filter { it.id in forceArtworkRefreshIds }
                .forEach(embeddedArtworkResolver::invalidate)
        }
        var embeddedArtworkExtractionMs = 0L
        var embeddedArtworkExtractionCount = 0
        val artworkRepairKeys = mutableSetOf<String>()
        val classificationStartedAt = SystemClock.elapsedRealtime()
        val result = tracePerformance(PerformanceTraceNames.LIBRARY_CLASSIFICATION) {
            LibraryRefreshEngine.refresh(
                cachedSongs = cachedSongs,
                indexSongs = indexSongs,
                requiresEnrichment = { cached, current ->
                    val requiresRepair = cached.id in forceArtworkRefreshIds ||
                            cached.requiresArtworkRepair(current) ||
                            embeddedArtworkResolver.requiresReconstruction(cached)
                    if (requiresRepair) artworkRepairKeys += current.uri.toString()
                    requiresRepair
                },
                enrich = { indexSong ->
                    val embeddedStartedAt = SystemClock.elapsedRealtime()
                    val embeddedArtwork = embeddedArtworkResolver.resolve(indexSong)
                    embeddedArtworkExtractionMs +=
                        SystemClock.elapsedRealtime() - embeddedStartedAt
                    embeddedArtworkExtractionCount += 1
                    indexSong.copy(
                        albumArtUri = selectArtwork(
                            embedded = embeddedArtwork,
                            folder = folderArtworkResolver.resolve(indexSong)
                        ),
                        artworkEnrichmentVersion = CURRENT_ARTWORK_ENRICHMENT_VERSION
                    )
                }
            )
        }
        debugTiming(
            "classification elapsedMs=${SystemClock.elapsedRealtime() - classificationStartedAt} " +
                    "songs=${indexSongs.size}"
        )
        debugTiming(
            "embedded-artwork-extraction elapsedMs=$embeddedArtworkExtractionMs " +
                    "files=$embeddedArtworkExtractionCount"
        )
        return result.copy(artworkRepairCount = artworkRepairKeys.size)
    }

    fun applyFolderArtwork(
        songs: List<Song>,
        folderArtworkTreeUri: Uri?
    ): List<Song> {
        val folderArtworkResolver = FolderArtworkResolver(context, folderArtworkTreeUri)
        var changed = false
        val updated = songs.map { song ->
            if (EmbeddedArtworkContract.isEmbeddedArtworkUri(song.albumArtUri)) {
                song
            } else {
                val folderArtwork = folderArtworkResolver.resolve(song)
                if (folderArtwork != song.albumArtUri) {
                    changed = true
                    song.copy(albumArtUri = folderArtwork)
                } else {
                    song
                }
            }
        }
        return if (changed) updated else songs
    }

    fun prepareCachedSongsForPublication(cachedSongs: List<Song>): List<Song> {
        val resolver = EmbeddedArtworkResolver(context)
        return hideUnavailableEmbeddedArtwork(cachedSongs, resolver::isMaterialized)
    }

    private fun querySongIndex(): List<Song>? {
        val songs = mutableListOf<Song>()

        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = MediaStoreProjectionPolicy.audioProjection(Build.VERSION.SDK_INT)
            .toTypedArray()

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        val queryStartedAt = SystemClock.elapsedRealtime()
        val query = context.contentResolver.query(
            collection,
            projection,
            selection,
            null,
            sortOrder
        )
        debugTiming(
            "audio-mediastore-query elapsedMs=${SystemClock.elapsedRealtime() - queryStartedAt}"
        )

        val mappingStartedAt = SystemClock.elapsedRealtime()
        query?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStoreProjectionPolicy.ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStoreProjectionPolicy.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStoreProjectionPolicy.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStoreProjectionPolicy.ALBUM)
            val trackColumn = cursor.getColumnIndexOrThrow(MediaStoreProjectionPolicy.TRACK)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStoreProjectionPolicy.DURATION)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStoreProjectionPolicy.DATA)
            val displayNameColumn = cursor.getColumnIndex(MediaStoreProjectionPolicy.DISPLAY_NAME)
            val fileSizeColumn = cursor.getColumnIndex(MediaStoreProjectionPolicy.SIZE)
            val dateAddedColumn = cursor.getColumnIndex(MediaStoreProjectionPolicy.DATE_ADDED)
            val dateModifiedColumn = cursor.getColumnIndex(MediaStoreProjectionPolicy.DATE_MODIFIED)
            val yearColumn = cursor.getColumnIndex(MediaStoreProjectionPolicy.YEAR)
            val volumeNameColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cursor.getColumnIndex(MediaStoreProjectionPolicy.VOLUME_NAME)
            } else {
                -1
            }
            val relativePathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cursor.getColumnIndex(MediaStoreProjectionPolicy.RELATIVE_PATH)
            } else {
                -1
            }

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn) ?: "Unknown Title"
                val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                val album = cursor.getString(albumColumn) ?: "Unknown Album"
                val duration = cursor.getLong(durationColumn)
                val filePath = cursor.getString(dataColumn) ?: ""
                val trackNumber = cursor.getInt(trackColumn)
                val displayName = cursor.stringOrEmpty(displayNameColumn)
                val fileSizeBytes = cursor.longOrZero(fileSizeColumn)
                val dateAddedEpochSeconds = cursor.longOrZero(dateAddedColumn)
                val dateModifiedEpochSeconds = cursor.longOrZero(dateModifiedColumn)
                val year = cursor.intOrNull(yearColumn)?.takeIf { it in 1000..2999 }
                val volumeName = cursor.stringOrEmpty(volumeNameColumn)
                val relativePath = cursor.stringOrEmpty(relativePathColumn)

                val folderPath = mediaFolderPath(filePath, relativePath)

                if (folderPath.isBlank()) {
                    continue
                }

                val audioCollection = if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && volumeName.isNotBlank()
                ) {
                    MediaStore.Audio.Media.getContentUri(volumeName)
                } else {
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
                val uri = ContentUris.withAppendedId(
                    audioCollection,
                    id
                )

                val albumArtist = ""

                val mediaStoreSong = Song(
                    id = id,
                    title = title,
                    artist = artist,
                    album = album,
                    trackNumber = trackNumber,
                    duration = duration,
                    uri = uri,
                    filePath = filePath,
                    folderPath = folderPath,
                    albumArtUri = null,
                    albumArtist = albumArtist,
                    volumeName = volumeName,
                    displayName = displayName,
                    relativePath = relativePath,
                    fileSizeBytes = fileSizeBytes,
                    dateAddedEpochSeconds = dateAddedEpochSeconds,
                    dateModifiedEpochSeconds = dateModifiedEpochSeconds,
                    year = year
                )
                val song = if (isWavFile(filePath, displayName)) {
                    val embeddedMetadata = embeddedMetadataReader
                        .readOrNull(File(filePath))
                        ?.takeIf { it.format == AudioMetadataFormat.WAV }
                        ?.metadata
                    mergeWavEmbeddedMetadata(mediaStoreSong, embeddedMetadata)
                } else {
                    mediaStoreSong
                }

                songs.add(song)
            }
        }
        debugTiming(
            "cursor-mapping elapsedMs=${SystemClock.elapsedRealtime() - mappingStartedAt} " +
                    "songs=${songs.size}"
        )
        return if (query == null) null else songs
    }

    private fun debugTiming(message: String) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            Log.d("LibraryTiming", message)
        }
    }

}

internal class MediaLibraryAccessException(cause: SecurityException) :
    RuntimeException("Media library permission is unavailable.", cause)

internal fun selectArtwork(embedded: Uri?, folder: Uri?): Uri? = embedded ?: folder

internal fun hideUnavailableEmbeddedArtwork(
    songs: List<Song>,
    isMaterialized: (Uri?) -> Boolean
): List<Song> {
    var changed = false
    val prepared = songs.map { song ->
        if (
            EmbeddedArtworkContract.isEmbeddedArtworkUri(song.albumArtUri) &&
            !isMaterialized(song.albumArtUri)
        ) {
            changed = true
            song.copy(albumArtUri = null)
        } else {
            song
        }
    }
    return if (changed) prepared else songs
}

private fun android.database.Cursor.stringOrEmpty(columnIndex: Int): String {
    return if (columnIndex >= 0 && !isNull(columnIndex)) getString(columnIndex).orEmpty() else ""
}

private fun android.database.Cursor.longOrZero(columnIndex: Int): Long {
    return if (columnIndex >= 0 && !isNull(columnIndex)) getLong(columnIndex) else 0L
}

private fun android.database.Cursor.intOrNull(columnIndex: Int): Int? {
    return if (columnIndex >= 0 && !isNull(columnIndex)) getInt(columnIndex) else null
}

internal fun mediaFolderPath(dataPath: String, relativePath: String): String {
    return (File(dataPath).parent ?: "")
        .ifBlank { relativePath.trimEnd('/', '\\') }
}

internal fun isWavFile(filePath: String, displayName: String): Boolean =
    sequenceOf(filePath, displayName)
        .map { it.substringAfterLast('.', missingDelimiterValue = "") }
        .any { it.equals("wav", ignoreCase = true) }

/**
 * MediaStore remains the library index, while valid embedded WAV fields take precedence over
 * MediaStore's often incomplete WAV projection. Missing embedded fields retain MediaStore values.
 */
internal fun mergeWavEmbeddedMetadata(
    mediaStoreSong: Song,
    embeddedMetadata: AudioMetadata?
): Song {
    if (embeddedMetadata == null) return mediaStoreSong

    return mediaStoreSong.copy(
        title = embeddedMetadata.title ?: mediaStoreSong.title,
        artist = embeddedMetadata.primaryArtist ?: mediaStoreSong.artist,
        album = embeddedMetadata.album ?: mediaStoreSong.album,
        albumArtist = embeddedMetadata.albumArtist ?: mediaStoreSong.albumArtist,
        trackNumber = embeddedMetadata.trackNumber
            ?.substringBefore('/')
            ?.trim()
            ?.toIntOrNull()
            ?: mediaStoreSong.trackNumber,
        year = embeddedMetadata.date
            ?.trim()
            ?.take(4)
            ?.toIntOrNull()
            ?.takeIf { it in 1000..2999 }
            ?: mediaStoreSong.year
    )
}
