package io.github.rsgarrido.sazanami.player

import android.net.Uri
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.ui.library.buildLibraryAlbumGroups
import io.github.rsgarrido.sazanami.ui.library.buildLibraryArtistGroups
import java.security.MessageDigest
import java.util.Locale

enum class AutoBrowseContentStyle {
    LIST,
    GRID
}

data class AutoPlaylistEntry(
    val playlistId: Long,
    val name: String,
    val songs: List<Song>,
    val artworkUri: Uri? = null
)

data class AutoBrowseNode(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val song: Song? = null,
    val artworkUri: Uri? = song?.albumArtUri,
    val children: List<AutoBrowseNode> = emptyList(),
    val browsableChildrenStyle: AutoBrowseContentStyle? = null,
    val playableChildrenStyle: AutoBrowseContentStyle? = null
) {
    val isBrowsable: Boolean get() = children.isNotEmpty()
    val isPlayable: Boolean get() = song != null
}

fun buildAndroidAutoBrowseTree(
    songs: List<Song>,
    rootTitle: String,
    playlists: List<AutoPlaylistEntry> = emptyList(),
    artistArtworkUris: Map<String, Uri> = emptyMap()
): AutoBrowseNode {
    val playlistNodes = playlists
        .filter { playlist -> playlist.songs.isNotEmpty() }
        .sortedBy { playlist -> playlist.name.lowercase(Locale.ROOT) }
        .map { playlist ->
            val parentId = "playlist:${playlist.playlistId}"
            AutoBrowseNode(
                id = parentId,
                title = playlist.name,
                subtitle = songCountSubtitle(playlist.songs.size),
                artworkUri = playlist.artworkUri ?: firstArtworkUri(playlist.songs),
                children = playlist.songs.map { song -> songNode(song, parentId) },
                playableChildrenStyle = AutoBrowseContentStyle.LIST
            )
        }

    val albums = buildLibraryAlbumGroups(songs)
        .sortedWith(
            compareBy({ album -> album.title.lowercase(Locale.ROOT) }, { album -> album.artistText.lowercase(Locale.ROOT) })
        )
        .map { album ->
            val parentId = "album:${stableAutoNodeToken(album.key)}"
            AutoBrowseNode(
                id = parentId,
                title = album.title,
                subtitle = album.artistText,
                artworkUri = firstArtworkUri(album.songs),
                children = album.songs.map { song -> songNode(song, parentId) },
                playableChildrenStyle = AutoBrowseContentStyle.LIST
            )
        }

    val artists = buildLibraryArtistGroups(songs)
        .sortedBy { artist -> artist.name.lowercase(Locale.ROOT) }
        .map { artist ->
            val parentId = "artist:${artist.key}"
            AutoBrowseNode(
                id = parentId,
                title = artist.name,
                subtitle = songCountSubtitle(artist.songs.size),
                artworkUri = artistArtworkUris[artist.key] ?: firstArtworkUri(artist.songs),
                children = artist.songs.map { song -> songNode(song, parentId) },
                playableChildrenStyle = AutoBrowseContentStyle.LIST
            )
        }

    return AutoBrowseNode(
        id = ROOT_ID,
        title = rootTitle,
        children = listOf(
            AutoBrowseNode(
                id = PLAYLISTS_ID,
                title = "Playlists",
                children = playlistNodes,
                browsableChildrenStyle = AutoBrowseContentStyle.GRID
            ),
            AutoBrowseNode(
                id = ALBUMS_ID,
                title = "Albums",
                children = albums,
                browsableChildrenStyle = AutoBrowseContentStyle.GRID
            ),
            AutoBrowseNode(
                id = ARTISTS_ID,
                title = "Artists",
                children = artists,
                browsableChildrenStyle = AutoBrowseContentStyle.GRID
            ),
            AutoBrowseNode(
                id = SONGS_ID,
                title = "Songs",
                children = songs.map { song -> songNode(song, SONGS_ID) },
                playableChildrenStyle = AutoBrowseContentStyle.LIST
            )
        ),
        browsableChildrenStyle = AutoBrowseContentStyle.LIST,
        playableChildrenStyle = AutoBrowseContentStyle.LIST
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
    song = song,
    artworkUri = song.albumArtUri
)

private fun firstArtworkUri(songs: List<Song>): Uri? =
    songs.asSequence().mapNotNull(Song::albumArtUri).firstOrNull()

private fun songCountSubtitle(count: Int): String =
    if (count == 1) "1 song" else "$count songs"

private fun stableAutoNodeToken(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
    return digest.take(12).joinToString("") { byte -> "%02x".format(byte) }
}

const val ROOT_ID = "root"
const val PLAYLISTS_ID = "playlists"
const val ARTISTS_ID = "artists"
const val ALBUMS_ID = "albums"
const val SONGS_ID = "songs"
