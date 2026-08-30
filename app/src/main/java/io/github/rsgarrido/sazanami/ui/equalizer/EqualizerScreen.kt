package io.github.rsgarrido.sazanami.ui.equalizer

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.player.equalizer.MAX_EQUALIZER_BAND_DB
import io.github.rsgarrido.sazanami.player.equalizer.MAX_EQUALIZER_PREAMP_DB
import io.github.rsgarrido.sazanami.player.equalizer.MIN_EQUALIZER_BAND_DB
import io.github.rsgarrido.sazanami.player.equalizer.MIN_EQUALIZER_PREAMP_DB
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerMode
import io.github.rsgarrido.sazanami.player.equalizer.UserEqualizerPreset
import io.github.rsgarrido.sazanami.player.equalizer.dsp.GraphicEqualizerDefaults
import io.github.rsgarrido.sazanami.player.equalizer.normalizeEqualizerDb
import io.github.rsgarrido.sazanami.player.equalizer.limiter.MAX_LIMITER_CEILING_DBFS
import io.github.rsgarrido.sazanami.player.equalizer.limiter.MIN_LIMITER_CEILING_DBFS
import java.util.Locale
import kotlin.math.round

@Composable
internal fun EqualizerScreen(
    state: EqualizerScreenState,
    actions: EqualizerUiActions,
    modifier: Modifier = Modifier
) {
    if (state.importPreview != null) {
        EqualizerImportPreviewScreen(
            state = state,
            actions = actions,
            modifier = modifier
        )
        return
    }
    var presetSelectorVisible by remember {
        mutableStateOf(false)
    }
    var exportPresetSelectorVisible by remember {
        mutableStateOf(false)
    }
    var saveDialogVisible by remember {
        mutableStateOf(false)
    }
    var renamePreset by remember {
        mutableStateOf<UserEqualizerPreset?>(null)
    }
    var deletePreset by remember {
        mutableStateOf<UserEqualizerPreset?>(null)
    }
    var resetConfirmationVisible by remember {
        mutableStateOf(false)
    }
    var fineEditTarget by remember {
        mutableStateOf<FineEditTarget?>(null)
    }
    var limiterCeilingDialogVisible by remember {
        mutableStateOf(false)
    }
    var limiterCeilingDialogInitialValue by remember {
        mutableDoubleStateOf(-1.0)
    }
    val preferences = state.editablePreferences
    val activePreampDb = when (preferences.mode) {
        EqualizerMode.GRAPHIC -> preferences.preampDb
        EqualizerMode.PARAMETRIC ->
            preferences.parametricState.preampDb
    }
    val activeAutomaticHeadroom = when (preferences.mode) {
        EqualizerMode.GRAPHIC ->
            preferences.automaticHeadroomEnabled
        EqualizerMode.PARAMETRIC ->
            preferences.parametricState.automaticHeadroomEnabled
    }
    var latestPreampDragValue by remember(
        activePreampDb
    ) {
        mutableDoubleStateOf(activePreampDb)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            IconButton(onClick = actions.onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
            Text(
                text = "Equalizer",
                style = MaterialTheme.typography.titleLarge
            )
        }

        ListItem(
            headlineContent = { Text("Equalizer") },
            supportingContent = {
                Text(state.statusText)
            },
            trailingContent = {
                Switch(
                    checked = preferences.enabled,
                    onCheckedChange = actions.onEnabledChanged,
                    modifier = Modifier.semantics {
                        contentDescription =
                            "Equalizer enabled"
                    }
                )
            }
        )

        Text(
            text = "Equalizer mode",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(
                start = 16.dp,
                top = 12.dp
            )
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .semantics {
                    contentDescription = "Equalizer mode selector"
                }
        ) {
            EqualizerMode.entries.forEach { mode ->
                FilterChip(
                    selected = preferences.mode == mode,
                    onClick = { actions.onModeChanged(mode) },
                    label = { Text(mode.displayName) },
                    modifier = Modifier.semantics {
                        contentDescription =
                            "${mode.displayName} equalizer mode"
                    }
                )
            }
        }

        Text(
            text = "Import and export",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(
                start = 16.dp,
                top = 12.dp
            )
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(
                horizontal = 16.dp,
                vertical = 8.dp
            )
        ) {
            OutlinedButton(
                onClick = actions.onImportFromFile,
                modifier = Modifier.semantics {
                    contentDescription = "Import EQ from file"
                }
            ) {
                Text("Import from file")
            }
            OutlinedButton(
                onClick = actions.onPasteEqText,
                modifier = Modifier.semantics {
                    contentDescription = "Paste EQ text"
                }
            ) {
                Text("Paste EQ text")
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            OutlinedButton(
                onClick = actions.onExportCurrentEqText,
                enabled = preferences.mode ==
                    EqualizerMode.PARAMETRIC,
                modifier = Modifier.semantics {
                    contentDescription =
                        "Export current Parametric EQ text"
                }
            ) {
                Text("Export current EQ")
            }
            OutlinedButton(
                onClick = actions.onCopyCurrentEqText,
                enabled = preferences.mode ==
                    EqualizerMode.PARAMETRIC,
                modifier = Modifier.semantics {
                    contentDescription =
                        "Copy Parametric EQ text"
                }
            ) {
                Text("Copy EQ text")
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(
                horizontal = 16.dp,
                vertical = 8.dp
            )
        ) {
            OutlinedButton(
                onClick = actions.onExportCurrentNative,
                enabled = preferences.mode ==
                    EqualizerMode.PARAMETRIC,
                modifier = Modifier.semantics {
                    contentDescription =
                        "Export current native Sazanami preset"
                }
            ) {
                Text("Export native")
            }
            OutlinedButton(
                onClick = {
                    exportPresetSelectorVisible = true
                },
                enabled =
                    state.parametricUserPresets.isNotEmpty(),
                modifier = Modifier.semantics {
                    contentDescription =
                        "Export saved Parametric preset"
                }
            ) {
                Text("Export preset")
            }
        }
        Text(
            text = if (preferences.mode == EqualizerMode.GRAPHIC) {
                "Current export is unavailable in Graphic mode. " +
                    "Sazanami does not convert Graphic bands into " +
                    "Parametric filters."
            } else {
                "Text export is Equalizer APO-compatible. Native " +
                    ".cdpeq preserves automatic headroom, IDs, and " +
                    "all stored parameters losslessly."
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        if (state.importInProgress) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .semantics {
                        contentDescription =
                            "Reading EQ import"
                    }
            )
        }
        state.importMessage?.let { message ->
            Text(
                message,
                color = if (
                    message.startsWith("Couldn't")
                ) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.padding(
                    horizontal = 16.dp,
                    vertical = 4.dp
                )
            )
        }

        if (preferences.mode == EqualizerMode.GRAPHIC) {
            ListItem(
                headlineContent = { Text("Preset") },
                supportingContent = { Text(state.presetLabel) },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Choose equalizer preset"
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription =
                            "Equalizer preset, ${state.presetLabel}"
                    }
                    .padding(horizontal = 4.dp)
            )
            TextButton(
                onClick = { presetSelectorVisible = true },
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                Text("Choose or manage presets")
            }
        } else {
            ParametricEqualizerEditor(
                state = state,
                actions = actions
            )
        }

        EqualizerResponseGraph(
            analysis = state.analysis,
            filters = if (
                preferences.mode == EqualizerMode.PARAMETRIC
            ) {
                preferences.parametricState.filters
            } else {
                emptyList()
            },
            selectedFilterId = state.selectedParametricFilterId,
            ignoredFilterIndices = state.analysis.ignoredFilterIndices,
            onSelectFilter = actions.onSelectParametricFilter,
            onPreviewFilter = actions.onPreviewParametricFilter,
            onCommitFilter = actions.onCommitParametricFilter,
            modifier = Modifier.padding(16.dp)
        )

        EqualizerAnalysisStatus(state)

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Text(
            text = "Preamp",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Slider(
                value = activePreampDb.toFloat(),
                onValueChange = { value ->
                    latestPreampDragValue =
                        snapPreamp(value.toDouble())
                    actions.onPreviewPreamp(
                        latestPreampDragValue
                    )
                },
                onValueChangeFinished = {
                    actions.onCommitPreamp(
                        latestPreampDragValue
                    )
                },
                valueRange =
                    MIN_EQUALIZER_PREAMP_DB.toFloat()..
                        MAX_EQUALIZER_PREAMP_DB.toFloat(),
                steps = 41,
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        contentDescription =
                            "Equalizer preamp, " +
                                formatEqualizerDb(
                                    activePreampDb
                                )
                    }
            )
            TextButton(
                onClick = {
                    fineEditTarget = FineEditTarget(
                        title = "Preamp",
                        initialValueDb =
                            activePreampDb,
                        minimumDb =
                            MIN_EQUALIZER_PREAMP_DB,
                        maximumDb =
                            MAX_EQUALIZER_PREAMP_DB,
                        bandIndex = null
                    )
                }
            ) {
                Text(formatEqualizerDb(activePreampDb))
            }
        }

        if (preferences.mode == EqualizerMode.GRAPHIC) {
            Text(
                text = "Graphic bands",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(
                    start = 16.dp,
                    top = 16.dp
                )
            )
            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                GraphicEqualizerDefaults.frequenciesHz
                    .forEachIndexed { index, frequencyHz ->
                        EqualizerBandSlider(
                            frequencyHz = frequencyHz,
                            gainDb =
                                preferences.bandGainsDb[index],
                            unavailable =
                                index in state.analysis
                                    .ignoredBandIndices,
                            onValueChange = { gain ->
                                actions.onPreviewBandGain(index, gain)
                            },
                            onValueChangeFinished = { gain ->
                                actions.onCommitBandGain(index, gain)
                            },
                            onFineEditClick = {
                                fineEditTarget = FineEditTarget(
                                    title = formatEqualizerFrequency(
                                        frequencyHz
                                    ),
                                    initialValueDb =
                                        preferences.bandGainsDb[index],
                                    minimumDb =
                                        MIN_EQUALIZER_BAND_DB,
                                    maximumDb =
                                        MAX_EQUALIZER_BAND_DB,
                                    bandIndex = index
                                )
                            }
                        )
                    }
            }
        }

        ListItem(
            headlineContent = {
                Text("Automatic headroom")
            },
            supportingContent = {
                Text(
                    "Primary gain-safety stage. Reduces the signal " +
                        "before equalization when " +
                        "the combined curve is predicted to exceed " +
                        "digital full scale."
                )
            },
            trailingContent = {
                Switch(
                    checked =
                        activeAutomaticHeadroom,
                    onCheckedChange =
                        actions.onAutomaticHeadroomChanged,
                    modifier = Modifier.semantics {
                        contentDescription =
                            "Automatic equalizer headroom"
                    }
                )
            }
        )
        if (
            !activeAutomaticHeadroom &&
            state.analysis.predictedMaximumDb > 0.0
        ) {
            Text(
                text = "The predicted response exceeds 0 dB. " +
                    "PCM16 saturation is not a limiter.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Text(
            text = "Limiter",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        ListItem(
            headlineContent = { Text("Sample-peak limiter") },
            supportingContent = {
                Text(
                    "Channel-linked gain safety after the equalizer. " +
                        "Enabling it adds 5 ms of audio latency."
                )
            },
            trailingContent = {
                Switch(
                    checked = preferences.limiterEnabled,
                    onCheckedChange =
                        actions.onLimiterEnabledChanged,
                    modifier = Modifier.semantics {
                        contentDescription =
                            "Sample-peak limiter enabled"
                    }
                )
            }
        )
        var latestLimiterCeiling by remember(
            preferences.limiterCeilingDbfs
        ) {
            mutableDoubleStateOf(
                preferences.limiterCeilingDbfs
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Slider(
                value =
                    preferences.limiterCeilingDbfs.toFloat(),
                onValueChange = { value ->
                    latestLimiterCeiling =
                        (round(value * 10.0) / 10.0)
                    actions.onPreviewLimiterCeiling(
                        latestLimiterCeiling
                    )
                },
                onValueChangeFinished = {
                    actions.onCommitLimiterCeiling(
                        latestLimiterCeiling
                    )
                },
                valueRange =
                    MIN_LIMITER_CEILING_DBFS.toFloat()..
                        MAX_LIMITER_CEILING_DBFS.toFloat(),
                steps = 29,
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        contentDescription =
                            "Limiter ceiling, " +
                                formatLimiterDb(
                                    preferences
                                        .limiterCeilingDbfs
                                )
                    }
            )
            TextButton(
                onClick = {
                    limiterCeilingDialogInitialValue =
                        preferences.limiterCeilingDbfs
                    limiterCeilingDialogVisible = true
                }
            ) {
                Text(
                    formatLimiterDb(
                        preferences.limiterCeilingDbfs
                    )
                )
            }
        }
        LimiterMeters(
            state = state,
            limiterEnabled = preferences.limiterEnabled,
            onReset = actions.onResetLimiterMeters
        )
        Text(
            text = "Fixed lookahead: 5 ms · Release: 100 ms",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Text(
            text = "This is a sample-peak safety limiter, not a " +
                "true-peak limiter. Inter-sample peaks can still exceed " +
                "the selected ceiling.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .padding(
                    horizontal = 16.dp,
                    vertical = 8.dp
                )
                .semantics {
                    contentDescription =
                        "Sample-peak limiter disclaimer. " +
                            "This is not a true-peak limiter."
                }
        )
        if (
            !preferences.limiterEnabled &&
            state.runtimeState.saturatedSampleCount > 0L
        ) {
            Text(
                text = "PCM16 output saturation has been observed. " +
                    "Saturation is not limiting.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        if (state.comparisonAvailable) {
            Text(
                text = "A/B comparison",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(
                    start = 16.dp,
                    top = 20.dp
                )
            )
            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                if (!state.comparisonBypassed) {
                    Button(
                        onClick = {
                            actions
                                .onComparisonBypassedChanged(false)
                        }
                    ) {
                        Text("A · Equalized")
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            actions
                                .onComparisonBypassedChanged(false)
                        }
                    ) {
                        Text("A · Equalized")
                    }
                }
                if (state.comparisonBypassed) {
                    Button(
                        onClick = {
                            actions
                                .onComparisonBypassedChanged(true)
                        }
                    ) {
                        Text("B · Bypass")
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            actions
                                .onComparisonBypassedChanged(true)
                        }
                    ) {
                        Text("B · Bypass")
                    }
                }
            }
            Text(
                text = "B uses exact DSP bypass while keeping decoded " +
                    "PCM active to avoid offload or renderer churn.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        } else if (preferences.limiterEnabled) {
            Text(
                text = "Disable the limiter for exact A/B comparison.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(
                    horizontal = 16.dp,
                    vertical = 12.dp
                )
            )
        }

        FilledTonalButton(
            onClick = {
                if (state.presetLabel == "Flat") {
                    actions.onResetToFlat()
                } else {
                    resetConfirmationVisible = true
                }
            },
            modifier = Modifier.padding(16.dp)
        ) {
            Text("Reset to Flat")
        }

        Card(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "An active equalizer requires decoded PCM. " +
                    "Sazanami does not claim bit-perfect or " +
                    "high-resolution output while processing.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp)
            )
        }
        Spacer(Modifier.height(24.dp))
    }

    if (presetSelectorVisible) {
        EqualizerPresetSelectorDialog(
            userPresets = state.userPresets,
            onDismiss = {
                presetSelectorVisible = false
            },
            onApplyBuiltIn =
                actions.onApplyBuiltInPreset,
            onApplyUser = actions.onApplyUserPreset,
            onSaveAs = { saveDialogVisible = true },
            onRename = { preset ->
                presetSelectorVisible = false
                renamePreset = preset
            },
            onDelete = { preset ->
                presetSelectorVisible = false
                deletePreset = preset
            }
        )
    }
    if (exportPresetSelectorVisible) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                exportPresetSelectorVisible = false
            },
            title = { Text("Export Parametric preset") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(
                        rememberScrollState()
                    )
                ) {
                    state.parametricUserPresets.forEach { preset ->
                        Row(
                            verticalAlignment =
                                Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                preset.name,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    exportPresetSelectorVisible =
                                        false
                                    actions
                                        .onExportParametricPresetText(
                                            preset
                                        )
                                }
                            ) {
                                Text("Text")
                            }
                            TextButton(
                                onClick = {
                                    exportPresetSelectorVisible =
                                        false
                                    actions
                                        .onExportParametricPresetNative(
                                            preset
                                        )
                                }
                            ) {
                                Text("Native")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        exportPresetSelectorVisible = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
    if (saveDialogVisible) {
        EqualizerPresetNameDialog(
            title = "Save as preset",
            initialName = "",
            userPresets = state.userPresets,
            confirmText = "Save",
            onDismiss = { saveDialogVisible = false },
            onConfirm = { name ->
                actions.onSaveUserPreset(name)
                saveDialogVisible = false
            }
        )
    }
    renamePreset?.let { preset ->
        EqualizerPresetNameDialog(
            title = "Rename preset",
            initialName = preset.name,
            userPresets = state.userPresets,
            excludingPresetId = preset.id,
            confirmText = "Rename",
            onDismiss = { renamePreset = null },
            onConfirm = { name ->
                actions.onRenameUserPreset(preset.id, name)
                renamePreset = null
            }
        )
    }
    deletePreset?.let { preset ->
        ConfirmEqualizerActionDialog(
            title = "Delete ${preset.name}?",
            message = "The active equalizer curve will not change.",
            confirmText = "Delete",
            onDismiss = { deletePreset = null },
            onConfirm = {
                actions.onDeleteUserPreset(preset.id)
                deletePreset = null
            }
        )
    }
    if (resetConfirmationVisible) {
        ConfirmEqualizerActionDialog(
            title = "Reset to Flat?",
            message = if (
                preferences.mode == EqualizerMode.GRAPHIC
            ) {
                "Preamp and all band gains will reset. " +
                    "Your saved presets will remain."
            } else {
                "Parametric preamp and filters will reset. " +
                    "Your saved presets will remain."
            },
            confirmText = "Reset",
            onDismiss = {
                resetConfirmationVisible = false
            },
            onConfirm = {
                actions.onResetToFlat()
                resetConfirmationVisible = false
            }
        )
    }
    fineEditTarget?.let { target ->
        EqualizerValueDialog(
            title = target.title,
            initialValueDb = target.initialValueDb,
            minimumDb = target.minimumDb,
            maximumDb = target.maximumDb,
            onPreview = { value ->
                target.bandIndex?.let { index ->
                    actions.onPreviewBandGain(index, value)
                } ?: actions.onPreviewPreamp(value)
            },
            onCancel = {
                target.bandIndex?.let { index ->
                    actions.onCancelBandGainPreview(
                        index,
                        target.initialValueDb
                    )
                } ?: actions.onCancelPreampPreview(
                    target.initialValueDb
                )
                fineEditTarget = null
            },
            onApply = { value ->
                target.bandIndex?.let { index ->
                    actions.onCommitBandGain(index, value)
                } ?: actions.onCommitPreamp(value)
                fineEditTarget = null
            }
        )
    }
    if (limiterCeilingDialogVisible) {
        EqualizerValueDialog(
            title = "Limiter ceiling",
            initialValueDb =
                limiterCeilingDialogInitialValue,
            minimumDb = MIN_LIMITER_CEILING_DBFS,
            maximumDb = MAX_LIMITER_CEILING_DBFS,
            onPreview =
                actions.onPreviewLimiterCeiling,
            onCancel = {
                actions.onCancelLimiterCeilingPreview(
                    limiterCeilingDialogInitialValue
                )
                limiterCeilingDialogVisible = false
            },
            onApply = { value ->
                actions.onCommitLimiterCeiling(value)
                limiterCeilingDialogVisible = false
            }
        )
    }
}

@Composable
private fun LimiterMeters(
    state: EqualizerScreenState,
    limiterEnabled: Boolean,
    onReset: () -> Unit
) {
    val runtime = state.runtimeState
    Column(
        modifier = Modifier.padding(
            horizontal = 16.dp,
            vertical = 8.dp
        )
    ) {
        LimiterMeter(
            label = "Pre-limiter peak",
            valueDb = runtime.preLimiterPeakDbfs,
            progress = meterProgress(runtime.preLimiterPeakDbfs)
        )
        LimiterMeter(
            label = "Post-limiter peak",
            valueDb = runtime.postLimiterPeakDbfs,
            progress = meterProgress(runtime.postLimiterPeakDbfs)
        )
        LimiterMeter(
            label = "Gain reduction",
            valueDb = runtime.currentGainReductionDb,
            progress =
                (runtime.currentGainReductionDb / 12.0)
                    .toFloat()
                    .coerceIn(0f, 1f),
            positive = true
        )
        Text(
            "Recent maximum reduction: " +
                formatLimiterDb(
                    runtime.maximumRecentGainReductionDb,
                    positive = true
                )
        )
        Text(
            "Over-range samples: ${runtime.overRangeSampleCount} · " +
                "Saturated samples: ${runtime.saturatedSampleCount}",
            modifier = Modifier.semantics {
                contentDescription =
                    "Over-range sample count, " +
                        "${runtime.overRangeSampleCount}. " +
                        "Saturated sample count, " +
                        runtime.saturatedSampleCount
            }
        )
        Text(
            "Active/reduced frames: " +
                "${runtime.limiterActiveFrameCount} / " +
                runtime.limiterReducedFrameCount
        )
        if (limiterEnabled) {
            Text(
                if (runtime.limiterPrimed) {
                    "Limiter primed"
                } else {
                    "Limiter priming"
                }
            )
        }
        TextButton(
            onClick = onReset,
            modifier = Modifier.semantics {
                contentDescription =
                    "Reset limiter meters and counters"
            }
        ) {
            Text("Reset limiter meters")
        }
    }
}

@Composable
private fun LimiterMeter(
    label: String,
    valueDb: Double,
    progress: Float,
    positive: Boolean = false
) {
    Text(
        "$label: ${formatLimiterDb(valueDb, positive)}",
        style = MaterialTheme.typography.bodyMedium
    )
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription =
                    "$label, ${formatLimiterDb(valueDb, positive)}"
            }
    )
}

private fun meterProgress(valueDb: Double): Float =
    ((valueDb.coerceIn(-60.0, 0.0) + 60.0) / 60.0)
        .toFloat()

private fun formatLimiterDb(
    value: Double,
    positive: Boolean = false
): String = String.format(
    Locale.ROOT,
    if (positive) "%.1f dB" else "%.1f dBFS",
    value
)

@Composable
private fun EqualizerAnalysisStatus(
    state: EqualizerScreenState
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Analysis",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                "User preamp: " +
                    formatEqualizerDb(
                        when (
                            state.editablePreferences.mode
                        ) {
                            EqualizerMode.GRAPHIC ->
                                state.editablePreferences.preampDb
                            EqualizerMode.PARAMETRIC ->
                                state.editablePreferences
                                    .parametricState.preampDb
                        }
                    )
            )
            Text(
                "Automatic attenuation: " +
                    formatEqualizerDb(
                        state.analysis.automaticHeadroom
                            .attenuationDb,
                        includePlus = false
                    )
            )
            Text(
                "Effective preamp: " +
                    formatEqualizerDb(
                        state.analysis.automaticHeadroom
                            .effectivePreampDb
                    )
            )
            Text(
                "Predicted maximum: " +
                    formatEqualizerDb(
                        state.analysis.predictedMaximumDb
                    )
            )
            Text(
                "Sample rate: " +
                    "${state.analysis.sampleRateHz} Hz" +
                    if (
                        state.analysis.usesFallbackSampleRate
                    ) {
                        " (preview fallback)"
                    } else {
                        ""
                    }
            )
            if (state.analysis.ignoredBandIndices.isNotEmpty()) {
                val labels = state.analysis.ignoredBandIndices
                    .sorted()
                    .joinToString { index ->
                        when (state.editablePreferences.mode) {
                            EqualizerMode.GRAPHIC ->
                                formatEqualizerFrequency(
                                    GraphicEqualizerDefaults
                                        .frequenciesHz[index]
                                )
                            EqualizerMode.PARAMETRIC -> {
                                val filter =
                                    state.editablePreferences
                                        .parametricState.filters[index]
                                "${index + 1} " +
                                    formatEqualizerFrequency(
                                        filter.frequencyHz
                                    )
                            }
                        }
                    }
                Text(
                    text = "Unavailable for current source: $labels",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private data class FineEditTarget(
    val title: String,
    val initialValueDb: Double,
    val minimumDb: Double,
    val maximumDb: Double,
    val bandIndex: Int?
)

internal fun snapPreamp(value: Double): Double {
    return normalizeEqualizerDb(
        round(
            value.coerceIn(
                MIN_EQUALIZER_PREAMP_DB,
                MAX_EQUALIZER_PREAMP_DB
            ) * 2.0
        ) / 2.0
    )
}
