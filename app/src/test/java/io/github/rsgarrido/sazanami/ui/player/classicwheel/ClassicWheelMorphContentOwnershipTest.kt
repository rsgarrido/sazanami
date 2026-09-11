package io.github.rsgarrido.sazanami.ui.player.classicwheel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassicWheelMorphContentOwnershipTest {
    @Test
    fun `static mini owns exact collapsed endpoint`() {
        assertEquals(
            ClassicWheelMiniVisualOwner.MINI,
            classicWheelMiniVisualOwner(
                progress = 0f,
                shellGeometryReady = true,
                sharedGeometryReady = true,
                miniChromeGeometryReady = true,
                ownsNowPlayingContent = true
            )
        )
    }

    @Test
    fun `transition owns immediately above collapsed endpoint when ready`() {
        assertEquals(
            ClassicWheelMiniVisualOwner.TRANSITION,
            classicWheelMiniVisualOwner(
                progress = Float.MIN_VALUE,
                shellGeometryReady = true,
                sharedGeometryReady = true,
                miniChromeGeometryReady = true,
                ownsNowPlayingContent = true
            )
        )
    }

    @Test
    fun `static mini remains visible when transition cannot own content`() {
        assertEquals(
            ClassicWheelMiniVisualOwner.MINI,
            classicWheelMiniVisualOwner(
                progress = .5f,
                shellGeometryReady = false,
                sharedGeometryReady = true,
                miniChromeGeometryReady = true,
                ownsNowPlayingContent = true
            )
        )
        assertEquals(
            ClassicWheelMiniVisualOwner.MINI,
            classicWheelMiniVisualOwner(
                progress = .5f,
                shellGeometryReady = true,
                sharedGeometryReady = true,
                miniChromeGeometryReady = true,
                ownsNowPlayingContent = false
            )
        )
        assertEquals(
            ClassicWheelMiniVisualOwner.MINI,
            classicWheelMiniVisualOwner(
                progress = .01f,
                shellGeometryReady = true,
                sharedGeometryReady = true,
                miniChromeGeometryReady = false,
                ownsNowPlayingContent = true
            )
        )
    }

    @Test
    fun `now playing owns shared morph content`() {
        assertTrue(ClassicWheelMenuScreen.NowPlaying.ownsNowPlayingMorphContent())
    }

    @Test
    fun `every internal menu page rejects now playing morph content`() {
        val internalPages = listOf(
            ClassicWheelMenuScreen.MainMenu,
            ClassicWheelMenuScreen.Songs,
            ClassicWheelMenuScreen.Artists,
            ClassicWheelMenuScreen.ArtistSongs("Artist"),
            ClassicWheelMenuScreen.Albums,
            ClassicWheelMenuScreen.AlbumSongs("album-key", "Album")
        )

        internalPages.forEach { page ->
            assertFalse(page.ownsNowPlayingMorphContent())
        }
    }

    @Test
    fun `returning from songs restores now playing ownership`() {
        val state = ClassicWheelMenuState()

        assertTrue(state.currentScreen.ownsNowPlayingMorphContent())
        state.openMainMenu()
        state.openSongs()
        assertFalse(state.currentScreen.ownsNowPlayingMorphContent())
        state.goBack()
        assertFalse(state.currentScreen.ownsNowPlayingMorphContent())
        state.goBack()
        assertTrue(state.currentScreen.ownsNowPlayingMorphContent())
    }

    @Test
    fun `only now playing allows the display lyrics swipe`() {
        assertTrue(ClassicWheelMenuScreen.NowPlaying.allowsLyricsSwipe())

        val internalPages = listOf(
            ClassicWheelMenuScreen.MainMenu,
            ClassicWheelMenuScreen.Songs,
            ClassicWheelMenuScreen.Artists,
            ClassicWheelMenuScreen.ArtistSongs("Artist"),
            ClassicWheelMenuScreen.Albums,
            ClassicWheelMenuScreen.AlbumSongs("album-key", "Album")
        )
        internalPages.forEach { page ->
            assertFalse(page.allowsLyricsSwipe())
        }
    }
}
