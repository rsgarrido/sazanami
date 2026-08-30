package io.github.rsgarrido.sazanami.ui.statistics

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.rsgarrido.sazanami.data.AlbumListeningStats
import io.github.rsgarrido.sazanami.data.ArtistListeningStats
import io.github.rsgarrido.sazanami.data.ListeningPlayCountBreakdown
import io.github.rsgarrido.sazanami.data.ListeningRankingCategory
import io.github.rsgarrido.sazanami.data.TrackListeningStats
import io.github.rsgarrido.sazanami.controller.SongRatingUiState
import io.github.rsgarrido.sazanami.ui.ratings.LocalSongRatingUi
import io.github.rsgarrido.sazanami.ui.ratings.SongRatingUiEnvironment
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AnalyticsRankingsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun categorySelectionIsImmediateAndDefaultsToTracks() {
        val category = mutableStateOf(ListeningRankingCategory.TRACKS)
        var changes = 0
        composeRule.setContent {
            MaterialTheme {
                TopListeningHeader(
                    selectedCategory = category.value,
                    onCategorySelected = {
                        changes++
                        category.value = it
                    }
                )
            }
        }
        composeRule.onNodeWithText("Tracks").assertIsSelected()
        composeRule.onNodeWithText("Artists").performClick().assertIsSelected()
        composeRule.runOnIdle { assertEquals(1, changes) }
        composeRule.onNodeWithText("Qualified plays").assertExists()
    }

    @Test
    fun trackRowsPreserveDuplicateLookingIdentitiesAndRemainNonClickable() {
        val first = track(11L, "Same title", "同じアーティスト", "Album", 24L, 4_680_000L)
        val second = track(22L, "Same title", "同じアーティスト", "Album", 7L, 0L)
        composeRule.setContent {
            MaterialTheme {
                Column {
                    TrackRankingRow(1, first)
                    TrackRankingRow(2, second)
                }
            }
        }
        composeRule.onAllNodesWithText("Same title").assertCountEquals(2)
        composeRule.onNodeWithText("24 plays").assertExists()
        composeRule.onNodeWithText("1 hr 18 min recorded").assertExists()
        composeRule.onNodeWithContentDescription("1. Same title", substring = true)
            .assertHasNoClickAction()
    }

    @Test
    fun artistAndAlbumRowsShowRepositoryOrderCountsAndRecordedTime() {
        val artist = ArtistListeningStats(
            groupingKey = "artist-key",
            artist = "Björk",
            playCounts = counts(62L),
            confirmedDetailedListeningMs = 14_400_000L,
            naturalCompletionCount = 50L,
            distinctTrackCount = 8L,
            distinctAlbumCount = 3L,
            latestKnownPlayAt = null
        )
        val album = AlbumListeningStats(
            groupingKey = "album-key",
            album = "A Very Long Album Title That Must Not Replace Its Metrics",
            albumArtist = "Various Artists",
            playCounts = counts(41L),
            confirmedDetailedListeningMs = 7_200_000L,
            naturalCompletionCount = 30L,
            trackCount = 12L,
            latestKnownPlayAt = null
        )
        composeRule.setContent {
            MaterialTheme {
                Column {
                    ArtistRankingRow(1, artist)
                    AlbumRankingRow(2, album)
                }
            }
        }
        composeRule.onNodeWithText("Björk").assertExists()
        composeRule.onNodeWithText("62 plays").assertExists()
        composeRule.onNodeWithText("41 plays").assertExists()
        composeRule.onNodeWithText("Various Artists").assertExists()
        composeRule.onNodeWithText("2 hr 0 min recorded").assertExists()
    }

    @Test
    fun topTrackRatingUsesIdentityMapAndUpdatesWithoutChangingRankOrder() {
        val rating = mutableStateOf(2)
        val first = track(11L, "First", "Artist", "Album", 5L, 0L)
        val second = track(22L, "Second", "Artist", "Album", 4L, 0L)
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalSongRatingUi provides SongRatingUiEnvironment(
                        state = SongRatingUiState(
                            ratingsByTrackIdentityId = mapOf(11L to rating.value)
                        )
                    )
                ) {
                    Column {
                        TrackRankingRow(1, first)
                        TrackRankingRow(2, second)
                        ArtistRankingRow(3, ArtistListeningStats(
                            groupingKey = "artist",
                            artist = "Artist only",
                            playCounts = counts(1),
                            confirmedDetailedListeningMs = 0,
                            naturalCompletionCount = 0,
                            distinctTrackCount = 1,
                            distinctAlbumCount = 1,
                            latestKnownPlayAt = null
                        ))
                    }
                }
            }
        }
        composeRule.onNodeWithContentDescription("1. First", substring = true)
            .assertHasNoClickAction()
        composeRule.onNodeWithContentDescription("Rated 2 out of 5").assertExists()
        composeRule.onAllNodesWithText("First").assertCountEquals(1)
        composeRule.onAllNodesWithText("Second").assertCountEquals(1)
        composeRule.runOnIdle { rating.value = 5 }
        composeRule.onNodeWithContentDescription("Rated 5 out of 5").assertExists()
        composeRule.onAllNodesWithContentDescription("Rated", substring = true)
            .assertCountEquals(1)
    }

    private fun track(
        id: Long,
        title: String,
        artist: String,
        album: String,
        plays: Long,
        recordedMs: Long
    ) = TrackListeningStats(
        trackIdentityId = id,
        title = title,
        artist = artist,
        album = album,
        albumArtist = null,
        durationMs = null,
        binding = null,
        playCounts = counts(plays),
        confirmedDetailedListeningMs = recordedMs,
        detailedEventCount = plays,
        naturalCompletionCount = 0L,
        nonQualifiedAttemptCount = 0L,
        firstKnownPlayAt = null,
        latestKnownPlayAt = null,
        latestDetailedEventAt = null
    )

    private fun counts(total: Long) = ListeningPlayCountBreakdown(
        totalPlayCount = total,
        legacyPlayCount = 0L,
        detailedPlayCount = total
    )
}
