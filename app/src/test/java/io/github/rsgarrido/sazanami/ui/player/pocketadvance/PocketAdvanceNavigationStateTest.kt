package io.github.rsgarrido.sazanami.ui.player.pocketadvance

import io.github.rsgarrido.sazanami.ui.player.PlayerMorphState
import io.github.rsgarrido.sazanami.ui.player.PlayerPresentation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun unavailableEntriesNeverOpenADestination() {
        val navigation = PocketAdvanceNavigationState()
        navigation.openBrowser()
        PocketAdvanceRootEntry.entries.drop(1).forEach { entry ->
            navigation.moveSelectionDown()
            assertEquals(entry, navigation.selectedRootEntry)
            assertFalse(entry.isAvailable)
            navigation.confirmSelection()
            navigation.activateRootEntry(entry)
            assertEquals(PocketAdvanceLcdMode.MusicBrowser, navigation.lcdMode)
            assertEquals(PocketAdvanceBrowserPage.Root, navigation.browserPage)
            assertEquals(entry, navigation.selectedRootEntry)
        }
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
