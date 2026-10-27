package io.github.rsgarrido.sazanami.ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.BuildConfig
import io.github.rsgarrido.sazanami.ui.settings.SettingsRow
import io.github.rsgarrido.sazanami.ui.settings.SettingsSection
import io.github.rsgarrido.sazanami.ui.settings.SettingsSectionSpacer

/**
 * Activity-saved development nomination, deliberately separate from DataStore, backup, and the
 * primary theme. Release builds cannot enable it, even if restored state contains a true value.
 */
@Stable
internal class PocketAdvanceDevelopmentState internal constructor(
    val isAvailable: Boolean,
    private val requested: MutableState<Boolean>
) {
    val enabled: Boolean get() = isAvailable && requested.value

    fun setEnabled(enabled: Boolean) {
        if (isAvailable) requested.value = enabled
    }
}

@Composable
internal fun rememberPocketAdvanceDevelopmentState(): PocketAdvanceDevelopmentState {
    val requested = rememberSaveable { mutableStateOf(false) }
    return remember(requested) {
        PocketAdvanceDevelopmentState(BuildConfig.DEBUG, requested)
    }
}

internal val LocalPocketAdvanceDevelopmentState = staticCompositionLocalOf {
    PocketAdvanceDevelopmentState(false, mutableStateOf(false))
}

/** This entire section is absent when the development state is unavailable. */
@Composable
internal fun PocketAdvanceDevelopmentSettings() {
    val state = LocalPocketAdvanceDevelopmentState.current
    if (!state.isAvailable) return
    var showChoices by rememberSaveable { mutableStateOf(false) }

    SettingsSectionSpacer()
    SettingsSection(
        title = "Development",
        description = "Temporary player testing controls. Your primary theme and app colors stay unchanged.",
        icon = Icons.Filled.BugReport
    ) {
        SettingsRow(
            title = "Tablet landscape player",
            summary = if (state.enabled) {
                "Pocket Advance · falls back to the primary player outside supported tablet landscape windows"
            } else {
                "Primary player presentation"
            },
            icon = Icons.Filled.BugReport,
            onClick = { showChoices = true },
            navigationContentDescription = "Choose development tablet landscape player"
        )
    }
    if (showChoices) {
        AlertDialog(
            onDismissRequest = { showChoices = false },
            title = { Text("Development player presentation") },
            text = {
                Column {
                    Text(
                        "Pocket Advance activates only in a supported tablet landscape window. This choice is saved for Activity recreation, not as an app preference.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    listOf(false to "Primary player", true to "Pocket Advance").forEach { (enabled, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(
                                    selected = state.enabled == enabled,
                                    role = Role.RadioButton,
                                    onClick = {
                                        state.setEnabled(enabled)
                                        showChoices = false
                                    }
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = state.enabled == enabled, onClick = null)
                            Text(label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showChoices = false }) { Text("Close") }
            }
        )
    }
}
