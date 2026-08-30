package io.github.rsgarrido.sazanami.ui.equalizer

import io.github.rsgarrido.sazanami.player.equalizer.EqualizerMode
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerPreferencesState
import io.github.rsgarrido.sazanami.player.equalizer.interchange.CdplayaPresetFile
import io.github.rsgarrido.sazanami.player.equalizer.interchange.EqualizerProfileDiagnosticCode
import io.github.rsgarrido.sazanami.player.equalizer.interchange.EqualizerProfileDiagnosticSeverity
import io.github.rsgarrido.sazanami.player.equalizer.interchange.EqualizerProfileFormat
import io.github.rsgarrido.sazanami.player.equalizer.interchange.EqualizerProfileParseResult
import io.github.rsgarrido.sazanami.player.equalizer.interchange.ImportedFilterDeclaration
import io.github.rsgarrido.sazanami.player.equalizer.interchange.ImportedFilterStatus
import io.github.rsgarrido.sazanami.player.equalizer.parametric.ParametricEqualizerState
import io.github.rsgarrido.sazanami.player.equalizer.parametric.ParametricFilter
import java.util.Locale

internal data class EqualizerImportPreviewState(
    val parseResult: EqualizerProfileParseResult,
    val proposedName: String,
    val automaticHeadroomEnabled: Boolean,
    val selectedSourceLines: Set<Int>,
    val supportedOnlyOverrideConfirmed: Boolean = false,
    val unsupportedExclusionConfirmed: Boolean = false,
    val unrecognizedTextConfirmed: Boolean = false,
    val flatImportConfirmed: Boolean = false,
    val previewAtCurrentTrackRate: Boolean = false,
    val isNative: Boolean = false
) {
    val selectedDeclarations: List<ImportedFilterDeclaration>
        get() = parseResult.declarations.filter {
            it.sourceLineNumber in selectedSourceLines &&
                it.status == ImportedFilterStatus.VALID &&
                it.mappedFilter != null
        }

    val selectedFilters: List<ParametricFilter>
        get() = selectedDeclarations.mapNotNull { it.mappedFilter }

    val validDeclarationCount: Int
        get() = parseResult.declarations.count {
            it.status == ImportedFilterStatus.VALID
        }

    val hasUnsupportedDeclarations: Boolean
        get() = parseResult.declarations.any {
            it.status == ImportedFilterStatus.UNSUPPORTED
        }

    val hasUnrecognizedText: Boolean
        get() = parseResult.allDiagnostics.any {
            it.code == EqualizerProfileDiagnosticCode.UNRECOGNIZED_TEXT
        }

    val hasOverrideableSemanticBlocks: Boolean
        get() = parseResult.diagnostics.any {
            it.severity ==
                EqualizerProfileDiagnosticSeverity.BLOCKING &&
                it.code in overrideableSemanticCodes
        }

    val hasNonOverrideableBlocks: Boolean
        get() =
            parseResult.declarations.any { declaration ->
                declaration.diagnostics.any {
                    it.severity ==
                        EqualizerProfileDiagnosticSeverity.BLOCKING
                }
            } ||
                parseResult.diagnostics.any {
                    it.severity ==
                        EqualizerProfileDiagnosticSeverity.BLOCKING &&
                        it.code !in overrideableSemanticCodes
                }

    val canApply: Boolean
        get() = !hasNonOverrideableBlocks &&
            selectedFilters.size <= 10 &&
            (!hasOverrideableSemanticBlocks ||
                supportedOnlyOverrideConfirmed) &&
            (!hasUnsupportedDeclarations ||
                unsupportedExclusionConfirmed) &&
            (!hasUnrecognizedText || unrecognizedTextConfirmed) &&
            (
                selectedFilters.isNotEmpty() ||
                    flatImportConfirmed &&
                    (parseResult.preampDb ?: 0.0) == 0.0
                )

    fun toggleSelection(lineNumber: Int): EqualizerImportPreviewState {
        val declaration = parseResult.declarations.first {
            it.sourceLineNumber == lineNumber
        }
        if (
            declaration.status != ImportedFilterStatus.VALID ||
            declaration.mappedFilter == null
        ) {
            return this
        }
        val next = selectedSourceLines.toMutableSet()
        if (!next.add(lineNumber)) {
            next.remove(lineNumber)
        } else {
            require(next.size <= 10) {
                "Sazanami supports at most ten selected filters."
            }
        }
        return copy(
            selectedSourceLines = next.toSet(),
            flatImportConfirmed = false
        )
    }

    fun selectFirstTen(): EqualizerImportPreviewState = copy(
        selectedSourceLines = parseResult.declarations
            .asSequence()
            .filter {
                it.status == ImportedFilterStatus.VALID &&
                    it.mappedFilter != null
            }
            .take(10)
            .map { it.sourceLineNumber }
            .toSet(),
        flatImportConfirmed = false
    )

    fun clearSelection(): EqualizerImportPreviewState = copy(
        selectedSourceLines = emptySet(),
        flatImportConfirmed = false
    )

    fun replaceDeclaration(
        lineNumber: Int,
        filter: ParametricFilter
    ): EqualizerImportPreviewState {
        val updated = parseResult.declarations.map { declaration ->
            if (declaration.sourceLineNumber == lineNumber) {
                declaration.copy(
                    externalTypeToken = filter.type.name,
                    enabled = filter.enabled,
                    frequencyHz = filter.frequencyHz,
                    mappedFilter = filter,
                    status = ImportedFilterStatus.VALID,
                    diagnostics = emptyList()
                )
            } else {
                declaration
            }
        }
        val nextSelection = selectedSourceLines.toMutableSet()
        if (nextSelection.size < 10) nextSelection += lineNumber
        return copy(
            parseResult = parseResult.copy(declarations = updated),
            selectedSourceLines = nextSelection.toSet()
        )
    }

    fun proposedParametricState(
        durable: ParametricEqualizerState
    ): ParametricEqualizerState = ParametricEqualizerState(
        preampDb = if (
            supportedOnlyOverrideConfirmed &&
            hasOverrideableSemanticBlocks
        ) {
            0.0
        } else {
            parseResult.preampDb ?: 0.0
        },
        automaticHeadroomEnabled = automaticHeadroomEnabled,
        filters = selectedFilters,
        userPresets = durable.userPresets
    )

    fun proposedPreferences(
        durable: EqualizerPreferencesState
    ): EqualizerPreferencesState = durable.copy(
        mode = EqualizerMode.PARAMETRIC,
        parametricState = proposedParametricState(
            durable.parametricState
        )
    )

    companion object {
        fun fromText(
            result: EqualizerProfileParseResult
        ): EqualizerImportPreviewState {
            val valid = result.declarations.filter {
                it.status == ImportedFilterStatus.VALID &&
                    it.mappedFilter != null
            }
            return EqualizerImportPreviewState(
                parseResult = result,
                proposedName = proposedImportName(
                    result.sourceName,
                    null
                ),
                automaticHeadroomEnabled = true,
                selectedSourceLines = if (valid.size <= 10) {
                    valid.map { it.sourceLineNumber }.toSet()
                } else {
                    emptySet()
                }
            )
        }

        fun fromNative(
            file: CdplayaPresetFile,
            sourceName: String?
        ): EqualizerImportPreviewState {
            val declarations = file.filters.mapIndexed { index, filter ->
                ImportedFilterDeclaration(
                    sourceLineNumber = index + 1,
                    originalText = "Native filter ${index + 1}",
                    sourceFilterNumber = index + 1,
                    externalTypeToken = filter.type.name,
                    enabled = filter.enabled,
                    frequencyHz = filter.frequencyHz,
                    gainDb = null,
                    q = null,
                    mappedFilter = filter,
                    status = ImportedFilterStatus.VALID
                )
            }
            return EqualizerImportPreviewState(
                parseResult = EqualizerProfileParseResult(
                    detectedFormat =
                        EqualizerProfileFormat
                            .CDPLAYA_PARAMETRIC_PRESET_JSON,
                    sourceName = sourceName,
                    preampDb = file.preampDb,
                    declarations = declarations
                ),
                proposedName = proposedImportName(
                    sourceName,
                    file.name
                ),
                automaticHeadroomEnabled =
                    file.automaticHeadroomEnabled,
                selectedSourceLines =
                    declarations.map { it.sourceLineNumber }.toSet(),
                isNative = true
            )
        }
    }
}

internal fun proposedImportName(
    sourceName: String?,
    nativeName: String?
): String {
    if (!nativeName.isNullOrBlank()) return nativeName.trim().take(40)
    var name = sourceName?.trim().orEmpty()
    val suffixes = listOf(
        ".cdpeq", ".json", ".txt", ".peq", ".eq",
        " ParametricEQ", " Parametric EQ", " FixedBandEQ",
        "ParametricEQ", "Parametric EQ", "FixedBandEQ"
    )
    var changed: Boolean
    do {
        changed = false
        suffixes.firstOrNull {
            name.lowercase(Locale.ROOT)
                .endsWith(it.lowercase(Locale.ROOT))
        }?.let {
            name = name.dropLast(it.length).trim()
            changed = true
        }
    } while (changed)
    return name.ifBlank { "Imported EQ" }.take(40)
}

private val overrideableSemanticCodes = setOf(
    EqualizerProfileDiagnosticCode.UNSUPPORTED_COMMAND,
    EqualizerProfileDiagnosticCode.UNKNOWN_COMMAND
)
