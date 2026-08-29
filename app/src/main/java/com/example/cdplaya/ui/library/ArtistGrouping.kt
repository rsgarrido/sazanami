package com.example.cdplaya.ui.library

import com.example.cdplaya.data.ArtistIdentity
import com.example.cdplaya.data.Song
import com.example.cdplaya.data.UNKNOWN_ARTIST_DISPLAY_NAME
import com.example.cdplaya.data.artistIdentity
import com.example.cdplaya.ui.sortSongsForArtistDetail

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
