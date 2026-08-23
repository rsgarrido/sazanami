package com.example.cdplaya.data.home

import com.example.cdplaya.data.Song
import com.example.cdplaya.data.SongReference
import com.example.cdplaya.data.hasPersistedIdentity
import com.example.cdplaya.data.normalizedForPersistence
import com.example.cdplaya.data.toSongReference
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
enum class HomePinType {
    SONG,
    ALBUM,
    ARTIST
}

@Serializable
data class HomePin(
    val id: String,
    val type: HomePinType,
    val title: String,
    val subtitle: String = "",
    val anchor: SongReference
) {
    fun normalizedForPersistence(): HomePin? {
        val normalizedId = id.trim()
        val normalizedTitle = title.trim()
        if (normalizedId.isBlank() || normalizedTitle.isBlank() || !anchor.hasPersistedIdentity()) {
            return null
        }
        return copy(
            id = normalizedId,
            title = normalizedTitle,
            subtitle = subtitle.trim(),
            anchor = anchor.normalizedForPersistence()
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
    }
}

fun sanitizeHomePins(pins: List<HomePin>): List<HomePin> {
    val ids = mutableSetOf<String>()
    return pins.mapNotNull(HomePin::normalizedForPersistence)
        .filter { pin -> ids.add(pin.id) }
        .take(HomePin.MAX_COUNT)
}
