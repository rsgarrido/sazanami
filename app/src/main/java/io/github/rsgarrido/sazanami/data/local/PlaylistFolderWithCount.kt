package io.github.rsgarrido.sazanami.data.local

data class PlaylistFolderWithCount(
    val folderId: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val playlistCount: Int
)
