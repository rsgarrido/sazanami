package io.github.rsgarrido.sazanami.data

import android.net.Uri
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock

class SongReferenceResolverTest {
    @Test
    fun exactLocalIdentityWinsBeforeDuplicatePortableMetadata() {
        val first = song(id = 1, uri = "content://media/external/audio/1")
        val second = song(id = 2, uri = "content://media/external/audio/2")

        val result = SongReferenceResolver.resolve(first.toSongReference(), listOf(second, first))

        assertEquals(
            SongReferenceResolution.Resolved(first, SongReferenceMatchType.LOCAL),
            result
        )
    }

    @Test
    fun exactSourcePathResolvesAfterMetadataChanges() {
        val before = song(id = 1)
        val retagged = song(id = 22, title = "New title", artist = "New artist")
        val reference = before.toSongReference().copy(mediaStoreId = null, contentUri = "")

        val result = SongReferenceResolver.resolve(reference, listOf(retagged))

        assertEquals(
            SongReferenceResolution.Resolved(retagged, SongReferenceMatchType.SOURCE),
            result
        )
    }

    @Test
    fun fileSignatureResolvesUniqueMove() {
        val before = song(id = 1, relativePath = "Music/Old/")
        val moved = song(id = 9, relativePath = "Music/New/")
        val reference = before.toSongReference().copy(mediaStoreId = null, contentUri = "")

        val result = SongReferenceResolver.resolve(reference, listOf(moved))

        assertEquals(
            SongReferenceResolution.Resolved(moved, SongReferenceMatchType.FILE_SIGNATURE),
            result
        )
    }

    @Test
    fun portableIdentityResolvesAfterIdPathAndFilenameChange() {
        val before = song(id = 1)
        val restored = song(
            id = 33,
            uri = "content://media/card/audio/33",
            volumeName = "card",
            relativePath = "Restored/",
            displayName = "renamed.flac",
            fileSizeBytes = 99L
        )
        val reference = before.toSongReference().copy(mediaStoreId = null, contentUri = "")

        val result = SongReferenceResolver.resolve(reference, listOf(restored))

        assertEquals(
            SongReferenceResolution.Resolved(restored, SongReferenceMatchType.PORTABLE),
            result
        )
    }

    @Test
    fun legacyOnlyReferenceStillResolves() {
        val current = song(id = 7)
        val reference = SongReference(legacyStableKey = current.stableKey())

        val result = SongReferenceResolver.resolve(reference, listOf(current))

        assertEquals(
            SongReferenceResolution.Resolved(current, SongReferenceMatchType.LEGACY),
            result
        )
    }

    @Test
    fun duplicatePortableMetadataIsAmbiguousAndDeterministic() {
        val first = song(id = 2, relativePath = "B/")
        val second = song(id = 1, relativePath = "A/")
        val reference = SongReference(
            title = first.title,
            artist = first.artist,
            album = first.album,
            duration = first.duration,
            portableKey = first.songIdentity().portableKey.orEmpty()
        )

        val result = SongReferenceResolver.resolve(reference, listOf(first, second))

        assertTrue(result is SongReferenceResolution.Ambiguous)
        result as SongReferenceResolution.Ambiguous
        assertEquals(SongReferenceMatchType.PORTABLE, result.matchType)
        assertEquals(listOf(1L, 2L), result.candidates.map { it.id })
    }

    @Test
    fun blankUnknownMetadataDoesNotCreateUniversalPortableIdentity() {
        assertNull(portableMetadataKey("", "Unknown Artist", "Unknown Album", 120_000L))
        assertEquals(
            SongReferenceResolution.NotFound,
            SongReferenceResolver.resolve(
                SongReference(duration = 120_000L),
                listOf(song(id = 1, title = "", artist = "", album = ""))
            )
        )
    }

    @Test
    fun normalizationDoesNotDependOnDefaultLocale() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val turkish = portableMetadataKey("  INDIGO  SKY ", "ARTIST", "ALBUM", 100L)
            Locale.setDefault(Locale.US)
            val english = portableMetadataKey("indigo sky", "artist", "album", 100L)
            assertEquals(english, turkish)
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    private fun song(
        id: Long,
        uri: String = "content://media/external/audio/$id",
        title: String = "Title",
        artist: String = "Artist",
        album: String = "Album",
        volumeName: String = "external_primary",
        relativePath: String = "Music/Album/",
        displayName: String = "track.flac",
        fileSizeBytes: Long = 12_000L
    ): Song {
        val mockedUri = mock(Uri::class.java)
        doReturn(uri).`when`(mockedUri).toString()
        return Song(
            id = id,
            title = title,
            artist = artist,
            album = album,
            trackNumber = 1,
            duration = 180_000L,
            uri = mockedUri,
            filePath = "/storage/$relativePath$displayName",
            folderPath = "/storage/${relativePath.trimEnd('/')}",
            albumArtUri = null,
            volumeName = volumeName,
            relativePath = relativePath,
            displayName = displayName,
            fileSizeBytes = fileSizeBytes,
            dateModifiedEpochSeconds = 1_700_000_000L
        )
    }
}
