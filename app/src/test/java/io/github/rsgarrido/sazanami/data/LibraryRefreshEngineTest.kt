package io.github.rsgarrido.sazanami.data

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock

class LibraryRefreshEngineTest {
    @Test
    fun unchangedRowReusesCachedEnrichmentAndPreservesMissingDateAdded() {
        val cached = song(1, modified = 10, size = 20, added = 5, artwork = true)
        val index = song(1, modified = 10, size = 20, added = 0)
        var enrichmentCalls = 0

        val result = LibraryRefreshEngine.refresh(listOf(cached), listOf(index)) {
            enrichmentCalls += 1
            it
        }

        assertEquals(0, enrichmentCalls)
        assertEquals(1, result.reusedCount)
        assertSame(cached.albumArtUri, result.songs.single().albumArtUri)
        assertEquals(5L, result.songs.single().dateAddedEpochSeconds)
    }

    @Test
    fun modifiedAndNewRowsAreEnrichedWhileMissingRowsAreRemoved() {
        val cachedModified = song(1, modified = 10, size = 20)
        val cachedRemoved = song(2, modified = 10, size = 20)
        val modified = song(1, modified = 11, size = 21)
        val added = song(3, title = "Different", path = "Other/", modified = 1, size = 4)
        val enrichedIds = mutableListOf<Long>()

        val result = LibraryRefreshEngine.refresh(
            listOf(cachedModified, cachedRemoved),
            listOf(modified, added)
        ) {
            enrichedIds += it.id
            it
        }

        assertEquals(listOf(1L, 3L), enrichedIds)
        assertEquals(1, result.updatedCount)
        assertEquals(1, result.addedCount)
        assertEquals(1, result.removedCount)
    }

    @Test
    fun uniquelyMovedRowReusesEnrichmentAndUpdatesCurrentSource() {
        val cached = song(1, path = "Old/", modified = 10, size = 20, artwork = true)
        val moved = song(9, path = "New/", modified = 10, size = 20)

        val result = LibraryRefreshEngine.refresh(listOf(cached), listOf(moved)) { it }

        assertEquals(1, result.movedCount)
        assertEquals(1, result.reusedCount)
        assertEquals(9L, result.songs.single().id)
        assertEquals("New/", result.songs.single().relativePath)
        assertSame(cached.albumArtUri, result.songs.single().albumArtUri)
    }

    @Test
    fun failedOrSuspiciousEmptyScanDoesNotWipeValidCache() {
        val cached = listOf(song(1))

        val failed = LibraryRefreshEngine.fallbackForIncompleteScan(cached, null)
        val suspiciousEmpty = LibraryRefreshEngine.fallbackForIncompleteScan(cached, emptyList())

        assertFalse(failed!!.successfulCompleteScan)
        assertEquals(cached, failed.songs)
        assertFalse(suspiciousEmpty!!.successfulCompleteScan)
        assertTrue(LibraryRefreshEngine.fallbackForIncompleteScan(emptyList(), emptyList()) == null)
    }

    @Test
    fun migratedLegacyCachedSongRequestsOneTimeArtworkRepair() {
        val legacy = song(1).copy(artworkEnrichmentVersion = 0)
        var enrichmentCalls = 0

        val result = LibraryRefreshEngine.refresh(listOf(legacy), listOf(song(1))) {
            enrichmentCalls += 1
            it.copy(artworkEnrichmentVersion = CURRENT_ARTWORK_ENRICHMENT_VERSION)
        }

        assertEquals(1, enrichmentCalls)
        assertEquals(1, result.updatedCount)
    }

    @Test
    fun repairedCachedSongDoesNotReenrichOnEveryRefresh() {
        val legacy = song(1).copy(artworkEnrichmentVersion = 0)
        var enrichmentCalls = 0
        val repaired = LibraryRefreshEngine.refresh(listOf(legacy), listOf(song(1))) {
            enrichmentCalls += 1
            it.copy(artworkEnrichmentVersion = CURRENT_ARTWORK_ENRICHMENT_VERSION)
        }.songs.single()

        val second = LibraryRefreshEngine.refresh(listOf(repaired), listOf(song(1))) {
            enrichmentCalls += 1
            it
        }

        assertEquals(1, enrichmentCalls)
        assertEquals(1, second.reusedCount)
        assertEquals(0, second.enrichmentCount)
        assertFalse(second.requiresCacheWrite)
    }

    @Test
    fun secondIdenticalPassPerformsZeroArtworkRepairs() {
        val result = LibraryRefreshEngine.refresh(listOf(song(1)), listOf(song(1))) { it }

        assertEquals(0, result.updatedCount)
        assertEquals(0, result.enrichmentCount)
        assertFalse(result.requiresCacheWrite)
    }

    @Test
    fun trulyArtworklessSongDoesNotRetryEveryLaunch() {
        val artworkless = song(1).copy(albumArtUri = null)
        var enrichmentCalls = 0

        val result = LibraryRefreshEngine.refresh(listOf(artworkless), listOf(song(1))) {
            enrichmentCalls += 1
            it
        }

        assertEquals(0, enrichmentCalls)
        assertEquals(1, result.reusedCount)
    }

    @Test
    fun folderArtworkRemainsSupported() {
        val folderArtwork = mock(Uri::class.java)
        val cached = song(1).copy(albumArtUri = folderArtwork)
        var enrichmentCalls = 0

        val result = LibraryRefreshEngine.refresh(listOf(cached), listOf(song(1))) {
            enrichmentCalls += 1
            it
        }

        assertEquals(0, enrichmentCalls)
        assertSame(folderArtwork, result.songs.single().albumArtUri)
    }

    @Test
    fun embeddedArtworkAndFolderArtworkKeepExistingPrecedence() {
        val embedded = mock(Uri::class.java)
        val folder = mock(Uri::class.java)

        assertSame(embedded, selectArtwork(embedded, folder))
        assertSame(folder, selectArtwork(null, folder))
    }

    @Test
    fun sourceChangeInvalidatesEmbeddedArtworkReference() {
        val cached = song(1, modified = 10, size = 20)
        val changed = song(1, modified = 11, size = 21)
        var enrichmentCalls = 0

        LibraryRefreshEngine.refresh(listOf(cached), listOf(changed)) {
            enrichmentCalls += 1
            it
        }

        assertEquals(1, enrichmentCalls)
    }

    @Test
    fun tagArtworkEditRefreshesEmbeddedArtworkReference() {
        val cached = song(1)
        var enrichmentCalls = 0

        LibraryRefreshEngine.refresh(
            cachedSongs = listOf(cached),
            indexSongs = listOf(song(1)),
            requiresEnrichment = { _, current -> current.id == 1L }
        ) {
            enrichmentCalls += 1
            it
        }

        assertEquals(1, enrichmentCalls)
    }

    @Test
    fun artworkRepairDoesNotTriggerRepeatedLibraryPublication() {
        repairedCachedSongDoesNotReenrichOnEveryRefresh()
    }

    private fun song(
        id: Long,
        title: String = "Title",
        path: String = "Music/",
        modified: Long = 1,
        size: Long = 2,
        added: Long = 3,
        artwork: Boolean = false
    ): Song {
        val uri = mock(Uri::class.java)
        doReturn("content://media/external/audio/$id").`when`(uri).toString()
        return Song(
            id = id,
            title = title,
            artist = "Artist",
            album = "Album",
            trackNumber = 1,
            duration = 180_000L,
            uri = uri,
            filePath = "/storage/$path/track.flac",
            folderPath = "/storage/$path",
            albumArtUri = if (artwork) mock(Uri::class.java) else null,
            volumeName = "external_primary",
            relativePath = path,
            displayName = "track.flac",
            fileSizeBytes = size,
            dateAddedEpochSeconds = added,
            dateModifiedEpochSeconds = modified,
            artworkEnrichmentVersion = CURRENT_ARTWORK_ENRICHMENT_VERSION
        )
    }
}
