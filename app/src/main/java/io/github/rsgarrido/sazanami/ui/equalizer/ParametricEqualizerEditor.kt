package io.github.rsgarrido.sazanami.ui.equalizer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.player.equalizer.parametric.MAX_PARAMETRIC_FILTER_COUNT
import io.github.rsgarrido.sazanami.player.equalizer.parametric.MAX_PARAMETRIC_FREQUENCY_HZ
import io.github.rsgarrido.sazanami.player.equalizer.parametric.MAX_PARAMETRIC_GAIN_DB
import io.github.rsgarrido.sazanami.player.equalizer.parametric.MAX_PARAMETRIC_Q
import io.github.rsgarrido.sazanami.player.equalizer.parametric.MAX_PARAMETRIC_SHELF_SLOPE
import io.github.rsgarrido.sazanami.player.equalizer.parametric.MIN_PARAMETRIC_FREQUENCY_HZ
import io.github.rsgarrido.sazanami.player.equalizer.parametric.MIN_PARAMETRIC_GAIN_DB
import io.github.rsgarrido.sazanami.player.equalizer.parametric.MIN_PARAMETRIC_Q
import io.github.rsgarrido.sazanami.player.equalizer.parametric.MIN_PARAMETRIC_SHELF_SLOPE
import io.github.rsgarrido.sazanami.player.equalizer.parametric.ParametricEqualizerPreset
import io.github.rsgarrido.sazanami.player.equalizer.parametric.ParametricFilter
import io.github.rsgarrido.sazanami.player.equalizer.parametric.ParametricFilterFactory
import io.github.rsgarrido.sazanami.player.equalizer.parametric.ParametricFilterType
import io.github.rsgarrido.sazanami.player.equalizer.parametric.changeType
import io.github.rsgarrido.sazanami.player.equalizer.parametric.gainDbOrNull
import io.github.rsgarrido.sazanami.player.equalizer.parametric.qOrNull
import io.github.rsgarrido.sazanami.player.equalizer.parametric.slopeOrNull
import io.github.rsgarrido.sazanami.player.equalizer.parametric.withEnabled
import io.github.rsgarrido.sazanami.player.equalizer.parametric.withFrequencyHz
import io.github.rsgarrido.sazanami.player.equalizer.parametric.withGainDb
import io.github.rsgarrido.sazanami.player.equalizer.parametric.withQ
import io.github.rsgarrido.sazanami.player.equalizer.parametric.withShelfSlope
import java.util.Locale

@Composable
internal fun ParametricEqualizerEditor(
    state: EqualizerScreenState,
    actions: EqualizerUiActions
) {
    var presetDialogVisible by remember { mutableStateOf(false) }
    var saveNameDialogVisible by remember { mutableStateOf(false) }
    var renamePreset by remember {
        mutableStateOf<ParametricEqualizerPreset?>(null)
    }
    var deletePreset by remember {
        mutableStateOf<ParametricEqualizerPreset?>(null)
    }
    var editOriginal by remember {
        mutableStateOf<ParametricFilter?>(null)
    }
    var deleteFilter by remember {
        mutableStateOf<ParametricFilter?>(null)
    }
    val parametric = state.editablePreferences.parametricState

    ListItem(
        headlineContent = { Text("Parametric preset") },
        supportingContent = { Text(state.presetLabel) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { presetDialogVisible = true }
            .semantics {
                contentDescription =
                    "Parametric preset, ${state.presetLabel}"
            }
    )
    TextButton(
        onClick = { presetDialogVisible = true },
        modifier = Modifier.padding(horizontal = 12.dp)
    ) {
        Text("Choose or manage presets")
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Button(
            onClick = actions.onAddParametricFilter,
            enabled =
                parametric.filters.size < MAX_PARAMETRIC_FILTER_COUNT,
            modifier = Modifier.semantics {
                contentDescription = "Add parametric filter"
            }
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text("Add Filter")
        }
        Text(
            "${parametric.filters.size}/$MAX_PARAMETRIC_FILTER_COUNT filters",
            style = MaterialTheme.typography.bodyMedium
        )
    }
    if (parametric.filters.size == MAX_PARAMETRIC_FILTER_COUNT) {
        Text(
            "Maximum of ten filters reached.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
    if (parametric.filters.isEmpty()) {
        Text(
            "Flat: no parametric filters. Add a filter to begin.",
            modifier = Modifier.padding(16.dp)
        )
    }
    parametric.filters.forEachIndexed { index, filter ->
        ParametricFilterCard(
            index = index,
            filter = filter,
            selected = filter.id == state.selectedParametricFilterId,
            unavailable = index in state.analysis.ignoredFilterIndices,
            canMoveUp = index > 0,
            canMoveDown = index < parametric.filters.lastIndex,
            onSelect = { actions.onSelectParametricFilter(filter.id) },
            onToggle = { enabled ->
                actions.onCommitParametricFilter(
                    filter.withEnabled(enabled)
                )
            },
            onMoveUp = {
                actions.onMoveParametricFilter(filter.id, index - 1)
            },
            onMoveDown = {
                actions.onMoveParametricFilter(filter.id, index + 1)
            },
            onEdit = {
                actions.onSelectParametricFilter(filter.id)
                editOriginal = filter
            },
            onDelete = { deleteFilter = filter }
        )
    }

    if (presetDialogVisible) {
        ParametricPresetSelectorDialog(
            userPresets = state.parametricUserPresets,
            onDismiss = { presetDialogVisible = false },
            onApplyFlat = actions.onApplyParametricFlatPreset,
            onApplyUser = actions.onApplyParametricUserPreset,
            onSaveAs = { saveNameDialogVisible = true },
            onRename = { renamePreset = it },
            onDelete = { deletePreset = it }
        )
    }
    if (saveNameDialogVisible) {
        ParametricPresetNameDialog(
            title = "Save parametric preset",
            initialName = "",
            presets = state.parametricUserPresets,
            confirmText = "Save",
            onDismiss = { saveNameDialogVisible = false },
            onConfirm = {
                actions.onSaveParametricUserPreset(it)
                saveNameDialogVisible = false
            }
        )
    }
    renamePreset?.let { preset ->
        ParametricPresetNameDialog(
            title = "Rename parametric preset",
            initialName = preset.name,
            presets = state.parametricUserPresets,
            excludingPresetId = preset.id,
            confirmText = "Rename",
            onDismiss = { renamePreset = null },
            onConfirm = {
                actions.onRenameParametricUserPreset(preset.id, it)
                renamePreset = null
            }
        )
    }
    deletePreset?.let { preset ->
        ConfirmEqualizerActionDialog(
            title = "Delete ${preset.name}?",
            message = "The active parametric curve will not change.",
            confirmText = "Delete",
            onDismiss = { deletePreset = null },
            onConfirm = {
                actions.onDeleteParametricUserPreset(preset.id)
                deletePreset = null
            }
        )
    }
    deleteFilter?.let { filter ->
        ConfirmEqualizerActionDialog(
            title = "Delete ${filter.type.displayName} filter?",
            message = "This filter will be removed from the active curve.",
            confirmText = "Delete",
            onDismiss = { deleteFilter = null },
            onConfirm = {
                actions.onDeleteParametricFilter(filter.id)
                deleteFilter = null
            }
        )
    }
    editOriginal?.let { original ->
        ParametricFilterEditorDialog(
            original = original,
            unavailable =
                parametric.filters.indexOfFirst { it.id == original.id } in
                    state.analysis.ignoredFilterIndices,
            onPreview = actions.onPreviewParametricFilter,
            onCancel = {
                actions.onCancelParametricFilterPreview(original)
                editOriginal = null
            },
            onApply = {
                actions.onCommitParametricFilter(it)
                editOriginal = null
            }
        )
    }
}

@Composable
private fun ParametricFilterCard(
    index: Int,
    filter: ParametricFilter,
    selected: Boolean,
    unavailable: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onSelect: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val description = filterAccessibilityDescription(
        index, filter, unavailable
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onSelect)
            .semantics {
                this.selected = selected
                contentDescription = description
            }
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${index + 1}. ${filter.type.displayName}" +
                            if (selected) " · Selected" else "",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(filterParameterSummary(filter))
                    if (unavailable) {
                        Text(
                            "Unavailable for current source",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else if (!filter.enabled) {
                        Text(
                            "Bypassed",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Switch(
                    checked = filter.enabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.semantics {
                        contentDescription =
                            "Filter ${index + 1} enabled"
                    }
                )
            }
            Row(horizontalArrangement = Arrangement.End) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = canMoveUp
                ) {
                    Icon(
                        Icons.Default.ArrowUpward,
                        contentDescription = "Move filter ${index + 1} up"
                    )
                }
                IconButton(
                    onClick = onMoveDown,
                    enabled = canMoveDown
                ) {
                    Icon(
                        Icons.Default.ArrowDownward,
                        contentDescription = "Move filter ${index + 1} down"
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit filter ${index + 1}"
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete filter ${index + 1}"
                    )
                }
            }
        }
    }
}

@Composable
internal fun ParametricFilterEditorDialog(
    original: ParametricFilter,
    unavailable: Boolean,
    onPreview: (ParametricFilter) -> Unit,
    onCancel: () -> Unit,
    onApply: (ParametricFilter) -> Unit
) {
    var draft by remember(original) { mutableStateOf(original) }
    var typeMenuVisible by remember { mutableStateOf(false) }
    var frequencyText by remember(original) {
        mutableStateOf(formatEditable(original.frequencyHz, 1))
    }
    var gainText by remember(original) {
        mutableStateOf(
            original.gainDbOrNull?.let { formatEditable(it, 1) } ?: ""
        )
    }
    var qText by remember(original) {
        mutableStateOf(
            original.qOrNull?.let { formatEditable(it, 2) } ?: ""
        )
    }
    var slopeText by remember(original) {
        mutableStateOf(
            original.slopeOrNull?.let { formatEditable(it, 2) } ?: ""
        )
    }

    fun update(candidate: ParametricFilter) {
        draft = candidate
        onPreview(candidate)
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Edit ${draft.type.displayName} filter") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enabled", Modifier.weight(1f))
                    Checkbox(
                        checked = draft.enabled,
                        onCheckedChange = {
                            update(draft.withEnabled(it))
                        },
                        modifier = Modifier.semantics {
                            contentDescription = "Filter enabled"
                        }
                    )
                }
                OutlinedButton(
                    onClick = { typeMenuVisible = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription =
                                "Filter type, ${draft.type.displayName}"
                        }
                ) {
                    Text("Type: ${draft.type.displayName}")
                }
                DropdownMenu(
                    expanded = typeMenuVisible,
                    onDismissRequest = { typeMenuVisible = false }
                ) {
                    ParametricFilterType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.displayName) },
                            onClick = {
                                val changed = draft.changeType(type)
                                update(changed)
                                frequencyText =
                                    formatEditable(changed.frequencyHz, 1)
                                gainText = changed.gainDbOrNull
                                    ?.let { formatEditable(it, 1) } ?: ""
                                qText = changed.qOrNull
                                    ?.let { formatEditable(it, 2) } ?: ""
                                slopeText = changed.slopeOrNull
                                    ?.let { formatEditable(it, 2) } ?: ""
                                typeMenuVisible = false
                            }
                        )
                    }
                }
                ParametricNumberField(
                    label = "Frequency (Hz)",
                    value = frequencyText,
                    range = "20.0 to 20,000.0 Hz",
                    step = frequencyStep(draft.frequencyHz),
                    minimum = MIN_PARAMETRIC_FREQUENCY_HZ,
                    maximum = MAX_PARAMETRIC_FREQUENCY_HZ,
                    decimals = 1,
                    onValueChanged = { text, value ->
                        frequencyText = text
                        value?.let {
                            update(draft.withFrequencyHz(it))
                        }
                    }
                )
                draft.gainDbOrNull?.let {
                    ParametricNumberField(
                        label = "Gain (dB)",
                        value = gainText,
                        range = "−15.0 to +15.0 dB",
                        step = 0.1,
                        minimum = MIN_PARAMETRIC_GAIN_DB,
                        maximum = MAX_PARAMETRIC_GAIN_DB,
                        decimals = 1,
                        onValueChanged = { text, value ->
                            gainText = text
                            value?.let {
                                update(draft.withGainDb(it))
                            }
                        }
                    )
                }
                draft.qOrNull?.let {
                    ParametricNumberField(
                        label = "Q",
                        value = qText,
                        range = "0.10 to 20.00",
                        step = 0.01,
                        minimum = MIN_PARAMETRIC_Q,
                        maximum = MAX_PARAMETRIC_Q,
                        decimals = 2,
                        onValueChanged = { text, value ->
                            qText = text
                            value?.let { update(draft.withQ(it)) }
                        }
                    )
                }
                draft.slopeOrNull?.let {
                    ParametricNumberField(
                        label = "Shelf slope S",
                        value = slopeText,
                        range = "0.10 to 1.00",
                        step = 0.01,
                        minimum = MIN_PARAMETRIC_SHELF_SLOPE,
                        maximum = MAX_PARAMETRIC_SHELF_SLOPE,
                        decimals = 2,
                        onValueChanged = { text, value ->
                            slopeText = text
                            value?.let {
                                update(draft.withShelfSlope(it))
                            }
                        }
                    )
                }
                if (unavailable) {
                    Text(
                        "This frequency is unavailable for the current " +
                            "sample rate. It remains saved and will become " +
                            "active for a compatible source.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                TextButton(
                    onClick = {
                        val reset = ParametricFilterFactory.default(
                            draft.type,
                            draft.id
                        ).withEnabled(draft.enabled)
                        update(reset)
                        frequencyText = formatEditable(reset.frequencyHz, 1)
                        gainText = reset.gainDbOrNull
                            ?.let { formatEditable(it, 1) } ?: ""
                        qText = reset.qOrNull
                            ?.let { formatEditable(it, 2) } ?: ""
                        slopeText = reset.slopeOrNull
                            ?.let { formatEditable(it, 2) } ?: ""
                    }
                ) {
                    Text("Reset parameter values")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel") }
        },
        confirmButton = {
            TextButton(
                onClick = { onApply(draft) },
                enabled =
                    parseInRange(
                        frequencyText,
                        MIN_PARAMETRIC_FREQUENCY_HZ,
                        MAX_PARAMETRIC_FREQUENCY_HZ
                    ) != null &&
                        (draft.gainDbOrNull == null ||
                            parseInRange(
                                gainText,
                                MIN_PARAMETRIC_GAIN_DB,
                                MAX_PARAMETRIC_GAIN_DB
                            ) != null) &&
                        (draft.qOrNull == null ||
                            parseInRange(
                                qText,
                                MIN_PARAMETRIC_Q,
                                MAX_PARAMETRIC_Q
                            ) != null) &&
                        (draft.slopeOrNull == null ||
                            parseInRange(
                                slopeText,
                                MIN_PARAMETRIC_SHELF_SLOPE,
                                MAX_PARAMETRIC_SHELF_SLOPE
                            ) != null)
            ) {
                Text("Apply")
            }
        }
    )
}

@Composable
private fun ParametricNumberField(
    label: String,
    value: String,
    range: String,
    step: Double,
    minimum: Double,
    maximum: Double,
    decimals: Int,
    onValueChanged: (String, Double?) -> Unit
) {
    val parsed = parseInRange(value, minimum, maximum)
    Column(Modifier.padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = {
                    val next = ((parsed ?: minimum) - step)
                        .coerceIn(minimum, maximum)
                    onValueChanged(
                        formatEditable(next, decimals),
                        next
                    )
                },
                modifier = Modifier.semantics {
                    contentDescription = "Decrease $label"
                }
            ) { Text("−") }
            OutlinedTextField(
                value = value,
                onValueChange = { text ->
                    onValueChanged(
                        text,
                        parseInRange(text, minimum, maximum)
                    )
                },
                label = { Text(label) },
                supportingText = {
                    Text(if (parsed == null) "Enter $range" else range)
                },
                isError = parsed == null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal
                ),
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = {
                    val next = ((parsed ?: minimum) + step)
                        .coerceIn(minimum, maximum)
                    onValueChanged(
                        formatEditable(next, decimals),
                        next
                    )
                },
                modifier = Modifier.semantics {
                    contentDescription = "Increase $label"
                }
            ) { Text("+") }
        }
    }
}

@Composable
private fun ParametricPresetSelectorDialog(
    userPresets: List<ParametricEqualizerPreset>,
    onDismiss: () -> Unit,
    onApplyFlat: () -> Unit,
    onApplyUser: (String) -> Unit,
    onSaveAs: () -> Unit,
    onRename: (ParametricEqualizerPreset) -> Unit,
    onDelete: (ParametricEqualizerPreset) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose parametric preset") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ListItem(
                    headlineContent = { Text("Flat") },
                    supportingContent = { Text("0 dB · No filters") },
                    modifier = Modifier.clickable {
                        onApplyFlat()
                        onDismiss()
                    }
                )
                userPresets.forEach { preset ->
                    ListItem(
                        headlineContent = { Text(preset.name) },
                        supportingContent = {
                            Text("${preset.filters.size} filters")
                        },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { onRename(preset) }) {
                                    Icon(
                                        Icons.Default.Edit,
                                        "Rename ${preset.name}"
                                    )
                                }
                                IconButton(onClick = { onDelete(preset) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        "Delete ${preset.name}"
                                    )
                                }
                            }
                        },
                        modifier = Modifier.clickable {
                            onApplyUser(preset.id)
                            onDismiss()
                        }
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onSaveAs()
                }
            ) { Text("Save as preset") }
        }
    )
}

@Composable
private fun ParametricPresetNameDialog(
    title: String,
    initialName: String,
    presets: List<ParametricEqualizerPreset>,
    excludingPresetId: String? = null,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    val normalized = name.trim()
    val error = when {
        normalized.isBlank() -> "Name cannot be blank."
        normalized.length > 40 -> "Name must be 40 characters or fewer."
        normalized.equals("Flat", ignoreCase = true) ->
            "Flat is a built-in preset."
        presets.any {
            it.id != excludingPresetId &&
                it.name.equals(normalized, ignoreCase = true)
        } -> "A preset with that name already exists."
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Preset name") },
                supportingText = error?.let { message ->
                    { Text(message) }
                },
                isError = error != null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(normalized) },
                enabled = error == null
            ) { Text(confirmText) }
        }
    )
}

internal val ParametricFilterType.displayName: String
    get() = when (this) {
        ParametricFilterType.PEAKING -> "Peaking"
        ParametricFilterType.LOW_SHELF -> "Low shelf"
        ParametricFilterType.HIGH_SHELF -> "High shelf"
        ParametricFilterType.LOW_PASS -> "Low pass"
        ParametricFilterType.HIGH_PASS -> "High pass"
        ParametricFilterType.NOTCH -> "Notch"
        ParametricFilterType.BAND_PASS -> "Band pass"
    }

internal fun filterParameterSummary(filter: ParametricFilter): String =
    buildString {
        append(formatEqualizerFrequency(filter.frequencyHz))
        filter.gainDbOrNull?.let {
            append(" · ")
            append(formatEqualizerDb(it))
        }
        filter.qOrNull?.let {
            append(" · Q ")
            append(formatEditable(it, 2))
        }
        filter.slopeOrNull?.let {
            append(" · S ")
            append(formatEditable(it, 2))
        }
    }

private fun filterAccessibilityDescription(
    index: Int,
    filter: ParametricFilter,
    unavailable: Boolean
): String = buildString {
    append("Filter ${index + 1}, ")
    append(filter.type.displayName.lowercase())
    append(if (filter.enabled) ", enabled" else ", bypassed")
    append(", ${formatEqualizerFrequency(filter.frequencyHz)}")
    filter.gainDbOrNull?.let {
        append(", ${formatEqualizerDb(it)}")
    }
    filter.qOrNull?.let {
        append(", Q ${formatEditable(it, 2)}")
    }
    filter.slopeOrNull?.let {
        append(", shelf slope S ${formatEditable(it, 2)}")
    }
    if (unavailable) append(", unavailable for current source")
}

private fun frequencyStep(frequencyHz: Double): Double = when {
    frequencyHz < 100.0 -> 1.0
    frequencyHz < 1_000.0 -> 10.0
    frequencyHz < 10_000.0 -> 100.0
    else -> 1_000.0
}

private fun parseInRange(
    text: String,
    minimum: Double,
    maximum: Double
): Double? = text.trim()
    .replace(',', '.')
    .toDoubleOrNull()
    ?.takeIf { it.isFinite() && it in minimum..maximum }

private fun formatEditable(value: Double, decimals: Int): String =
    String.format(Locale.ROOT, "%.${decimals}f", value)
