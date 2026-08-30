package io.github.rsgarrido.sazanami.ui.playlist

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import io.github.rsgarrido.sazanami.data.Playlist

@Composable
fun DeletePlaylistDialog(
    playlist: Playlist,
    onDismiss: () -> Unit,
    onConfirmDeleteClick: (Playlist) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Delete Playlist?")
        },
        text = {
            Text(
                text = "This will delete \"${playlist.name}\" from Sazanami. The audio files on your phone will not be deleted."
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmDeleteClick(playlist)
                }
            ) {
                Text(text = "Delete")
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss
            ) {
                Text(text = "Cancel")
            }
        }
    )
}
