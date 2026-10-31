package io.github.rsgarrido.sazanami.ui.player.pockethorizon

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.pressBack
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.artistIdentity
import io.github.rsgarrido.sazanami.data.stableUiKey
import io.github.rsgarrido.sazanami.ui.state.LibraryUiState
import io.github.rsgarrido.sazanami.ui.state.PlaybackProgressUiState
import io.github.rsgarrido.sazanami.ui.state.PlaybackUiState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Independent Horizon presentation with real production grouping/controls and test-only local tracks. */
class PocketHorizonLibraryNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val first = song(1L, "First", "/alpha", "Alpha", "Example", 1)
    private val second = song(2L, "Second", "/alpha", "Alpha", "Example", 2)
    private val other = song(3L, "Zulu", "/beta", "Beta", "Other", 1)
    private val supplied = listOf(other, second, first)
    private var library by mutableStateOf(ready(supplied))
    private var playback by mutableStateOf(PlaybackUiState(isConnected = true, currentSong = first))
    private var inputEnabled by mutableStateOf(true)
    private var selectionAvailable by mutableStateOf(true)
    private var viewport by mutableStateOf(DpSize(1200.dp, 900.dp))
    private var fontScale by mutableStateOf(1f)
    private var direction by mutableStateOf(LayoutDirection.Ltr)
    private var renderedDensity = .5f
    private lateinit var navigation: PocketHorizonNavigationState
    private val requests = mutableListOf<Pair<Song, List<Song>>>()
    private val progress = MutableStateFlow(PlaybackProgressUiState(currentPosition = 30_000, duration = 180_000))
    private var playPauseRequests = 0
    private var queueRequests = 0

    @Composable
    private fun Content() {
        navigation = rememberPocketHorizonNavigationState()
        MaterialTheme {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                var density = .5f
                while (density > .001f && (viewport.width.value * density > constraints.maxWidth ||
                    viewport.height.value * density > constraints.maxHeight)) density /= 2f
                SideEffect { renderedDensity = density }
                CompositionLocalProvider(
                    LocalDensity provides Density(density, fontScale),
                    LocalLayoutDirection provides direction
                ) {
                    Box(Modifier.requiredSize(viewport.width, viewport.height)) {
                        PocketHorizonExpandedPlayer(
                            playbackState = playback,
                            progressState = progress,
                            onPlayPauseClick = { playPauseRequests++ },
                            onPreviousClick = {}, onNextClick = {}, onSeekChange = {},
                            inputEnabled = inputEnabled,
                            seekEnabled = true,
                            libraryState = library,
                            onSongClick = if (selectionAvailable) ({ selected, context -> requests += selected to context }) else null,
                            navigationState = navigation,
                            onOpenQueueHubClick = { queueRequests++ }
                        )
                    }
                }
            }
        }
    }

    private fun mount() = compose.setContent { Content() }

    @Test fun consoleControlsBrowseConfirmBackToggleAndForwardPlaybackExactlyOnce() {
        mount()
        compose.onNodeWithTag("horizonX").performClick()
        compose.onNodeWithTag("horizonY").performClick()
        compose.onNodeWithTag("horizonStart").performClick()
        category(PocketHorizonCategory.Songs).assertIsSelected()
        compose.onNodeWithTag("horizonDown").performClick()
        songNode(second).assertIsSelected()
        compose.onNodeWithTag("horizonA").performClick()
        compose.runOnIdle {
            assertEquals(second, requests.single().first)
            assertEquals(listOf(first, second, other), requests.single().second)
            assertEquals(1, playPauseRequests)
            assertEquals(1, queueRequests)
        }
        compose.onNodeWithTag("horizonStart").performClick()
        compose.onNodeWithTag("horizonSelect").performClick()
        compose.onNodeWithTag("horizonControlGuide").assertIsDisplayed()
        compose.onNodeWithTag("horizonX").assertIsNotEnabled()
        compose.onNodeWithTag("horizonB").performClick()
        compose.onNodeWithTag("horizonControlGuide").assertDoesNotExist()
        compose.onNodeWithTag("horizonRight").performClick()
        category(PocketHorizonCategory.Albums).assertIsSelected()
        compose.onNodeWithTag("horizonA").performClick()
        compose.onNodeWithTag("pocketHorizonLibraryBack").assertIsDisplayed()
        compose.onNodeWithTag("horizonB").performClick()
        compose.onNodeWithTag("pocketHorizonLibraryBack").assertDoesNotExist()
        val lateSelect = compose.onNodeWithTag("horizonSelect").fetchSemanticsNode()
            .config[SemanticsActions.OnClick].action!!
        compose.runOnIdle { inputEnabled = false }
        listOf("horizonX", "horizonY", "horizonA", "horizonStart", "horizonSelect").forEach {
            compose.onNodeWithTag(it).assertIsNotEnabled()
        }
        compose.runOnIdle {
            lateSelect()
            assertEquals(PocketHorizonCategory.Albums, navigation.category)
            assertEquals(1, playPauseRequests); assertEquals(1, queueRequests)
        }
    }

    @Test fun analogStrokeMovesFocusWithoutPlaybackAndConsoleWingsStayOutsideTheLcd() {
        mount()
        select(PocketHorizonCategory.Songs)
        compose.onNodeWithTag("horizonAnalog").performTouchInput {
            down(center); moveTo(center + androidx.compose.ui.geometry.Offset(1f, 1f)); cancel()
        }
        songNode(first).assertIsSelected()
        compose.onNodeWithTag("horizonAnalog").performTouchInput {
            swipe(center, androidx.compose.ui.geometry.Offset(center.x, height.toFloat() - 1f), 300)
        }
        // This stroke releases before hold-repeat starts. Verify the requested canonical focus
        // actually commits after measured reveal, rather than checking only a row's fallback style.
        awaitFocus(PocketHorizonList.Songs, second.stableUiKey())
        compose.runOnIdle { assertTrue(requests.isEmpty()); assertEquals(0, playPauseRequests) }
        listOf(LayoutDirection.Ltr, LayoutDirection.Rtl).forEach { layoutDirection ->
            compose.runOnIdle { direction = layoutDirection }
            val display = bounds("pocketHorizonDisplay")
            assertTrue(bounds("horizonLeftWing").right <= display.left + 1f)
            assertTrue(bounds("horizonRightWing").left >= display.right - 1f)
            listOf("horizonUp", "horizonDown", "horizonLeft", "horizonRight", "horizonX", "horizonA", "horizonB", "horizonY").forEach { tag ->
                val box = bounds(tag)
                assertTrue(box.width >= 48f * renderedDensity - 1f)
                assertTrue(box.height >= 48f * renderedDensity - 1f)
            }
        }
    }
    @Test fun focusMovesWithinVisibleRowsWithoutTopAlignmentAndStickMovesOnNowPlaying() {
        mount()
        // The knob's tag precedes graphicsLayer. Measure the inner content coordinates, which
        // include that layer's physical translation, instead of the outer semantics bounds.
        val rest = unclippedBounds("horizonAnalogKnob")
        compose.onNodeWithTag("horizonAnalog").performTouchInput {
            // Vertical movement has no navigation binding on Now Playing, but must still move
            // the cap. A rightward stroke would also change the category to Songs.
            down(center); moveTo(androidx.compose.ui.geometry.Offset(center.x, 1f))
        }
        val displaced = unclippedBounds("horizonAnalogKnob")
        assertTrue("The analog cap moves upward without a browsing action", displaced.top < rest.top)
        assertEquals(rest.left, displaced.left, 1f)
        compose.runOnIdle { assertEquals(PocketHorizonCategory.NowPlaying, navigation.category) }
        compose.onNodeWithTag("horizonAnalog").performTouchInput { up() }
        assertEquals(rest, unclippedBounds("horizonAnalogKnob"))
        select(PocketHorizonCategory.Songs)
        val firstTop = bounds("pocketHorizonSong:" + first.stableUiKey()).top
        compose.onNodeWithTag("horizonDown").performClick()
        awaitFocus(PocketHorizonList.Songs, second.stableUiKey())
        assertEquals(firstTop, bounds("pocketHorizonSong:" + first.stableUiKey()).top, 1f)
        compose.onNodeWithTag("horizonUp").performClick()
        awaitFocus(PocketHorizonList.Songs, first.stableUiKey())
        compose.runOnIdle { assertTrue(requests.isEmpty()); assertEquals(0, playPauseRequests) }
    }

    @Test fun analogHoldKeepsFocusVisibleWithinItsRepeatLimitAndReleaseStopsNavigation() {
        val tracks = (1..30).map { song(it.toLong(), "Track " + it.toString().padStart(2, '0'), "/repeat", "Repeat", "Artist", it) }
        library = ready(tracks)
        mount()
        select(PocketHorizonCategory.Songs)
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithTag("horizonAnalog").performTouchInput {
                down(center); moveTo(androidx.compose.ui.geometry.Offset(center.x, height.toFloat() - 1f))
            }
            // A request launches a measured reveal; focus is committed only after that job runs.
            // runOnIdle alone cannot drive its pending Compose work with autoAdvance disabled.
            awaitFocus(PocketHorizonList.Songs, tracks[1].stableUiKey())
            repeat(25) {
                compose.mainClock.advanceTimeBy(240)
                compose.mainClock.advanceTimeByFrame()
                awaitFocus(PocketHorizonList.Songs)
            }
            val stopped = compose.runOnIdle { requireNotNull(navigation.focusedKey(PocketHorizonList.Songs)) }
            val accepted = tracks.indexOfFirst { it.stableUiKey() == stopped }
            // Layout-gated repeats may be dropped on a busy frame, but must repeat, stay bounded,
            // and never select a row before it has been fully revealed.
            assertTrue(accepted in 2..24)
            compose.mainClock.advanceTimeBy(2_000)
            compose.runOnIdle { assertEquals(stopped, navigation.focusedKey(PocketHorizonList.Songs)) }
            compose.onNodeWithTag("horizonAnalog").performTouchInput { up() }
            compose.mainClock.advanceTimeBy(2_000)
            compose.runOnIdle {
                assertEquals(stopped, navigation.focusedKey(PocketHorizonList.Songs))
                assertTrue(requests.isEmpty()); assertEquals(0, playPauseRequests)
            }
            // Release a fresh gesture before its repeat delay, independently of the exhausted
            // hold above. Its first request must commit, but no delayed repeat may follow it.
            compose.onNodeWithTag("horizonAnalog").performTouchInput {
                down(center); moveTo(androidx.compose.ui.geometry.Offset(center.x, height.toFloat() - 1f)); up()
            }
            val released = tracks[accepted + 1].stableUiKey()
            awaitFocus(PocketHorizonList.Songs, released)
            compose.mainClock.advanceTimeBy(2_000)
            compose.runOnIdle {
                assertEquals(released, navigation.focusedKey(PocketHorizonList.Songs))
                assertTrue(requests.isEmpty()); assertEquals(0, playPauseRequests)
            }
        } finally { compose.mainClock.autoAdvance = true }
    }

    private fun category(category: PocketHorizonCategory) =
        compose.onNodeWithTag("pocketHorizonCategory" + category.name)
    private fun select(category: PocketHorizonCategory) = category(category).performClick()
    private fun songNode(song: Song) = compose.onNodeWithTag("pocketHorizonSong:" + song.stableUiKey())
    private fun albumNode(key: String) = compose.onNodeWithTag("pocketHorizonAlbum:$key")
    private fun artistNode(name: String) = compose.onNodeWithTag("pocketHorizonArtist:" + artistIdentity(name).key)
    private fun list() = compose.onNodeWithTag("pocketHorizonLibraryList")
    private fun message() = compose.onNodeWithTag("pocketHorizonLibraryMessage")
    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag, useUnmergedTree = true)
        .fetchSemanticsNode().boundsInRoot

    @OptIn(ExperimentalTestApi::class)
    private fun unclippedBounds(tag: String): Rect {
        val coordinates = compose.onNodeWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNode().layoutInfo.coordinates
        return Rect(coordinates.positionInRoot(), Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()))
    }

    private fun assertFocusedSongFullyVisible(key: String) {
        val viewport = unclippedBounds("pocketHorizonLibraryList")
        val row = unclippedBounds("pocketHorizonSong:$key")
        compose.onNodeWithTag("pocketHorizonSong:$key").assertIsSelected()
        // Clipped semantics bounds would hide precisely the defect this test must catch.
        assertTrue("Focused row $row must start inside $viewport", row.top >= viewport.top - 1f)
        assertTrue("Entire focused row $row must end inside $viewport", row.bottom <= viewport.bottom + 1f)
    }

    private fun awaitFocus(list: PocketHorizonList, key: String? = null) {
        var committed: String? = null
        compose.waitUntil(timeoutMillis = 5_000) {
            // Reveal and selected-row composition are separate steps. Drive paused Compose work
            // while also yielding to Android's actual lazy-list placement, with no guessed delay.
            if (!compose.mainClock.autoAdvance) compose.mainClock.advanceTimeByFrame()
            val focused = compose.runOnIdle { navigation.focusedKey(list) }
            if (focused == null || (key != null && focused != key)) return@waitUntil false
            val row = compose.onAllNodesWithTag("pocketHorizonSong:$focused", useUnmergedTree = true)
                .fetchSemanticsNodes().singleOrNull()
            if (row?.config?.getOrNull(SemanticsProperties.Selected) != true) return@waitUntil false
            val viewport = unclippedBounds("pocketHorizonLibraryList")
            val bounds = unclippedBounds("pocketHorizonSong:$focused")
            val contained = bounds.top >= viewport.top - 1f && bounds.bottom <= viewport.bottom + 1f
            if (contained) committed = focused
            contained
        }
        assertFocusedSongFullyVisible(requireNotNull(committed))
    }

    @Test fun variableHeightRowsRemainFullyVisibleAtBothEdgesIncludingAlbumAndArtistDetails() {
        val tracks = (1..18).map { index ->
            song(index.toLong(), "Track " + index.toString().padStart(2, '0') +
                if (index % 2 == 0) " long title ".repeat(16) else "", "/visibility", "Album", "Artist", index)
        }
        library = ready(tracks)
        fontScale = 2f
        viewport = minimumPocketHorizonSize(fontScale)
        mount()
        select(PocketHorizonCategory.Songs)
        for (index in 1..14) {
            compose.onNodeWithTag("horizonDown").performClick()
            awaitFocus(PocketHorizonList.Songs, tracks[index].stableUiKey())
        }
        for (index in 13 downTo 0) {
            compose.onNodeWithTag("horizonUp").performClick()
            awaitFocus(PocketHorizonList.Songs, tracks[index].stableUiKey())
        }
        select(PocketHorizonCategory.Albums)
        albumNode("/visibility").performClick()
        for (index in 1..6) {
            compose.onNodeWithTag("horizonDown").performClick()
            awaitFocus(PocketHorizonList.AlbumTracks, tracks[index].stableUiKey())
        }
        compose.onNodeWithTag("horizonB").performClick()
        select(PocketHorizonCategory.Artists)
        artistNode("Artist").performClick()
        for (index in 1..6) {
            compose.onNodeWithTag("horizonDown").performClick()
            awaitFocus(PocketHorizonList.ArtistTracks, tracks[index].stableUiKey())
        }
        compose.runOnIdle { assertTrue(requests.isEmpty()); assertEquals(0, playPauseRequests) }
    }

    @Test
    fun fourCategoriesExposeSelectedAccessibleTabsAndHonorDisabledInput() {
        mount()
        category(PocketHorizonCategory.NowPlaying).assertIsSelected()
        PocketHorizonCategory.entries.forEach { target ->
            category(target).assertIsEnabled()
            assertEquals(Role.Tab, category(target).fetchSemanticsNode().config[SemanticsProperties.Role])
            select(target)
            category(target).assertIsSelected()
            if (target != PocketHorizonCategory.NowPlaying) list().assertIsDisplayed()
        }
        compose.runOnIdle { inputEnabled = false }
        PocketHorizonCategory.entries.forEach { category(it).assertIsNotEnabled() }
        category(PocketHorizonCategory.Songs).performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle {
            assertEquals(PocketHorizonCategory.Artists, navigation.category)
            assertTrue(requests.isEmpty())
        }
    }

    @Test
    fun songTapForwardsSortedContextOnceAndReturnsWithoutInventingPlaybackSuccess() {
        val original = playback
        mount()
        select(PocketHorizonCategory.Songs)
        songNode(second).performClick()
        category(PocketHorizonCategory.NowPlaying).assertIsSelected()
        compose.runOnIdle {
            assertEquals(1, requests.size)
            assertEquals(second, requests.single().first)
            assertEquals(listOf(1L, 2L, 3L), requests.single().second.map(Song::id))
            assertEquals(original, playback)
            assertFalse(playback.isPlaying)
        }
        compose.onNodeWithTag("pocketHorizonPlayPause").performClick()
        compose.runOnIdle { assertEquals(1, playPauseRequests) }
    }

    @Test
    fun albumDetailsUseCanonicalTrackContextAndRetainTheirLocationAcrossCategories() {
        mount()
        select(PocketHorizonCategory.Albums)
        albumNode("/alpha").performClick()
        compose.onNodeWithTag("pocketHorizonDetailTitle").assertTextContains("Alpha")
        select(PocketHorizonCategory.Songs)
        select(PocketHorizonCategory.Albums)
        compose.onNodeWithTag("pocketHorizonDetailTitle").assertTextContains("Alpha")
        songNode(second).performClick()
        compose.runOnIdle {
            assertEquals(second, requests.single().first)
            assertEquals(listOf(1L, 2L), requests.single().second.map(Song::id))
            assertEquals("/alpha", navigation.albumKey)
        }
        select(PocketHorizonCategory.Albums)
        compose.onNodeWithTag("pocketHorizonLibraryBack").performClick()
        albumNode("/alpha").assertIsDisplayed()
        compose.runOnIdle { assertEquals(null, navigation.albumKey) }
    }

    @Test
    fun artistDetailsUseCanonicalMembershipAcrossAlbumsAndScopedBackReturnsToTheParent() {
        val later = song(4L, "Later", "/later", "Zulu", "EXAMPLE", 1)
        library = ready(listOf(later) + supplied)
        mount()
        select(PocketHorizonCategory.Artists)
        artistNode("Example").performClick()
        list().performScrollToIndex(3) // Artist summary precedes the three canonical tracks.
        songNode(later).performClick()
        compose.runOnIdle {
            assertEquals(later, requests.single().first)
            assertEquals(listOf(1L, 2L, 4L), requests.single().second.map(Song::id))
            assertTrue(requests.single().second.none { it.artist == "Other" })
        }
        select(PocketHorizonCategory.Artists)
        pressBack()
        artistNode("Example").assertIsDisplayed()
        compose.runOnIdle { assertEquals(null, navigation.artistKey) }
        pressBack()
        category(PocketHorizonCategory.NowPlaying).assertIsSelected()
    }

    @Test
    fun independentCategoryAnchorsSurviveSwitchesAndVerticalScrollingDoesNotActivateMusic() {
        val many = (1L..100L).map {
            song(it, "Track " + it.toString().padStart(3, '0'), "/album/$it", "Album " + it.toString().padStart(3, '0'),
                "Artist " + it.toString().padStart(3, '0'), 1)
        }
        library = ready(many.reversed())
        mount()
        select(PocketHorizonCategory.Songs)
        list().performScrollToIndex(60)
        compose.waitForIdle()
        val songAnchor = compose.runOnIdle { navigation.anchor(PocketHorizonList.Songs) }
        assertEquals(many[60].stableUiKey(), songAnchor.key)
        select(PocketHorizonCategory.Albums)
        list().performScrollToIndex(40)
        compose.waitForIdle()
        val albumAnchor = compose.runOnIdle { navigation.anchor(PocketHorizonList.Albums) }
        assertEquals("/album/41", albumAnchor.key)
        select(PocketHorizonCategory.Songs)
        songNode(many[60]).assertIsDisplayed()
        val tabsBefore = bounds("pocketHorizonCategorySongs")
        list().performTouchInput { swipeUp() }
        compose.runOnIdle { assertTrue(requests.isEmpty()); assertEquals(PocketHorizonCategory.Songs, navigation.category) }
        assertEquals(tabsBefore, bounds("pocketHorizonCategorySongs"))
        select(PocketHorizonCategory.Albums)
        albumNode("/album/41").assertIsDisplayed()
        compose.runOnIdle { assertEquals(albumAnchor.key, navigation.anchor(PocketHorizonList.Albums).key) }
        albumNode("/album/41").performClick()
        compose.onNodeWithTag("pocketHorizonLibraryBack").performClick()
        albumNode("/album/41").assertIsDisplayed()
        compose.runOnIdle { assertTrue(requests.isEmpty()) }
    }

    @Test
    fun reorderingAndTransientEmptyDataPreserveTheVisibleSongIdentity() {
        val many = (1L..60L).map {
            song(it, "Track " + it.toString().padStart(3, '0'), "/album", "Album", "Artist", it.toInt())
        }
        library = ready(many)
        mount()
        select(PocketHorizonCategory.Songs)
        list().performScrollToIndex(30)
        compose.waitForIdle()
        val savedKey = many[30].stableUiKey()
        compose.runOnIdle { assertEquals(savedKey, navigation.anchor(PocketHorizonList.Songs).key) }
        val inserted = song(1000L, "A new first track", "/inserted", "Inserted", "Artist", 1)
        compose.runOnIdle { library = ready(listOf(inserted) + many) }
        songNode(many[30]).assertIsDisplayed()
        compose.runOnIdle { assertEquals(savedKey, navigation.anchor(PocketHorizonList.Songs).key) }
        compose.runOnIdle { library = library.copy(songs = emptyList(), isRefreshing = true) }
        message().assertTextContains(compose.activity.getString(R.string.pocket_horizon_library_refreshing))
        compose.runOnIdle { assertEquals(savedKey, navigation.anchor(PocketHorizonList.Songs).key) }
        compose.runOnIdle { library = ready(listOf(inserted) + many) }
        songNode(many[30]).assertIsDisplayed()
        compose.runOnIdle { assertEquals(savedKey, navigation.anchor(PocketHorizonList.Songs).key); assertTrue(requests.isEmpty()) }
    }

    @Test
    fun partialRefreshDoesNotReplaceAMissingStableAnchorWithItsFallbackRow() {
        val many = (1L..60L).map {
            song(it, "Track " + it.toString().padStart(3, '0'), "/album", "Album", "Artist", it.toInt())
        }
        library = ready(many)
        mount()
        select(PocketHorizonCategory.Songs)
        list().performScrollToIndex(30)
        compose.waitForIdle()
        val savedKey = many[30].stableUiKey()
        compose.runOnIdle {
            assertEquals(savedKey, navigation.anchor(PocketHorizonList.Songs).key)
            library = ready(many.take(10)).copy(isRefreshing = true)
        }
        compose.onNodeWithTag("pocketHorizonLibraryStatus")
            .assertTextContains(compose.activity.getString(R.string.pocket_horizon_library_refreshing))
        compose.runOnIdle { assertEquals(savedKey, navigation.anchor(PocketHorizonList.Songs).key) }
        compose.runOnIdle { library = ready(many) }
        songNode(many[30]).assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(savedKey, navigation.anchor(PocketHorizonList.Songs).key)
            assertTrue(requests.isEmpty())
        }
    }

    @Test
    fun saverRestoresTheActiveDetailAndTheOtherCategoryDetailAfterRecreation() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Content() }
        select(PocketHorizonCategory.Albums)
        albumNode("/alpha").performClick()
        select(PocketHorizonCategory.Artists)
        artistNode("Example").performClick()
        restoration.emulateSavedInstanceStateRestore()
        category(PocketHorizonCategory.Artists).assertIsSelected()
        compose.onNodeWithTag("pocketHorizonDetailTitle").assertExists()
        compose.runOnIdle {
            assertEquals(artistIdentity("Example").key, navigation.artistKey)
            assertEquals("/alpha", navigation.albumKey)
            assertTrue(requests.isEmpty())
        }
        select(PocketHorizonCategory.Albums)
        compose.onNodeWithTag("pocketHorizonDetailTitle").assertTextContains("Alpha")
    }

    @Test
    fun provisionalEmptyAndErrorsRetainDetailsUntilAnAuthoritativeRemoval() {
        mount()
        select(PocketHorizonCategory.Albums)
        albumNode("/alpha").performClick()
        compose.runOnIdle { library = library.copy(songs = emptyList(), isRefreshing = true) }
        message().assertTextContains(compose.activity.getString(R.string.pocket_horizon_library_refreshing))
        compose.runOnIdle { assertEquals("/alpha", navigation.albumKey) }
        compose.runOnIdle { library = library.copy(isRefreshing = false, errorMessage = "Scan interrupted") }
        message().assertTextContains(compose.activity.getString(R.string.pocket_horizon_library_error))
        compose.runOnIdle { assertEquals("/alpha", navigation.albumKey) }
        compose.runOnIdle { library = ready(supplied) }
        compose.onNodeWithTag("pocketHorizonDetailTitle").assertTextContains("Alpha")
        compose.runOnIdle { library = ready(emptyList()) }
        compose.waitUntil(5_000) { navigation.albumKey == null }
        category(PocketHorizonCategory.Albums).assertIsSelected()
        message().assertTextContains(compose.activity.getString(R.string.pocket_horizon_library_no_albums))
        compose.runOnIdle { assertTrue(requests.isEmpty()) }
    }

    @Test
    fun initialLoadingAndAuthoritativeEmptyRemainSafeWithNoCurrentTrack() {
        playback = PlaybackUiState.Disconnected
        library = LibraryUiState.Empty
        mount()
        select(PocketHorizonCategory.Songs)
        message().assertTextContains(compose.activity.getString(R.string.pocket_horizon_library_loading))
        compose.runOnIdle { library = ready(emptyList()) }
        message().assertTextContains(compose.activity.getString(R.string.pocket_horizon_library_no_songs))
        compose.runOnIdle { assertEquals(PocketHorizonCategory.Songs, navigation.category); assertTrue(requests.isEmpty()) }
        select(PocketHorizonCategory.NowPlaying)
        compose.onNodeWithTag("pocketHorizonPlayPause").assertIsNotEnabled()
    }

    @Test
    fun missingCallbackAndDisabledInputDoNotDispatchTrackSelections() {
        selectionAvailable = false
        mount()
        select(PocketHorizonCategory.Songs)
        songNode(first).assertIsNotEnabled()
        songNode(first).performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle { selectionAvailable = true; inputEnabled = false }
        songNode(first).assertIsNotEnabled()
        songNode(first).performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle { assertTrue(requests.isEmpty()); assertEquals(PocketHorizonCategory.Songs, navigation.category) }
    }

    @Test
    fun rtlLargeTextAndConstrainedBrowsingKeepListsBoundedAndTabsReachable() {
        fontScale = 2f
        direction = LayoutDirection.Rtl
        val long = first.copy(title = "Long 日本語 عنوان track title ".repeat(25),
            artist = "A long artist name ".repeat(20))
        library = ready(listOf(long, second, other))
        mount()
        select(PocketHorizonCategory.Songs)
        val tab = category(PocketHorizonCategory.Songs).fetchSemanticsNode()
        assertTrue(tab.boundsInRoot.height >= 48f * renderedDensity - 1f)
        assertTrue(bounds("pocketHorizonCategoryNowPlaying").left > bounds("pocketHorizonCategoryArtists").left)
        assertContained(bounds("pocketHorizonCategoryBody"), bounds("pocketHorizonLibraryList"))
        val row = songNode(long).fetchSemanticsNode()
        assertEquals(Role.Button, row.config[SemanticsProperties.Role])
        assertTrue(row.boundsInRoot.height >= 48f * renderedDensity - 1f)
        compose.runOnIdle { fontScale = 1f; direction = LayoutDirection.Ltr; viewport = DpSize(760.dp, 320.dp) }
        list().assertExists()
        assertContained(bounds("pocketHorizonCategoryBody"), bounds("pocketHorizonLibraryList"))
        category(PocketHorizonCategory.NowPlaying).assertExists()
        compose.runOnIdle { assertTrue(requests.isEmpty()) }
    }

    private fun assertContained(parent: Rect, child: Rect) {
        assertTrue(child.left >= parent.left - 1f)
        assertTrue(child.top >= parent.top - 1f)
        assertTrue(child.right <= parent.right + 1f)
        assertTrue(child.bottom <= parent.bottom + 1f)
        assertTrue("List viewport has finite height", child.height > 0f && child.height.isFinite())
    }

    private fun ready(songs: List<Song>) = LibraryUiState(songs = songs, hasPublishedInitialLibraryState = true)

    private fun song(id: Long, title: String, folder: String, album: String, artist: String, track: Int) =
        Song(id, title, artist, album, track, 180_000L,
            Uri.parse("content://media/external/audio/media/$id"), "$folder/$id.flac", folder, null)
}
