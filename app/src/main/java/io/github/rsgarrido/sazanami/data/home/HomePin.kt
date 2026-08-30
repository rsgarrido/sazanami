package io.github.rsgarrido.sazanami.data.home

import io.github.rsgarrido.sazanami.data.Playlist
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.SongReference
import io.github.rsgarrido.sazanami.data.hasPersistedIdentity
import io.github.rsgarrido.sazanami.data.normalizedForPersistence
import io.github.rsgarrido.sazanami.data.toSongReference
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
enum class HomePinType {
    SONG,
    ALBUM,
    ARTIST,
    PLAYLIST
}

@Serializable
data class HomePin(
    val id: String,
    val type: HomePinType,
    val title: String,
    val subtitle: String = "",
    val anchor: SongReference? = null,
    val playlistId: Long? = null
) {
    fun normalizedForPersistence(): HomePin? {
        val normalizedId = id.trim()
        val normalizedTitle = title.trim()
        if (normalizedId.isBlank() || normalizedTitle.isBlank()) return null

        val normalizedAnchor = when (type) {
            HomePinType.PLAYLIST -> null
            else -> anchor?.takeIf { it.hasPersistedIdentity() }
                ?.normalizedForPersistence()
                ?: return null
        }
        val normalizedPlaylistId = when (type) {
            HomePinType.PLAYLIST -> playlistId?.takeIf { it > 0L } ?: return null
            else -> null
        }
        return copy(
            id = normalizedId,
            title = normalizedTitle,
            subtitle = subtitle.trim(),
            anchor = normalizedAnchor,
            playlistId = normalizedPlaylistId
        )
    }

    companion object {
        const val MAX_COUNT = 4

        fun song(song: Song): HomePin = HomePin(
            id = UUID.randomUUID().toString(),
            type = HomePinType.SONG,
            title = song.title.ifBlank { "Unknown Title" },
            subtitle = song.artist.ifBlank { "Unknown Artist" },
            anchor = song.toSongReference()
        )

        fun album(
            title: String,
            artistText: String,
            songs: List<Song>
        ): HomePin? {
            val anchorSong = songs.firstOrNull() ?: return null
            return HomePin(
                id = UUID.randomUUID().toString(),
                type = HomePinType.ALBUM,
                title = title.ifBlank { "Unknown Album" },
                subtitle = artistText.ifBlank { "Various Artists" },
                anchor = anchorSong.toSongReference()
            )
        }

        fun artist(
            name: String,
            songs: List<Song>
        ): HomePin? {
            val anchorSong = songs.firstOrNull() ?: return null
            return HomePin(
                id = UUID.randomUUID().toString(),
                type = HomePinType.ARTIST,
                title = name.ifBlank { "Unknown Artist" },
                subtitle = "Artist",
                anchor = anchorSong.toSongReference()
            )
        }

        fun playlist(playlist: Playlist): HomePin = HomePin(
            id = UUID.randomUUID().toString(),
            type = HomePinType.PLAYLIST,
            title = playlist.name,
            subtitle = "Playlist",
            playlistId = playlist.playlistId
        )
    }
}

fun sanitizeHomePins(pins: List<HomePin>): List<HomePin> {
    val ids = mutableSetOf<String>()
    return pins.mapNotNull(HomePin::normalizedForPersistence)
        .filter { pin -> ids.add(pin.id) }
        .take(HomePin.MAX_COUNT)
}
