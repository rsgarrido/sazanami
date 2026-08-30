package io.github.rsgarrido.sazanami.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "song_ratings",
    foreignKeys = [
        ForeignKey(
            entity = ListeningTrackIdentityEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackIdentityId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["rating"])]
)
data class SongRatingEntity(
    @PrimaryKey
    val trackIdentityId: Long,
    val rating: Int,
    val ratedAt: Long,
    val updatedAt: Long
) {
    init {
        require(rating in 1..5) { "Song rating must be between 1 and 5" }
        require(ratedAt >= 0L) { "Song rating timestamp cannot be negative" }
        require(updatedAt >= ratedAt) { "Song rating update cannot precede its creation" }
    }
}
