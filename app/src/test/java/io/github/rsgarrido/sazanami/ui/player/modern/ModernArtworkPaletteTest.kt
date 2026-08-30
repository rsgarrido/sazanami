package io.github.rsgarrido.sazanami.ui.player.modern

import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.rsgarrido.sazanami.data.Song
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
    fun albumAccentKeepsColorfulArtworkHueAndOnlyFallsBackWithoutPalette() {
        val fallback = Color.Magenta
        val colorful = ModernArtworkPalette(
            dominant = Color.Red,
            primary = Color.Red,
            secondary = Color.Yellow,
            accent = Color.Green,
            readableForeground = Color.White
        )
        val neutral = colorful.copy(accent = Color(0xFF333333.toInt()))

        val resolvedColorful = resolveModernAlbumAccent(colorful, fallback)
        val sourceHsv = modernArgbToHsv(colorful.accent.toArgb().toUInt().toLong())
        val resolvedHsv = modernArgbToHsv(resolvedColorful.toArgb().toUInt().toLong())

        assertNotEquals(fallback, resolvedColorful)
        assertTrue(kotlin.math.abs(sourceHsv.hue - resolvedHsv.hue) < 2f)
        assertNotEquals(fallback, resolveModernAlbumAccent(neutral, fallback))
        assertEquals(fallback, resolveModernAlbumAccent(null, fallback))
        assertEquals(
            fallback,
            resolveModernAlbumAccent(ModernArtworkPalette.fallback(fallback), fallback)
        )
    }

    @Test
    fun darkSaturatedArtworkProducesBrighterHuePreservingAccent() {
        val source = Color(0xFF280505.toInt())
        val adjusted = adjustModernArtworkDerivedAccent(source)
        val sourceHsv = modernArgbToHsv(source.toArgb().toUInt().toLong())
        val adjustedHsv = modernArgbToHsv(adjusted.toArgb().toUInt().toLong())

        assertTrue(kotlin.math.abs(sourceHsv.hue - adjustedHsv.hue) < 2f)
        assertTrue(adjustedHsv.value >= 0.4f)
        assertTrue(adjustedHsv.saturation >= 0.8f)
    }

    @Test
    fun brightLowSaturationArtworkStaysInItsArtworkColorFamily() {
        val source = Color(0xFFE5F2FA.toInt())
        val adjusted = adjustModernArtworkDerivedAccent(source)
        val sourceHsv = modernArgbToHsv(source.toArgb().toUInt().toLong())
        val adjustedHsv = modernArgbToHsv(adjusted.toArgb().toUInt().toLong())

        assertTrue(kotlin.math.abs(sourceHsv.hue - adjustedHsv.hue) < 2f)
        assertTrue(adjustedHsv.saturation > sourceHsv.saturation)
        assertTrue(adjustedHsv.value in 0.7f..0.82f)
    }

    @Test
    fun veryBrightSaturatedArtworkIsDarkenedWithoutChangingHueFamily() {
        val source = Color(0xFFFFE11A.toInt())
        val adjusted = adjustModernArtworkDerivedAccent(source)
        val sourceHsv = modernArgbToHsv(source.toArgb().toUInt().toLong())
        val adjustedHsv = modernArgbToHsv(adjusted.toArgb().toUInt().toLong())

        assertTrue(kotlin.math.abs(sourceHsv.hue - adjustedHsv.hue) < 2f)
        assertTrue(adjustedHsv.value <= 0.8f)
        assertTrue(adjustedHsv.saturation > 0.7f)
    }

    @Test
    fun lowSaturationMidtoneArtworkReceivesOnlyAModestSaturationLift() {
        val source = Color(0xFF7F8A91.toInt())
        val adjusted = adjustModernArtworkDerivedAccent(source)
        val sourceHsv = modernArgbToHsv(source.toArgb().toUInt().toLong())
        val adjustedHsv = modernArgbToHsv(adjusted.toArgb().toUInt().toLong())

        assertTrue(kotlin.math.abs(sourceHsv.hue - adjustedHsv.hue) < 2f)
        assertTrue(adjustedHsv.saturation > sourceHsv.saturation)
        assertTrue(adjustedHsv.saturation <= 0.24f)
        assertTrue(adjustedHsv.value in 0.5f..0.65f)
    }

    @Test
    fun grayscaleArtworkProducesVisibleDerivedNeutralInsteadOfAppAccent() {
        val fallback = Color.Magenta
        val grayscale = ModernArtworkPalette(
            dominant = Color(0xFF181818.toInt()),
            primary = Color(0xFF2A2A2A.toInt()),
            secondary = Color(0xFF777777.toInt()),
            accent = Color(0xFF2A2A2A.toInt()),
            readableForeground = Color.White
        )
        val resolved = resolveModernAlbumAccent(grayscale, fallback)
        val hsv = modernArgbToHsv(resolved.toArgb().toUInt().toLong())

        assertNotEquals(fallback, resolved)
        assertTrue(hsv.saturation < 0.04f)
        assertTrue(hsv.value >= 0.4f)
    }

    @Test
    fun colorfulMinorityCanBeatMostlyBlackDominantPixelsForAccent() {
        val pixels = IntArray(100) { index ->
            if (index < 88) 0xFF050505.toInt() else 0xFF9A1717.toInt()
        }
        val palette = extractModernArtworkPalette(pixels, Color.Cyan)
        val hsv = modernArgbToHsv(palette.accent.toArgb().toUInt().toLong())

        assertFalse(palette.isFallback)
        assertTrue(hsv.saturation > 0.65f)
        assertTrue(hsv.hue < 20f || hsv.hue > 340f)
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
