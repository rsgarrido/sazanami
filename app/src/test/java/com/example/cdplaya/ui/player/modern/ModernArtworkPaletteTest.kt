package com.example.cdplaya.ui.player.modern

import android.net.Uri
import androidx.compose.ui.graphics.Color
import com.example.cdplaya.data.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock

class ModernArtworkPaletteTest {
    @Test
    fun extractorReflectsDifferentArtworkColors() {
        val red = extractModernArtworkPalette(
            IntArray(64) { 0xFFE33A32.toInt() },
            Color.Cyan
        )
        val blue = extractModernArtworkPalette(
            IntArray(64) { 0xFF2467D8.toInt() },
            Color.Cyan
        )

        assertFalse(red.isFallback)
        assertFalse(blue.isFallback)
        assertNotEquals(red.dominant, blue.dominant)
        assertNotEquals(red.accent, blue.accent)
        assertTrue(resolveModernAlbumGradient(red, Color.Cyan).usedArtworkPalette)
    }

    @Test
    fun extractorUsesSafeFallbackForEmptyOrTransparentArtwork() {
        val fallback = extractModernArtworkPalette(IntArray(16), Color.Magenta)

        assertTrue(fallback.isFallback)
        assertEquals(
            false,
            resolveModernAlbumGradient(null, Color.Magenta).usedArtworkPalette
        )
    }

    @Test
    fun paletteCacheIsBoundedAndUsesRecentEntries() {
        val cache = BoundedArtworkPaletteCache(maximumSize = 2)
        val palette = ModernArtworkPalette.fallback(Color.Cyan)

        cache.put("one", palette)
        cache.put("two", palette)
        cache.get("one")
        cache.put("three", palette)

        assertEquals(setOf("one", "three"), cache.cachedKeys())
        assertEquals(null, cache.get("two"))
    }

    @Test
    fun failedArtworkKeysAreRememberedUntilAValidPaletteArrives() {
        val cache = BoundedArtworkPaletteCache(maximumSize = 2)
        val palette = ModernArtworkPalette.fallback(Color.Green)

        cache.markFailure("missing")
        assertTrue(cache.hasFailed("missing"))
        cache.put("missing", palette)
        assertFalse(cache.hasFailed("missing"))
        assertEquals(palette, cache.get("missing"))
    }

    @Test
    fun cacheKeyUsesStableArtworkIdentityInsteadOfSongIdentity() {
        val artworkOne = mock(Uri::class.java)
        val artworkTwo = mock(Uri::class.java)
        doReturn("content://artwork/album-7").`when`(artworkOne).toString()
        doReturn("content://artwork/album-7").`when`(artworkTwo).toString()

        assertEquals(
            modernArtworkPaletteCacheKey(song(1, artworkOne)),
            modernArtworkPaletteCacheKey(song(2, artworkTwo))
        )
        assertEquals(null, modernArtworkPaletteCacheKey(song(3, null)))
    }

    @Test
    fun solidBackgroundScrimIncreasesForBrighterColors() {
        assertTrue(
            modernSolidColorReadabilityScrimAlpha(0xFFFFFFFFL) >
                    modernSolidColorReadabilityScrimAlpha(0xFF101010L)
        )
    }

    @Test
    fun albumAccentUsesPaletteAndFallsBackForUnreadableNeutralColors() {
        val fallback = Color.Magenta
        val colorful = ModernArtworkPalette(
            dominant = Color.Red,
            primary = Color.Red,
            secondary = Color.Yellow,
            accent = Color.Green,
            readableForeground = Color.White
        )
        val neutral = colorful.copy(accent = Color(0xFF333333.toInt()))

        assertEquals(colorful.accent, resolveModernAlbumAccent(colorful, fallback))
        assertEquals(fallback, resolveModernAlbumAccent(neutral, fallback))
        assertEquals(fallback, resolveModernAlbumAccent(null, fallback))
    }

    private fun song(id: Long, artwork: Uri?): Song = Song(
        id = id,
        title = "Song $id",
        artist = "Artist",
        album = "Album",
        trackNumber = 1,
        duration = 1_000,
        uri = mock(Uri::class.java),
        filePath = "Music/$id.flac",
        folderPath = "Music",
        albumArtUri = artwork
    )
}
