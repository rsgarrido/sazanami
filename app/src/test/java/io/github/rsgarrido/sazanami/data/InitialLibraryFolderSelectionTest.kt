package io.github.rsgarrido.sazanami.data

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class InitialLibraryFolderSelectionTest {
    @Test
    fun folderHierarchyAndCountsAreBuiltDirectlyFromLightweightRows() {
        val folders = buildLibraryFolders(
            listOf(
                song(1, "/storage/emulated/0/Music/Artist/Album"),
                song(2, "/storage/emulated/0/Music/Artist/Album"),
                song(3, "/storage/emulated/0/Recordings")
            )
        )

        assertEquals(
            2,
            folders.single { it.path == "/storage/emulated/0/Music/Artist/Album" }.songCount
        )
        assertEquals(
            1,
            folders.single { it.path == "/storage/emulated/0/Recordings" }.songCount
        )
    }

    @Test
    fun conventionalMusicRootIsPreselectedWithoutUnrelatedAudioRoots() {
        val selection = defaultInitialLibraryFolderSelection(
            listOf(
                LibraryFolder("/storage/emulated/0/Music", "Music", 10),
                LibraryFolder("/storage/emulated/0/Download", "Download", 3),
                LibraryFolder("/storage/emulated/0/Recordings", "Recordings", 2)
            )
        )

        assertEquals(FolderSelectionMode.CUSTOM, selection.mode)
        assertEquals(setOf("/storage/emulated/0/Music"), selection.customFolders)
        assertTrue(selection.includes("/storage/emulated/0/Music/Artist/Album"))
        assertFalse(selection.includes("/storage/emulated/0/Download"))
        assertFalse(selection.includes("/storage/emulated/0/Recordings"))
    }

    @Test
    fun conventionalMusicRootsOnMultipleVolumesAreAllPreselected() {
        val selection = defaultInitialLibraryFolderSelection(
            listOf(
                LibraryFolder("/storage/emulated/0/Music", "Music", 10),
                LibraryFolder("/storage/1234-5678/Music", "Music", 5),
                LibraryFolder("/storage/emulated/0/Download", "Download", 3)
            )
        )

        assertEquals(
            setOf("/storage/emulated/0/Music", "/storage/1234-5678/Music"),
            selection.customFolders
        )
    }

    @Test
    fun downloadsAndLocalizedOrCustomRootsRemainAvailableForManualSelection() {
        val available = listOf(
            "/storage/emulated/0/Music",
            "/storage/emulated/0/Download",
            "/storage/emulated/0/Música",
            "/storage/1234-5678/My Collection"
        )
        var selection = FolderSelection(
            FolderSelectionMode.CUSTOM,
            setOf("/storage/emulated/0/Music")
        )

        selection = selection.toggle("/storage/emulated/0/Download", available)
        selection = selection.toggle("/storage/emulated/0/Música", available)
        selection = selection.toggle("/storage/1234-5678/My Collection", available)

        assertTrue(selection.includes("/storage/emulated/0/Download/Album"))
        assertTrue(selection.includes("/storage/emulated/0/Música/Album"))
        assertTrue(selection.includes("/storage/1234-5678/My Collection/Album"))
        assertEquals(4, selection.customFolders.size)
    }

    @Test
    fun initialLibraryContainsSelectedRootAndNeverPublishesUnselectedRoot() {
        val music = song(1, "/storage/emulated/0/Music/Album")
        val download = song(2, "/storage/emulated/0/Download")
        val selection = FolderSelection(
            FolderSelectionMode.CUSTOM,
            setOf("/storage/emulated/0/Music")
        )

        val data = buildInitialSelectedLibraryData(
            discoveredSongs = listOf(music, download),
            refreshedSelectedSongs = listOf(music.copy(title = "Enriched music")),
            selection = selection
        )

        assertEquals(listOf("Enriched music"), data.songs.map(Song::title))
        assertEquals(setOf(1L, 2L), data.referenceSongs.mapTo(mutableSetOf(), Song::id))
        assertTrue(data.songs.none { it.folderPath.contains("Download") })
    }

    @Test
    fun initialLibrarySupportsMultipleSelectedRoots() {
        val selection = FolderSelection(
            FolderSelectionMode.CUSTOM,
            setOf("/storage/emulated/0/Music", "/storage/1234-5678/Audio")
        )
        val data = buildInitialSelectedLibraryData(
            discoveredSongs = listOf(
                song(1, "/storage/emulated/0/Music/Album"),
                song(2, "/storage/1234-5678/Audio/Artist"),
                song(3, "/storage/emulated/0/Recordings")
            ),
            refreshedSelectedSongs = listOf(
                song(1, "/storage/emulated/0/Music/Album"),
                song(2, "/storage/1234-5678/Audio/Artist")
            ),
            selection = selection
        )

        assertEquals(setOf(1L, 2L), data.songs.mapTo(mutableSetOf(), Song::id))
    }

    @Test
    fun lightweightDiscoveryRowsAreReusedForCoreSelectedLibrary() {
        val discoveredMusic = song(1, "/storage/emulated/0/Music/Album")
        val discoveredRecording = song(2, "/storage/emulated/0/Recordings")
        val core = buildInitialSelectedCoreLibrary(
            discoveredSongs = listOf(discoveredMusic, discoveredRecording),
            cachedSongs = emptyList(),
            selection = FolderSelection(
                FolderSelectionMode.CUSTOM,
                setOf("/storage/emulated/0/Music")
            )
        ).libraryData

        assertEquals(listOf(discoveredMusic), core.songs)
        assertEquals(listOf(discoveredMusic, discoveredRecording), core.referenceSongs)
    }

    @Test
    fun completedCustomEmptySelectionProducesAnEmptyLibrary() {
        val data = buildInitialSelectedLibraryData(
            discoveredSongs = listOf(song(1, "/storage/emulated/0/Music")),
            refreshedSelectedSongs = emptyList(),
            selection = FolderSelection(FolderSelectionMode.CUSTOM, emptySet())
        )

        assertTrue(data.songs.isEmpty())
        assertEquals(1, data.referenceSongs.size)
    }

    private fun song(id: Long, folder: String) = Song(
        id = id,
        title = "Song $id",
        artist = "Artist",
        album = "Album",
        trackNumber = 1,
        duration = 1L,
        uri = mock(Uri::class.java),
        filePath = "$folder/song$id.flac",
        folderPath = folder,
        albumArtUri = null,
        volumeName = "external_primary"
    )
}
