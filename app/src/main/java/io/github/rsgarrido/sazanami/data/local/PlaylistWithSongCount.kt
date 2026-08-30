package io.github.rsgarrido.sazanami.data.local

data class PlaylistWithSongCount(
    val playlistId: Long,
    val name: String,
    val type: String,
    val artworkMode: String,
    val artworkReference: String?,
    val folderId: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val songCount: Int,
    val totalDuration: Long
)
