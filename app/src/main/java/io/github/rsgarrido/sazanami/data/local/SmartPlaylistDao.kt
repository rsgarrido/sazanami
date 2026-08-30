package io.github.rsgarrido.sazanami.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

data class SmartPlaylistCandidateRow(
    val mediaStoreId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val trackNumber: Int,
    val duration: Long,
    val uriString: String,
    val filePath: String,
    val folderPath: String,
    val albumArtUriString: String?,
    val albumArtist: String,
    val volumeName: String,
    val displayName: String,
    val relativePath: String,
    val fileSizeBytes: Long,
    val dateAddedEpochSeconds: Long,
    val dateModifiedEpochSeconds: Long,
    val year: Int?,
    val artworkEnrichmentVersion: Int,
    val genresJson: String,
    val normalizedGenresJson: String,
    val composersJson: String,
    val composerText: String,
    val publisher: String,
    val bpm: Int?,
    val discNumber: Int?,
    val discTotal: Int?,
    val embeddedMetadataEnrichmentVersion: Int,
    val cachedAt: Long,
    val totalPlayCount: Long,
    val lastPlayedAt: Long?,
    val rating: Int?
)

data class SmartPlaylistCountRow(val count: Int)

@Dao
interface SmartPlaylistDao {
    @Query("SELECT * FROM smart_playlist_definitions WHERE playlistId = :playlistId LIMIT 1")
    suspend fun getDefinition(playlistId: Long): SmartPlaylistDefinitionEntity?

    @Query("SELECT * FROM smart_playlist_definitions ORDER BY playlistId ASC")
    suspend fun getAllDefinitions(): List<SmartPlaylistDefinitionEntity>

    @Query("SELECT * FROM smart_playlist_definitions WHERE playlistId = :playlistId LIMIT 1")
    fun observeDefinition(playlistId: Long): Flow<SmartPlaylistDefinitionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDefinition(definition: SmartPlaylistDefinitionEntity)

    @Query("DELETE FROM smart_playlist_definitions WHERE playlistId = :playlistId")
    suspend fun deleteDefinition(playlistId: Long)

    @Query("SELECT * FROM smart_playlist_resolution_states WHERE playlistId = :playlistId LIMIT 1")
    suspend fun getResolutionState(playlistId: Long): SmartPlaylistResolutionStateEntity?

    @Query("SELECT * FROM smart_playlist_resolution_states WHERE playlistId = :playlistId LIMIT 1")
    fun observeResolutionState(playlistId: Long): Flow<SmartPlaylistResolutionStateEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertResolutionState(state: SmartPlaylistResolutionStateEntity)

    @Query("UPDATE smart_playlist_resolution_states SET isDirty = 1 WHERE playlistId = :playlistId")
    suspend fun markDirty(playlistId: Long)

    @Query("UPDATE smart_playlist_resolution_states SET isDirty = 1")
    suspend fun markAllDirty()

    @Query("SELECT songs.* FROM smart_playlist_cached_songs cache JOIN cached_songs songs ON songs.mediaStoreId = cache.mediaStoreId AND songs.volumeName = cache.volumeName WHERE cache.playlistId = :playlistId ORDER BY cache.position ASC")
    suspend fun getCachedSongs(playlistId: Long): List<CachedSongEntity>

    @Query("DELETE FROM smart_playlist_cached_songs WHERE playlistId = :playlistId")
    suspend fun deleteCachedSongs(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedSongs(songs: List<SmartPlaylistCachedSongEntity>)

    @RawQuery(
        observedEntities = [
            CachedSongEntity::class,
            ListeningTrackIdentityEntity::class,
            LocalTrackBindingEntity::class,
            ListeningIdentityReconciliationEntity::class,
            ListeningEventEntity::class,
            LegacyListeningBaselineEntity::class,
            SongRatingEntity::class
        ]
    )
    suspend fun evaluate(query: SupportSQLiteQuery): List<SmartPlaylistCandidateRow>

    @RawQuery(
        observedEntities = [
            CachedSongEntity::class,
            ListeningTrackIdentityEntity::class,
            LocalTrackBindingEntity::class,
            ListeningIdentityReconciliationEntity::class,
            ListeningEventEntity::class,
            LegacyListeningBaselineEntity::class,
            SongRatingEntity::class
        ]
    )
    suspend fun count(query: SupportSQLiteQuery): SmartPlaylistCountRow

    @Query("SELECT * FROM generated_playlist_states WHERE playlistId = :playlistId LIMIT 1")
    suspend fun getGeneratedState(playlistId: Long): GeneratedPlaylistStateEntity?

    @Query("SELECT * FROM generated_playlist_states ORDER BY playlistId ASC")
    suspend fun getAllGeneratedStates(): List<GeneratedPlaylistStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGeneratedState(state: GeneratedPlaylistStateEntity)

    @Query("SELECT * FROM generated_playlist_songs WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getGeneratedSongs(playlistId: Long): List<GeneratedPlaylistSongEntity>

    @Query("SELECT * FROM generated_playlist_songs ORDER BY playlistId ASC, position ASC")
    suspend fun getAllGeneratedSongs(): List<GeneratedPlaylistSongEntity>

    @Query("DELETE FROM generated_playlist_songs WHERE playlistId = :playlistId")
    suspend fun deleteGeneratedSongs(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGeneratedSongs(songs: List<GeneratedPlaylistSongEntity>)
}
