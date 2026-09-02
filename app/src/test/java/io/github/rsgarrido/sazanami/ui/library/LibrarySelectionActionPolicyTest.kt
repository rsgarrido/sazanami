package io.github.rsgarrido.sazanami.ui.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySelectionActionPolicyTest {
    @Test
    fun `single song More keeps only single item workflows`() {
        assertTrue(isSongSelectionMoreAction("Rate song", "Rate song"))
        assertTrue(isSongSelectionMoreAction("Edit tags", "Rate song"))
        assertTrue(isSongSelectionMoreAction("Pin to Home", "Rate song"))
        assertFalse(isSongSelectionMoreAction("Play next", "Rate song"))
        assertFalse(isSongSelectionMoreAction("Add to favorites", "Rate song"))
    }

    @Test
    fun `single album More excludes batch actions`() {
        assertTrue(isAlbumSelectionMoreAction("Play"))
        assertTrue(isAlbumSelectionMoreAction("Shuffle"))
        assertTrue(isAlbumSelectionMoreAction("Edit album metadata"))
        assertTrue(isAlbumSelectionMoreAction("Unpin from Home"))
        assertFalse(isAlbumSelectionMoreAction("Add to queue"))
        assertFalse(isAlbumSelectionMoreAction("Add to playlist"))
    }
}
