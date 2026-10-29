package io.github.rsgarrido.sazanami.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.data.TabletLandscapeTheme
import io.github.rsgarrido.sazanami.ui.AppShellIcons

/** Visible in every configuration; eligibility belongs to the player resolver, not Settings. */
@Composable
internal fun TabletLandscapeThemeSettings(
    selectedTheme: TabletLandscapeTheme,
    onThemeSelected: (TabletLandscapeTheme) -> Unit
) {
    var showChoices by rememberSaveable { mutableStateOf(false) }
    val explanation = stringResource(R.string.settings_tablet_landscape_theme_summary)
    SettingsRow(
        title = stringResource(R.string.settings_tablet_landscape_theme),
        summary = stringResource(selectedTheme.labelRes),
        icon = AppShellIcons.Deck,
        modifier = Modifier.testTag("tabletLandscapeThemeSetting"),
        onClick = { showChoices = true },
        emphasizeSummary = true,
        navigationContentDescription = stringResource(R.string.settings_choose_tablet_landscape_theme)
    )
    SettingsFooterNote(explanation)
    if (showChoices) {
        AlertDialog(
            onDismissRequest = { showChoices = false },
            title = { Text(stringResource(R.string.settings_tablet_landscape_theme)) },
            text = {
                Column(Modifier.selectableGroup()) {
                    Text(explanation, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 12.dp))
                    TabletLandscapeTheme.entries.forEach { theme ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .testTag("tabletLandscapeThemeOption" + theme.name)
                            .selectable(selected = selectedTheme == theme, role = Role.RadioButton,
                                onClick = { onThemeSelected(theme); showChoices = false })
                            .padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selectedTheme == theme, onClick = null)
                            Text(stringResource(theme.labelRes), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showChoices = false }) { Text(stringResource(R.string.settings_close)) }
            }
        )
    }
}
