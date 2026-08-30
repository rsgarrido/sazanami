package io.github.rsgarrido.sazanami.data

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.Mockito.mock

class LightweightLibraryIndexTest {
    @Test
    fun lightweightRowsNeverInvokePerFileMetadataOrArtworkEnrichmentEvenForWav() {
        val row = song("track.wav")
        var fileEnrichmentCalls = 0

        val result = row.withIndexRowEnrichment(
            mode = LibraryIndexMode.LIGHTWEIGHT,
            isWav = true
        ) {
            fileEnrichmentCalls += 1
            it.copy(title = "Embedded title")
        }

        assertSame(row, result)
        assertEquals(0, fileEnrichmentCalls)
    }

    @Test
    fun fullIndexModeRetainsIntentionalWavMetadataSupport() {
        val row = song("track.wav")
        var fileEnrichmentCalls = 0

        val result = row.withIndexRowEnrichment(
            mode = LibraryIndexMode.WITH_WAV_METADATA,
            isWav = true
        ) {
            fileEnrichmentCalls += 1
            it.copy(title = "Embedded title")
        }

        assertEquals("Embedded title", result.title)
        assertEquals(1, fileEnrichmentCalls)
    }

    private fun song(displayName: String) = Song(
        id = 1,
        title = "MediaStore title",
        artist = "Artist",
        album = "Album",
        trackNumber = 1,
        duration = 100,
        uri = mock(Uri::class.java),
        filePath = "/storage/emulated/0/Music/$displayName",
        folderPath = "/storage/emulated/0/Music",
        albumArtUri = null,
        displayName = displayName
    )
}
