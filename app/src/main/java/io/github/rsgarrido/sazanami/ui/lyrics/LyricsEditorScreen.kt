package io.github.rsgarrido.sazanami.ui.lyrics

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
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
    val source = state.source
    val enabled = !state.busy && !state.parsing && state.recovery == null && state.baseline != null
    LyricsEditorWorkspace(controller, state,
        onImport = {
            pickerSession = state.sessionId
            try { importPicker.launch(arrayOf("text/*", "application/octet-stream")) }
            catch (_: Exception) { controller.report(LyricsEditError.IMPORT_READ) }
        },
        onExport = {
            scope.launch {
                try {
                    val bytes = controller.exportBytes()
                    if (controller.state.value.sessionId != state.sessionId) return@launch
                    pickerSession = state.sessionId
                    controller.holdExport(bytes)
                    val format = source?.let { LyricsExportFormat.forSource(it) } ?: LyricsExportFormat.PLAIN
                    val stem = state.song?.displayName?.substringBeforeLast('.')?.take(80)
                        ?.replace(Regex("[^\\p{L}\\p{N} _.-]"), "_")?.ifBlank { null }
                        ?: resources.getString(R.string.lyrics_editor_filename)
                    exportPicker.launch(LyricsExportRequest("$stem.${format.extension}", format))
                } catch (cancelled: CancellationException) { throw cancelled }
                  catch (failure: LyricsEditException) { controller.report(failure.reason) }
                  catch (_: Exception) { controller.completeExport { throw LyricsEditException(LyricsEditError.EXPORT_WRITE) } }
            }
        })
    if (state.rowId != null && !state.rowCloseConfirmation && state.confirmation != LyricsEditorConfirmation.WORD_TIMINGS) AlertDialog(
        onDismissRequest = controller::requestRowClose,
        title = { Text(stringResource(R.string.lyrics_editor_edit_row)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            OutlinedTextField(state.rowText, controller::setRowText, enabled = !state.busy, label = { Text(stringResource(R.string.lyrics_editor_line_text)) })
            OutlinedTextField(state.rowTimes, controller::setRowTimes, enabled = !state.busy, label = { Text(stringResource(R.string.lyrics_editor_timestamps)) },
                supportingText = { Text(stringResource(R.string.lyrics_editor_time_help)) })
            state.error?.let { Text(stringResource(editorErrorResource(it, state.restriction)), color = MaterialTheme.colorScheme.error) }
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
                state.error?.let { Text(stringResource(editorErrorResource(it, state.restriction)), color = MaterialTheme.colorScheme.error) }
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

internal fun diagnosticResource(code: LyricsDiagnosticCode): Int = when (code) {
    LyricsDiagnosticCode.INVALID_TIMESTAMP -> R.string.lyrics_editor_bad_time
    LyricsDiagnosticCode.INVALID_OFFSET -> R.string.lyrics_editor_bad_offset
    LyricsDiagnosticCode.UNKNOWN_METADATA -> R.string.lyrics_editor_unknown_metadata
    LyricsDiagnosticCode.UNSUPPORTED_LINE -> R.string.lyrics_editor_raw_retained
    LyricsDiagnosticCode.INVALID_ENCODING -> R.string.lyrics_editor_bad_encoding
    else -> R.string.lyrics_editor_span_warning
}

fun editorErrorResource(error: LyricsEditError, restriction: LyricsEditingRestriction? = null): Int = when (error) {
    LyricsEditError.EMPTY -> R.string.lyrics_editor_empty
    LyricsEditError.TOO_LARGE -> R.string.lyrics_editor_limit
    LyricsEditError.ENCODING -> R.string.lyrics_editor_bad_encoding
    LyricsEditError.INVALID_TIMESTAMP -> R.string.lyrics_editor_bad_time
    LyricsEditError.INVALID_OFFSET -> R.string.lyrics_editor_bad_offset
    LyricsEditError.RAW_REQUIRED -> when (restriction) {
        LyricsEditingRestriction.PLAIN_TIMING -> R.string.lyrics_editor_plain_timing
        LyricsEditingRestriction.PLAIN_SOURCE -> R.string.lyrics_editor_plain_source
        LyricsEditingRestriction.SOURCE_ROW -> R.string.lyrics_editor_raw_retained
        LyricsEditingRestriction.REPEATED_TIMES -> R.string.lyrics_editor_repeated_times
        LyricsEditingRestriction.MULTILINE_ROW -> R.string.lyrics_editor_multiline_row
        null -> R.string.lyrics_editor_raw_required
    }
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
