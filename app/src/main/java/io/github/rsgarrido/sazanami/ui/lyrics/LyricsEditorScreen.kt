package io.github.rsgarrido.sazanami.ui.lyrics

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.lyrics.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

val LocalLyricsEditor = staticCompositionLocalOf<LyricsEditorController?> { null }

private data class LyricsExportRequest(val name: String, val format: LyricsExportFormat)
private class CreateLyricsDocument : ActivityResultContract<LyricsExportRequest, Uri?>() {
    override fun createIntent(context: Context, input: LyricsExportRequest): Intent =
        ActivityResultContracts.CreateDocument(input.format.mimeType)
            .createIntent(context, input.name)
    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        ActivityResultContracts.CreateDocument("text/plain").parseResult(resultCode, intent)
}

@Composable
fun LyricsEditorHost(controller: LyricsEditorController) {
    val state by controller.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(controller, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) controller.flushDraft() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); controller.flushDraft() }
    }
    if (!state.visible) return
    Dialog(onDismissRequest = controller::requestClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize()) {
            LyricsEditorScreen(controller, state)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LyricsEditorScreen(controller: LyricsEditorController, state: LyricsEditorUiState) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var pickerSession by rememberSaveable { mutableStateOf<String?>(null) }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val session = pickerSession
        scope.launch {
            try {
                val bytes = LyricsDocumentTransfer.importDocument(context.contentResolver, uri)
                if (controller.state.value.sessionId == session) controller.importBytes(bytes)
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (failure: LyricsEditException) { if (controller.state.value.sessionId == session) controller.report(failure.reason) }
        }
    }
    val exportPicker = rememberLauncherForActivityResult(remember { CreateLyricsDocument() }) { uri ->
        controller.completeExport { bytes -> LyricsDocumentTransfer.exportDocument(context.contentResolver, uri, bytes) }
    }
    BackHandler { controller.requestClose() }
    var offsetDialog by rememberSaveable { mutableStateOf(false) }
    var offsetText by rememberSaveable { mutableStateOf("") }
    val source = state.source
    val enabled = !state.busy && !state.parsing && state.recovery == null && state.baseline != null
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
    val toolbarMaxHeight = maxHeight * 0.45f
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp).testTag("lyrics_editor")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = controller::requestClose, enabled = !state.busy) { Text(stringResource(R.string.lyrics_editor_cancel)) }
            Text(stringResource(R.string.lyrics_editor_title), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            Button(onClick = { controller.save() }, enabled = enabled && source != null && state.rowId == null && (state.dirty || !state.hasOverride),
                modifier = Modifier.testTag("lyrics_editor_save")) { Text(stringResource(R.string.lyrics_editor_save)) }
        }
        Column(Modifier.fillMaxWidth().heightIn(max = toolbarMaxHeight).verticalScroll(rememberScrollState())) {
        Text(state.song?.title.orEmpty(), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.lyrics_editor_local_notice), style = MaterialTheme.typography.bodySmall)
        if (state.busy || state.parsing) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { Text(stringResource(editorErrorResource(it)), color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("lyrics_editor_error")) }
        if (state.exported) Text(stringResource(R.string.lyrics_editor_exported), style = MaterialTheme.typography.bodySmall)
        if (state.exported) state.exportedName?.let { Text(stringResource(R.string.lyrics_editor_export_name, it), style = MaterialTheme.typography.bodySmall) }
        if (state.saved && !state.dirty) Text(stringResource(R.string.lyrics_editor_saved), modifier = Modifier.testTag("lyrics_editor_saved"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            LyricsEditorMode.entries.forEach { mode ->
                FilterChip(selected = state.mode == mode, onClick = { controller.setMode(mode) }, enabled = enabled && state.rowId == null,
                    label = { Text(stringResource(when (mode) {
                        LyricsEditorMode.PLAIN -> R.string.lyrics_editor_plain
                        LyricsEditorMode.STRUCTURED -> R.string.lyrics_editor_sync
                        LyricsEditorMode.RAW -> R.string.lyrics_editor_raw
                    })) })
            }
            TextButton(onClick = controller::undo, enabled = enabled && state.canUndo) { Text(stringResource(R.string.lyrics_editor_undo)) }
            TextButton(onClick = {
                pickerSession = state.sessionId
                try { importPicker.launch(arrayOf("text/*", "application/octet-stream")) }
                catch (_: Exception) { controller.report(LyricsEditError.IMPORT_READ) }
            }, enabled = enabled) { Text(stringResource(R.string.lyrics_editor_import)) }
            TextButton(onClick = {
                scope.launch {
                    try {
                        val bytes = controller.exportBytes()
                        if (controller.state.value.sessionId != state.sessionId) return@launch
                        pickerSession = state.sessionId
                        controller.holdExport(bytes)
                        val format = source?.let { LyricsExportFormat.forSource(it) } ?: LyricsExportFormat.PLAIN
                        val stem = state.song?.displayName?.substringBeforeLast('.')?.take(80)?.replace(Regex("[^\\p{L}\\p{N} _.-]"), "_")?.ifBlank { null } ?: resources.getString(R.string.lyrics_editor_filename)
                        exportPicker.launch(LyricsExportRequest("$stem.${format.extension}", format))
                    } catch (cancelled: CancellationException) { throw cancelled }
                      catch (failure: LyricsEditException) { controller.report(failure.reason) }
                      catch (_: Exception) { controller.completeExport { throw LyricsEditException(LyricsEditError.EXPORT_WRITE) } }
                }
            }, enabled = enabled && source != null && state.rowId == null) { Text(stringResource(R.string.lyrics_editor_export)) }
            if (state.hasOverride) TextButton(onClick = controller::requestRemove, enabled = enabled) { Text(stringResource(R.string.lyrics_editor_remove)) }
            TextButton(onClick = controller::manageDrafts, enabled = !state.busy && !state.parsing && state.recovery == null) { Text(stringResource(R.string.lyrics_editor_manage_drafts)) }
        }
        if (source != null) {
            if (state.mode == LyricsEditorMode.STRUCTURED) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.Center) {
                    TextButton(onClick = { controller.shiftOffset(-100) }, enabled = enabled) { Text(stringResource(R.string.lyrics_editor_earlier)) }
                    TextButton(onClick = { offsetText = source.metadata.offsetMs.toString(); offsetDialog = true }, enabled = enabled) {
                        Text(stringResource(R.string.lyrics_editor_offset, source.metadata.offsetMs))
                    }
                    TextButton(onClick = { controller.shiftOffset(100) }, enabled = enabled) { Text(stringResource(R.string.lyrics_editor_later)) }
                    TextButton(onClick = { controller.setOffset(0) }, enabled = enabled) { Text(stringResource(R.string.lyrics_editor_zero)) }
                    TextButton(onClick = { controller.setOffset(state.baseline?.originalOffsetMs ?: 0) }, enabled = enabled) { Text(stringResource(R.string.lyrics_editor_original)) }
                }
                Text(stringResource(R.string.lyrics_editor_stamping_notice), style = MaterialTheme.typography.bodySmall)
                if (state.target?.trackKey != state.activeTrackKey) Text(stringResource(R.string.lyrics_editor_other_track), color = MaterialTheme.colorScheme.error)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(onClick = controller::stamp, enabled = enabled && state.canStamp, modifier = Modifier.testTag("lyrics_editor_stamp")) { Text(stringResource(R.string.lyrics_editor_stamp)) }
                    TextButton(onClick = controller::skip, enabled = enabled) { Text(stringResource(R.string.lyrics_editor_skip)) }
                    TextButton(onClick = controller.playPause, enabled = enabled && state.target?.trackKey == state.activeTrackKey) { Text(stringResource(R.string.lyrics_editor_play_pause)) }
                    TextButton(onClick = controller::replaySelected, enabled = enabled && state.canStamp) { Text(stringResource(R.string.lyrics_editor_replay)) }
                    TextButton(onClick = controller::preview, enabled = enabled && state.canStamp) { Text(stringResource(R.string.lyrics_editor_preview)) }
                    TextButton(onClick = controller::insert, enabled = enabled) { Text(stringResource(R.string.lyrics_editor_insert)) }
                }
            }
            state.previewPosition?.let { time ->
                val projection = remember(source) { LrcParser().project(source) }
                val lines = (projection as? LyricsDocument.Synced)?.let { ActiveLyricResolver.resolve(it.cues, time)?.lines }.orEmpty()
                Text(stringResource(R.string.lyrics_editor_preview_at, LyricsDocumentEditor.formatTime(time), lines.joinToString(" / ")),
                    style = MaterialTheme.typography.bodyMedium)
            }
            val warnings = source.diagnostics.map { it.code }.distinct()
            val outOfOrder = remember(source) {
                source.lines.flatMap { it.timestamps }.zipWithNext().any { (a, b) -> a.timestampMs > b.timestampMs }
            }
            if (outOfOrder) Text(stringResource(R.string.lyrics_editor_time_order), style = MaterialTheme.typography.bodySmall)
            if (warnings.isNotEmpty()) Text(stringResource(R.string.lyrics_editor_warnings,
                warnings.joinToString("; ") { resources.getString(diagnosticResource(it)) }), style = MaterialTheme.typography.bodySmall)
        }
        }
        if (source != null) {
            if (state.mode == LyricsEditorMode.STRUCTURED) {
                val list = rememberLazyListState()
                LaunchedEffect(state.selectedLine, source.lines.size) { list.animateScrollToItem(state.selectedLine.coerceIn(source.lines.indices)) }
                LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("lyrics_editor_rows"), state = list) {
                    itemsIndexed(source.lines, key = { _, row -> row.id }) { index, row ->
                        Surface(color = if (index == state.selectedLine) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth().semantics { selected = index == state.selectedLine }.clickable(enabled = enabled) { controller.select(index) }) {
                            Column(Modifier.padding(8.dp)) {
                                Text(stringResource(R.string.lyrics_editor_line, index + 1), style = MaterialTheme.typography.labelMedium)
                                Text(row.timestamps.joinToString(" ") { it.raw }.ifEmpty { stringResource(R.string.lyrics_editor_untimed) }, style = MaterialTheme.typography.labelLarge)
                                Text(row.displayedText.ifEmpty { stringResource(R.string.lyrics_editor_blank) })
                                if (row.kind == LyricsSourceLineKind.METADATA || row.kind == LyricsSourceLineKind.UNSUPPORTED)
                                    Text(stringResource(R.string.lyrics_editor_raw_retained), style = MaterialTheme.typography.bodySmall)
                                Row {
                                    IconButton(onClick = { controller.editRow(row.id) }, enabled = enabled && LyricsDocumentEditor().editable(row)) {
                                        Icon(Icons.Default.Edit, stringResource(R.string.lyrics_editor_edit_line, index + 1))
                                    }
                                    IconButton(onClick = { controller.moveLine(row.id, -1) }, enabled = enabled && index > 0) {
                                        Icon(Icons.Default.ArrowUpward, stringResource(R.string.lyrics_editor_up, index + 1))
                                    }
                                    IconButton(onClick = { controller.moveLine(row.id, 1) }, enabled = enabled && index < source.lines.lastIndex) {
                                        Icon(Icons.Default.ArrowDownward, stringResource(R.string.lyrics_editor_down, index + 1))
                                    }
                                    IconButton(onClick = { controller.removeLine(row.id) }, enabled = enabled) {
                                        Icon(Icons.Default.Delete, stringResource(R.string.lyrics_editor_delete_line, index + 1))
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                OutlinedTextField(value = state.text, onValueChange = controller::setText, enabled = !state.busy,
                    label = { Text(stringResource(if (state.mode == LyricsEditorMode.RAW) R.string.lyrics_editor_raw_text else R.string.lyrics_editor_plain_text)) },
                    modifier = Modifier.weight(1f).fillMaxWidth().testTag("lyrics_editor_text"))
            }
        } else Spacer(Modifier.weight(1f))
    }
    }
    if (state.rowId != null && !state.rowCloseConfirmation && state.confirmation != LyricsEditorConfirmation.WORD_TIMINGS) AlertDialog(
        onDismissRequest = controller::requestRowClose,
        title = { Text(stringResource(R.string.lyrics_editor_edit_row)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            OutlinedTextField(state.rowText, controller::setRowText, enabled = !state.busy, label = { Text(stringResource(R.string.lyrics_editor_line_text)) })
            OutlinedTextField(state.rowTimes, controller::setRowTimes, enabled = !state.busy, label = { Text(stringResource(R.string.lyrics_editor_timestamps)) },
                supportingText = { Text(stringResource(R.string.lyrics_editor_time_help)) })
            state.error?.let { Text(stringResource(editorErrorResource(it)), color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(onClick = { controller.applyRow() }, enabled = !state.busy) { Text(stringResource(R.string.lyrics_editor_apply)) } },
        dismissButton = { TextButton(onClick = controller::cancelRow, enabled = !state.busy) { Text(stringResource(R.string.lyrics_editor_cancel)) } })
    if (state.rowCloseConfirmation && state.confirmation != LyricsEditorConfirmation.WORD_TIMINGS) AlertDialog(
        onDismissRequest = controller::keepRowEditing,
        title = { Text(stringResource(R.string.lyrics_editor_row_unsaved)) },
        text = { Text(stringResource(R.string.lyrics_editor_row_unsaved_detail)) },
        confirmButton = { TextButton(onClick = { controller.applyRow() }, enabled = !state.busy) { Text(stringResource(R.string.lyrics_editor_apply)) } },
        dismissButton = { Row {
            TextButton(onClick = controller::cancelRow, enabled = !state.busy) { Text(stringResource(R.string.lyrics_editor_discard)) }
            TextButton(onClick = controller::keepRowEditing, enabled = !state.busy) { Text(stringResource(R.string.lyrics_editor_keep_editing)) }
        } })
    if (offsetDialog) AlertDialog(onDismissRequest = { offsetDialog = false }, title = { Text(stringResource(R.string.lyrics_editor_offset_title)) },
        text = { OutlinedTextField(offsetText, { offsetText = it }, label = { Text(stringResource(R.string.lyrics_editor_offset_help)) },
            isError = state.error == LyricsEditError.INVALID_OFFSET,
            supportingText = { if (state.error == LyricsEditError.INVALID_OFFSET) Text(stringResource(R.string.lyrics_editor_bad_offset)) }) },
        confirmButton = { TextButton(onClick = {
            val value = offsetText.trim().toLongOrNull()
            if (value == null) controller.report(LyricsEditError.INVALID_OFFSET) else { controller.setOffset(value); offsetDialog = false }
        }) { Text(stringResource(R.string.lyrics_editor_apply)) } }, dismissButton = { TextButton(onClick = { offsetDialog = false }) { Text(stringResource(R.string.lyrics_editor_cancel)) } })
    if (state.recovery != null) AlertDialog(onDismissRequest = controller::requestClose,
        title = { Text(stringResource(R.string.lyrics_editor_recovery)) }, text = { Text(stringResource(R.string.lyrics_editor_recovery_detail)) },
        confirmButton = { TextButton(onClick = controller::recoverDraft, enabled = !state.busy) { Text(stringResource(R.string.lyrics_editor_recover)) } },
        dismissButton = { TextButton(onClick = controller::discardRecovery, enabled = !state.busy) { Text(stringResource(R.string.lyrics_editor_discard)) } })
    state.otherDrafts?.let { drafts ->
        AlertDialog(onDismissRequest = controller::closeDraftManager,
            title = { Text(stringResource(R.string.lyrics_editor_other_drafts)) },
            text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                if (drafts.isEmpty()) Text(stringResource(R.string.lyrics_editor_no_other_drafts))
                drafts.forEach { draft ->
                    Text(draft.title ?: stringResource(R.string.lyrics_editor_unknown_track), style = MaterialTheme.typography.titleSmall)
                    Text(java.time.Instant.ofEpochMilli(draft.updatedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString())
                    TextButton(onClick = { controller.requestDiscardDraft(draft) }, enabled = !state.busy) { Text(stringResource(R.string.lyrics_editor_discard)) }
                }
                state.error?.let { Text(stringResource(editorErrorResource(it)), color = MaterialTheme.colorScheme.error) }
            } },
            confirmButton = { TextButton(onClick = controller::closeDraftManager) { Text(stringResource(R.string.lyrics_editor_done)) } })
    }
    state.discardDraft?.let { draft ->
        AlertDialog(onDismissRequest = controller::cancelDiscardDraft,
            title = { Text(stringResource(R.string.lyrics_editor_discard_named_draft, draft.title ?: stringResource(R.string.lyrics_editor_unknown_track))) },
            text = { Text(stringResource(R.string.lyrics_editor_discard_other_detail)) },
            confirmButton = { TextButton(onClick = controller::discardOtherDraft, enabled = !state.busy) { Text(stringResource(R.string.lyrics_editor_discard)) } },
            dismissButton = { TextButton(onClick = controller::cancelDiscardDraft, enabled = !state.busy) { Text(stringResource(R.string.lyrics_editor_cancel)) } })
    }
    state.confirmation?.let { confirmation ->
        AlertDialog(onDismissRequest = controller::dismissConfirmation,
            title = { Text(stringResource(when (confirmation) {
                LyricsEditorConfirmation.LEAVE -> R.string.lyrics_editor_unsaved
                LyricsEditorConfirmation.IMPORT -> R.string.lyrics_editor_replace
                LyricsEditorConfirmation.REMOVE -> R.string.lyrics_editor_remove
                LyricsEditorConfirmation.WORD_TIMINGS -> R.string.lyrics_editor_word_choice
            })) },
            text = { Text(stringResource(when (confirmation) {
                LyricsEditorConfirmation.LEAVE -> if (state.removeAfterDirty) R.string.lyrics_editor_unsaved_remove_detail else R.string.lyrics_editor_unsaved_detail
                LyricsEditorConfirmation.IMPORT -> R.string.lyrics_editor_replace_detail
                LyricsEditorConfirmation.REMOVE -> R.string.lyrics_editor_remove_detail
                LyricsEditorConfirmation.WORD_TIMINGS -> R.string.lyrics_editor_word_detail
            })) },
            confirmButton = { TextButton(onClick = {
                when (confirmation) {
                    LyricsEditorConfirmation.LEAVE -> controller.save(closeAfter = true)
                    LyricsEditorConfirmation.IMPORT -> controller.confirmImport()
                    LyricsEditorConfirmation.REMOVE -> controller.removeOverride()
                    LyricsEditorConfirmation.WORD_TIMINGS -> controller.applyRow(removeWordTimings = true)
                }
            }, enabled = enabled && (confirmation != LyricsEditorConfirmation.LEAVE || state.rowId == null)) {
                Text(stringResource(when (confirmation) {
                    LyricsEditorConfirmation.LEAVE -> R.string.lyrics_editor_save
                    LyricsEditorConfirmation.IMPORT -> R.string.lyrics_editor_replace
                    LyricsEditorConfirmation.REMOVE -> R.string.lyrics_editor_remove
                    LyricsEditorConfirmation.WORD_TIMINGS -> R.string.lyrics_editor_remove_word_timings
                }))
            } },
            dismissButton = { Row {
                if (confirmation == LyricsEditorConfirmation.LEAVE) TextButton(onClick = controller::discardChangesChoice, enabled = !state.busy) { Text(stringResource(R.string.lyrics_editor_discard)) }
                TextButton(onClick = controller::dismissConfirmation, enabled = !state.busy) { Text(stringResource(R.string.lyrics_editor_keep_editing)) }
            } })
    }
}

private fun diagnosticResource(code: LyricsDiagnosticCode): Int = when (code) {
    LyricsDiagnosticCode.INVALID_TIMESTAMP -> R.string.lyrics_editor_bad_time
    LyricsDiagnosticCode.INVALID_OFFSET -> R.string.lyrics_editor_bad_offset
    LyricsDiagnosticCode.UNKNOWN_METADATA -> R.string.lyrics_editor_unknown_metadata
    LyricsDiagnosticCode.UNSUPPORTED_LINE -> R.string.lyrics_editor_raw_retained
    LyricsDiagnosticCode.INVALID_ENCODING -> R.string.lyrics_editor_bad_encoding
    else -> R.string.lyrics_editor_span_warning
}

fun editorErrorResource(error: LyricsEditError): Int = when (error) {
    LyricsEditError.EMPTY -> R.string.lyrics_editor_empty
    LyricsEditError.TOO_LARGE -> R.string.lyrics_editor_limit
    LyricsEditError.ENCODING -> R.string.lyrics_editor_bad_encoding
    LyricsEditError.INVALID_TIMESTAMP -> R.string.lyrics_editor_bad_time
    LyricsEditError.INVALID_OFFSET -> R.string.lyrics_editor_bad_offset
    LyricsEditError.RAW_REQUIRED -> R.string.lyrics_editor_raw_required
    LyricsEditError.WORD_TIMING_CHOICE -> R.string.lyrics_editor_word_detail
    LyricsEditError.STORAGE -> R.string.lyrics_editor_storage_error
    LyricsEditError.TARGET_MISSING -> R.string.lyrics_editor_target_missing
    LyricsEditError.TARGET_AMBIGUOUS -> R.string.lyrics_editor_target_ambiguous
    LyricsEditError.REVISION_CONFLICT -> R.string.lyrics_editor_conflict
    LyricsEditError.RESTORE_CONFLICT -> R.string.lyrics_editor_restore_conflict
    LyricsEditError.IMPORT_CANCELLED -> R.string.lyrics_editor_import_cancelled
    LyricsEditError.IMPORT_READ -> R.string.lyrics_editor_import_error
    LyricsEditError.IMPORT_FORMAT -> R.string.lyrics_editor_import_format
    LyricsEditError.EXPORT_CANCELLED -> R.string.lyrics_editor_export_cancelled
    LyricsEditError.EXPORT_WRITE -> R.string.lyrics_editor_export_error
    LyricsEditError.POSITION_UNAVAILABLE -> R.string.lyrics_editor_position_error
    LyricsEditError.TRACK_MISMATCH -> R.string.lyrics_editor_other_track
}
