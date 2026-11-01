package io.github.rsgarrido.sazanami.ui.lyrics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.lyrics.*
import io.github.rsgarrido.sazanami.ui.readableContentWidth
import kotlinx.coroutines.flow.first

/** Presentation only. Documents, selection identity, mutations and playback remain controller-owned. */
@Composable
internal fun LyricsEditorWorkspace(
    controller: LyricsEditorController,
    state: LyricsEditorUiState,
    onImport: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val source = state.source
    val enabled = !state.busy && !state.parsing && state.recovery == null && state.baseline != null
    var more by rememberSaveable(state.sessionId) { mutableStateOf(false) }
    var info by rememberSaveable(state.sessionId) { mutableStateOf(false) }
    var timing by rememberSaveable(state.sessionId) { mutableStateOf(false) }
    var directInfo by rememberSaveable(state.sessionId) { mutableStateOf(false) }
    var diagnostics by rememberSaveable(state.sessionId) { mutableStateOf(false) }
    var statusDetails by rememberSaveable(state.sessionId) { mutableStateOf(false) }
    var deleteId by rememberSaveable(state.sessionId) { mutableStateOf<String?>(null) }
    var selectionStart by rememberSaveable(state.sessionId) { mutableIntStateOf(0) }
    var selectionEnd by rememberSaveable(state.sessionId) { mutableIntStateOf(0) }
    val warnings = remember(source) { source?.diagnostics?.map { it.code }?.distinct().orEmpty() }
    val outOfOrder = remember(source) {
        source?.lines?.flatMap { it.timestamps }?.zipWithNext()?.any { (a, b) -> a.timestampMs > b.timestampMs } == true
    }
    Column(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()
        .readableContentWidth().padding(horizontal = 8.dp).testTag("lyrics_editor")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(controller::requestClose, enabled = !state.busy, modifier = Modifier.testTag("lyrics_editor_cancel")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.lyrics_editor_cancel))
            }
            Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                Text(stringResource(R.string.lyrics_editor_title), style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(state.song?.title.orEmpty(), style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Button(onClick = { controller.save() },
                enabled = enabled && source != null && state.rowId == null && (state.dirty || !state.hasOverride),
                modifier = Modifier.testTag("lyrics_editor_save"), contentPadding = PaddingValues(horizontal = 12.dp)) {
                Text(stringResource(R.string.lyrics_editor_save_short))
            }
            Box {
                IconButton(onClick = { more = true }, modifier = Modifier.testTag("lyrics_editor_more")) {
                    Icon(Icons.Default.MoreVert, stringResource(R.string.lyrics_editor_more))
                }
                DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.lyrics_editor_import)) },
                        onClick = { more = false; onImport() }, enabled = enabled)
                    DropdownMenuItem(text = { Text(stringResource(R.string.lyrics_editor_export)) },
                        onClick = { more = false; onExport() }, enabled = enabled && source != null && state.rowId == null)
                    DropdownMenuItem(text = { Text(stringResource(R.string.lyrics_editor_manage_drafts)) },
                        onClick = { more = false; controller.manageDrafts() },
                        enabled = !state.busy && !state.parsing && state.recovery == null)
                    if (state.hasOverride) DropdownMenuItem(text = { Text(stringResource(R.string.lyrics_editor_remove)) },
                        onClick = { more = false; controller.requestRemove() }, enabled = enabled)
                    if (warnings.isNotEmpty() || outOfOrder) DropdownMenuItem(
                        text = { Text(stringResource(R.string.lyrics_editor_source_details)) },
                        onClick = { more = false; diagnostics = true })
                    if (state.mode == LyricsEditorMode.STRUCTURED) DropdownMenuItem(
                        text = { Text(stringResource(R.string.lyrics_editor_preview)) },
                        onClick = {
                            more = false
                            controller.preview()
                            if (controller.state.value.error == null) diagnostics = true
                        }, enabled = enabled && state.canStamp && !state.isDirectPlayback)
                    if (state.mode != LyricsEditorMode.STRUCTURED) DropdownMenuItem(
                        text = { Text(stringResource(R.string.lyrics_editor_undo)) },
                        onClick = { more = false; controller.undo() }, enabled = enabled && state.canUndo)
                    DropdownMenuItem(text = { Text(stringResource(R.string.lyrics_editor_save_info)) },
                        onClick = { more = false; info = true })
                }
            }
        }
        EditorModes(state.mode, enabled && state.rowId == null, controller::setMode)
        if (state.busy || state.parsing) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { error ->
            TextButton(onClick = { statusDetails = true }, modifier = Modifier.fillMaxWidth().testTag("lyrics_editor_error")) {
                Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.error)
                Text(stringResource(editorErrorResource(error, state.restriction)), Modifier.weight(1f).padding(start = 6.dp),
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Default.ChevronRight, stringResource(R.string.lyrics_editor_details))
            }
        }
        if (state.saved && !state.dirty) Text(stringResource(R.string.lyrics_editor_saved),
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("lyrics_editor_saved"))
        if (state.exported) TextButton(onClick = { statusDetails = true }, modifier = Modifier.testTag("lyrics_editor_export_status")) {
            Text(stringResource(R.string.lyrics_editor_export_status), style = MaterialTheme.typography.bodySmall)
        }
        if (source != null && state.mode == LyricsEditorMode.STRUCTURED) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { timing = true }, enabled = enabled,
                    modifier = Modifier.weight(1f).testTag("lyrics_editor_timing")) {
                    Icon(Icons.Default.Tune, null)
                    Text(stringResource(R.string.lyrics_editor_timing_summary, signedLyricsOffset(source.metadata.offsetMs)),
                        Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelLarge)
                }
                IconButton(controller::insert, enabled = enabled, modifier = Modifier.testTag("lyrics_editor_insert")) {
                    Icon(Icons.Default.Add, stringResource(R.string.lyrics_editor_insert))
                }
                if (warnings.isNotEmpty() || outOfOrder) IconButton(onClick = { diagnostics = true }) {
                    Icon(Icons.Default.Info, stringResource(R.string.lyrics_editor_source_details))
                }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
                // A short landscape pane has room for the dock beside the list. Capture constraints
                // here, before entering Row/Column DSL scopes.
                val besideList = maxWidth >= 640.dp * fontScale && maxHeight < 320.dp * fontScale
                val dockWidth = 300.dp * fontScale
                if (besideList) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EditorRows(source, state.selectedLine, enabled, controller,
                        onDelete = { deleteId = it }, modifier = Modifier.weight(1f).fillMaxHeight())
                    Column(Modifier.width(dockWidth).fillMaxHeight()) {
                        SynchronizationDock(state, enabled, controller, onDirectInfo = { directInfo = true })
                    }
                } else Column(Modifier.fillMaxSize()) {
                    EditorRows(source, state.selectedLine, enabled, controller,
                        onDelete = { deleteId = it }, modifier = Modifier.weight(1f))
                    SynchronizationDock(state, enabled, controller, onDirectInfo = { directInfo = true })
                }
            }
        } else if (source != null) {
            EditorText(state, controller::setText, TextRange(selectionStart, selectionEnd),
                onSelection = {
                    // The departing field loses focus and emits a collapsed selection on removal.
                    // Read the current mode, not this composition's captured state, so that cleanup
                    // cannot overwrite the range retained for the next text-mode entry.
                    val current = controller.state.value
                    if (current.sessionId == state.sessionId && current.mode != LyricsEditorMode.STRUCTURED) {
                        selectionStart = it.start
                        selectionEnd = it.end
                    }
                },
                modifier = Modifier.weight(1f).fillMaxWidth().padding(bottom = 8.dp))
        } else Spacer(Modifier.weight(1f))
    }

    if (info) EditorInformation(R.string.lyrics_editor_save_info, stringResource(R.string.lyrics_editor_local_notice)) { info = false }
    if (directInfo) EditorInformation(R.string.lyrics_editor_direct_unavailable, stringResource(R.string.lyrics_editor_direct_detail)) { directInfo = false }
    if (statusDetails) {
        val resources = LocalResources.current
        val message = buildList {
            state.error?.let { add(resources.getString(editorErrorResource(it, state.restriction))) }
            if (state.exported) {
                add(resources.getString(R.string.lyrics_editor_exported))
                state.exportedName?.let { add(resources.getString(R.string.lyrics_editor_export_name, it)) }
            }
        }.joinToString("\n\n")
        EditorInformation(R.string.lyrics_editor_details, message) { statusDetails = false }
    }
    if (diagnostics) {
        val resources = LocalResources.current
        val projection = remember(source) { source?.let { LrcParser().project(it) } }
        val message = buildList {
            if (outOfOrder) add(resources.getString(R.string.lyrics_editor_time_order))
            warnings.forEach { add(resources.getString(diagnosticResource(it))) }
            state.previewPosition?.let { time ->
                val lines = (projection as? LyricsDocument.Synced)?.let { ActiveLyricResolver.resolve(it.cues, time)?.lines }.orEmpty()
                add(resources.getString(R.string.lyrics_editor_preview_at, LyricsDocumentEditor.formatTime(time), lines.joinToString(" / ")))
            }
        }.joinToString("\n\n")
        EditorInformation(R.string.lyrics_editor_source_details, message) { diagnostics = false }
    }
    if (timing && source != null) TimingAdjustment(state, enabled, controller) { timing = false }
    deleteId?.let { id ->
        val index = source?.lines?.indexOfFirst { it.id == id } ?: -1
        if (index >= 0) AlertDialog(onDismissRequest = { deleteId = null },
            title = { Text(stringResource(R.string.lyrics_editor_delete_line, index + 1)) },
            text = { Text(stringResource(R.string.lyrics_editor_delete_detail)) },
            confirmButton = { TextButton(onClick = { controller.removeLine(id); deleteId = null }, enabled = enabled) {
                Text(stringResource(R.string.lyrics_editor_delete_confirm))
            } },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text(stringResource(R.string.lyrics_editor_cancel)) } })
    }
}

@Composable
private fun EditorModes(mode: LyricsEditorMode, enabled: Boolean, onMode: (LyricsEditorMode) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(IntrinsicSize.Min).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        LyricsEditorMode.entries.forEach { entry ->
            val selected = mode == entry
            Surface(shape = MaterialTheme.shapes.small,
                border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp).testTag("lyrics_editor_mode_${entry.name}")
                    .selectable(selected, enabled = enabled, role = Role.Tab, onClick = { onMode(entry) })) {
                Row(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center) {
                    if (selected) Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                    Text(stringResource(when (entry) {
                        LyricsEditorMode.PLAIN -> R.string.lyrics_editor_plain
                        LyricsEditorMode.STRUCTURED -> R.string.lyrics_editor_sync
                        LyricsEditorMode.RAW -> R.string.lyrics_editor_raw
                    }), Modifier.weight(1f, fill = false), textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun EditorRows(source: LyricsSourceDocument, selectedIndex: Int, enabled: Boolean,
    controller: LyricsEditorController, onDelete: (String) -> Unit, modifier: Modifier) {
    val list = rememberLazyListState()
    val currentSource by rememberUpdatedState(source)
    val selectedId = source.lines.getOrNull(selectedIndex)?.id
    // Stable identity + relocation, not playback position or every document revision. Manual scrolling
    // never triggers this effect. Re-entry/recreation restores the list and reveals selection if needed.
    LaunchedEffect(selectedId, selectedIndex) {
        if (selectedId == null) return@LaunchedEffect
        val layout = snapshotFlow { list.layoutInfo }.first {
            it.totalItemsCount == currentSource.lines.size && it.visibleItemsInfo.isNotEmpty() &&
                it.visibleItemsInfo.all { item -> currentSource.lines.getOrNull(item.index)?.id == item.key }
        }
        val target = selectedLyricsScrollTarget(selectedIndex, selectedId,
            layout.visibleItemsInfo.map { LyricsVisibleRow(it.key, it.offset, it.size) },
            layout.viewportStartOffset, layout.viewportEndOffset)
        if (target != null) list.animateScrollToItem(target, -((layout.viewportEndOffset - layout.viewportStartOffset) / 4))
    }
    LazyColumn(modifier.fillMaxWidth().testTag("lyrics_editor_rows"), state = list,
        contentPadding = PaddingValues(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        itemsIndexed(source.lines, key = { _, row -> row.id }) { index, row ->
            var menu by remember(row.id) { mutableStateOf(false) }
            val selected = row.id == selectedId
            val editable = row.kind == LyricsSourceLineKind.TEXT || row.kind == LyricsSourceLineKind.TIMED
            Surface(shape = MaterialTheme.shapes.small,
                color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                modifier = Modifier.fillMaxWidth().testTag("lyrics_editor_row_${row.id}")
                    .semantics { this.selected = selected }.clickable(enabled = enabled) { controller.select(index) }) {
                Row(Modifier.padding(start = 8.dp).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (selected) Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                            Text(stringResource(R.string.lyrics_editor_line, index + 1), style = MaterialTheme.typography.labelSmall)
                            Text(rowTimingLabel(row),
                                Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace)
                        }
                        Text(row.displayedText.ifEmpty { if (editable) stringResource(R.string.lyrics_editor_blank) else row.rawText },
                            style = MaterialTheme.typography.bodyLarge)
                        if (!editable) Text(stringResource(R.string.lyrics_editor_raw_retained), style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { controller.editRow(row.id) }, enabled = enabled && editable) {
                        Icon(Icons.Default.Edit, stringResource(R.string.lyrics_editor_edit_line, index + 1))
                    }
                    Box {
                        IconButton(onClick = { menu = true }, enabled = enabled) {
                            Icon(Icons.Default.MoreVert, stringResource(R.string.lyrics_editor_row_actions, index + 1))
                        }
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.lyrics_editor_up, index + 1)) },
                                onClick = { menu = false; controller.moveLine(row.id, -1) }, enabled = enabled && index > 0)
                            DropdownMenuItem(text = { Text(stringResource(R.string.lyrics_editor_down, index + 1)) },
                                onClick = { menu = false; controller.moveLine(row.id, 1) }, enabled = enabled && index < source.lines.lastIndex)
                            DropdownMenuItem(text = { Text(stringResource(R.string.lyrics_editor_delete_line, index + 1)) },
                                onClick = { menu = false; onDelete(row.id) }, enabled = enabled)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SynchronizationDock(state: LyricsEditorUiState, enabled: Boolean, controller: LyricsEditorController, onDirectInfo: () -> Unit) {
    val row = state.source?.lines?.getOrNull(state.selectedLine)
    val editable = row?.let { it.kind == LyricsSourceLineKind.TEXT || it.kind == LyricsSourceLineKind.TIMED } == true
    val matchingTrack = state.target?.trackKey == state.activeTrackKey
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth().testTag("lyrics_editor_dock")) {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
            Text(stringResource(R.string.lyrics_editor_selected_line, state.selectedLine + 1,
                rowTimingLabel(row)),
                style = MaterialTheme.typography.labelMedium, modifier = Modifier.testTag("lyrics_editor_selected"))
            if (state.isDirectPlayback) TextButton(onClick = onDirectInfo, modifier = Modifier.testTag("lyrics_editor_direct")) {
                Icon(Icons.Default.Info, null)
                Text(stringResource(R.string.lyrics_editor_direct_unavailable), Modifier.padding(start = 4.dp), style = MaterialTheme.typography.bodySmall)
            } else if (!matchingTrack) Text(stringResource(R.string.lyrics_editor_other_track),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = controller::stamp, enabled = enabled && state.canStamp && editable && !state.isDirectPlayback,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("lyrics_editor_stamp"),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                    Icon(Icons.Default.TouchApp, null, Modifier.size(18.dp))
                    Text(stringResource(if (row?.timestamps.isNullOrEmpty()) R.string.lyrics_editor_stamp_short else R.string.lyrics_editor_restamp), Modifier.padding(start = 4.dp))
                }
                OutlinedButton(onClick = controller.playPause, enabled = enabled && matchingTrack,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("lyrics_editor_play_pause"),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                    Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                    Text(stringResource(if (state.isPlaying) R.string.lyrics_editor_pause else R.string.lyrics_editor_resume), Modifier.padding(start = 4.dp))
                }
            }
            Row(Modifier.fillMaxWidth()) {
                DockAction(R.string.lyrics_editor_replay_short, R.string.lyrics_editor_replay, Icons.Default.Replay,
                    enabled && state.canStamp && !state.isDirectPlayback && !row?.timestamps.isNullOrEmpty(), controller::replaySelected,
                    Modifier.weight(1f).testTag("lyrics_editor_replay"))
                DockAction(R.string.lyrics_editor_skip_short, R.string.lyrics_editor_skip, Icons.Default.SkipNext,
                    enabled, controller::skip, Modifier.weight(1f).testTag("lyrics_editor_skip"))
                DockAction(R.string.lyrics_editor_undo, R.string.lyrics_editor_undo, Icons.AutoMirrored.Filled.Undo,
                    enabled && state.canUndo, controller::undo, Modifier.weight(1f).testTag("lyrics_editor_undo"))
            }
        }
    }
}

@Composable
private fun rowTimingLabel(row: LyricsSourceLine?): String {
    val times = row?.timestamps.orEmpty()
    val first = times.firstOrNull()?.raw ?: return stringResource(R.string.lyrics_editor_untimed)
    return if (times.size > 1) stringResource(R.string.lyrics_editor_more_times, first, times.size - 1) else first
}

@Composable
private fun DockAction(label: Int, description: Int, icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val accessibilityLabel = stringResource(description)
    TextButton(onClick, enabled = enabled, modifier = modifier.heightIn(min = 48.dp).semantics { contentDescription = accessibilityLabel },
        contentPadding = PaddingValues(horizontal = 4.dp)) {
        Icon(icon, null, Modifier.size(18.dp))
        Text(stringResource(label), Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun EditorText(state: LyricsEditorUiState, onText: (String) -> Unit, selection: TextRange,
    onSelection: (TextRange) -> Unit, modifier: Modifier) {
    // Only selection is saveable presentation state. Controller text always wins, including recovery,
    // undo/import and failed raw validation. IME composition is retained for ordinary input updates.
    val clamped = TextRange(selection.start.coerceIn(0, state.text.length), selection.end.coerceIn(0, state.text.length))
    var field by remember(state.sessionId) { mutableStateOf(TextFieldValue(state.text, clamped)) }
    val value = if (field.text == state.text) field else TextFieldValue(state.text,
        clamped)
    SideEffect {
        field = value
        if (selection != value.selection) onSelection(value.selection)
    }
    val raw = state.mode == LyricsEditorMode.RAW
    OutlinedTextField(value, onValueChange = {
        field = it; onSelection(it.selection)
        if (it.text != state.text) onText(it.text)
    }, enabled = !state.busy, label = { Text(stringResource(if (raw) R.string.lyrics_editor_raw_text else R.string.lyrics_editor_plain_text)) },
        textStyle = if (raw) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodyLarge,
        modifier = modifier.testTag("lyrics_editor_text"))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimingAdjustment(state: LyricsEditorUiState, enabled: Boolean, controller: LyricsEditorController, onClose: () -> Unit) {
    val offset = state.source?.metadata?.offsetMs ?: 0L
    var text by rememberSaveable(state.sessionId) { mutableStateOf(offset.toString()) }
    var observedOffset by rememberSaveable(state.sessionId) { mutableLongStateOf(offset) }
    // Button adjustments refresh the manual field; opening or dismissing this dialog never edits lyrics.
    // Retain an unapplied manual entry across recreation when the document offset has not changed.
    LaunchedEffect(offset) {
        if (observedOffset != offset) { observedOffset = offset; text = offset.toString() }
    }
    AlertDialog(onDismissRequest = onClose, title = { Text(stringResource(R.string.lyrics_editor_offset_title)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()).testTag("lyrics_editor_timing_tools")) {
            Text(stringResource(R.string.lyrics_editor_offset_help), style = MaterialTheme.typography.bodySmall)
            FlowRow {
                TextButton(onClick = { controller.shiftOffset(-100) }, enabled = enabled) { Text(stringResource(R.string.lyrics_editor_earlier)) }
                TextButton(onClick = { controller.shiftOffset(100) }, enabled = enabled) { Text(stringResource(R.string.lyrics_editor_later)) }
            }
            OutlinedTextField(text, { text = it }, enabled = enabled, singleLine = true,
                label = { Text(stringResource(R.string.lyrics_editor_offset, state.source?.metadata?.offsetMs ?: 0L)) },
                isError = state.error == LyricsEditError.INVALID_OFFSET,
                supportingText = { if (state.error == LyricsEditError.INVALID_OFFSET) Text(stringResource(R.string.lyrics_editor_bad_offset)) })
            FlowRow {
                TextButton(onClick = { controller.setOffset(0) }, enabled = enabled) { Text(stringResource(R.string.lyrics_editor_zero)) }
                TextButton(onClick = { controller.setOffset(state.baseline?.originalOffsetMs ?: 0L) }, enabled = enabled) { Text(stringResource(R.string.lyrics_editor_original)) }
            }
        } },
        confirmButton = { TextButton(onClick = {
            val value = text.trim().toLongOrNull()
            if (value == null) controller.report(LyricsEditError.INVALID_OFFSET) else { controller.setOffset(value); onClose() }
        }, enabled = enabled) { Text(stringResource(R.string.lyrics_editor_apply)) } },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.lyrics_editor_done)) } })
}

@Composable
private fun EditorInformation(title: Int, message: String, onClose: () -> Unit) {
    AlertDialog(onDismissRequest = onClose, title = { Text(stringResource(title)) },
        text = { Text(message, Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.lyrics_editor_done)) } })
}
