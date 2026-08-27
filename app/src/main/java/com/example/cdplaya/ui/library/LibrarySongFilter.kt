package com.example.cdplaya.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cdplaya.data.Song
import com.example.cdplaya.data.UNKNOWN_GENRE_KEY
import com.example.cdplaya.data.UNKNOWN_GENRE_NAME
import com.example.cdplaya.data.buildGenreCollections
import com.example.cdplaya.ui.AppShellAccent
import com.example.cdplaya.ui.AppShellIconButton

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

@Composable
fun LibrarySongFilterButton(
    songs: List<Song>,
    state: LibrarySongFilterState,
    onStateChanged: (LibrarySongFilterState) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(LibraryFilterMenuPage.ROOT) }
    val genreOptions = remember(songs) { availableLibraryGenreFilters(songs) }
    val yearOptions = remember(songs) { availableLibraryYears(songs) }
    val selectedGenreName = genreOptions
        .firstOrNull { option -> option.key == state.genre?.key }
        ?.name
        ?: state.genre?.name
        ?: "All genres"
    val selectedYearName = state.year.displayName()

    Box(modifier = modifier) {
        AppShellIconButton(
            onClick = {
                page = LibraryFilterMenuPage.ROOT
                expanded = true
            },
            imageVector = Icons.Filled.FilterList,
            contentDescription = if (state.isActive) {
                "Filter songs, ${state.activeFilterCount} active"
            } else {
                "Filter songs"
            },
            accented = state.isActive
        )

        if (state.isActive) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
                    .size(18.dp),
                shape = CircleShape,
                color = AppShellAccent,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = state.activeFilterCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                page = LibraryFilterMenuPage.ROOT
            }
        ) {
            when (page) {
                LibraryFilterMenuPage.ROOT -> {
                    FilterCategoryMenuItem(
                        title = "Genre",
                        selection = selectedGenreName,
                        onClick = { page = LibraryFilterMenuPage.GENRE }
                    )
                    FilterCategoryMenuItem(
                        title = "Year",
                        selection = selectedYearName,
                        onClick = { page = LibraryFilterMenuPage.YEAR }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Clear filters") },
                        enabled = state.isActive,
                        onClick = {
                            onStateChanged(state.clear())
                            expanded = false
                        }
                    )
                }

                LibraryFilterMenuPage.GENRE -> {
                    FilterMenuBackItem(title = "Genre") {
                        page = LibraryFilterMenuPage.ROOT
                    }
                    FilterSelectionMenuItem(
                        title = "All genres",
                        selected = state.genre == null,
                        onClick = {
                            onStateChanged(state.copy(genre = null))
                            expanded = false
                        }
                    )
                    genreOptions.forEach { option ->
                        FilterSelectionMenuItem(
                            title = option.name,
                            selected = state.genre?.key == option.key,
                            onClick = {
                                onStateChanged(state.copy(genre = option))
                                expanded = false
                            }
                        )
                    }
                }

                LibraryFilterMenuPage.YEAR -> {
                    FilterMenuBackItem(title = "Year") {
                        page = LibraryFilterMenuPage.ROOT
                    }
                    FilterSelectionMenuItem(
                        title = "All years",
                        selected = state.year == LibraryYearFilter.All,
                        onClick = {
                            onStateChanged(state.copy(year = LibraryYearFilter.All))
                            expanded = false
                        }
                    )
                    yearOptions.forEach { year ->
                        FilterSelectionMenuItem(
                            title = year.toString(),
                            selected = state.year == LibraryYearFilter.Exact(year),
                            onClick = {
                                onStateChanged(
                                    state.copy(year = LibraryYearFilter.Exact(year))
                                )
                                expanded = false
                            }
                        )
                    }
                    FilterSelectionMenuItem(
                        title = "Unknown Year",
                        selected = state.year == LibraryYearFilter.Unknown,
                        onClick = {
                            onStateChanged(state.copy(year = LibraryYearFilter.Unknown))
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterCategoryMenuItem(
    title: String,
    selection: String,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Column {
                Text(title)
                Text(
                    text = selection,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        onClick = onClick
    )
}

@Composable
private fun FilterMenuBackItem(
    title: String,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(title, fontWeight = FontWeight.SemiBold) },
        leadingIcon = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back to filters"
            )
        },
        onClick = onClick
    )
    HorizontalDivider()
}

@Composable
private fun FilterSelectionMenuItem(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(title) },
        leadingIcon = {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = AppShellAccent
                )
            }
        },
        onClick = onClick
    )
}

private enum class LibraryFilterMenuPage {
    ROOT,
    GENRE,
    YEAR
}

private fun LibraryYearFilter.displayName(): String = when (this) {
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
