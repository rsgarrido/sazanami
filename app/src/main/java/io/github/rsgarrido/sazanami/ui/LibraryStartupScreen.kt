package io.github.rsgarrido.sazanami.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.mediaaccess.MediaAccessState
import io.github.rsgarrido.sazanami.mediaaccess.PermissionAccess

@Composable
internal fun LibraryStartupScreen(
    mediaAccessState: MediaAccessState,
    initialLibraryReady: Boolean,
    folderArtworkOnboardingComplete: Boolean,
    onRequestAudioAccess: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onChooseFolderArtwork: () -> Unit,
    onSkipFolderArtwork: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 30.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.size(24.dp))

        when {
            !mediaAccessState.hasAudioAccess -> {
                Text(
                    text = "Find your music",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    text = "Sazanami needs access to audio files on this device to build your local music library. Your music stays on your device.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.size(22.dp))
                if (mediaAccessState.audioAccess == PermissionAccess.PERMANENTLY_DENIED) {
                    Button(onClick = onOpenAppSettings) { Text("Open app settings") }
                } else {
                    Button(onClick = onRequestAudioAccess) {
                        Text(if (mediaAccessState.audioPermissionRequested) "Try again" else "Grant music access")
                    }
                }
            }

            !initialLibraryReady -> {
                CircularProgressIndicator()
                Spacer(Modifier.size(18.dp))
                Text(
                    text = "Finding your music…",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    text = "Songs will appear as soon as the device music index is ready. Artwork can continue loading afterward.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            !folderArtworkOnboardingComplete -> {
                Text(
                    text = "Optional folder artwork",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    text = "If your music folders contain files such as cover.jpg, folder.jpg, or front.png, choose your Music folder and Sazanami can use them. Embedded album artwork works without this access.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.size(22.dp))
                Button(onClick = onChooseFolderArtwork) { Text("Choose music folder") }
                Spacer(Modifier.size(10.dp))
                OutlinedButton(onClick = onSkipFolderArtwork) { Text("Not now") }
            }
        }
    }
}
