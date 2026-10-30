package io.github.rsgarrido.sazanami.ui.player.pockethorizon

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.ui.state.DirectScrubAuthority
import io.github.rsgarrido.sazanami.ui.state.DirectSeekGestureEnvironment
import io.github.rsgarrido.sazanami.ui.state.LocalDirectSeekGestureEnvironment
import io.github.rsgarrido.sazanami.ui.state.LocalDirectSeekPreview
import io.github.rsgarrido.sazanami.ui.state.LocalPlaybackPlayPauseIntent
import io.github.rsgarrido.sazanami.ui.state.PlaybackProgressUiState
import io.github.rsgarrido.sazanami.ui.state.PlaybackUiState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Real presentation controls and shared seek protocol; fixtures never start a playback engine. */
class PocketHorizonInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val song = Song(21L, "Horizon track", "Horizon artist", "Horizon album", 1,
        180_000L, Uri.parse("content://media/external/audio/media/21"), "", "", null)
    private var playback by mutableStateOf(PlaybackUiState(isConnected = true, currentSong = song))
    private var inputEnabled by mutableStateOf(true)
    private var seekEnabled by mutableStateOf(true)
    private var previousEnabled by mutableStateOf(true)
    private var nextEnabled by mutableStateOf(true)
    private var playPauseIntent by mutableStateOf<Boolean?>(null)
    private var layoutDirection by mutableStateOf(LayoutDirection.Ltr)
    private var authority = DirectScrubAuthority(true, 1L, 0L, true)
    private val progress = MutableStateFlow(PlaybackProgressUiState(currentPosition = 30_000, duration = 180_000))
    private val actions = mutableListOf<String>()
    private val seeks = mutableListOf<Int>()
    private val artworkRequests = mutableListOf<Song>()
    private lateinit var seekEnvironment: DirectSeekGestureEnvironment
    private var renderedDensity = 0.5f

    private fun content(optionalActions: Boolean = false, artworkAction: Boolean = false) {
        compose.setContent {
            val preview = remember { mutableStateOf<Int?>(null) }
            seekEnvironment = remember {
                DirectSeekGestureEnvironment({ authority }) { preview.value = it }
            }
            MaterialTheme {
                // Keep every real touch target inside the physical test window, even on small emulators.
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    var density = 0.5f
                    while (density > 0.001f &&
                        (1000f * density > constraints.maxWidth || 640f * density > constraints.maxHeight)) {
                        density /= 2f
                    }
                    SideEffect { renderedDensity = density }
                    CompositionLocalProvider(
                        LocalDensity provides Density(density, 1f),
                        LocalLayoutDirection provides layoutDirection,
                        LocalDirectSeekGestureEnvironment provides seekEnvironment,
                        LocalDirectSeekPreview provides preview.value,
                        LocalPlaybackPlayPauseIntent provides playPauseIntent
                    ) {
                        Box(Modifier.requiredSize(1000.dp, 640.dp)) {
                            PocketHorizonExpandedPlayer(
                                playbackState = playback,
                                progressState = progress,
                                onPlayPauseClick = { actions += "playPause" },
                                onPreviousClick = { actions += "previous" },
                                onNextClick = { actions += "next" },
                                onSeekChange = { position ->
                                    seeks += position
                                    authority = authority.copy(seekSerial = authority.seekSerial + 1)
                                },
                                inputEnabled = inputEnabled,
                                seekEnabled = seekEnabled,
                                previousEnabled = previousEnabled,
                                nextEnabled = nextEnabled,
                                onOpenQueueHubClick = if (optionalActions) ({ actions += "queue" }) else null,
                                onOpenMoreClick = if (optionalActions) ({ actions += "more" }) else null,
                                onOpenLyrics = if (optionalActions) ({ actions += "lyrics" }) else null,
                                onViewArtwork = if (artworkAction) ({ artworkRequests += it }) else null
                            )
                        }
                    }
                }
            }
        }
    }

    private fun invoke(tag: String) = compose.onNodeWithTag(tag)
        .performSemanticsAction(SemanticsActions.OnClick) { it() }

    @Test
    fun playbackAndSuppliedOptionalActionsForwardExactlyOnce() {
        content(optionalActions = true)
        listOf("pocketHorizonPrevious", "pocketHorizonPlayPause", "pocketHorizonNext",
            "pocketHorizonQueue", "pocketHorizonMore", "pocketHorizonLyrics").forEach(::invoke)
        compose.runOnIdle {
            assertEquals(listOf("previous", "playPause", "next", "queue", "more", "lyrics"), actions)
            assertTrue(seeks.isEmpty())
        }
    }

    @Test
    fun absentOptionalCallbacksCreateNoPlaceholderActions() {
        content()
        listOf("pocketHorizonQueue", "pocketHorizonMore", "pocketHorizonLyrics").forEach {
            compose.onNodeWithTag(it).assertDoesNotExist()
        }
        compose.onNodeWithTag("pocketHorizonArtwork").assertExists()
        compose.runOnIdle { assertTrue(actions.isEmpty()); assertTrue(artworkRequests.isEmpty()) }
    }

    @Test
    fun connectionTrackAndExplicitCapabilitiesGateActionsWithoutChangingPlaybackState() {
        content(optionalActions = true)
        val original = playback
        compose.runOnIdle { previousEnabled = false; nextEnabled = false; seekEnabled = false }
        listOf("pocketHorizonPrevious", "pocketHorizonNext", "pocketHorizonSeek").forEach {
            compose.onNodeWithTag(it).assertIsNotEnabled()
        }
        invoke("pocketHorizonPrevious")
        invoke("pocketHorizonNext")
        compose.onNodeWithTag("pocketHorizonPlayPause").assertIsEnabled()
        compose.runOnIdle { assertTrue(actions.isEmpty()); assertTrue(seeks.isEmpty()) }

        listOf(original.copy(isConnected = false), original.copy(currentSong = null)).forEach { unavailable ->
            compose.runOnIdle { playback = unavailable }
            listOf("pocketHorizonPrevious", "pocketHorizonPlayPause", "pocketHorizonNext",
                "pocketHorizonSeek", "pocketHorizonQueue", "pocketHorizonMore", "pocketHorizonLyrics").forEach {
                compose.onNodeWithTag(it).assertIsNotEnabled()
                if (it != "pocketHorizonSeek") invoke(it)
            }
            compose.runOnIdle { assertTrue(actions.isEmpty()); assertTrue(seeks.isEmpty()) }
        }

        compose.runOnIdle { playback = original; inputEnabled = false }
        invoke("pocketHorizonPlayPause")
        invoke("pocketHorizonQueue")
        compose.runOnIdle {
            assertTrue(actions.isEmpty())
            assertEquals(original, playback)
        }
    }

    @Test
    fun controlsHaveSingleAccessibleButtonLabelsAndAdequateTargets() {
        content(optionalActions = true)
        val controls = listOf(
            "pocketHorizonPrevious" to R.string.player_previous_song,
            "pocketHorizonPlayPause" to R.string.player_play,
            "pocketHorizonNext" to R.string.player_next_song,
            "pocketHorizonQueue" to R.string.player_open_queues,
            "pocketHorizonMore" to R.string.player_more_actions,
            "pocketHorizonLyrics" to R.string.player_open_lyrics
        )
        controls.forEach { (tag, resource) ->
            val label = compose.activity.getString(resource)
            compose.onAllNodesWithContentDescription(label).assertCountEquals(1)
            val node = compose.onNodeWithTag(tag).fetchSemanticsNode()
            assertEquals(Role.Button, node.config[SemanticsProperties.Role])
            assertEquals(listOf(label), node.config[SemanticsProperties.ContentDescription])
            assertTrue("$tag width", node.boundsInRoot.width >= 48f * renderedDensity - 1f)
            assertTrue("$tag height", node.boundsInRoot.height >= 48f * renderedDensity - 1f)
        }
    }

    @Test
    fun playPauseSemanticsFollowSharedCommandIntentAndThenConfirmedPlayback() {
        content()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.player_play)).assertExists()
        compose.runOnIdle { playback = playback.copy(playPauseIntent = true) }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.player_pause)).assertExists()
        compose.runOnIdle { playPauseIntent = false }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.player_play)).assertExists()
        compose.runOnIdle { playPauseIntent = true }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.player_pause)).assertExists()
        invoke("pocketHorizonPlayPause")
        compose.runOnIdle {
            assertEquals(listOf("playPause"), actions)
            assertEquals(false, playback.isPlaying)
            playPauseIntent = null
            playback = playback.copy(isPlaying = true, playPauseIntent = null)
        }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.player_pause)).assertExists()
    }

    @Test
    fun accessibilitySeekForwardsOneClampedRequestAndUnknownDurationDisablesSeeking() {
        content()
        compose.onNodeWithTag("pocketHorizonSeek").performSemanticsAction(SemanticsActions.SetProgress) {
            assertTrue(it(90_000f))
        }
        compose.runOnIdle { assertEquals(listOf(90_000), seeks) }
        compose.onNodeWithTag("pocketHorizonSeek").performSemanticsAction(SemanticsActions.SetProgress) {
            assertFalse(it(Float.NaN))
            assertFalse(it(Float.POSITIVE_INFINITY))
        }
        compose.onNodeWithTag("pocketHorizonSeek").performSemanticsAction(SemanticsActions.SetProgress) {
            it(500_000f)
        }
        compose.runOnIdle {
            assertEquals(listOf(90_000, 180_000), seeks)
            progress.value = PlaybackProgressUiState.Empty
        }
        compose.onNodeWithTag("pocketHorizonSeek").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(2, seeks.size) }
    }

    @Test
    fun directTouchPreviewsAndCommitsOnlyItsFinalValueOnce() {
        content()
        val slider = compose.onNodeWithTag("pocketHorizonSeek")
        slider.performTouchInput {
            down(Offset(width * .2f, centerY))
            moveTo(Offset(width * .4f, centerY))
        }
        compose.runOnIdle { assertTrue("Touch preview must not dispatch", seeks.isEmpty()) }
        slider.performTouchInput { moveTo(Offset(width * .8f, centerY)); up() }
        compose.runOnIdle {
            assertEquals(1, seeks.size)
            assertTrue("Final position follows the last delta", seeks.single() in 126_000..162_000)
        }
    }

    @Test
    fun directGenerationChangeAndCapabilityLossCancelOldTouchRequests() {
        content()
        val slider = compose.onNodeWithTag("pocketHorizonSeek")
        slider.performTouchInput { down(center); moveTo(Offset(width * .7f, centerY)) }
        compose.runOnIdle {
            authority = authority.copy(generation = 2)
            seekEnvironment.observe()
        }
        slider.performTouchInput { moveTo(Offset(width * .8f, centerY)); up() }
        compose.runOnIdle { assertTrue(seeks.isEmpty()) }
        slider.performTouchInput { down(center); moveTo(Offset(width * .7f, centerY)) }
        compose.runOnIdle { seekEnabled = false }
        slider.performTouchInput { up() }
        compose.runOnIdle { assertTrue(seeks.isEmpty()); seekEnabled = true }
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(60_000f) }
        compose.runOnIdle { assertEquals(listOf(60_000), seeks) }
    }

    @Test
    fun replacingTrackRetiresAnUnfinishedGestureEvenWhenDurationIsUnchanged() {
        content()
        val slider = compose.onNodeWithTag("pocketHorizonSeek")
        slider.performTouchInput { down(center); moveTo(Offset(width * .7f, centerY)) }
        compose.runOnIdle {
            playback = playback.copy(currentSong = song.copy(id = 23L,
                uri = Uri.parse("content://media/external/audio/media/23"), title = "New track"))
        }
        slider.performTouchInput { up() }
        compose.runOnIdle { assertTrue("Old track gesture must be retired", seeks.isEmpty()) }
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(45_000f) }
        compose.runOnIdle { assertEquals(listOf(45_000), seeks) }
    }

    @Test
    fun normalPlaybackKeepsItsEstablishedImmediateSeekRouting() {
        authority = authority.copy(enabled = false)
        content()
        val slider = compose.onNodeWithTag("pocketHorizonSeek")
        slider.performTouchInput {
            down(Offset(width * .2f, centerY))
            moveTo(Offset(width * .4f, centerY))
        }
        compose.runOnIdle { assertTrue("Normal mode forwards changes before release", seeks.isNotEmpty()) }
        slider.performTouchInput { moveTo(Offset(width * .8f, centerY)); up() }
        compose.runOnIdle { assertTrue(seeks.all { it in 0..180_000 }) }
    }

    @Test
    fun directTouchRespectsRightToLeftProgressDirection() {
        layoutDirection = LayoutDirection.Rtl
        content()
        val slider = compose.onNodeWithTag("pocketHorizonSeek")
        slider.performTouchInput { down(Offset(width * .25f, centerY)); up() }
        slider.performTouchInput { down(Offset(width * .75f, centerY)); up() }
        compose.runOnIdle {
            assertEquals(2, seeks.size)
            assertTrue("The left side represents a later position in RTL", seeks[0] > seeks[1])
            assertTrue(seeks.all { it in 0..180_000 })
        }
    }

    @Test
    fun artworkActionTargetsTheCurrentSongAndDisappearsWithMissingArtwork() {
        val first = song.copy(albumArtUri = Uri.parse("content://pocket-horizon-tests/artwork/first"))
        val replacement = song.copy(id = 22L, title = "Replacement track",
            uri = Uri.parse("content://media/external/audio/media/22"),
            albumArtUri = Uri.parse("content://pocket-horizon-tests/artwork/next"))
        playback = playback.copy(currentSong = first)
        content(artworkAction = true)
        invoke("pocketHorizonArtwork")
        compose.runOnIdle { playback = playback.copy(currentSong = replacement) }
        invoke("pocketHorizonArtwork")
        compose.runOnIdle {
            assertEquals(listOf(first, replacement), artworkRequests)
            inputEnabled = false
        }
        invoke("pocketHorizonArtwork")
        compose.runOnIdle {
            inputEnabled = true
            playback = playback.copy(isConnected = false)
        }
        invoke("pocketHorizonArtwork")
        compose.runOnIdle {
            assertEquals(listOf(first, replacement), artworkRequests)
            playback = playback.copy(currentSong = replacement.copy(albumArtUri = null))
        }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.player_view_artwork)).assertDoesNotExist()
        compose.onNodeWithTag("pocketHorizonArtwork").assertExists()
    }
}
