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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.ui.state.PlaybackProgressUiState
import io.github.rsgarrido.sazanami.ui.state.PlaybackUiState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Isolated production shell. Synthetic viewports exercise layout without live presentation dispatch. */
class PocketHorizonLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val song = Song(31L, "A title", "An artist", "An album", 1, 180_000L,
        Uri.parse("content://media/external/audio/media/31"), "", "", null)
    private var viewport by mutableStateOf(DpSize(1000.dp, 640.dp))
    private var fontScale by mutableStateOf(1f)
    private var playback by mutableStateOf(PlaybackUiState(isConnected = true, currentSong = song))
    private val progress = MutableStateFlow(PlaybackProgressUiState(currentPosition = 30_000, duration = 180_000))
    private var renderedDensity = 0.5f

    private fun content() {
        compose.setContent {
            MaterialTheme {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    // Binary density steps keep exact minimum-dp dimensions stable after pixel rounding.
                    var density = 0.5f
                    while (density > 0.001f && (viewport.width.value * density > constraints.maxWidth ||
                        viewport.height.value * density > constraints.maxHeight)) {
                        density /= 2f
                    }
                    SideEffect { renderedDensity = density }
                    CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                        Box(Modifier.requiredSize(viewport.width, viewport.height).testTag("horizonTestHost")) {
                            PocketHorizonExpandedPlayer(
                                playbackState = playback,
                                progressState = progress,
                                onPlayPauseClick = {}, onPreviousClick = {}, onNextClick = {}, onSeekChange = {},
                                seekEnabled = true,
                                onOpenQueueHubClick = {}, onOpenMoreClick = {}, onOpenLyrics = {}
                            )
                        }
                    }
                }
            }
        }
    }

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag, useUnmergedTree = true)
        .fetchSemanticsNode().boundsInRoot

    private fun assertContains(parent: Rect, child: Rect, name: String) {
        assertTrue("$name left", child.left >= parent.left - 1f)
        assertTrue("$name top", child.top >= parent.top - 1f)
        assertTrue("$name right", child.right <= parent.right + 1f)
        assertTrue("$name bottom", child.bottom <= parent.bottom + 1f)
    }

    private fun assertSupportedContentFits() {
        val host = bounds("horizonTestHost")
        val shell = bounds("pocketHorizonShell")
        val display = bounds("pocketHorizonDisplay")
        assertContains(host, shell, "shell")
        assertContains(shell, display, "display")
        listOf("pocketHorizonStatus", "pocketHorizonArtwork", "pocketHorizonTitle",
            "pocketHorizonArtist", "pocketHorizonAlbum", "pocketHorizonPrevious",
            "pocketHorizonPlayPause", "pocketHorizonNext", "pocketHorizonSeek",
            "pocketHorizonTimes", "pocketHorizonQueue", "pocketHorizonMore",
            "pocketHorizonLyrics").forEach { tag -> assertContains(display, bounds(tag), tag) }

        val artwork = bounds("pocketHorizonArtwork")
        val title = bounds("pocketHorizonTitle")
        val artist = bounds("pocketHorizonArtist")
        val album = bounds("pocketHorizonAlbum")
        val controls = bounds("pocketHorizonPlayPause")
        val seek = bounds("pocketHorizonSeek")
        val times = bounds("pocketHorizonTimes")
        assertTrue("Artwork stays beside metadata", artwork.right <= title.left + 1f)
        assertTrue("Title and artist do not overlap", title.bottom <= artist.top + 1f)
        assertTrue("Artist and album do not overlap", artist.bottom <= album.top + 1f)
        assertTrue("Album and seek do not overlap", album.bottom <= seek.top + 1f)
        assertTrue("Seek and times do not overlap", seek.bottom <= times.top + 1f)
        assertTrue("Times and transport do not overlap", times.bottom <= controls.top + 1f)
        assertTrue("Metadata and transport do not overlap", album.bottom <= controls.top + 1f)
        assertFalse("Transport and seek targets do not overlap", controls.overlaps(seek))
        assertTrue("Display remains the dominant shell surface", display.width * display.height > shell.width * shell.height * .8f)
    }

    @Test
    fun minimumTypicalAndLargeLandscapeViewportsContainTheRealControls() {
        viewport = minimumPocketHorizonSize(1f)
        content()
        listOf(minimumPocketHorizonSize(1f), DpSize(1000.dp, 640.dp), DpSize(1600.dp, 1000.dp)).forEach { size ->
            compose.runOnIdle { viewport = size }
            assertSupportedContentFits()
        }
    }

    @Test
    fun largeFontMinimumPreservesMetadataAndTouchTargetsWithoutScalingTheSurface() {
        fontScale = 2f
        viewport = minimumPocketHorizonSize(2f)
        playback = playback.copy(currentSong = song.copy(title = "Large readable track title",
            artist = "Large readable artist name", album = "Album"))
        content()
        assertSupportedContentFits()
        val title = textLayout("pocketHorizonTitle")
        val artist = textLayout("pocketHorizonArtist")
        assertFalse("Title must not lose a line vertically", title.didOverflowHeight)
        assertFalse("Artist must not lose a line vertically", artist.didOverflowHeight)
        assertTrue(title.lineCount <= 2)
        assertTrue(artist.lineCount <= 2)
        assertTrue("64 dp main transport target is preserved",
            bounds("pocketHorizonPlayPause").height >= 64f * renderedDensity - 1f)
    }

    @Test
    fun longAndUnusualMetadataUsesBoundedLinesWithoutOverlappingControls() {
        viewport = minimumPocketHorizonSize(1f)
        playback = playback.copy(currentSong = song.copy(
            title = "A very long title with 日本語 and العربية and ♫ ".repeat(14),
            artist = "An artist whose full name exceeds the display width ".repeat(14),
            album = "An album title that is intentionally longer than one line ".repeat(8)))
        content()
        assertSupportedContentFits()
        assertEquals(2, textLayout("pocketHorizonTitle").lineCount)
        assertEquals(2, textLayout("pocketHorizonArtist").lineCount)
        assertEquals(1, textLayout("pocketHorizonAlbum").lineCount)
        listOf("pocketHorizonTitle", "pocketHorizonArtist", "pocketHorizonAlbum").forEach {
            val layout = textLayout(it)
            // More text may intentionally be ellipsized; every displayed line must fit vertically.
            assertTrue("$it visible lines fit", layout.getLineBottom(layout.lineCount - 1) <= layout.size.height + 1f)
        }
    }

    @Test
    fun insufficientHeightKeepsControlsReachableByScrolling() {
        viewport = DpSize(760.dp, 300.dp)
        content()
        val shell = bounds("pocketHorizonShell")
        assertContains(bounds("horizonTestHost"), shell, "small shell")
        compose.onNodeWithTag("pocketHorizonSeek").performScrollTo()
        assertContains(bounds("pocketHorizonDisplay"), bounds("pocketHorizonSeek"), "scrolled seek")
        compose.onNodeWithTag("pocketHorizonTitle").performScrollTo()
        assertContains(bounds("pocketHorizonDisplay"), bounds("pocketHorizonTitle"), "scrolled title")
    }

    @Test
    fun noTrackMissingMetadataAndUnavailableProgressHaveHonestFallbacks() {
        content()
        compose.onNodeWithTag("pocketHorizonArtwork").assertExists()
        compose.runOnIdle {
            playback = playback.copy(currentSong = song.copy(title = "", artist = "  ", album = ""))
        }
        compose.onNodeWithTag("pocketHorizonTitle")
            .assertTextContains(compose.activity.getString(R.string.library_song_unknown_title))
        compose.onNodeWithTag("pocketHorizonArtist")
            .assertTextContains(compose.activity.getString(R.string.library_song_unknown_artist))
        compose.onNodeWithTag("pocketHorizonAlbum")
            .assertTextContains(compose.activity.getString(R.string.library_song_unknown_album))
        compose.runOnIdle {
            playback = playback.copy(currentSong = null)
            progress.value = PlaybackProgressUiState.Empty
        }
        compose.onNodeWithTag("pocketHorizonTitle")
            .assertTextContains(compose.activity.getString(R.string.pocket_horizon_no_track))
        compose.onNodeWithTag("pocketHorizonTimes")
            .assertTextContains(compose.activity.getString(R.string.pocket_horizon_time_unavailable), substring = true)
    }

    private fun textLayout(tag: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                assertTrue(action(results))
            }
        return results.single()
    }
}
