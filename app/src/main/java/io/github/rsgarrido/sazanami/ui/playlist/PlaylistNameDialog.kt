package io.github.rsgarrido.sazanami.ui.playlist

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun PlaylistNameDialog(
    title: String = "Create Playlist",
    confirmButtonText: String = "Create",
    initialName: String = "",
    existingPlaylistNames: List<String> = emptyList(),
    originalName: String? = null,
    onDismiss: () -> Unit,
    onConfirmClick: (String) -> Unit
) {
    var playlistName by remember {
        mutableStateOf(initialName)
    }

    val trimmedName = playlistName.trim()

    val duplicateNameExists = existingPlaylistNames.any { existingName ->
        existingName.equals(trimmedName, ignoreCase = true) &&
                !existingName.equals(originalName, ignoreCase = true)
    }

    val errorMessage = when {
        trimmedName.isBlank() -> "Playlist name cannot be empty."
        duplicateNameExists -> "A playlist with this name already exists."
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title)
        },
        text = {
            OutlinedTextField(
                value = playlistName,
                onValueChange = { value ->
                    playlistName = value
                },
                label = {
                    Text(text = "Playlist name")
                },
                singleLine = true,
                isError = errorMessage != null,
                supportingText = {
                    if (errorMessage != null) {
                        Text(text = errorMessage)
                    }
                }
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmClick(trimmedName)
                },
                enabled = errorMessage == null
            ) {
                Text(text = confirmButtonText)
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