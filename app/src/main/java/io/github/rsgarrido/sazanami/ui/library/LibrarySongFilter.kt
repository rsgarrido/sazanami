package io.github.rsgarrido.sazanami.ui.library

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.UNKNOWN_GENRE_KEY
import io.github.rsgarrido.sazanami.data.UNKNOWN_GENRE_NAME
import io.github.rsgarrido.sazanami.data.buildGenreCollections

data class LibraryGenreFilter(
    val key: String,
    val name: String
)

sealed interface LibraryYearFilter {
    data object All : LibraryYearFilter
    data object Unknown : LibraryYearFilter
    data class Exact(val year: Int) : LibraryYearFilter
}

data class LibrarySongFilterState(
    val genre: LibraryGenreFilter? = null,
    val year: LibraryYearFilter = LibraryYearFilter.All
) {
    val activeFilterCount: Int
        get() = listOfNotNull(
            genre,
            year.takeUnless { it == LibraryYearFilter.All }
        ).size

    val isActive: Boolean
        get() = activeFilterCount > 0

    fun clear(): LibrarySongFilterState = LibrarySongFilterState()
}

val LibrarySongFilterStateSaver: Saver<LibrarySongFilterState, Any> =
    listSaver<LibrarySongFilterState, String>(
        save = { state -> state.toStorageValues() },
        restore = { values -> librarySongFilterStateFromStorageValues(values) }
    )

internal fun LibrarySongFilterState.toStorageValues(): List<String> = listOf(
    genre?.key.orEmpty(),
    genre?.name.orEmpty(),
    year.toStorageValue()
)

internal fun librarySongFilterStateFromStorageValues(
    values: List<String>
): LibrarySongFilterState = LibrarySongFilterState(
    genre = values[0].takeIf(String::isNotBlank)?.let { key ->
        LibraryGenreFilter(key = key, name = values[1])
    },
    year = libraryYearFilterFromStorageValue(values[2])
)

internal data class LibrarySongsCollectionKey(
    val sortState: LibrarySortState,
    val filterState: LibrarySongFilterState
)

fun filterSongsForLibraryMetadata(
    songs: List<Song>,
    filterState: LibrarySongFilterState
): List<Song> {
    val genreFilteredSongs = filterState.genre?.let { selectedGenre ->
        buildGenreCollections(songs)
            .firstOrNull { genre -> genre.key == selectedGenre.key }
            ?.songs
            .orEmpty()
    } ?: songs

    return when (val yearFilter = filterState.year) {
        LibraryYearFilter.All -> genreFilteredSongs
        LibraryYearFilter.Unknown -> genreFilteredSongs.filter { song -> song.year == null }
        is LibraryYearFilter.Exact -> genreFilteredSongs.filter { song ->
            song.year == yearFilter.year
        }
    }
}

internal fun availableLibraryGenreFilters(songs: List<Song>): List<LibraryGenreFilter> {
    val groupedGenres = buildGenreCollections(songs)
    val knownGenres = groupedGenres
        .filterNot { genre -> genre.key == UNKNOWN_GENRE_KEY }
        .map { genre -> LibraryGenreFilter(genre.key, genre.name) }
    val unknownGenre = groupedGenres
        .firstOrNull { genre -> genre.key == UNKNOWN_GENRE_KEY }
        ?.let { genre -> LibraryGenreFilter(genre.key, genre.name) }
        ?: LibraryGenreFilter(UNKNOWN_GENRE_KEY, UNKNOWN_GENRE_NAME)
    return knownGenres + unknownGenre
}

internal fun availableLibraryYears(songs: List<Song>): List<Int> = songs
    .mapNotNull(Song::year)
    .distinct()
    .sortedDescending()

internal fun LibraryYearFilter.displayName(): String = when (this) {
    LibraryYearFilter.All -> "All years"
    LibraryYearFilter.Unknown -> "Unknown Year"
    is LibraryYearFilter.Exact -> year.toString()
}

private fun LibraryYearFilter.toStorageValue(): String = when (this) {
    LibraryYearFilter.All -> "all"
    LibraryYearFilter.Unknown -> "unknown"
    is LibraryYearFilter.Exact -> "exact:$year"
}

private fun libraryYearFilterFromStorageValue(value: String): LibraryYearFilter =
    when {
        value == "unknown" -> LibraryYearFilter.Unknown
        value.startsWith("exact:") -> value.substringAfter(':').toIntOrNull()
            ?.let { year -> LibraryYearFilter.Exact(year) }
            ?: LibraryYearFilter.All
        else -> LibraryYearFilter.All
    }
