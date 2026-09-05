package io.github.rsgarrido.sazanami.ui.library

import android.net.Uri
import io.github.rsgarrido.sazanami.controller.LibrarySelectionController
import io.github.rsgarrido.sazanami.data.Playlist
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.membershipKey
import io.github.rsgarrido.sazanami.ui.state.LibrarySelectionEntity
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

class LibrarySearchIndexTest {
    @Test fun `ranking tiers prioritize direct title over related metadata`() {
        val names = listOf("the", "the warning", "meet the warning", "breathe", "unrelated")
        assertEquals(listOf(0, 1, 2, 3, 4), names.map {
            LibrarySearchRanking.score(it, listOf("the warning"), "the")
        })
        assertNull(LibrarySearchRanking.score("unrelated", emptyList(), "the"))
    }

    @Test fun `normalization handles case whitespace and punctuation without changing metadata`() {
        assertEquals("best damn thing", LibrarySearchRanking.normalize("  BEST—Damn...  Thing! "))
        assertEquals("ac dc", LibrarySearchRanking.normalize("AC/DC"))
        val track = song(1, "Best-Damn Thing")
        val index = LibrarySearchIndex(listOf(track), emptyList())
        assertEquals(track, (index.search("  BEST   damn ").ranked.first() as LibrarySearchResult.Track).song)
        assertEquals("Best-Damn Thing", track.title)
    }

    @Test fun `classification preserves four domain entities and category filters`() {
        val track = song(1, "The Warning", "The Warning", "The Warning")
        val playlist = Playlist(9, "The Warning", 1)
        val results = LibrarySearchIndex(listOf(track), listOf(playlist)).search("the warning")
        assertEquals(4, results.inCategory(SearchCategory.ALL).size)
        assertEquals(track, (results.inCategory(SearchCategory.SONGS).single() as LibrarySearchResult.Track).song)
        assertEquals(buildLibraryAlbumGroups(listOf(track)).single(),
            (results.inCategory(SearchCategory.ALBUMS).single() as LibrarySearchResult.Album).album)
        assertEquals(buildLibraryArtistGroups(listOf(track)).single(),
            (results.inCategory(SearchCategory.ARTISTS).single() as LibrarySearchResult.Artist).artist)
        assertEquals(playlist, (results.inCategory(SearchCategory.PLAYLISTS).single() as LibrarySearchResult.PlaylistItem).playlist)
    }

    @Test fun `exact artist outranks title contains and artist metadata songs`() {
        val songs = listOf(song(1, "Meet The Warning"), song(2, "Shattered Heart"))
        val results = LibrarySearchIndex(songs, emptyList()).search("the warning")
        assertTrue(results.ranked.first() is LibrarySearchResult.Artist)
        assertEquals(SearchCategory.ARTISTS, results.sectionOrder.first())
        assertEquals(listOf(1L, 2L), results.inCategory(SearchCategory.SONGS).map { (it as LibrarySearchResult.Track).song.id })
    }

    @Test fun `album word prefix outranks metadata only songs`() {
        val track = song(1, "Girlfriend", "Avril Lavigne", "The Best Damn Thing")
        val results = LibrarySearchIndex(listOf(track), emptyList()).search("best damn")
        assertTrue(results.ranked.first() is LibrarySearchResult.Album)
        assertEquals(listOf(SearchCategory.ALBUMS, SearchCategory.SONGS), results.sectionOrder)
        assertTrue(results.ranked.last() is LibrarySearchResult.Track)
    }

    @Test fun `artist query also finds related albums and songs`() {
        val track = song(1, "Complicated", "Avril Lavigne", "Let Go")
        val results = LibrarySearchIndex(listOf(track), emptyList()).search("Avril")
        assertEquals(setOf(SearchCategory.SONGS, SearchCategory.ALBUMS, SearchCategory.ARTISTS),
            results.ranked.map { it.category }.toSet())
        assertTrue(results.ranked.first() is LibrarySearchResult.Artist)
    }

    @Test fun `empty and unmatched queries have no results`() {
        val index = LibrarySearchIndex(listOf(song(1, "Heart")), emptyList())
        listOf("", "   ", "---", "xyz").forEach { assertTrue(index.search(it).ranked.isEmpty()) }
    }

    @Test fun `ties have deterministic identity order independent of source order`() {
        val songs = listOf(song(3, "Heart"), song(1, "Heart"), song(2, "Heart"))
        assertEquals(LibrarySearchIndex(songs, emptyList()).search("heart"),
            LibrarySearchIndex(songs.reversed(), emptyList()).search("heart"))
    }

    @Test fun `new library snapshot includes changed songs and playlists`() {
        val old = LibrarySearchIndex(listOf(song(1, "Old")), listOf(Playlist(1, "Old", 0)))
        val updated = LibrarySearchIndex(listOf(song(1, "New")), listOf(Playlist(1, "New", 0)))
        assertTrue(old.search("new").ranked.isEmpty())
        assertEquals(2, updated.search("new").ranked.size)
        assertTrue(updated.search("old").ranked.isEmpty())
    }

    @Test fun `playlists are searchable even with an empty song library`() {
        val playlist = Playlist(5, "Road Trip", 0)
        val results = LibrarySearchIndex(emptyList(), listOf(playlist)).search("road")
        assertEquals(listOf(LibrarySearchResult.PlaylistItem(playlist)), results.ranked)
        assertEquals(listOf(SearchCategory.PLAYLISTS), results.sectionOrder)
    }

    @Test fun `searched selection resolves batch in result order and supports toggle and cancel`() {
        val songs = listOf(song(1, "Heart Beat"), song(2, "Heart"), song(3, "My Heart"))
        val ranked = LibrarySearchIndex(songs, emptyList()).search("heart")
            .inCategory(SearchCategory.SONGS).map { (it as LibrarySearchResult.Track).song }
        val controller = LibrarySelectionController()
        controller.enter(LibrarySelectionEntity.SONG, songs[0].membershipKey())
        assertTrue(controller.uiState.value.isActive)
        controller.toggle(LibrarySelectionEntity.SONG, songs[1].membershipKey())
        assertEquals(listOf(2L, 1L), resolveSelectedSongs(controller.uiState.value.selectedKeys, ranked, songs).map { it.id })
        controller.toggle(LibrarySelectionEntity.SONG, songs[0].membershipKey())
        assertEquals(1, controller.uiState.value.selectedCount)
        controller.clear()
        assertFalse(controller.uiState.value.isActive)
        assertTrue(resolveSelectedSongs(controller.uiState.value.selectedKeys, ranked, songs).isEmpty())
    }

    private fun song(id: Long, title: String, artist: String = "The Warning", album: String = "Album"): Song {
        val uri = mock(Uri::class.java)
        doReturn("content://media/external/audio/$id").`when`(uri).toString()
        return Song(id, title, artist, album, 1, 1000, uri, "/music/$id/$id.flac", "/music/$id", null,
            albumArtist = artist)
    }
}
