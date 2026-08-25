package com.example.cdplaya.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlists ORDER BY createdAt ASC, playlistId ASC")
    suspend fun getAllPlaylistEntities(): List<PlaylistEntity>

    @Query("SELECT * FROM playlist_folders ORDER BY name COLLATE NOCASE ASC, folderId ASC")
    suspend fun getAllPlaylistFolderEntities(): List<PlaylistFolderEntity>

    @Query(
        """
        SELECT playlist_folders.folderId,
               playlist_folders.name,
               playlist_folders.createdAt,
               playlist_folders.updatedAt,
               COUNT(playlists.playlistId) AS playlistCount
        FROM playlist_folders
        LEFT JOIN playlists ON playlists.folderId = playlist_folders.folderId
        GROUP BY playlist_folders.folderId
        ORDER BY playlist_folders.name COLLATE NOCASE ASC, playlist_folders.folderId ASC
        """
    )
    suspend fun getPlaylistFoldersWithCount(): List<PlaylistFolderWithCount>

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
            playlists.folderId,
            playlists.createdAt,
            playlists.updatedAt,
            COUNT(CASE WHEN playlists.type = 'MANUAL' THEN playlist_songs.playlistSongId END) AS songCount,
            COALESCE(SUM(CASE WHEN playlists.type = 'MANUAL' AND playlist_songs.duration > 0 THEN playlist_songs.duration ELSE 0 END), 0) AS totalDuration
        FROM playlists
        LEFT JOIN playlist_songs ON playlists.playlistId = playlist_songs.playlistId
        GROUP BY playlists.playlistId
        ORDER BY playlists.updatedAt DESC
        """
    )
    suspend fun getPlaylistsWithSongCount(): List<PlaylistWithSongCount>

    @Query("SELECT * FROM playlists WHERE playlistId = :playlistId LIMIT 1")
    suspend fun getPlaylistById(playlistId: Long): PlaylistEntity?

    @Query("SELECT * FROM playlist_folders WHERE folderId = :folderId LIMIT 1")
    suspend fun getPlaylistFolderById(folderId: Long): PlaylistFolderEntity?

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getPlaylistSongs(playlistId: Long): List<PlaylistSongEntity>

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

    @Query("SELECT COUNT(*) FROM playlist_folders WHERE LOWER(name) = LOWER(:name)")
    suspend fun countPlaylistFoldersWithName(name: String): Int

    @Query(
        """
        SELECT COUNT(*) FROM playlist_folders
        WHERE LOWER(name) = LOWER(:name) AND folderId != :folderId
        """
    )
    suspend fun countOtherPlaylistFoldersWithName(folderId: Long, name: String): Int

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Insert
    suspend fun insertPlaylistFolder(folder: PlaylistFolderEntity): Long

    @Insert
    suspend fun insertPlaylistSong(playlistSong: PlaylistSongEntity): Long

    @Insert
    suspend fun insertPlaylistSongs(playlistSongs: List<PlaylistSongEntity>): List<Long>

    @Update
    suspend fun updatePlaylistSong(playlistSong: PlaylistSongEntity)

    @Update
    suspend fun updatePlaylistSongs(playlistSongs: List<PlaylistSongEntity>)

    @Transaction
    suspend fun updatePlaylistSongOrder(
        playlistId: Long,
        orderedPlaylistSongIds: List<Long>,
        updatedAt: Long
    ): Boolean {
        val playlistSongs = getPlaylistSongs(playlistId)
        if (
            orderedPlaylistSongIds.size != playlistSongs.size ||
            orderedPlaylistSongIds.distinct().size != orderedPlaylistSongIds.size ||
            orderedPlaylistSongIds.toSet() != playlistSongs
                .mapTo(mutableSetOf(), PlaylistSongEntity::playlistSongId)
        ) {
            return false
        }
        val songsById = playlistSongs.associateBy(PlaylistSongEntity::playlistSongId)
        updatePlaylistSongs(
            orderedPlaylistSongIds.mapIndexed { position, playlistSongId ->
                checkNotNull(songsById[playlistSongId]).copy(position = position)
            }
        )
        updatePlaylistTimestamp(playlistId, updatedAt)
        return true
    }

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

    @Query(
        "UPDATE playlist_folders SET name = :name, updatedAt = :updatedAt WHERE folderId = :folderId"
    )
    suspend fun renamePlaylistFolder(folderId: Long, name: String, updatedAt: Long)

    @Query("UPDATE playlist_folders SET updatedAt = :updatedAt WHERE folderId = :folderId")
    suspend fun updatePlaylistFolderTimestamp(folderId: Long, updatedAt: Long)

    @Query(
        "UPDATE playlists SET folderId = :folderId, updatedAt = :updatedAt WHERE playlistId = :playlistId"
    )
    suspend fun updatePlaylistFolderMembership(
        playlistId: Long,
        folderId: Long?,
        updatedAt: Long
    )

    @Transaction
    suspend fun movePlaylistToFolder(
        playlistId: Long,
        folderId: Long?,
        updatedAt: Long
    ): Boolean {
        val playlist = getPlaylistById(playlistId) ?: return false
        if (playlist.folderId == folderId) return true
        if (folderId != null && getPlaylistFolderById(folderId) == null) return false

        updatePlaylistFolderMembership(playlistId, folderId, updatedAt)
        playlist.folderId?.let { updatePlaylistFolderTimestamp(it, updatedAt) }
        folderId?.let { updatePlaylistFolderTimestamp(it, updatedAt) }
        return true
    }

    @Query(
        "UPDATE playlists SET folderId = NULL, updatedAt = :updatedAt WHERE folderId = :folderId"
    )
    suspend fun moveFolderPlaylistsToRoot(folderId: Long, updatedAt: Long)

    @Query("DELETE FROM playlist_folders WHERE folderId = :folderId")
    suspend fun deletePlaylistFolderEntity(folderId: Long)

    @Transaction
    suspend fun deletePlaylistFolder(folderId: Long, updatedAt: Long) {
        moveFolderPlaylistsToRoot(folderId, updatedAt)
        deletePlaylistFolderEntity(folderId)
    }

    @Query("DELETE FROM playlists WHERE playlistId = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistSongId = :playlistSongId")
    suspend fun deletePlaylistSong(playlistSongId: Long)

    @Query("DELETE FROM playlist_songs")
    suspend fun deleteAllPlaylistSongs()

    @Query("DELETE FROM playlists")
    suspend fun deleteAllPlaylists()

    @Query("DELETE FROM playlist_folders")
    suspend fun deleteAllPlaylistFolders()

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun getLastPositionForPlaylist(playlistId: Long): Int

    @Query("UPDATE playlists SET updatedAt = :updatedAt WHERE playlistId = :playlistId")
    suspend fun updatePlaylistTimestamp(playlistId: Long, updatedAt: Long)
}
