package io.github.rsgarrido.sazanami.ui.player.pockethorizon

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.*
import org.junit.Test

class PocketHorizonConsoleNavigationTest {
    @Test fun startSelectAndDirectionalCategoriesRetainDetailAndFocusKeysAcrossSave() {
        val state = PocketHorizonNavigationState()
        state.stepCategory(1)
        assertEquals(PocketHorizonCategory.Songs, state.category)
        state.stepCategory(1)
        state.openAlbum("album-key")
        state.focus(PocketHorizonList.Albums, "album-key")
        state.focus(PocketHorizonList.AlbumTracks, "track-key")
        state.toggleMusicBrowser()
        assertEquals(PocketHorizonCategory.NowPlaying, state.category)
        state.toggleMusicBrowser()
        assertEquals(PocketHorizonCategory.Albums, state.category)
        assertEquals("album-key", state.albumKey)
        state.showControlGuide = true
        val scope = object : SaverScope { override fun canBeSaved(value: Any): Boolean = true }
        val saved = with(PocketHorizonNavigationState.Saver) { scope.save(state) }
        val restored = requireNotNull(PocketHorizonNavigationState.Saver.restore(requireNotNull(saved)))
        assertFalse(restored.showControlGuide)
        assertEquals("track-key", restored.focusedKey(PocketHorizonList.AlbumTracks))
        assertTrue(restored.back())
        assertNull(restored.albumKey)
        assertEquals("album-key", restored.focusedKey(PocketHorizonList.Albums))
        restored.stepCategory(1)
        assertEquals(PocketHorizonCategory.Artists, restored.category)
        restored.stepCategory(20)
        assertEquals(PocketHorizonCategory.Artists, restored.category)
        restored.stepCategory(-20)
        assertEquals(PocketHorizonCategory.NowPlaying, restored.category)
    }
}
