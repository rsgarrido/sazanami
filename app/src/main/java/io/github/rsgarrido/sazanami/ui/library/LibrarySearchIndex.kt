package io.github.rsgarrido.sazanami.ui.library

import io.github.rsgarrido.sazanami.data.Playlist
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.membershipKey
import java.util.Locale

enum class SearchCategory(val label: String) {
    ALL("All"), SONGS("Songs"), ALBUMS("Albums"), ARTISTS("Artists"), PLAYLISTS("Playlists")
}

/** Search normalization never changes domain metadata or entity identity. */
object LibrarySearchRanking {
    private val separators = Regex("[^\\p{L}\\p{N}]+")
    fun normalize(text: String): String = text.lowercase(Locale.ROOT)
        .replace(separators, " ").trim()

    /** Inputs are normalized once by the snapshot, and once per query. Lower is better. */
    fun score(name: String, metadata: List<String>, query: String): Int? = when {
        query.isEmpty() -> null
        name == query -> 0
        name.startsWith(query) -> 1
        name.contains(" $query") -> 2
        name.contains(query) -> 3
        metadata.any { it.contains(query) } -> 4
        else -> null
    }
}

sealed interface LibrarySearchResult {
    val category: SearchCategory
    val key: String
    val title: String
    data class Track(val song: Song) : LibrarySearchResult {
        override val category = SearchCategory.SONGS
        override val key = song.membershipKey()
        override val title = song.title
    }
    data class Album(val album: LibraryAlbumGroup) : LibrarySearchResult {
        override val category = SearchCategory.ALBUMS
        override val key = album.key
        override val title = album.title
    }
    data class Artist(val artist: LibraryArtistGroup) : LibrarySearchResult {
        override val category = SearchCategory.ARTISTS
        override val key = artist.key
        override val title = artist.name
    }
    data class PlaylistItem(val playlist: Playlist) : LibrarySearchResult {
        override val category = SearchCategory.PLAYLISTS
        override val key = playlist.playlistId.toString()
        override val title = playlist.name
    }
}

data class LibrarySearchResults(val ranked: List<LibrarySearchResult> = emptyList()) {
    /** Put the category with the strongest hit first, retaining distinct grouped sections. */
    val sectionOrder: List<SearchCategory> get() = ranked.map { it.category }.distinct()
    fun inCategory(category: SearchCategory): List<LibrarySearchResult> =
        if (category == SearchCategory.ALL) ranked else ranked.filter { it.category == category }
}

/** Immutable normalized snapshot rebuilt only when library or playlist state changes. */
class LibrarySearchIndex(songs: List<Song>, playlists: List<Playlist>) {
    val albums = buildLibraryAlbumGroups(songs)
    private data class Entry(val result: LibrarySearchResult, val name: String, val metadata: List<String>)
    private val entries = buildList {
        fun addResult(result: LibrarySearchResult, metadata: List<String> = emptyList()) {
            add(Entry(result, LibrarySearchRanking.normalize(result.title), metadata.map(LibrarySearchRanking::normalize)))
        }
        songs.distinctBy(Song::membershipKey).forEach {
            addResult(LibrarySearchResult.Track(it), listOf(it.artist, it.album, it.albumArtist))
        }
        albums.forEach { album ->
            addResult(LibrarySearchResult.Album(album),
                (listOf(album.artistText) + album.songs.flatMap { listOf(it.artist, it.albumArtist) }).distinct())
        }
        buildLibraryArtistGroups(songs).forEach { addResult(LibrarySearchResult.Artist(it)) }
        playlists.forEach { addResult(LibrarySearchResult.PlaylistItem(it)) }
    }

    fun search(query: String): LibrarySearchResults {
        val normalized = LibrarySearchRanking.normalize(query)
        if (normalized.isEmpty()) return LibrarySearchResults()
        return LibrarySearchResults(entries.mapNotNull { entry ->
            LibrarySearchRanking.score(entry.name, entry.metadata, normalized)?.let { it to entry }
        }.sortedWith(compareBy<Pair<Int, Entry>> { it.first }
            .thenBy { it.second.name }.thenBy { it.second.result.category.ordinal }
            .thenBy { it.second.result.key }).map { it.second.result })
    }
}
