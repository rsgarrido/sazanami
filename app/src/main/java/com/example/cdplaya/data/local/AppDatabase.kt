package com.example.cdplaya.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        DatabaseMarkerEntity::class,
        FavoriteSongEntity::class,
        PlaylistFolderEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        SongPlayStatsEntity::class,
        CachedSongEntity::class,
        ListeningTrackIdentityEntity::class,
        LocalTrackBindingEntity::class,
        ListeningIdentityReconciliationEntity::class,
        ListeningEventEntity::class,
        LegacyListeningBaselineEntity::class,
        SongRatingEntity::class,
        ListeningImportSourceEntity::class,
        ListeningImportBatchEntity::class,
        ListeningTrackExternalIdEntity::class,
        ImportedListeningEventEvidenceEntity::class,
        ListeningImportBatchEventEntity::class
    ],
    version = 14,
    exportSchema = true
)
@TypeConverters(ListeningHistoryTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteSongDao(): FavoriteSongDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun songPlayStatsDao(): SongPlayStatsDao
    abstract fun cachedSongDao(): CachedSongDao
    abstract fun listeningTrackIdentityDao(): ListeningTrackIdentityDao
    abstract fun localTrackBindingDao(): LocalTrackBindingDao
    abstract fun listeningIdentityReconciliationDao(): ListeningIdentityReconciliationDao
    abstract fun listeningIdentityReconciliationCandidateDao(): ListeningIdentityReconciliationCandidateDao
    abstract fun listeningEventDao(): ListeningEventDao
    abstract fun listeningStatsDao(): ListeningStatsDao
    abstract fun legacyListeningBaselineDao(): LegacyListeningBaselineDao
    abstract fun songRatingDao(): SongRatingDao
    abstract fun listeningImportSourceDao(): ListeningImportSourceDao
    abstract fun listeningImportBatchDao(): ListeningImportBatchDao
    abstract fun listeningTrackExternalIdDao(): ListeningTrackExternalIdDao
    abstract fun importedListeningEventEvidenceDao(): ImportedListeningEventEvidenceDao
    abstract fun listeningImportBatchEventDao(): ListeningImportBatchEventDao
}
