package com.example.cdplaya.data

import kotlinx.serialization.Serializable

/**
 * Storage values are strings on purpose. Unknown future fields/operators survive backup and
 * database round-trips without requiring a Room schema change or a closed enum migration.
 */
@Serializable
data class SmartPlaylistRule(
    val field: String,
    val operator: String,
    val values: List<String> = emptyList(),
    val parameters: Map<String, String> = emptyMap()
)

@Serializable
data class SmartPlaylistDraft(
    val matchMode: String = SmartPlaylistMatchMode.ALL,
    val rules: List<SmartPlaylistRule> = emptyList(),
    val sortField: String = SmartPlaylistSortField.TITLE,
    val sortDirection: String = SmartPlaylistSortDirection.ASCENDING,
    val resultLimit: Int? = null,
    val definitionVersion: Int = CURRENT_SMART_PLAYLIST_DEFINITION_VERSION
) {
    fun validated(): SmartPlaylistDraft {
        require(matchMode in SmartPlaylistMatchMode.ALL_VALUES) { "Unknown match mode: $matchMode" }
        require(sortDirection in SmartPlaylistSortDirection.ALL_VALUES) {
            "Unknown sort direction: $sortDirection"
        }
        require(sortField.isNotBlank()) { "A Smart Playlist sort field is required." }
        require(definitionVersion > 0) { "Definition version must be positive." }
        require(resultLimit == null || resultLimit in 1..MAX_SMART_PLAYLIST_RESULT_LIMIT) {
            "Result limit must be between 1 and $MAX_SMART_PLAYLIST_RESULT_LIMIT."
        }
        require(rules.size <= MAX_SMART_PLAYLIST_RULES) {
            "A Smart Playlist supports at most $MAX_SMART_PLAYLIST_RULES rules."
        }
        rules.forEach { rule ->
            require(rule.field.isNotBlank()) { "A Smart Playlist rule field is required." }
            require(rule.operator.isNotBlank()) { "A Smart Playlist rule operator is required." }
        }
        return this
    }
}

data class SmartPlaylistDefinition(
    val playlistId: Long,
    val draft: SmartPlaylistDraft,
    val updatedAt: Long
) {
    init {
        require(playlistId > 0L)
        draft.validated()
    }
}

data class SmartPlaylistResolution(
    val playlistId: Long?,
    val songs: List<Song>,
    val resolvedAt: Long,
    val fromDerivedCache: Boolean,
    val generatedSnapshot: Boolean = false
) {
    val count: Int get() = songs.size
}

data class GeneratedPlaylistState(
    val playlistId: Long,
    val templateKey: String,
    val membershipMode: String,
    val refreshPolicy: String,
    val refreshIntervalMillis: Long?,
    val lastRefreshedAt: Long?,
    val snapshotVersion: Int
)

enum class PlaylistMembershipBehavior {
    MANUAL,
    USER_SMART_LIVE,
    GENERATED_SMART_LIVE,
    GENERATED_SMART_SNAPSHOT
}

object SmartPlaylistMatchMode {
    const val ALL = "ALL"
    const val ANY = "ANY"
    internal val ALL_VALUES = setOf(ALL, ANY)
}

object SmartPlaylistRuleField {
    const val RATING = "rating"
    const val TOTAL_PLAY_COUNT = "total_play_count"
    const val RECENT_PLAY_COUNT = "recent_play_count"
    const val LAST_PLAYED = "last_played"
    const val NEVER_PLAYED = "never_played"
    const val TITLE = "title"
    const val ARTIST = "artist"
    const val ALBUM = "album"
    const val YEAR = "year"
    const val DURATION = "duration_ms"
    const val DATE_ADDED = "date_added"
}

object SmartPlaylistOperator {
    const val IS = "is"
    const val IS_NOT = "is_not"
    const val CONTAINS = "contains"
    const val DOES_NOT_CONTAIN = "does_not_contain"
    const val EQUALS = "equals"
    const val AT_LEAST = "at_least"
    const val AT_MOST = "at_most"
    const val BETWEEN = "between"
    const val UNRATED = "unrated"
    const val WITHIN_LAST_DAYS = "within_last_days"
    const val MORE_THAN_DAYS_AGO = "more_than_days_ago"
    const val NEVER = "never"
    const val SHORTER_THAN = "shorter_than"
    const val LONGER_THAN = "longer_than"
    const val ABOUT = "about"
    const val BEFORE = "before"
    const val AFTER = "after"
}

object SmartPlaylistSortField {
    const val PLAY_COUNT = "play_count"
    const val RECENT_PLAY_COUNT = "recent_play_count"
    const val FORGOTTEN_FAVORITES_RANK = "forgotten_favorites_rank"
    const val LAST_PLAYED = "last_played"
    const val RATING = "rating"
    const val TITLE = "title"
    const val ARTIST = "artist"
    const val ALBUM = "album"
    const val YEAR = "year"
}

object SmartPlaylistSortDirection {
    const val ASCENDING = "ASC"
    const val DESCENDING = "DESC"
    internal val ALL_VALUES = setOf(ASCENDING, DESCENDING)
}

object GeneratedPlaylistRefreshPolicy {
    const val MANUAL = "manual"
    const val PERIODIC = "periodic"
    const val ON_OPEN_IF_STALE = "on_open_if_stale"
}

object GeneratedPlaylistMembershipMode {
    const val LIVE_DERIVED = "live_derived"
    const val SNAPSHOT = "snapshot"
}

const val CURRENT_SMART_PLAYLIST_DEFINITION_VERSION = 1
const val CURRENT_GENERATED_SNAPSHOT_VERSION = 1
const val MAX_SMART_PLAYLIST_RULES = 64
const val MAX_SMART_PLAYLIST_RESULT_LIMIT = 10_000

fun generatedSnapshotRefreshEligible(state: GeneratedPlaylistState, nowMillis: Long): Boolean {
    if (state.membershipMode != GeneratedPlaylistMembershipMode.SNAPSHOT) return false
    if (state.lastRefreshedAt == null) return true
    if (state.refreshPolicy != GeneratedPlaylistRefreshPolicy.ON_OPEN_IF_STALE) return false
    val interval = state.refreshIntervalMillis ?: return false
    return nowMillis - state.lastRefreshedAt >= interval
}
