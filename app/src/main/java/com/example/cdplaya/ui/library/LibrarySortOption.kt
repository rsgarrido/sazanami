package com.example.cdplaya.ui.library

enum class LibrarySortOption(val title: String) {
    TITLE("Title"),
    ARTIST("Artist"),
    ALBUM("Album"),
    RATING_HIGH_TO_LOW("Rating: High to low"),
    RATING_LOW_TO_HIGH("Rating: Low to high"),
    DATE_ADDED_NEWEST("Date added: Newest first"),
    DATE_ADDED_OLDEST("Date added: Oldest first"),
    NAME("Name"),
    SONG_COUNT("Song count")
}

internal fun librarySortOptionsFor(
    tab: LibraryTab,
    ratingFeaturesEnabled: Boolean = true
): List<LibrarySortOption> = when (tab) {
    LibraryTab.SONGS -> listOf(
        LibrarySortOption.TITLE,
        LibrarySortOption.ARTIST,
        LibrarySortOption.ALBUM,
        LibrarySortOption.DATE_ADDED_NEWEST
    )

    LibraryTab.RATED -> if (ratingFeaturesEnabled) {
        listOf(
            LibrarySortOption.RATING_HIGH_TO_LOW,
            LibrarySortOption.RATING_LOW_TO_HIGH,
            LibrarySortOption.TITLE,
            LibrarySortOption.ARTIST,
            LibrarySortOption.ALBUM,
            LibrarySortOption.DATE_ADDED_NEWEST
        )
    } else {
        emptyList()
    }

    LibraryTab.RECENTLY_ADDED -> listOf(
        LibrarySortOption.DATE_ADDED_NEWEST,
        LibrarySortOption.DATE_ADDED_OLDEST
    )

    LibraryTab.FAVORITES -> listOf(
        LibrarySortOption.TITLE,
        LibrarySortOption.ARTIST,
        LibrarySortOption.ALBUM,
        LibrarySortOption.DATE_ADDED_NEWEST
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
    LibraryTab.RECENTLY_PLAYED,
    LibraryTab.MOST_PLAYED,
    LibraryTab.QUEUE -> emptyList()
}

internal fun LibraryTab.showsQuickRateAction(ratingFeaturesEnabled: Boolean = true): Boolean =
    this == LibraryTab.RATED && ratingFeaturesEnabled

internal fun LibrarySortOption.displayTitleFor(tab: LibraryTab): String =
    if (tab == LibraryTab.RECENTLY_ADDED) {
        when (this) {
            LibrarySortOption.DATE_ADDED_NEWEST -> "Newest first"
            LibrarySortOption.DATE_ADDED_OLDEST -> "Oldest first"
            else -> title
        }
    } else {
        title
    }
