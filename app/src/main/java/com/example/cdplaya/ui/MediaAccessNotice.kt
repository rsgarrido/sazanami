package com.example.cdplaya.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.cdplaya.mediaaccess.MediaAccessState
import com.example.cdplaya.mediaaccess.PermissionAccess

@Composable
internal fun MediaAccessNotice(
    state: MediaAccessState,
    onRequestAudioAccess: () -> Unit,
    onRequestArtworkAccess: () -> Unit,
    onOpenAppSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!state.hasAudioAccess) {
            Text(
                text = "Audio access needed",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = if (state.audioPermissionRequested) {
                    "Sazanami still needs access to audio files to build your music library."
                } else {
                    "Sazanami needs access to audio files on this device to build your music library."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            when (state.audioAccess) {
                PermissionAccess.PERMANENTLY_DENIED -> {
                    Text(
                        text = "Audio access is disabled. Enable it in Android app settings.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = onOpenAppSettings) {
                        Text("Open app settings")
                    }
                }
                PermissionAccess.REQUESTABLE,
                PermissionAccess.DENIED -> {
                    Button(onClick = onRequestAudioAccess) {
                        Text(
                            if (state.audioPermissionRequested) {
                                "Try again"
                            } else {
                                "Grant audio access"
                            }
                        )
                    }
                }
                PermissionAccess.GRANTED,
                PermissionAccess.NOT_REQUIRED -> Unit
            }
        } else if (!state.hasArtworkAccess) {
            Text(
                text = "Folder artwork access is optional",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = "Songs and embedded cover art remain available. Allow image access only to find standalone folder covers.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.artworkAccess == PermissionAccess.PERMANENTLY_DENIED) {
                OutlinedButton(onClick = onOpenAppSettings) {
                    Text("Open app settings")
                }
            } else {
                OutlinedButton(onClick = onRequestArtworkAccess) {
                    Text(
                        if (state.artworkPermissionRequested) {
                            "Try folder artwork again"
                        } else {
                            "Allow folder artwork"
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun LibraryLoadingNotice(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CircularProgressIndicator()
        Text("Loading music library")
    }
}

@Composable
internal fun EmptyLibraryNotice(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "No music found",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = "Check that music files are in shared storage, Android has indexed them, and your selected library folders include them.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun LibraryErrorNotice(
    message: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Music library unavailable",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error
        )
    }
}
