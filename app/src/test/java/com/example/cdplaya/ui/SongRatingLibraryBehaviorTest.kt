package com.example.cdplaya.ui

import android.net.Uri
import com.example.cdplaya.ui.library.SongRatingFilter
import com.example.cdplaya.data.Song
import com.example.cdplaya.data.membershipKey
import com.example.cdplaya.ui.library.LibrarySortOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test
import org.mockito.Mockito.mock

class SongRatingLibraryBehaviorTest {
    @Test
    fun ratingSortIsDescendingUnratedLastWithTitleAndStableKeyTies() {
        val unrated = song(6, "A")
        val fiveB = song(5, "Beta")
        val one = song(4, "One")
        val four = song(3, "Four")
        val fiveA2 = song(2, "Alpha")
        val fiveA1 = song(1, "Alpha")
        val source = listOf(unrated, fiveB, one, four, fiveA2, fiveA1)
        val ratings = mapOf(
            fiveB.membershipKey() to 5,
            one.membershipKey() to 1,
            four.membershipKey() to 4,
            fiveA2.membershipKey() to 5,
            fiveA1.membershipKey() to 5
        )

        val sorted = sortSongsForLibrary(
            source,
            LibrarySortOption.RATING_HIGH_TO_LOW,
            ratings
        )

        assertEquals(
            listOf(fiveA1, fiveA2).sortedBy { it.membershipKey() },
            sorted.take(2)
        )
        assertEquals(listOf(5, 5, 5, 4, 1, null), sorted.map { ratings[it.membershipKey()] })
        assertEquals(source, listOf(unrated, fiveB, one, four, fiveA2, fiveA1))
        assertNotSame(source, sorted)
    }

    @Test
    fun ratingSortSupportsLowToHighWithUnratedLast() {
        val unrated = song(4, "Unrated")
        val high = song(3, "High")
        val low = song(2, "Low")
        val middle = song(1, "Middle")
        val ratings = mapOf(
            high.membershipKey() to 5,
            low.membershipKey() to 1,
            middle.membershipKey() to 3
        )

        val sorted = sortSongsForLibrary(
            listOf(unrated, high, low, middle),
            LibrarySortOption.RATING_LOW_TO_HIGH,
            ratings
        )

        assertEquals(listOf(low, middle, high, unrated), sorted)
    }

    @Test
    fun allRatedAndUnratedFiltersReactToMapChangesAndComposeWithSearch() {
        val alpha = song(1, "Alpha")
        val beta = song(2, "Beta")
        val songs = listOf(alpha, beta)
        val rated = mapOf(alpha.membershipKey() to 3)

        assertEquals(songs, filterSongsByRating(songs, SongRatingFilter.ALL, rated))
        assertEquals(listOf(alpha), filterSongsByRating(songs, SongRatingFilter.RATED, rated))
        assertEquals(listOf(beta), filterSongsByRating(songs, SongRatingFilter.UNRATED, rated))
        assertEquals(
            emptyList<Song>(),
            filterSongsByRating(
                filterSongsForSearch(songs, "beta"),
                SongRatingFilter.RATED,
                rated
            )
        )
        assertEquals(
            emptyList<Song>(),
            filterSongsByRating(songs, SongRatingFilter.UNRATED, rated + (beta.membershipKey() to 4))
        )
        assertEquals(
            emptyList<Song>(),
            filterSongsByRating(songs, SongRatingFilter.RATED, emptyMap())
        )
    }

    private fun song(id: Long, title: String) = Song(
        id = id,
        title = title,
        artist = "Same artist",
        album = "Same album",
        trackNumber = 1,
        duration = 1_000,
        uri = mock(Uri::class.java),
        filePath = "/music/$id.mp3",
        folderPath = "/music",
        albumArtUri = null,
        displayName = "$id.mp3"
    )
}
