package com.example.cdplaya.ui.library

import com.example.cdplaya.data.Song
import com.example.cdplaya.data.membershipKey

enum class SongRatingFilter { ALL, RATED, UNRATED }

enum class RatedSongFilter(val exactRating: Int?) {
    ALL(null),
    FIVE(5),
    FOUR(4),
    THREE(3),
    TWO(2),
    ONE(1);

    val label: String
        get() = exactRating?.let { rating ->
            "$rating star${if (rating == 1) "" else "s"}"
        } ?: "All ratings"
}

fun filterSongsForRatedCollection(
    songs: List<Song>,
    filter: RatedSongFilter,
    ratingsByReferenceKey: Map<String, Int>
): List<Song> = songs.filter { song ->
    val rating = ratingsByReferenceKey[song.membershipKey()]
    rating in 1..5 && (filter.exactRating == null || rating == filter.exactRating)
}

internal fun ratedCollectionEmptyMessage(
    filter: RatedSongFilter,
    searchQuery: String
): String = when {
    searchQuery.isNotBlank() -> "No rated songs match your search."
    filter != RatedSongFilter.ALL -> "No songs match this rating filter."
    else -> "No rated songs yet."
}
