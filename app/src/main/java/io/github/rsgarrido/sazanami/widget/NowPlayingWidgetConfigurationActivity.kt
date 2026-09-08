package io.github.rsgarrido.sazanami.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.ui.theme.SazanamiTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NowPlayingWidgetConfigurationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        val glanceManager = GlanceAppWidgetManager(this)
        val glanceId = glanceManager.getGlanceIdBy(intent)
        if (glanceId == null) {
            finish()
            return
        }
        val appWidgetId = glanceManager.getAppWidgetId(glanceId)
        val provider = AppWidgetManager.getInstance(this)
            .getAppWidgetInfo(appWidgetId)
            ?.provider
        if (provider != ComponentName(this, NowPlayingWidgetReceiver::class.java)) {
            finish()
            return
        }
        val preferences = NowPlayingWidgetPreferences(this)
        setResult(
            RESULT_CANCELED,
            widgetConfigurationResultIntent(appWidgetId)
        )

        setContent {
            SazanamiTheme {
                var isSaving by remember { mutableStateOf(false) }
                var saveFailed by remember { mutableStateOf(false) }
                WidgetConfigurationScreen(
                    initialMode = preferences.load(appWidgetId),
                    isSaving = isSaving,
                    saveFailed = saveFailed,
                    onCancel = ::finish,
                    onSave = { mode ->
                        if (!isSaving) {
                            isSaving = true
                            saveFailed = false
                            lifecycleScope.launch {
                                val updated = saveAndRefreshWidgetAppearance(
                                    appWidgetId = appWidgetId,
                                    mode = mode,
                                    persist = { targetId, targetMode ->
                                        withContext(Dispatchers.IO) {
                                            preferences.save(targetId, targetMode)
                                        }
                                    },
                                    refresh = { targetId ->
                                        check(targetId == appWidgetId)
                                        invalidateNowPlayingWidgetPresentation(
                                            this@NowPlayingWidgetConfigurationActivity,
                                            glanceId
                                        )
                                    }
                                )
                                if (updated) {
                                    setResult(
                                        RESULT_OK,
                                        widgetConfigurationResultIntent(appWidgetId)
                                    )
                                    finish()
                                } else {
                                    isSaving = false
                                    saveFailed = true
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

internal fun widgetConfigurationResultIntent(appWidgetId: Int): Intent =
    Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

@Composable
private fun WidgetConfigurationScreen(
    initialMode: WidgetAppearanceMode,
    isSaving: Boolean,
    saveFailed: Boolean,
    onCancel: () -> Unit,
    onSave: (WidgetAppearanceMode) -> Unit
) {
    var selectedMode by remember(initialMode) { mutableStateOf(initialMode) }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Text(
                text = stringResource(R.string.widget_configure_title),
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.widget_configure_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(20.dp))

            WidgetAppearanceMode.entries.forEach { mode ->
                WidgetAppearanceOption(
                    mode = mode,
                    selected = mode == selectedMode,
                    enabled = !isSaving,
                    onSelect = { selectedMode = mode }
                )
            }

            if (saveFailed) {
                Text(
                    text = stringResource(R.string.widget_configure_save_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onCancel, enabled = !isSaving) {
                    Text(stringResource(R.string.widget_configure_cancel))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onSave(selectedMode) },
                    enabled = !isSaving
                ) {
                    Text(stringResource(R.string.widget_configure_save))
                }
            }
        }
    }
}

@Composable
private fun WidgetAppearanceOption(
    mode: WidgetAppearanceMode,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit
) {
    val title = when (mode) {
        WidgetAppearanceMode.FOLLOW_PLAYER_THEME -> R.string.widget_appearance_follow
        WidgetAppearanceMode.SAZANAMI_DEFAULT -> R.string.widget_appearance_default
        WidgetAppearanceMode.SYSTEM_DYNAMIC -> R.string.widget_appearance_system_dynamic
        WidgetAppearanceMode.RETRO_RACK -> R.string.widget_appearance_retro_rack
        WidgetAppearanceMode.POCKET_CASSETTE -> R.string.widget_appearance_pocket_cassette
    }
    val description = when (mode) {
        WidgetAppearanceMode.FOLLOW_PLAYER_THEME -> R.string.widget_appearance_follow_description
        WidgetAppearanceMode.SAZANAMI_DEFAULT -> R.string.widget_appearance_default_description
        WidgetAppearanceMode.SYSTEM_DYNAMIC -> R.string.widget_appearance_system_dynamic_description
        WidgetAppearanceMode.RETRO_RACK -> R.string.widget_appearance_retro_rack_description
        WidgetAppearanceMode.POCKET_CASSETTE ->
            R.string.widget_appearance_pocket_cassette_description
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onSelect)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            enabled = enabled
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
