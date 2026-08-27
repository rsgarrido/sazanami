package com.example.cdplaya.ui.library

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.cdplaya.controller.SongRatingUiState
import com.example.cdplaya.data.Song
import com.example.cdplaya.data.membershipKey
import com.example.cdplaya.ui.ratings.LocalSongRatingUi
import com.example.cdplaya.ui.ratings.SongRatingUiEnvironment
import com.example.cdplaya.ui.LibrarySortAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SongsRatingIntegrationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun quickRateActionIsOnlyVisibleInRatedCollection() {
        val selectedTab = mutableStateOf(LibraryTab.SONGS)
        val quickRateMode = mutableStateOf(false)
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalSongRatingUi provides SongRatingUiEnvironment(
                        quickRateMode = quickRateMode.value,
                        onQuickRateModeChanged = { quickRateMode.value = it }
                    )
                ) {
                    LibrarySortAction(
                        selectedLibraryTab = selectedTab.value,
                        selectedArtistName = null,
                        selectedAlbumFolderPath = null,
                        selectedSongSortState = if (selectedTab.value == LibraryTab.RATED) {
                            LibrarySortState(
                                LibrarySortOption.RATING,
                                LibrarySortDirection.DESCENDING
                            )
                        } else {
                            LibrarySortState(
                                LibrarySortOption.TITLE,
                                LibrarySortDirection.ASCENDING
                            )
                        },
                        selectedArtistSortState = LibrarySortState(
                            LibrarySortOption.NAME,
                            LibrarySortDirection.ASCENDING
                        ),
                        selectedAlbumSortState = LibrarySortState(
                            LibrarySortOption.TITLE,
                            LibrarySortDirection.ASCENDING
                        ),
                        selectedFavoriteSortState = LibrarySortState(
                            LibrarySortOption.TITLE,
                            LibrarySortDirection.ASCENDING
                        ),
                        onSongSortStateChanged = {},
                        onArtistSortStateChanged = {},
                        onAlbumSortStateChanged = {},
                        onFavoriteSortStateChanged = {}
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("Start Quick Rate").assertDoesNotExist()
        composeRule.runOnIdle { selectedTab.value = LibraryTab.RATED }
        composeRule.onNodeWithContentDescription("Start Quick Rate").assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithContentDescription("Exit Quick Rate").assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithContentDescription("Start Quick Rate").assertIsDisplayed()
    }

    @Test
    fun ratedFilterRowOffersAllAndExactStarChoices() {
        val selectedFilter = mutableStateOf(RatedSongFilter.ALL)
        composeRule.setContent {
            MaterialTheme {
                RatedSongFilterRow(
                    selectedFilter = selectedFilter.value,
                    onFilterSelected = { selectedFilter.value = it }
                )
            }
        }

        composeRule.onNodeWithContentDescription("All ratings").assertIsDisplayed()
        (1..5).forEach { rating ->
            composeRule.onNodeWithContentDescription(
                "$rating star${if (rating == 1) "" else "s"}"
            ).assertExists()
        }
        composeRule.onNodeWithContentDescription("4 stars").performClick()
        composeRule.runOnIdle { assertEquals(RatedSongFilter.FOUR, selectedFilter.value) }
    }

    @Test
    fun ratedListShowsCompactRatingsAndReactsImmediately() {
        val low = song(1, "Low")
        val high = song(2, "High")
        val unrated = song(3, "Unrated song")
        val state = mutableStateOf(
            SongRatingUiState(
                ratingsByReferenceKey = mapOf(
                    low.membershipKey() to 1,
                    high.membershipKey() to 5
                )
            )
        )
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalSongRatingUi provides SongRatingUiEnvironment(
                        state = state.value
                    )
                ) {
                    RatedSongsTabContent(
                        songs = listOf(low, high, unrated),
                        searchQuery = "",
                        sortState = LibrarySortState(
                            LibrarySortOption.RATING,
                            LibrarySortDirection.DESCENDING
                        ),
                        currentSong = null,
                        viewMode = LibraryViewMode.LIST,
                        gridColumnCount = 2,
                        recentlyAddedSongIds = emptySet(),
                        onSongClick = { _, _ -> },
                        onPlayNextClick = {},
                        onAddToQueueClick = {},
                        favoriteMembershipKeys = emptySet(),
                        onToggleFavoriteClick = {},
                        onAddToPlaylistClick = {},
                        onEditSongTagsClick = {}
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("Rated 5 out of 5").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Rated 1 out of 5").assertIsDisplayed()
        composeRule.onNodeWithText("Unrated song").assertDoesNotExist()
        val highY = composeRule.onNodeWithText("High").fetchSemanticsNode().positionInRoot.y
        val lowY = composeRule.onNodeWithText("Low").fetchSemanticsNode().positionInRoot.y
        assertTrue(highY < lowY)

        composeRule.runOnIdle {
            state.value = state.value.copy(
                ratingsByReferenceKey = state.value.ratingsByReferenceKey +
                    (unrated.membershipKey() to 4)
            )
        }
        val updatedHighY = composeRule.onNodeWithText("High").fetchSemanticsNode().positionInRoot.y
        val unratedY = composeRule.onNodeWithText("Unrated song").fetchSemanticsNode().positionInRoot.y
        val updatedLowY = composeRule.onNodeWithText("Low").fetchSemanticsNode().positionInRoot.y
        assertTrue(updatedHighY < unratedY)
        assertTrue(unratedY < updatedLowY)
        composeRule.onNodeWithContentDescription("Rated 4 out of 5").assertIsDisplayed()
    }

    @Test
    fun ratedListAndGridUseTheSameExactStarFilterAndCompactIndicator() {
        val five = song(1, "Five")
        val four = song(2, "Four")
        val viewMode = mutableStateOf(LibraryViewMode.LIST)
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalSongRatingUi provides SongRatingUiEnvironment(
                        state = SongRatingUiState(
                            ratingsByReferenceKey = mapOf(
                                five.membershipKey() to 5,
                                four.membershipKey() to 4
                            )
                        )
                    )
                ) {
                    RatedSongsTabContent(
                        songs = listOf(five, four),
                        searchQuery = "",
                        sortState = LibrarySortState(
                            LibrarySortOption.RATING,
                            LibrarySortDirection.DESCENDING
                        ),
                        selectedFilter = RatedSongFilter.FOUR,
                        currentSong = null,
                        viewMode = viewMode.value,
                        gridColumnCount = 2,
                        recentlyAddedSongIds = emptySet(),
                        favoriteMembershipKeys = emptySet(),
                        onSongClick = { _, _ -> },
                        onPlayNextClick = {},
                        onAddToQueueClick = {},
                        onToggleFavoriteClick = {},
                        onAddToPlaylistClick = {},
                        onEditSongTagsClick = {}
                    )
                }
            }
        }

        composeRule.onNodeWithText("Four").assertIsDisplayed()
        composeRule.onNodeWithText("Five").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Rated 4 out of 5").assertIsDisplayed()

        composeRule.runOnIdle { viewMode.value = LibraryViewMode.GRID }
        composeRule.onNodeWithText("Four").assertIsDisplayed()
        composeRule.onNodeWithText("Five").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Rated 4 out of 5").assertIsDisplayed()
    }

    @Test
    fun searchPresentationCanDisableRatingBadgeFilterAndSort() {
        val song = song(1, "Search result")
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalSongRatingUi provides SongRatingUiEnvironment(
                        state = SongRatingUiState(
                            ratingsByReferenceKey = mapOf(song.membershipKey() to 5)
                        ),
                        filter = SongRatingFilter.RATED
                    )
                ) {
                    SongsTabContent(
                        songs = listOf(song),
                        searchQuery = "Search",
                        sortState = LibrarySortState(
                            LibrarySortOption.RATING,
                            LibrarySortDirection.DESCENDING
                        ),
                        currentSong = null,
                        viewMode = LibraryViewMode.LIST,
                        gridColumnCount = 2,
                        recentlyAddedSongIds = emptySet(),
                        onSongClick = { _, _ -> },
                        onPlayNextClick = {},
                        onAddToQueueClick = {},
                        favoriteMembershipKeys = emptySet(),
                        onToggleFavoriteClick = {},
                        onAddToPlaylistClick = {},
                        onEditSongTagsClick = {},
                        ratingFeaturesEnabled = false
                    )
                }
            }
        }
        composeRule.onNodeWithText("Search result").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Rated 5 out of 5").assertDoesNotExist()
    }

    private fun song(id: Long, title: String) = Song(
        id = id,
        title = title,
        artist = "Artist",
        album = "Album",
        trackNumber = 1,
        duration = 1_000,
        uri = Uri.parse("content://song/$id"),
        filePath = "/music/$id.mp3",
        folderPath = "/music",
        albumArtUri = null,
        displayName = "$id.mp3"
    )

}
