package com.example.cdplaya.data.local

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.cdplaya.data.SmartPlaylistDraft
import com.example.cdplaya.data.FolderSelection
import com.example.cdplaya.data.FolderSelectionMode
import com.example.cdplaya.data.SmartPlaylistMatchMode
import com.example.cdplaya.data.SmartPlaylistOperator
import com.example.cdplaya.data.SmartPlaylistRule
import com.example.cdplaya.data.SmartPlaylistRuleField
import com.example.cdplaya.data.SmartPlaylistSortDirection
import com.example.cdplaya.data.SmartPlaylistSortField
import com.example.cdplaya.data.UNKNOWN_GENRE_KEY
import com.example.cdplaya.data.UNKNOWN_GENRE_NAME
import com.example.cdplaya.data.normalizedGenreKey
import java.util.Locale
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal object SmartPlaylistDependencies {
    const val LIBRARY = 1
    const val RATINGS = 1 shl 1
    const val LISTENING = 1 shl 2
    const val ALL = LIBRARY or RATINGS or LISTENING

    fun forDefinition(draft: SmartPlaylistDraft): Int {
        // The authoritative library membership is a dependency for every definition, even when
        // all of its explicit rules concern ratings or listening history.
        var mask = draft.rules.fold(LIBRARY) { result, rule -> result or forField(rule.field) }
        mask = mask or when (draft.sortField) {
            SmartPlaylistSortField.PLAY_COUNT,
            SmartPlaylistSortField.RECENT_PLAY_COUNT,
            SmartPlaylistSortField.LAST_PLAYED -> LISTENING
            SmartPlaylistSortField.FORGOTTEN_FAVORITES_RANK -> LISTENING or RATINGS
            SmartPlaylistSortField.RATING -> RATINGS
            SmartPlaylistSortField.TITLE,
            SmartPlaylistSortField.ARTIST,
            SmartPlaylistSortField.ALBUM,
            SmartPlaylistSortField.YEAR -> LIBRARY
            else -> ALL
        }
        return mask
    }

    fun isTimeSensitive(draft: SmartPlaylistDraft): Boolean = draft.rules.any { rule ->
        rule.field == SmartPlaylistRuleField.RECENT_PLAY_COUNT ||
            rule.operator == SmartPlaylistOperator.WITHIN_LAST_DAYS ||
            rule.operator == SmartPlaylistOperator.MORE_THAN_DAYS_AGO
    }

    private fun forField(field: String): Int = when (field) {
        SmartPlaylistRuleField.RATING -> RATINGS
        SmartPlaylistRuleField.TOTAL_PLAY_COUNT,
        SmartPlaylistRuleField.RECENT_PLAY_COUNT,
        SmartPlaylistRuleField.LAST_PLAYED,
        SmartPlaylistRuleField.NEVER_PLAYED -> LISTENING
        SmartPlaylistRuleField.TITLE,
        SmartPlaylistRuleField.ARTIST,
        SmartPlaylistRuleField.ALBUM,
        SmartPlaylistRuleField.GENRE,
        SmartPlaylistRuleField.COMPOSER,
        SmartPlaylistRuleField.PUBLISHER,
        SmartPlaylistRuleField.YEAR,
        SmartPlaylistRuleField.BPM,
        SmartPlaylistRuleField.DURATION,
        SmartPlaylistRuleField.DATE_ADDED -> LIBRARY
        else -> ALL
    }
}

internal object SmartPlaylistQueries {
    fun resolve(
        draft: SmartPlaylistDraft,
        nowMillis: Long,
        folderSelection: FolderSelection = FolderSelection.All
    ): SupportSQLiteQuery = build(draft, nowMillis, folderSelection).toQuery()

    fun count(
        draft: SmartPlaylistDraft,
        nowMillis: Long,
        folderSelection: FolderSelection = FolderSelection.All
    ): SupportSQLiteQuery {
        val inner = build(draft.copy(resultLimit = null), nowMillis, folderSelection)
        return SimpleSQLiteQuery(
            "SELECT COUNT(*) AS count FROM (${inner.sql})",
            inner.args
        )
    }

    private fun build(
        draft: SmartPlaylistDraft,
        nowMillis: Long,
        folderSelection: FolderSelection
    ): SqlAndArgs {
        draft.validated()
        val recentWindows = draft.rules
            .filter { it.field == SmartPlaylistRuleField.RECENT_PLAY_COUNT }
            .map(::recentWindowDays)
            .distinct()
            .sorted()
        val args = mutableListOf<Any>()
        val recentCtes = recentWindows.mapIndexed { index, days ->
            val cutoff = nowMillis - daysToMillis(days)
            args += cutoff
            """
            recent_$index AS (
                SELECT COALESCE(r.targetIdentityId, e.trackIdentityId) AS trackIdentityId,
                       COUNT(*) AS qualifiedPlayCount
                FROM listening_events e
                LEFT JOIN listening_identity_reconciliations r
                  ON r.sourceIdentityId = e.trackIdentityId
                WHERE e.publicationState != 'import_pending'
                  AND e.qualifiedAsPlay = 1
                  AND e.attributionAt >= ?
                GROUP BY COALESCE(r.targetIdentityId, e.trackIdentityId)
            )
            """.trimIndent()
        }
        val recentJoins = recentWindows.indices.joinToString("\n") { index ->
            "LEFT JOIN recent_$index ON recent_$index.trackIdentityId = binding.trackIdentityId"
        }
        val recentColumns = recentWindows.indices.joinToString("") { index ->
            ", COALESCE(recent_$index.qualifiedPlayCount, 0) AS recentPlayCount$index"
        }
        val eligibleLibraryPredicate = folderSelectionPredicate(folderSelection, args)
        val predicates = draft.rules.map { rule ->
            predicate(rule, recentWindows, nowMillis, args)
        }
        val where = when {
            predicates.isEmpty() -> "1 = 1"
            draft.matchMode == SmartPlaylistMatchMode.ALL -> predicates.joinToString(" AND ", "(", ")")
            else -> predicates.joinToString(" OR ", "(", ")")
        }
        val direction = if (draft.sortDirection == SmartPlaylistSortDirection.DESCENDING) {
            "DESC"
        } else {
            "ASC"
        }
        val primaryOrder = when (draft.sortField) {
            SmartPlaylistSortField.PLAY_COUNT -> "library_rows.totalPlayCount $direction"
            SmartPlaylistSortField.RECENT_PLAY_COUNT -> {
                require(recentWindows.isNotEmpty()) {
                    "Recent play-count sorting requires a recent play-count rule."
                }
                "library_rows.recentPlayCount0 $direction"
            }
            SmartPlaylistSortField.FORGOTTEN_FAVORITES_RANK ->
                "library_rows.totalPlayCount DESC, " +
                    "CASE WHEN library_rows.rating IS NULL THEN 1 ELSE 0 END ASC, " +
                    "library_rows.rating DESC, library_rows.lastPlayedAt ASC"
            SmartPlaylistSortField.LAST_PLAYED -> if (
                direction == SmartPlaylistSortDirection.ASCENDING
            ) {
                "CASE WHEN library_rows.lastPlayedAt IS NULL THEN 0 ELSE 1 END ASC, library_rows.lastPlayedAt ASC"
            } else {
                "CASE WHEN library_rows.lastPlayedAt IS NULL THEN 1 ELSE 0 END ASC, library_rows.lastPlayedAt DESC"
            }
            SmartPlaylistSortField.RATING ->
                "CASE WHEN library_rows.rating IS NULL THEN 1 ELSE 0 END ASC, library_rows.rating $direction"
            SmartPlaylistSortField.TITLE -> "library_rows.title COLLATE NOCASE $direction"
            SmartPlaylistSortField.ARTIST -> "library_rows.artist COLLATE NOCASE $direction"
            SmartPlaylistSortField.ALBUM -> "library_rows.album COLLATE NOCASE $direction"
            SmartPlaylistSortField.YEAR ->
                "CASE WHEN library_rows.year IS NULL THEN 1 ELSE 0 END ASC, library_rows.year $direction"
            else -> throw IllegalArgumentException("Unsupported Smart Playlist sort field: ${draft.sortField}")
        }
        val limit = draft.resultLimit?.let {
            args += it
            "LIMIT ?"
        }.orEmpty()
        val optionalRecentCtes = if (recentCtes.isEmpty()) "" else ",\n" + recentCtes.joinToString(",\n")
        val sql = """
            WITH resolved_detailed AS (
                SELECT COALESCE(r.targetIdentityId, e.trackIdentityId) AS trackIdentityId,
                       SUM(CASE WHEN e.qualifiedAsPlay = 1 THEN 1 ELSE 0 END) AS qualifiedPlayCount,
                       MAX(CASE WHEN e.qualifiedAsPlay = 1 THEN e.attributionAt END) AS lastPlayedAt
                FROM listening_events e
                LEFT JOIN listening_identity_reconciliations r
                  ON r.sourceIdentityId = e.trackIdentityId
                WHERE e.publicationState != 'import_pending'
                GROUP BY COALESCE(r.targetIdentityId, e.trackIdentityId)
            ),
            resolved_baseline AS (
                SELECT COALESCE(r.targetIdentityId, b.trackIdentityId) AS trackIdentityId,
                       SUM(b.historicalPlayCount) AS playCount,
                       MAX(b.lastKnownPlayedAt) AS lastPlayedAt
                FROM legacy_listening_baselines b
                LEFT JOIN listening_identity_reconciliations r
                  ON r.sourceIdentityId = b.trackIdentityId
                GROUP BY COALESCE(r.targetIdentityId, b.trackIdentityId)
            )
            $optionalRecentCtes,
            library_rows AS (
                SELECT songs.*,
                       COALESCE(resolved_baseline.playCount, 0) +
                           COALESCE(resolved_detailed.qualifiedPlayCount, 0) AS totalPlayCount,
                       CASE
                         WHEN resolved_baseline.lastPlayedAt IS NULL THEN resolved_detailed.lastPlayedAt
                         WHEN resolved_detailed.lastPlayedAt IS NULL THEN resolved_baseline.lastPlayedAt
                         WHEN resolved_baseline.lastPlayedAt >= resolved_detailed.lastPlayedAt THEN resolved_baseline.lastPlayedAt
                         ELSE resolved_detailed.lastPlayedAt
                       END AS lastPlayedAt,
                       ratings.rating AS rating
                       $recentColumns
                FROM cached_songs songs
                LEFT JOIN local_track_bindings binding ON binding.id = (
                    SELECT candidate.id
                    FROM local_track_bindings candidate
                    WHERE candidate.mediaStoreId = songs.mediaStoreId
                      AND COALESCE(candidate.volumeName, '') = songs.volumeName
                      AND candidate.missingSince IS NULL
                    ORDER BY candidate.lastSeenAt DESC, candidate.id ASC
                    LIMIT 1
                )
                LEFT JOIN resolved_detailed ON resolved_detailed.trackIdentityId = binding.trackIdentityId
                LEFT JOIN resolved_baseline ON resolved_baseline.trackIdentityId = binding.trackIdentityId
                LEFT JOIN song_ratings ratings ON ratings.trackIdentityId = binding.trackIdentityId
                $recentJoins
                WHERE $eligibleLibraryPredicate
            )
            SELECT mediaStoreId, title, artist, album, trackNumber, duration, uriString,
                   filePath, folderPath, albumArtUriString, albumArtist, volumeName,
                   displayName, relativePath, fileSizeBytes, dateAddedEpochSeconds,
                   dateModifiedEpochSeconds, year, artworkEnrichmentVersion, genresJson,
                   normalizedGenresJson, composersJson, composerText, publisher, bpm,
                   embeddedMetadataEnrichmentVersion, cachedAt,
                   totalPlayCount, lastPlayedAt, rating
            FROM library_rows
            WHERE $where
            ORDER BY $primaryOrder,
                     library_rows.title COLLATE NOCASE ASC,
                     library_rows.artist COLLATE NOCASE ASC,
                     library_rows.album COLLATE NOCASE ASC,
                     library_rows.volumeName ASC,
                     library_rows.mediaStoreId ASC
            $limit
        """.trimIndent()
        return SqlAndArgs(sql, args.toTypedArray())
    }

    private fun predicate(
        rule: SmartPlaylistRule,
        recentWindows: List<Int>,
        nowMillis: Long,
        args: MutableList<Any>
    ): String {
        val column = when (rule.field) {
            SmartPlaylistRuleField.TITLE -> "library_rows.title"
            SmartPlaylistRuleField.ARTIST -> "library_rows.artist"
            SmartPlaylistRuleField.ALBUM -> "library_rows.album"
            SmartPlaylistRuleField.GENRE -> "library_rows.normalizedGenresJson"
            SmartPlaylistRuleField.COMPOSER -> "library_rows.composerText"
            SmartPlaylistRuleField.PUBLISHER -> "library_rows.publisher"
            SmartPlaylistRuleField.YEAR -> "library_rows.year"
            SmartPlaylistRuleField.BPM -> "library_rows.bpm"
            SmartPlaylistRuleField.DURATION -> "library_rows.duration"
            SmartPlaylistRuleField.DATE_ADDED -> "library_rows.dateAddedEpochSeconds"
            SmartPlaylistRuleField.RATING -> "library_rows.rating"
            SmartPlaylistRuleField.TOTAL_PLAY_COUNT -> "library_rows.totalPlayCount"
            SmartPlaylistRuleField.LAST_PLAYED -> "library_rows.lastPlayedAt"
            SmartPlaylistRuleField.NEVER_PLAYED -> "library_rows.totalPlayCount"
            SmartPlaylistRuleField.RECENT_PLAY_COUNT -> {
                val index = recentWindows.indexOf(recentWindowDays(rule))
                check(index >= 0)
                "library_rows.recentPlayCount$index"
            }
            else -> throw IllegalArgumentException("Unsupported Smart Playlist field: ${rule.field}")
        }

        if (rule.field == SmartPlaylistRuleField.NEVER_PLAYED) {
            val expected = rule.values.firstOrNull()?.toBooleanStrictOrNull() ?: true
            return if (rule.operator == SmartPlaylistOperator.IS && expected ||
                rule.operator == SmartPlaylistOperator.IS_NOT && !expected
            ) "$column = 0" else if (
                rule.operator == SmartPlaylistOperator.IS_NOT && expected ||
                rule.operator == SmartPlaylistOperator.IS && !expected
            ) "$column > 0" else throw IllegalArgumentException("Unsupported never-played operator")
        }
        if (rule.operator == SmartPlaylistOperator.UNRATED) {
            require(rule.field == SmartPlaylistRuleField.RATING)
            return "$column IS NULL"
        }
        if (rule.operator == SmartPlaylistOperator.NEVER) {
            require(rule.field == SmartPlaylistRuleField.LAST_PLAYED)
            return "$column IS NULL"
        }
        if (rule.operator == SmartPlaylistOperator.ABOUT) {
            require(rule.field == SmartPlaylistRuleField.DURATION)
            val center = numericValue(
                requireNotNull(rule.values.firstOrNull()) { "An approximate duration is required." }
            ).toLong()
            val lowerInclusive = (center - HALF_MINUTE_MILLIS).coerceAtLeast(0L)
            val upperExclusive = center + HALF_MINUTE_MILLIS
            args += lowerInclusive
            args += upperExclusive
            return "$column IS NOT NULL AND $column >= ? AND $column < ?"
        }
        if (rule.operator == SmartPlaylistOperator.WITHIN_LAST_DAYS ||
            rule.operator == SmartPlaylistOperator.MORE_THAN_DAYS_AGO
        ) {
            require(rule.field == SmartPlaylistRuleField.LAST_PLAYED ||
                rule.field == SmartPlaylistRuleField.DATE_ADDED)
            val days = positiveInt(rule.values.firstOrNull(), "relative days")
            val cutoffMillis = nowMillis - daysToMillis(days)
            val cutoff = if (rule.field == SmartPlaylistRuleField.DATE_ADDED) cutoffMillis / 1000L
                else cutoffMillis
            args += cutoff
            return if (rule.operator == SmartPlaylistOperator.WITHIN_LAST_DAYS) {
                "$column IS NOT NULL AND $column > 0 AND $column >= ?"
            } else {
                "$column IS NOT NULL AND $column > 0 AND $column < ?"
            }
        }
        if (rule.field == SmartPlaylistRuleField.GENRE) {
            return genrePredicate(rule, args)
        }
        if (rule.field == SmartPlaylistRuleField.COMPOSER &&
            rule.operator in setOf(SmartPlaylistOperator.IS, SmartPlaylistOperator.IS_NOT)
        ) {
            return composerIdentityPredicate(rule, args)
        }
        return when (rule.operator) {
            SmartPlaylistOperator.IS,
            SmartPlaylistOperator.IS_NOT,
            SmartPlaylistOperator.CONTAINS,
            SmartPlaylistOperator.DOES_NOT_CONTAIN -> textPredicate(column, rule, args)
            SmartPlaylistOperator.EQUALS -> numericPredicate(column, "=", rule, args)
            SmartPlaylistOperator.NOT_EQUALS -> numericPredicate(column, "!=", rule, args)
            SmartPlaylistOperator.GREATER_THAN -> numericPredicate(column, ">", rule, args)
            SmartPlaylistOperator.LESS_THAN -> numericPredicate(column, "<", rule, args)
            SmartPlaylistOperator.AT_LEAST -> numericPredicate(column, ">=", rule, args)
            SmartPlaylistOperator.AT_MOST -> numericPredicate(column, "<=", rule, args)
            SmartPlaylistOperator.SHORTER_THAN -> {
                require(rule.field == SmartPlaylistRuleField.DURATION)
                numericPredicate(column, "<", rule, args)
            }
            SmartPlaylistOperator.LONGER_THAN -> {
                require(rule.field == SmartPlaylistRuleField.DURATION)
                numericPredicate(column, ">", rule, args)
            }
            SmartPlaylistOperator.BEFORE -> {
                require(rule.field == SmartPlaylistRuleField.YEAR)
                numericPredicate(column, "<", rule, args)
            }
            SmartPlaylistOperator.AFTER -> {
                require(rule.field == SmartPlaylistRuleField.YEAR)
                numericPredicate(column, ">", rule, args)
            }
            SmartPlaylistOperator.BETWEEN -> {
                require(rule.values.size == 2) { "Between requires two values." }
                args += numericValue(rule.values[0])
                args += numericValue(rule.values[1])
                val knownValue = if (rule.field == SmartPlaylistRuleField.DATE_ADDED) {
                    "$column IS NOT NULL AND $column > 0"
                } else {
                    "$column IS NOT NULL"
                }
                "$knownValue AND $column BETWEEN ? AND ?"
            }
            else -> throw IllegalArgumentException("Unsupported Smart Playlist operator: ${rule.operator}")
        }
    }

    private fun textPredicate(
        column: String,
        rule: SmartPlaylistRule,
        args: MutableList<Any>
    ): String {
        require(rule.field in setOf(
            SmartPlaylistRuleField.TITLE,
            SmartPlaylistRuleField.ARTIST,
            SmartPlaylistRuleField.ALBUM,
            SmartPlaylistRuleField.COMPOSER,
            SmartPlaylistRuleField.PUBLISHER
        )) { "Text operator ${rule.operator} is not valid for ${rule.field}." }
        val value = requireNotNull(rule.values.firstOrNull()) { "A text value is required." }
        return when (rule.operator) {
            SmartPlaylistOperator.IS -> {
                args += value
                "$column = ? COLLATE NOCASE"
            }
            SmartPlaylistOperator.IS_NOT -> {
                args += value
                "$column != ? COLLATE NOCASE"
            }
            SmartPlaylistOperator.CONTAINS -> {
                args += escapeLike(value.lowercase(Locale.ROOT))
                "LOWER($column) LIKE '%' || ? || '%' ESCAPE '\\'"
            }
            SmartPlaylistOperator.DOES_NOT_CONTAIN -> {
                args += escapeLike(value.lowercase(Locale.ROOT))
                "LOWER($column) NOT LIKE '%' || ? || '%' ESCAPE '\\'"
            }
            else -> error("Not a text operator")
        }
    }

    private fun numericPredicate(
        column: String,
        comparison: String,
        rule: SmartPlaylistRule,
        args: MutableList<Any>
    ): String {
        require(rule.field !in setOf(
            SmartPlaylistRuleField.TITLE,
            SmartPlaylistRuleField.ARTIST,
            SmartPlaylistRuleField.ALBUM,
            SmartPlaylistRuleField.GENRE,
            SmartPlaylistRuleField.COMPOSER,
            SmartPlaylistRuleField.PUBLISHER,
            SmartPlaylistRuleField.LAST_PLAYED
        )) { "Numeric operator ${rule.operator} is not valid for ${rule.field}." }
        args += numericValue(requireNotNull(rule.values.firstOrNull()) { "A numeric value is required." })
        val knownValue = if (rule.field == SmartPlaylistRuleField.DATE_ADDED) {
            "$column IS NOT NULL AND $column > 0"
        } else {
            "$column IS NOT NULL"
        }
        return "$knownValue AND $column $comparison ?"
    }

    private fun genrePredicate(
        rule: SmartPlaylistRule,
        args: MutableList<Any>
    ): String {
        require(rule.operator in setOf(
            SmartPlaylistOperator.IS,
            SmartPlaylistOperator.IS_NOT,
            SmartPlaylistOperator.CONTAINS
        )) { "Genre operator ${rule.operator} is not supported." }
        val rawValue = requireNotNull(rule.values.firstOrNull()) { "A Genre value is required." }
        val cleanedValue = rawValue.trim()
        val isUnknown = cleanedValue.equals(UNKNOWN_GENRE_NAME, ignoreCase = true) ||
            cleanedValue.equals(UNKNOWN_GENRE_KEY, ignoreCase = true)
        if (isUnknown) {
            return if (rule.operator == SmartPlaylistOperator.IS_NOT) {
                "library_rows.normalizedGenresJson != '[]'"
            } else {
                "library_rows.normalizedGenresJson = '[]'"
            }
        }
        val normalized = normalizedGenreKey(cleanedValue)
        require(normalized.isNotBlank()) { "A Genre value is required." }
        return when (rule.operator) {
            SmartPlaylistOperator.IS -> {
                args += escapeLike(valueJson.encodeToString(normalized).lowercase(Locale.ROOT))
                "LOWER(library_rows.normalizedGenresJson) LIKE '%' || ? || '%' ESCAPE '\\'"
            }
            SmartPlaylistOperator.IS_NOT -> {
                args += escapeLike(valueJson.encodeToString(normalized).lowercase(Locale.ROOT))
                "LOWER(library_rows.normalizedGenresJson) NOT LIKE '%' || ? || '%' ESCAPE '\\'"
            }
            SmartPlaylistOperator.CONTAINS -> {
                args += escapeLike(normalized)
                "LOWER(library_rows.normalizedGenresJson) LIKE '%' || ? || '%' ESCAPE '\\'"
            }
            else -> error("Unsupported Genre operator")
        }
    }

    private fun composerIdentityPredicate(
        rule: SmartPlaylistRule,
        args: MutableList<Any>
    ): String {
        val value = requireNotNull(rule.values.firstOrNull()) { "A Composer value is required." }
            .trim()
        require(value.isNotBlank()) { "A Composer value is required." }
        args += escapeLike(valueJson.encodeToString(value.lowercase(Locale.ROOT)))
        val comparison = if (rule.operator == SmartPlaylistOperator.IS) "LIKE" else "NOT LIKE"
        return "LOWER(library_rows.composersJson) $comparison '%' || ? || '%' ESCAPE '\\'"
    }

    /** Mirrors [FolderSelection.includes] without narrowing the durable reference cache. */
    private fun folderSelectionPredicate(
        selection: FolderSelection,
        args: MutableList<Any>
    ): String {
        data class FolderRule(val path: String, val included: Boolean)

        val rules = buildList {
            selection.excludedFolders.forEach { path ->
                normalizeFolderRoot(path)?.let { add(FolderRule(it, included = false)) }
            }
            selection.customFolders.forEach { path ->
                normalizeFolderRoot(path)?.let { add(FolderRule(it, included = true)) }
            }
        }.distinctBy { it.path.lowercase(Locale.ROOT) to it.included }
            .sortedWith(
                compareByDescending<FolderRule> { it.path.length }
                    // An equal-specificity exclusion wins, matching FolderSelection.includes.
                    .thenBy { it.included }
            )

        if (rules.isEmpty()) {
            return if (selection.mode == FolderSelectionMode.ALL) "1 = 1" else "0 = 1"
        }

        return buildString {
            append("CASE ")
            rules.forEach { rule ->
                append("WHEN (songs.folderPath = ? COLLATE NOCASE OR ")
                append("LOWER(songs.folderPath) LIKE LOWER(? || '/%') ESCAPE '\\') THEN ")
                append(if (rule.included) "1 " else "0 ")
                args += rule.path
                args += escapeLike(rule.path)
            }
            append("ELSE ")
            append(if (selection.mode == FolderSelectionMode.ALL) "1 " else "0 ")
            append("END = 1")
        }
    }

    private fun normalizeFolderRoot(path: String): String? = path
        .trim()
        .replace('\\', '/')
        .trimEnd('/')
        .takeIf(String::isNotBlank)

    private fun recentWindowDays(rule: SmartPlaylistRule): Int = positiveInt(
        rule.parameters["days"],
        "recent play-count window days"
    )

    private fun positiveInt(value: String?, label: String): Int =
        requireNotNull(value?.toIntOrNull()?.takeIf { it > 0 }) { "$label must be positive." }

    private fun numericValue(value: String): Number =
        value.toLongOrNull() ?: value.toDoubleOrNull()
        ?: throw IllegalArgumentException("Invalid numeric Smart Playlist value: $value")

    private fun daysToMillis(days: Int): Long = Math.multiplyExact(days.toLong(), MILLIS_PER_DAY)

    private fun escapeLike(value: String): String = value
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")

    private const val MILLIS_PER_DAY = 86_400_000L
    private const val HALF_MINUTE_MILLIS = 30_000L
    private val valueJson = Json {}

    private data class SqlAndArgs(val sql: String, val args: Array<Any>) {
        fun toQuery(): SupportSQLiteQuery = SimpleSQLiteQuery(sql, args)
    }
}
