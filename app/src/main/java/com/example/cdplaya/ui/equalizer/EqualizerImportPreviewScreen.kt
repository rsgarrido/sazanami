package com.example.cdplaya.ui.equalizer

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.cdplaya.player.equalizer.interchange.EqualizerProfileDiagnostic
import com.example.cdplaya.player.equalizer.interchange.EqualizerProfileFormat
import com.example.cdplaya.player.equalizer.interchange.ImportedFilterDeclaration
import com.example.cdplaya.player.equalizer.interchange.ImportedFilterStatus
import com.example.cdplaya.player.equalizer.parametric.ParametricEqualizerPresets
import com.example.cdplaya.player.equalizer.parametric.ParametricFilter
import com.example.cdplaya.player.equalizer.parametric.ParametricFilterFactory
import com.example.cdplaya.player.equalizer.parametric.gainDbOrNull
import com.example.cdplaya.player.equalizer.parametric.qOrNull
import com.example.cdplaya.player.equalizer.parametric.slopeOrNull
import java.util.Locale

@Composable
internal fun EqualizerImportPreviewScreen(
    state: EqualizerScreenState,
    actions: EqualizerUiActions,
    modifier: Modifier = Modifier
) {
    val preview = state.importPreview ?: return
    var editingLine by remember { mutableStateOf<Int?>(null) }
    var replaceConfirmationVisible by remember {
        mutableStateOf(false)
    }
    var supportedOnlyConfirmationVisible by remember {
        mutableStateOf(false)
    }
    val nameIsValid = remember(
        preview.proposedName,
        state.parametricUserPresets
    ) {
        runCatching {
            ParametricEqualizerPresets.requireNameAvailable(
                preview.proposedName,
                state.parametricUserPresets
            )
        }.isSuccess
    }
    val currentParametricIsFlat =
        state.durablePreferences.parametricState.let {
            it.preampDb == 0.0 &&
                it.automaticHeadroomEnabled &&
                it.filters.isEmpty()
        }

    BackHandler(onBack = actions.onDismissImportPreview)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag("equalizer_import_preview")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            IconButton(onClick = actions.onDismissImportPreview) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Cancel EQ import"
                )
            }
            Text(
                "Import Parametric EQ",
                style = MaterialTheme.typography.titleLarge
            )
        }

        ImportSummary(preview)

        OutlinedTextField(
            value = preview.proposedName,
            onValueChange = { name ->
                actions.onUpdateImportPreview {
                    it.copy(proposedName = name.take(40))
                }
            },
            label = { Text("Preset name") },
            supportingText = {
                Text(
                    if (nameIsValid) {
                        "Used only when saving a preset."
                    } else {
                        "Enter a unique name (1–40 characters)."
                    }
                )
            },
            isError = !nameIsValid,
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .semantics {
                    contentDescription =
                        "Imported preset name, ${preview.proposedName}"
                }
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text("Automatic headroom")
                Text(
                    if (preview.automaticHeadroomEnabled) {
                        "On — recommended"
                    } else {
                        "Off — use imported preamp exactly"
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(
                checked = preview.automaticHeadroomEnabled,
                onCheckedChange = { enabled ->
                    actions.onUpdateImportPreview {
                        it.copy(
                            automaticHeadroomEnabled = enabled
                        )
                    }
                },
                modifier = Modifier.semantics {
                    contentDescription =
                        "Imported automatic headroom"
                }
            )
        }

        if (state.runtimeState.sampleRateHz != null) {
            Text(
                "Preview sample rate",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(
                    horizontal = 16.dp,
                    vertical = 8.dp
                )
            ) {
                FilterChip(
                    selected = preview.previewAtCurrentTrackRate,
                    onClick = {
                        actions.onUpdateImportPreview {
                            it.copy(previewAtCurrentTrackRate = true)
                        }
                    },
                    label = { Text("Current track") }
                )
                FilterChip(
                    selected = !preview.previewAtCurrentTrackRate,
                    onClick = {
                        actions.onUpdateImportPreview {
                            it.copy(previewAtCurrentTrackRate = false)
                        }
                    },
                    label = { Text("48 kHz") }
                )
            }
        }

        EqualizerResponseGraph(
            analysis = state.importAnalysis,
            modifier = Modifier.padding(16.dp)
        )
        ImportAnalysisSummary(state)

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            OutlinedButton(
                onClick = {
                    actions.onUpdateImportPreview {
                        it.selectFirstTen()
                    }
                }
            ) {
                Text("Select first 10")
            }
            TextButton(
                onClick = {
                    actions.onUpdateImportPreview {
                        it.clearSelection()
                    }
                }
            ) {
                Text("Clear selection")
            }
        }
        Text(
            "Sazanami can apply at most 10 filters. Selection preserves " +
                "source order; files with more than 10 start unselected.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(
                horizontal = 16.dp,
                vertical = 8.dp
            )
        )

        preview.parseResult.declarations.forEach { declaration ->
            ImportedFilterRow(
                declaration = declaration,
                selected = declaration.sourceLineNumber in
                    preview.selectedSourceLines,
                selectionLimitReached =
                    preview.selectedFilters.size >= 10,
                onSelectionChanged = {
                    actions.onUpdateImportPreview {
                        it.toggleSelection(
                            declaration.sourceLineNumber
                        )
                    }
                },
                onEdit = {
                    editingLine = declaration.sourceLineNumber
                }
            )
        }

        ImportSafetyConfirmations(
            preview = preview,
            onUpdate = actions.onUpdateImportPreview,
            onRequestSupportedOnly = {
                supportedOnlyConfirmationVisible = true
            }
        )
        if (state.importInProgress) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .semantics {
                        contentDescription =
                            "Saving imported EQ profile"
                    }
            )
        }
        state.importMessage?.let { message ->
            Text(
                message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        Text(
            "Import destination",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(
                start = 16.dp,
                top = 16.dp
            )
        )
        Text(
            "Replace switches to Parametric and retains EQ enabled, " +
                "limiter, and Graphic settings. Save alone does not " +
                "change playback.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Button(
            onClick = {
                if (currentParametricIsFlat) {
                    actions.onReplaceWithImportedProfile()
                } else {
                    replaceConfirmationVisible = true
                }
            },
            enabled = preview.canApply && !state.importInProgress,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .semantics {
                    contentDescription =
                        "Replace current Parametric EQ"
                }
        ) {
            Text("Replace current Parametric EQ")
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            OutlinedButton(
                onClick = {
                    actions.onSaveImportedProfile(false)
                },
                enabled = preview.canApply &&
                    nameIsValid &&
                    !state.importInProgress,
                modifier = Modifier.weight(1f)
            ) {
                Text("Save as preset")
            }
            OutlinedButton(
                onClick = {
                    actions.onSaveImportedProfile(true)
                },
                enabled = preview.canApply &&
                    nameIsValid &&
                    !state.importInProgress,
                modifier = Modifier.weight(1f)
            ) {
                Text("Save and apply")
            }
        }
        TextButton(
            onClick = actions.onDismissImportPreview,
            modifier = Modifier.padding(8.dp)
        ) {
            Text("Cancel")
        }
        Spacer(Modifier.height(24.dp))
    }

    editingLine?.let { lineNumber ->
        val declaration = preview.parseResult.declarations.first {
            it.sourceLineNumber == lineNumber
        }
        val initial = declaration.mappedFilter
            ?: ParametricFilterFactory.default()
        ParametricFilterEditorDialog(
            original = initial,
            unavailable = false,
            onPreview = {},
            onCancel = { editingLine = null },
            onApply = { filter ->
                actions.onUpdateImportPreview {
                    it.replaceDeclaration(lineNumber, filter)
                }
                editingLine = null
            }
        )
    }
    if (supportedOnlyConfirmationVisible) {
        AlertDialog(
            onDismissRequest = {
                supportedOnlyConfirmationVisible = false
            },
            title = { Text("Import supported filters only?") },
            text = {
                Text(
                    "Sazanami will not reproduce the unsupported commands. " +
                        "The result may not match the original file, and " +
                        "scoped preamp commands will not be imported."
                )
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        supportedOnlyConfirmationVisible = false
                    }
                ) {
                    Text("Cancel")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        actions.onUpdateImportPreview {
                            it.copy(
                                supportedOnlyOverrideConfirmed = true
                            )
                        }
                        supportedOnlyConfirmationVisible = false
                    }
                ) {
                    Text("Import supported only")
                }
            }
        )
    }
    if (replaceConfirmationVisible) {
        AlertDialog(
            onDismissRequest = {
                replaceConfirmationVisible = false
            },
            title = { Text("Replace current Parametric EQ?") },
            text = {
                Text(
                    "This replaces the current Parametric curve. Saved " +
                        "presets and the Graphic curve remain available."
                )
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        replaceConfirmationVisible = false
                    }
                ) {
                    Text("Cancel")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        replaceConfirmationVisible = false
                        actions.onReplaceWithImportedProfile()
                    }
                ) {
                    Text("Replace")
                }
            }
        )
    }
}

@Composable
private fun ImportSummary(
    preview: EqualizerImportPreviewState
) {
    val result = preview.parseResult
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Source: ${result.sourceName ?: "Clipboard"}",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                formatName(result.detectedFormat),
                modifier = Modifier.semantics {
                    contentDescription =
                        "Detected format, " +
                            formatName(result.detectedFormat)
                }
            )
            Text(
                "Preamp: " +
                    formatEqualizerDb(result.preampDb ?: 0.0)
            )
            Text(
                "Filters: ${result.declarations.size} · " +
                    "Selected: ${preview.selectedFilters.size}",
                modifier = Modifier.semantics {
                    contentDescription =
                        "Selected filter count, " +
                            "${preview.selectedFilters.size} of " +
                            result.declarations.size
                }
            )
            Text(
                "Warnings: ${result.warningCount} · " +
                    "Errors: ${result.errorCount}",
                color = if (result.errorCount > 0) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.semantics {
                    contentDescription =
                        "Warning count, ${result.warningCount}. " +
                            "Error count, ${result.errorCount}."
                }
            )
        }
    }
}

@Composable
private fun ImportAnalysisSummary(state: EqualizerScreenState) {
    val analysis = state.importAnalysis
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Preview analysis",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                "Predicted maximum: " +
                    formatEqualizerDb(analysis.predictedMaximumDb)
            )
            Text(
                "Automatic attenuation: " +
                    formatEqualizerDb(
                        analysis.automaticHeadroom.attenuationDb,
                        includePlus = false
                    )
            )
            Text(
                "Effective preamp: " +
                    formatEqualizerDb(
                        analysis.automaticHeadroom.effectivePreampDb
                    )
            )
            Text("Sample rate: ${analysis.sampleRateHz} Hz")
            if (analysis.ignoredFilterIndices.isNotEmpty()) {
                Text(
                    "${analysis.ignoredFilterIndices.size} selected " +
                        "filter(s) unavailable at this sample rate.",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun ImportedFilterRow(
    declaration: ImportedFilterDeclaration,
    selected: Boolean,
    selectionLimitReached: Boolean,
    onSelectionChanged: () -> Unit,
    onEdit: () -> Unit
) {
    val filter = declaration.mappedFilter
    val status = declaration.status.name.lowercase()
        .replaceFirstChar { it.uppercase() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics {
                contentDescription =
                    "Source line ${declaration.sourceLineNumber}, " +
                        "$status, " +
                        if (selected) "selected" else "not selected"
            }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onSelectionChanged() },
                enabled = filter != null &&
                    (!selectionLimitReached || selected),
                modifier = Modifier.semantics {
                    contentDescription =
                        "Select source line " +
                            declaration.sourceLineNumber
                }
            )
            Column(Modifier.weight(1f)) {
                Text(
                    "Line ${declaration.sourceLineNumber} · $status",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    filter?.summary()
                        ?: declaration.originalText.take(160),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TextButton(onClick = onEdit) {
                Text(
                    if (filter == null) {
                        "Replace with supported filter"
                    } else {
                        "Edit"
                    }
                )
            }
        }
        declaration.diagnostics.forEach { diagnostic ->
            DiagnosticText(diagnostic)
        }
        HorizontalDivider(Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun DiagnosticText(
    diagnostic: EqualizerProfileDiagnostic
) {
    Text(
        "${diagnostic.severity.name}: ${diagnostic.message}",
        color = if (
            diagnostic.severity.name in setOf("ERROR", "BLOCKING")
        ) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun ImportSafetyConfirmations(
    preview: EqualizerImportPreviewState,
    onUpdate:
        ((EqualizerImportPreviewState) ->
            EqualizerImportPreviewState) -> Unit,
    onRequestSupportedOnly: () -> Unit
) {
    if (preview.hasOverrideableSemanticBlocks) {
        SafetyCheckbox(
            checked = preview.supportedOnlyOverrideConfirmed,
            text = "Import supported standalone filters only. " +
                "The result may not match the original configuration.",
            description = "Supported-only import confirmation",
            onCheckedChange = { checked ->
                if (checked) {
                    onRequestSupportedOnly()
                } else {
                    onUpdate {
                        it.copy(
                            supportedOnlyOverrideConfirmed = false
                        )
                    }
                }
            }
        )
    }
    if (preview.hasUnsupportedDeclarations) {
        SafetyCheckbox(
            checked = preview.unsupportedExclusionConfirmed,
            text = "I explicitly exclude unsupported filter " +
                "declarations.",
            description = "Unsupported filters excluded",
            onCheckedChange = { checked ->
                onUpdate {
                    it.copy(
                        unsupportedExclusionConfirmed = checked
                    )
                }
            }
        )
    }
    if (preview.hasUnrecognizedText) {
        SafetyCheckbox(
            checked = preview.unrecognizedTextConfirmed,
            text = "I reviewed the unrecognized non-comment text.",
            description = "Unrecognized text reviewed",
            onCheckedChange = { checked ->
                onUpdate {
                    it.copy(unrecognizedTextConfirmed = checked)
                }
            }
        )
    }
    if (preview.selectedFilters.isEmpty()) {
        SafetyCheckbox(
            checked = preview.flatImportConfirmed,
            text = "I intend to import a Flat Parametric profile " +
                "with zero selected filters.",
            description = "Flat import confirmed",
            onCheckedChange = { checked ->
                onUpdate {
                    it.copy(flatImportConfirmed = checked)
                }
            }
        )
    }
    preview.parseResult.diagnostics.forEach { diagnostic ->
        DiagnosticText(diagnostic)
    }
}

@Composable
private fun SafetyCheckbox(
    checked: Boolean,
    text: String,
    description: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics {
                contentDescription = description
            }
        )
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

private fun ParametricFilter.summary(): String {
    val parameters = buildList {
        add(if (enabled) "ON" else "OFF")
        add(type.name.replace('_', ' '))
        add(formatFrequency(frequencyHz))
        gainDbOrNull?.let { add(formatEqualizerDb(it)) }
        qOrNull?.let {
            add(String.format(Locale.ROOT, "Q %.2f", it))
        }
        slopeOrNull?.let {
            add(String.format(Locale.ROOT, "S %.2f", it))
        }
    }
    return parameters.joinToString(" · ")
}

private fun formatFrequency(value: Double): String =
    String.format(Locale.ROOT, "%.1f Hz", value)

private fun formatName(format: EqualizerProfileFormat): String =
    when (format) {
        EqualizerProfileFormat.AUTOEQ_PARAMETRIC_TEXT ->
            "AutoEq-style Parametric EQ text"
        EqualizerProfileFormat.EQUALIZER_APO_SUBSET ->
            "Equalizer APO-compatible subset"
        EqualizerProfileFormat.CDPLAYA_PARAMETRIC_PRESET_JSON ->
            "Native Sazanami Parametric preset"
    }
