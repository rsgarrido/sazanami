package com.example.cdplaya.ui.tageditor

import android.app.Activity
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.cdplaya.data.BatchArtworkValue
import com.example.cdplaya.data.BatchCancellationSignal
import com.example.cdplaya.data.BatchEditIntent
import com.example.cdplaya.data.BatchMetadataExecutionResult
import com.example.cdplaya.data.BatchMetadataPlan
import com.example.cdplaya.data.BatchMetadataProgress
import com.example.cdplaya.data.BatchTargetStatus
import com.example.cdplaya.data.PreparedBatchArtwork
import com.example.cdplaya.data.Song
import com.example.cdplaya.data.frozenCopy
import com.example.cdplaya.data.membershipKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

sealed interface BatchExecutionUiState {
    val plan: BatchMetadataPlan

    data class Preparing(override val plan: BatchMetadataPlan) : BatchExecutionUiState

    data class Running(
        override val plan: BatchMetadataPlan,
        val progress: BatchMetadataProgress,
        val cancellationRequested: Boolean = false
    ) : BatchExecutionUiState

    data class Complete(
        override val plan: BatchMetadataPlan,
        val result: BatchMetadataExecutionResult
    ) : BatchExecutionUiState
}

data class BatchMetadataActions(
    val executionState: BatchExecutionUiState?,
    val apply: (BatchMetadataPlan) -> Unit,
    val cancel: () -> Unit,
    val closeResults: () -> Unit
)

private data class PendingBatchExecution(
    val plan: BatchMetadataPlan,
    val preparedArtwork: PreparedBatchArtwork?
)

@Composable
fun rememberBatchMetadataActions(
    songs: List<Song>,
    snackbarHostState: SnackbarHostState,
    onPrepareArtwork: (Uri) -> PreparedBatchArtwork?,
    onExecute: (
        BatchMetadataPlan,
        List<Song>,
        PreparedBatchArtwork?,
        BatchCancellationSignal,
        (BatchMetadataProgress) -> Unit
    ) -> BatchMetadataExecutionResult,
    onSuccessfulTargetsScanned: (List<Song>) -> Unit
): BatchMetadataActions {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentSongs by rememberUpdatedState(songs)
    var executionState by remember { mutableStateOf<BatchExecutionUiState?>(null) }
    var pendingExecution by remember { mutableStateOf<PendingBatchExecution?>(null) }
    var cancellationSignal by remember { mutableStateOf<BatchCancellationSignal?>(null) }

    fun showMessage(message: String) {
        coroutineScope.launch {
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short,
                withDismissAction = true
            )
        }
    }

    fun scanSuccessfulTargets(result: BatchMetadataExecutionResult) {
        val successfulSongs = result.targetResults
            .filter { it.status == BatchTargetStatus.SUCCESS }
            .mapNotNull { it.resolvedSong }
            .distinctBy { it.membershipKey() }
        if (successfulSongs.isEmpty()) return

        val remaining = AtomicInteger(successfulSongs.size)
        MediaScannerConnection.scanFile(
            context.applicationContext,
            successfulSongs.map(Song::filePath).toTypedArray(),
            null
        ) { _, _ ->
            if (remaining.decrementAndGet() == 0) {
                coroutineScope.launch {
                    delay(500)
                    onSuccessfulTargetsScanned(successfulSongs)
                }
            }
        }
    }

    fun execute(pending: PendingBatchExecution) {
        val signal = BatchCancellationSignal()
        val songsAtExecutionStart = currentSongs.toList()
        cancellationSignal = signal
        executionState = BatchExecutionUiState.Running(
            plan = pending.plan,
            progress = BatchMetadataProgress(0, pending.plan.selectedTrackCount, null)
        )
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                onExecute(
                    pending.plan,
                    songsAtExecutionStart,
                    pending.preparedArtwork,
                    signal
                ) { progress ->
                    coroutineScope.launch {
                        val running = executionState as? BatchExecutionUiState.Running
                        if (running?.plan == pending.plan) {
                            executionState = running.copy(progress = progress)
                        }
                    }
                }
            }
            cancellationSignal = null
            executionState = BatchExecutionUiState.Complete(pending.plan, result)
            scanSuccessfulTargets(result)
        }
    }

    val writeRequestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        val pending = pendingExecution
        pendingExecution = null
        if (pending == null) return@rememberLauncherForActivityResult

        if (activityResult.resultCode == Activity.RESULT_OK) {
            execute(pending)
        } else {
            executionState = null
            showMessage("Write permission was denied. No batch changes were made.")
        }
    }

    fun requestPermissionOrExecute(pending: PendingBatchExecution) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            execute(pending)
            return
        }

        val selectedKeys = pending.plan.selectedTargets.mapTo(mutableSetOf()) { it.referenceKey }
        val writableUris = currentSongs
            .filter { it.membershipKey() in selectedKeys }
            .map(Song::uri)
            .distinct()
        if (writableUris.isEmpty()) {
            execute(pending)
            return
        }

        try {
            pendingExecution = pending
            val writeRequest = MediaStore.createWriteRequest(
                context.contentResolver,
                writableUris
            )
            writeRequestLauncher.launch(
                IntentSenderRequest.Builder(writeRequest.intentSender).build()
            )
        } catch (exception: Exception) {
            pendingExecution = null
            executionState = null
            showMessage(exception.message ?: "Could not request write permission.")
        }
    }

    return BatchMetadataActions(
        executionState = executionState,
        apply = { reviewedPlan ->
            val frozenPlan = reviewedPlan.frozenCopy()
            executionState = BatchExecutionUiState.Preparing(frozenPlan)
            coroutineScope.launch {
                val artworkUri = frozenPlan.replacementArtworkUri()
                val preparedArtwork = if (artworkUri == null) {
                    null
                } else {
                    withContext(Dispatchers.IO) { onPrepareArtwork(artworkUri) }
                }
                if (artworkUri != null && preparedArtwork == null) {
                    executionState = null
                    showMessage("Replacement artwork could not be prepared. No files were changed.")
                } else {
                    requestPermissionOrExecute(
                        PendingBatchExecution(frozenPlan, preparedArtwork)
                    )
                }
            }
        },
        cancel = {
            cancellationSignal?.cancel()
            val running = executionState as? BatchExecutionUiState.Running
            if (running != null) executionState = running.copy(cancellationRequested = true)
        },
        closeResults = {
            if (executionState is BatchExecutionUiState.Complete) executionState = null
        }
    )
}

private fun BatchMetadataPlan.replacementArtworkUri(): Uri? {
    val set = artworkChange?.intent as? BatchEditIntent.Set ?: return null
    val present = set.value as? BatchArtworkValue.Present ?: return null
    return Uri.parse(present.artwork.previewUri)
}
