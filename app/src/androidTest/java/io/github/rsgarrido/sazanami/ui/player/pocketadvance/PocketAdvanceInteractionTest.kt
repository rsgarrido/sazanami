package io.github.rsgarrido.sazanami.ui.player.pocketadvance

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.player.RepeatMode
import io.github.rsgarrido.sazanami.ui.player.PlayerEndpointBounds
import io.github.rsgarrido.sazanami.ui.player.mini.DefaultMiniPlayerMorphCallbacks
import io.github.rsgarrido.sazanami.ui.player.mini.MiniPlayerCallbacks
import io.github.rsgarrido.sazanami.ui.player.mini.MiniPlayerState
import io.github.rsgarrido.sazanami.ui.player.mini.PocketAdvanceMiniPlayer
import io.github.rsgarrido.sazanami.ui.state.DirectScrubAuthority
import io.github.rsgarrido.sazanami.ui.state.DirectSeekGestureEnvironment
import io.github.rsgarrido.sazanami.ui.state.LocalDirectSeekGestureEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Real endpoint controls and production seek adapter; no playback engine or USB execution. */
class PocketAdvanceInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val song = Song(1L, "Advance track", "Sazanami artist", "Album", 1, 180_000L,
        Uri.EMPTY, "", "", null)
    private val commands = mutableListOf<String>()
    private val seeks = mutableListOf<Int>()
    private val miniState get() = MiniPlayerState(song, false, 30_000, 180_000, 36.dp)
    private fun callbacks() = MiniPlayerCallbacks(
        { commands += "play" }, { commands += "previous" }, { commands += "next" },
        { commands += "queue" }, { commands += "expand" }
    )

    private fun expanded(inputEnabled: Boolean = true) {
        compose.setContent {
            val preview = remember { mutableStateOf<Int?>(null) }
            val environment = remember {
                DirectSeekGestureEnvironment({ DirectScrubAuthority(true, 1, 0, true) }) { preview.value = it }
            }
            MaterialTheme {
                CompositionLocalProvider(LocalDirectSeekGestureEnvironment provides environment) {
                    Box(Modifier.requiredSize(1000.dp, 560.dp)) {
                        PocketAdvanceExpandedPlayer(
                            currentSong = song, isPlaying = false, isShuffleEnabled = false,
                            repeatMode = RepeatMode.OFF, currentPosition = preview.value ?: 30_000,
                            duration = 180_000, onPlayPauseClick = { commands += "play" },
                            onPreviousClick = { commands += "previous" }, onNextClick = { commands += "next" },
                            onSeekChange = { seeks += it }, onShuffleClick = { commands += "shuffle" },
                            onRepeatClick = { commands += "repeat" }, onCollapseClick = { commands += "collapse" },
                            onOpenUpNextClick = { commands += "queue" }, onMoreClick = { commands += "more" },
                            inputEnabled = inputEnabled, collapseGestureEnabled = false,
                            onOpenArtist = { commands += "artist:${it.id}" },
                            onOpenAlbum = { commands += "album:${it.id}" }
                        )
                    }
                }
            }
        }
    }

    @Test
    fun deviceButtonsForwardDistinctExistingCommands() {
        expanded()
        // Semantics actions also exercise hardware controls on phone-sized instrumentation hosts.
        listOf(
            R.string.player_previous_song, R.string.player_next_song, R.string.player_shuffle_off,
            R.string.player_repeat_off, R.string.player_play, R.string.player_collapse,
            R.string.player_open_queues, R.string.player_more_actions
        ).forEach { label ->
            compose.onNodeWithContentDescription(compose.activity.getString(label))
                .performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
        }
        compose.runOnIdle {
            assertEquals(listOf("previous", "next", "shuffle", "repeat", "play", "collapse", "queue", "more"), commands)
            assertTrue(seeks.isEmpty())
        }
    }

    @Test
    fun metadataActionsRetainSongIdentityAndAccessibleActionLabels() {
        expanded()
        assertEquals(compose.activity.getString(R.string.library_search_go_artist),
            compose.onNodeWithTag("pocketAdvanceArtist").fetchSemanticsNode().config[SemanticsActions.OnClick].label)
        assertEquals(compose.activity.getString(R.string.player_go_to_album),
            compose.onNodeWithTag("pocketAdvanceAlbum").fetchSemanticsNode().config[SemanticsActions.OnClick].label)
        compose.onNodeWithTag("pocketAdvanceArtist").performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
        compose.onNodeWithTag("pocketAdvanceAlbum").performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
        compose.runOnIdle { assertEquals(listOf("artist:1", "album:1"), commands) }
    }

    @Test
    fun seekAccessibilityUsesSharedImmediateAuthorityExactlyOnce() {
        expanded()
        compose.onNodeWithTag("pocketAdvanceSeek")
            .performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(90_000f)) }
        compose.runOnIdle { assertEquals(listOf(90_000), seeks); assertTrue(commands.isEmpty()) }
    }

    @Test
    fun cancelledDirectSeekDoesNotCommitAndTheNextTapStillWorks() {
        expanded()
        compose.onNodeWithTag("pocketAdvanceSeek").performTouchInput {
            down(Offset(width * .2f, centerY)); moveTo(Offset(width * .8f, centerY)); cancel()
        }
        compose.runOnIdle {
            assertTrue("Cancelled seek committed: $seeks", seeks.isEmpty())
            assertTrue("Seek gesture dispatched unrelated commands: $commands", commands.isEmpty())
        }
        compose.onNodeWithTag("pocketAdvanceSeek").performTouchInput { down(center); up() }
        compose.runOnIdle {
            assertEquals("The tap after cancellation must commit exactly once: $seeks", 1, seeks.size)
            assertTrue("The center tap must seek to the midpoint: $seeks", seeks.single() in 85_000..95_000)
            assertTrue("Seek tap dispatched unrelated commands: $commands", commands.isEmpty())
        }
    }

    @Test
    fun directSeekDragStillCommitsOnlyOnceOnNormalRelease() {
        expanded()
        compose.onNodeWithTag("pocketAdvanceSeek").performTouchInput {
            down(Offset(width * .2f, centerY)); moveTo(Offset(width * .4f, centerY))
        }
        compose.runOnIdle { assertTrue("A held drag must only preview: $seeks", seeks.isEmpty()) }
        compose.onNodeWithTag("pocketAdvanceSeek").performTouchInput {
            moveTo(Offset(width * .8f, centerY)); up()
        }
        compose.runOnIdle {
            assertEquals("Normal release must commit exactly once: $seeks", 1, seeks.size)
            assertTrue("The final sample must be committed: $seeks", seeks.single() in 143_000..145_000)
            assertTrue(commands.isEmpty())
        }
    }

    @Test
    fun endpointControlsAreDisabledUntilTheMorphAllowsInput() {
        expanded(inputEnabled = false)
        listOf("pocketAdvancePrevious", "pocketAdvanceNext", "pocketAdvancePlay", "pocketAdvanceCollapse",
            "pocketAdvanceArtist", "pocketAdvanceAlbum").forEach {
            compose.onNodeWithTag(it).assertIsNotEnabled()
        }
        compose.runOnIdle { assertTrue(commands.isEmpty()); assertTrue(seeks.isEmpty()) }
    }

    @Test
    @OptIn(ExperimentalLayoutApi::class)
    fun largeTextKeepsFaceButtonTouchTargetsSeparate() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(.25f, 2f)) {
                MaterialTheme {
                    Box(Modifier.requiredSize(1200.dp, 900.dp).consumeWindowInsets(
                        WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout))) {
                        PocketAdvanceExpandedPlayer(
                            currentSong = song, isPlaying = false, isShuffleEnabled = false,
                            repeatMode = RepeatMode.OFF, currentPosition = 30_000, duration = 180_000,
                            onPlayPauseClick = { commands += "play" }, onPreviousClick = {}, onNextClick = {},
                            onSeekChange = { seeks += it }, onShuffleClick = {}, onRepeatClick = {},
                            onCollapseClick = { commands += "collapse" }, onOpenUpNextClick = {}, onMoreClick = {},
                            collapseGestureEnabled = false
                        )
                    }
                }
            }
        }
        val play = compose.onNodeWithTag("pocketAdvancePlay").fetchSemanticsNode().boundsInRoot
        val close = compose.onNodeWithTag("pocketAdvanceCollapse").fetchSemanticsNode().boundsInRoot
        assertTrue(play.width >= 48f * .25f && close.width >= 48f * .25f)
        assertTrue("Long/scaled captions must not move A/B targets into each other", play.left >= close.right)
        compose.runOnIdle { assertTrue(commands.isEmpty()); assertTrue(seeks.isEmpty()) }
    }

    @Test
    fun miniTapPlayAndHorizontalSwipeUseExistingCallbacks() {
        compose.setContent {
            MaterialTheme { Box(Modifier.width(360.dp)) { PocketAdvanceMiniPlayer(miniState, callbacks()) } }
        }
        compose.onNodeWithTag("pocketAdvanceMini").performClick()
        compose.onNodeWithTag("pocketAdvanceMiniPlay").performClick()
        compose.onNodeWithTag("pocketAdvanceMini").performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals(listOf("expand", "play", "next"), commands) }
    }

    @Test
    fun miniVerticalDragAndCancellationDoNotDispatchTrackCommands() {
        val dragEvents = mutableListOf<String>()
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(360.dp)) {
                    PocketAdvanceMiniPlayer(miniState, callbacks(),
                        morphCallbacks = DefaultMiniPlayerMorphCallbacks(
                            { dragEvents += "start" }, { dragEvents += "delta" },
                            { dragEvents += "end" }, { dragEvents += "cancel" }
                        ))
                }
            }
        }
        compose.onNodeWithTag("pocketAdvanceMini").performTouchInput {
            down(center); moveTo(Offset(centerX, centerY - 45f)); cancel()
        }
        compose.runOnIdle {
            assertTrue(dragEvents.contains("start")); assertTrue(dragEvents.contains("delta"))
            assertEquals("cancel", dragEvents.last()); assertTrue(commands.isEmpty())
        }
    }

    @Test
    fun hiddenMiniKeepsAllVisualAnchorsMeasurable() = assertHiddenMiniAnchors()

    @Test
    fun quarterDensityMiniReservesProgressBeforeRoundingCanExhaustItsHeight() =
        assertHiddenMiniAnchors(Density(.25f))

    @Test
    fun halfDensityMiniReservesProgressBeforeRoundingCanExhaustItsHeight() =
        assertHiddenMiniAnchors(Density(.5f))

    private fun assertHiddenMiniAnchors(densityOverride: Density? = null) {
        var measuredDensity = 0f
        val bounds = PocketAdvancePlayerMorphBounds()
        compose.setContent {
            val density = densityOverride ?: LocalDensity.current
            measuredDensity = density.density
            CompositionLocalProvider(LocalDensity provides density) {
                MaterialTheme {
                    Box(Modifier.width(360.dp)) {
                        PocketAdvanceMiniPlayer(miniState, callbacks(), morphBounds = bounds, morphOwnsVisuals = true)
                    }
                }
            }
        }
        compose.runOnIdle {
            val surface = requireNotNull(bounds.miniSurface)
            val endpoints = PlayerEndpointBounds().apply {
                updateMini(surface); updateExpanded(Rect(0f, 0f, 1200f, 800f))
            }
            bounds.updateExpandedSurface(Rect(24f, 24f, 1176f, 776f))
            bounds.updateExpandedDisplay(Rect(270f, 100f, 850f, 650f))
            bounds.updateExpandedArtwork(Rect(300f, 200f, 444f, 344f))
            bounds.updateExpandedTitle(Rect(460f, 200f, 700f, 250f))
            bounds.updateExpandedArtist(Rect(460f, 260f, 700f, 280f))
            bounds.updateExpandedPlayPause(Rect(1000f, 300f, 1056f, 356f))
            bounds.updateExpandedProgress(Rect(300f, 600f, 820f, 608f))
            val geometry = bounds.resolveGeometry(0f, endpoints)
            assertNotNull(geometry)
            assertEquals(surface, geometry!!.shell)
            assertTrue(geometry.artwork.width > 0f && geometry.title.height > 0f && geometry.artist.height > 0f)
            assertTrue(geometry.progressTrack.width > 0f && geometry.progressTrack.height > 0f)
            assertEquals(geometry.artist.bottom, geometry.progressTrack.center.y, 1f)
            assertEquals(geometry.artist.width, geometry.progressTrack.width, 1f)
            assertEquals(3f * measuredDensity, geometry.progressTrack.height, 1f)
            assertTrue(geometry.playPause.width < 48f * measuredDensity)
            assertTrue(commands.isEmpty())
        }
    }
}
