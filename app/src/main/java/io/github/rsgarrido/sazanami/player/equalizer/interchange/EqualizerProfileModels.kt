package io.github.rsgarrido.sazanami.player.equalizer.interchange

import io.github.rsgarrido.sazanami.player.equalizer.parametric.ParametricFilter

enum class EqualizerProfileFormat {
    AUTOEQ_PARAMETRIC_TEXT,
    EQUALIZER_APO_SUBSET,
    CDPLAYA_PARAMETRIC_PRESET_JSON
}

enum class EqualizerProfileDiagnosticSeverity {
    INFO,
    WARNING,
    ERROR,
    BLOCKING
}

enum class EqualizerProfileDiagnosticCode {
    DECIMAL_COMMA_NORMALIZED,
    MULTIPLE_PREAMPS_COMBINED,
    UNSUPPORTED_COMMAND,
    UNSUPPORTED_FILTER_TYPE,
    MISSING_PARAMETER,
    DUPLICATE_PARAMETER,
    OUT_OF_RANGE,
    FILTER_LIMIT_EXCEEDED,
    UNKNOWN_COMMAND,
    MALFORMED_FILTER,
    SHELF_ROUNDING_NORMALIZED,
    UNRECOGNIZED_TEXT,
    INPUT_TOO_LARGE,
    TOO_MANY_LINES,
    LINE_TOO_LONG,
    TOO_MANY_DECLARATIONS,
    NUL_CHARACTER,
    INVALID_UNIT,
    EXTRA_PARAMETER,
    INVALID_NUMBER,
    INVALID_PREAMP,
    INVALID_NATIVE_FILE,
    UNSUPPORTED_NATIVE_VERSION
}

data class EqualizerProfileDiagnostic(
    val code: EqualizerProfileDiagnosticCode,
    val severity: EqualizerProfileDiagnosticSeverity,
    val lineNumber: Int? = null,
    val message: String
)

enum class ImportedFilterStatus {
    VALID,
    INVALID,
    UNSUPPORTED
}

data class ImportedFilterDeclaration(
    val sourceLineNumber: Int,
    val originalText: String,
    val sourceFilterNumber: Int?,
    val externalTypeToken: String?,
    val enabled: Boolean?,
    val frequencyHz: Double?,
    val gainDb: Double?,
    val q: Double?,
    val mappedFilter: ParametricFilter?,
    val status: ImportedFilterStatus,
    val diagnostics: List<EqualizerProfileDiagnostic> = emptyList()
)

data class EqualizerProfileParseResult(
    val detectedFormat: EqualizerProfileFormat,
    val sourceName: String? = null,
    val preampDb: Double? = null,
    val declarations: List<ImportedFilterDeclaration> = emptyList(),
    val diagnostics: List<EqualizerProfileDiagnostic> = emptyList()
) {
    val hasBlockingDiagnostics: Boolean
        get() = diagnostics.any {
            it.severity == EqualizerProfileDiagnosticSeverity.BLOCKING
        } || declarations.any { declaration ->
            declaration.diagnostics.any {
                it.severity ==
                    EqualizerProfileDiagnosticSeverity.BLOCKING
            }
        }

    val warningCount: Int
        get() = allDiagnostics.count {
            it.severity == EqualizerProfileDiagnosticSeverity.WARNING
        }

    val errorCount: Int
        get() = allDiagnostics.count {
            it.severity == EqualizerProfileDiagnosticSeverity.ERROR ||
                it.severity ==
                EqualizerProfileDiagnosticSeverity.BLOCKING
        }

    val allDiagnostics: List<EqualizerProfileDiagnostic>
        get() = diagnostics +
            declarations.flatMap { declaration ->
                declaration.diagnostics
            }
}

object EqualizerProfileLimits {
    const val MAX_INPUT_BYTES = 256 * 1024
    const val MAX_LOGICAL_LINES = 2_000
    const val MAX_LINE_LENGTH = 4_096
    const val MAX_FILTER_DECLARATIONS = 256
    const val MAX_IMPORTED_FILTERS = 10
}
