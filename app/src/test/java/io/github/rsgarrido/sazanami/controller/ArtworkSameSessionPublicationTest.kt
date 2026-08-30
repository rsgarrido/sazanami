package io.github.rsgarrido.sazanami.controller

import android.net.Uri
import io.github.rsgarrido.sazanami.data.EmbeddedArtworkReference
import io.github.rsgarrido.sazanami.data.EmbeddedArtworkSource
import io.github.rsgarrido.sazanami.data.MusicLibraryData
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.hideUnavailableEmbeddedArtwork
import io.github.rsgarrido.sazanami.ui.library.buildLibraryAlbumGroups
import io.github.rsgarrido.sazanami.ui.library.buildLibraryArtistGroups
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock

class ArtworkSameSessionPublicationTest {
    @Test
    fun permissionRevocationAllowsTheSameLibraryToPublishAfterRestore() {
        val tracker = LibraryPublicationTracker()
        val snapshot = library(listOf(song(albumArtUri = null)))

        assertTrue(tracker.shouldPublish(snapshot))
        assertFalse(tracker.shouldPublish(snapshot))

        tracker.reset()

        assertTrue(tracker.shouldPublish(snapshot))
    }

    @Test
    fun repairedArtworkIsPublishedDuringSameSession() {
        val uri = embeddedArtworkUri()
        val cached = song(albumArtUri = uri)
        val initiallyPublished = hideUnavailableEmbeddedArtwork(listOf(cached)) { false }
        val repaired = listOf(cached)
        val tracker = LibraryPublicationTracker()

        assertTrue(tracker.shouldPublish(library(initiallyPublished)))
        assertNull(initiallyPublished.single().albumArtUri)
        assertTrue(tracker.shouldPublish(library(repaired)))
        assertSame(uri, repaired.single().albumArtUri)
    }

    @Test
    fun repairBatchPublishesLibraryExactlyOnce() {
        val tracker = LibraryPublicationTracker()
        val initial = library(listOf(song(albumArtUri = null)))
        val repaired = library(listOf(song(albumArtUri = embeddedArtworkUri())))
        var repairPublications = 0

        tracker.shouldPublish(initial)
        if (tracker.shouldPublish(repaired)) repairPublications += 1
        if (tracker.shouldPublish(repaired)) repairPublications += 1

        assertEquals(1, repairPublications)
    }

    @Test
    fun repairedArtworkUpdatesHomeAlbumAndArtistDerivedCollections() {
        val artwork = embeddedArtworkUri()
        val repairedSongs = listOf(song(albumArtUri = artwork))

        assertSame(artwork, repairedSongs.single().albumArtUri)
        assertSame(artwork, buildLibraryAlbumGroups(repairedSongs).single().songs.single().albumArtUri)
        assertSame(artwork, buildLibraryArtistGroups(repairedSongs).single().songs.single().albumArtUri)
    }

    @Test
    fun progressiveArtworkReplacesSelectedReferencesWithoutTouchingDiscoveryRows() {
        val artwork = embeddedArtworkUri()
        val selected = song(albumArtUri = null)
        val unselected = song(albumArtUri = null).copy(id = 2L, folderPath = "/recordings")
        val enriched = selected.copy(albumArtUri = artwork)

        val updated = replaceSelectedSongReferences(
            referenceSongs = listOf(selected, unselected),
            selectedSongs = listOf(enriched)
        )

        assertSame(artwork, updated.first().albumArtUri)
        assertSame(unselected, updated.last())
    }

    @Test
    fun artworkPublicationDoesNotRestartRepairGeneration() {
        val tracker = LibraryPublicationTracker()
        val repaired = library(listOf(song(albumArtUri = embeddedArtworkUri())))

        assertTrue(tracker.shouldPublish(repaired))
        assertFalse(tracker.shouldPublish(repaired))
    }

    @Test
    fun failedInitialImageBecomesRetryableAfterRepair() {
        val uri = embeddedArtworkUri()
        val hidden = hideUnavailableEmbeddedArtwork(listOf(song(albumArtUri = uri))) { false }

        assertNull(hidden.single().albumArtUri)
        assertSame(uri, song(albumArtUri = uri).albumArtUri)
    }

    @Test
    fun artworkRevisionChangesOnlyWhenArtworkChanges() {
        val sourceUri = mock(Uri::class.java)
        doReturn("content://media/external/audio/media/1").`when`(sourceUri).toString()
        val source = EmbeddedArtworkSource(sourceUri, "track.flac", 10L, 20L)
        val first = EmbeddedArtworkReference(source, "a".repeat(64), "jpg")
        val same = EmbeddedArtworkReference(source, "a".repeat(64), "jpg")
        val changed = EmbeddedArtworkReference(source, "b".repeat(64), "jpg")

        assertEquals(first.fileName, same.fileName)
        assertNotEquals(first.fileName, changed.fileName)
    }

    @Test
    fun cacheClearReconstructionAppearsWithoutActivityResume() {
        repairedArtworkIsPublishedDuringSameSession()
    }

    private fun library(songs: List<Song>) = MusicLibraryData(
        songs = songs,
        libraryFolders = emptyList(),
        referenceSongs = songs
    )

    private fun embeddedArtworkUri(): Uri {
        val uri = mock(Uri::class.java)
        doReturn("content").`when`(uri).scheme
        doReturn("io.github.rsgarrido.sazanami.embeddedartwork").`when`(uri).authority
        return uri
    }

    private fun song(albumArtUri: Uri?) = Song(
        id = 1L,
        title = "Track",
        artist = "Artist",
        album = "Album",
        trackNumber = 1,
        duration = 100L,
        uri = mock(Uri::class.java),
        filePath = "/music/track.flac",
        folderPath = "/music",
        albumArtUri = albumArtUri,
        artworkEnrichmentVersion = 1
    )
}
