package com.example.cdplaya.ui.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.cdplaya.controller.SmartPlaylistUiData
import com.example.cdplaya.data.SmartPlaylistDefinition
import com.example.cdplaya.data.SmartPlaylistDraft
import com.example.cdplaya.data.SmartPlaylistMatchMode
import com.example.cdplaya.data.SmartPlaylistOperator
import com.example.cdplaya.data.SmartPlaylistResolution
import com.example.cdplaya.data.SmartPlaylistRuleField
import com.example.cdplaya.data.SmartPlaylistSortDirection
import com.example.cdplaya.data.SmartPlaylistTemplate
import kotlinx.coroutines.delay

@Immutable
data class SmartPlaylistUiEnvironment(
    val onPreview: (SmartPlaylistDraft, (Result<SmartPlaylistResolution>) -> Unit) -> Unit = { _, _ -> },
    val onCreate: (
        String,
        SmartPlaylistDraft,
        Long?,
        SmartPlaylistTemplate?,
        (Result<SmartPlaylistDefinition>) -> Unit
    ) -> Unit = { _, _, _, _, _ -> },
    val onUpdate: (Long, SmartPlaylistDraft, (Result<SmartPlaylistDefinition>) -> Unit) -> Unit =
        { _, _, _ -> },
    val onLoad: (Long, (Result<SmartPlaylistUiData>) -> Unit) -> Unit = { _, _ -> },
    val onRefresh: (Long, (Result<SmartPlaylistResolution>) -> Unit) -> Unit = { _, _ -> },
    val onResolve: (Long, (Result<SmartPlaylistResolution>) -> Unit) -> Unit = { _, _ -> }
)

val LocalSmartPlaylistUi = compositionLocalOf { SmartPlaylistUiEnvironment() }

data class SmartPlaylistEditorRequest(
    val folderId: Long?,
    val playlistId: Long? = null,
    val originalName: String? = null,
    val model: SmartPlaylistEditorModel = SmartPlaylistEditorModel(),
    val template: SmartPlaylistTemplate? = null
)

@Composable
fun PlaylistCreationChooserDialog(
    onDismiss: () -> Unit,
    onManual: () -> Unit,
    onSmart: (SmartPlaylistTemplate?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) },
        title = { Text("New playlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onManual, modifier = Modifier.fillMaxWidth()) {
                    Text("Manual Playlist")
                }
                Button(onClick = { onSmart(null) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                    Text("Smart Playlist", modifier = Modifier.padding(start = 8.dp))
                }
                Text(
                    "Smart Playlist ideas",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 6.dp)
                )
                SmartPlaylistTemplate.entries.forEach { template ->
                    TextButton(
                        onClick = { onSmart(template) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(template.displayName, fontWeight = FontWeight.SemiBold)
                            Text(
                                template.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun SmartPlaylistEditor(
    request: SmartPlaylistEditorRequest,
    existingNames: List<String>,
    onDismiss: () -> Unit,
    onSaved: (name: String) -> Unit
) {
    val smartUi = LocalSmartPlaylistUi.current
    var model by remember(request) { mutableStateOf(request.model) }
    var nextRuleId by remember(request) {
        mutableLongStateOf((model.rules.maxOfOrNull(SmartPlaylistEditorRule::id) ?: 0L) + 1L)
    }
    var preview by remember(request) { mutableStateOf<SmartPlaylistResolution?>(null) }
    var previewError by remember(request) { mutableStateOf<String?>(null) }
    var saveError by remember(request) { mutableStateOf<String?>(null) }
    var saving by remember(request) { mutableStateOf(false) }
    val validation = model.validation(existingNames, request.originalName)
    val definitionReadOnly = request.template != null

    LaunchedEffect(model, validation.isValid) {
        preview = null
        previewError = null
        if (!validation.isValid) return@LaunchedEffect
        delay(350L)
        smartUi.onPreview(model.toDraft()) { result ->
            result.onSuccess { preview = it }
                .onFailure { previewError = smartEditorError(it) }
        }
    }

    BackHandler(onBack = onDismiss)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close editor")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (request.playlistId == null) "New Smart Playlist" else "Edit Smart Playlist",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        request.template?.let {
                            Text("${it.displayName} template", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    TextButton(
                        enabled = validation.isValid && !saving,
                        onClick = {
                            saving = true
                            saveError = null
                            val completion: (Result<SmartPlaylistDefinition>) -> Unit = { result ->
                                saving = false
                                result.onSuccess { onSaved(model.name.trim()) }
                                    .onFailure { saveError = smartEditorError(it) }
                            }
                            request.playlistId?.let { id ->
                                smartUi.onUpdate(id, model.toDraft(), completion)
                            } ?: smartUi.onCreate(
                                model.name.trim(),
                                model.toDraft(),
                                request.folderId,
                                request.template,
                                completion
                            )
                        }
                    ) { Text(if (saving) "Saving…" else "Save") }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        OutlinedTextField(
                            value = model.name,
                            onValueChange = { model = model.copy(name = it) },
                            label = { Text("Name") },
                            isError = validation.nameError != null,
                            supportingText = validation.nameError?.let { error -> { Text(error) } },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (model.showsMatchModeChoice) item {
                        Text("Songs must match:", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                SmartPlaylistMatchMode.ALL to "All conditions",
                                SmartPlaylistMatchMode.ANY to "Any condition"
                            ).forEach { (mode, label) ->
                                FilterChip(
                                    selected = model.matchMode == mode,
                                    onClick = { if (!definitionReadOnly) model = model.copy(matchMode = mode) },
                                    enabled = !definitionReadOnly,
                                    label = { Text(label) }
                                )
                            }
                        }
                        Text(
                            if (model.matchMode == SmartPlaylistMatchMode.ALL) {
                                "Every condition must match."
                            } else {
                                "At least one condition must match."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(model.rules, key = SmartPlaylistEditorRule::id) { rule ->
                        SmartRuleCard(
                            rule = rule,
                            error = validation.ruleErrors[rule.id],
                            readOnly = definitionReadOnly,
                            onChange = { changed ->
                                model = model.copy(rules = model.rules.map {
                                    if (it.id == changed.id) changed else it
                                })
                            },
                            onRemove = {
                                model = model.copy(rules = model.rules.filterNot { it.id == rule.id })
                            }
                        )
                    }
                    if (!definitionReadOnly) {
                        item {
                            OutlinedButton(
                                onClick = {
                                    model = model.copy(
                                        rules = model.rules + SmartPlaylistEditorRule(nextRuleId++)
                                    )
                                }
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                                Text("Add rule", modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                    }
                    item {
                        Text("Results", style = MaterialTheme.typography.titleMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SelectionMenu(
                                label = "Sort by",
                                selected = model.sortField,
                                options = smartSortOptions,
                                enabled = !definitionReadOnly,
                                onSelected = { model = model.copy(sortField = it) },
                                modifier = Modifier.weight(1f)
                            )
                            SelectionMenu(
                                label = "Direction",
                                selected = model.sortDirection,
                                options = listOf(
                                    SmartPlaylistSortDirection.ASCENDING to "Ascending",
                                    SmartPlaylistSortDirection.DESCENDING to "Descending"
                                ),
                                enabled = !definitionReadOnly,
                                onSelected = { model = model.copy(sortDirection = it) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        OutlinedTextField(
                            value = model.resultLimit,
                            onValueChange = { if (!definitionReadOnly) model = model.copy(resultLimit = it) },
                            enabled = !definitionReadOnly,
                            label = { Text("Limit (optional)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                    }
                    item {
                        PreviewCard(
                            preview = preview,
                            error = previewError,
                            validationError = validation.generalError,
                            usesRecentHistory = model.rules.any { rule ->
                                rule.field == SmartPlaylistRuleField.RECENT_PLAY_COUNT ||
                                    rule.field == SmartPlaylistRuleField.LAST_PLAYED
                            }
                        )
                    }
                    saveError?.let { error ->
                        item { Text(error, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmartRuleCard(
    rule: SmartPlaylistEditorRule,
    error: String?,
    readOnly: Boolean,
    onChange: (SmartPlaylistEditorRule) -> Unit,
    onRemove: () -> Unit
) {
    val field = smartRuleFieldOptions.firstOrNull { it.storage == rule.field }
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SelectionMenu(
                    label = "Field",
                    selected = rule.field,
                    options = smartRuleFieldOptions.map { it.storage to it.label },
                    enabled = !readOnly,
                    onSelected = { selected ->
                        val selectedField = smartRuleFieldOptions.first { it.storage == selected }
                        onChange(
                            rule.copy(
                                field = selected,
                                operator = selectedField.operators.first().storage,
                                value = "",
                                secondValue = ""
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
                if (!readOnly) {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove rule")
                    }
                }
            }
            if (field == null) {
                Text("Unsupported field: ${rule.field}", color = MaterialTheme.colorScheme.error)
            } else {
                SelectionMenu(
                    label = "Condition",
                    selected = rule.operator,
                    options = field.operators.map { it.storage to it.label },
                    enabled = !readOnly,
                    onSelected = { onChange(rule.copy(operator = it)) },
                    modifier = Modifier.fillMaxWidth()
                )
                RuleValueInput(rule, field.valueKind, readOnly, onChange)
                Text(
                    naturalRuleText(rule),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun RuleValueInput(
    rule: SmartPlaylistEditorRule,
    kind: SmartRuleValueKind,
    readOnly: Boolean,
    onChange: (SmartPlaylistEditorRule) -> Unit
) {
    if (rule.operator == SmartPlaylistOperator.UNRATED ||
        rule.operator == SmartPlaylistOperator.NEVER || kind == SmartRuleValueKind.NONE
    ) return
    if (kind == SmartRuleValueKind.RATING) {
        SelectionMenu(
            label = "Stars",
            selected = rule.value,
            options = (1..5).map { it.toString() to "★".repeat(it) },
            enabled = !readOnly,
            onSelected = { onChange(rule.copy(value = it)) },
            modifier = Modifier.fillMaxWidth()
        )
        return
    }
    val keyboardType = when (kind) {
        SmartRuleValueKind.TEXT -> KeyboardType.Text
        SmartRuleValueKind.DURATION_MINUTES -> KeyboardType.Decimal
        else -> KeyboardType.Number
    }
    OutlinedTextField(
        value = rule.value,
        onValueChange = { if (!readOnly) onChange(rule.copy(value = it)) },
        enabled = !readOnly,
        label = {
            Text(when (kind) {
                SmartRuleValueKind.DURATION_MINUTES -> "Minutes"
                SmartRuleValueKind.RELATIVE_DAYS -> "Days"
                else -> "Value"
            })
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType
        ),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    if (kind == SmartRuleValueKind.DURATION_MINUTES &&
        rule.operator == SmartPlaylistOperator.ABOUT
    ) {
        Text(
            "Uses the nearest-minute bucket; for example, about 4 minutes matches 3:30–4:29.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (rule.operator == SmartPlaylistOperator.BETWEEN) {
        OutlinedTextField(
            value = rule.secondValue,
            onValueChange = { if (!readOnly) onChange(rule.copy(secondValue = it)) },
            enabled = !readOnly,
            label = {
                Text(if (kind == SmartRuleValueKind.DURATION_MINUTES) "And (minutes)" else "And")
            },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
    if (kind == SmartRuleValueKind.RECENT_COUNT) {
        OutlinedTextField(
            value = rule.windowDays,
            onValueChange = { if (!readOnly) onChange(rule.copy(windowDays = it)) },
            enabled = !readOnly,
            label = { Text("Within the last (days)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SelectionMenu(
    label: String,
    selected: String,
    options: List<Pair<String, String>>,
    enabled: Boolean,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second ?: "Unsupported ($selected)"
    Box(modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(label, style = MaterialTheme.typography.labelSmall)
                Text(selectedLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (storage, optionLabel) ->
                DropdownMenuItem(
                    text = { Text(optionLabel) },
                    onClick = {
                        onSelected(storage)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun PreviewCard(
    preview: SmartPlaylistResolution?,
    error: String?,
    validationError: String?,
    usesRecentHistory: Boolean
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Preview", style = MaterialTheme.typography.titleMedium)
            when {
                validationError != null -> Text(validationError, color = MaterialTheme.colorScheme.error)
                error != null -> Text(error, color = MaterialTheme.colorScheme.error)
                preview == null -> Text("Checking matching songs…")
                preview.songs.isEmpty() -> Text(
                    if (usesRecentHistory) {
                        "No songs currently match. Recent windows use dated qualified plays; undated legacy totals do not qualify."
                    } else {
                        "No songs currently match these rules."
                    }
                )
                else -> {
                    Text("${preview.count} matching song${if (preview.count == 1) "" else "s"}")
                    preview.songs.take(5).forEach { song ->
                        Text("${song.title} — ${song.artist}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (preview.count > 5) Text("and ${preview.count - 5} more")
                }
            }
        }
    }
}

internal fun naturalRuleText(rule: SmartPlaylistEditorRule): String {
    val field = smartRuleFieldOptions.firstOrNull { it.storage == rule.field }?.label ?: rule.field
    val operator = smartRuleFieldOptions.firstOrNull { it.storage == rule.field }
        ?.operators?.firstOrNull { it.storage == rule.operator }?.label ?: rule.operator
    return when {
        rule.field == SmartPlaylistRuleField.RATING && rule.operator != SmartPlaylistOperator.UNRATED ->
            "$field | $operator | ${"★".repeat(rule.value.toIntOrNull() ?: 0)}"
        rule.operator == SmartPlaylistOperator.UNRATED || rule.operator == SmartPlaylistOperator.NEVER ->
            "$field | $operator"
        rule.field == LISTENING_HISTORY_EDITOR_FIELD ->
            if (rule.operator == SmartPlaylistOperator.WITHIN_LAST_DAYS) {
                "$field | played within last ${rule.value} days"
            } else {
                "$field | not played for ${rule.value} days"
            }
        rule.field == SmartPlaylistRuleField.DATE_ADDED ->
            if (rule.operator == SmartPlaylistOperator.WITHIN_LAST_DAYS) {
                "$field | within last ${rule.value} days"
            } else {
                "$field | more than ${rule.value} days ago"
            }
        rule.field == SmartPlaylistRuleField.RECENT_PLAY_COUNT ->
            "$field | $operator | ${rule.value} in ${rule.windowDays} days"
        rule.field == SmartPlaylistRuleField.DURATION ->
            if (rule.operator == SmartPlaylistOperator.BETWEEN) {
                "$field | between | ${rule.value} and ${rule.secondValue} minutes"
            } else {
                "$field | $operator | ${rule.value} minute${if (rule.value == "1") "" else "s"}"
            }
        rule.operator == SmartPlaylistOperator.BETWEEN ->
            "$field | between | ${rule.value} and ${rule.secondValue}"
        else -> "$field | $operator | ${rule.value}"
    }
}

private fun smartEditorError(error: Throwable): String = when (error) {
    is IllegalArgumentException -> error.message ?: "This Smart Playlist definition is invalid."
    else -> error.message ?: "Unable to update this Smart Playlist."
}
