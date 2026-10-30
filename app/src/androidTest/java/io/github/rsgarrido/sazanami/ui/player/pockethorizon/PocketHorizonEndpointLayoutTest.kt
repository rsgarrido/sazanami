package io.github.rsgarrido.sazanami.ui.player.pockethorizon

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.ui.player.PlayerEndpointBounds
import io.github.rsgarrido.sazanami.ui.player.PlayerMorphHost
import io.github.rsgarrido.sazanami.ui.player.mini.MiniPlayerCallbacks
import io.github.rsgarrido.sazanami.ui.player.mini.MiniPlayerState
import io.github.rsgarrido.sazanami.ui.player.rememberPlayerMorphState
import io.github.rsgarrido.sazanami.ui.state.PlaybackProgressUiState
import io.github.rsgarrido.sazanami.ui.state.PlaybackUiState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real independent endpoints inside the existing measurement host, without live dispatch/nomination. */
class PocketHorizonEndpointLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var viewport by mutableStateOf(DpSize(1200.dp, 900.dp))
    private var scale by mutableStateOf(1f)
    private var direction by mutableStateOf(LayoutDirection.Ltr)
    private var miniPosition by mutableStateOf(0)
    private var song by mutableStateOf(Song(1, "Track", "Artist", "Album", 1, 120_000L,
        Uri.parse("content://media/1"), "/music/1.flac", "/music", null))
    private val progress = MutableStateFlow(PlaybackProgressUiState(currentPosition = 0, duration = 120_000))
    private val navigation = PocketHorizonNavigationState()
    private lateinit var owner: PlayerEndpointBounds
    private lateinit var mini: PocketHorizonEndpointMeasurements
    private lateinit var expanded: PocketHorizonEndpointMeasurements

    private fun mount() = compose.setContent {
        MaterialTheme {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                var density = .5f
                while (viewport.width.value * density > constraints.maxWidth ||
                    viewport.height.value * density > constraints.maxHeight) density /= 2f
                CompositionLocalProvider(LocalDensity provides Density(density, scale), LocalLayoutDirection provides direction) {
                    val morph = rememberPlayerMorphState()
                    PlayerMorphHost(morph, Modifier.requiredSize(viewport.width, viewport.height).padding(12.dp),
                        layoutBoundary = listOf(viewport, scale, direction)) { endpoints ->
                        val miniScope = rememberPocketHorizonEndpointMeasurements(endpoints, song)
                        val expandedScope = rememberPocketHorizonEndpointMeasurements(endpoints, song)
                        SideEffect { owner = endpoints; mini = miniScope; expanded = expandedScope }
                        PocketHorizonExpandedPlayer(PlaybackUiState(isConnected = true, currentSong = song), progress,
                            {}, {}, {}, {}, inputEnabled = false, navigationState = navigation, measurements = expandedScope)
                        PocketHorizonMiniPlayer(MiniPlayerState(song, false, miniPosition, 120_000, 44.dp),
                            MiniPlayerCallbacks({}, {}, {}, {}, {}),
                            modifier = Modifier.align(Alignment.BottomCenter).width(600.dp)
                                .onGloballyPositioned { endpoints.updateMini(it.boundsInRoot()) },
                            inputEnabled = false, measurements = miniScope)
                    }
                }
            }
        }
    }

    private fun pair() = compose.runOnIdle { requireNotNull(resolvePocketHorizonEndpointPair(owner, mini, expanded)) }

    @Test fun actualAnchorsMatchRenderedBoxesAndConvertAnInsetHostOrigin() {
        mount()
        val pair = pair()
        assertTrue(pair.frame.originInRoot.x > 0f)
        fun root(tag: String): Rect = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val origin = pair.frame.originInRoot
        assertRect(root("pocketHorizonMiniArtwork").translate(-origin.x, -origin.y), pair.mini.artwork)
        assertRect(root("pocketHorizonArtwork").translate(-origin.x, -origin.y), pair.expanded.artwork)
        assertRect(root("pocketHorizonPlayPause").translate(-origin.x, -origin.y), pair.expanded.playPause)
        assertTrue(pair.mini.isValid())
        assertTrue(pair.expanded.isValid())
    }

    @Test fun tabletSizesLargeFontsAndRtlProduceFreshContainedPairs() {
        mount()
        for ((size, fontScale) in listOf(DpSize(1000.dp, 640.dp) to 1f,
            DpSize(1600.dp, 1000.dp) to 1f, DpSize(1200.dp, 900.dp) to 2f)) {
            val old = compose.runOnIdle { mini }
            compose.runOnIdle { viewport = size; scale = fontScale; direction = LayoutDirection.Rtl }
            val pair = pair()
            assertTrue(pair.mini.isValid())
            assertTrue(pair.expanded.isValid())
            compose.runOnIdle { assertNull(old.geometry()); assertNotSame(old, mini) }
        }
    }

    @Test fun changedMetadataArtworkAndCategoryRetireTheirPreviousEndpointReports() {
        mount()
        pair()
        val old = compose.runOnIdle { mini to expanded }
        compose.runOnIdle { song = song.copy(title = "Long unusual 日本語 title ".repeat(20), artist = "Artist ".repeat(20),
            albumArtUri = Uri.parse("content://artwork/new")) }
        pair()
        compose.runOnIdle {
            assertNull(old.first.geometry())
            assertNull(old.second.geometry())
            navigation.selectCategory(PocketHorizonCategory.Songs)
        }
        compose.onNodeWithTag("pocketHorizonLibraryMessage").assertIsDisplayed()
        compose.runOnIdle { assertNull(resolvePocketHorizonEndpointPair(owner, mini, expanded)) }
        compose.runOnIdle { navigation.showNowPlaying() }
        pair()
    }

    @Test fun progressTicksKeepTheStructuralMeasurementLifetime() {
        mount()
        pair()
        val scopes = compose.runOnIdle { mini to expanded }
        compose.runOnIdle {
            miniPosition = 40_000
            progress.value = progress.value.copy(currentPosition = 40_000)
        }
        pair()
        compose.runOnIdle {
            assertSame(scopes.first, mini)
            assertSame(scopes.second, expanded)
        }
    }

    private fun assertRect(expected: Rect, actual: Rect) {
        assertEquals(expected.left, actual.left, 1f)
        assertEquals(expected.top, actual.top, 1f)
        assertEquals(expected.right, actual.right, 1f)
        assertEquals(expected.bottom, actual.bottom, 1f)
    }
}
