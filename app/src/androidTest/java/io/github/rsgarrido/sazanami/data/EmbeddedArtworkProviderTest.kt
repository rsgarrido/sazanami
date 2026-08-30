package io.github.rsgarrido.sazanami.data

import android.content.pm.ProviderInfo
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileNotFoundException

@RunWith(AndroidJUnit4::class)
class EmbeddedArtworkProviderTest {
    @Test
    fun unchangedSongWithValidEmbeddedArtworkIsReused() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val song = song()
        val source = requireNotNull(EmbeddedArtworkContract.sourceFor(song))
        val reference = EmbeddedArtworkReference(source, "a".repeat(64), "jpg")
        val artworkUri = EmbeddedArtworkContract.buildUri(context.packageName, reference)
        var enrichmentCalls = 0

        val result = LibraryRefreshEngine.refresh(
            cachedSongs = listOf(song.copy(albumArtUri = artworkUri)),
            indexSongs = listOf(song)
        ) {
            enrichmentCalls += 1
            it
        }

        assertEquals(0, enrichmentCalls)
        assertEquals(1, result.reusedCount)
    }

    @Test
    fun embeddedArtworkProviderOpensCurrentContentUriSource() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = requireNotNull(EmbeddedArtworkContract.sourceFor(song()))
        val reference = EmbeddedArtworkReference(source, "b".repeat(64), "jpg")
        val uri = EmbeddedArtworkContract.buildUri(context.packageName, reference)
        val imageFile = File(context.cacheDir, "provider-test-${System.nanoTime()}.jpg")
        imageFile.writeBytes(byteArrayOf(1, 2, 3, 4))
        val provider = RecordingEmbeddedArtworkProvider(imageFile)
        provider.attachInfo(context, ProviderInfo().apply {
            authority = "${context.packageName}.embeddedartwork"
            exported = true
        })

        try {
            provider.openFile(uri, "r").use { descriptor ->
                assertEquals(4L, descriptor.statSize)
            }
            assertEquals(source.uri, provider.openedReference?.source?.uri)
        } finally {
            imageFile.delete()
        }
    }

    @Test
    fun malformedEmbeddedArtworkFallsBackWithoutCrash() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val provider = RecordingEmbeddedArtworkProvider(File(context.cacheDir, "unused"))
        provider.attachInfo(context, ProviderInfo().apply {
            authority = "${context.packageName}.embeddedartwork"
            exported = true
        })

        var rejectedAsMissing = false
        try {
            provider.openFile(
                Uri.parse("content://${context.packageName}.embeddedartwork/not-a-valid-reference"),
                "r"
            )
        } catch (_: FileNotFoundException) {
            rejectedAsMissing = true
        }

        assertTrue(rejectedAsMissing)
    }

    @Test
    fun sourceChangeInvalidatesEmbeddedArtworkReferenceContract() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val original = song()
        val source = requireNotNull(EmbeddedArtworkContract.sourceFor(original))
        val artworkUri = EmbeddedArtworkContract.buildUri(
            context.packageName,
            EmbeddedArtworkReference(source, "c".repeat(64), "png")
        )

        assertTrue(EmbeddedArtworkContract.isCurrentReferenceFor(artworkUri, original))
        assertFalse(
            EmbeddedArtworkContract.isCurrentReferenceFor(
                artworkUri,
                original.copy(dateModifiedEpochSeconds = 11L)
            )
        )
    }

    @Test
    fun cacheClearReconstructionAppearsWithoutActivityResume() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val uniqueId = System.nanoTime()
        val cachedSong = song().copy(
            id = uniqueId,
            uri = Uri.parse("content://media/external_primary/audio/media/$uniqueId"),
            dateModifiedEpochSeconds = uniqueId
        )
        val source = requireNotNull(EmbeddedArtworkContract.sourceFor(cachedSong))
        val reference = EmbeddedArtworkReference(source, "d".repeat(64), "jpg")
        val artworkUri = EmbeddedArtworkContract.buildUri(context.packageName, reference)
        val songWithReference = cachedSong.copy(albumArtUri = artworkUri)
        val artworkFile = File(
            File(context.cacheDir, "embedded_album_art"),
            reference.fileName
        )
        artworkFile.delete()

        try {
            val repository = MusicRepository(context)
            assertNull(
                repository.prepareCachedSongsForPublication(listOf(songWithReference))
                    .single()
                    .albumArtUri
            )

            artworkFile.parentFile?.mkdirs()
            artworkFile.writeBytes(byteArrayOf(1, 2, 3))

            assertEquals(
                artworkUri,
                repository.prepareCachedSongsForPublication(listOf(songWithReference))
                    .single()
                    .albumArtUri
            )
        } finally {
            artworkFile.delete()
        }
    }

    private fun song() = Song(
        id = 41L,
        title = "Track",
        artist = "Artist",
        album = "Album",
        trackNumber = 1,
        duration = 100L,
        uri = Uri.parse("content://media/external_primary/audio/media/41"),
        filePath = "/storage/emulated/0/Music/Track.flac",
        folderPath = "/storage/emulated/0/Music",
        albumArtUri = null,
        volumeName = "external_primary",
        displayName = "Track.flac",
        relativePath = "Music/",
        fileSizeBytes = 20L,
        dateModifiedEpochSeconds = 10L,
        artworkEnrichmentVersion = CURRENT_ARTWORK_ENRICHMENT_VERSION
    )

    private class RecordingEmbeddedArtworkProvider(
        private val imageFile: File
    ) : EmbeddedArtworkProvider() {
        var openedReference: EmbeddedArtworkReference? = null

        override fun resolveArtworkFile(reference: EmbeddedArtworkReference): File? {
            openedReference = reference
            return imageFile
        }
    }
}
