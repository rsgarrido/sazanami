package io.github.rsgarrido.sazanami.ui.player.pocketadvance

import androidx.compose.runtime.saveable.SaverScope
import io.github.rsgarrido.sazanami.ui.player.PlayerMorphState
import io.github.rsgarrido.sazanami.ui.player.PlayerPresentation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PocketAdvanceNavigationStateTest {
    @Test
    fun initialModeAndRootOrderMatchTheBrowserContract() {
        val navigation = PocketAdvanceNavigationState()
        assertEquals(PocketAdvanceLcdMode.NowPlaying, navigation.lcdMode)
        assertEquals(PocketAdvanceBrowserPage.Root, navigation.browserPage)
        assertEquals(PocketAdvanceRootEntry.NowPlaying, navigation.selectedRootEntry)
        assertEquals(listOf("NowPlaying", "Songs", "Artists", "Albums"),
            PocketAdvanceRootEntry.entries.map { it.name })
    }

    @Test
    fun startAndBackRetainThePreviousRootSelection() {
        val navigation = PocketAdvanceNavigationState()
        navigation.toggleMode()
        navigation.moveSelectionDown()
        navigation.moveSelectionDown()
        assertEquals(PocketAdvanceRootEntry.Artists, navigation.selectedRootEntry)
        navigation.toggleMode()
        assertEquals(PocketAdvanceLcdMode.NowPlaying, navigation.lcdMode)
        navigation.back()
        assertEquals(PocketAdvanceLcdMode.MusicBrowser, navigation.lcdMode)
        assertEquals(PocketAdvanceRootEntry.Artists, navigation.selectedRootEntry)
        navigation.back()
        assertEquals(PocketAdvanceLcdMode.NowPlaying, navigation.lcdMode)
        navigation.openBrowser()
        assertEquals(PocketAdvanceRootEntry.Artists, navigation.selectedRootEntry)
    }

    @Test
    fun selectionWrapsBothWaysAndCannotMoveInNowPlaying() {
        val navigation = PocketAdvanceNavigationState()
        navigation.moveSelectionUp()
        assertEquals(PocketAdvanceRootEntry.NowPlaying, navigation.selectedRootEntry)
        navigation.openBrowser()
        navigation.moveSelectionUp()
        assertEquals(PocketAdvanceRootEntry.Albums, navigation.selectedRootEntry)
        navigation.moveSelectionDown()
        assertEquals(PocketAdvanceRootEntry.NowPlaying, navigation.selectedRootEntry)
        navigation.moveSelectionDown()
        navigation.moveSelectionUp()
        assertEquals(PocketAdvanceRootEntry.NowPlaying, navigation.selectedRootEntry)
    }

    @Test
    fun confirmingNowPlayingOnlyChangesTheLcdMode() {
        val navigation = PocketAdvanceNavigationState()
        navigation.openBrowser()
        navigation.confirmSelection()
        assertEquals(PocketAdvanceLcdMode.NowPlaying, navigation.lcdMode)
        navigation.openBrowser()
        navigation.moveSelectionDown()
        navigation.activateRootEntry(PocketAdvanceRootEntry.NowPlaying)
        assertEquals(PocketAdvanceLcdMode.NowPlaying, navigation.lcdMode)
        assertEquals(PocketAdvanceRootEntry.NowPlaying, navigation.selectedRootEntry)
    }

    @Test
    fun allFourRootEntriesAreAvailableAndOpenTheirTypedDestinations() {
        val navigation = PocketAdvanceNavigationState()
        val destinations = listOf(PocketAdvanceBrowserPage.Songs, PocketAdvanceBrowserPage.Artists,
            PocketAdvanceBrowserPage.AlbumCatalogue)
        PocketAdvanceRootEntry.entries.drop(1).zip(destinations).forEach { (entry, destination) ->
            navigation.openBrowser()
            assertTrue(entry.isAvailable)
            navigation.activateRootEntry(entry)
            assertEquals(destination, navigation.browserPage)
            assertEquals(entry, navigation.selectedRootEntry)
            assertEquals(PocketAdvanceLcdMode.MusicBrowser, navigation.lcdMode)
            navigation.back()
            assertEquals(PocketAdvanceBrowserPage.Root, navigation.browserPage)
        }
        navigation.activateRootEntry(PocketAdvanceRootEntry.NowPlaying)
        assertEquals(PocketAdvanceLcdMode.NowPlaying, navigation.lcdMode)
    }

    @Test
    fun collapseModeResetKeepsBrowserLocation() {
        val navigation = PocketAdvanceNavigationState(PocketAdvanceRootEntry.Albums)
        navigation.openBrowser()
        navigation.showNowPlaying()
        navigation.openBrowser()
        assertEquals(PocketAdvanceRootEntry.Albums, navigation.selectedRootEntry)
    }

    @Test
    fun savedBrowserLocationRestoresIntoSafeNowPlaying() {
        val restored = requireNotNull(PocketAdvanceNavigationState.Saver.restore("Artists"))
        assertEquals(PocketAdvanceLcdMode.NowPlaying, restored.lcdMode)
        restored.openBrowser()
        assertEquals(PocketAdvanceRootEntry.Artists, restored.selectedRootEntry)
        val unknown = requireNotNull(PocketAdvanceNavigationState.Saver.restore("RemovedPage"))
        assertEquals(PocketAdvanceRootEntry.NowPlaying, unknown.selectedRootEntry)
    }

    private fun songsNavigation(keys: List<String>) =
        PocketAdvanceNavigationState(PocketAdvanceRootEntry.Songs).apply {
            openBrowser()
            confirmSelection()
            reconcileSongs(keys, isLibraryReady = true)
        }

    @Test
    fun songsOpensAtFirstSongAndBackRestoresTheRootSelection() {
        val keys = listOf("third-in-title-order", "first-in-title-order", "second-in-title-order")
        val navigation = songsNavigation(keys)
        assertTrue(PocketAdvanceRootEntry.Songs.isAvailable)
        assertEquals(PocketAdvanceBrowserPage.Songs, navigation.browserPage)
        assertEquals(keys.first(), navigation.selectedSongKey)
        assertEquals(0, navigation.selectedSongIndex(keys))
        navigation.moveSelectionDown(keys)
        navigation.back()
        assertEquals(PocketAdvanceBrowserPage.Root, navigation.browserPage)
        assertEquals(PocketAdvanceRootEntry.Songs, navigation.selectedRootEntry)
        navigation.confirmSelection()
        assertEquals(PocketAdvanceBrowserPage.Songs, navigation.browserPage)
        assertEquals(keys[1], navigation.selectedSongKey)
    }

    @Test
    fun songsWrapsBothWaysWithoutChangingTheSuppliedOrder() {
        val keys = (0 until 10_000).map { "volume-a-song-$it" }
        val navigation = songsNavigation(keys)
        navigation.moveSelectionUp(keys)
        assertEquals(keys.last(), navigation.selectedSongKey)
        assertEquals(keys.lastIndex, navigation.selectedSongIndex(keys))
        navigation.moveSelectionDown(keys)
        assertEquals(keys.first(), navigation.selectedSongKey)
        navigation.moveSelectionDown(keys)
        assertEquals(keys[1], navigation.selectedSongKey)
        navigation.showNowPlaying()
        navigation.moveSelectionDown(keys)
        assertEquals(keys[1], navigation.selectedSongKey)
    }

    @Test
    fun startAndCompletedCollapseRetainSongsPageCursorAndViewport() {
        val keys = (0 until 50).map { "song-$it" }
        val navigation = songsNavigation(keys)
        navigation.selectSong(keys[23], 23)
        navigation.songsListState.requestScrollToItem(21, 13)
        val retainedList = navigation.songsListState
        navigation.toggleMode()
        assertEquals(PocketAdvanceLcdMode.NowPlaying, navigation.lcdMode)
        navigation.back()
        assertEquals(PocketAdvanceBrowserPage.Songs, navigation.browserPage)
        assertEquals(keys[23], navigation.selectedSongKey)
        assertSame(retainedList, navigation.songsListState)
        assertEquals(21, retainedList.firstVisibleItemIndex)
        assertEquals(13, retainedList.firstVisibleItemScrollOffset)
        navigation.showNowPlaying() // Same mode reset used after a completed collapse/fallback.
        navigation.openBrowser()
        assertEquals(PocketAdvanceBrowserPage.Songs, navigation.browserPage)
        assertEquals(keys[23], navigation.selectedSongKey)
        assertEquals(21, retainedList.firstVisibleItemIndex)
        assertEquals(13, retainedList.firstVisibleItemScrollOffset)
    }

    @Test
    fun reorderedAndInsertedSongsResolveSelectionByIdentityBeforeReconciliation() {
        val navigation = songsNavigation(listOf("a", "b", "c"))
        navigation.moveSelectionDown(listOf("a", "b", "c"))
        val reordered = listOf("new", "c", "a", "b")
        assertEquals(3, navigation.selectedSongIndex(reordered))
        assertEquals("b", reordered[navigation.selectedSongIndex(reordered)!!])
        navigation.reconcileSongs(reordered, isLibraryReady = true)
        assertEquals("b", navigation.selectedSongKey)
        assertEquals(3, navigation.selectedSongIndex(reordered))
    }

    @Test
    fun deletedSelectionChoosesAValidNeighborAndHighlightMatchesConfirmation() {
        val navigation = songsNavigation(listOf("a", "b", "c", "d"))
        navigation.selectSong("b", 1)
        val withoutB = listOf("a", "c", "d")
        assertEquals("c", withoutB[navigation.selectedSongIndex(withoutB)!!])
        navigation.reconcileSongs(withoutB, isLibraryReady = true)
        assertEquals("c", navigation.selectedSongKey)
        navigation.selectSong("d", 2)
        navigation.reconcileSongs(listOf("a", "c"), isLibraryReady = true)
        assertEquals("c", navigation.selectedSongKey)
        assertEquals(1, navigation.selectedSongIndex(listOf("a", "c")))
        assertTrue(navigation.canConfirm(listOf("a", "c")))
    }

    @Test
    fun transientEmptyAndPartialSnapshotsRetainSelectionAndScrollUntilAuthoritative() {
        val original = listOf("a", "b", "c")
        val navigation = songsNavigation(original)
        navigation.selectSong("b", 1)
        navigation.songsListState.requestScrollToItem(1, 9)
        navigation.reconcileSongs(emptyList(), isLibraryReady = false)
        assertEquals("b", navigation.selectedSongKey)
        assertNull(navigation.selectedSongIndex(emptyList()))
        assertFalse(navigation.canConfirm(emptyList()))
        assertEquals(1, navigation.songsListState.firstVisibleItemIndex)
        assertEquals(9, navigation.songsListState.firstVisibleItemScrollOffset)
        navigation.reconcileSongs(listOf("c", "a"), isLibraryReady = false)
        assertEquals("b", navigation.selectedSongKey)
        assertEquals(1, navigation.selectedSongIndex(listOf("c", "a")))
        navigation.reconcileSongs(listOf("b", "a", "c"), isLibraryReady = true)
        assertEquals("b", navigation.selectedSongKey)
        assertEquals(0, navigation.selectedSongIndex(listOf("b", "a", "c")))
    }

    @Test
    fun authoritativeEmptyLibraryHasNoSelectionAndKeepsSongsPage() {
        val navigation = songsNavigation(listOf("a", "b"))
        navigation.selectSong("b", 1)
        navigation.songsListState.requestScrollToItem(1, 9)
        navigation.reconcileSongs(emptyList(), isLibraryReady = true)
        assertNull(navigation.selectedSongKey)
        assertNull(navigation.selectedSongIndex(emptyList()))
        assertFalse(navigation.canConfirm(emptyList()))
        navigation.moveSelectionUp(emptyList())
        navigation.moveSelectionDown(emptyList())
        assertEquals(PocketAdvanceBrowserPage.Songs, navigation.browserPage)
        assertEquals(0, navigation.songsListState.firstVisibleItemIndex)
        assertEquals(0, navigation.songsListState.firstVisibleItemScrollOffset)
        navigation.reconcileSongs(listOf("new-first"), isLibraryReady = true)
        assertEquals("new-first", navigation.selectedSongKey)
    }

    @Test
    fun dpadScrollRequestsAreConsumedOnceAndNotReplayedOnStartOrTouchSelection() {
        val keys = listOf("a", "b", "c")
        val navigation = songsNavigation(keys)
        assertFalse(navigation.takeSongsScrollRequest())
        navigation.moveSelectionDown(keys)
        assertTrue(navigation.takeSongsScrollRequest())
        assertFalse(navigation.takeSongsScrollRequest())
        navigation.toggleMode()
        navigation.toggleMode()
        assertFalse(navigation.takeSongsScrollRequest())
        navigation.selectSong("c", 2)
        assertFalse(navigation.takeSongsScrollRequest())
        navigation.back()
        navigation.moveSelectionDown()
        assertTrue(navigation.takeRootScrollRequest())
        assertFalse(navigation.takeRootScrollRequest())
    }

    @Test
    fun saverRoundTripsBothPageViewportsAndSongsIdentityIntoSafeNowPlaying() {
        val navigation = songsNavigation(listOf("a", "b", "c"))
        navigation.selectSong("b", 1)
        navigation.rootListState.requestScrollToItem(1, 4)
        navigation.songsListState.requestScrollToItem(1, 11)
        val scope = object : SaverScope {
            override fun canBeSaved(value: Any): Boolean = true
        }
        val saved = with(PocketAdvanceNavigationState.Saver) { scope.save(navigation) }
        val restored = requireNotNull(PocketAdvanceNavigationState.Saver.restore(requireNotNull(saved)))
        assertEquals(PocketAdvanceLcdMode.NowPlaying, restored.lcdMode)
        assertEquals(PocketAdvanceBrowserPage.Songs, restored.browserPage)
        assertEquals(PocketAdvanceRootEntry.Songs, restored.selectedRootEntry)
        assertEquals("b", restored.selectedSongKey)
        assertEquals(1, restored.rootListState.firstVisibleItemIndex)
        assertEquals(4, restored.rootListState.firstVisibleItemScrollOffset)
        assertEquals(1, restored.songsListState.firstVisibleItemIndex)
        assertEquals(11, restored.songsListState.firstVisibleItemScrollOffset)
    }

    @Test
    fun restoredViewportUsesItsSongKeyWhenTheNewLibraryOrderChanges() {
        val restored = requireNotNull(PocketAdvanceNavigationState.Saver.restore(
            listOf("Songs", "Songs", "selected", 2, 0, 0, 2, 13, "visible")
        ))
        restored.reconcileSongs(emptyList(), isLibraryReady = false)
        assertEquals(2, restored.songsListState.firstVisibleItemIndex)
        val keys = listOf("selected", "a", "b", "c", "visible")
        restored.reconcileSongs(keys, isLibraryReady = true)
        assertEquals("selected", restored.selectedSongKey)
        assertEquals(0, restored.selectedSongIndex(keys))
        assertEquals(4, restored.songsListState.firstVisibleItemIndex)
        assertEquals(13, restored.songsListState.firstVisibleItemScrollOffset)
    }

    @Test
    fun holdTicksMoveOneAdjacentSongAndStopAtBothBoundaries() {
        val keys = listOf("a", "b", "c", "d")
        val navigation = songsNavigation(keys)
        assertTrue(navigation.repeatSongSelection(1, keys))
        assertEquals("b", navigation.selectedSongKey)
        assertTrue(navigation.repeatSongSelection(1, keys))
        assertEquals("c", navigation.selectedSongKey)
        assertTrue(navigation.repeatSongSelection(1, keys))
        assertEquals("d", navigation.selectedSongKey)
        repeat(100) { assertFalse(navigation.repeatSongSelection(1, keys)) }
        assertEquals("d", navigation.selectedSongKey)
        assertTrue(navigation.repeatSongSelection(-1, keys))
        assertEquals("c", navigation.selectedSongKey)
        assertTrue(navigation.repeatSongSelection(-1, keys))
        assertEquals("b", navigation.selectedSongKey)
        assertTrue(navigation.repeatSongSelection(-1, keys))
        assertEquals("a", navigation.selectedSongKey)
        assertFalse(navigation.repeatSongSelection(-1, keys))
        navigation.moveSelectionUp(keys)
        assertEquals("d", navigation.selectedSongKey)
        navigation.moveSelectionDown(keys)
        assertEquals("a", navigation.selectedSongKey)
    }

    @Test
    fun holdTicksCannotNavigateNowPlayingRootOrAnEmptyLibrary() {
        val keys = listOf("a", "b", "c")
        val navigation = songsNavigation(keys)
        navigation.showNowPlaying()
        assertFalse(navigation.repeatSongSelection(1, keys))
        assertEquals("a", navigation.selectedSongKey)
        navigation.openBrowser()
        navigation.back()
        assertFalse(navigation.repeatSongSelection(1, keys))
        assertEquals(PocketAdvanceRootEntry.Songs, navigation.selectedRootEntry)
        navigation.confirmSelection()
        assertFalse(navigation.repeatSongSelection(1, emptyList()))
        assertFalse(navigation.repeatSongSelection(-1, emptyList()))
        assertFalse(navigation.repeatSongSelection(0, keys))
        assertEquals("a", navigation.selectedSongKey)
    }

    @Test
    fun holdTicksUseCurrentStableIdentityAfterReorderingOrDeletingSongs() {
        val navigation = songsNavigation(listOf("a", "b", "c", "d"))
        navigation.selectSong("b", 1)
        val reordered = listOf("new", "a", "c", "b", "d")
        assertTrue(navigation.repeatSongSelection(1, reordered))
        assertEquals("d", navigation.selectedSongKey)
        assertEquals(4, navigation.selectedSongIndex(reordered))
        assertFalse(navigation.repeatSongSelection(1, reordered))
        val removed = listOf("new", "a", "c", "b")
        assertTrue(navigation.repeatSongSelection(-1, removed))
        assertEquals("c", navigation.selectedSongKey)
        assertEquals(2, navigation.selectedSongIndex(removed))
    }

    @Test
    fun holdBoundaryDoesNotRequestMoreScrollingAndSingleSongNeverRepeats() {
        val keys = listOf("only")
        val navigation = songsNavigation(keys)
        navigation.moveSelectionDown(keys)
        assertTrue(navigation.takeSongsScrollRequest())
        assertFalse(navigation.repeatSongSelection(1, keys))
        assertFalse(navigation.repeatSongSelection(-1, keys))
        assertFalse(navigation.takeSongsScrollRequest())
        assertEquals("only", navigation.selectedSongKey)
    }

    @Test
    fun collapseCaptureYieldsBrowserBeforeProgressMovesAndSettlementCanRestoreIt() = runBlocking {
        val navigation = PocketAdvanceNavigationState(PocketAdvanceRootEntry.Albums)
        val player = PlayerMorphState(PlayerPresentation.Expanded, CoroutineScope(Dispatchers.Unconfined))
        navigation.openBrowser()
        assertTrue(shouldShowPocketAdvanceBrowser(navigation, player))
        player.beginDragWithRange(500f)
        assertEquals(1f, player.progress, 0f)
        assertFalse(shouldShowPocketAdvanceBrowser(navigation, player))
        player.dragBy(120f)
        assertFalse(shouldShowPocketAdvanceBrowser(navigation, player))
        // The existing presentation boundary settles the original expanded target, without a new controller.
        player.settleForPresentationChange()
        assertTrue(shouldShowPocketAdvanceBrowser(navigation, player))
        assertEquals(PocketAdvanceRootEntry.Albums, navigation.selectedRootEntry)
    }
}
