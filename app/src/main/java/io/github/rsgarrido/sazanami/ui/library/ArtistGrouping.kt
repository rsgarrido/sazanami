package io.github.rsgarrido.sazanami.ui.library

import io.github.rsgarrido.sazanami.data.ArtistIdentity
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.UNKNOWN_ARTIST_DISPLAY_NAME
import io.github.rsgarrido.sazanami.data.artistIdentity
import io.github.rsgarrido.sazanami.ui.sortSongsForArtistDetail

data class LibraryArtistGroup(
    val identity: ArtistIdentity,
    val name: String,
    val songs: List<Song>
) {
    val key: String get() = identity.key
}

fun buildLibraryArtistGroups(songs: List<Song>): List<LibraryArtistGroup> {
    return songs
        .groupBy { song -> artistIdentity(song.artist) }
        .map { (identity, artistSongs) ->
            LibraryArtistGroup(
                identity = identity,
                name = artistDisplayName(identity, artistSongs),
                songs = sortSongsForArtistDetail(artistSongs)
            )
        }
}

private fun artistDisplayName(identity: ArtistIdentity, songs: List<Song>): String {
    if (identity.isUnknown) return UNKNOWN_ARTIST_DISPLAY_NAME
    return songs.asSequence()
        .map { it.artist.trim().replace(Regex("\\s+"), " ") }
        .filter(String::isNotEmpty)
        .sortedWith(compareBy<String> { it.lowercase() }.thenBy { it })
        .firstOrNull()
        ?: UNKNOWN_ARTIST_DISPLAY_NAME
}
