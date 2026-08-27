package com.example.cdplaya.ui.library

import android.net.Uri
import com.example.cdplaya.data.Song
import com.example.cdplaya.data.UNKNOWN_GENRE_KEY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class LibrarySongFilterTest {
    @Test
    fun genreFilteringReusesNormalizedGenreMembership() {
        val punk = song(1, genres = listOf(" Punk "))
        val mixedCasePunk = song(2, genres = listOf("pUnK", "Rock"))
        val jazz = song(3, genres = listOf("Jazz"))

        assertEquals(
            listOf(mixedCasePunk, punk),
            filterSongsForLibraryMetadata(
                listOf(jazz, mixedCasePunk, punk),
                LibrarySongFilterState(
                    genre = LibraryGenreFilter("known:punk", "Punk")
                )
            )
        )
    }

    @Test
    fun exactAndUnknownYearFiltersUseAuthoritativeSongYear() {
        val from1994 = song(1, year = 1994)
        val from2004 = song(2, year = 2004)
        val unknown = song(3, year = null)
        val songs = listOf(unknown, from2004, from1994)

        assertEquals(
            listOf(from1994),
            filterSongsForLibraryMetadata(
                songs,
                LibrarySongFilterState(year = LibraryYearFilter.Exact(1994))
            )
        )
        assertEquals(
            listOf(unknown),
            filterSongsForLibraryMetadata(
                songs,
                LibrarySongFilterState(year = LibraryYearFilter.Unknown)
            )
        )
    }

    @Test
    fun genreAndYearComposeWhileAllSelectionsReturnTheSource() {
        val punk1994 = song(1, genres = listOf("Punk"), year = 1994)
        val punk2004 = song(2, genres = listOf("Punk"), year = 2004)
        val rock1994 = song(3, genres = listOf("Rock"), year = 1994)
        val songs = listOf(punk2004, rock1994, punk1994)

        assertEquals(
            listOf(punk1994),
            filterSongsForLibraryMetadata(
                songs,
                LibrarySongFilterState(
                    genre = LibraryGenreFilter("known:punk", "Punk"),
                    year = LibraryYearFilter.Exact(1994)
                )
            )
        )
        assertEquals(songs, filterSongsForLibraryMetadata(songs, LibrarySongFilterState()))
    }

    @Test
    fun unknownGenreUsesGenreBrowserSemantics() {
        val unknown = song(1, genres = listOf("<unknown>"))
        val blank = song(2, genres = emptyList())
        val known = song(3, genres = listOf("Rock"))

        assertEquals(
            listOf(unknown, blank),
            filterSongsForLibraryMetadata(
                listOf(known, unknown, blank),
                LibrarySongFilterState(
                    genre = LibraryGenreFilter(UNKNOWN_GENRE_KEY, "Unknown Genre")
                )
            )
        )
    }

    @Test
    fun missingSelectedValuesRemainActiveAndProduceNoResults() {
        val source = listOf(song(1, genres = listOf("Rock"), year = 2004))
        val missingGenre = LibrarySongFilterState(
            genre = LibraryGenreFilter("known:shoegaze", "Shoegaze")
        )
        val missingYear = LibrarySongFilterState(year = LibraryYearFilter.Exact(1994))

        assertTrue(missingGenre.isActive)
        assertTrue(missingYear.isActive)
        assertEquals(emptyList<Song>(), filterSongsForLibraryMetadata(source, missingGenre))
        assertEquals(emptyList<Song>(), filterSongsForLibraryMetadata(source, missingYear))
    }

    @Test
    fun recomputedSongMetadataImmediatelyChangesFilterMembership() {
        val original = song(1, genres = listOf("Rock"), year = 2004)
        val rockFilter = LibrarySongFilterState(
            genre = LibraryGenreFilter("known:rock", "Rock")
        )
        val yearFilter = LibrarySongFilterState(year = LibraryYearFilter.Exact(2004))

        assertEquals(listOf(original), filterSongsForLibraryMetadata(listOf(original), rockFilter))
        assertEquals(
            emptyList<Song>(),
            filterSongsForLibraryMetadata(
                listOf(original.copy(genres = listOf("Punk"))),
                rockFilter
            )
        )
        assertEquals(
            emptyList<Song>(),
            filterSongsForLibraryMetadata(listOf(original.copy(year = 2005)), yearFilter)
        )
    }

    @Test
    fun activeCountAndClearAreIndependentFromSortState() {
        val sortState = LibrarySortState(
            LibrarySortOption.YEAR,
            LibrarySortDirection.DESCENDING
        )
        val filterState = LibrarySongFilterState(
            genre = LibraryGenreFilter("known:punk", "Punk"),
            year = LibraryYearFilter.Exact(1994)
        )
        val clearedKey = LibrarySongsCollectionKey(sortState, filterState.clear())

        assertEquals(2, filterState.activeFilterCount)
        assertFalse(filterState.clear().isActive)
        assertEquals(sortState, clearedKey.sortState)
    }

    @Test
    fun filterStateStorageRetainsGenreDisplayAndExactYear() {
        val state = LibrarySongFilterState(
            genre = LibraryGenreFilter("known:shoegaze", "Shoegaze"),
            year = LibraryYearFilter.Exact(1994)
        )

        assertEquals(
            state,
            librarySongFilterStateFromStorageValues(state.toStorageValues())
        )
    }

    @Test
    fun filterChangesResetScrollIntentButRestoredStateDoesNot() {
        val sortState = LibrarySortState(
            LibrarySortOption.TITLE,
            LibrarySortDirection.ASCENDING
        )
        val initialKey = LibrarySongsCollectionKey(sortState, LibrarySongFilterState())
        val tracker = SortChangeResetTracker(initialKey)

        assertFalse(tracker.shouldReset(initialKey))
        assertTrue(
            tracker.shouldReset(
                LibrarySongsCollectionKey(
                    sortState,
                    LibrarySongFilterState(year = LibraryYearFilter.Exact(1994))
                )
            )
        )

        val restoredKey = LibrarySongsCollectionKey(
            sortState,
            LibrarySongFilterState(year = LibraryYearFilter.Exact(1994))
        )
        assertFalse(SortChangeResetTracker(restoredKey).shouldReset(restoredKey))
    }

    @Test
    fun selectorYearsAreNewestFirstAndUnknownGenreIsLast() {
        val songs = listOf(
            song(1, genres = listOf("Rock"), year = 1994),
            song(2, genres = listOf("Jazz"), year = 2026),
            song(3, genres = emptyList(), year = 2004)
        )

        assertEquals(listOf(2026, 2004, 1994), availableLibraryYears(songs))
        assertEquals(UNKNOWN_GENRE_KEY, availableLibraryGenreFilters(songs).last().key)
    }

    @Test
    fun organizeSelectorsKeepMissingSelectionsVisibleInDeterministicOrder() {
        val unknown = LibraryGenreFilter(UNKNOWN_GENRE_KEY, "Unknown Genre")
        val missing = LibraryGenreFilter("known:shoegaze", "Shoegaze")

        assertEquals(
            listOf("known:rock", "known:shoegaze", UNKNOWN_GENRE_KEY),
            organizeGenreOptions(
                availableOptions = listOf(
                    LibraryGenreFilter("known:rock", "Rock"),
                    unknown
                ),
                selectedGenre = missing
            ).map(LibraryGenreFilter::key)
        )
        assertEquals(
            listOf(2026, 2004, 1994),
            organizeYearOptions(
                availableYears = listOf(2026, 1994),
                selectedYear = LibraryYearFilter.Exact(2004)
            )
        )
    }

    @Test
    fun organizeButtonDescriptionReflectsOnlyActiveFilterCount() {
        assertEquals("Organize library", organizeButtonContentDescription(0))
        assertEquals(
            "Organize library, 1 active filter",
            organizeButtonContentDescription(1)
        )
        assertEquals(
            "Organize library, 2 active filters",
            organizeButtonContentDescription(2)
        )
    }

    private fun song(
        id: Long,
        genres: List<String> = emptyList(),
        year: Int? = null
    ): Song {
        val uri = mock(Uri::class.java)
        `when`(uri.toString()).thenReturn("content://media/external/audio/$id")
        return Song(
            id = id,
            title = "Song $id",
            artist = "Artist",
            album = "Album",
            trackNumber = 1,
            duration = 1_000L,
            uri = uri,
            filePath = "/music/$id.mp3",
            folderPath = "/music",
            albumArtUri = null,
            genres = genres,
            year = year
        )
    }
}
