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
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.data.TabletLandscapeTheme
import io.github.rsgarrido.sazanami.ui.player.PlayerEndpointBounds
import io.github.rsgarrido.sazanami.ui.player.PlayerDeviceKind
import io.github.rsgarrido.sazanami.ui.player.PlayerMorphHost
import io.github.rsgarrido.sazanami.ui.player.PlayerPresentationIdentity
import io.github.rsgarrido.sazanami.ui.player.TabletLandscapePlayerContext
import io.github.rsgarrido.sazanami.ui.player.resolvePlayerPresentation
import io.github.rsgarrido.sazanami.ui.player.tabletLandscapePlayerCandidate
import io.github.rsgarrido.sazanami.ui.player.theme.defaultTokens
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
    private val hostInset = 12.dp
    private var renderedDensity = 0f

    private fun mount() = compose.setContent {
        MaterialTheme {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                var density = .5f
                while (viewport.width.value * density > constraints.maxWidth ||
                    viewport.height.value * density > constraints.maxHeight) density /= 2f
                CompositionLocalProvider(LocalDensity provides Density(density, scale), LocalLayoutDirection provides direction) {
                    val morph = rememberPlayerMorphState()
                    PlayerMorphHost(morph, Modifier.requiredSize(viewport.width, viewport.height).padding(hostInset),
                        layoutBoundary = listOf(viewport, scale, direction)) { endpoints ->
                        val miniScope = rememberPocketHorizonEndpointMeasurements(endpoints, song)
                        val expandedScope = rememberPocketHorizonEndpointMeasurements(endpoints, song)
                        SideEffect { owner = endpoints; mini = miniScope; expanded = expandedScope; renderedDensity = density }
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

    private data class MeasuredPair(
        val owner: PlayerEndpointBounds,
        val mini: PocketHorizonEndpointMeasurements,
        val expanded: PocketHorizonEndpointMeasurements,
        val pair: PocketHorizonEndpointPair
    )

    private fun currentScopesMatchRequestedLayout(): Boolean {
        if (!::owner.isInitialized || !::mini.isInitialized || !::expanded.isInitialized) return false
        val frame = owner.layoutFrame ?: return false
        val key = pocketHorizonMeasurementKey(song, direction)
        return frame.layoutBoundary == listOf(viewport, scale, direction) && frame.fontScale == scale &&
            frame.density == renderedDensity &&
            mini !== expanded && mini.owner === owner && expanded.owner === owner &&
            mini.frame == frame && expanded.frame == frame && mini.contentKey == key && expanded.contentKey == key
    }

    private fun measurementDescription(): String {
        fun Any.identity() = "${javaClass.simpleName}@${System.identityHashCode(this).toString(16)}"
        fun PocketHorizonEndpointMeasurements.describe() =
            "${identity()}, owner=${owner.identity()}, frame=$frame, key=$contentKey, geometry=${geometry()}"
        return "viewport=$viewport, usable=${DpSize(viewport.width - hostInset * 2, viewport.height - hostInset * 2)}, " +
            "fontScale=$scale, direction=$direction, density=$renderedDensity, expectedKey=${pocketHorizonMeasurementKey(song, direction)}; " +
            if (!::owner.isInitialized || !::mini.isInitialized || !::expanded.isInitialized) "scopes not published"
            else "owner=${owner.identity()}, frame=${owner.layoutFrame}, miniHost=${owner.mini}, expandedHost=${owner.expanded}; " +
                "mini=[${mini.describe()}]; expanded=[${expanded.describe()}]"
    }

    private fun renderedBoundsDescription(): String = listOf(
        "pocketHorizonMini", "pocketHorizonMiniDisplay", "pocketHorizonMiniArtwork", "pocketHorizonMiniMetadata",
        "pocketHorizonMiniPlayPause", "pocketHorizonMiniProgress", "pocketHorizonMiniExpand",
        "pocketHorizonShell", "pocketHorizonDisplay", "pocketHorizonArtwork", "pocketHorizonTitle",
        "pocketHorizonArtist", "pocketHorizonPlayPause", "pocketHorizonSeek", "pocketHorizonCollapseRegion"
    ).joinToString { tag ->
        "$tag=${compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }}"
    }

    private fun measuredPair(): MeasuredPair {
        var result: MeasuredPair? = null
        var description = "No placement observed"
        try {
            // SideEffect publishes scopes before placement. Idleness alone is not the contract:
            // await a complete pair for the requested layout/content, never an outgoing generation.
            compose.waitUntil(timeoutMillis = 5_000) {
                result = compose.runOnIdle {
                    description = measurementDescription()
                    if (!currentScopesMatchRequestedLayout()) return@runOnIdle null
                    resolvePocketHorizonEndpointPair(owner, mini, expanded)?.let {
                        MeasuredPair(owner, mini, expanded, it)
                    }
                }
                result != null
            }
        } catch (timeout: ComposeTimeoutException) {
            val rendered = runCatching { renderedBoundsDescription() }.getOrElse { "unavailable: $it" }
            throw AssertionError("No current measured Horizon pair: $description; rendered=$rendered", timeout)
        }
        return requireNotNull(result)
    }

    private fun pair() = measuredPair().pair

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
        var old = measuredPair()
        for ((size, fontScale, layoutDirection) in listOf(
            Triple(DpSize(1000.dp, 640.dp), 1f, LayoutDirection.Rtl),
            Triple(DpSize(1600.dp, 1000.dp), 1f, LayoutDirection.Rtl),
            Triple(DpSize(1400.dp, 900.dp), 2f, LayoutDirection.Rtl),
            Triple(DpSize(1400.dp, 900.dp), 2f, LayoutDirection.Ltr), // Direction alone changes.
            Triple(DpSize(1400.dp, 900.dp), 1f, LayoutDirection.Ltr) // Font scale alone changes.
        )) {
            compose.runOnIdle { viewport = size; scale = fontScale; direction = layoutDirection }
            val current = measuredPair()
            val pair = current.pair
            assertTrue(pair.mini.isValid())
            assertTrue(pair.expanded.isValid())
            val host = pair.frame.boundsInRoot.translate(-pair.frame.originInRoot.x, -pair.frame.originInRoot.y)
            assertContained(host, pair.mini.surface)
            assertContained(host, pair.expanded.surface)
            val minimum = minimumPocketHorizonSize(fontScale)
            assertTrue(host.width / pair.frame.density >= minimum.width.value)
            assertTrue(host.height / pair.frame.density >= minimum.height.value)
            val origin = pair.frame.originInRoot
            assertRect(compose.onNodeWithTag("pocketHorizonMiniArtwork", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot.translate(-origin.x, -origin.y), pair.mini.artwork)
            assertRect(compose.onNodeWithTag("pocketHorizonArtwork", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot.translate(-origin.x, -origin.y), pair.expanded.artwork)
            if (layoutDirection == LayoutDirection.Rtl) {
                assertTrue(pair.expanded.artwork.left >= pair.expanded.metadata.right)
                assertTrue(pair.mini.artwork.left >= pair.mini.metadata.right)
            } else {
                assertTrue(pair.expanded.artwork.right <= pair.expanded.metadata.left)
                assertTrue(pair.mini.artwork.right <= pair.mini.metadata.left)
            }
            compose.runOnIdle {
                assertSame(old.owner, current.owner)
                assertNotSame(old.mini, current.mini)
                assertNotSame(old.expanded, current.expanded)
                assertTrue(pair.frame.generation > old.pair.frame.generation)
                assertNull(old.mini.geometry())
                assertNull(old.expanded.geometry())
                assertNull(resolvePocketHorizonEndpointPair(owner, old.mini, old.expanded))
                assertNull(resolvePocketHorizonEndpointPair(owner, old.mini, current.expanded))
                assertNull(resolvePocketHorizonEndpointPair(owner, current.mini, old.expanded))
            }
            old = current
        }
    }

    @Test fun insufficientMeasuredSpaceFallsBackAndReturningSpaceRequiresFreshScopes() {
        mount()
        val old = measuredPair()
        compose.runOnIdle { viewport = DpSize(900.dp, 650.dp); scale = 2f }
        compose.waitUntil(timeoutMillis = 5_000) { compose.runOnIdle { currentScopesMatchRequestedLayout() } }
        compose.runOnIdle {
            val frame = requireNotNull(owner.layoutFrame)
            val size = DpSize((frame.boundsInRoot.width / frame.density).dp,
                (frame.boundsInRoot.height / frame.density).dp)
            assertFalse(requireNotNull(resolvePocketHorizonLayout(size, scale)).fitsMinimum)
            val primary = PlayerTheme.CLASSIC_WHEEL
            val tokens = primary.defaultTokens()
            val selection = TabletLandscapeTheme.fromId("pocket_horizon")
            val resolved = resolvePlayerPresentation(primary, tokens,
                TabletLandscapePlayerContext(PlayerDeviceKind.TABLET, 800, size),
                tabletLandscapePlayerCandidate(selection, tokens, scale))
            assertEquals(PlayerPresentationIdentity.Existing(primary), resolved.identity)
            assertSame(tokens, resolved.tokens)
            assertEquals(TabletLandscapeTheme.POCKET_HORIZON, selection)
            assertNull(old.mini.geometry())
            assertNull(old.expanded.geometry())
            assertNull(resolvePocketHorizonEndpointPair(owner, old.mini, old.expanded))
            viewport = DpSize(1200.dp, 900.dp); scale = 1f
        }
        val restored = measuredPair()
        compose.runOnIdle {
            assertSame(old.owner, restored.owner)
            assertNotSame(old.mini, restored.mini)
            assertNotSame(old.expanded, restored.expanded)
            assertTrue(restored.pair.frame.generation > old.pair.frame.generation)
            assertNull(old.mini.geometry())
            assertNull(old.expanded.geometry())
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

    @Test fun shoulderPressDoesNotTransformOrInvalidateTheStableEndpointPair() {
        mount()
        val before = measuredPair()
        for (tag in listOf("horizonL", "horizonR")) {
            repeat(3) {
                compose.onNodeWithTag(tag).performTouchInput { down(center) }
                val pressed = measuredPair()
                assertSame(before.owner, pressed.owner)
                assertSame(before.mini, pressed.mini)
                assertSame(before.expanded, pressed.expanded)
                assertEquals(before.pair, pressed.pair)
                compose.onNodeWithTag(tag).performTouchInput { up() }
                assertEquals(before.pair, measuredPair().pair)
            }
        }
    }

    private fun assertRect(expected: Rect, actual: Rect) {
        assertEquals(expected.left, actual.left, 1f)
        assertEquals(expected.top, actual.top, 1f)
        assertEquals(expected.right, actual.right, 1f)
        assertEquals(expected.bottom, actual.bottom, 1f)
    }

    private fun assertContained(container: Rect, child: Rect) {
        assertTrue("Expected $child inside $container", child.width > 0f && child.height > 0f &&
            child.left >= container.left - 1f && child.top >= container.top - 1f &&
            child.right <= container.right + 1f && child.bottom <= container.bottom + 1f)
    }
}
