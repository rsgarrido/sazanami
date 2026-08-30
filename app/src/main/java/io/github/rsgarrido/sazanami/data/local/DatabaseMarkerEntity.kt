package io.github.rsgarrido.sazanami.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "database_marker")
data class DatabaseMarkerEntity(
    @PrimaryKey val id: Int,
    val name: String
)