package com.example.cdplaya.ui.library

import com.example.cdplaya.data.Song
import com.example.cdplaya.data.membershipKey

enum class SongRatingFilter { ALL, RATED, UNRATED }

enum class RatedSongFilter(val exactRating: Int?) {
    ALL(null),
    UNRATED(null),
    FIVE(5),
    FOUR(4),
    THREE(3),
    TWO(2),
    ONE(1);

    val label: String
        get() = when (this) {
            ALL -> "All ratings"
            UNRATED -> "Unrated"
            else -> exactRating?.let { rating ->
                "$rating star${if (rating == 1) "" else "s"}"
            }.orEmpty()
    }
}

internal fun visibleRatedSongFilters(quickRateActive: Boolean): List<RatedSongFilter> =
    RatedSongFilter.entries.filter { filter ->
        quickRateActive || filter != RatedSongFilter.UNRATED
    }

internal fun normalizeRatedSongFilterForQuickRateMode(
    filter: RatedSongFilter,
    quickRateActive: Boolean
): RatedSongFilter = if (!quickRateActive && filter == RatedSongFilter.UNRATED) {
    RatedSongFilter.ALL
} else {
    filter
}

fun filterSongsForRatedCollection(
    songs: List<Song>,
    filter: RatedSongFilter,
    ratingsByReferenceKey: Map<String, Int>
): List<Song> = when (filter) {
    RatedSongFilter.UNRATED -> emptyList()
    else -> songs.filter { song ->
        val rating = ratingsByReferenceKey[song.membershipKey()]
        rating in 1..5 && (filter.exactRating == null || rating == filter.exactRating)
    }
}

/**
 * Quick Rate turns RATED's All ratings view into a rating workflow over the authoritative Songs
 * catalog. Normal RATED and exact-star filters retain rated-only membership.
 */
internal fun projectSongsForRatedCollection(
    songs: List<Song>,
    filter: RatedSongFilter,
    ratingsByReferenceKey: Map<String, Int>,
    quickRateActive: Boolean
): List<Song> = when {
    quickRateActive && filter == RatedSongFilter.ALL -> songs
    quickRateActive && filter == RatedSongFilter.UNRATED -> songs.filter { song ->
        ratingsByReferenceKey[song.membershipKey()] !in 1..5
    }
    else -> filterSongsForRatedCollection(songs, filter, ratingsByReferenceKey)
}

internal fun ratedCollectionEmptyMessage(
    filter: RatedSongFilter,
    searchQuery: String,
    quickRateActive: Boolean
): String = when {
    quickRateActive && filter == RatedSongFilter.ALL && searchQuery.isNotBlank() ->
        "No songs match your search."
    searchQuery.isNotBlank() -> "No rated songs match your search."
    filter != RatedSongFilter.ALL -> "No songs match this rating filter."
    else -> "No rated songs yet."
}
