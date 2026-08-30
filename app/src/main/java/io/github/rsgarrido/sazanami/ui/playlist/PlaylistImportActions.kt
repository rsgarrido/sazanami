package io.github.rsgarrido.sazanami.ui.playlist

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import io.github.rsgarrido.sazanami.data.playlistfile.PlaylistImportResult
import kotlinx.coroutines.launch

data class PlaylistImportActions(
    val importPlaylist: () -> Unit
)

@Composable
fun rememberPlaylistImportActions(
    snackbarHostState: SnackbarHostState,
    onImport: (Uri, (Result<PlaylistImportResult>) -> Unit) -> Unit
): PlaylistImportActions {
    val coroutineScope = rememberCoroutineScope()

    fun showMessage(message: String) {
        coroutineScope.launch {
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short,
                withDismissAction = true
            )
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImport(uri) { result ->
                result.fold(
                    onSuccess = { importResult ->
                        showMessage(importResultMessage(importResult))
                    },
                    onFailure = {
                        showMessage("Couldn't import playlist.")
                    }
                )
            }
        }
    }

    return PlaylistImportActions(
        importPlaylist = {
            openDocumentLauncher.launch(
                arrayOf(
                    "*/*",
                    "audio/x-mpegurl",
                    "application/vnd.apple.mpegurl",
                    "text/plain",
                    "application/octet-stream"
                )
            )
        }
    )
}

internal fun importResultMessage(result: PlaylistImportResult): String {
    val playlistName = result.playlistName

    if (result.importedSongCount == 0 || playlistName == null) {
        return "No matching songs found in your library."
    }

    val songLabel = if (result.importedSongCount == 1) "song" else "songs"
    val importedMessage =
        "Imported ${result.importedSongCount} $songLabel into $playlistName"

    return if (result.unmatchedEntryCount > 0) {
        val entryLabel = if (result.unmatchedEntryCount == 1) "entry" else "entries"
        "$importedMessage. ${result.unmatchedEntryCount} $entryLabel could not be matched."
    } else {
        importedMessage
    }
}
