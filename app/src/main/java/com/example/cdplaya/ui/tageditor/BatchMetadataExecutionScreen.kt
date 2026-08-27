package com.example.cdplaya.ui.tageditor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.cdplaya.data.BatchTargetStatus
import com.example.cdplaya.data.BatchMetadataTargetId
import java.io.File

@Composable
fun BatchMetadataExecutionScreen(
    state: BatchExecutionUiState,
    onCancel: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler {
        when (state) {
            is BatchExecutionUiState.Complete -> onDone()
            is BatchExecutionUiState.Running -> onCancel()
            is BatchExecutionUiState.Preparing -> Unit
        }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when (state) {
            is BatchExecutionUiState.Preparing -> PreparingContent(state)
            is BatchExecutionUiState.Running -> RunningContent(state, onCancel)
            is BatchExecutionUiState.Complete -> CompleteContent(state, onDone)
        }
    }
}

@Composable
private fun PreparingContent(state: BatchExecutionUiState.Preparing) {
    Text("Preparing batch", style = MaterialTheme.typography.headlineSmall)
    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    Text(
        "Preparing durable inputs for ${state.plan.selectedTrackCount} tracks. No files have been written yet.",
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun RunningContent(
    state: BatchExecutionUiState.Running,
    onCancel: () -> Unit
) {
    val progress = state.progress
    Text("Updating metadata", style = MaterialTheme.typography.headlineSmall)
    LinearProgressIndicator(
        progress = { progress.completedCount.toFloat() / progress.totalCount.coerceAtLeast(1) },
        modifier = Modifier.fillMaxWidth()
    )
    Text("${progress.completedCount} of ${progress.totalCount} tracks processed")
    progress.currentTarget?.let { target ->
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("Current", style = MaterialTheme.typography.labelLarge)
                Text(
                    target.displayLabel(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    Text(
        if (state.cancellationRequested) {
            "Cancellation requested. The current file will finish and verify safely; remaining files will not start."
        } else {
            "Cancel stops before the next file. The current file is allowed to finish and verify."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    OutlinedButton(
        onClick = onCancel,
        enabled = !state.cancellationRequested,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(if (state.cancellationRequested) "Cancelling…" else "Cancel")
    }
}

@Composable
private fun CompleteContent(
    state: BatchExecutionUiState.Complete,
    onDone: () -> Unit
) {
    val result = state.result
    Text(
        if (result.wasCancelled) "Batch cancelled" else "Batch complete",
        style = MaterialTheme.typography.headlineSmall
    )
    Text("${result.successCount} of ${state.plan.selectedTrackCount} tracks updated")
    if (result.failureCount > 0) Text("${result.failureCount} failed or could not be safely updated")
    if (result.notProcessedCount > 0) Text("${result.notProcessedCount} not processed")

    result.targetResults.forEach { targetResult ->
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (targetResult.status == BatchTargetStatus.SUCCESS) {
                    MaterialTheme.colorScheme.surfaceContainerLow
                } else {
                    MaterialTheme.colorScheme.errorContainer
                }
            )
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    targetResult.target.displayLabel(),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(targetResult.status.displayLabel())
                }
                targetResult.reason?.let { reason ->
                    Spacer(Modifier.height(4.dp))
                    Text(reason, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
}

private fun BatchTargetStatus.displayLabel(): String = when (this) {
    BatchTargetStatus.SUCCESS -> "Updated and verified"
    BatchTargetStatus.MISSING -> "Missing"
    BatchTargetStatus.IDENTITY_MISMATCH -> "File changed after review"
    BatchTargetStatus.UNSUPPORTED -> "Unsupported"
    BatchTargetStatus.WRITE_FAILED -> "Write failed"
    BatchTargetStatus.VERIFICATION_FAILED -> "Verification failed"
    BatchTargetStatus.NOT_PROCESSED -> "Not processed"
}

private fun BatchMetadataTargetId.displayLabel(): String = when {
    title.isNotBlank() && artist.isNotBlank() -> "$title — $artist"
    title.isNotBlank() -> title
    displayName.isNotBlank() -> displayName
    else -> File(filePath).name
}
