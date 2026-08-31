package io.github.rsgarrido.sazanami.ui.player

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
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
}
