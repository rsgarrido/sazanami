package io.github.rsgarrido.sazanami.ui.equalizer

import io.github.rsgarrido.sazanami.player.equalizer.EqualizerMode
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerPreferencesState
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerPresetMatch
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerRuntimeState
import io.github.rsgarrido.sazanami.player.equalizer.GraphicEqualizerPresets
import io.github.rsgarrido.sazanami.player.equalizer.UserEqualizerPreset
import io.github.rsgarrido.sazanami.player.equalizer.toDspConfiguration
import io.github.rsgarrido.sazanami.player.equalizer.parametric.ParametricEqualizerPreset
import io.github.rsgarrido.sazanami.player.equalizer.parametric.ParametricEqualizerPresetMatcher
import io.github.rsgarrido.sazanami.player.equalizer.parametric.ParametricFilter

internal data class EqualizerScreenState(
    val durablePreferences: EqualizerPreferencesState =
        EqualizerPreferencesState(),
    val editablePreferences: EqualizerPreferencesState =
        EqualizerPreferencesState(),
    val presetMatch: EqualizerPresetMatch =
        EqualizerPresetMatch("Flat"),
    val analysis: EqualizerAnalysisResult =
        EqualizerAnalysisResult(),
    val runtimeState: EqualizerRuntimeState =
        EqualizerRuntimeState(),
    val importPreview: EqualizerImportPreviewState? = null,
    val importAnalysis: EqualizerAnalysisResult =
        EqualizerAnalysisResult(),
    val importInProgress: Boolean = false,
    val importMessage: String? = null,
    val selectedParametricFilterId: String? = null,
    val comparisonBypassed: Boolean = false,
    val hasUncommittedPreview: Boolean = false,
    val isLoaded: Boolean = false
) {
    val presetLabel: String
        get() = presetMatch.name

    val userPresets: List<UserEqualizerPreset>
        get() = editablePreferences.userPresets
            .sortedBy { preset -> preset.name.lowercase() }

    val parametricUserPresets: List<ParametricEqualizerPreset>
        get() = editablePreferences.parametricState.userPresets
            .sortedBy { preset -> preset.name.lowercase() }

    val selectedParametricFilter: ParametricFilter?
        get() = editablePreferences.parametricState.filters
            .firstOrNull { filter ->
                filter.id == selectedParametricFilterId
            }

    val comparisonAvailable: Boolean
        get() = !editablePreferences.limiterEnabled &&
            editablePreferences.enabled &&
            !editablePreferences
                .toDspConfiguration(enabledOverride = true)
                .isEffectivelyFlat

    val statusText: String
        get() = when {
            !editablePreferences.enabled ->
                "Off · ${editablePreferences.mode.displayName} " +
                    "· $presetLabel"
            editablePreferences
                .toDspConfiguration(enabledOverride = true)
                .isEffectivelyFlat -> "On · Flat"
            comparisonBypassed -> "B · Exact DSP bypass"
            else -> "A · Active · $presetLabel"
        }

    val settingsSummary: String
        get() = when {
            !editablePreferences.enabled -> "Off"
            else -> buildString {
                append(editablePreferences.mode.displayName)
                append(" · ")
                append(presetLabel)
                if (editablePreferences.limiterEnabled) {
                    append(" · Limiter")
                }
            }
        }
}

internal val EqualizerMode.displayName: String
    get() = when (this) {
        EqualizerMode.GRAPHIC -> "Graphic"
        EqualizerMode.PARAMETRIC -> "Parametric"
    }

internal data class EqualizerUiActions(
    val onBack: () -> Unit,
    val onEnabledChanged: (Boolean) -> Unit,
    val onModeChanged: (EqualizerMode) -> Unit,
    val onPreviewBandGain: (Int, Double) -> Unit,
    val onCommitBandGain: (Int, Double) -> Unit,
    val onCancelBandGainPreview: (Int, Double) -> Unit,
    val onPreviewPreamp: (Double) -> Unit,
    val onCommitPreamp: (Double) -> Unit,
    val onCancelPreampPreview: (Double) -> Unit,
    val onAutomaticHeadroomChanged: (Boolean) -> Unit,
    val onLimiterEnabledChanged: (Boolean) -> Unit,
    val onPreviewLimiterCeiling: (Double) -> Unit,
    val onCommitLimiterCeiling: (Double) -> Unit,
    val onCancelLimiterCeilingPreview: (Double) -> Unit,
    val onResetLimiterMeters: () -> Unit,
    val onApplyBuiltInPreset: (Int) -> Unit,
    val onApplyUserPreset: (String) -> Unit,
    val onSaveUserPreset: (String) -> Unit,
    val onRenameUserPreset: (String, String) -> Unit,
    val onDeleteUserPreset: (String) -> Unit,
    val onSelectParametricFilter: (String?) -> Unit,
    val onAddParametricFilter: () -> Unit,
    val onPreviewParametricFilter: (ParametricFilter) -> Unit,
    val onCommitParametricFilter: (ParametricFilter) -> Unit,
    val onCancelParametricFilterPreview: (ParametricFilter) -> Unit,
    val onMoveParametricFilter: (String, Int) -> Unit,
    val onDeleteParametricFilter: (String) -> Unit,
    val onApplyParametricFlatPreset: () -> Unit,
    val onApplyParametricUserPreset: (String) -> Unit,
    val onSaveParametricUserPreset: (String) -> Unit,
    val onRenameParametricUserPreset: (String, String) -> Unit,
    val onDeleteParametricUserPreset: (String) -> Unit,
    val onImportFromFile: () -> Unit,
    val onPasteEqText: () -> Unit,
    val onExportCurrentEqText: () -> Unit,
    val onCopyCurrentEqText: () -> Unit,
    val onExportCurrentNative: () -> Unit,
    val onExportParametricPresetText:
        (ParametricEqualizerPreset) -> Unit,
    val onExportParametricPresetNative:
        (ParametricEqualizerPreset) -> Unit,
    val onDismissImportPreview: () -> Unit,
    val onUpdateImportPreview:
        ((EqualizerImportPreviewState) ->
            EqualizerImportPreviewState) -> Unit,
    val onReplaceWithImportedProfile: () -> Unit,
    val onSaveImportedProfile: (Boolean) -> Unit,
    val onResetToFlat: () -> Unit,
    val onComparisonBypassedChanged: (Boolean) -> Unit
)

internal fun presetMatchFor(
    state: EqualizerPreferencesState
): EqualizerPresetMatch {
    return when (state.mode) {
        EqualizerMode.GRAPHIC ->
            io.github.rsgarrido.sazanami.player.equalizer
                .EqualizerPresetMatcher
                .match(state)
        EqualizerMode.PARAMETRIC ->
            ParametricEqualizerPresetMatcher.match(
                state.parametricState
            )
    }
        ?: EqualizerPresetMatch("Custom")
}

internal val builtInEqualizerPresets
    get() = GraphicEqualizerPresets.builtIns
