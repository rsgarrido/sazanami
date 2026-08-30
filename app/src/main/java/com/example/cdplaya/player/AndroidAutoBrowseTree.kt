package com.example.cdplaya.player

import com.example.cdplaya.data.Song
import com.example.cdplaya.ui.library.buildLibraryAlbumGroups
import com.example.cdplaya.ui.library.buildLibraryArtistGroups

data class AutoBrowseNode(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val song: Song? = null,
    val children: List<AutoBrowseNode> = emptyList()
)

fun buildAndroidAutoBrowseTree(
    songs: List<Song>,
    rootTitle: String
): AutoBrowseNode {
    val artists = buildLibraryArtistGroups(songs)
        .sortedBy { it.name.lowercase() }
        .mapIndexed { index, artist ->
            AutoBrowseNode(
                id = "artist:$index",
                title = artist.name,
                children = artist.songs.map { song -> songNode(song, "artist:$index") }
            )
        }

    val albums = buildLibraryAlbumGroups(songs)
        .sortedBy { it.title.lowercase() }
        .mapIndexed { index, album ->
            AutoBrowseNode(
                id = "album:$index",
                title = album.title,
                subtitle = album.artistText,
                children = album.songs.map { song -> songNode(song, "album:$index") }
            )
        }

    return AutoBrowseNode(
        id = ROOT_ID,
        title = rootTitle,
        children = listOf(
            AutoBrowseNode(ARTISTS_ID, "Artists", children = artists),
            AutoBrowseNode(ALBUMS_ID, "Albums", children = albums),
            AutoBrowseNode(
                SONGS_ID,
                "Songs",
                children = songs.map { song -> songNode(song, SONGS_ID) }
            )
        )
    )
}

fun AutoBrowseNode.findNode(mediaId: String): AutoBrowseNode? {
    if (id == mediaId) return this
    return children.firstNotNullOfOrNull { child -> child.findNode(mediaId) }
}

fun AutoBrowseNode.findParent(mediaId: String): AutoBrowseNode? {
    if (children.any { child -> child.id == mediaId }) return this
    return children.firstNotNullOfOrNull { child -> child.findParent(mediaId) }
}

private fun songNode(song: Song, parentId: String) = AutoBrowseNode(
    id = "song:$parentId:${song.id}",
    title = song.title,
    subtitle = song.artist.ifBlank { "Unknown Artist" },
    song = song
)

const val ROOT_ID = "root"
const val ARTISTS_ID = "artists"
const val ALBUMS_ID = "albums"
const val SONGS_ID = "songs"
