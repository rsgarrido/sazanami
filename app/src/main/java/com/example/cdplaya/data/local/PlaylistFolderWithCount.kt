package com.example.cdplaya.data.local

data class PlaylistFolderWithCount(
    val folderId: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val playlistCount: Int
)
