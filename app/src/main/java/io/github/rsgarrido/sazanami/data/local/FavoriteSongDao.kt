package io.github.rsgarrido.sazanami.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface FavoriteSongDao {

    @Query("SELECT * FROM favorite_songs ORDER BY createdAt DESC")
    suspend fun getAllFavorites(): List<FavoriteSongEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favoriteSong: FavoriteSongEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorites(favorites: List<FavoriteSongEntity>)

    @Query("DELETE FROM favorite_songs WHERE referenceKey IN (:referenceKeys)")
    suspend fun deleteFavoritesByReferenceKeys(referenceKeys: List<String>)

    @Transaction
    suspend fun applyReferenceBackfill(
        oldReferenceKeys: List<String>,
        favorites: List<FavoriteSongEntity>
    ) {
        if (oldReferenceKeys.isNotEmpty()) deleteFavoritesByReferenceKeys(oldReferenceKeys)
        if (favorites.isNotEmpty()) insertFavorites(favorites)
    }

    @Query("SELECT COUNT(*) FROM favorite_songs WHERE referenceKey = :referenceKey")
    suspend fun countFavoriteByReferenceKey(referenceKey: String): Int

    @Query("DELETE FROM favorite_songs WHERE referenceKey = :referenceKey")
    suspend fun deleteFavoriteByReferenceKey(referenceKey: String)

    @Query("DELETE FROM favorite_songs WHERE songKey = :songKey")
    suspend fun deleteFavoriteByKey(songKey: String)

    @Query("DELETE FROM favorite_songs")
    suspend fun deleteAllFavorites()
}
