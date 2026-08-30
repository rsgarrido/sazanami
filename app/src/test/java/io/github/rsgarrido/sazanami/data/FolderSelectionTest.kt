package io.github.rsgarrido.sazanami.data

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class FolderSelectionTest {
    private val discovered = setOf(
        "Music",
        "Music/Albums",
        "Music/Live",
        "Podcasts"
    )

    @Test
    fun freshAndLegacyEmptySelectionsResolveToAll() {
        assertEquals(FolderSelectionMode.ALL, FolderSelection.All.mode)
        assertEquals(
            FolderSelectionMode.ALL,
            FolderSelection.fromStored(null, emptySet()).mode
        )
    }

    @Test
    fun legacyNonEmptySelectionResolvesToCustom() {
        val selection = FolderSelection.fromStored(null, setOf("Music/Live"))

        assertEquals(FolderSelectionMode.CUSTOM, selection.mode)
        assertEquals(setOf("Music/Live"), selection.customFolders)
        assertTrue(selection.excludedFolders.isEmpty())
    }

    @Test
    fun allRendersEveryDiscoveredFolderEnabled() {
        val enabled = FolderSelection.All.effectiveFolders(discovered)

        assertEquals(discovered, enabled)
        assertTrue(discovered.all(FolderSelection.All::includes))
    }

    @Test
    fun togglingOneFolderFromAllCreatesPersistentExclusion() {
        val selection = FolderSelection.All.toggle("Music/Live", discovered)

        assertEquals(FolderSelectionMode.ALL, selection.mode)
        assertEquals(setOf("Music/Live"), selection.excludedFolders)
        assertFalse(selection.includes("Music/Live"))
        assertTrue(selection.includes("Music/Albums"))
    }

    @Test
    fun selectingParentIncludesCurrentAndFutureDescendants() {
        val selection = FolderSelection(
            FolderSelectionMode.CUSTOM,
            setOf("Music")
        )

        assertTrue(selection.includes("Music/Albums"))
        assertTrue(selection.includes("Music/New Artist/New Album"))
        assertFalse(selection.includes("Podcasts"))
    }

    @Test
    fun descendantExclusionOverridesSelectedParentWithoutLosingFutureSiblings() {
        val selection = FolderSelection(
            mode = FolderSelectionMode.CUSTOM,
            customFolders = setOf("Music")
        ).toggle("Music/Live", discovered)

        assertTrue(selection.includes("Music/Albums"))
        assertTrue(selection.includes("Music/New Artist/New Album"))
        assertFalse(selection.includes("Music/Live"))
        assertFalse(selection.includes("Music/Live/Bootleg"))
        assertEquals(
            FolderSelectionState.PARTIAL,
            selection.stateFor("Music", discovered)
        )
    }

    @Test
    fun selectingPartialParentClearsDescendantExclusions() {
        val partial = FolderSelection(
            mode = FolderSelectionMode.CUSTOM,
            customFolders = setOf("Music"),
            excludedFolders = setOf("Music/Live")
        )

        val selected = partial.toggle("Music", discovered)

        assertTrue(selected.excludedFolders.isEmpty())
        assertTrue(selected.includes("Music/Live"))
        assertEquals(
            FolderSelectionState.SELECTED,
            selected.stateFor("Music", discovered)
        )
    }

    @Test
    fun storedRulesRoundTripIncludesAndExclusions() {
        val original = FolderSelection(
            mode = FolderSelectionMode.CUSTOM,
            customFolders = setOf("Music"),
            excludedFolders = setOf("Music/Podcasts")
        )

        val restored = FolderSelection.fromStored(
            storedMode = original.mode.name,
            storedFolders = original.toStoredFolders()
        )

        assertEquals(original, restored)
    }

    @Test
    fun customEmptySelectionShowsNoSongsAndIsNotAll() {
        val selection = FolderSelection(FolderSelectionMode.CUSTOM, emptySet())
        val data = buildMusicLibraryData(
            allSongs = listOf(song("Music/Albums"), song("Podcasts")),
            folderSelection = selection
        )

        assertEquals(FolderSelectionMode.CUSTOM, selection.mode)
        assertTrue(data.songs.isEmpty())
    }

    @Test
    fun folderDiscoveryBuildsParentTreeWithRecursiveCounts() {
        val folders = buildLibraryFolders(
            listOf(
                song(
                    folder = "/storage/emulated/0/Music/Artist/Album A",
                    relativePath = "Music/Artist/Album A/"
                ),
                song(
                    folder = "/storage/emulated/0/Music/Artist/Album B",
                    relativePath = "Music/Artist/Album B/"
                ),
                song(
                    folder = "/storage/emulated/0/Music/Artist/Album B",
                    relativePath = "Music/Artist/Album B/"
                )
            )
        )

        assertEquals(listOf("Music", "Artist", "Album A", "Album B"), folders.map { it.name })
        assertEquals(3, folders.first { it.name == "Music" }.songCount)
        assertEquals(3, folders.first { it.name == "Artist" }.songCount)
        assertEquals(2, folders.first { it.name == "Album B" }.directSongCount)
        assertTrue(folders.first { it.name == "Music" }.hasChildren)
    }

    private fun song(
        folder: String,
        relativePath: String = folder
    ) = Song(
        id = (folder + relativePath + System.identityHashCode(relativePath)).hashCode().toLong(),
        title = "Song",
        artist = "Artist",
        album = "Album",
        trackNumber = 1,
        duration = 1L,
        uri = mock(Uri::class.java),
        filePath = "$folder/song.flac",
        folderPath = folder,
        albumArtUri = null,
        relativePath = relativePath
    )
}
