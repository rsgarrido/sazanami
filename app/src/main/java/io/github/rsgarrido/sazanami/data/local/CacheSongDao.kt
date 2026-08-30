package io.github.rsgarrido.sazanami.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface CachedSongDao {
    @Query("SELECT * FROM cached_songs ORDER BY title COLLATE NOCASE ASC")
    suspend fun getAllCachedSongs(): List<CachedSongEntity>

    @Query("SELECT COUNT(*) FROM cached_songs")
    suspend fun getCachedSongCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedSongs(songs: List<CachedSongEntity>)

    @Query("DELETE FROM cached_songs")
    suspend fun clearCachedSongs()

    @Transaction
    suspend fun replaceCachedSongs(songs: List<CachedSongEntity>) {
        clearCachedSongs()

        if (songs.isNotEmpty()) {
            insertCachedSongs(songs)
        }
    }
}