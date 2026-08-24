package com.example.cdplaya.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlists ORDER BY createdAt ASC, playlistId ASC")
    suspend fun getAllPlaylistEntities(): List<PlaylistEntity>

    @Query("SELECT * FROM playlist_songs ORDER BY playlistId ASC, position ASC")
    suspend fun getAllPlaylistSongEntities(): List<PlaylistSongEntity>

    @Query(
        """
        SELECT 
            playlists.playlistId,
            playlists.name,
            playlists.type,
            playlists.artworkMode,
            playlists.artworkReference,
            playlists.createdAt,
            playlists.updatedAt,
            COUNT(playlist_songs.playlistSongId) AS songCount,
            COALESCE(SUM(CASE WHEN playlist_songs.duration > 0 THEN playlist_songs.duration ELSE 0 END), 0) AS totalDuration
        FROM playlists
        LEFT JOIN playlist_songs ON playlists.playlistId = playlist_songs.playlistId
        GROUP BY playlists.playlistId
        ORDER BY playlists.updatedAt DESC
        """
    )
    suspend fun getPlaylistsWithSongCount(): List<PlaylistWithSongCount>

    @Query("SELECT * FROM playlists WHERE playlistId = :playlistId LIMIT 1")
    suspend fun getPlaylistById(playlistId: Long): PlaylistEntity?

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getPlaylistSongs(playlistId: Long): List<PlaylistSongEntity>

    @Query("UPDATE playlist_songs SET position = :position WHERE playlistSongId = :playlistSongId")
    suspend fun updatePlaylistSongPosition(
        playlistSongId: Long,
        position: Int
    )

    @Query("SELECT COUNT(*) FROM playlists WHERE LOWER(name) = LOWER(:name)")
    suspend fun countPlaylistsWithName(name: String): Int

    @Query(
        """
        SELECT COUNT(*) 
        FROM playlists 
        WHERE LOWER(name) = LOWER(:name) 
        AND playlistId != :playlistId
        """
    )
    suspend fun countOtherPlaylistsWithName(
        playlistId: Long,
        name: String
    ): Int

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Insert
    suspend fun insertPlaylistSong(playlistSong: PlaylistSongEntity): Long

    @Insert
    suspend fun insertPlaylistSongs(playlistSongs: List<PlaylistSongEntity>): List<Long>

    @Update
    suspend fun updatePlaylistSong(playlistSong: PlaylistSongEntity)

    @Update
    suspend fun updatePlaylistSongs(playlistSongs: List<PlaylistSongEntity>)

    @Query("UPDATE playlists SET name = :name, updatedAt = :updatedAt WHERE playlistId = :playlistId")
    suspend fun renamePlaylist(
        playlistId: Long,
        name: String,
        updatedAt: Long
    )

    @Query(
        """
        UPDATE playlists
        SET artworkMode = :artworkMode,
            artworkReference = :artworkReference,
            updatedAt = :updatedAt
        WHERE playlistId = :playlistId
        """
    )
    suspend fun updatePlaylistArtwork(
        playlistId: Long,
        artworkMode: String,
        artworkReference: String?,
        updatedAt: Long
    )

    @Query("DELETE FROM playlists WHERE playlistId = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistSongId = :playlistSongId")
    suspend fun deletePlaylistSong(playlistSongId: Long)

    @Query("DELETE FROM playlist_songs")
    suspend fun deleteAllPlaylistSongs()

    @Query("DELETE FROM playlists")
    suspend fun deleteAllPlaylists()

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun getLastPositionForPlaylist(playlistId: Long): Int

    @Query("UPDATE playlists SET updatedAt = :updatedAt WHERE playlistId = :playlistId")
    suspend fun updatePlaylistTimestamp(playlistId: Long, updatedAt: Long)
}
