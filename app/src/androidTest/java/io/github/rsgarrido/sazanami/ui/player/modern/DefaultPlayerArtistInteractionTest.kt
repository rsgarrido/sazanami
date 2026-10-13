package io.github.rsgarrido.sazanami.ui.player.modern

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.ui.rememberNowPlayingArtistNavigation
import io.github.rsgarrido.sazanami.ui.player.PlayerLyricsTransitionState
import io.github.rsgarrido.sazanami.ui.player.PlayerMorphState
import io.github.rsgarrido.sazanami.ui.player.PlayerPresentation
import io.github.rsgarrido.sazanami.ui.player.PlayerSurfaceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DefaultPlayerArtistInteractionTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var player: PlayerMorphState
    private lateinit var lyrics: PlayerLyricsTransitionState
    private lateinit var carousel: ModernArtworkCarouselPresentation
    private val artistControl = hasText("Current Artist") and hasClickAction()

    @Test fun visibleMorphArtistIsTheOnlyArtistControlAndUnsafeStatesRemoveItsClick() {
        val available = mutableStateOf(true)
        var clicks = 0
        setMorphContent(available) { clicks++ }
        composeRule.onAllNodes(artistControl).assertCountEquals(1)
        val semantics = composeRule.onNode(artistControl).fetchSemanticsNode().config
        assertEquals(Role.Button, semantics[SemanticsProperties.Role])
        assertEquals(composeRule.activity.getString(R.string.library_search_go_artist),
            semantics[SemanticsActions.OnClick].label)
        composeRule.onAllNodes(hasText("Previous Artist") and hasClickAction()).assertCountEquals(0)
        composeRule.onAllNodes(hasText("Next Artist") and hasClickAction()).assertCountEquals(0)
        composeRule.onNode(artistControl).performClick()
        composeRule.runOnIdle { assertEquals(1, clicks); available.value = false }
        composeRule.onAllNodes(artistControl).assertCountEquals(0)
        composeRule.onNodeWithText("Current Artist").assertExists()
        composeRule.runOnIdle {
            available.value = true
            carousel.state.updateArtworkWidth(300)
            carousel.state.startDrag()
            carousel.state.dragBy(-100f)
        }
        composeRule.onAllNodes(artistControl).assertCountEquals(0)
        composeRule.runOnIdle { carousel.state.dragBy(100f); lyrics.beginOpeningDrag() }
        composeRule.onAllNodes(artistControl).assertCountEquals(0)
        composeRule.runOnIdle { lyrics.snapToExpanded(); player.beginDrag(1_000f) }
        composeRule.onAllNodes(artistControl).assertCountEquals(0)
    }

    @Test fun verticalSwipesStartingOnTheArtistStillCollapseAndOpenLyricsWithoutNavigating() {
        var clicks = 0
        setMorphContent(mutableStateOf(true)) { clicks++ }
        composeRule.onNode(artistControl).performTouchInput {
            swipe(center, center + Offset(0f, 300f), durationMillis = 400)
        }
        composeRule.runOnIdle {
            assertEquals(PlayerPresentation.Collapsed, player.settledPresentation)
            assertEquals(0, clicks)
            player.snapTo(PlayerPresentation.Expanded)
        }
        composeRule.onNode(artistControl).performTouchInput {
            swipe(center, center - Offset(0f, 250f), durationMillis = 400)
        }
        composeRule.runOnIdle {
            assertEquals(PlayerSurfaceState.LYRICS, lyrics.settledSurface)
            assertEquals(0, clicks)
        }
    }

    @Test fun fallbackHiddenAndUnresolvedArtistHaveNoClickSemantics() {
        val hidden = mutableStateOf(false)
        val navigable = mutableStateOf(true)
        var clicks = 0
        composeRule.setContent {
            MaterialTheme {
                ModernPlayerMetadata(
                    currentSong = song(1, "Current Artist"), style = ModernPlayerDefaults.style(),
                    hidePersistentContent = hidden.value,
                    onOpenArtistClick = if (navigable.value) ({ clicks++ }) else null
                )
            }
        }
        composeRule.onNode(artistControl).performClick()
        composeRule.runOnIdle { assertEquals(1, clicks); hidden.value = true }
        composeRule.onAllNodes(artistControl).assertCountEquals(0)
        composeRule.runOnIdle { hidden.value = false; navigable.value = false }
        composeRule.onAllNodes(artistControl).assertCountEquals(0)
        composeRule.onNodeWithText("Current Artist").assertExists()
    }

    @Test fun morphArtistTapsUseTextWidthForLeftAndCenteredMetadata() {
        val alignment = mutableStateOf(ModernMetadataAlignment.LEFT)
        var clicks = 0
        setMorphContent(mutableStateOf(true), alignment) { clicks++ }
        composeRule.onNode(artistControl).performTouchInput { click(center) }
        tapEmptyArtistRowSpace(onLeft = false)
        composeRule.runOnIdle { assertEquals(1, clicks); alignment.value = ModernMetadataAlignment.CENTER }
        tapEmptyArtistRowSpace(onLeft = true)
        tapEmptyArtistRowSpace(onLeft = false)
        composeRule.onNode(artistControl).performTouchInput { click(center) }
        composeRule.onAllNodes(artistControl).assertCountEquals(1)
        composeRule.runOnIdle { assertEquals(2, clicks) }
    }

    @Test fun fallbackArtistTapsUseTextWidthForLeftAndCenteredMetadata() {
        val alignment = mutableStateOf(ModernMetadataAlignment.LEFT)
        var clicks = 0
        composeRule.setContent {
            MaterialTheme {
                ModernPlayerMetadata(
                    currentSong = song(1, "Current Artist"), style = ModernPlayerDefaults.style(),
                    alignment = alignment.value, onOpenArtistClick = { clicks++ }
                )
            }
        }
        composeRule.onNode(artistControl).performTouchInput { click(center) }
        tapEmptyArtistRowSpace(onLeft = false)
        composeRule.runOnIdle { assertEquals(1, clicks); alignment.value = ModernMetadataAlignment.CENTER }
        tapEmptyArtistRowSpace(onLeft = true)
        tapEmptyArtistRowSpace(onLeft = false)
        composeRule.onNode(artistControl).performTouchInput { click(center) }
        composeRule.onAllNodes(artistControl).assertCountEquals(1)
        composeRule.runOnIdle { assertEquals(2, clicks) }
    }

    private fun tapEmptyArtistRowSpace(onLeft: Boolean) {
        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val artistBounds = composeRule.onNode(artistControl).fetchSemanticsNode().boundsInRoot
        // Stay well inside the row, away from the label's minimum touch accommodation.
        val emptyStart = if (onLeft) rootBounds.left + 16f else artistBounds.right
        val emptyEnd = if (onLeft) artistBounds.left else rootBounds.right - 16f
        assertTrue("The artist must leave horizontal space outside its click bounds", emptyEnd > emptyStart)
        val position = Offset(
            (emptyStart + emptyEnd) / 2f - rootBounds.left,
            artistBounds.center.y - rootBounds.top
        )
        composeRule.onRoot().performTouchInput { click(position) }
    }

    @Test fun sharedCallbackStaysStableAndReadsTheLatestTrackAndLibrary() {
        val target = song(1, "Current Artist")
        val current = mutableStateOf(target)
        val library = mutableStateOf(listOf(target))
        val opened = mutableListOf<String>()
        lateinit var callback: (Song) -> Unit
        composeRule.setContent {
            callback = rememberNowPlayingArtistNavigation(current.value, library.value) { opened += it }
        }
        lateinit var original: (Song) -> Unit
        composeRule.runOnIdle { original = callback; callback(target) }
        composeRule.runOnIdle { library.value = emptyList() }
        composeRule.runOnIdle {
            assertSame(original, callback)
            callback(target)
            current.value = song(2, "Next Artist")
            library.value = listOf(current.value)
        }
        composeRule.runOnIdle {
            callback(target)
            callback(current.value)
            assertEquals(listOf("Current Artist", "Next Artist"), opened)
        }
    }

    private fun setMorphContent(
        available: State<Boolean>,
        metadataAlignment: State<ModernMetadataAlignment>? = null,
        onClick: () -> Unit
    ) {
        composeRule.setContent {
            MaterialTheme {
                val scope = rememberCoroutineScope()
                player = remember { PlayerMorphState(PlayerPresentation.Expanded, scope) }
                lyrics = remember { PlayerLyricsTransitionState(false, scope) {} }
                val target = song(1, "Current Artist")
                carousel = rememberModernArtworkCarouselPresentation(
                    target, song(2, "Previous Artist"), song(3, "Next Artist"), {}, {}
                )
                val metadataRight = with(LocalDensity.current) {
                    LocalConfiguration.current.screenWidthDp.dp.toPx()
                } - 16f
                DefaultPlayerMorph(
                    progress = player.progress,
                    geometry = DefaultPlayerMorphGeometry(
                        Rect(0f, 0f, 400f, 800f), Rect(16f, 16f, 160f, 160f),
                        Rect(16f, 300f, metadataRight, 500f), Rect(200f, 550f, 270f, 620f)
                    ),
                    carouselPresentation = carousel,
                    artworkTransitionStyle = ModernArtworkTransitionStyle.SLIDE,
                    isPlaying = false, onPlayPauseClick = {}, style = ModernPlayerDefaults.style(),
                    appearance = ModernPlayerAppearance.Default.copy(
                        layout = ModernPlayerAppearance.Default.layout.copy(
                            metadataAlignment = metadataAlignment?.value ?: ModernMetadataAlignment.LEFT
                        )
                    ),
                    artworkPalette = ModernArtworkPalette.fallback(ModernPlayerDefaults.style().accentColor),
                    expandedArtworkRequestSizePx = 160,
                    onOpenArtistClick = modernArtistClickCallback(
                        if (available.value) onClick else null,
                        player, lyrics, carousel.state.offsetX, true, true
                    ),
                    metadataGestureModifier = rememberModernPlayerVerticalDragModifier(
                        player, lyrics, 800f, 400f
                    )
                ) { visualState ->
                    ModernPlayerMetadata(
                        currentSong = target, style = ModernPlayerDefaults.style(),
                        hidePersistentContent = visualState.isReady, onOpenArtistClick = onClick
                    )
                }
            }
        }
    }

    private fun song(id: Long, artist: String) = Song(
        id, "Song $id", artist, "Album", 1, 120_000L,
        Uri.parse("content://media/external/audio/media/$id"), "/music/$id.flac", "/music", null,
        volumeName = "external"
    )
}
