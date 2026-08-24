package com.example.cdplaya.data.local

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playlists",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistFolderEntity::class,
            parentColumns = ["folderId"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["folderId"])]
)
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    val playlistId: Long = 0,
    val name: String,
    @ColumnInfo(defaultValue = "MANUAL") val type: String = "MANUAL",
    @ColumnInfo(defaultValue = "AUTOMATIC") val artworkMode: String = "AUTOMATIC",
    val artworkReference: String? = null,
    val folderId: Long? = null,
    val createdAt: Long,
    val updatedAt: Long
)
