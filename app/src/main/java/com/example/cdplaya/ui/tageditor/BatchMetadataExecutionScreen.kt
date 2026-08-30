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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.cdplaya.data.BatchMetadataOperationState
import com.example.cdplaya.data.BatchMetadataTargetId
import com.example.cdplaya.data.BatchPostWriteStageResult
import com.example.cdplaya.data.BatchTargetResult
import com.example.cdplaya.data.BatchTargetStatus
import com.example.cdplaya.data.BatchTerminalOutcome
import com.example.cdplaya.data.isRetryableFailure
import java.io.File

@Composable
fun BatchMetadataExecutionScreen(
    state: BatchMetadataOperationState,
    onCancel: () -> Unit,
    onRetryFailed: () -> Unit,
    onContinueUnprocessed: () -> Unit,
    onRetryRefresh: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler {
        when (state) {
            is BatchMetadataOperationState.Complete,
            is BatchMetadataOperationState.Interrupted -> onDone()
            is BatchMetadataOperationState.Preparing,
            is BatchMetadataOperationState.AwaitingPermission,
            is BatchMetadataOperationState.Running,
            is BatchMetadataOperationState.PostProcessing -> onCancel()
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
            is BatchMetadataOperationState.Interrupted -> InterruptedContent(onDone)
            is BatchMetadataOperationState.Preparing -> PreparingContent(state)
            is BatchMetadataOperationState.AwaitingPermission -> PermissionContent(state, onCancel)
            is BatchMetadataOperationState.Running -> RunningContent(state, onCancel)
            is BatchMetadataOperationState.PostProcessing -> PostProcessingContent(state, onCancel)
            is BatchMetadataOperationState.Complete -> CompleteContent(
                state,
                onRetryFailed,
                onContinueUnprocessed,
                onRetryRefresh,
                onDone
            )
        }
    }
}

@Composable
private fun InterruptedContent(onDone: () -> Unit) {
    Text("Previous batch interrupted", style = MaterialTheme.typography.headlineSmall)
    Text(
        "A previous metadata batch ended while its outcome was still uncertain. Some files may " +
            "have completed. Sazanami will not replay it automatically. Rescan the library and " +
            "review the affected files before creating a new batch."
    )
    Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Acknowledge") }
}

@Composable
private fun PreparingContent(state: BatchMetadataOperationState.Preparing) {
    Text("Preparing batch", style = MaterialTheme.typography.headlineSmall)
    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    Text("Preparing durable inputs for ${state.plan.selectedTrackCount} tracks.")
}

@Composable
private fun PermissionContent(
    state: BatchMetadataOperationState.AwaitingPermission,
    onCancel: () -> Unit
) {
    Text("Requesting write access", style = MaterialTheme.typography.headlineSmall)
    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    Text(
        "Permission group ${state.batchIndex + 1} of ${state.permissionBatches.size}. " +
            "No metadata writes begin until every required group is approved."
    )
    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
        Text("Cancel batch")
    }
}

@Composable
private fun RunningContent(
    state: BatchMetadataOperationState.Running,
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
        Card(colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )) {
            Column(Modifier.padding(14.dp)) {
                Text("Current", style = MaterialTheme.typography.labelLarge)
                Text(target.displayLabel(), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    Text(
        if (state.cancellationRequested) {
            "Cancellation requested. The active file will finish and verify safely."
        } else {
            "Cancel stops before the next file; it never interrupts a physical file write."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    OutlinedButton(
        onClick = onCancel,
        enabled = !state.cancellationRequested,
        modifier = Modifier.fillMaxWidth()
    ) { Text(if (state.cancellationRequested) "Cancelling…" else "Cancel") }
}

@Composable
private fun PostProcessingContent(
    state: BatchMetadataOperationState.PostProcessing,
    onCancel: () -> Unit
) {
    Text("Refreshing library", style = MaterialTheme.typography.headlineSmall)
    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    Text("${state.result.successCount} metadata writes succeeded and were verified.")
    Text("MediaStore scan: ${state.scan.displayLabel()}")
    Text("Library refresh: ${state.refresh.displayLabel()}")
    Text(
        "Stop waiting records a refresh warning; it does not undo or repeat file writes.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
        Text("Stop waiting")
    }
}

@Composable
private fun CompleteContent(
    state: BatchMetadataOperationState.Complete,
    onRetryFailed: () -> Unit,
    onContinueUnprocessed: () -> Unit,
    onRetryRefresh: () -> Unit,
    onDone: () -> Unit
) {
    val result = state.result
    var detailsExpanded by remember(state.operationId) { mutableStateOf(false) }
    Text(state.terminalOutcome.displayLabel(), style = MaterialTheme.typography.headlineSmall)
    if (result.successCount > 0) {
        Text("${result.successCount} ${trackWord(result.successCount)} updated")
    }
    if (result.failureCount > 0) {
        Text("${result.failureCount} could not be updated")
    }
    if (result.notProcessedCount > 0) {
        Text("${result.notProcessedCount} ${trackWord(result.notProcessedCount)} not processed")
    }
    if (state.scan.hasWarning || state.refresh.hasWarning) {
        Card(colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Library refresh is still pending", style = MaterialTheme.typography.titleSmall)
                Text("Completed file updates remain successful and will not be repeated.")
            }
        }
    }

    OutlinedButton(
        onClick = { detailsExpanded = !detailsExpanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(if (detailsExpanded) "Hide details" else "Show details")
    }
    if (detailsExpanded) {
        if (state.scan.hasWarning || state.refresh.hasWarning) {
            Text("MediaStore scan: ${state.scan.displayLabel()}")
            state.scan.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text("Library refresh: ${state.refresh.displayLabel()}")
            state.refresh.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        result.targetResults.forEach { TargetResultCard(it) }
    }

    if (result.targetResults.any(BatchTargetResult::isRetryableFailure)) {
        Button(onClick = onRetryFailed, modifier = Modifier.fillMaxWidth()) {
            Text("Retry failed")
        }
    }
    if (result.notProcessedCount > 0) {
        OutlinedButton(onClick = onContinueUnprocessed, modifier = Modifier.fillMaxWidth()) {
            Text("Continue unprocessed")
        }
    }
    if (state.scan.hasWarning || state.refresh.hasWarning) {
        OutlinedButton(onClick = onRetryRefresh, modifier = Modifier.fillMaxWidth()) {
            Text("Retry library refresh")
        }
    }
    Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
}

@Composable
private fun TargetResultCard(targetResult: BatchTargetResult) {
    Card(colors = CardDefaults.cardColors(
        containerColor = if (targetResult.status == BatchTargetStatus.SUCCESS) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.errorContainer
        }
    )) {
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

private fun BatchTargetStatus.displayLabel(): String = when (this) {
    BatchTargetStatus.SUCCESS -> "Updated and verified"
    BatchTargetStatus.MISSING -> "Missing — not retryable"
    BatchTargetStatus.IDENTITY_MISMATCH -> "File changed — not retryable"
    BatchTargetStatus.UNSUPPORTED -> "Unsupported — not retryable"
    BatchTargetStatus.WRITE_FAILED -> "Write failed — retryable"
    BatchTargetStatus.VERIFICATION_FAILED -> "Verification failed — retryable"
    BatchTargetStatus.PERMISSION_DENIED -> "Permission denied — retryable"
    BatchTargetStatus.NOT_PROCESSED -> "Not processed"
}

private fun BatchTerminalOutcome.displayLabel(): String = when (this) {
    BatchTerminalOutcome.SUCCESS -> "Metadata updated"
    BatchTerminalOutcome.PARTIAL_SUCCESS -> "Some tracks were not updated"
    BatchTerminalOutcome.CANCELLED -> "Update cancelled"
    BatchTerminalOutcome.FAILED -> "Tracks could not be updated"
    BatchTerminalOutcome.PERMISSION_DENIED -> "Write permission denied"
    BatchTerminalOutcome.REFRESH_WARNING -> "Metadata updated"
}

private fun trackWord(count: Int): String = if (count == 1) "track" else "tracks"

private fun BatchPostWriteStageResult.displayLabel(): String =
    status.name.lowercase().replace('_', ' ')

private fun BatchMetadataTargetId.displayLabel(): String = when {
    title.isNotBlank() && artist.isNotBlank() -> "$title — $artist"
    title.isNotBlank() -> title
    displayName.isNotBlank() -> displayName
    else -> File(filePath).name
}
