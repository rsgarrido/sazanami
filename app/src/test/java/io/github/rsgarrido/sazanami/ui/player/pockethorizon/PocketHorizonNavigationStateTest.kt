package io.github.rsgarrido.sazanami.ui.player.pockethorizon

import androidx.compose.runtime.saveable.SaverScope
import io.github.rsgarrido.sazanami.ui.player.pocketadvance.PocketAdvanceBrowserPage
import io.github.rsgarrido.sazanami.ui.player.pocketadvance.PocketAdvanceLcdMode
import io.github.rsgarrido.sazanami.ui.player.pocketadvance.PocketAdvanceNavigationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PocketHorizonNavigationStateTest {
    @Test
    fun startsInNowPlayingWithOnlyTheFourIntendedCategories() {
        val state = PocketHorizonNavigationState()
        assertEquals(PocketHorizonCategory.NowPlaying, state.category)
        assertEquals(listOf("NowPlaying", "Songs", "Albums", "Artists"),
            PocketHorizonCategory.entries.map { it.name })
        assertFalse(state.canGoBack)
        assertFalse(state.back())
        assertNull(state.albumKey)
        assertNull(state.artistKey)
        PocketHorizonList.entries.forEach { assertEquals(PocketHorizonScrollAnchor(), state.anchor(it)) }
    }

    @Test
    fun switchingCategoriesRetainsOpenDetailsAndIndependentBrowsingPositions() {
        val state = PocketHorizonNavigationState()
        state.updateAnchor(PocketHorizonList.Songs, "song-10", 10, 7)
        state.updateAnchor(PocketHorizonList.Albums, "album-4", 4, 12)
        state.updateAnchor(PocketHorizonList.Artists, "artist-8", 8, 20)
        state.openAlbum("album-4")
        state.updateAnchor(PocketHorizonList.AlbumTracks, "album-track-3", 3, 9)
        state.openArtist("artist-8")
        state.updateAnchor(PocketHorizonList.ArtistTracks, "artist-track-6", 6, 15)
        state.selectCategory(PocketHorizonCategory.Songs)
        assertEquals("song-10", state.anchor(PocketHorizonList.Songs).key)
        state.selectCategory(PocketHorizonCategory.Albums)
        assertEquals("album-4", state.albumKey)
        assertEquals(PocketHorizonScrollAnchor("album-track-3", 3, 9), state.anchor(PocketHorizonList.AlbumTracks))
        state.selectCategory(PocketHorizonCategory.Artists)
        assertEquals("artist-8", state.artistKey)
        assertEquals(PocketHorizonScrollAnchor("artist-track-6", 6, 15), state.anchor(PocketHorizonList.ArtistTracks))
        assertEquals(PocketHorizonScrollAnchor("album-4", 4, 12), state.anchor(PocketHorizonList.Albums))
        assertEquals(PocketHorizonScrollAnchor("artist-8", 8, 20), state.anchor(PocketHorizonList.Artists))
    }

    @Test
    fun detailBackRestoresItsParentThenCategoryBackShowsNowPlaying() {
        val state = PocketHorizonNavigationState()
        state.updateAnchor(PocketHorizonList.Albums, "album", 7, 19)
        state.openAlbum("album")
        assertTrue(state.back())
        assertEquals(PocketHorizonCategory.Albums, state.category)
        assertNull(state.albumKey)
        assertEquals(PocketHorizonScrollAnchor("album", 7, 19), state.anchor(PocketHorizonList.Albums))
        assertTrue(state.back())
        assertEquals(PocketHorizonCategory.NowPlaying, state.category)
        state.updateAnchor(PocketHorizonList.Artists, "artist", 3, 5)
        state.openArtist("artist")
        assertTrue(state.back())
        assertEquals(PocketHorizonCategory.Artists, state.category)
        assertNull(state.artistKey)
        assertEquals(PocketHorizonScrollAnchor("artist", 3, 5), state.anchor(PocketHorizonList.Artists))
        assertTrue(state.back())
        assertFalse(state.back())
    }

    @Test
    fun reopeningTheSameDetailRetainsItsViewportButAnotherGroupStartsAtTheTop() {
        val state = PocketHorizonNavigationState()
        state.openAlbum("one")
        state.updateAnchor(PocketHorizonList.AlbumTracks, "track", 4, 6)
        state.back()
        state.openAlbum("one")
        assertEquals("track", state.anchor(PocketHorizonList.AlbumTracks).key)
        state.openAlbum("two")
        assertEquals(PocketHorizonScrollAnchor(), state.anchor(PocketHorizonList.AlbumTracks))
        state.openArtist("one")
        state.updateAnchor(PocketHorizonList.ArtistTracks, "artist-track", 5, 7)
        state.back()
        state.openArtist("one")
        assertEquals("artist-track", state.anchor(PocketHorizonList.ArtistTracks).key)
        state.openArtist("two")
        assertEquals(PocketHorizonScrollAnchor(), state.anchor(PocketHorizonList.ArtistTracks))
    }

    @Test
    fun stableKeysRestoreAcrossReorderingAndIndicesClampOnlyAfterAuthoritativeRemoval() {
        val state = PocketHorizonNavigationState()
        state.updateAnchor(PocketHorizonList.Songs, "b", 1, 13)
        assertEquals(PocketHorizonScrollAnchor("b", 2, 13),
            state.resolveAnchor(PocketHorizonList.Songs, listOf("c", "a", "b"), true))
        state.updateAnchor(PocketHorizonList.Songs, "missing", 999, 80)
        assertEquals(PocketHorizonScrollAnchor("b", 1, 0),
            state.resolveAnchor(PocketHorizonList.Songs, listOf("a", "b"), true))
        assertEquals(PocketHorizonScrollAnchor("b", 1, 0), state.anchor(PocketHorizonList.Songs))
    }

    @Test
    fun loadingEmptyAndPartialSnapshotsDoNotEraseRetainedStableAnchors() {
        val state = PocketHorizonNavigationState()
        val original = PocketHorizonScrollAnchor("returning", 8, 44)
        state.updateAnchor(PocketHorizonList.Songs, original.key, original.index, original.offset)
        assertEquals(PocketHorizonScrollAnchor(), state.resolveAnchor(PocketHorizonList.Songs, emptyList(), false))
        assertEquals(original, state.anchor(PocketHorizonList.Songs))
        assertEquals(PocketHorizonScrollAnchor("temporary", 0, 0),
            state.resolveAnchor(PocketHorizonList.Songs, listOf("temporary"), false))
        assertEquals(original, state.anchor(PocketHorizonList.Songs))
        assertEquals(PocketHorizonScrollAnchor("returning", 1, 44),
            state.resolveAnchor(PocketHorizonList.Songs, listOf("other", "returning"), true))
        state.resolveAnchor(PocketHorizonList.Songs, emptyList(), true)
        assertEquals(PocketHorizonScrollAnchor(), state.anchor(PocketHorizonList.Songs))
    }

    @Test
    fun onlyAuthoritativeLibraryRemovalInvalidatesContextsAndSelections() {
        val state = PocketHorizonNavigationState()
        state.openAlbum("album")
        state.updateAnchor(PocketHorizonList.AlbumTracks, "song", 1, 2)
        state.openArtist("artist")
        state.updateAnchor(PocketHorizonList.ArtistTracks, "song", 2, 3)
        state.selectSong("song")
        state.reconcileLibrary(emptySet(), emptySet(), false, emptySet())
        assertEquals("album", state.albumKey)
        assertEquals("artist", state.artistKey)
        assertEquals("song", state.selectedSongKey)
        state.reconcileLibrary(setOf("album"), setOf("artist"), true, setOf("song"))
        assertEquals("album", state.albumKey)
        assertEquals("artist", state.artistKey)
        state.reconcileLibrary(emptySet(), emptySet(), true, emptySet())
        assertNull(state.albumKey)
        assertNull(state.artistKey)
        assertNull(state.selectedSongKey)
        assertEquals(PocketHorizonCategory.Artists, state.category)
        assertEquals(PocketHorizonScrollAnchor(), state.anchor(PocketHorizonList.AlbumTracks))
        assertEquals(PocketHorizonScrollAnchor(), state.anchor(PocketHorizonList.ArtistTracks))
    }

    @Test
    fun saverRestoresActiveCategoryBothDetailsAndCompactStableAnchors() {
        val original = PocketHorizonNavigationState()
        original.openAlbum("")
        original.openArtist("artist")
        original.selectSong("selected-song")
        PocketHorizonList.entries.forEachIndexed { index, list ->
            original.updateAnchor(list, if (list == PocketHorizonList.Albums) "" else list.name, index + 2, index + 3)
        }
        val saved = save(original)
        assertTrue((saved as List<*>).all { it == null || it is String || it is Int })
        val restored = requireNotNull(PocketHorizonNavigationState.Saver.restore(saved))
        assertEquals(PocketHorizonCategory.Artists, restored.category)
        assertEquals("", restored.albumKey)
        assertEquals("artist", restored.artistKey)
        assertEquals("selected-song", restored.selectedSongKey)
        PocketHorizonList.entries.forEach { assertEquals(original.anchor(it), restored.anchor(it)) }
        restored.selectCategory(PocketHorizonCategory.Albums)
        restored.back()
        restored.openAlbum("")
        assertEquals(original.anchor(PocketHorizonList.AlbumTracks), restored.anchor(PocketHorizonList.AlbumTracks))
    }

    @Test
    fun malformedSavedStateAndNegativePositionsUseSafeDefaults() {
        for (saved in listOf("old", emptyList<Any>(), listOf(99, "Albums"), mapOf("category" to "Songs"))) {
            val restored = requireNotNull(PocketHorizonNavigationState.Saver.restore(saved))
            assertEquals(PocketHorizonCategory.NowPlaying, restored.category)
            assertNull(restored.albumKey)
        }
        val restored = requireNotNull(PocketHorizonNavigationState.Saver.restore(
            listOf(1, "Unknown", null, null, null, null, null, "song", -8, -10)))
        assertEquals(PocketHorizonCategory.NowPlaying, restored.category)
        assertEquals(PocketHorizonScrollAnchor("song", 0, 0), restored.anchor(PocketHorizonList.Songs))
    }

    @Test
    fun showingNowPlayingDoesNotDiscardNavigationOrShareAdvanceState() {
        val horizon = PocketHorizonNavigationState()
        val advance = PocketAdvanceNavigationState()
        horizon.openAlbum("horizon-album")
        horizon.selectSong("horizon-song")
        horizon.showNowPlaying()
        assertEquals("horizon-album", horizon.albumKey)
        assertEquals("horizon-song", horizon.selectedSongKey)
        assertEquals(PocketAdvanceLcdMode.NowPlaying, advance.lcdMode)
        assertEquals(PocketAdvanceBrowserPage.Root, advance.browserPage)
        advance.openBrowser()
        advance.confirmSelection()
        assertEquals(PocketHorizonCategory.NowPlaying, horizon.category)
        assertEquals("horizon-album", horizon.albumKey)
    }

    private fun save(state: PocketHorizonNavigationState): Any {
        val scope = object : SaverScope { override fun canBeSaved(value: Any): Boolean = true }
        return requireNotNull(with(PocketHorizonNavigationState.Saver) { scope.save(state) })
    }
}
