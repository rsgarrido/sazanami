package com.example.cdplaya.data.local

data class PlaylistWithSongCount(
    val playlistId: Long,
    val name: String,
    val type: String,
    val artworkMode: String,
    val artworkReference: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val songCount: Int,
    val totalDuration: Long
)
