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
import io.github.rsgarrido.sazanami.data.PocketHorizonAppearance
import io.github.rsgarrido.sazanami.data.PocketHorizonConsoleFinish
import io.github.rsgarrido.sazanami.data.PocketHorizonWaveTreatment
import io.github.rsgarrido.sazanami.data.PocketHorizonLcdPalette
import io.github.rsgarrido.sazanami.ui.AppShellIcons
import io.github.rsgarrido.sazanami.ui.player.pockethorizon.PocketHorizonStyle
import io.github.rsgarrido.sazanami.ui.player.pockethorizon.PocketHorizonAppearancePreview
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeColorPreset

/** Appearance is independent of activation and can be prepared on any device or orientation. */
@Composable
internal fun PocketHorizonAppearanceSettings(
    appearance: PocketHorizonAppearance,
    onConsoleSelected: (PocketHorizonConsoleFinish) -> Unit,
    onLcdSelected: (PocketHorizonLcdPalette) -> Unit,
    onWaveSelected: (PocketHorizonWaveTreatment) -> Unit,
    onReset: () -> Unit
) {
    var showChoices by rememberSaveable { mutableStateOf(false) }
    // Retain the established landscape appearance row identity for both dedicated themes.
    SettingsRow(
        title = stringResource(R.string.settings_customize_pocket_horizon),
        summary = stringResource(R.string.settings_customize_pocket_horizon_summary),
        icon = AppShellIcons.Palette,
        modifier = Modifier.testTag("pocketAdvanceAppearanceSetting"),
        onClick = { showChoices = true },
        navigationContentDescription = stringResource(R.string.settings_customize_pocket_horizon)
    )
    if (showChoices) {
        PocketHorizonAppearanceDialog(appearance, onConsoleSelected, onLcdSelected, onWaveSelected,
            onReset, onDismiss = { showChoices = false })
    }
}

@Composable
private fun PocketHorizonAppearanceDialog(
    appearance: PocketHorizonAppearance,
    onConsoleSelected: (PocketHorizonConsoleFinish) -> Unit,
    onLcdSelected: (PocketHorizonLcdPalette) -> Unit,
    onWaveSelected: (PocketHorizonWaveTreatment) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val consoleColors = remember {
        PocketHorizonConsoleFinish.entries.associateWith { PocketHorizonStyle.from(PocketHorizonAppearance(consoleFinish = it)).shell }
    }
    val lcdColors = remember {
        PocketHorizonLcdPalette.entries.associateWith { PocketHorizonStyle.from(PocketHorizonAppearance(lcdPalette = it)).displayTop }
    }
    val waveColors = remember(appearance.lcdPalette) {
        PocketHorizonWaveTreatment.entries.associateWith { PocketHorizonStyle.from(PocketHorizonAppearance(lcdPalette = appearance.lcdPalette, waveTreatment = it)).let { style -> androidx.compose.ui.graphics.lerp(style.displayBottom, style.horizon, style.waveIntensity) } }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pocket_horizon_appearance_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).testTag("pocketHorizonAppearanceOptions")) {
                PocketHorizonAppearancePreview(remember(appearance) { PocketHorizonStyle.from(appearance) })
                Spacer(Modifier.padding(top = 12.dp))
                PocketHorizonPresetSection(
                    title = stringResource(R.string.pocket_horizon_console_finish),
                    currentName = stringResource(appearance.consoleFinish.labelRes),
                    currentTag = "pocketHorizonConsoleCurrent", selected = appearance.consoleFinish,
                    choices = PocketHorizonConsoleFinish.entries.map { finish ->
                        HorizonAppearanceSwatch(finish, stringResource(finish.labelRes),
                            consoleColors.getValue(finish))
                    }, onSelected = onConsoleSelected
                )
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                PocketHorizonPresetSection(
                    title = stringResource(R.string.pocket_horizon_lcd_palette),
                    currentName = stringResource(appearance.lcdPalette.labelRes),
                    currentTag = "pocketHorizonLcdCurrent", selected = appearance.lcdPalette,
                    choices = PocketHorizonLcdPalette.entries.map { preset ->
                        HorizonAppearanceSwatch(preset, stringResource(preset.labelRes),
                            lcdColors.getValue(preset))
                    }, onSelected = onLcdSelected
                )
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                PocketHorizonPresetSection(
                    title = stringResource(R.string.pocket_horizon_wave_treatment),
                    currentName = stringResource(appearance.waveTreatment.labelRes),
                    currentTag = "pocketHorizonWaveCurrent", selected = appearance.waveTreatment,
                    choices = PocketHorizonWaveTreatment.entries.map { finish ->
                        HorizonAppearanceSwatch(finish, stringResource(finish.labelRes),
                            waveColors.getValue(finish))
                    }, onSelected = onWaveSelected
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

private data class HorizonAppearanceSwatch<T>(val value: T, val name: String, val color: Color)

@Composable
private fun <T> PocketHorizonPresetSection(
    title: String, currentName: String, currentTag: String,
    selected: T, choices: List<HorizonAppearanceSwatch<T>>, onSelected: (T) -> Unit
) {
    val columns = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.5f) 1 else 2
    Column(Modifier.fillMaxWidth().selectableGroup()) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(currentName, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp).testTag(currentTag))
        Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            choices.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { choice ->
                        ColorSwatchButton(PlayerThemeColorPreset(choice.name, choice.color), title,
                            choice.value == selected, onClick = { onSelected(choice.value) },
                            modifier = Modifier.weight(1f), swatchSize = 48.dp, selectionRole = Role.RadioButton)
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
