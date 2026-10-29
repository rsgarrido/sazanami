package io.github.rsgarrido.sazanami.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.data.PocketAdvanceAppearance
import io.github.rsgarrido.sazanami.data.PocketAdvanceConsoleFinish
import io.github.rsgarrido.sazanami.data.PocketAdvanceControlsFinish
import io.github.rsgarrido.sazanami.data.PocketAdvanceLcdPalette
import io.github.rsgarrido.sazanami.ui.AppShellIcons
import io.github.rsgarrido.sazanami.ui.player.pocketadvance.PocketAdvancePalette
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeColorPreset

/** Appearance is independent of activation and can be prepared on any device or orientation. */
@Composable
internal fun PocketAdvanceAppearanceSettings(
    appearance: PocketAdvanceAppearance,
    onConsoleSelected: (PocketAdvanceConsoleFinish) -> Unit,
    onLcdSelected: (PocketAdvanceLcdPalette) -> Unit,
    onControlsSelected: (PocketAdvanceControlsFinish) -> Unit,
    onReset: () -> Unit
) {
    var showChoices by rememberSaveable { mutableStateOf(false) }
    SettingsRow(
        title = stringResource(R.string.settings_customize_pocket_advance),
        summary = stringResource(R.string.settings_customize_pocket_advance_summary),
        icon = AppShellIcons.Palette,
        modifier = Modifier.testTag("pocketAdvanceAppearanceSetting"),
        onClick = { showChoices = true },
        navigationContentDescription = stringResource(R.string.settings_customize_pocket_advance)
    )
    if (showChoices) {
        PocketAdvanceAppearanceDialog(appearance, onConsoleSelected, onLcdSelected, onControlsSelected,
            onReset, onDismiss = { showChoices = false })
    }
}

@Composable
private fun PocketAdvanceAppearanceDialog(
    appearance: PocketAdvanceAppearance,
    onConsoleSelected: (PocketAdvanceConsoleFinish) -> Unit,
    onLcdSelected: (PocketAdvanceLcdPalette) -> Unit,
    onControlsSelected: (PocketAdvanceControlsFinish) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val consoleColors = remember {
        PocketAdvanceConsoleFinish.entries.associateWith { PocketAdvancePalette.from(PocketAdvanceAppearance(consoleFinish = it)).Shell }
    }
    val lcdColors = remember {
        PocketAdvanceLcdPalette.entries.associateWith { PocketAdvancePalette.from(PocketAdvanceAppearance(lcdPalette = it)).Screen }
    }
    val controlsColors = remember {
        PocketAdvanceControlsFinish.entries.associateWith { PocketAdvancePalette.from(PocketAdvanceAppearance(controlsFinish = it)).Button }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pocket_advance_appearance_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).testTag("pocketAdvanceAppearanceOptions")) {
                PocketAdvancePresetSection(
                    title = stringResource(R.string.pocket_advance_console_finish),
                    description = stringResource(R.string.pocket_advance_console_finish_description),
                    currentName = stringResource(appearance.consoleFinish.labelRes),
                    currentTag = "pocketAdvanceConsoleCurrent", selected = appearance.consoleFinish,
                    choices = PocketAdvanceConsoleFinish.entries.map { finish ->
                        AppearanceSwatch(finish, stringResource(finish.labelRes),
                            consoleColors.getValue(finish))
                    }, onSelected = onConsoleSelected
                )
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                PocketAdvancePresetSection(
                    title = stringResource(R.string.pocket_advance_lcd_palette),
                    description = stringResource(R.string.pocket_advance_lcd_palette_description),
                    currentName = stringResource(appearance.lcdPalette.labelRes),
                    currentTag = "pocketAdvanceLcdCurrent", selected = appearance.lcdPalette,
                    choices = PocketAdvanceLcdPalette.entries.map { preset ->
                        AppearanceSwatch(preset, stringResource(preset.labelRes),
                            lcdColors.getValue(preset))
                    }, onSelected = onLcdSelected
                )
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                PocketAdvancePresetSection(
                    title = stringResource(R.string.pocket_advance_controls_finish),
                    description = stringResource(R.string.pocket_advance_controls_finish_description),
                    currentName = stringResource(appearance.controlsFinish.labelRes),
                    currentTag = "pocketAdvanceControlsCurrent", selected = appearance.controlsFinish,
                    choices = PocketAdvanceControlsFinish.entries.map { finish ->
                        AppearanceSwatch(finish, stringResource(finish.labelRes),
                            controlsColors.getValue(finish))
                    }, onSelected = onControlsSelected
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onReset) { Text(stringResource(R.string.theme_color_reset)) }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_done)) }
        }
    )
}

private data class AppearanceSwatch<T>(val value: T, val name: String, val color: Color)

@Composable
private fun <T> PocketAdvancePresetSection(
    title: String, description: String, currentName: String, currentTag: String,
    selected: T, choices: List<AppearanceSwatch<T>>, onSelected: (T) -> Unit
) {
    Column(Modifier.fillMaxWidth().selectableGroup()) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(description, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(currentName, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp).testTag(currentTag))
        Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            choices.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { choice ->
                        ColorSwatchButton(PlayerThemeColorPreset(choice.name, choice.color), title,
                            choice.value == selected, onClick = { onSelected(choice.value) },
                            modifier = Modifier.weight(1f), swatchSize = 48.dp, selectionRole = Role.RadioButton)
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
