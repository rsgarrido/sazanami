package io.github.rsgarrido.sazanami.player.equalizer.interchange

import io.github.rsgarrido.sazanami.player.equalizer.parametric.ParametricFilter
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID

object EqualizerProfileParser {
    private val filterPrefix =
        Regex("^\\s*filter(?:\\s+(\\d+))?\\s*:\\s*(.*)$",
            RegexOption.IGNORE_CASE)
    private val filterBody =
        Regex("^(ON|OFF)\\s+(\\S+)\\s*(.*)$",
            RegexOption.IGNORE_CASE)
    private val preampLine =
        Regex("^\\s*preamp\\s*:\\s*(\\S+)\\s+(\\S+)\\s*$",
            RegexOption.IGNORE_CASE)
    private val commandLine =
        Regex("^\\s*([A-Za-z][A-Za-z0-9 ]*)\\s*:(.*)$")
    private val numberPattern =
        Regex("^[+-]?(?:\\d+(?:[.,]\\d+)?|\\.\\d+)$")
    private val variableAssignment =
        Regex("^\\s*[A-Za-z_][A-Za-z0-9_]*\\s*=.*$")
    private val blockedCommands = setOf(
        "include", "device", "channel", "stage", "graphiceq",
        "convolution", "delay", "copy", "if", "elseif", "else",
        "endif", "eval", "expression", "iir"
    )
    private val supportedTypes =
        setOf("PK", "PEQ", "LSC", "HSC", "LPQ", "HPQ", "BP", "NO")
    private val unsupportedTypes =
        setOf("LS", "HS", "LP", "HP", "AP", "MODAL", "IIR")

    fun parse(
        input: String,
        sourceName: String? = null,
        idFactory: () -> String = { UUID.randomUUID().toString() }
    ): EqualizerProfileParseResult {
        val early = validateInput(input, sourceName)
        if (early != null) return early
        val text = input.removePrefix("\uFEFF")
        val lines = text.split(Regex("\\r\\n|\\n|\\r"))
        val declarations = mutableListOf<ImportedFilterDeclaration>()
        val diagnostics = mutableListOf<EqualizerProfileDiagnostic>()
        val preamps = mutableListOf<Double>()
        var decimalCommaSeen = false
        var apoSemanticsSeen = false
        var blockingSemanticsSeen = false

        lines.forEachIndexed { index, original ->
            val lineNumber = index + 1
            val trimmed = original.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachIndexed

            preampLine.matchEntire(original)?.let { match ->
                val number = parseNumber(match.groupValues[1])
                val unit = match.groupValues[2]
                if (number == null || !unit.equals("dB", true)) {
                    diagnostics += diagnostic(
                        EqualizerProfileDiagnosticCode.INVALID_PREAMP,
                        EqualizerProfileDiagnosticSeverity.BLOCKING,
                        lineNumber,
                        "Malformed Preamp declaration."
                    )
                } else {
                    preamps += number.value
                    decimalCommaSeen =
                        decimalCommaSeen || number.decimalCommaNormalized
                }
                return@forEachIndexed
            }

            filterPrefix.matchEntire(original)?.let { match ->
                if (
                    declarations.size >=
                    EqualizerProfileLimits.MAX_FILTER_DECLARATIONS
                ) {
                    diagnostics += diagnostic(
                        EqualizerProfileDiagnosticCode.TOO_MANY_DECLARATIONS,
                        EqualizerProfileDiagnosticSeverity.BLOCKING,
                        lineNumber,
                        "The profile contains more than 256 filter declarations."
                    )
                    return@forEachIndexed
                }
                val sourceNumberText =
                    match.groupValues[1].takeIf { it.isNotEmpty() }
                val sourceNumber = sourceNumberText?.toIntOrNull()
                if (
                    sourceNumberText != null &&
                    sourceNumber == null
                ) {
                    declarations += invalidDeclaration(
                        line = lineNumber,
                        original = original,
                        sourceNumber = null,
                        code = EqualizerProfileDiagnosticCode
                            .MALFORMED_FILTER,
                        message = "Filter number is too large."
                    ).declaration
                    return@forEachIndexed
                }
                val body = match.groupValues[2]
                val parsed = parseFilter(
                    lineNumber = lineNumber,
                    original = original,
                    sourceNumber = sourceNumber,
                    body = body,
                    idFactory = idFactory
                )
                declarations += parsed.declaration
                decimalCommaSeen =
                    decimalCommaSeen || parsed.decimalCommaNormalized
                return@forEachIndexed
            }
            if (trimmed.startsWith("filter", ignoreCase = true)) {
                declarations += invalidDeclaration(
                    line = lineNumber,
                    original = original,
                    sourceNumber = null,
                    code =
                        EqualizerProfileDiagnosticCode.MALFORMED_FILTER,
                    message =
                        "Malformed Filter declaration; a colon and a " +
                            "complete supported filter are required."
                ).declaration
                return@forEachIndexed
            }

            val command = commandLine.matchEntire(original)
            if (command != null) {
                val name = command.groupValues[1]
                    .trim().lowercase(Locale.ROOT)
                apoSemanticsSeen = true
                diagnostics += if (name in blockedCommands) {
                    blockingSemanticsSeen = true
                    diagnostic(
                        EqualizerProfileDiagnosticCode.UNSUPPORTED_COMMAND,
                        EqualizerProfileDiagnosticSeverity.BLOCKING,
                        lineNumber,
                        "Unsupported Equalizer APO command '${command.groupValues[1].trim()}'."
                    )
                } else {
                    blockingSemanticsSeen = true
                    diagnostic(
                        EqualizerProfileDiagnosticCode.UNKNOWN_COMMAND,
                        EqualizerProfileDiagnosticSeverity.BLOCKING,
                        lineNumber,
                        "Unknown command '${command.groupValues[1].trim()}'."
                    )
                }
            } else if (variableAssignment.matches(original)) {
                apoSemanticsSeen = true
                blockingSemanticsSeen = true
                diagnostics += diagnostic(
                    EqualizerProfileDiagnosticCode.UNSUPPORTED_COMMAND,
                    EqualizerProfileDiagnosticSeverity.BLOCKING,
                    lineNumber,
                    "Variable assignments and expressions are unsupported."
                )
            } else {
                diagnostics += diagnostic(
                    EqualizerProfileDiagnosticCode.UNRECOGNIZED_TEXT,
                    EqualizerProfileDiagnosticSeverity.WARNING,
                    lineNumber,
                    "Unrecognized non-comment text."
                )
            }
        }

        if (decimalCommaSeen) {
            diagnostics += diagnostic(
                EqualizerProfileDiagnosticCode.DECIMAL_COMMA_NORMALIZED,
                EqualizerProfileDiagnosticSeverity.WARNING,
                null,
                "Unambiguous decimal commas were normalized."
            )
        }
        if (preamps.size > 1 && !blockingSemanticsSeen) {
            diagnostics += diagnostic(
                EqualizerProfileDiagnosticCode.MULTIPLE_PREAMPS_COMBINED,
                EqualizerProfileDiagnosticSeverity.INFO,
                null,
                "Combined ${preamps.size} global preamp lines."
            )
        }
        val preamp = preamps
            .takeIf {
                it.isNotEmpty() && !blockingSemanticsSeen
            }
            ?.sum()
        if (preamp != null && (!preamp.isFinite() || preamp !in -15.0..6.0)) {
            diagnostics += diagnostic(
                EqualizerProfileDiagnosticCode.OUT_OF_RANGE,
                EqualizerProfileDiagnosticSeverity.BLOCKING,
                null,
                "Combined preamp is outside Sazanami's -15.0 to +6.0 dB range."
            )
        }
        val validCount = declarations.count {
            it.status == ImportedFilterStatus.VALID
        }
        if (validCount > EqualizerProfileLimits.MAX_IMPORTED_FILTERS) {
            diagnostics += diagnostic(
                EqualizerProfileDiagnosticCode.FILTER_LIMIT_EXCEEDED,
                EqualizerProfileDiagnosticSeverity.WARNING,
                null,
                "The profile has $validCount supported filters; select at most 10."
            )
        }
        return EqualizerProfileParseResult(
            detectedFormat = if (apoSemanticsSeen) {
                EqualizerProfileFormat.EQUALIZER_APO_SUBSET
            } else {
                EqualizerProfileFormat.AUTOEQ_PARAMETRIC_TEXT
            },
            sourceName = sourceName,
            preampDb = preamp,
            declarations = declarations.toList(),
            diagnostics = diagnostics.toList()
        )
    }

    private fun parseFilter(
        lineNumber: Int,
        original: String,
        sourceNumber: Int?,
        body: String,
        idFactory: () -> String
    ): ParsedDeclaration {
        val bodyMatch = filterBody.matchEntire(body.trim())
            ?: return invalidDeclaration(
                lineNumber, original, sourceNumber,
                EqualizerProfileDiagnosticCode.MALFORMED_FILTER,
                "Filter must specify ON or OFF, a type, and complete parameters."
            )
        val enabled = bodyMatch.groupValues[1].equals("ON", true)
        val type = bodyMatch.groupValues[2].uppercase(Locale.ROOT)
        if (type !in supportedTypes) {
            val message = if (
                type in unsupportedTypes ||
                type.startsWith("LS") ||
                type.startsWith("HS")
            ) {
                "Unsupported filter type '$type'; it was not approximated."
            } else {
                "Unknown filter type '$type'."
            }
            return ParsedDeclaration(
                ImportedFilterDeclaration(
                    sourceLineNumber = lineNumber,
                    originalText = original,
                    sourceFilterNumber = sourceNumber,
                    externalTypeToken = type,
                    enabled = enabled,
                    frequencyHz = null,
                    gainDb = null,
                    q = null,
                    mappedFilter = null,
                    status = ImportedFilterStatus.UNSUPPORTED,
                    diagnostics = listOf(
                        diagnostic(
                            EqualizerProfileDiagnosticCode.UNSUPPORTED_FILTER_TYPE,
                            EqualizerProfileDiagnosticSeverity.ERROR,
                            lineNumber,
                            message
                        )
                    )
                )
            )
        }
        if (
            bodyMatch.groupValues[3]
                .split(Regex("\\s+"))
                .any {
                    it.equals("BW", true) ||
                        it.equals("OCT", true)
                }
        ) {
            return ParsedDeclaration(
                ImportedFilterDeclaration(
                    sourceLineNumber = lineNumber,
                    originalText = original,
                    sourceFilterNumber = sourceNumber,
                    externalTypeToken = type,
                    enabled = enabled,
                    frequencyHz = null,
                    gainDb = null,
                    q = null,
                    mappedFilter = null,
                    status = ImportedFilterStatus.UNSUPPORTED,
                    diagnostics = listOf(
                        diagnostic(
                            EqualizerProfileDiagnosticCode
                                .UNSUPPORTED_FILTER_TYPE,
                            EqualizerProfileDiagnosticSeverity.ERROR,
                            lineNumber,
                            "Bandwidth-in-octaves filters are unsupported."
                        )
                    )
                )
            )
        }
        val tokens = bodyMatch.groupValues[3]
            .trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val values = linkedMapOf<String, ParsedNumber>()
        var cursor = 0
        var comma = false
        fun fail(
            code: EqualizerProfileDiagnosticCode,
            message: String
        ) = invalidDeclaration(
            lineNumber, original, sourceNumber, code, message,
            type, enabled
        )
        while (cursor < tokens.size) {
            val key = tokens[cursor].uppercase(Locale.ROOT)
            if (key !in setOf("FC", "GAIN", "Q")) {
                return fail(
                    EqualizerProfileDiagnosticCode.EXTRA_PARAMETER,
                    "Unexpected token '${tokens[cursor]}' in supported filter."
                )
            }
            if (values.containsKey(key)) {
                return fail(
                    EqualizerProfileDiagnosticCode.DUPLICATE_PARAMETER,
                    "Duplicate $key parameter."
                )
            }
            if (cursor + 1 >= tokens.size) {
                return fail(
                    EqualizerProfileDiagnosticCode.MISSING_PARAMETER,
                    "Missing numeric value after $key."
                )
            }
            val number = parseNumber(tokens[cursor + 1])
                ?: return fail(
                    EqualizerProfileDiagnosticCode.INVALID_NUMBER,
                    "Invalid numeric value for $key."
                )
            comma = comma || number.decimalCommaNormalized
            val unitRequired = key == "FC" || key == "GAIN"
            if (unitRequired) {
                if (cursor + 2 >= tokens.size) {
                    return fail(
                        EqualizerProfileDiagnosticCode.INVALID_UNIT,
                        "Missing ${if (key == "FC") "Hz" else "dB"} unit."
                    )
                }
                val expected = if (key == "FC") "Hz" else "dB"
                if (!tokens[cursor + 2].equals(expected, true)) {
                    return fail(
                        EqualizerProfileDiagnosticCode.INVALID_UNIT,
                        "Expected $expected after $key."
                    )
                }
            }
            values[key] = number
            cursor += if (unitRequired) 3 else 2
        }
        val gainBearing = type in setOf("PK", "PEQ", "LSC", "HSC")
        val required = if (gainBearing) {
            setOf("FC", "GAIN", "Q")
        } else {
            setOf("FC", "Q")
        }
        val missing = required - values.keys
        if (missing.isNotEmpty()) {
            return fail(
                EqualizerProfileDiagnosticCode.MISSING_PARAMETER,
                "Missing required parameter(s): ${missing.joinToString()}."
            )
        }
        if (!gainBearing && "GAIN" in values) {
            return fail(
                EqualizerProfileDiagnosticCode.EXTRA_PARAMETER,
                "Gain is invalid for $type filters."
            )
        }
        val frequency = values.getValue("FC").value
        val gain = values["GAIN"]?.value
        val q = values.getValue("Q").value
        val mappingDiagnostics = mutableListOf<EqualizerProfileDiagnostic>()
        val mapped = try {
            when (type) {
                "PK", "PEQ" -> ParametricFilter.Peaking(
                    idFactory(), enabled, frequency, gain!!, q
                )
                "LSC", "HSC" -> {
                    val conversion =
                        ShelfParameterConversion.qToSlope(gain!!, q)
                    if (conversion.boundaryNormalized) {
                        mappingDiagnostics += diagnostic(
                            EqualizerProfileDiagnosticCode.SHELF_ROUNDING_NORMALIZED,
                            EqualizerProfileDiagnosticSeverity.WARNING,
                            lineNumber,
                            "Shelf rounding was normalized to S 1.00."
                        )
                    }
                    if (type == "LSC") {
                        ParametricFilter.LowShelf(
                            idFactory(), enabled, frequency, gain,
                            conversion.value
                        )
                    } else {
                        ParametricFilter.HighShelf(
                            idFactory(), enabled, frequency, gain,
                            conversion.value
                        )
                    }
                }
                "LPQ" -> ParametricFilter.LowPass(
                    idFactory(), enabled, frequency, q
                )
                "HPQ" -> ParametricFilter.HighPass(
                    idFactory(), enabled, frequency, q
                )
                "BP" -> ParametricFilter.BandPass(
                    idFactory(), enabled, frequency, q
                )
                else -> ParametricFilter.Notch(
                    idFactory(), enabled, frequency, q
                )
            }
        } catch (error: IllegalArgumentException) {
            return fail(
                EqualizerProfileDiagnosticCode.OUT_OF_RANGE,
                error.message ?: "Imported filter value is out of range."
            )
        }
        return ParsedDeclaration(
            declaration = ImportedFilterDeclaration(
                sourceLineNumber = lineNumber,
                originalText = original,
                sourceFilterNumber = sourceNumber,
                externalTypeToken = type,
                enabled = enabled,
                frequencyHz = frequency,
                gainDb = gain,
                q = q,
                mappedFilter = mapped,
                status = ImportedFilterStatus.VALID,
                diagnostics = mappingDiagnostics
            ),
            decimalCommaNormalized = comma
        )
    }

    private fun validateInput(
        input: String,
        sourceName: String?
    ): EqualizerProfileParseResult? {
        val error = when {
            input.toByteArray(StandardCharsets.UTF_8).size >
                EqualizerProfileLimits.MAX_INPUT_BYTES ->
                EqualizerProfileDiagnosticCode.INPUT_TOO_LARGE to
                    "EQ text exceeds the 256 KiB limit."
            '\u0000' in input ->
                EqualizerProfileDiagnosticCode.NUL_CHARACTER to
                    "EQ text contains a NUL character."
            else -> {
                val lines = input.removePrefix("\uFEFF")
                    .split(Regex("\\r\\n|\\n|\\r"))
                when {
                    lines.size > EqualizerProfileLimits.MAX_LOGICAL_LINES ->
                        EqualizerProfileDiagnosticCode.TOO_MANY_LINES to
                            "EQ text exceeds the 2,000-line limit."
                    lines.any {
                        it.length > EqualizerProfileLimits.MAX_LINE_LENGTH
                    } ->
                        EqualizerProfileDiagnosticCode.LINE_TOO_LONG to
                            "An EQ line exceeds the 4,096-character limit."
                    else -> null
                }
            }
        } ?: return null
        return EqualizerProfileParseResult(
            detectedFormat =
                EqualizerProfileFormat.AUTOEQ_PARAMETRIC_TEXT,
            sourceName = sourceName,
            diagnostics = listOf(
                diagnostic(
                    error.first,
                    EqualizerProfileDiagnosticSeverity.BLOCKING,
                    null,
                    error.second
                )
            )
        )
    }

    private fun parseNumber(token: String): ParsedNumber? {
        if (!numberPattern.matches(token)) return null
        if (
            token.matches(Regex("^\\+?\\d{1,3},\\d{3}$"))
        ) {
            return null
        }
        val comma = ',' in token
        val value = token.replace(',', '.').toDoubleOrNull()
            ?: return null
        if (!value.isFinite()) return null
        return ParsedNumber(value, comma)
    }

    private fun invalidDeclaration(
        line: Int,
        original: String,
        sourceNumber: Int?,
        code: EqualizerProfileDiagnosticCode,
        message: String,
        type: String? = null,
        enabled: Boolean? = null
    ): ParsedDeclaration = ParsedDeclaration(
        ImportedFilterDeclaration(
            sourceLineNumber = line,
            originalText = original,
            sourceFilterNumber = sourceNumber,
            externalTypeToken = type,
            enabled = enabled,
            frequencyHz = null,
            gainDb = null,
            q = null,
            mappedFilter = null,
            status = ImportedFilterStatus.INVALID,
            diagnostics = listOf(
                diagnostic(
                    code,
                    EqualizerProfileDiagnosticSeverity.BLOCKING,
                    line,
                    message
                )
            )
        )
    )

    private fun diagnostic(
        code: EqualizerProfileDiagnosticCode,
        severity: EqualizerProfileDiagnosticSeverity,
        line: Int?,
        message: String
    ) = EqualizerProfileDiagnostic(code, severity, line, message)

    private data class ParsedNumber(
        val value: Double,
        val decimalCommaNormalized: Boolean
    )

    private data class ParsedDeclaration(
        val declaration: ImportedFilterDeclaration,
        val decimalCommaNormalized: Boolean = false
    )
}
