package io.github.rsgarrido.sazanami.ui.player.modern

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.player.RepeatMode
import io.github.rsgarrido.sazanami.player.audioquality.AudioQualityInfo
import io.github.rsgarrido.sazanami.ui.player.PlayerMorphState
import io.github.rsgarrido.sazanami.ui.player.PlayerLyricsTransitionState
import io.github.rsgarrido.sazanami.ui.player.PlayerPresentation
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ModernPlayerAudioQualityBadgeTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
    private val pill = hasText("FLAC", substring = true) and
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    @Test fun onlyCurrentSettledPillCanOpenInformationAndItKeepsVerticalDrags() {
        lateinit var player: PlayerMorphState
        lateinit var lyrics: PlayerLyricsTransitionState
        lateinit var carousel: ModernArtworkCarouselPresentation
        var opened = 0
        composeRule.setContent {
            MaterialTheme {
                val scope = rememberCoroutineScope()
                player = remember { PlayerMorphState(PlayerPresentation.Expanded, scope) }
                lyrics = remember { PlayerLyricsTransitionState(false, scope) {} }
                carousel = rememberModernArtworkCarouselPresentation(song(1), song(2), song(3), {}, {})
                ModernExpandedPlayer(
                    currentSong = song(1), previousPreviewSong = song(2), nextPreviewSong = song(3),
                    isPlaying = false, isShuffleEnabled = false, repeatMode = RepeatMode.OFF,
                    currentPosition = 0, duration = 180_000, isCurrentSongFavorite = false,
                    onPlayPauseClick = {}, onPreviousClick = {}, onNextClick = {}, onSeekChange = {},
                    onShuffleClick = {}, onRepeatClick = {}, onCollapseClick = {},
                    playerMorphState = player, lyricsTransitionState = lyrics, onOpenUpNextClick = {},
                    onToggleFavoriteClick = {}, onTrackInfoClick = { opened++ }, carouselPresentation = carousel,
                    defaultMorphDragRangePx = 400f
                )
            }
        }
        composeRule.waitUntil { composeRule.onAllNodes(pill).fetchSemanticsNodes().size == 1 }
        composeRule.onAllNodes(pill).assertCountEquals(1)
        composeRule.onNode(pill).performClick()
        composeRule.runOnIdle { assertEquals(1, opened); carousel.state.updateArtworkWidth(300); carousel.state.dragBy(-100f) }
        composeRule.onAllNodes(pill).assertCountEquals(0)
        composeRule.runOnIdle { carousel.state.dragBy(100f); lyrics.beginOpeningDrag() }
        composeRule.onAllNodes(pill).assertCountEquals(0)
        composeRule.runOnIdle { lyrics.snapToExpanded() }
        composeRule.onNode(pill).performTouchInput {
            swipe(center, center + Offset(0f, 200f), durationMillis = 400)
        }
        composeRule.runOnIdle { assertEquals(1, opened); assertEquals(PlayerPresentation.Collapsed, player.settledPresentation) }
        composeRule.onAllNodes(pill).assertCountEquals(0)
    }

    @Test fun badgeWithoutALiveCallbackRemainsDecorative() {
        composeRule.setContent {
            MaterialTheme {
                ModernPlayerAudioQualityBadge(AudioQualityInfo("FLAC", 16, 44_100, 830), ModernPlayerDefaults.style())
            }
        }
        composeRule.onNodeWithText("16 bit  44.1 kHz  830 kbps  FLAC").assertHasNoClickAction()
    }

    private fun song(id: Long) = Song(id, "Track $id", "Artist", "Album", 1, 180_000L,
        Uri.parse("content://media/external/audio/media/$id"), "/music/$id.flac", "/music", null,
        volumeName = "external", displayName = "$id.flac")
}
