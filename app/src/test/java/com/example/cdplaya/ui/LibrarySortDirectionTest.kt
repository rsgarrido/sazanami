package com.example.cdplaya.ui

import android.net.Uri
import com.example.cdplaya.data.Song
import com.example.cdplaya.ui.library.LibrarySortDirection
import com.example.cdplaya.ui.library.LibrarySortOption
import com.example.cdplaya.ui.library.LibrarySortState
import com.example.cdplaya.ui.library.sortedLibraryAlbumGroups
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock

class LibrarySortDirectionTest {
    @Test
    fun titleSortSupportsAscendingAndDescendingWithMissingTitlesLast() {
        val alpha = song(1, title = "Alpha")
        val beta = song(2, title = "Beta")
        val missing = song(3, title = "")
        val songs = listOf(missing, beta, alpha)

        assertEquals(
            listOf(alpha, beta, missing),
            sortSongsForLibrary(
                songs,
                LibrarySortOption.TITLE,
                LibrarySortDirection.ASCENDING
            )
        )
        assertEquals(
            listOf(beta, alpha, missing),
            sortSongsForLibrary(
                songs,
                LibrarySortOption.TITLE,
                LibrarySortDirection.DESCENDING
            )
        )
    }

    @Test
    fun artistSortKeepsMissingMetadataLastInBothDirections() {
        val alpha = song(1, title = "One", artist = "Alpha")
        val zulu = song(2, title = "Two", artist = "Zulu")
        val missing = song(3, title = "Three", artist = "")
        val songs = listOf(missing, zulu, alpha)

        assertEquals(
            listOf(alpha, zulu, missing),
            sortSongsForLibrary(
                songs,
                LibrarySortOption.ARTIST,
                LibrarySortDirection.ASCENDING
            )
        )
        assertEquals(
            listOf(zulu, alpha, missing),
            sortSongsForLibrary(
                songs,
                LibrarySortOption.ARTIST,
                LibrarySortDirection.DESCENDING
            )
        )
    }

    @Test
    fun dateAddedSortSupportsBothDirectionsWithUnknownDatesLast() {
        val unknown = song(1, title = "Unknown", dateAdded = 0L)
        val older = song(2, title = "Older", dateAdded = 100L)
        val newer = song(3, title = "Newer", dateAdded = 200L)
        val songs = listOf(unknown, newer, older)

        assertEquals(
            listOf(older, newer, unknown),
            sortSongsForLibrary(
                songs,
                LibrarySortOption.DATE_ADDED,
                LibrarySortDirection.ASCENDING
            )
        )
        assertEquals(
            listOf(newer, older, unknown),
            sortSongsForLibrary(
                songs,
                LibrarySortOption.DATE_ADDED,
                LibrarySortDirection.DESCENDING
            )
        )
    }

    @Test
    fun yearSortSupportsBothDirectionsWithUnknownYearsLast() {
        val unknown = song(1, title = "Unknown", year = null)
        val older = song(2, title = "Older", year = 1995)
        val middle = song(3, title = "Middle", year = 2004)
        val newer = song(4, title = "Newer", year = 2026)
        val songs = listOf(unknown, newer, older, middle)

        assertEquals(
            listOf(older, middle, newer, unknown),
            sortSongsForLibrary(
                songs,
                LibrarySortOption.YEAR,
                LibrarySortDirection.ASCENDING
            )
        )
        assertEquals(
            listOf(newer, middle, older, unknown),
            sortSongsForLibrary(
                songs,
                LibrarySortOption.YEAR,
                LibrarySortDirection.DESCENDING
            )
        )
    }

    @Test
    fun albumTitleSortKeepsUnknownAlbumsLastWhenDescending() {
        val alpha = song(1, title = "One", album = "Alpha")
        val zulu = song(2, title = "Two", album = "Zulu")
        val unknown = song(3, title = "Three", album = "")

        val sorted = sortedLibraryAlbumGroups(
            listOf(unknown, alpha, zulu),
            LibrarySortState(
                LibrarySortOption.TITLE,
                LibrarySortDirection.DESCENDING
            )
        )

        assertEquals(listOf("Zulu", "Alpha", "Unknown Album"), sorted.map { it.title })
    }

    private fun song(
        id: Long,
        title: String,
        artist: String = "Artist",
        album: String = "Album",
        dateAdded: Long = 1L,
        year: Int? = null
    ) = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        trackNumber = 1,
        duration = 1_000L,
        uri = mock(Uri::class.java),
        filePath = "/music/$id.mp3",
        folderPath = "/music/$id",
        albumArtUri = null,
        dateAddedEpochSeconds = dateAdded,
        year = year
    )
}
