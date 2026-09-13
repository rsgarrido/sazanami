package io.github.rsgarrido.sazanami.player.waveform

import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveformDataTest {
    @Test
    fun cacheKeyVersion_invalidatesPreviousWaveformAlgorithm() {
        val source = source()

        assertEquals(6, WAVEFORM_CACHE_KEY_VERSION)
        assertNotEquals(waveformCacheKeyForVersion(source, 5), waveformCacheKey(source))
    }

    @Test
    fun cacheKey_isStableForTheSameSource() {
        val source = source()

        assertEquals(waveformCacheKey(source), waveformCacheKey(source.copy()))
    }

    @Test
    fun cacheKey_changesWhenFileMetadataChanges() {
        val source = source()

        assertNotEquals(
            waveformCacheKey(source),
            waveformCacheKey(source.copy(lastModified = source.lastModified + 1))
        )
        assertNotEquals(
            waveformCacheKey(source),
            waveformCacheKey(source.copy(fileLength = source.fileLength + 1))
        )
    }

    @Test
    fun normalization_clampsInvalidValuesToUnitRange() {
        val normalized = normalizeWaveformAmplitudes(
            listOf(-2f, Float.NaN, 2f, 4f, Float.POSITIVE_INFINITY)
        )

        assertEquals(listOf(0f, 0f, 0.5f, 1f, 0f), normalized)
        assertTrue(normalized.all { amplitude -> amplitude in 0f..1f })
    }

    @Test
    fun mapping_handlesEmptyAndProducesRequestedBarCount() {
        assertTrue(mapWaveformAmplitudes(emptyList(), 48).isEmpty())
        assertTrue(mapWaveformAmplitudes(listOf(0.5f), 0).isEmpty())

        val mapped = mapWaveformAmplitudes(listOf(0f, 1f, 0f, 1f), 2)

        assertEquals(listOf(0.61f, 0.61f), mapped)
    }

    @Test
    fun mapping_preservesSomeLocalPeakWhenDownsampling() {
        val mapped = mapWaveformAmplitudes(
            amplitudes = listOf(0.2f, 1f, 0.2f, 0.2f),
            barCount = 2
        )

        assertTrue(mapped.first() > 0.6f)
        assertEquals(0.2f, mapped.last(), 0.0001f)
    }

    @Test
    fun mapping_downsamplesHigherResolutionWaveformForExistingSeekbars() {
        val mapped = mapWaveformAmplitudes(
            amplitudes = List(512) { index -> index / 511f },
            barCount = 48
        )

        assertEquals(48, mapped.size)
        assertTrue(mapped.all { amplitude -> amplitude in 0f..1f })
    }

    private fun source() = WaveformSource(
        songId = 42L,
        filePath = "/music/track.flac",
        lastModified = 1_700_000_000L,
        fileLength = 12_345_678L
    )

    private fun waveformCacheKeyForVersion(source: WaveformSource, version: Int): String {
        val identity = buildString {
            append(version)
            append('\u0000')
            append(source.songId)
            append('\u0000')
            append(source.filePath)
            append('\u0000')
            append(source.lastModified)
            append('\u0000')
            append(source.fileLength)
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
