package io.github.rsgarrido.sazanami.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlist_folders")
data class PlaylistFolderEntity(
    @PrimaryKey(autoGenerate = true)
    val folderId: Long = 0,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long
)
