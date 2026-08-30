package io.github.rsgarrido.sazanami.data.playlistfile

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.github.rsgarrido.sazanami.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

data class M3uImportResult(
    val sourceDisplayName: String?,
    val entries: List<M3uEntry>,
    val matchedSongs: List<Song>,
    val unmatchedEntries: List<M3uEntry>
) {
    val matchedSongCount: Int
        get() = matchedSongs.size

    val unmatchedEntryCount: Int
        get() = unmatchedEntries.size
}

data class M3uExportResult(
    val exportedSongCount: Int
)

class PlaylistFileRepository(
    context: Context
) {
    private val applicationContext = context.applicationContext

    suspend fun importM3uPlaylist(
        uri: Uri,
        librarySongs: List<Song>
    ): M3uImportResult {
        return withContext(Dispatchers.IO) {
            val lines = readTextLinesFromUri(uri)
            val entries = M3uPlaylistFile.parseM3uLines(lines)
            val matchResult = M3uPlaylistMatcher.matchEntriesToLibrarySongs(
                entries = entries,
                librarySongs = librarySongs
            )

            M3uImportResult(
                sourceDisplayName = getDisplayName(uri),
                entries = entries,
                matchedSongs = matchResult.matchedSongs,
                unmatchedEntries = matchResult.unmatchedEntries
            )
        }
    }

    suspend fun exportM3uPlaylist(
        uri: Uri,
        songs: List<Song>
    ): M3uExportResult {
        return withContext(Dispatchers.IO) {
            val m3uText = M3uPlaylistFile.buildM3uText(songs)

            val outputStream = applicationContext
                .contentResolver
                .openOutputStream(uri)
                ?: throw IOException("Unable to open playlist export destination.")

            outputStream.use { stream ->
                stream.write(m3uText.toByteArray(Charsets.UTF_8))
            }

            M3uExportResult(
                exportedSongCount = songs.size
            )
        }
    }

    private fun readTextLinesFromUri(uri: Uri): List<String> {
        val inputStream = applicationContext
            .contentResolver
            .openInputStream(uri)
            ?: throw IOException("Unable to open playlist file.")

        return inputStream.use { stream ->
            stream.bufferedReader(Charsets.UTF_8).readLines()
        }
    }

    private fun getDisplayName(uri: Uri): String? {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)

        return applicationContext
            .contentResolver
            .query(
                uri,
                projection,
                null,
                null,
                null
            )
            ?.use { cursor ->
                val displayNameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)

                if (displayNameColumn == -1 || !cursor.moveToFirst()) {
                    null
                } else {
                    cursor.getString(displayNameColumn)
                }
            }
    }
}