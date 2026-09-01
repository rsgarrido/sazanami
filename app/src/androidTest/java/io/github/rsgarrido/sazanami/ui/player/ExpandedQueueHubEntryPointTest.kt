package io.github.rsgarrido.sazanami.ui.player

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onAllNodesWithText
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.player.RepeatMode
import io.github.rsgarrido.sazanami.ui.player.classicwheel.ClassicWheelNowPlayingDisplay
import io.github.rsgarrido.sazanami.ui.player.modern.ModernQueueHubButton
import io.github.rsgarrido.sazanami.ui.player.pocketcassette.PocketCassetteControls
import io.github.rsgarrido.sazanami.ui.player.pocketflip.PocketFlipControlHalf
import io.github.rsgarrido.sazanami.ui.player.retrorack.RetroRackExpandedPlayer
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ExpandedQueueHubEntryPointTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun modernExpandedControlAreaHasOneQueueHubAction() {
        var openCount = 0
        composeRule.setContent {
            MaterialTheme {
                ModernQueueHubButton(onClick = { openCount += 1 })
            }
        }

        assertSingleQueueHubActionAndClick()
        composeRule.runOnIdle { assertEquals(1, openCount) }
    }

    @Test
    fun classicWheelExistingQueueControlOpensQueueHubWithoutADuplicateAction() {
        var openCount = 0
        composeRule.setContent {
            MaterialTheme {
                ClassicWheelNowPlayingDisplay(
                    currentSong = null,
                    currentPosition = 0,
                    duration = 0,
                    isCurrentSongFavorite = false,
                    isShuffleEnabled = false,
                    repeatMode = RepeatMode.OFF,
                    musicVolume = 0,
                    maxMusicVolume = 1,
                    isVolumeIndicatorVisible = false,
                    onSeekChange = {},
                    onShuffleClick = {},
                    onRepeatClick = {},
                    onOpenUpNextClick = { openCount += 1 },
                    onToggleFavoriteClick = {}
                )
            }
        }

        assertSingleQueueHubActionAndClick()
        composeRule.runOnIdle { assertEquals(1, openCount) }
    }

    @Test
    fun retroRackExistingQueueControlOpensQueueHubWithoutADuplicateAction() {
        var openCount = 0
        composeRule.setContent {
            MaterialTheme {
                RetroRackExpandedPlayer(
                    currentSong = null,
                    isVisualizerWorkAllowed = false,
                    isPlaying = false,
                    isShuffleEnabled = false,
                    repeatMode = RepeatMode.OFF,
                    currentPosition = 0,
                    duration = 0,
                    isCurrentSongFavorite = false,
                    upcomingSongs = emptyList(),
                    onPlayPauseClick = {},
                    onPreviousClick = {},
                    onNextClick = {},
                    onSeekChange = {},
                    onShuffleClick = {},
                    onRepeatClick = {},
                    onCollapseClick = {},
                    onOpenUpNextClick = { openCount += 1 },
                    onToggleFavoriteClick = {},
                    onSongClick = { _, _ -> }
                )
            }
        }

        assertSingleQueueHubActionAndClick()
        composeRule.runOnIdle { assertEquals(1, openCount) }
    }

    @Test
    fun retroRackRendersAuthoritativeQueueIncludingDuplicateEntries() {
        val duplicate = song(7L, "Duplicate")
        composeRule.setContent {
            MaterialTheme {
                RetroRackExpandedPlayer(
                    currentSong = duplicate,
                    isVisualizerWorkAllowed = false,
                    isPlaying = true,
                    isShuffleEnabled = false,
                    repeatMode = RepeatMode.OFF,
                    currentPosition = 0,
                    duration = 180_000,
                    isCurrentSongFavorite = false,
                    upcomingSongs = emptyList(),
                    activeQueueSongs = listOf(duplicate, song(8L, "Middle"), duplicate),
                    onPlayPauseClick = {},
                    onPreviousClick = {},
                    onNextClick = {},
                    onSeekChange = {},
                    onShuffleClick = {},
                    onRepeatClick = {},
                    onCollapseClick = {},
                    onOpenUpNextClick = {},
                    onToggleFavoriteClick = {},
                    onSongClick = { _, _ -> }
                )
            }
        }

        composeRule.onAllNodesWithText("Duplicate", substring = true)
            .assertCountEquals(2)
        composeRule.onAllNodesWithText("Middle", substring = true)
            .assertCountEquals(1)
    }

    @Test
    fun pocketFlipReusesItsSingleExistingQueueControl() {
        var flipOpenCount = 0
        composeRule.setContent {
            MaterialTheme {
                PocketFlipControlHalf(
                    currentSong = null,
                    isPlaying = false,
                    isShuffleEnabled = false,
                    repeatMode = RepeatMode.OFF,
                    isCurrentSongFavorite = false,
                    onPlayPauseClick = {},
                    onPreviousClick = {},
                    onNextClick = {},
                    onShuffleClick = {},
                    onRepeatClick = {},
                    onOpenUpNextClick = { flipOpenCount += 1 },
                    onCollapseClick = {},
                    onToggleFavoriteClick = {},
                    compact = true,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        assertSingleQueueHubActionAndClick()
        composeRule.runOnIdle { assertEquals(1, flipOpenCount) }
    }

    @Test
    fun pocketCassetteReusesItsSingleExistingQueueControl() {
        var cassetteOpenCount = 0
        composeRule.setContent {
            MaterialTheme {
                PocketCassetteControls(
                    currentSong = null,
                    isPlaying = false,
                    isShuffleEnabled = false,
                    repeatMode = RepeatMode.OFF,
                    currentPosition = 0,
                    duration = 0,
                    isCurrentSongFavorite = false,
                    onPlayPauseClick = {},
                    onPreviousClick = {},
                    onNextClick = {},
                    onSeekChange = {},
                    onShuffleClick = {},
                    onRepeatClick = {},
                    onOpenUpNextClick = { cassetteOpenCount += 1 },
                    onToggleFavoriteClick = {},
                    compact = true
                )
            }
        }
        assertSingleQueueHubActionAndClick()
        composeRule.runOnIdle { assertEquals(1, cassetteOpenCount) }
    }

    private fun assertSingleQueueHubActionAndClick() {
        composeRule.onAllNodesWithContentDescription("Open queues").assertCountEquals(1)
        composeRule.onNodeWithContentDescription("Open queues").performClick()
    }

    private fun song(id: Long, title: String) = Song(
        id = id,
        title = title,
        artist = "Artist",
        album = "Album",
        trackNumber = id.toInt(),
        duration = 180_000L,
        uri = Uri.parse("content://media/$id"),
        filePath = "/music/$id.flac",
        folderPath = "/music",
        albumArtUri = null,
        volumeName = "external",
        displayName = "$id.flac",
        relativePath = "Music/",
        fileSizeBytes = 1_000L,
        dateModifiedEpochSeconds = 1L
    )
}
