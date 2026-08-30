package io.github.rsgarrido.sazanami.ui.equalizer

import io.github.rsgarrido.sazanami.data.preferences.AppPreferencesRepository
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerMode
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerPreferencesState
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerRuntimeBridge
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerRuntimeState
import io.github.rsgarrido.sazanami.player.equalizer.UserEqualizerPreset
import io.github.rsgarrido.sazanami.player.equalizer.applyPreset
import io.github.rsgarrido.sazanami.player.equalizer.activeAutomaticHeadroomEnabled
import io.github.rsgarrido.sazanami.player.equalizer.toDspConfiguration
import io.github.rsgarrido.sazanami.player.equalizer.limiter.LimiterConfiguration
import io.github.rsgarrido.sazanami.player.equalizer.interchange.CdplayaPresetFileJson
import io.github.rsgarrido.sazanami.player.equalizer.interchange.EqualizerProfileParser
import io.github.rsgarrido.sazanami.player.equalizer.parametric.MAX_PARAMETRIC_FILTER_COUNT
import io.github.rsgarrido.sazanami.player.equalizer.parametric.ParametricFilter
import io.github.rsgarrido.sazanami.player.equalizer.parametric.ParametricFilterFactory
import io.github.rsgarrido.sazanami.player.equalizer.parametric.ParametricEqualizerPresets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class EqualizerUiController(
    private val preferencesRepository:
        AppPreferencesRepository,
    private val runtimeState: StateFlow<EqualizerRuntimeState>,
    private val scope: CoroutineScope
) {
    private val analysisController =
        EqualizerAnalysisController(scope)
    private val _state =
        MutableStateFlow(EqualizerScreenState())
    val state: StateFlow<EqualizerScreenState> =
        _state.asStateFlow()

    private var stateCollectionJob: Job? = null
    private var hasPreviewEdits = false
    private var pendingCommit:
        EqualizerPreferencesState? = null

    init {
        stateCollectionJob = scope.launch {
            launch {
                combine(
                    preferencesRepository.state.filter {
                            preferences ->
                        preferences.isLoaded
                    },
                    runtimeState
                ) { appPreferences, runtime ->
                    appPreferences to runtime
                }.collectLatest {
                        (appPreferences, runtime) ->
                    val durable =
                        appPreferences.equalizerPreferences
                    if (
                        pendingCommit?.hasSameConfigurationAs(
                            durable
                        ) == true
                    ) {
                        pendingCommit = null
                    }
                    val editable = if (hasPreviewEdits) {
                        _state.value.editablePreferences
                            .withDurablePresetLists(durable)
                    } else if (pendingCommit != null) {
                        pendingCommit!!
                            .withDurablePresetLists(durable)
                    } else {
                        durable
                    }
                    _state.value = _state.value.copy(
                        durablePreferences = durable,
                        editablePreferences = editable,
                        presetMatch = presetMatchFor(editable),
                        runtimeState = runtime,
                        isLoaded = true
                    )
                    analysisController.submit(
                        preferences = editable,
                        currentSampleRateHz =
                            runtime.sampleRateHz
                    )
                }
            }
            launch {
                analysisController.state.collectLatest { analysis ->
                    _state.value = _state.value.copy(
                        analysis = analysis
                    )
                }
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        val updated = _state.value.editablePreferences
            .withEnabled(enabled)
        updatePreview(
            updated,
            markDirty = false
        )
        if (!hasPreviewEdits) {
            pendingCommit = updated
        }
        scope.launch {
            preferencesRepository.setEqualizerEnabled(enabled)
        }
    }

    fun setMode(mode: EqualizerMode) {
        val current = _state.value
        if (current.editablePreferences.mode == mode) return
        val updated = current.editablePreferences.withMode(mode)
        updatePreview(updated, markDirty = false)
        pendingCommit = updated
        val selectedId = if (mode == EqualizerMode.PARAMETRIC) {
            updated.parametricState.filters.firstOrNull()?.id
        } else {
            null
        }
        _state.value = _state.value.copy(
            selectedParametricFilterId = selectedId,
            comparisonBypassed = false
        )
        scope.launch {
            preferencesRepository.setEqualizerMode(mode)
        }
    }

    fun previewBandGain(
        index: Int,
        gainDb: Double
    ) {
        updatePreview(
            _state.value.editablePreferences
                .withBandGainDb(index, gainDb)
        )
    }

    fun commitBandGain(
        index: Int,
        gainDb: Double
    ) {
        previewBandGain(index, gainDb)
        commitEditablePreferences()
    }

    fun previewPreamp(preampDb: Double) {
        val preferences = _state.value.editablePreferences
        val updated = when (preferences.mode) {
            EqualizerMode.GRAPHIC ->
                preferences.withPreampDb(preampDb)
            EqualizerMode.PARAMETRIC ->
                preferences.withParametricState(
                    preferences.parametricState
                        .withPreampDb(preampDb)
                )
        }
        updatePreview(updated)
    }

    fun commitPreamp(preampDb: Double) {
        previewPreamp(preampDb)
        commitEditablePreferences()
    }

    fun cancelBandGainPreview(
        index: Int,
        gainDb: Double
    ) {
        cancelPreview(
            _state.value.editablePreferences
                .withBandGainDb(index, gainDb)
        )
    }

    fun cancelPreampPreview(preampDb: Double) {
        val preferences = _state.value.editablePreferences
        cancelPreview(
            when (preferences.mode) {
                EqualizerMode.GRAPHIC ->
                    preferences.withPreampDb(preampDb)
                EqualizerMode.PARAMETRIC ->
                    preferences.withParametricState(
                        preferences.parametricState
                            .withPreampDb(preampDb)
                    )
            }
        )
    }

    fun setAutomaticHeadroomEnabled(enabled: Boolean) {
        val preferences = _state.value.editablePreferences
        updatePreview(
            when (preferences.mode) {
                EqualizerMode.GRAPHIC ->
                    preferences.withAutomaticHeadroomEnabled(enabled)
                EqualizerMode.PARAMETRIC ->
                    preferences.withParametricState(
                        preferences.parametricState
                            .withAutomaticHeadroomEnabled(enabled)
                    )
            }
        )
        commitEditablePreferences()
    }

    fun setLimiterEnabled(enabled: Boolean) {
        if (enabled) {
            EqualizerRuntimeBridge.setComparisonState(
                sessionActive = false,
                bypassed = false
            )
        }
        val updated = _state.value.editablePreferences
            .withLimiterEnabled(enabled)
        updatePreview(updated, markDirty = false)
        pendingCommit = updated
        scope.launch {
            preferencesRepository.setLimiterEnabled(enabled)
        }
    }

    fun previewLimiterCeiling(ceilingDbfs: Double) {
        updatePreview(
            _state.value.editablePreferences
                .withLimiterCeilingDbfs(ceilingDbfs)
        )
    }

    fun commitLimiterCeiling(ceilingDbfs: Double) {
        previewLimiterCeiling(ceilingDbfs)
        commitEditablePreferences()
    }

    fun cancelLimiterCeilingPreview(ceilingDbfs: Double) {
        cancelPreview(
            _state.value.editablePreferences
                .withLimiterCeilingDbfs(ceilingDbfs)
        )
    }

    fun resetLimiterMeters() {
        EqualizerRuntimeBridge.requestLimiterMeterReset()
    }

    fun applyBuiltInPreset(index: Int) {
        val preset = builtInEqualizerPresets[index]
        updatePreview(
            _state.value.editablePreferences
                .applyPreset(preset)
        )
        commitEditablePreferences()
    }

    fun applyUserPreset(presetId: String) {
        val preset = _state.value.userPresets
            .first { candidate -> candidate.id == presetId }
        updatePreview(
            _state.value.editablePreferences
                .applyPreset(preset)
        )
        commitEditablePreferences()
    }

    fun saveUserPreset(name: String) {
        val settled = _state.value.editablePreferences
        beginPendingCommit(settled)
        scope.launch {
            preferencesRepository.saveUserEqualizerPreset(
                name = name,
                curve = settled
            )
        }
    }

    fun renameUserPreset(
        presetId: String,
        name: String
    ) {
        scope.launch {
            preferencesRepository.renameUserEqualizerPreset(
                presetId,
                name
            )
        }
    }

    fun deleteUserPreset(presetId: String) {
        scope.launch {
            preferencesRepository.deleteUserEqualizerPreset(
                presetId
            )
        }
    }

    fun selectParametricFilter(filterId: String?) {
        if (filterId != null) {
            require(
                _state.value.editablePreferences
                    .parametricState.filters.any { filter ->
                        filter.id == filterId
                    }
            ) {
                "Unknown parametric filter ID: $filterId"
            }
        }
        _state.value = _state.value.copy(
            selectedParametricFilterId = filterId
        )
    }

    fun addParametricFilter() {
        val preferences = _state.value.editablePreferences
        val parametric = preferences.parametricState
        require(
            parametric.filters.size < MAX_PARAMETRIC_FILTER_COUNT
        ) {
            "Parametric filter limit reached"
        }
        val filter = ParametricFilterFactory.default()
        val updated = preferences.withParametricState(
            parametric.addFilter(filter)
        )
        updatePreview(updated)
        _state.value = _state.value.copy(
            selectedParametricFilterId = filter.id
        )
        commitEditablePreferences()
    }

    fun previewParametricFilter(filter: ParametricFilter) {
        val preferences = _state.value.editablePreferences
        updatePreview(
            preferences.withParametricState(
                preferences.parametricState.withFilter(filter)
            )
        )
        _state.value = _state.value.copy(
            selectedParametricFilterId = filter.id
        )
    }

    fun commitParametricFilter(filter: ParametricFilter) {
        previewParametricFilter(filter)
        commitEditablePreferences()
    }

    fun cancelParametricFilterPreview(
        original: ParametricFilter
    ) {
        val preferences = _state.value.editablePreferences
        cancelPreview(
            preferences.withParametricState(
                preferences.parametricState.withFilter(original)
            )
        )
        _state.value = _state.value.copy(
            selectedParametricFilterId = original.id
        )
    }

    fun moveParametricFilter(
        filterId: String,
        destinationIndex: Int
    ) {
        val preferences = _state.value.editablePreferences
        updatePreview(
            preferences.withParametricState(
                preferences.parametricState.moveFilter(
                    filterId,
                    destinationIndex
                )
            )
        )
        commitEditablePreferences()
    }

    fun deleteParametricFilter(filterId: String) {
        val preferences = _state.value.editablePreferences
        val filters = preferences.parametricState.filters
        val removedIndex =
            filters.indexOfFirst { filter -> filter.id == filterId }
        require(removedIndex >= 0) {
            "Unknown parametric filter ID: $filterId"
        }
        val updatedParametric =
            preferences.parametricState.removeFilter(filterId)
        updatePreview(
            preferences.withParametricState(updatedParametric)
        )
        val nextSelection = updatedParametric.filters
            .getOrNull(
                removedIndex.coerceAtMost(
                    updatedParametric.filters.lastIndex
                )
            )
            ?.id
        _state.value = _state.value.copy(
            selectedParametricFilterId = nextSelection
        )
        commitEditablePreferences()
    }

    fun applyParametricFlatPreset() {
        val preferences = _state.value.editablePreferences
        updatePreview(
            preferences.withParametricState(
                preferences.parametricState.flatCurve()
            )
        )
        _state.value = _state.value.copy(
            selectedParametricFilterId = null
        )
        commitEditablePreferences()
    }

    fun applyParametricUserPreset(presetId: String) {
        val preferences = _state.value.editablePreferences
        val parametric = preferences.parametricState
        val preset = parametric.userPresets.first { candidate ->
            candidate.id == presetId
        }
        val updated = parametric.applyPreset(preset)
        updatePreview(
            preferences.withParametricState(updated)
        )
        _state.value = _state.value.copy(
            selectedParametricFilterId =
                updated.filters.firstOrNull()?.id
        )
        commitEditablePreferences()
    }

    fun saveParametricUserPreset(name: String) {
        val settled = _state.value.editablePreferences
        beginPendingCommit(settled)
        scope.launch {
            preferencesRepository.saveParametricEqualizerPreset(
                name = name,
                curve = settled.parametricState
            )
        }
    }

    fun renameParametricUserPreset(
        presetId: String,
        name: String
    ) {
        scope.launch {
            preferencesRepository.renameParametricEqualizerPreset(
                presetId,
                name
            )
        }
    }

    fun deleteParametricUserPreset(presetId: String) {
        scope.launch {
            preferencesRepository.deleteParametricEqualizerPreset(
                presetId
            )
        }
    }

    fun openImportPreview(
        text: String,
        sourceName: String?
    ) {
        _state.value = _state.value.copy(
            importInProgress = true,
            importMessage = null
        )
        scope.launch {
            runCatching {
                withContext(Dispatchers.Default) {
                    if (
                        sourceName?.endsWith(
                            ".cdpeq",
                            ignoreCase = true
                        ) == true ||
                        sourceName?.endsWith(
                            ".json",
                            ignoreCase = true
                        ) == true ||
                        text.trimStart().startsWith("{")
                    ) {
                        EqualizerImportPreviewState.fromNative(
                            file = CdplayaPresetFileJson.decode(text),
                            sourceName = sourceName
                        )
                    } else {
                        EqualizerImportPreviewState.fromText(
                            EqualizerProfileParser.parse(
                                input = text,
                                sourceName = sourceName
                            )
                        )
                    }
                }
            }.fold(
                onSuccess = { preview ->
                    _state.value = _state.value.copy(
                        importPreview = preview,
                        importInProgress = false
                    )
                    refreshImportAnalysis(preview)
                },
                onFailure = { error ->
                    _state.value = _state.value.copy(
                        importInProgress = false,
                        importMessage =
                            error.message
                                ?: "Couldn't parse EQ profile."
                    )
                }
            )
        }
    }

    fun dismissImportPreview() {
        _state.value = _state.value.copy(
            importPreview = null,
            importMessage = null
        )
    }

    fun updateImportPreview(
        transform: (EqualizerImportPreviewState) ->
            EqualizerImportPreviewState
    ) {
        val current = _state.value.importPreview ?: return
        val updated = transform(current)
        _state.value = _state.value.copy(importPreview = updated)
        refreshImportAnalysis(updated)
    }

    fun replaceWithImportedProfile() {
        persistImportedProfile(
            presetName = null,
            apply = true
        )
    }

    fun saveImportedProfile(
        apply: Boolean
    ) {
        val preview = _state.value.importPreview ?: return
        persistImportedProfile(
            presetName = preview.proposedName,
            apply = apply
        )
    }

    private fun persistImportedProfile(
        presetName: String?,
        apply: Boolean
    ) {
        val preview = _state.value.importPreview ?: return
        require(preview.canApply) {
            "Import preview still has unresolved safety requirements."
        }
        val current = _state.value.editablePreferences
        val curve = preview.proposedParametricState(
            current.parametricState
        )
        val updated = if (apply) {
            current.copy(
                mode = EqualizerMode.PARAMETRIC,
                parametricState = curve
            )
        } else {
            current
        }
        _state.value = _state.value.copy(
            importInProgress = true,
            importMessage = null
        )
        scope.launch {
            runCatching {
                presetName?.let { name ->
                    ParametricEqualizerPresets.requireNameAvailable(
                        name = name,
                        userPresets = current.parametricState
                            .userPresets
                    )
                }
                preferencesRepository.importParametricEqualizerProfile(
                    curve = curve,
                    presetName = presetName,
                    apply = apply
                )
            }.fold(
                onSuccess = { createdPreset ->
                    val settled = if (
                        apply && createdPreset != null
                    ) {
                        updated.copy(
                            parametricState =
                                updated.parametricState.copy(
                                    userPresets =
                                        updated.parametricState
                                            .userPresets +
                                            createdPreset
                                )
                        )
                    } else {
                        updated
                    }
                    if (apply) beginPendingCommit(settled)
                    _state.value = _state.value.copy(
                        importPreview = null,
                        importInProgress = false,
                        importMessage = when {
                            presetName != null && apply ->
                                "Preset saved and applied."
                            presetName != null -> "Preset saved."
                            else -> "Imported profile applied."
                        },
                        selectedParametricFilterId =
                            if (apply) {
                                curve.filters.firstOrNull()?.id
                            } else {
                                _state.value
                                    .selectedParametricFilterId
                            }
                    )
                },
                onFailure = { error ->
                    _state.value = _state.value.copy(
                        importInProgress = false,
                        importMessage =
                            error.message
                                ?: "Couldn't save imported profile."
                    )
                }
            )
        }
    }

    private fun refreshImportAnalysis(
        preview: EqualizerImportPreviewState
    ) {
        val durable = _state.value.editablePreferences
        scope.launch {
            val analysis = withContext(Dispatchers.Default) {
                EqualizerAnalysisCalculator.calculate(
                    EqualizerAnalysisRequest(
                        preferences =
                            preview.proposedPreferences(durable),
                        currentSampleRateHz = if (
                            preview.previewAtCurrentTrackRate
                        ) {
                            runtimeState.value.sampleRateHz
                        } else {
                            null
                        }
                    )
                )
            }
            if (_state.value.importPreview == preview) {
                _state.value = _state.value.copy(
                    importAnalysis = analysis
                )
            }
        }
    }

    fun resetToFlat() {
        val preferences = _state.value.editablePreferences
        updatePreview(
            when (preferences.mode) {
                EqualizerMode.GRAPHIC -> preferences.flatCurve()
                EqualizerMode.PARAMETRIC ->
                    preferences.withParametricState(
                        preferences.parametricState.flatCurve()
                    )
            }
        )
        if (preferences.mode == EqualizerMode.PARAMETRIC) {
            _state.value = _state.value.copy(
                selectedParametricFilterId = null
            )
        }
        commitEditablePreferences()
    }

    fun setComparisonBypassed(bypassed: Boolean) {
        val current = _state.value
        if (!current.comparisonAvailable) return
        EqualizerRuntimeBridge.setComparisonState(
            sessionActive = true,
            bypassed = bypassed
        )
        requestRuntime(
            preferences = current.editablePreferences,
            enabledOverride = if (bypassed) {
                false
            } else {
                current.editablePreferences.enabled
            }
        )
        _state.value = current.copy(
            comparisonBypassed = bypassed
        )
    }

    fun closeScreen() {
        if (hasPreviewEdits) {
            commitEditablePreferences()
        }
        requestRuntime(_state.value.editablePreferences)
        EqualizerRuntimeBridge.setComparisonState(
            sessionActive = false,
            bypassed = false
        )
        _state.value = _state.value.copy(
            comparisonBypassed = false
        )
    }

    fun release() {
        closeScreen()
        stateCollectionJob?.cancel()
        stateCollectionJob = null
        analysisController.release()
    }

    private fun updatePreview(
        updated: EqualizerPreferencesState,
        markDirty: Boolean = true
    ) {
        hasPreviewEdits = hasPreviewEdits || markDirty
        _state.value = _state.value.copy(
            editablePreferences = updated,
            presetMatch = presetMatchFor(updated),
            comparisonBypassed = false,
            hasUncommittedPreview = hasPreviewEdits
        )
        requestRuntime(updated)
        EqualizerRuntimeBridge.setComparisonState(
            sessionActive = false,
            bypassed = false
        )
        analysisController.submit(
            preferences = updated,
            currentSampleRateHz =
                runtimeState.value.sampleRateHz
        )
    }

    private fun commitEditablePreferences() {
        val settled = _state.value.editablePreferences
        beginPendingCommit(settled)
        scope.launch {
            preferencesRepository.replaceEqualizerPreferences(
                settled
            )
        }
    }

    private fun beginPendingCommit(
        settled: EqualizerPreferencesState
    ) {
        hasPreviewEdits = false
        pendingCommit = settled
        _state.value = _state.value.copy(
            hasUncommittedPreview = false
        )
        requestRuntime(settled)
    }

    private fun cancelPreview(
        restored: EqualizerPreferencesState
    ) {
        hasPreviewEdits = false
        _state.value = _state.value.copy(
            editablePreferences = restored,
            presetMatch = presetMatchFor(restored),
            hasUncommittedPreview = false
        )
        requestRuntime(restored)
        analysisController.submit(
            preferences = restored,
            currentSampleRateHz =
                runtimeState.value.sampleRateHz
        )
    }

    private fun requestRuntime(
        preferences: EqualizerPreferencesState,
        enabledOverride: Boolean = preferences.enabled
    ) {
        EqualizerRuntimeBridge.requestConfiguration(
            configuration = preferences.toDspConfiguration(
                enabledOverride = enabledOverride
            ),
            automaticHeadroomEnabled =
                preferences.activeAutomaticHeadroomEnabled,
            mode = preferences.mode,
            limiterConfiguration = LimiterConfiguration(
                enabled = preferences.limiterEnabled,
                ceilingDbfs = preferences.limiterCeilingDbfs
            )
        )
    }
}

private fun EqualizerPreferencesState
    .hasSameConfigurationAs(
        other: EqualizerPreferencesState
    ): Boolean {
    return enabled == other.enabled &&
        preampDb.toBits() == other.preampDb.toBits() &&
        automaticHeadroomEnabled ==
            other.automaticHeadroomEnabled &&
            bandGainsDb == other.bandGainsDb &&
        mode == other.mode &&
        parametricState == other.parametricState &&
        limiterEnabled == other.limiterEnabled &&
        limiterCeilingDbfs.toBits() ==
            other.limiterCeilingDbfs.toBits()
}

private fun EqualizerPreferencesState.withDurablePresetLists(
    durable: EqualizerPreferencesState
): EqualizerPreferencesState = copy(
    userPresets = durable.userPresets,
    parametricState = parametricState.copy(
        userPresets = durable.parametricState.userPresets
    )
)
