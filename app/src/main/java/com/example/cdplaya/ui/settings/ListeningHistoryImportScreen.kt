package com.example.cdplaya.ui.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.cdplaya.controller.ListeningHistoryImportFile
import com.example.cdplaya.controller.SpotifyImportUiError
import com.example.cdplaya.controller.SpotifyImportUiState
import com.example.cdplaya.data.importing.ListeningImportExecutionPhase
import com.example.cdplaya.data.importing.ListeningImportExecutionResult
import com.example.cdplaya.data.importing.spotify.SpotifyListeningHistoryImportPreview
import com.example.cdplaya.ui.AppShellTypography
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

data class SpotifyImportUiActions(
    val onEnter: () -> Unit,
    val onFilesSelected: (List<ListeningHistoryImportFile>) -> Unit,
    val onAnalyze: () -> Unit,
    val onCancelAnalysis: () -> Unit,
    val onImport: () -> Unit,
    val onCancelImport: () -> Unit,
    val onRetry: () -> Unit,
    val onChangeFiles: () -> Unit,
    val onCleanStaleImport: () -> Unit,
    val onImportMore: () -> Unit,
    val onDone: () -> Unit,
    val onBack: () -> Unit
)

@Composable
fun ListeningHistoryImportScreen(
    state: SpotifyImportUiState,
    actions: SpotifyImportUiActions,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showCancelDialog by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(OpenSpotifyHistoryDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            actions.onFilesSelected(
                SafListeningHistoryImportFile.fromUris(context.contentResolver, uris)
            )
        }
    }
    val activeImport = state is SpotifyImportUiState.Importing
    val blockedBack = state is SpotifyImportUiState.Cancelling ||
        state is SpotifyImportUiState.CleaningStaleImport ||
        state is SpotifyImportUiState.CheckingRecovery

    fun requestBack() {
        when {
            activeImport -> showCancelDialog = true
            state is SpotifyImportUiState.Analyzing -> actions.onCancelAnalysis()
            blockedBack -> Unit
            else -> actions.onBack()
        }
    }

    BackHandler { requestBack() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, top = 10.dp, end = 20.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = ::requestBack, enabled = !blockedBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            ) {
                Text(
                    text = "Import listening history",
                    style = AppShellTypography.ScreenTitle,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Spotify · Extended Streaming History",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        when (state) {
            SpotifyImportUiState.Landing -> LandingContent(onSelect = { picker.launch(Unit) })
            SpotifyImportUiState.CheckingRecovery -> ProgressContent(
                title = "Checking previous imports…",
                description = "Making sure it is safe to start a new import."
            )
            is SpotifyImportUiState.StaleImportRecovery -> RecoveryContent(state, actions)
            SpotifyImportUiState.CleaningStaleImport -> ProgressContent(
                title = "Cleaning up unfinished import…",
                description = "Published listening history will not be changed."
            )
            is SpotifyImportUiState.FilesSelected -> FilesSelectedContent(
                files = state.files,
                cancellationMessage = state.cancellationMessage,
                onChange = { picker.launch(Unit) },
                onAnalyze = actions.onAnalyze
            )
            is SpotifyImportUiState.Analyzing -> AnalysisProgressContent(state, actions)
            is SpotifyImportUiState.Preview -> PreviewContent(state.preview, actions)
            is SpotifyImportUiState.Importing -> ImportProgressContent(state, actions)
            is SpotifyImportUiState.Cancelling -> ProgressContent(
                title = "Cancelling import…",
                description = "CDPlaya is cleaning up unfinished changes."
            )
            is SpotifyImportUiState.Cancelled -> CancelledContent(actions)
            is SpotifyImportUiState.Success -> ResultContent(state.result, actions)
            is SpotifyImportUiState.Error -> ErrorContent(state, actions) { picker.launch(Unit) }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Cancel import?") },
            text = {
                Text("The current import will be stopped and unfinished changes will be cleaned up.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showCancelDialog = false
                    actions.onCancelImport()
                }) { Text("Cancel import") }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) { Text("Keep importing") }
            }
        )
    }
}

@Composable
private fun LandingContent(onSelect: () -> Unit) {
    ImportCard(
        title = "Spotify",
        icon = { Icon(Icons.Default.History, contentDescription = null) }
    ) {
        Text(
            "Select the JSON files from Spotify's Extended Streaming History export. " +
                "The simpler Account Data streaming-history files are not supported.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Your history is processed locally on this device.",
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 14.dp)
        )
        Button(onClick = onSelect, modifier = Modifier.padding(top = 20.dp)) {
            Text("Select JSON files")
        }
    }
}

@Composable
private fun FilesSelectedContent(
    files: List<ListeningHistoryImportFile>,
    cancellationMessage: Boolean,
    onChange: () -> Unit,
    onAnalyze: () -> Unit
) {
    ImportCard(
        title = "${formatCount(files.size.toLong())} ${if (files.size == 1) "file" else "files"} selected",
        icon = { Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null) }
    ) {
        if (cancellationMessage) {
            Text(
                "Analysis was cancelled. No listening history was added.",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        if (files.size <= 5) {
            files.forEach { file ->
                Text(
                    text = file.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
        ActionRow(
            primaryText = "Analyze",
            onPrimary = onAnalyze,
            secondaryText = "Change files",
            onSecondary = onChange
        )
    }
}

@Composable
private fun AnalysisProgressContent(
    state: SpotifyImportUiState.Analyzing,
    actions: SpotifyImportUiActions
) {
    ProgressContent(
        title = "Analyzing listening history…",
        description = if (state.recordsProcessed > 0) {
            "${formatCount(state.recordsProcessed)} records processed"
        } else {
            "Reading ${state.files.size} selected ${if (state.files.size == 1) "file" else "files"}."
        },
        actionText = "Cancel",
        onAction = actions.onCancelAnalysis
    )
}

@Composable
private fun PreviewContent(
    preview: SpotifyListeningHistoryImportPreview,
    actions: SpotifyImportUiActions
) {
    val analysis = preview.analysis
    val dedupe = preview.dedupe
    ImportCard(title = "Preview") {
        StatRow("Listening records found", analysis.totalRecords)
        StatRow("Music records", analysis.validMusicRecords)
        StatRow("New listening records", dedupe.newOccurrences, emphasize = true)
        StatRow("Already imported", dedupe.alreadyImportedOccurrences)
        StatRow("Overlap ignored", dedupe.overlappingOccurrencesSuppressed)
        StatRow(
            "Unsupported / non-music",
            analysis.podcastRecords + analysis.audiobookRecords +
                analysis.videoRecords + analysis.unknownRecords
        )
        StatRow("Invalid records", analysis.invalidRecords)
        DateRangeRow(analysis.earliestAt, analysis.latestAt)
        if (dedupe.newOccurrences == 0L) {
            Text(
                "Everything in these files has already been imported.",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp)
            )
            ActionRow(
                primaryText = "Done",
                onPrimary = actions.onDone,
                secondaryText = "Change files",
                onSecondary = actions.onChangeFiles
            )
        } else {
            Text(
                "${formatCount(dedupe.newOccurrences)} new listening records will be added. " +
                    "They will affect Statistics and remain on this device.",
                modifier = Modifier.padding(top = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ActionRow(
                primaryText = "Import history",
                onPrimary = actions.onImport,
                secondaryText = "Change files",
                onSecondary = actions.onChangeFiles
            )
        }
    }
}

@Composable
private fun ImportProgressContent(
    state: SpotifyImportUiState.Importing,
    actions: SpotifyImportUiActions
) {
    val progress = state.progress
    val phase = when (progress?.phase) {
        null, ListeningImportExecutionPhase.ANALYZING -> "Preparing…"
        ListeningImportExecutionPhase.IMPORTING -> "Importing history…"
        ListeningImportExecutionPhase.PUBLISHING -> "Publishing…"
        ListeningImportExecutionPhase.COMPLETED -> "Finishing…"
    }
    val total = state.preview.analysis.totalRecords
    val determinate = progress?.phase == ListeningImportExecutionPhase.ANALYZING ||
        progress?.phase == ListeningImportExecutionPhase.IMPORTING
    ImportCard(title = phase) {
        if (determinate && total > 0L) {
            val current = progress?.recordsProcessed?.coerceIn(0L, total) ?: 0L
            LinearProgressIndicator(
                progress = { current.toFloat() / total.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = "${formatCount(current)} of ${formatCount(total)} records"
                    }
            )
            Text(
                "${formatCount(current)} / ${formatCount(total)}",
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = phase }
            )
        }
        OutlinedButton(onClick = actions.onCancelImport, modifier = Modifier.padding(top = 20.dp)) {
            Text("Cancel")
        }
    }
}

@Composable
private fun ResultContent(result: ListeningImportExecutionResult, actions: SpotifyImportUiActions) {
    ImportCard(title = "Import complete") {
        Text(
            if (result.newPublished == 0L) {
                "No new listening records were added. Everything selected was already in your history."
            } else {
                "${formatCount(result.newPublished)} listening records were added to your history."
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        StatRow("Imported", result.newPublished, emphasize = true)
        StatRow("Already imported", result.alreadyImported)
        StatRow("Overlap ignored", result.overlappingOccurrencesSuppressed)
        StatRow("Skipped non-music", result.unsupportedMedia)
        StatRow("Invalid records", result.invalid)
        DateRangeRow(result.sourceRangeStart, result.sourceRangeEnd)
        ActionRow(
            primaryText = "Done",
            onPrimary = actions.onDone,
            secondaryText = "Import more",
            onSecondary = actions.onImportMore
        )
    }
}

@Composable
private fun CancelledContent(actions: SpotifyImportUiActions) {
    ImportCard(title = "Import cancelled") {
        Text("No new history from this import was added. Previously imported history is unchanged.")
        ActionRow(
            primaryText = "Done",
            onPrimary = actions.onDone,
            secondaryText = "Try again",
            onSecondary = actions.onChangeFiles
        )
    }
}

@Composable
private fun RecoveryContent(
    state: SpotifyImportUiState.StaleImportRecovery,
    actions: SpotifyImportUiActions
) {
    ImportCard(title = "An earlier import didn't finish") {
        Text(
            if (state.pendingBatchCount == null) {
                "CDPlaya couldn't verify or clean up an earlier import. Try again before starting a new import."
            } else {
                "CDPlaya can clean up its unfinished changes before starting another import. " +
                    "Published history will remain unchanged."
            }
        )
        if (state.cleanupFailed) {
            Text(
                "Cleanup couldn't be completed. Try again.",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        ActionRow(
            primaryText = if (state.cleanupFailed) "Try again" else "Clean up",
            onPrimary = actions.onCleanStaleImport,
            secondaryText = "Back",
            onSecondary = actions.onBack
        )
    }
}

@Composable
private fun ErrorContent(
    state: SpotifyImportUiState.Error,
    actions: SpotifyImportUiActions,
    onChangeFiles: () -> Unit
) {
    val base = when (state.error) {
        SpotifyImportUiError.ACCOUNT_DATA_FORMAT ->
            "This looks like Spotify's Account Data streaming history. CDPlaya currently supports Extended Streaming History instead."
        SpotifyImportUiError.UNKNOWN_JSON ->
            "These files don't appear to contain supported Spotify Extended Streaming History."
        SpotifyImportUiError.MALFORMED_JSON ->
            "One of the selected files couldn't be read as valid JSON."
        SpotifyImportUiError.FILE_ACCESS ->
            "CDPlaya could no longer access one of the selected files. Select the files again and retry."
        SpotifyImportUiError.NO_MUSIC ->
            "No music listening history was found in these files."
        SpotifyImportUiError.IMPORT_FAILED ->
            "The import couldn't be completed. Unfinished changes were cleaned up and it is safe to try again."
    }
    ImportCard(title = "Import couldn't continue") {
        Text(base, color = MaterialTheme.colorScheme.error)
        state.failedDisplayName?.let { name ->
            Text(
                "File: $name",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        ActionRow(
            primaryText = "Try again",
            onPrimary = actions.onRetry,
            secondaryText = "Change files",
            onSecondary = onChangeFiles
        )
    }
}

@Composable
private fun ProgressContent(
    title: String,
    description: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    ImportCard(title = title) {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = title }
        )
        Text(
            description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp)
        )
        if (actionText != null && onAction != null) {
            OutlinedButton(onClick = onAction, modifier = Modifier.padding(top = 20.dp)) {
                Text(actionText)
            }
        }
    }
}

@Composable
private fun ImportCard(
    title: String,
    icon: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    icon()
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Text(title, style = AppShellTypography.SectionTitle)
            }
            Column(modifier = Modifier.padding(top = 14.dp), content = content)
        }
    }
}

@Composable
private fun ActionRow(
    primaryText: String,
    onPrimary: () -> Unit,
    secondaryText: String,
    onSecondary: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 22.dp)
    ) {
        Button(onClick = onPrimary, modifier = Modifier.fillMaxWidth()) { Text(primaryText) }
        OutlinedButton(onClick = onSecondary, modifier = Modifier.fillMaxWidth()) {
            Text(secondaryText)
        }
    }
}

@Composable
private fun StatRow(label: String, value: Long, emphasize: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            formatCount(value),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 12.dp),
            color = if (emphasize) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun DateRangeRow(earliest: Instant?, latest: Instant?) {
    if (earliest == null || latest == null) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            "Date range",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            formatRange(earliest, latest),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

private fun formatCount(value: Long): String = NumberFormat.getIntegerInstance().format(value)

private fun formatRange(earliest: Instant, latest: Instant): String {
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    val zone = ZoneId.systemDefault()
    val first = formatter.format(earliest.atZone(zone))
    val last = formatter.format(latest.atZone(zone))
    return if (first == last) first else "$first – $last"
}
