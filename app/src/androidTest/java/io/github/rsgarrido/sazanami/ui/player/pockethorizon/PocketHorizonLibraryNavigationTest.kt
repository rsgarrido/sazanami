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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
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
                            navigationState = navigation
                        )
                    }
                }
            }
        }
    }

    private fun mount() = compose.setContent { Content() }
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
