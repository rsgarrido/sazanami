package io.github.rsgarrido.sazanami.ui.library

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import java.util.Locale

enum class LibrarySortOption(val title: String) {
    TITLE("Title"),
    ARTIST("Artist"),
    ALBUM("Album"),
    YEAR("Year"),
    RATING("Rating"),
    DATE_ADDED("Date added"),
    NAME("Name"),
    SONG_COUNT("Song count")
}

enum class LibrarySortDirection {
    ASCENDING,
    DESCENDING;

    fun toggled(): LibrarySortDirection = when (this) {
        ASCENDING -> DESCENDING
        DESCENDING -> ASCENDING
    }

    fun applyTo(comparison: Int): Int = when (this) {
        ASCENDING -> comparison
        DESCENDING -> when {
            comparison < 0 -> 1
            comparison > 0 -> -1
            else -> 0
        }
    }
}

data class LibrarySortState(
    val option: LibrarySortOption,
    val direction: LibrarySortDirection
) {
    fun select(option: LibrarySortOption): LibrarySortState = copy(option = option)

    fun toggleDirection(): LibrarySortState = copy(direction = direction.toggled())
}

val LibrarySortStateSaver: Saver<LibrarySortState, Any> = listSaver<LibrarySortState, String>(
    save = { state -> listOf(state.option.name, state.direction.name) },
    restore = { values ->
        LibrarySortState(
            option = LibrarySortOption.valueOf(values[0]),
            direction = LibrarySortDirection.valueOf(values[1])
        )
    }
)

internal fun compareLibraryText(
    left: String,
    right: String,
    direction: LibrarySortDirection
): Int {
    val knownComparison = compareKnownPresence(left.isNotBlank(), right.isNotBlank())
    if (knownComparison != 0 || left.isBlank()) return knownComparison

    return direction.applyTo(
        left.trim().lowercase(Locale.ROOT).compareTo(right.trim().lowercase(Locale.ROOT))
    )
}

internal fun compareKnownPositiveLong(
    left: Long,
    right: Long,
    direction: LibrarySortDirection
): Int {
    val knownComparison = compareKnownPresence(left > 0L, right > 0L)
    if (knownComparison != 0 || left <= 0L) return knownComparison
    return direction.applyTo(left.compareTo(right))
}

internal fun compareKnownPresence(leftKnown: Boolean, rightKnown: Boolean): Int = when {
    leftKnown == rightKnown -> 0
    leftKnown -> -1
    else -> 1
}

internal fun librarySortOptionsFor(
    tab: LibraryTab,
    ratingFeaturesEnabled: Boolean = true
): List<LibrarySortOption> = when (tab) {
    LibraryTab.SONGS -> listOf(
        LibrarySortOption.TITLE,
        LibrarySortOption.ARTIST,
        LibrarySortOption.ALBUM,
        LibrarySortOption.YEAR
    )

    LibraryTab.RATED -> if (ratingFeaturesEnabled) {
        listOf(
            LibrarySortOption.RATING,
            LibrarySortOption.TITLE,
            LibrarySortOption.ARTIST,
            LibrarySortOption.ALBUM
        )
    } else {
        emptyList()
    }

    LibraryTab.RECENTLY_ADDED -> listOf(LibrarySortOption.DATE_ADDED)

    LibraryTab.FAVORITES -> listOf(
        LibrarySortOption.TITLE,
        LibrarySortOption.ARTIST,
        LibrarySortOption.ALBUM
    )

    LibraryTab.ARTISTS -> listOf(
        LibrarySortOption.NAME,
        LibrarySortOption.SONG_COUNT
    )

    LibraryTab.ALBUMS -> listOf(
        LibrarySortOption.TITLE,
        LibrarySortOption.ARTIST,
        LibrarySortOption.SONG_COUNT
    )

    LibraryTab.PLAYLISTS,
    LibraryTab.GENRES,
    LibraryTab.RECENTLY_PLAYED,
    LibraryTab.MOST_PLAYED,
    LibraryTab.QUEUE -> emptyList()
}

internal fun LibraryTab.showsQuickRateAction(ratingFeaturesEnabled: Boolean = true): Boolean =
    this == LibraryTab.RATED && ratingFeaturesEnabled

internal fun LibrarySortOption.displayTitleFor(tab: LibraryTab): String = title
