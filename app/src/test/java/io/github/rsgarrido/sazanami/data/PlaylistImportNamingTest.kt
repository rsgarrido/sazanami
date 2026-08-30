package io.github.rsgarrido.sazanami.data

import io.github.rsgarrido.sazanami.data.playlistfile.defaultImportedPlaylistName
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistImportNamingTest {
    @Test
    fun defaultImportedPlaylistName_removesM3uExtensionsCaseInsensitively() {
        assertEquals("Road Trip", defaultImportedPlaylistName("Road Trip.M3U8"))
        assertEquals("Favorites", defaultImportedPlaylistName("Favorites.m3u"))
    }

    @Test
    fun defaultImportedPlaylistName_preservesUnicodeAndUsesFallback() {
        assertEquals("旅行 – Café", defaultImportedPlaylistName("旅行 – Café.m3u8"))
        assertEquals("Imported Playlist", defaultImportedPlaylistName(".m3u"))
        assertEquals("Imported Playlist", defaultImportedPlaylistName(null))
    }

    @Test
    fun uniquePlaylistName_usesFirstAvailableCaseInsensitiveSuffix() {
        assertEquals(
            "Road Trip (3)",
            uniquePlaylistName(
                preferredName = "Road Trip",
                existingNames = listOf("road trip", "Road Trip (2)")
            )
        )
    }
}
