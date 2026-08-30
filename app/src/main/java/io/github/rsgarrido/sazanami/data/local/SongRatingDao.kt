package io.github.rsgarrido.sazanami.data.local

import androidx.room.Dao
import androidx.room.ColumnInfo
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class SongRatingBindingRow(
    val trackIdentityId: Long,
    val rating: Int,
    val ratedAt: Long,
    val updatedAt: Long,
    @ColumnInfo(name = "bindingReferenceKey")
    val referenceKey: String?
)

@Dao
interface SongRatingDao {
    @Query("SELECT * FROM song_ratings WHERE trackIdentityId = :trackIdentityId")
    suspend fun getByTrackIdentityId(trackIdentityId: Long): SongRatingEntity?

    @Query(
        "SELECT ratings.trackIdentityId, ratings.rating, ratings.ratedAt, ratings.updatedAt, " +
            "bindings.referenceKey AS bindingReferenceKey " +
            "FROM song_ratings AS ratings " +
            "LEFT JOIN local_track_bindings AS bindings " +
            "ON bindings.trackIdentityId = ratings.trackIdentityId " +
            "ORDER BY ratings.trackIdentityId ASC, bindings.id ASC"
    )
    fun observeAllWithBindings(): Flow<List<SongRatingBindingRow>>

    @Query("SELECT * FROM song_ratings ORDER BY trackIdentityId ASC")
    suspend fun getAllForBackup(): List<SongRatingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rating: SongRatingEntity)

    @Insert
    suspend fun insert(ratings: List<SongRatingEntity>)

    @Query("DELETE FROM song_ratings WHERE trackIdentityId = :trackIdentityId")
    suspend fun deleteByTrackIdentityId(trackIdentityId: Long): Int

    @Query("DELETE FROM song_ratings")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM song_ratings")
    suspend fun count(): Long
}
