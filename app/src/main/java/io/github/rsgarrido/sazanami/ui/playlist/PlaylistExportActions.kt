package io.github.rsgarrido.sazanami.ui.playlist

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import io.github.rsgarrido.sazanami.data.Playlist
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.playlistfile.M3uExportResult
import io.github.rsgarrido.sazanami.data.playlistfile.PreparedPlaylistExport
import kotlinx.coroutines.launch
import java.util.Locale

data class PlaylistExportActions(
    val exportPlaylist: (Playlist) -> Unit
)

@Composable
fun rememberPlaylistExportActions(
    snackbarHostState: SnackbarHostState,
    onPrepareExport: (Playlist, (Result<PreparedPlaylistExport>) -> Unit) -> Unit,
    onExport: (Uri, List<Song>, (Result<M3uExportResult>) -> Unit) -> Unit
): PlaylistExportActions {
    val coroutineScope = rememberCoroutineScope()
    var pendingExport by remember {
        mutableStateOf<PreparedPlaylistExport?>(null)
    }

    fun showMessage(message: String) {
        coroutineScope.launch {
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short,
                withDismissAction = true
            )
        }
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/x-mpegurl")
    ) { uri ->
        val export = pendingExport
        pendingExport = null

        if (uri != null && export != null) {
            onExport(uri, export.songs) { result ->
                result.fold(
                    onSuccess = { exportResult ->
                        showMessage(
                            exportSuccessMessage(
                                exportedSongCount = exportResult.exportedSongCount,
                                unavailableSongCount = export.unavailableSongCount
                            )
                        )
                    },
                    onFailure = {
                        showMessage("Couldn't export playlist.")
                    }
                )
            }
        }
    }

    return PlaylistExportActions(
        exportPlaylist = { playlist ->
            onPrepareExport(playlist) { result ->
                result.fold(
                    onSuccess = { export ->
                        if (export.songs.isEmpty()) {
                            val message = if (export.unavailableSongCount == 0) {
                                "This playlist is empty."
                            } else {
                                "No songs could be exported. ${unavailableSongsMessage(export.unavailableSongCount)}"
                            }

                            showMessage(message)
                        } else {
                            pendingExport = export
                            createDocumentLauncher.launch(
                                sanitizedM3uFilename(export.playlistName)
                            )
                        }
                    },
                    onFailure = {
                        showMessage("Couldn't prepare playlist for export.")
                    }
                )
            }
        }
    )
}

internal fun sanitizedM3uFilename(playlistName: String): String {
    val sanitizedName = playlistName
        .trim()
        .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_")
        .trimEnd('.', ' ')
        .ifBlank { "playlist" }

    val baseName = if (sanitizedName.lowercase(Locale.ROOT).endsWith(".m3u8")) {
        sanitizedName.dropLast(".m3u8".length)
    } else {
        sanitizedName
    }.trimEnd('.', ' ').ifBlank { "playlist" }

    return "${baseName.take(120)}.m3u8"
}

internal fun exportSuccessMessage(
    exportedSongCount: Int,
    unavailableSongCount: Int
): String {
    val songLabel = if (exportedSongCount == 1) "song" else "songs"
    val exportedMessage = "Exported $exportedSongCount $songLabel"

    return if (unavailableSongCount > 0) {
        "$exportedMessage. ${unavailableSongsMessage(unavailableSongCount)}"
    } else {
        exportedMessage
    }
}

private fun unavailableSongsMessage(unavailableSongCount: Int): String {
    return if (unavailableSongCount == 1) {
        "1 unavailable song was skipped."
    } else {
        "$unavailableSongCount unavailable songs were skipped."
    }
}
