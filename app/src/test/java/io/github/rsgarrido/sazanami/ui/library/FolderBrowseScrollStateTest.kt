package io.github.rsgarrido.sazanami.ui.library

import androidx.compose.foundation.lazy.LazyListState
import io.github.rsgarrido.sazanami.data.FolderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderBrowseScrollStateTest {
    @Test
    fun rootParentAndSiblingKeepIndependentPositions() {
        val artist = id("music/artist")
        val album = id("music/artist/album")
        val sibling = id("music/other")
        val holder = FolderBrowseScrollStateHolder { key ->
            when (key) {
                FolderBrowseScrollKey.Root -> LazyListState(40, 12)
                FolderBrowseScrollKey.Folder(artist) -> LazyListState(18, 7)
                FolderBrowseScrollKey.Folder(album) -> LazyListState(6, 3)
                else -> LazyListState(2, 1)
            }
        }

        val rootState = holder.listStateFor(null)
        val artistState = holder.listStateFor(artist)
        val albumState = holder.listStateFor(album)
        val siblingState = holder.listStateFor(sibling)

        assertEquals(40, rootState.firstVisibleItemIndex)
        assertEquals(12, rootState.firstVisibleItemScrollOffset)
        assertEquals(18, artistState.firstVisibleItemIndex)
        assertEquals(7, artistState.firstVisibleItemScrollOffset)
        assertNotSame(rootState, artistState)
        assertNotSame(artistState, albumState)
        assertNotSame(albumState, siblingState)
        assertSame(artistState, holder.listStateFor(artist))
        assertSame(rootState, holder.listStateFor(null))
    }

    @Test
    fun staleFolderStatesAreRemovedWithoutClearingRootOrRetainedFolders() {
        val retained = id("music/retained")
        val removed = id("music/removed")
        val holder = FolderBrowseScrollStateHolder()
        val rootState = holder.listStateFor(null)
        val retainedState = holder.listStateFor(retained)
        val removedState = holder.listStateFor(removed)

        holder.retainFolderIds(setOf(retained))

        assertTrue(holder.hasStateFor(null))
        assertTrue(holder.hasStateFor(retained))
        assertFalse(holder.hasStateFor(removed))
        assertSame(rootState, holder.listStateFor(null))
        assertSame(retainedState, holder.listStateFor(retained))
        assertNotSame(removedState, holder.listStateFor(removed))
    }

    @Test
    fun positionsClampSafelyWhenContentsShrink() {
        assertEquals(
            FolderBrowseScrollPosition(9, 0),
            clampFolderBrowseScrollPosition(
                FolderBrowseScrollPosition(40, 24),
                itemCount = 10
            )
        )
        assertEquals(
            FolderBrowseScrollPosition(4, 24),
            clampFolderBrowseScrollPosition(
                FolderBrowseScrollPosition(4, 24),
                itemCount = 10
            )
        )
        assertEquals(
            FolderBrowseScrollPosition(0, 0),
            clampFolderBrowseScrollPosition(
                FolderBrowseScrollPosition(4, 24),
                itemCount = 0
            )
        )
    }

    private fun id(path: String) = FolderId("external_primary", path)
}
