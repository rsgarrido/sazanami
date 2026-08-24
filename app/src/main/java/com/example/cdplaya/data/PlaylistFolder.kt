package com.example.cdplaya.data

data class PlaylistFolder(
    val folderId: Long,
    val name: String,
    val playlistCount: Int,
    val createdAt: Long,
    val modifiedAt: Long
)
