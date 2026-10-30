package io.github.rsgarrido.sazanami.ui.player.pockethorizon

import androidx.compose.runtime.saveable.SaverScope
import io.github.rsgarrido.sazanami.ui.player.PlayerPresentation
import org.junit.Assert.*
import org.junit.Test

class PocketHorizonNavigationLifecycleTest {
    @Test fun completedCollapseAndLeavingPresentationShowNowPlayingWithoutDiscardingBrowseLocations() {
        val navigation = browsing()
        navigation.updatePlayerLifecycle(true, PlayerPresentation.Collapsed, PlayerPresentation.Collapsed, 0f, false, false)
        assertEquals(PocketHorizonCategory.NowPlaying, navigation.category)
        assertEquals("album", navigation.albumKey)
        assertEquals("artist", navigation.artistKey)
        assertEquals("song", navigation.anchor(PocketHorizonList.Songs).key)
        navigation.selectCategory(PocketHorizonCategory.Albums)
        navigation.updatePlayerLifecycle(false, PlayerPresentation.Expanded, PlayerPresentation.Expanded, 1f, false, false)
        assertEquals(PocketHorizonCategory.NowPlaying, navigation.category)
    }

    @Test fun collapseExpansionReversalAndDragDoNotResetBeforeACompletedCollapse() {
        val cases = listOf(
            Triple(PlayerPresentation.Expanded, PlayerPresentation.Collapsed, .4f),
            Triple(PlayerPresentation.Expanded, PlayerPresentation.Expanded, .4f),
            Triple(PlayerPresentation.Collapsed, PlayerPresentation.Expanded, .6f),
            Triple(PlayerPresentation.Collapsed, PlayerPresentation.Collapsed, .6f),
            Triple(PlayerPresentation.Expanded, PlayerPresentation.Expanded, 1f)
        )
        cases.forEach { (settled, target, progress) ->
            val navigation = browsing()
            navigation.updatePlayerLifecycle(true, settled, target, progress, false, true)
            assertEquals(PocketHorizonCategory.Artists, navigation.category)
        }
        for ((dragging, animating) in listOf(true to false, false to true)) {
            val navigation = browsing()
            navigation.updatePlayerLifecycle(true, PlayerPresentation.Collapsed, PlayerPresentation.Collapsed,
                0f, dragging, animating)
            assertEquals(PocketHorizonCategory.Artists, navigation.category)
        }
    }

    @Test fun restoredExpandedStateKeepsItsDetailAndBackStillReturnsToArtistList() {
        val scope = object : SaverScope { override fun canBeSaved(value: Any) = true }
        val saved = requireNotNull(with(PocketHorizonNavigationState.Saver) { scope.save(browsing()) })
        val navigation = requireNotNull(PocketHorizonNavigationState.Saver.restore(saved))
        navigation.updatePlayerLifecycle(true, PlayerPresentation.Expanded, PlayerPresentation.Expanded, 1f, false, false)
        assertEquals(PocketHorizonCategory.Artists, navigation.category)
        assertTrue(navigation.back())
        assertEquals(PocketHorizonCategory.Artists, navigation.category)
        assertNull(navigation.artistKey)
    }

    private fun browsing() = PocketHorizonNavigationState().apply {
        openAlbum("album")
        openArtist("artist")
        updateAnchor(PocketHorizonList.Songs, "song", 10, 12)
    }
}
