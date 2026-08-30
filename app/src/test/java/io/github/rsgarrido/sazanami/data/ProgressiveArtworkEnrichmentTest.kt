package io.github.rsgarrido.sazanami.data

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock

class ProgressiveArtworkEnrichmentTest {
    @Test
    fun sharedAlbumArtworkUsesOneRepresentativeProbe() {
        val artwork = uri("content://artwork/album")
        var probes = 0
        val songs = listOf(song(1), song(2), song(3))
        val enriched = enricher(
            embedded = {
                probes += 1
                artwork
            }
        ).batches(songs).last()

        assertEquals(1, probes)
        assertTrue(enriched.all { it.albumArtUri === artwork })
    }

    @Test
    fun nearbyFolderArtworkRemainsTheFallbackAfterEmbeddedArtwork() {
        val folderArtwork = uri("content://folder/cover")
        var embeddedProbes = 0
        var folderProbes = 0
        val enriched = ProgressiveArtworkEnricher(
            cache = ArtworkResolutionCache(),
            resolveEmbedded = {
                embeddedProbes += 1
                null
            },
            resolveFolder = {
                folderProbes += 1
                folderArtwork
            },
            groupsPerBatch = 1
        ).batches(listOf(song(1), song(2))).last()

        assertEquals(1, embeddedProbes)
        assertEquals(1, folderProbes)
        assertTrue(enriched.all { it.albumArtUri === folderArtwork })
    }

    @Test
    fun successfulResolutionIsReusedFromBoundedStateCache() {
        val artwork = uri("content://artwork/album")
        val cache = ArtworkResolutionCache()
        var probes = 0
        val enricher = enricher(cache) {
            probes += 1
            artwork
        }

        enricher.batches(listOf(song(1), song(2))).last()
        enricher.batches(listOf(song(1), song(2))).last()

        assertEquals(1, probes)
    }

    @Test
    fun safeMissingResolutionIsReused() {
        val cache = ArtworkResolutionCache()
        var probes = 0
        val enricher = enricher(cache) {
            probes += 1
            null
        }

        val first = enricher.batches(listOf(song(1), song(2))).last()
        val second = enricher.batches(listOf(song(1), song(2))).last()

        assertEquals(1, probes)
        assertTrue(first.all { it.albumArtUri == null })
        assertTrue(second.all { it.albumArtUri == null })
    }

    @Test
    fun changedRepresentativeMediaInvalidatesCachedArtworkState() {
        val cache = ArtworkResolutionCache()
        var probes = 0
        val enricher = enricher(cache) {
            probes += 1
            uri("content://artwork/$probes")
        }

        val first = enricher.batches(listOf(song(1, modified = 10))).last().single()
        val changed = enricher.batches(listOf(song(1, modified = 11))).last().single()

        assertEquals(2, probes)
        assertNotSame(first.albumArtUri, changed.albumArtUri)
    }

    @Test
    fun unselectedRootsNeverScheduleArtworkWork() {
        val selection = FolderSelection(FolderSelectionMode.CUSTOM, setOf("/Music"))
        val core = buildInitialSelectedCoreLibrary(
            discoveredSongs = listOf(song(1, folder = "/Music"), song(2, folder = "/Recordings")),
            cachedSongs = emptyList(),
            selection = selection
        ).libraryData
        val probedIds = mutableListOf<Long>()

        enricher(embedded = { song ->
            probedIds += song.id
            null
        }).batches(core.songs).last()

        assertEquals(listOf(1L), probedIds)
    }

    @Test
    fun metadataOnlyCorePublicationLeavesSafeArtworkPlaceholders() {
        val core = buildInitialSelectedCoreLibrary(
            discoveredSongs = listOf(song(1)),
            cachedSongs = emptyList(),
            selection = FolderSelection(FolderSelectionMode.CUSTOM, setOf("/Music"))
        ).libraryData

        assertNull(core.songs.single().albumArtUri)
        assertEquals(0, core.songs.single().artworkEnrichmentVersion)
    }

    @Test
    fun trackSpecificResolutionRemainsAvailableForUniqueCovers() {
        val firstArtwork = uri("content://artwork/track-1")
        val secondArtwork = uri("content://artwork/track-2")
        var probes = 0
        val enricher = enricher(embedded = { song ->
            probes += 1
            if (song.id == 1L) firstArtwork else secondArtwork
        })

        val first = enricher.resolveTrackSpecific(song(1))
        val second = enricher.resolveTrackSpecific(song(2))

        assertEquals(2, probes)
        assertSame(firstArtwork, first.albumArtUri)
        assertSame(secondArtwork, second.albumArtUri)
    }

    @Test
    fun resultCacheIsBoundedAndStoresUrisRatherThanBitmaps() {
        val cache = ArtworkResolutionCache(maximumEntries = 2)
        repeat(3) { index ->
            cache.resolve(
                ArtworkResolutionKey.Track(
                    ArtworkMediaFingerprint("content://media/$index", index.toLong(), 1)
                )
            ) { uri("content://artwork/$index") }
        }

        assertEquals(2, cache.size())
    }

    private fun enricher(
        cache: ArtworkResolutionCache = ArtworkResolutionCache(),
        embedded: (Song) -> Uri? = { null }
    ) = ProgressiveArtworkEnricher(
        cache = cache,
        resolveEmbedded = embedded,
        resolveFolder = { null },
        groupsPerBatch = 1
    )

    private fun song(
        id: Long,
        folder: String = "/Music/Album",
        modified: Long = 10
    ) = Song(
        id = id,
        title = "Track $id",
        artist = "Artist",
        album = "Album",
        trackNumber = id.toInt(),
        duration = 100,
        uri = uri("content://media/external/audio/$id"),
        filePath = "$folder/track$id.flac",
        folderPath = folder,
        albumArtUri = null,
        volumeName = "external_primary",
        displayName = "track$id.flac",
        fileSizeBytes = 20,
        dateModifiedEpochSeconds = modified
    )

    private fun uri(value: String): Uri = mock(Uri::class.java).also { mocked ->
        doReturn(value).`when`(mocked).toString()
    }
}
