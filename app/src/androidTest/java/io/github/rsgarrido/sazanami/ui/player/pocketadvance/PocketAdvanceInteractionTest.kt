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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
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
    private lateinit var navigation: PocketAdvanceNavigationState
    private val miniState get() = MiniPlayerState(song, false, 30_000, 180_000, 36.dp)
    private fun callbacks() = MiniPlayerCallbacks(
        { commands += "play" }, { commands += "previous" }, { commands += "next" },
        { commands += "queue" }, { commands += "expand" }
    )

    private fun expanded(inputEnabled: Boolean = true) {
        compose.setContent {
            navigation = rememberPocketAdvanceNavigationState()
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
                            navigationState = navigation,
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
            R.string.player_repeat_off, R.string.player_play,
            R.string.player_open_queues, R.string.player_more_actions
        ).forEach { label ->
            compose.onNodeWithContentDescription(compose.activity.getString(label))
                .performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
        }
        compose.runOnIdle {
            assertEquals(listOf("previous", "next", "shuffle", "repeat", "play", "queue", "more"), commands)
            assertTrue(seeks.isEmpty())
        }
    }

    private fun control(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
    }

    @Test
    fun startBackAndDpadRestoreSelectionWithoutPlaybackOrCollapse() {
        expanded()
        val open = compose.activity.getString(R.string.pocket_advance_open_browser)
        val back = compose.activity.getString(R.string.pocket_advance_return_now_playing)
        assertEquals(open, compose.onNodeWithTag("pocketAdvanceStart").fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription].single())
        control("pocketAdvanceStart")
        assertEquals(back, compose.onNodeWithTag("pocketAdvanceStart").fetchSemanticsNode()
            .config[SemanticsActions.OnClick].label)
        control("pocketAdvanceUp") // Wrap from Now Playing to Albums.
        compose.onNodeWithTag("pocketAdvanceRootAlbums").assertIsSelected().assertIsNotEnabled()
        control("pocketAdvanceDown")
        compose.onNodeWithTag("pocketAdvanceRootNowPlaying").assertIsSelected()
        control("pocketAdvanceDown")
        control("pocketAdvanceDown")
        compose.onNodeWithTag("pocketAdvanceRootArtists").assertIsSelected().assertIsNotEnabled()
        assertEquals(compose.activity.getString(R.string.pocket_advance_unavailable),
            compose.onNodeWithTag("pocketAdvanceRootArtists").fetchSemanticsNode()
                .config[SemanticsProperties.StateDescription])
        compose.onNodeWithTag("pocketAdvancePlay").assertIsNotEnabled()
        compose.onNodeWithTag("pocketAdvanceNext").assertIsNotEnabled()
        // Even direct accessibility invocation cannot activate an unavailable destination.
        control("pocketAdvancePlay")
        control("pocketAdvanceNext")
        assertEquals(back, compose.onNodeWithTag("pocketAdvanceBack").fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription].single())
        control("pocketAdvanceStart")
        control("pocketAdvanceBack")
        compose.onNodeWithTag("pocketAdvanceRootArtists").assertIsSelected()
        control("pocketAdvanceBack")
        control("pocketAdvanceStart")
        control("pocketAdvancePrevious")
        compose.runOnIdle {
            assertEquals(PocketAdvanceLcdMode.NowPlaying, navigation.lcdMode)
            assertEquals(PocketAdvanceRootEntry.Artists, navigation.selectedRootEntry)
            assertTrue(commands.isEmpty()); assertTrue(seeks.isEmpty())
        }
    }

    @Test
    fun bothConfirmControlsReturnToNowPlayingWithoutPlayback() {
        expanded()
        control("pocketAdvanceStart")
        control("pocketAdvancePlay")
        control("pocketAdvanceBack")
        control("pocketAdvanceNext")
        compose.runOnIdle {
            assertEquals(PocketAdvanceLcdMode.NowPlaying, navigation.lcdMode)
            assertTrue(commands.isEmpty()); assertTrue(seeks.isEmpty())
        }
    }

    @Test
    fun collapseRemainsAnExplicitHeaderAccessibilityAction() {
        expanded()
        control("pocketAdvanceBack")
        val actions = compose.onNodeWithTag("pocketAdvanceHeader").fetchSemanticsNode()
            .config[SemanticsActions.CustomActions]
        compose.runOnIdle {
            assertEquals(listOf(compose.activity.getString(R.string.player_collapse)), actions.map { it.label })
            assertTrue(actions.single().action())
        }
        compose.runOnIdle { assertEquals(listOf("collapse"), commands) }
    }

    @Test
    fun compactSavedLocationSurvivesRecompositionAndRecreationWithSafeMode() {
        val restoration = StateRestorationTester(compose)
        val tick = mutableStateOf(0)
        restoration.setContent {
            navigation = rememberPocketAdvanceNavigationState()
            MaterialTheme { androidx.compose.material3.Text(tick.value.toString()) }
        }
        val first = compose.runOnIdle {
            navigation.openBrowser()
            navigation.moveSelectionUp()
            tick.value++
            navigation
        }
        compose.runOnIdle {
            assertTrue(first === navigation)
            assertEquals(PocketAdvanceRootEntry.Albums, navigation.selectedRootEntry)
        }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle {
            assertTrue(first !== navigation)
            assertEquals(PocketAdvanceLcdMode.NowPlaying, navigation.lcdMode)
            navigation.openBrowser()
            assertEquals(PocketAdvanceRootEntry.Albums, navigation.selectedRootEntry)
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
        listOf("pocketAdvancePrevious", "pocketAdvanceNext", "pocketAdvancePlay", "pocketAdvanceBack",
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
        val close = compose.onNodeWithTag("pocketAdvanceBack").fetchSemanticsNode().boundsInRoot
        assertTrue(play.width >= 48f * .25f && close.width >= 48f * .25f)
        assertTrue("Long/scaled captions must not move A/B targets into each other", play.left >= close.right)
        compose.runOnIdle { assertTrue(commands.isEmpty()); assertTrue(seeks.isEmpty()) }
    }

    @Test
    @OptIn(ExperimentalLayoutApi::class)
    fun startFitsMinimumBudgetsWithoutMovingDpadDisplayOrFaceButtons() {
        val scale = mutableStateOf(1f)
        val viewportScale = mutableStateOf(1f)
        val testDensity = .25f
        compose.setContent {
            navigation = rememberPocketAdvanceNavigationState()
            CompositionLocalProvider(LocalDensity provides Density(testDensity, scale.value)) {
                MaterialTheme {
                    val minimum = minimumPocketAdvanceSize(scale.value)
                    Box(Modifier.requiredSize(minimum.width * viewportScale.value, minimum.height * viewportScale.value)
                        .consumeWindowInsets(WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout))) {
                        PocketAdvanceExpandedPlayer(
                            currentSong = song, isPlaying = false, isShuffleEnabled = false,
                            repeatMode = RepeatMode.OFF, currentPosition = 30_000, duration = 180_000,
                            onPlayPauseClick = { commands += "play" }, onPreviousClick = {}, onNextClick = {},
                            onSeekChange = { seeks += it }, onShuffleClick = {}, onRepeatClick = {},
                            onCollapseClick = { commands += "collapse" }, onOpenUpNextClick = {}, onMoreClick = {},
                            navigationState = navigation, collapseGestureEnabled = false
                        )
                    }
                }
            }
        }
        for (fontScale in listOf(1f, 1.5f, 2f, 3f)) {
            for (hostScale in listOf(1f, 1.5f)) {
                compose.runOnIdle {
                    scale.value = fontScale
                    viewportScale.value = hostScale
                    navigation.showNowPlaying()
                }
                fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
                val measuredTags = listOf("pocketAdvanceDpad", "pocketAdvanceDisplay", "pocketAdvancePlay", "pocketAdvanceBack")
                val before = measuredTags.associateWith(::bounds)
                val dpad = before.getValue("pocketAdvanceDpad")
                val display = before.getValue("pocketAdvanceDisplay")
                val start = bounds("pocketAdvanceStart")
                val face = bounds("pocketAdvanceStartFace")
                val label = bounds("pocketAdvanceStartLabel")
                val caption = bounds("pocketAdvanceTrackMode")
                val shell = bounds("pocketAdvanceExpanded")
                assertTrue(start.width >= 48f * testDensity && start.height >= 48f * testDensity)
                assertEquals(64f * testDensity, start.width, 1f)
                assertEquals(52f * testDensity, face.width, 1f)
                assertEquals(26f * testDensity, face.height, 1f)
                assertTrue("START must stay below the D-pad at font scale $fontScale", start.top >= dpad.bottom)
                assertTrue(start.left >= shell.left && start.right <= shell.right && start.bottom <= shell.bottom)
                before.forEach { (tag, rect) -> assertTrue("START overlaps $tag at font scale $fontScale", !start.overlaps(rect)) }
                assertEquals("The original D-pad/caption group must remain vertically centered",
                    display.center.y, (dpad.top + caption.bottom) / 2f, 1f)
                assertEquals(dpad.center.x, start.center.x, 1f)
                assertEquals(caption.center.x, face.center.x, 1f)
                assertEquals("START must follow the caption instead of the wing bottom",
                    12f * testDensity, face.top - caption.bottom, 1f)
                assertTrue("The visible pill must clear the shell bottom inset", face.bottom <= shell.bottom - 24f * testDensity)
                assertEquals(face.center.x, label.center.x, 1f)
                assertEquals(face.center.y, label.center.y, 1f)
                assertTrue("START engraving must fit inside its face", label.width < face.width && label.height < face.height)
                control("pocketAdvanceStart")
                measuredTags.forEach { assertEquals("Mode switching moved $it", before.getValue(it), bounds(it)) }
                assertEquals(start, bounds("pocketAdvanceStart"))
            }
        }
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
