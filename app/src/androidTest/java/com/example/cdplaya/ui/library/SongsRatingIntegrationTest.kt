package com.example.cdplaya.ui.library

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.example.cdplaya.controller.SongRatingUiState
import com.example.cdplaya.data.Song
import com.example.cdplaya.data.membershipKey
import com.example.cdplaya.ui.ratings.LocalSongRatingUi
import com.example.cdplaya.ui.ratings.SongRatingUiEnvironment
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SongsRatingIntegrationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun ratingSortReactsImmediatelyWithoutInlineIndicators() {
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
                    SongsTabContent(
                        songs = listOf(low, high, unrated),
                        searchQuery = "",
                        sortOption = LibrarySortOption.RATING,
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

        composeRule.onNodeWithContentDescription("Rated 5 out of 5").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Rated 1 out of 5").assertDoesNotExist()
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
                        sortOption = LibrarySortOption.RATING,
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
