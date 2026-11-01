package io.github.rsgarrido.sazanami.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalResources
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.data.backup.BackupExportResult
import io.github.rsgarrido.sazanami.data.backup.BackupSizeLimitException
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
    val resources = LocalResources.current
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
                        showMessage(resources.backupExportSuccessMessage(exportResult))
                    },
                    onFailure = { failure ->
                        showMessage(resources.getString(if (failure is BackupSizeLimitException) R.string.lyrics_backup_limit else R.string.backup_export_failure))
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
