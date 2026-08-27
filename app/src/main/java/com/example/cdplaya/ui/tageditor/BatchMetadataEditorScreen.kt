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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.cdplaya.data.BatchArtworkValue
import com.example.cdplaya.data.BatchEditIntent
import com.example.cdplaya.data.BatchFieldState
import com.example.cdplaya.data.BatchInitialValue
import com.example.cdplaya.data.BatchMetadataEditorState
import com.example.cdplaya.data.BatchMetadataField
import com.example.cdplaya.data.BatchMetadataValue
import com.example.cdplaya.data.EditableMetadataField
import com.example.cdplaya.data.displayText
import com.example.cdplaya.data.isValidMetadataBpm

@Composable
fun BatchMetadataEditorScreen(
    state: BatchMetadataEditorState,
    onStateChanged: (BatchMetadataEditorState) -> Unit,
    onChooseArtwork: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val plan = state.plan()
    val hasInvalidBpm = state.fields.getValue(BatchMetadataField.BPM).intent
        .let { intent ->
            intent is BatchEditIntent.Set &&
                !intent.value.displayText().isValidMetadataBpm()
        }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Edit metadata", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${state.selectedTrackCount} tracks • planning only",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "Only fields you explicitly replace or clear enter the plan. No audio files are written in this screen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))

        BatchMetadataField.entries.forEach { field ->
            BatchFieldEditor(
                field = field,
                state = state.fields.getValue(field),
                supported = state.supports(field),
                onSet = { value -> onStateChanged(state.set(field, value)) },
                onClear = { onStateChanged(state.clear(field)) },
                onReset = { onStateChanged(state.reset(field)) }
            )
            Spacer(Modifier.height(14.dp))
        }

        BatchArtworkEditor(
            state = state.artwork,
            supported = state.capabilities.supports(EditableMetadataField.ARTWORK),
            onChooseArtwork = onChooseArtwork,
            onClear = { onStateChanged(state.clearArtwork()) },
            onReset = { onStateChanged(state.resetArtwork()) }
        )

        Spacer(Modifier.height(22.dp))
        Text("Planned changes", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (plan.changeCount == 0) {
            Text(
                "No fields will change.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    plan.fieldChanges.forEach { (field, change) ->
                        PlanChangeRow(
                            label = field.label,
                            oldValue = change.initial.describeInitial(),
                            newValue = change.intent.describeIntent()
                        )
                    }
                    plan.artworkChange?.let { change ->
                        PlanChangeRow(
                            label = "Artwork",
                            oldValue = change.initial.describeArtworkInitial(),
                            newValue = change.intent.describeArtworkIntent()
                        )
                    }
                }
            }
        }

        if (hasInvalidBpm) {
            Spacer(Modifier.height(8.dp))
            Text(
                "BPM must be a whole number from 1 to 999.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(20.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Done — discard plan")
        }
    }
}

@Composable
private fun BatchFieldEditor(
    field: BatchMetadataField,
    state: BatchFieldState<BatchMetadataValue>,
    supported: Boolean,
    onSet: (String) -> Unit,
    onClear: () -> Unit,
    onReset: () -> Unit
) {
    val displayValue = when (val intent = state.intent) {
        BatchEditIntent.Clear -> ""
        is BatchEditIntent.Set -> intent.value.displayText()
        BatchEditIntent.Untouched -> when (val initial = state.initial) {
            is BatchInitialValue.Common -> initial.value.displayText()
            BatchInitialValue.Mixed -> ""
        }
    }
    val isMixed = state.initial == BatchInitialValue.Mixed &&
        state.intent == BatchEditIntent.Untouched
    val isClear = state.intent == BatchEditIntent.Clear
    val hasIntent = state.intent != BatchEditIntent.Untouched
    val bpmError = field == BatchMetadataField.BPM &&
        state.intent is BatchEditIntent.Set &&
        !state.intent.value.displayText().isValidMetadataBpm()

    Column {
        OutlinedTextField(
            value = displayValue,
            onValueChange = onSet,
            label = { Text(field.label) },
            placeholder = if (isMixed) ({ Text("Multiple values") }) else null,
            enabled = supported && !isClear,
            isError = bpmError,
            supportingText = {
                Text(
                    when {
                        !supported -> "Not supported by every selected file."
                        isClear -> "Will be explicitly cleared from every selected track."
                        isMixed -> "Multiple values. Editing creates an explicit replacement."
                        hasIntent && displayValue.isEmpty() ->
                            "Will set an empty value. Use Clear field to remove the metadata key."
                        hasIntent -> "Will explicitly replace this field on every selected track."
                        field.isMultiValue -> "Separate multiple values with semicolons."
                        state.initial is BatchInitialValue.Common &&
                            state.initial.value.displayText().isEmpty() && !hasIntent ->
                            "All selected tracks currently have an empty value."
                        else -> "Unchanged unless edited."
                    }
                )
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = if (field == BatchMetadataField.BPM ||
                    field == BatchMetadataField.DISC_NUMBER ||
                    field == BatchMetadataField.DISC_TOTAL
                ) KeyboardType.Number else KeyboardType.Text
            ),
            singleLine = field != BatchMetadataField.COMMENT,
            minLines = if (field == BatchMetadataField.COMMENT) 3 else 1,
            modifier = Modifier.fillMaxWidth()
        )
        if (supported) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (!isClear) {
                    TextButton(onClick = onClear) { Text("Clear field") }
                }
                if (hasIntent) {
                    TextButton(onClick = onReset) { Text("Reset") }
                }
            }
        }
    }
}

@Composable
private fun BatchArtworkEditor(
    state: BatchFieldState<BatchArtworkValue>,
    supported: Boolean,
    onChooseArtwork: () -> Unit,
    onClear: () -> Unit,
    onReset: () -> Unit
) {
    val effective = when (val intent = state.intent) {
        is BatchEditIntent.Set -> intent.value
        BatchEditIntent.Clear -> BatchArtworkValue.None
        BatchEditIntent.Untouched -> (state.initial as? BatchInitialValue.Common)?.value
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Artwork", style = MaterialTheme.typography.titleSmall)
            when {
                !supported -> Text("Not supported by every selected file.")
                state.intent == BatchEditIntent.Clear -> Text("Will be explicitly cleared.")
                effective is BatchArtworkValue.Present -> {
                    AsyncImage(
                        model = effective.artwork.previewUri,
                        contentDescription = "Batch artwork preview",
                        modifier = Modifier.size(88.dp)
                    )
                    Text(if (state.intent is BatchEditIntent.Set) "Replacement artwork" else "Common artwork")
                }
                state.initial == BatchInitialValue.Mixed -> Text("Multiple artwork values")
                else -> Text("No artwork on selected tracks")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onChooseArtwork, enabled = supported) {
                    Text("Choose replacement")
                }
                TextButton(onClick = onClear, enabled = supported) { Text("Clear") }
                if (state.intent != BatchEditIntent.Untouched) {
                    TextButton(onClick = onReset) { Text("Reset") }
                }
            }
        }
    }
}

@Composable
private fun PlanChangeRow(label: String, oldValue: String, newValue: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(
            "$oldValue → $newValue",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun BatchInitialValue<BatchMetadataValue>.describeInitial(): String = when (this) {
    is BatchInitialValue.Common -> value.displayText().ifBlank { "Empty" }
    BatchInitialValue.Mixed -> "Multiple values"
}

private fun BatchEditIntent<BatchMetadataValue>.describeIntent(): String = when (this) {
    BatchEditIntent.Clear -> "Clear"
    is BatchEditIntent.Set -> "Set to ${value.displayText().ifBlank { "empty" }}"
    BatchEditIntent.Untouched -> "Unchanged"
}

private fun BatchInitialValue<BatchArtworkValue>.describeArtworkInitial(): String = when (this) {
    is BatchInitialValue.Common -> if (value is BatchArtworkValue.Present) "Common artwork" else "No artwork"
    BatchInitialValue.Mixed -> "Multiple artwork values"
}

private fun BatchEditIntent<BatchArtworkValue>.describeArtworkIntent(): String = when (this) {
    BatchEditIntent.Clear -> "Clear"
    is BatchEditIntent.Set -> "Replace artwork"
    BatchEditIntent.Untouched -> "Unchanged"
}
