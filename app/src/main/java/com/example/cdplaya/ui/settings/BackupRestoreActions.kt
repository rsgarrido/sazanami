package com.example.cdplaya.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cdplaya.data.backup.AppBackup
import com.example.cdplaya.data.backup.BackupRestoreResult
import com.example.cdplaya.data.backup.BackupRestoreSummary
import kotlinx.coroutines.launch

data class BackupRestoreActions(
    val restoreBackup: () -> Unit
)

@Composable
fun rememberBackupRestoreActions(
    snackbarHostState: SnackbarHostState,
    onRead: (Uri, (Result<AppBackup>) -> Unit) -> Unit,
    onSummarize: (AppBackup) -> BackupRestoreSummary,
    onRestore: (AppBackup, (Result<BackupRestoreResult>) -> Unit) -> Unit
): BackupRestoreActions {
    val coroutineScope = rememberCoroutineScope()
    var pendingRestore by remember {
        mutableStateOf<PendingBackupRestore?>(null)
    }
    var isRestoring by remember {
        mutableStateOf(false)
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

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onRead(uri) { result ->
                result.fold(
                    onSuccess = { backup ->
                        pendingRestore = PendingBackupRestore(
                            backup = backup,
                            summary = onSummarize(backup)
                        )
                    },
                    onFailure = {
                        showMessage(invalidBackupErrorMessage())
                    }
                )
            }
        }
    }

    val selectedRestore = pendingRestore

    if (selectedRestore != null) {
        AlertDialog(
            onDismissRequest = {
                if (!isRestoring) {
                    pendingRestore = null
                }
            },
            title = {
                Text(text = "Restore backup?")
            },
            text = {
                Column {
                    Text(text = backupRestoreConfirmationText())

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = backupRestoreSummaryText(selectedRestore.summary),
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isRestoring,
                    onClick = {
                        pendingRestore = null
                    }
                ) {
                    Text(text = "Cancel")
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isRestoring,
                    onClick = {
                        isRestoring = true

                        onRestore(selectedRestore.backup) { result ->
                            isRestoring = false
                            pendingRestore = null

                            result.fold(
                                onSuccess = { restoreResult ->
                                    showMessage(
                                        backupRestoreSuccessMessage(restoreResult)
                                    )
                                },
                                onFailure = {
                                    showMessage("Couldn't restore backup.")
                                }
                            )
                        }
                    }
                ) {
                    Text(text = "Restore")
                }
            }
        )
    }

    return BackupRestoreActions(
        restoreBackup = {
            openDocumentLauncher.launch(
                arrayOf(
                    "application/json",
                    "application/zip",
                    "text/plain",
                    "application/octet-stream",
                    "*/*"
                )
            )
        }
    )
}

internal fun backupRestoreConfirmationText(): String {
    return """
        This will replace your current Sazanami app data:
        - Favorites
        - Playlists
        - Artist pictures and custom playlist artwork
        - Listening history, imported-track links, and ratings
        - Library folder selection
        - Player theme and ReplayGain setting

        Your music files will not be changed.
    """.trimIndent()
}

internal fun backupRestoreSummaryText(summary: BackupRestoreSummary): String {
    return listOf(
        restoreCountLabel(summary.favoriteCount, "favorite"),
        restoreCountLabel(summary.playlistCount, "playlist"),
        restoreCountLabel(summary.playlistSongCount, "playlist song"),
        restoreCountLabel(summary.listeningHistoryCount, "history entry"),
        restoreCountLabel(summary.selectedFolderCount, "selected folder"),
        restoreCountLabel(summary.visualAssetCount, "picture")
    ).joinToString(separator = "\n") { count -> "- $count" }
}

internal fun backupRestoreSuccessMessage(result: BackupRestoreResult): String {
    return "Backup restored. " +
        "${restoreCountLabel(result.playlistCount, "playlist")}, " +
        "${restoreCountLabel(result.favoriteCount, "favorite")}, " +
        "${restoreCountLabel(result.listeningHistoryCount, "history entry")}, " +
        "${restoreCountLabel(result.visualAssetCount, "picture")}."
}

internal fun invalidBackupErrorMessage(): String {
    return "Couldn't open backup. The file is invalid or uses an unsupported version."
}

private fun restoreCountLabel(count: Int, singularLabel: String): String {
    val label = when {
        count == 1 -> singularLabel
        singularLabel == "history entry" -> "history entries"
        else -> "${singularLabel}s"
    }

    return "$count $label"
}

private data class PendingBackupRestore(
    val backup: AppBackup,
    val summary: BackupRestoreSummary
)
