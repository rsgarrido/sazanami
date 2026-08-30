package io.github.rsgarrido.sazanami.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "artist_picture_assignments")
data class ArtistPictureAssignmentEntity(
    @PrimaryKey val artistKey: String,
    val normalizedArtistName: String,
    val assetReference: String,
    val updatedAt: Long
)

@Dao
interface ArtistPictureAssignmentDao {
    @Query("SELECT * FROM artist_picture_assignments ORDER BY artistKey")
    fun observeAll(): Flow<List<ArtistPictureAssignmentEntity>>

    @Query("SELECT * FROM artist_picture_assignments ORDER BY artistKey")
    suspend fun getAll(): List<ArtistPictureAssignmentEntity>

    @Query("SELECT * FROM artist_picture_assignments WHERE artistKey = :artistKey")
    suspend fun get(artistKey: String): ArtistPictureAssignmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(assignment: ArtistPictureAssignmentEntity)

    @Query("DELETE FROM artist_picture_assignments WHERE artistKey = :artistKey")
    suspend fun delete(artistKey: String)

    @Query("DELETE FROM artist_picture_assignments")
    suspend fun deleteAll()
}
