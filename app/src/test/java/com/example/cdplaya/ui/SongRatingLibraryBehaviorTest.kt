package com.example.cdplaya.ui

import android.net.Uri
import com.example.cdplaya.ui.library.SongRatingFilter
import com.example.cdplaya.data.Song
import com.example.cdplaya.data.membershipKey
import com.example.cdplaya.ui.library.LibrarySortOption
import com.example.cdplaya.ui.library.RatedSongFilter
import com.example.cdplaya.ui.library.filterSongsForRatedCollection
import com.example.cdplaya.ui.library.normalizeRatedSongFilterForQuickRateMode
import com.example.cdplaya.ui.library.projectSongsForRatedCollection
import com.example.cdplaya.ui.library.visibleRatedSongFilters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
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

    @Test
    fun ratedCollectionSupportsEveryExactStarFilter() {
        val songs = (1L..6L).map { id -> song(id, "Song $id") }
        val ratings = songs.take(5).associate { candidate ->
            candidate.membershipKey() to candidate.id.toInt()
        }

        assertEquals(
            songs.take(5),
            filterSongsForRatedCollection(songs, RatedSongFilter.ALL, ratings)
        )
        listOf(
            RatedSongFilter.FIVE to 5,
            RatedSongFilter.FOUR to 4,
            RatedSongFilter.THREE to 3,
            RatedSongFilter.TWO to 2,
            RatedSongFilter.ONE to 1
        ).forEach { (filter, rating) ->
            assertEquals(
                listOf(songs[rating - 1]),
                filterSongsForRatedCollection(songs, filter, ratings)
            )
            assertEquals(
                listOf(songs[rating - 1]),
                projectSongsForRatedCollection(
                    songs,
                    filter,
                    ratings,
                    quickRateActive = true
                )
            )
        }
    }

    @Test
    fun ratedFilterOptionsIncludeUnratedInTheRequestedOrder() {
        assertEquals(
            listOf(
                "All ratings",
                "Unrated",
                "5 stars",
                "4 stars",
                "3 stars",
                "2 stars",
                "1 star"
            ),
            RatedSongFilter.entries.map { filter -> filter.label }
        )
    }

    @Test
    fun unratedFilterVisibilityFollowsQuickRateMode() {
        assertEquals(
            listOf(
                RatedSongFilter.ALL,
                RatedSongFilter.FIVE,
                RatedSongFilter.FOUR,
                RatedSongFilter.THREE,
                RatedSongFilter.TWO,
                RatedSongFilter.ONE
            ),
            visibleRatedSongFilters(quickRateActive = false)
        )
        assertEquals(
            RatedSongFilter.entries,
            visibleRatedSongFilters(quickRateActive = true)
        )
    }

    @Test
    fun exitingQuickRateResetsOnlyAnUnratedFilter() {
        assertEquals(
            RatedSongFilter.ALL,
            normalizeRatedSongFilterForQuickRateMode(
                filter = RatedSongFilter.UNRATED,
                quickRateActive = false
            )
        )
        assertEquals(
            RatedSongFilter.FOUR,
            normalizeRatedSongFilterForQuickRateMode(
                filter = RatedSongFilter.FOUR,
                quickRateActive = false
            )
        )
    }

    @Test
    fun quickRateUnratedShowsOnlySongsWithoutAValidRating() {
        val absent = song(1, "Absent rating")
        val five = song(2, "Five")
        val three = song(3, "Three")
        val zero = song(4, "Zero rating")
        val source = listOf(absent, five, three, zero)
        val ratings = mapOf(
            five.membershipKey() to 5,
            three.membershipKey() to 3,
            zero.membershipKey() to 0
        )

        assertEquals(
            listOf(absent, zero),
            projectSongsForRatedCollection(
                source,
                RatedSongFilter.UNRATED,
                ratings,
                quickRateActive = true
            )
        )
        assertEquals(
            emptyList<Song>(),
            projectSongsForRatedCollection(
                source,
                RatedSongFilter.UNRATED,
                ratings,
                quickRateActive = false
            )
        )
    }

    @Test
    fun ratingAnUnratedSongImmediatelyRemovesItFromQuickRateUnrated() {
        val candidate = song(1, "Needs rating")

        assertEquals(
            listOf(candidate),
            projectSongsForRatedCollection(
                listOf(candidate),
                RatedSongFilter.UNRATED,
                emptyMap(),
                quickRateActive = true
            )
        )
        assertEquals(
            emptyList<Song>(),
            projectSongsForRatedCollection(
                listOf(candidate),
                RatedSongFilter.UNRATED,
                mapOf(candidate.membershipKey() to 4),
                quickRateActive = true
            )
        )
    }

    @Test
    fun clearingARatingImmediatelyAddsTheSongToQuickRateUnrated() {
        val candidate = song(1, "Clear rating")

        assertEquals(
            emptyList<Song>(),
            projectSongsForRatedCollection(
                listOf(candidate),
                RatedSongFilter.UNRATED,
                mapOf(candidate.membershipKey() to 5),
                quickRateActive = true
            )
        )
        assertEquals(
            listOf(candidate),
            projectSongsForRatedCollection(
                listOf(candidate),
                RatedSongFilter.UNRATED,
                emptyMap(),
                quickRateActive = true
            )
        )
    }

    @Test
    fun normalRatedExcludesUnratedWhileQuickRateAllUsesTheFullCatalog() {
        val unrated = song(1, "Unrated")
        val five = song(2, "Five")
        val three = song(3, "Three")
        val source = listOf(unrated, five, three)
        val ratings = mapOf(
            five.membershipKey() to 5,
            three.membershipKey() to 3
        )

        assertEquals(
            listOf(five, three),
            projectSongsForRatedCollection(
                source,
                RatedSongFilter.ALL,
                ratings,
                quickRateActive = false
            )
        )
        assertEquals(
            source,
            projectSongsForRatedCollection(
                source,
                RatedSongFilter.ALL,
                ratings,
                quickRateActive = true
            )
        )
    }

    @Test
    fun assigningAndClearingRatingsKeepsQuickRateAllStableAndUpdatesNormalRated() {
        val unrated = song(1, "Unrated")
        val five = song(2, "Five")
        val three = song(3, "Three")
        val source = listOf(unrated, five, three)
        val initialRatings = mapOf(
            five.membershipKey() to 5,
            three.membershipKey() to 3
        )
        val afterAssign = initialRatings + (unrated.membershipKey() to 4)

        assertEquals(
            source,
            projectSongsForRatedCollection(
                source,
                RatedSongFilter.ALL,
                afterAssign,
                quickRateActive = true
            )
        )
        assertEquals(
            source,
            projectSongsForRatedCollection(
                source,
                RatedSongFilter.ALL,
                afterAssign,
                quickRateActive = false
            )
        )

        val afterClear = afterAssign - five.membershipKey()
        assertEquals(
            source,
            projectSongsForRatedCollection(
                source,
                RatedSongFilter.ALL,
                afterClear,
                quickRateActive = true
            )
        )
        assertEquals(
            listOf(unrated, three),
            projectSongsForRatedCollection(
                source,
                RatedSongFilter.ALL,
                afterClear,
                quickRateActive = false
            )
        )
    }

    @Test
    fun quickRateExactFilterExcludesUnratedAndReactsImmediatelyToMutation() {
        val unrated = song(1, "Unrated")
        val five = song(2, "Five")
        val source = listOf(unrated, five)
        val initialRatings = mapOf(five.membershipKey() to 5)

        assertEquals(
            listOf(five),
            projectSongsForRatedCollection(
                source,
                RatedSongFilter.FIVE,
                initialRatings,
                quickRateActive = true
            )
        )
        assertEquals(
            emptyList<Song>(),
            projectSongsForRatedCollection(
                source,
                RatedSongFilter.FIVE,
                mapOf(five.membershipKey() to 4),
                quickRateActive = true
            )
        )
    }

    @Test
    fun zeroRatedCatalogIsEmptyNormallyAndAvailableInQuickRateAll() {
        val source = listOf(song(1, "Alpha"), song(2, "Beta"))

        assertEquals(
            emptyList<Song>(),
            projectSongsForRatedCollection(
                source,
                RatedSongFilter.ALL,
                emptyMap(),
                quickRateActive = false
            )
        )
        assertEquals(
            source,
            projectSongsForRatedCollection(
                source,
                RatedSongFilter.ALL,
                emptyMap(),
                quickRateActive = true
            )
        )
    }

    @Test
    fun quickRateProjectionPreservesStableIdentityAndSourceOrdering() {
        val source = listOf(song(3, "Gamma"), song(1, "Alpha"), song(2, "Beta"))
        val projected = projectSongsForRatedCollection(
            source,
            RatedSongFilter.ALL,
            mapOf(source.first().membershipKey() to 5),
            quickRateActive = true
        )

        assertEquals(
            source.map { song -> song.membershipKey() },
            projected.map { song -> song.membershipKey() }
        )
        val projectedByIdentity = projected.associateBy { song -> song.membershipKey() }
        source.forEach { song ->
            assertSame(song, projectedByIdentity[song.membershipKey()])
        }
    }

    @Test
    fun exactFilterComposesWithRatingSortAndReactsToRatingChanges() {
        val alpha = song(1, "Alpha")
        val beta = song(2, "Beta")
        val source = listOf(beta, alpha)
        val initialRatings = mapOf(
            alpha.membershipKey() to 4,
            beta.membershipKey() to 5
        )

        val initiallyFiltered = filterSongsForRatedCollection(
            source,
            RatedSongFilter.FOUR,
            initialRatings
        )
        assertEquals(
            listOf(alpha),
            sortSongsForLibrary(
                initiallyFiltered,
                LibrarySortOption.RATING_HIGH_TO_LOW,
                initialRatings
            )
        )

        val changedRatings = mapOf(
            alpha.membershipKey() to 5,
            beta.membershipKey() to 4
        )
        assertEquals(
            listOf(beta),
            filterSongsForRatedCollection(source, RatedSongFilter.FOUR, changedRatings)
        )
        assertEquals(
            emptyList<Song>(),
            filterSongsForRatedCollection(
                source,
                RatedSongFilter.FOUR,
                changedRatings - beta.membershipKey()
            )
        )
        assertEquals(
            listOf(alpha),
            filterSongsForRatedCollection(
                source,
                RatedSongFilter.ALL,
                changedRatings - beta.membershipKey()
            )
        )
        assertEquals(
            emptyList<Song>(),
            filterSongsForRatedCollection(source, RatedSongFilter.ALL, emptyMap())
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
