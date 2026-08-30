package io.github.rsgarrido.sazanami.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import io.github.rsgarrido.sazanami.data.backup.BackupExportResult
import java.time.LocalDate
import kotlinx.coroutines.launch

data class BackupExportActions(
    val exportBackup: () -> Unit
)

@Composable
fun rememberBackupExportActions(
    snackbarHostState: SnackbarHostState,
    onExport: (Uri, (Result<BackupExportResult>) -> Unit) -> Unit
): BackupExportActions {
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

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            onExport(uri) { result ->
                result.fold(
                    onSuccess = { exportResult ->
                        showMessage(backupExportSuccessMessage(exportResult))
                    },
                    onFailure = {
                        showMessage("Couldn't export backup.")
                    }
                )
            }
        }
    }

    return BackupExportActions(
        exportBackup = {
            createDocumentLauncher.launch(
                backupFilename(LocalDate.now())
            )
        }
    )
}

internal fun backupFilename(date: LocalDate): String {
    return "sazanami-backup-$date.sazanami"
}

internal fun backupExportSuccessMessage(result: BackupExportResult): String {
    return "Backup exported. " +
        "${countLabel(result.playlistCount, "playlist")}, " +
        "${countLabel(result.favoriteCount, "favorite")}, " +
        "${countLabel(result.listeningHistoryCount, "history entry")}, " +
        "${countLabel(result.visualAssetCount, "picture")}."
}

private fun countLabel(count: Int, singularLabel: String): String {
    val label = if (count == 1) {
        singularLabel
    } else if (singularLabel == "history entry") {
        "history entries"
    } else {
        "${singularLabel}s"
    }

    return "$count $label"
}
