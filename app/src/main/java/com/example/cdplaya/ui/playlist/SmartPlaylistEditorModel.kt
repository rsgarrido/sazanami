package com.example.cdplaya.ui.playlist

import com.example.cdplaya.data.MAX_SMART_PLAYLIST_RESULT_LIMIT
import com.example.cdplaya.data.SmartPlaylistDraft
import com.example.cdplaya.data.SmartPlaylistMatchMode
import com.example.cdplaya.data.SmartPlaylistOperator
import com.example.cdplaya.data.SmartPlaylistRule
import com.example.cdplaya.data.SmartPlaylistRuleField
import com.example.cdplaya.data.SmartPlaylistSortDirection
import com.example.cdplaya.data.SmartPlaylistSortField

data class SmartPlaylistEditorRule(
    val id: Long,
    val field: String = SmartPlaylistRuleField.ARTIST,
    val operator: String = SmartPlaylistOperator.CONTAINS,
    val value: String = "",
    val secondValue: String = "",
    val windowDays: String = "30"
)

data class SmartPlaylistEditorModel(
    val name: String = "",
    val matchMode: String = SmartPlaylistMatchMode.ALL,
    val rules: List<SmartPlaylistEditorRule> = listOf(SmartPlaylistEditorRule(1L)),
    val sortField: String = SmartPlaylistSortField.TITLE,
    val sortDirection: String = SmartPlaylistSortDirection.ASCENDING,
    val resultLimit: String = ""
) {
    val showsMatchModeChoice: Boolean get() = rules.size > 1

    fun validation(existingNames: Collection<String>, originalName: String? = null): SmartEditorValidation {
        val errors = mutableMapOf<Long, String>()
        rules.forEach { rule -> validateRule(rule)?.let { errors[rule.id] = it } }
        val trimmedName = name.trim()
        val nameError = when {
            trimmedName.isBlank() -> "Enter a playlist name."
            existingNames.any {
                !it.equals(originalName, ignoreCase = true) && it.equals(trimmedName, ignoreCase = true)
            } -> "A playlist with that name already exists."
            else -> null
        }
        val parsedLimit = resultLimit.trim().takeIf(String::isNotEmpty)?.toIntOrNull()
        val limitError = when {
            resultLimit.isBlank() -> null
            parsedLimit == null -> "Enter a whole-number limit."
            parsedLimit !in 1..MAX_SMART_PLAYLIST_RESULT_LIMIT ->
                "Limit must be between 1 and $MAX_SMART_PLAYLIST_RESULT_LIMIT."
            else -> null
        }
        return SmartEditorValidation(
            nameError = nameError,
            generalError = when {
                rules.isEmpty() -> "Add at least one rule."
                sortField == SmartPlaylistSortField.RECENT_PLAY_COUNT &&
                    rules.none { it.field == SmartPlaylistRuleField.RECENT_PLAY_COUNT } ->
                    "Recent play-count sorting needs a recent play-count rule."
                limitError != null -> limitError
                else -> null
            },
            ruleErrors = errors
        )
    }

    fun toDraft(): SmartPlaylistDraft = SmartPlaylistDraft(
        matchMode = matchMode,
        rules = rules.map(SmartPlaylistEditorRule::toRule),
        sortField = sortField,
        sortDirection = sortDirection,
        resultLimit = resultLimit.trim().takeIf(String::isNotEmpty)?.toInt()
    ).validated()

    companion object {
        fun fromDraft(name: String, draft: SmartPlaylistDraft): SmartPlaylistEditorModel =
            SmartPlaylistEditorModel(
                name = name,
                matchMode = draft.matchMode,
                rules = draft.rules.mapIndexed { index, rule -> rule.toEditorRule(index + 1L) },
                sortField = draft.sortField,
                sortDirection = draft.sortDirection,
                resultLimit = draft.resultLimit?.toString().orEmpty()
            )
    }
}

data class SmartEditorValidation(
    val nameError: String? = null,
    val generalError: String? = null,
    val ruleErrors: Map<Long, String> = emptyMap()
) {
    val isValid: Boolean get() = nameError == null && generalError == null && ruleErrors.isEmpty()
}

data class SmartRuleFieldOption(
    val storage: String,
    val label: String,
    val operators: List<SmartRuleOperatorOption>,
    val valueKind: SmartRuleValueKind
)

data class SmartRuleOperatorOption(val storage: String, val label: String)

enum class SmartRuleValueKind { TEXT, NUMBER, RATING, DURATION_MINUTES, RELATIVE_DAYS, RECENT_COUNT, NONE }

private val equals = SmartRuleOperatorOption(SmartPlaylistOperator.EQUALS, "equals")
private val atLeast = SmartRuleOperatorOption(SmartPlaylistOperator.AT_LEAST, "at least")
private val atMost = SmartRuleOperatorOption(SmartPlaylistOperator.AT_MOST, "at most")
private val between = SmartRuleOperatorOption(SmartPlaylistOperator.BETWEEN, "between")
internal const val LISTENING_HISTORY_EDITOR_FIELD = "listening_history_editor"
private val relativeOperators = listOf(
    SmartRuleOperatorOption(SmartPlaylistOperator.WITHIN_LAST_DAYS, "within last N days"),
    SmartRuleOperatorOption(SmartPlaylistOperator.MORE_THAN_DAYS_AGO, "more than N days ago")
)

val smartRuleFieldOptions = listOf(
    SmartRuleFieldOption(
        SmartPlaylistRuleField.RATING,
        "Rating",
        listOf(equals, atLeast, atMost, SmartRuleOperatorOption(SmartPlaylistOperator.UNRATED, "unrated")),
        SmartRuleValueKind.RATING
    ),
    SmartRuleFieldOption(
        SmartPlaylistRuleField.TOTAL_PLAY_COUNT,
        "Total play count",
        listOf(equals, atLeast, atMost, between),
        SmartRuleValueKind.NUMBER
    ),
    SmartRuleFieldOption(
        SmartPlaylistRuleField.RECENT_PLAY_COUNT,
        "Play count",
        listOf(equals, atLeast, atMost, between),
        SmartRuleValueKind.RECENT_COUNT
    ),
    SmartRuleFieldOption(
        LISTENING_HISTORY_EDITOR_FIELD,
        "Listening history",
        listOf(
            SmartRuleOperatorOption(SmartPlaylistOperator.NEVER, "Never played"),
            SmartRuleOperatorOption(SmartPlaylistOperator.WITHIN_LAST_DAYS, "Played within last"),
            SmartRuleOperatorOption(SmartPlaylistOperator.MORE_THAN_DAYS_AGO, "Not played for")
        ),
        SmartRuleValueKind.RELATIVE_DAYS
    ),
    SmartRuleFieldOption(
        SmartPlaylistRuleField.TITLE,
        "Title",
        textOperators(),
        SmartRuleValueKind.TEXT
    ),
    SmartRuleFieldOption(
        SmartPlaylistRuleField.ARTIST,
        "Artist",
        textOperators(),
        SmartRuleValueKind.TEXT
    ),
    SmartRuleFieldOption(
        SmartPlaylistRuleField.ALBUM,
        "Album",
        textOperators(),
        SmartRuleValueKind.TEXT
    ),
    SmartRuleFieldOption(
        SmartPlaylistRuleField.YEAR,
        "Year",
        listOf(
            equals,
            SmartRuleOperatorOption(SmartPlaylistOperator.BEFORE, "before"),
            SmartRuleOperatorOption(SmartPlaylistOperator.AFTER, "after"),
            between
        ),
        SmartRuleValueKind.NUMBER
    ),
    SmartRuleFieldOption(
        SmartPlaylistRuleField.DURATION,
        "Duration",
        listOf(
            SmartRuleOperatorOption(SmartPlaylistOperator.SHORTER_THAN, "shorter than"),
            SmartRuleOperatorOption(SmartPlaylistOperator.LONGER_THAN, "longer than"),
            between,
            SmartRuleOperatorOption(SmartPlaylistOperator.ABOUT, "about")
        ),
        SmartRuleValueKind.DURATION_MINUTES
    ),
    SmartRuleFieldOption(
        SmartPlaylistRuleField.DATE_ADDED,
        "Date added",
        relativeOperators,
        SmartRuleValueKind.RELATIVE_DAYS
    )
)

val smartSortOptions = listOf(
    SmartPlaylistSortField.TITLE to "Title",
    SmartPlaylistSortField.ARTIST to "Artist",
    SmartPlaylistSortField.ALBUM to "Album",
    SmartPlaylistSortField.YEAR to "Year",
    SmartPlaylistSortField.RATING to "Rating",
    SmartPlaylistSortField.PLAY_COUNT to "Play count",
    SmartPlaylistSortField.RECENT_PLAY_COUNT to "Recent play count",
    SmartPlaylistSortField.FORGOTTEN_FAVORITES_RANK to "Historical strength and rating",
    SmartPlaylistSortField.LAST_PLAYED to "Last played"
)

private fun textOperators() = listOf(
    SmartRuleOperatorOption(SmartPlaylistOperator.CONTAINS, "contains"),
    SmartRuleOperatorOption(SmartPlaylistOperator.DOES_NOT_CONTAIN, "does not contain"),
    SmartRuleOperatorOption(SmartPlaylistOperator.IS, "is"),
    SmartRuleOperatorOption(SmartPlaylistOperator.IS_NOT, "is not")
)

private fun validateRule(rule: SmartPlaylistEditorRule): String? {
    val field = smartRuleFieldOptions.firstOrNull { it.storage == rule.field }
        ?: return "This rule field is not supported by this version."
    if (field.operators.none { it.storage == rule.operator }) {
        return "This operator is not supported for ${field.label.lowercase()}."
    }
    if (rule.operator == SmartPlaylistOperator.UNRATED ||
        rule.operator == SmartPlaylistOperator.NEVER || field.valueKind == SmartRuleValueKind.NONE
    ) return null
    if (rule.value.isBlank()) return "Enter a value."
    return when (field.valueKind) {
        SmartRuleValueKind.TEXT -> null
        SmartRuleValueKind.RATING -> if (rule.value.toIntOrNull() in 1..5) null else "Choose 1 to 5 stars."
        SmartRuleValueKind.NUMBER -> when (rule.field) {
            SmartPlaylistRuleField.YEAR -> wholeNumberRuleError(rule, minimum = 1)
            SmartPlaylistRuleField.TOTAL_PLAY_COUNT -> wholeNumberRuleError(rule, minimum = 0)
            else -> numericRuleError(rule, positive = false)
        }
        SmartRuleValueKind.DURATION_MINUTES -> numericRuleError(rule, positive = true)
        SmartRuleValueKind.RELATIVE_DAYS -> if (rule.value.toIntOrNull()?.let { it > 0 } == true) null
            else "Enter a positive number of days."
        SmartRuleValueKind.RECENT_COUNT -> when {
            rule.windowDays.toIntOrNull()?.let { it > 0 } != true -> "Enter a positive listening window."
            else -> wholeNumberRuleError(rule, minimum = 0)
        }
        SmartRuleValueKind.NONE -> null
    }
}

private fun wholeNumberRuleError(rule: SmartPlaylistEditorRule, minimum: Int): String? {
    val first = rule.value.toIntOrNull()
    if (first == null || first < minimum) return "Enter a valid whole number."
    if (rule.operator == SmartPlaylistOperator.BETWEEN) {
        val second = rule.secondValue.toIntOrNull()
        if (second == null || second < first) {
            return "Enter a whole-number upper value at least as large as the first."
        }
    }
    return null
}

private fun numericRuleError(rule: SmartPlaylistEditorRule, positive: Boolean): String? {
    val first = rule.value.toDoubleOrNull()
    if (first == null || positive && first <= 0 || !positive && first < 0) return "Enter a valid number."
    if (rule.operator == SmartPlaylistOperator.BETWEEN) {
        val second = rule.secondValue.toDoubleOrNull()
        if (second == null || second < first) return "Enter an upper value at least as large as the first."
    }
    return null
}

private fun SmartPlaylistEditorRule.toRule(): SmartPlaylistRule {
    val kind = smartRuleFieldOptions.first { it.storage == field }.valueKind
    val noValue = operator == SmartPlaylistOperator.UNRATED || operator == SmartPlaylistOperator.NEVER
    val values = when {
        field == SmartPlaylistRuleField.NEVER_PLAYED -> listOf("true")
        noValue -> emptyList()
        operator == SmartPlaylistOperator.BETWEEN -> listOf(convertValue(value, kind), convertValue(secondValue, kind))
        else -> listOf(convertValue(value, kind))
    }
    return SmartPlaylistRule(
        field = when {
            field == LISTENING_HISTORY_EDITOR_FIELD && operator == SmartPlaylistOperator.NEVER ->
                SmartPlaylistRuleField.NEVER_PLAYED
            field == LISTENING_HISTORY_EDITOR_FIELD -> SmartPlaylistRuleField.LAST_PLAYED
            else -> field
        },
        operator = if (
            field == LISTENING_HISTORY_EDITOR_FIELD && operator == SmartPlaylistOperator.NEVER
        ) SmartPlaylistOperator.IS else operator,
        values = values,
        parameters = if (kind == SmartRuleValueKind.RECENT_COUNT) mapOf("days" to windowDays) else emptyMap()
    )
}

private fun SmartPlaylistRule.toEditorRule(id: Long): SmartPlaylistEditorRule {
    val editorField = when (field) {
        SmartPlaylistRuleField.LAST_PLAYED,
        SmartPlaylistRuleField.NEVER_PLAYED -> LISTENING_HISTORY_EDITOR_FIELD
        else -> field
    }
    val editorOperator = when {
        field == SmartPlaylistRuleField.NEVER_PLAYED -> SmartPlaylistOperator.NEVER
        field == SmartPlaylistRuleField.DURATION && operator == SmartPlaylistOperator.EQUALS ->
            SmartPlaylistOperator.ABOUT
        field == SmartPlaylistRuleField.DURATION && operator == SmartPlaylistOperator.AT_LEAST ->
            SmartPlaylistOperator.LONGER_THAN
        field == SmartPlaylistRuleField.DURATION && operator == SmartPlaylistOperator.AT_MOST ->
            SmartPlaylistOperator.SHORTER_THAN
        field == SmartPlaylistRuleField.YEAR && operator == SmartPlaylistOperator.AT_LEAST ->
            SmartPlaylistOperator.AFTER
        field == SmartPlaylistRuleField.YEAR && operator == SmartPlaylistOperator.AT_MOST ->
            SmartPlaylistOperator.BEFORE
        else -> operator
    }
    val kind = smartRuleFieldOptions.firstOrNull { it.storage == editorField }?.valueKind
    return SmartPlaylistEditorRule(
        id = id,
        field = editorField,
        operator = editorOperator,
        value = displayValue(values.getOrNull(0).orEmpty(), kind),
        secondValue = displayValue(values.getOrNull(1).orEmpty(), kind),
        windowDays = parameters["days"] ?: "30"
    )
}

private fun convertValue(value: String, kind: SmartRuleValueKind): String =
    if (kind == SmartRuleValueKind.DURATION_MINUTES) {
        (value.toDouble() * 60_000.0).toLong().toString()
    } else value.trim()

private fun displayValue(value: String, kind: SmartRuleValueKind?): String =
    if (kind == SmartRuleValueKind.DURATION_MINUTES && value.isNotBlank()) {
        (value.toDoubleOrNull()?.div(60_000.0))?.let { minutes ->
            if (minutes % 1.0 == 0.0) minutes.toLong().toString() else minutes.toString()
        } ?: value
    } else value
