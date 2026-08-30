package io.github.rsgarrido.sazanami.data.playlistfile

import io.github.rsgarrido.sazanami.data.Song

data class PreparedPlaylistExport(
    val playlistName: String,
    val songs: List<Song>,
    val unavailableSongCount: Int
)
