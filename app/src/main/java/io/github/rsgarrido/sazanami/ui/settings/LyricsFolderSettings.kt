package io.github.rsgarrido.sazanami.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rsgarrido.sazanami.ui.AppShellIcons

@Composable
fun LyricsFolderSettings(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val controller = remember(context.applicationContext) {
        LyricsSettingsController.shared(context.applicationContext)
    }
    val state by controller.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) controller.addRoot(uri)
    }

    val folderCountLabel = if (state.roots.size == 1) "folder" else "folders"
    val fileCountLabel = if (state.indexedFileCount == 1) "file" else "files"
    val folderSummary = when {
        state.roots.isEmpty() ->
            "No folders selected. Local .lrc files will not be indexed."
        else ->
            "${state.roots.size} $folderCountLabel • ${state.indexedFileCount} .lrc $fileCountLabel"
    }

    SettingsSection(
        title = "Local lyrics",
        description = "Manage folders containing synced .lrc lyric files.",
        icon = AppShellIcons.Lyrics,
        modifier = modifier
    ) {
        SettingsRow(
            title = "Lyrics folders",
            summary = folderSummary,
            icon = AppShellIcons.Lyrics,
            emphasizeSummary = state.roots.isNotEmpty()
        )

        state.roots.forEach { item ->
            SettingsDivider()

            SettingsRow(
                title = item.root.displayName.ifBlank { item.root.uri },
                summary = if (item.hasPersistedAccess) {
                    item.root.uri
                } else {
                    "Persisted folder access is missing"
                },
                icon = AppShellIcons.Folder,
                emphasizeSummary = !item.hasPersistedAccess,
                trailingContent = {
                    TextButton(onClick = { controller.removeRoot(item.root.uri) }) {
                        Text("Remove")
                    }
                }
            )
        }

        SettingsDivider()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                enabled = !state.isScanning,
                onClick = { picker.launch(null) }
            ) {
                Text("Add folder")
            }

            OutlinedButton(
                enabled = !state.isScanning && state.roots.isNotEmpty(),
                onClick = controller::rescan
            ) {
                Text("Rescan")
            }

            if (state.isScanning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp
                )
            }
        }

        state.message?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
            )
        }

        SettingsFooterNote(
            text = "Removing a folder stops indexing it but does not revoke Android's " +
                    "persisted folder permission."
        )
    }
}
