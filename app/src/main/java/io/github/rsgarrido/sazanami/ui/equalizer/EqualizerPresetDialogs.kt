package io.github.rsgarrido.sazanami.ui.equalizer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.player.equalizer.GraphicEqualizerPresets
import io.github.rsgarrido.sazanami.player.equalizer.UserEqualizerPreset
import io.github.rsgarrido.sazanami.player.equalizer.normalizePresetName

@Composable
internal fun EqualizerPresetSelectorDialog(
    userPresets: List<UserEqualizerPreset>,
    onDismiss: () -> Unit,
    onApplyBuiltIn: (Int) -> Unit,
    onApplyUser: (String) -> Unit,
    onSaveAs: () -> Unit,
    onRename: (UserEqualizerPreset) -> Unit,
    onDelete: (UserEqualizerPreset) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose preset") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(
                    rememberScrollState()
                )
            ) {
                Text("Built-in presets")
                builtInEqualizerPresets.forEachIndexed {
                        index,
                        preset ->
                    ListItem(
                        headlineContent = { Text(preset.name) },
                        supportingContent = {
                            Text(
                                preset.bandGainsDb.joinToString(
                                    separator = "  "
                                ) { gain ->
                                    formatEqualizerDb(gain)
                                }
                            )
                        },
                        modifier = Modifier.clickable {
                            onApplyBuiltIn(index)
                            onDismiss()
                        }
                    )
                }
                if (userPresets.isNotEmpty()) {
                    Text(
                        text = "User presets",
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                userPresets.forEach { preset ->
                    ListItem(
                        headlineContent = { Text(preset.name) },
                        trailingContent = {
                            Row {
                                IconButton(
                                    onClick = { onRename(preset) }
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription =
                                            "Rename ${preset.name}"
                                    )
                                }
                                IconButton(
                                    onClick = { onDelete(preset) }
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription =
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
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onSaveAs()
                }
            ) {
                Text("Save as preset")
            }
        }
    )
}

@Composable
internal fun EqualizerPresetNameDialog(
    title: String,
    initialName: String,
    userPresets: List<UserEqualizerPreset>,
    excludingPresetId: String? = null,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember(initialName) {
        mutableStateOf(initialName)
    }
    val validationError = presetNameValidationError(
        name = name,
        userPresets = userPresets,
        excludingPresetId = excludingPresetId
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { updated -> name = updated },
                label = { Text("Preset name") },
                supportingText = validationError?.let { message ->
                    { Text(message) }
                },
                isError = validationError != null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        confirmButton = {
            TextButton(
                enabled = validationError == null,
                onClick = {
                    onConfirm(normalizePresetName(name))
                }
            ) {
                Text(confirmText)
            }
        }
    )
}

@Composable
internal fun ConfirmEqualizerActionDialog(
    title: String,
    message: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText)
            }
        }
    )
}

internal fun presetNameValidationError(
    name: String,
    userPresets: List<UserEqualizerPreset>,
    excludingPresetId: String? = null
): String? {
    val normalized = name.trim()
    if (normalized.isBlank()) return "Name cannot be blank."
    if (normalized.length > 40) {
        return "Name must be 40 characters or fewer."
    }
    if (
        normalized.lowercase() in
        GraphicEqualizerPresets.builtInNamesLowercase
    ) {
        return "That name is used by a built-in preset."
    }
    if (
        userPresets.any { preset ->
            preset.id != excludingPresetId &&
                preset.name.equals(normalized, ignoreCase = true)
        }
    ) {
        return "A preset with that name already exists."
    }
    return null
}
