package com.example.cdplaya.player.waveform

import android.net.Uri
import com.example.cdplaya.data.Song
import com.example.cdplaya.player.nativeaudio.NativeWaveformDecoder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito.mock

class NativeWaveformAnalyzerTest {
    @Test
    fun nativeSuccess_returnsNativeBarsWithoutCallingFallback() = runBlocking {
        var fallbackCalls = 0
        val analyzer = NativeWaveformAnalyzer(
            nativeDecoder = NativeWaveformDecoder { _, barCount ->
                List(barCount) { index -> if (index == 0) Float.NaN else 1.25f }
            },
            fallbackAnalyzer = WaveformAnalyzer { _, _, _ ->
                fallbackCalls++
                null
            }
        )

        val result = analyzer.analyze(song(), "source-key", 4)

        assertEquals(listOf(0f, 1f, 1f, 1f), result?.amplitudes)
        assertEquals("source-key", result?.sourceKey)
        assertEquals(0, fallbackCalls)
    }

    @Test
    fun nativeFailure_fallsBackToExistingAndroidPathContract() = runBlocking {
        var fallbackCalls = 0
        val analyzer = NativeWaveformAnalyzer(
            nativeDecoder = NativeWaveformDecoder { _, _ -> null },
            fallbackAnalyzer = WaveformAnalyzer { _, sourceKey, barCount ->
                fallbackCalls++
                WaveformData(List(barCount) { 0.5f }, sourceKey)
            }
        )

        val result = analyzer.analyze(song(), "fallback-key", 3)

        assertEquals(listOf(0.5f, 0.5f, 0.5f), result?.amplitudes)
        assertEquals("fallback-key", result?.sourceKey)
        assertEquals(1, fallbackCalls)
    }

    @Test
    fun malformedNativeResult_fallsBackInsteadOfCachingPartialWaveform() = runBlocking {
        val analyzer = NativeWaveformAnalyzer(
            nativeDecoder = NativeWaveformDecoder { _, _ -> listOf(1f, 0.5f) },
            fallbackAnalyzer = WaveformAnalyzer { _, sourceKey, barCount ->
                WaveformData(List(barCount) { 0.25f }, sourceKey)
            }
        )

        val result = analyzer.analyze(song(), "fallback-key", 4)

        assertEquals(List(4) { 0.25f }, result?.amplitudes)
    }

    @Test
    fun invalidBarCount_shortCircuitsBothPaths() = runBlocking {
        var nativeCalls = 0
        var fallbackCalls = 0
        val analyzer = NativeWaveformAnalyzer(
            nativeDecoder = NativeWaveformDecoder { _, _ ->
                nativeCalls++
                listOf(1f)
            },
            fallbackAnalyzer = WaveformAnalyzer { _, _, _ ->
                fallbackCalls++
                null
            }
        )

        assertNull(analyzer.analyze(song(), "key", 0))
        assertEquals(0, nativeCalls)
        assertEquals(0, fallbackCalls)
    }

    private fun song() = Song(
        id = 7L,
        title = "Example",
        artist = "Artist",
        album = "Album",
        trackNumber = 1,
        duration = 180_000L,
        uri = mock(Uri::class.java),
        filePath = "/music/example.flac",
        folderPath = "/music",
        albumArtUri = null
    )
}
