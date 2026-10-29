package io.github.rsgarrido.sazanami.ui.player.pocketadvance

import android.net.Uri
import androidx.compose.runtime.saveable.SaverScope
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.resolveAlbumDiscNumbers
import io.github.rsgarrido.sazanami.data.stableUiKey
import io.github.rsgarrido.sazanami.data.trackNumberWithinDisc
import io.github.rsgarrido.sazanami.ui.library.LibraryAlbumGroup
import io.github.rsgarrido.sazanami.ui.library.LibrarySortDirection
import io.github.rsgarrido.sazanami.ui.library.LibrarySortOption
import io.github.rsgarrido.sazanami.ui.library.LibrarySortState
import io.github.rsgarrido.sazanami.ui.library.buildLibraryAlbumDiscSections
import io.github.rsgarrido.sazanami.ui.library.sortedLibraryAlbumGroups
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class PocketAdvanceAlbumsNavigationTest {
    @Test
    fun canonicalFoldersDistinguishIdenticalTitlesAndKeepCompilationAlbumArtist() {
        val groups = albums(listOf(
            song(1, "/music/a", album = "Same", artist = "First", albumArtist = "First"),
            song(2, "/music/b", album = "Same", artist = "Second", albumArtist = "Second"),
            song(3, "/music/compilation", album = "Collection", artist = "Guest A", albumArtist = "Archive Ensemble"),
            song(4, "/music/compilation", album = "Collection", artist = "Guest B", albumArtist = "Archive Ensemble")
        ))
        assertEquals(3, groups.size)
        assertNotEquals(groups.single { it.key == "/music/a" }.key, groups.single { it.key == "/music/b" }.key)
        val compilation = groups.single { it.key == "/music/compilation" }
        assertEquals("Archive Ensemble", compilation.artistText)
        assertEquals(listOf(3L, 4L), compilation.songs.map(Song::id))
    }

    @Test
    fun canonicalMultiDiscMergeUsesAlbumAndArtistEvidenceAndCompleteDiscOrder() {
        val tracks = listOf(
            song(4, "/music/voyage/CD2", " VOYAGE ", track = 2, disc = 2),
            song(2, "/music/voyage/CD1", "Voyage", track = 2, disc = 1),
            song(3, "/music/voyage/CD2", "VOYAGE", track = 1, disc = 2),
            song(1, "/music/voyage/CD1", "Voyage", track = 1, disc = 1)
        )
        val group = albums(tracks).single()
        assertTrue(group.key.startsWith("multi-disc:"))
        assertEquals(listOf(1L, 2L, 3L, 4L), group.songs.map(Song::id))
        val sections = buildLibraryAlbumDiscSections(group.songs)
        assertEquals(listOf(1, 2), sections.map { it.discNumber })
        assertEquals(listOf(listOf(1L, 2L), listOf(3L, 4L)), sections.map { it.songs.map(Song::id) })
        val incompatible = tracks.map { if (it.discNumber == 2) it.copy(albumArtist = "Different") else it }
        assertEquals(2, albums(incompatible).size)
    }

    @Test
    fun missingTagsKeepCanonicalFolderKeysAndUnknownAlbumsDoNotMerge() {
        val tracks = listOf(song(1, "/music/unknown/disc1", "", artist = "", albumArtist = "", disc = 1),
            song(2, "/music/unknown/disc2", " ", artist = "", albumArtist = "", disc = 2))
        val groups = albums(tracks)
        assertEquals(2, groups.size)
        assertEquals(setOf("/music/unknown/disc1", "/music/unknown/disc2"), groups.map { it.key }.toSet())
        assertTrue(groups.all { it.title == "Unknown Album" && it.artistText == "Various Artists" })
    }

    @Test
    fun encodedDiscAndTrackNumbersUseTheSameCanonicalAlbumOrdering() {
        val group = albums(listOf(song(4, "/music/encoded", track = 2_002),
            song(2, "/music/encoded", track = 1_002), song(3, "/music/encoded", track = 2_001),
            song(1, "/music/encoded", track = 1_001))).single()
        assertEquals(listOf(1L, 2L, 3L, 4L), group.songs.map(Song::id))
        assertEquals(listOf(1, 2, 1, 2), group.songs.map(Song::trackNumberWithinDisc))
        val numbers = resolveAlbumDiscNumbers(group.songs)
        assertEquals(listOf(1, 1, 2, 2), group.songs.map { numbers[it.stableUiKey()] })
    }

    @Test
    fun mainLibrarySortChangesPreserveSelectionByCanonicalAlbumKey() {
        val tracks = listOf(song(1, "/a", "Alpha"), song(2, "/b", "Beta"), song(3, "/b", "Beta"))
        val original = albums(tracks)
        val navigation = catalogue(original)
        navigation.moveSelectionDown(original.map { it.key })
        val byCount = albums(tracks, LibrarySortOption.SONG_COUNT, LibrarySortDirection.DESCENDING)
        assertEquals(listOf("/b", "/a"), byCount.map { it.key })
        assertEquals(0, navigation.selectedAlbumIndex(byCount.map { it.key }))
        navigation.reconcileAlbums(byCount, true)
        assertEquals("/b", navigation.selectedAlbumKey)
        val reversed = albums(tracks, LibrarySortOption.TITLE, LibrarySortDirection.DESCENDING)
        assertEquals("/b", reversed.first().key)
        assertEquals("/b", navigation.selectedAlbumKey)
    }

    @Test
    fun recordBackAndStartRetainNestedLocationAndBothViewports() {
        val groups = albums(listOf(song(1, "/a"), song(2, "/b"), song(3, "/b")))
        val navigation = catalogue(groups)
        val album = groups[1]
        navigation.openAlbum(album.key, 1)
        navigation.reconcileAlbums(groups, true)
        navigation.selectAlbumTrack(album.songs[1].stableUiKey(), 1)
        navigation.albumsListState.requestScrollToItem(1, 7)
        navigation.albumTracksListState.requestScrollToItem(1, 13)
        navigation.toggleMode()
        assertEquals(PocketAdvanceLcdMode.NowPlaying, navigation.lcdMode)
        navigation.back()
        assertEquals(PocketAdvanceBrowserPage.AlbumRecord(album.key), navigation.browserPage)
        assertEquals(album.songs[1].stableUiKey(), navigation.selectedAlbumTrackKey)
        assertEquals(13, navigation.albumTracksListState.firstVisibleItemScrollOffset)
        navigation.back()
        assertEquals(PocketAdvanceBrowserPage.AlbumCatalogue, navigation.browserPage)
        assertEquals(album.key, navigation.selectedAlbumKey)
        assertEquals(7, navigation.albumsListState.firstVisibleItemScrollOffset)
        navigation.back()
        assertEquals(PocketAdvanceBrowserPage.Root, navigation.browserPage)
        assertEquals(PocketAdvanceRootEntry.Albums, navigation.selectedRootEntry)
        navigation.activateRootEntry(PocketAdvanceRootEntry.Songs)
        navigation.selectSong("library-song", 4)
        navigation.back()
        navigation.activateRootEntry(PocketAdvanceRootEntry.Artists)
        navigation.artistsCursor.select("artist", 2)
        navigation.back()
        navigation.activateRootEntry(PocketAdvanceRootEntry.Albums)
        navigation.openAlbum(album.key, 1)
        assertEquals("library-song", navigation.selectedSongKey)
        assertEquals("artist", navigation.selectedArtistKey)
        assertEquals(album.songs[1].stableUiKey(), navigation.selectedAlbumTrackKey)
        assertEquals(7, navigation.albumsListState.firstVisibleItemScrollOffset)
        assertEquals(13, navigation.albumTracksListState.firstVisibleItemScrollOffset)
    }

    @Test
    fun openingAnotherAlbumResetsTrackCursorWhileTheSameAlbumRestoresIt() {
        val groups = albums(listOf(song(1, "/a"), song(2, "/a"), song(3, "/b")))
        val navigation = catalogue(groups)
        navigation.openAlbum(groups[0].key, 0)
        navigation.reconcileAlbums(groups, true)
        navigation.selectAlbumTrack(groups[0].songs[1].stableUiKey(), 1)
        navigation.albumTracksListState.requestScrollToItem(1, 9)
        navigation.back()
        navigation.openAlbum(groups[0].key, 0)
        assertEquals(groups[0].songs[1].stableUiKey(), navigation.selectedAlbumTrackKey)
        assertEquals(9, navigation.albumTracksListState.firstVisibleItemScrollOffset)
        navigation.back()
        navigation.openAlbum(groups[1].key, 1)
        navigation.reconcileAlbums(groups, true)
        assertEquals(groups[1].songs.first().stableUiKey(), navigation.selectedAlbumTrackKey)
        assertEquals(0, navigation.albumTracksListState.firstVisibleItemIndex)
        assertEquals(0, navigation.albumTracksListState.firstVisibleItemScrollOffset)
    }

    @Test
    fun insertedReorderedAndMovedTracksResolveTheCurrentAlbumContext() {
        val tracks = listOf(song(1, "/a", track = 1), song(2, "/a", track = 2), song(3, "/a", track = 3), song(4, "/b"))
        val original = albums(tracks)
        val navigation = catalogue(original)
        navigation.openAlbum("/a", 0)
        navigation.reconcileAlbums(original, true)
        navigation.selectAlbumTrack(tracks[1].stableUiKey(), 1)
        val changed = albums(tracks.map { if (it.id == 2L) it.copy(trackNumber = 99) else it } + song(5, "/a", track = 0))
        val current = changed.single { it.key == "/a" }
        navigation.reconcileAlbums(changed, true)
        assertEquals(2, navigation.selectedAlbumTrackIndex(current.songs.map(Song::stableUiKey)))
        val moved = albums(tracks.map { if (it.id == 2L) it.copy(folderPath = "/b", album = "Other") else it })
        navigation.reconcileAlbums(moved, true)
        val remaining = moved.single { it.key == "/a" }
        assertEquals(listOf(1L, 3L), remaining.songs.map(Song::id))
        assertEquals(remaining.songs.last().stableUiKey(), navigation.selectedAlbumTrackKey)
        assertEquals(PocketAdvanceBrowserPage.AlbumRecord("/a"), navigation.browserPage)
        assertTrue(moved.single { it.key == "/b" }.songs.any { it.stableUiKey() == tracks[1].stableUiKey() })
    }

    @Test
    fun transientMissingDataPreservesRecordAndConfirmedRemovalReturnsToCatalogue() {
        val groups = albums(listOf(song(1, "/a"), song(2, "/b"), song(3, "/b")))
        val navigation = catalogue(groups)
        navigation.openAlbum("/b", 1)
        navigation.reconcileAlbums(groups, true)
        val key = groups[1].songs[1].stableUiKey()
        navigation.selectAlbumTrack(key, 1)
        navigation.albumTracksListState.requestScrollToItem(1, 11)
        navigation.reconcileAlbums(emptyList(), false)
        navigation.reconcileAlbums(groups.take(1), false)
        assertEquals(PocketAdvanceBrowserPage.AlbumRecord("/b"), navigation.browserPage)
        assertEquals(key, navigation.selectedAlbumTrackKey)
        assertFalse(navigation.canConfirm(emptyList()))
        assertEquals(11, navigation.albumTracksListState.firstVisibleItemScrollOffset)
        navigation.reconcileAlbums(groups.take(1), true)
        assertEquals(PocketAdvanceBrowserPage.AlbumCatalogue, navigation.browserPage)
        assertEquals("/a", navigation.selectedAlbumKey)
        assertNull(navigation.selectedAlbumTrackKey)
        assertNull(navigation.retainedAlbumKey)
        navigation.reconcileAlbums(emptyList(), true)
        assertNull(navigation.selectedAlbumKey)
        assertEquals(0, navigation.albumsListState.firstVisibleItemIndex)
    }

    @Test
    fun sustainedRepeatsClampAndCapturedPageCannotNavigateAnotherRecord() {
        val groups = albums(listOf(song(1, "/a"), song(2, "/b"), song(3, "/b")))
        val navigation = catalogue(groups)
        val keys = groups.map { it.key }
        val catalogue = PocketAdvanceBrowserPage.AlbumCatalogue
        assertFalse(navigation.repeatBrowseSelection(-1, keys, catalogue))
        assertTrue(navigation.repeatBrowseSelection(1, keys, catalogue))
        assertFalse(navigation.repeatBrowseSelection(1, keys, catalogue))
        navigation.moveSelectionDown(keys)
        assertEquals("/a", navigation.selectedAlbumKey)
        navigation.openAlbum("/b", 1)
        navigation.reconcileAlbums(groups, true)
        assertFalse(navigation.repeatBrowseSelection(1, keys, catalogue))
        val page = PocketAdvanceBrowserPage.AlbumRecord("/b")
        val tracks = groups[1].songs.map(Song::stableUiKey)
        assertTrue(navigation.repeatBrowseSelection(1, tracks, page))
        assertFalse(navigation.repeatBrowseSelection(1, tracks, page))
        navigation.moveSelectionDown(tracks)
        assertEquals(tracks.first(), navigation.selectedAlbumTrackKey)
        navigation.back()
        assertFalse(navigation.repeatBrowseSelection(1, tracks, page))
    }

    @Test
    fun canonicalEmptyFolderAlbumKeySurvivesSavingAndReordering() {
        val groups = albums(listOf(song(1, "", "Alpha"), song(2, "/other", "Zulu")))
        val navigation = catalogue(groups)
        navigation.openAlbum("", 0)
        navigation.reconcileAlbums(groups, true)
        val restored = requireNotNull(PocketAdvanceNavigationState.Saver.restore(save(navigation)))
        assertEquals("", restored.retainedAlbumKey)
        assertEquals("", restored.selectedAlbumKey)
        assertEquals(PocketAdvanceBrowserPage.AlbumRecord(""), restored.browserPage)
        restored.reconcileAlbums(groups.reversed(), true)
        assertEquals(1, restored.selectedAlbumIndex(groups.reversed().map { it.key }))
        assertEquals(groups.first().songs.first().stableUiKey(), restored.selectedAlbumTrackKey)
    }

    @Test
    fun compactAlbumSavingKeepsLegacyFieldsAndStartsSafelyInNowPlaying() {
        val groups = albums(listOf(song(1, "/a"), song(2, "/b"), song(3, "/b")))
        val navigation = catalogue(groups)
        navigation.openAlbum("/b", 1)
        navigation.reconcileAlbums(groups, true)
        navigation.selectAlbumTrack(groups[1].songs[1].stableUiKey(), 1)
        navigation.albumsListState.requestScrollToItem(1, 7)
        navigation.albumTracksListState.requestScrollToItem(1, 13)
        val saved = save(navigation)
        assertEquals(15, saved.size)
        assertEquals("AlbumRecord", saved[1])
        val restored = requireNotNull(PocketAdvanceNavigationState.Saver.restore(saved))
        assertEquals(PocketAdvanceLcdMode.NowPlaying, restored.lcdMode)
        assertEquals(PocketAdvanceBrowserPage.AlbumRecord("/b"), restored.browserPage)
        assertEquals(7, restored.albumsListState.firstVisibleItemScrollOffset)
        assertEquals(13, restored.albumTracksListState.firstVisibleItemScrollOffset)
        assertEquals(navigation.selectedAlbumTrackKey, restored.selectedAlbumTrackKey)
        val legacy = listOf("Artists", "ArtistSongs", "song", 2, 0, 0, 0, 0, "",
            listOf("artist", 0, 0, 0, ""), "artist", listOf("artist-song", 0, 0, 0, ""))
        val old = requireNotNull(PocketAdvanceNavigationState.Saver.restore(legacy))
        assertEquals(PocketAdvanceBrowserPage.ArtistSongs("artist"), old.browserPage)
        assertEquals("artist-song", old.selectedArtistSongKey)
        assertNull(old.retainedAlbumKey)
        assertNull(old.selectedAlbumKey)
    }

    @Test
    fun restoredCatalogueViewportFollowsItsCanonicalKeyWithoutADpadScrollRequest() {
        val groups = albums(listOf(song(1, "/a"), song(2, "/b"), song(3, "/c")))
        val payload = listOf("Albums", "AlbumCatalogue", "", 0, 0, 0, 0, 0, "", emptyList<Any>(), "", emptyList<Any>(),
            listOf("/b", 1, 0, 9, "/a", true, true), null, emptyList<Any>())
        val restored = requireNotNull(PocketAdvanceNavigationState.Saver.restore(payload))
        restored.reconcileAlbums(emptyList(), false)
        restored.reconcileAlbums(groups.reversed(), true)
        assertEquals("/b", restored.selectedAlbumKey)
        assertEquals(2, restored.albumsListState.firstVisibleItemIndex)
        assertEquals(9, restored.albumsListState.firstVisibleItemScrollOffset)
        assertFalse(restored.albumsCursor.takeScrollRequest())
    }

    @Test
    fun offlineArtworkPolicyNeverAdmitsRemoteSchemes() {
        listOf(null, "content", "file", "android.resource", "CONTENT").forEach { assertTrue(isPocketAdvanceArchiveArtworkSchemeLocal(it)) }
        listOf("https", "http", "ftp", "data").forEach { assertFalse(isPocketAdvanceArchiveArtworkSchemeLocal(it)) }
    }

    @Test
    fun archiveGeometryKeepsProminentArtworkAndPracticalListsAtSupportedBudgets() {
        for ((width, height, scale) in listOf(Triple(336f, 216f, 1f), Triple(512f, 320f, 2f), Triple(752f, 524f, 1f))) {
            val layout = pocketAdvanceAlbumArchiveLayout(width, height, scale)
            assertTrue(layout.catalogueArtwork.width >= 110f)
            assertTrue(layout.recordArtwork.width < layout.catalogueArtwork.width)
            assertTrue(layout.recordPanelWidth <= layout.cataloguePanelWidth)
            assertTrue(width - layout.cataloguePanelWidth - layout.gap >= 180f)
            listOf(layout.catalogueArtwork to layout.cataloguePanelWidth, layout.recordArtwork to layout.recordPanelWidth).forEach { (art, panel) ->
                assertTrue(art.left >= 0f && art.right <= panel && art.top >= 0f && art.bottom <= height)
                assertEquals(art.width, art.height, .001f)
            }
        }
        assertEquals(240, PocketAdvanceAlbumOpenDurationMillis)
    }

    private fun catalogue(groups: List<LibraryAlbumGroup>) = PocketAdvanceNavigationState(PocketAdvanceRootEntry.Albums).apply {
        openBrowser(); confirmSelection(); reconcileAlbums(groups, true)
    }
    private fun albums(tracks: List<Song>, option: LibrarySortOption = LibrarySortOption.TITLE,
        direction: LibrarySortDirection = LibrarySortDirection.ASCENDING) = sortedLibraryAlbumGroups(tracks, LibrarySortState(option, direction))
    private fun save(navigation: PocketAdvanceNavigationState): List<*> {
        val scope = object : SaverScope { override fun canBeSaved(value: Any) = true }
        return requireNotNull(with(PocketAdvanceNavigationState.Saver) { scope.save(navigation) }) as List<*>
    }
    private fun song(id: Long, folder: String, album: String = "Album", artist: String = "Artist",
        albumArtist: String = "Album Artist", track: Int = id.toInt(), disc: Int? = null) = Song(
        id, "Track $id", artist, album, track, 180_000L, mock(Uri::class.java), "$folder/$id.flac", folder, null,
        albumArtist = albumArtist, volumeName = "external_primary", discNumber = disc
    )
}
