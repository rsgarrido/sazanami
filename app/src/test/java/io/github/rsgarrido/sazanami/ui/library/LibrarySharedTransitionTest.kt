package io.github.rsgarrido.sazanami.ui.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LibrarySharedTransitionTest {
    @Test
    fun artworkKeys_matchOnlyTheSameEntityAndIdentity() {
        assertEquals(
            LibrarySharedArtworkKey.Album("collection-42"),
            LibrarySharedArtworkKey.Album("collection-42")
        )
        assertNotEquals(
            LibrarySharedArtworkKey.Album("42"),
            LibrarySharedArtworkKey.Artist("42")
        )
        assertNotEquals(
            LibrarySharedArtworkKey.Artist("42"),
            LibrarySharedArtworkKey.Playlist(42L)
        )
        assertEquals(
            LibrarySharedArtworkKey.Playlist(42L),
            LibrarySharedArtworkKey.Playlist(42L)
        )
        assertNotEquals(
            LibrarySharedArtworkKey.Playlist(42L),
            LibrarySharedArtworkKey.Playlist(43L)
        )
    }
}
