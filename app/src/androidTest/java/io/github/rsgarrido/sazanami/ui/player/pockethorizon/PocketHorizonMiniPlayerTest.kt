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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.ui.player.mini.MiniPlayerCallbacks
import io.github.rsgarrido.sazanami.ui.player.mini.MiniPlayerState
import io.github.rsgarrido.sazanami.ui.state.LocalPlaybackPlayPauseIntent
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PocketHorizonMiniPlayerTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var state by mutableStateOf(MiniPlayerState(song(), false, 30_000, 120_000, 44.dp))
    private var width by mutableStateOf(600.dp)
    private var height by mutableStateOf(200.dp)
    private var fontScale by mutableStateOf(1f)
    private var direction by mutableStateOf(LayoutDirection.Ltr)
    private var input by mutableStateOf(true)
    private var playback by mutableStateOf(true)
    private var previous by mutableStateOf(true)
    private var next by mutableStateOf(true)
    private var intent by mutableStateOf<Boolean?>(null)
    private var densityPx = .5f
    private val calls = IntArray(5)
    private val callbacks = MiniPlayerCallbacks({ calls[0]++ }, { calls[1]++ }, { calls[2]++ },
        { calls[3]++ }, { calls[4]++ })

    private fun mount() = compose.setContent {
        MaterialTheme {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                var density = .5f
                while (width.value * density > constraints.maxWidth || height.value * density > constraints.maxHeight) density /= 2f
                SideEffect { densityPx = density }
                CompositionLocalProvider(LocalDensity provides Density(density, fontScale),
                    LocalLayoutDirection provides direction, LocalPlaybackPlayPauseIntent provides intent) {
                    Box(Modifier.requiredSize(width, height)) {
                        PocketHorizonMiniPlayer(state, callbacks, inputEnabled = input, playbackEnabled = playback,
                            previousEnabled = previous, nextEnabled = next)
                    }
                }
            }
        }
    }

    private fun node(suffix: String) = compose.onNodeWithTag("pocketHorizonMini$suffix", useUnmergedTree = true)

    @Test fun realMetadataAndActionsForwardExactlyOnceWithoutChangingPlaybackState() {
        val original = state
        mount()
        node("Title").assertTextEquals("A real track")
        node("Artist").assertTextEquals("An artist")
        node("Expand").performClick()
        node("PlayPause").performClick()
        node("Previous").performClick()
        node("Next").performClick()
        compose.runOnIdle {
            assertArrayEquals(intArrayOf(1, 1, 1, 0, 1), calls)
            assertEquals(original, state)
        }
    }

    @Test fun unavailablePlaybackAndEndpointInputGateOnlyTheirRealActions() {
        mount()
        compose.runOnIdle { previous = false; next = false; playback = false }
        listOf("PlayPause", "Previous", "Next").forEach {
            node(it).assertIsNotEnabled().performSemanticsAction(SemanticsActions.OnClick) { action -> action() }
        }
        node("Expand").assertIsEnabled()
        compose.runOnIdle { input = false }
        node("Expand").assertIsNotEnabled().performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle { assertArrayEquals(IntArray(5), calls) }
    }

    @Test fun losingEndpointInputCancelsAnUnfinishedPlaybackTap() {
        mount()
        node("PlayPause").performTouchInput { down(center) }
        compose.runOnIdle { input = false }
        node("PlayPause").performTouchInput { up() }
        compose.runOnIdle { assertArrayEquals(IntArray(5), calls) }
    }

    @Test fun intentControlsTheAccessibleActionAndProgressRemainsReadOnly() {
        mount()
        compose.runOnIdle { intent = true }
        node("PlayPause").assertContentDescriptionEquals(compose.activity.getString(R.string.player_pause))
        node("PlayPause").performClick()
        compose.runOnIdle { assertEquals(1, calls[0]); assertFalse(state.isPlaying); intent = null }
        node("PlayPause").assertContentDescriptionEquals(compose.activity.getString(R.string.player_play))
        val progress = node("Progress").fetchSemanticsNode().config
        assertEquals(.25f, progress[SemanticsProperties.ProgressBarRangeInfo].current, .0001f)
        assertFalse(progress.contains(SemanticsActions.SetProgress))
        compose.runOnIdle { state = state.copy(isPlaying = true, currentPosition = 180_000, duration = 120_000) }
        node("PlayPause").assertContentDescriptionEquals(compose.activity.getString(R.string.player_pause))
        assertEquals(1f, node("Progress").fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current, 0f)
    }

    @Test fun blankMetadataMissingArtworkAndTrackReplacementHaveSafeRealFallbacks() {
        state = state.copy(currentSong = song().copy(title = "", artist = "", albumArtUri = null), duration = 0)
        mount()
        node("Title").assertTextEquals(compose.activity.getString(R.string.player_unknown_title))
        node("Artist").assertTextEquals(compose.activity.getString(R.string.player_unknown_artist))
        node("Artwork").assertIsDisplayed()
        compose.runOnIdle { state = state.copy(currentSong = song().copy(id = 2, title = "Replacement", artist = "New artist",
            albumArtUri = Uri.parse("content://artwork/2"))) }
        node("Title").assertTextEquals("Replacement")
        node("Artist").assertTextEquals("New artist")
        node("Artwork").assertIsDisplayed()
        compose.runOnIdle { assertArrayEquals(IntArray(5), calls) }
    }

    @Test fun widthsLargeTextAndRtlPreserveContainedAccessibleTouchRegions() {
        state = state.copy(currentSong = song().copy(title = "Very long 日本語 عنوان title ".repeat(30), artist = "Long artist ".repeat(30)))
        mount()
        for ((availableWidth, scale) in listOf(240.dp to 1f, 360.dp to 2f, 600.dp to 2f, 600.dp to 3f)) {
            compose.runOnIdle { width = availableWidth; fontScale = scale; direction = LayoutDirection.Rtl }
            val surface = node("").fetchSemanticsNode().boundsInRoot
            listOf("Expand", "PlayPause").forEach { tag ->
                val actual = node(tag).assertIsDisplayed().fetchSemanticsNode()
                assertEquals(Role.Button, actual.config[SemanticsProperties.Role])
                assertTrue(actual.boundsInRoot.width >= 48f * densityPx - 1f)
                assertTrue(actual.boundsInRoot.height >= 48f * densityPx - 1f)
                assertTrue(surface.contains(actual.boundsInRoot.center))
            }
            val text = node("Title").fetchSemanticsNode().boundsInRoot
            val play = node("PlayPause").fetchSemanticsNode().boundsInRoot
            assertTrue(text.left >= play.right - 1f)
            assertTrue(text.top >= surface.top && text.bottom <= surface.bottom)
            if (scale >= 2f) node("Artist").assertDoesNotExist()
        }
    }

    @Test fun insufficientHostDoesNotPublishClippedControls() {
        width = 120.dp
        mount()
        node("").assertDoesNotExist()
        node("PlayPause").assertDoesNotExist()
        compose.runOnIdle { width = 600.dp; height = 40.dp }
        node("PlayPause").assertDoesNotExist()
        compose.runOnIdle { assertArrayEquals(IntArray(5), calls) }
    }

    private fun song() = Song(1, "A real track", "An artist", "An album", 1, 120_000L,
        Uri.parse("content://media/external/audio/media/1"), "/music/1.flac", "/music", null)
}
