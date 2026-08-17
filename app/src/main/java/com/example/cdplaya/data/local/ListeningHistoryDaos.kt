package com.example.cdplaya.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ListeningTrackIdentityDao {
    @Insert
    suspend fun insert(identity: ListeningTrackIdentityEntity): Long
    @Insert suspend fun insert(identities: List<ListeningTrackIdentityEntity>): List<Long>

    @Query("SELECT * FROM listening_track_identities WHERE id = :id")
    suspend fun getById(id: Long): ListeningTrackIdentityEntity?

    @Query("SELECT * FROM listening_track_identities ORDER BY id")
    suspend fun getAll(): List<ListeningTrackIdentityEntity>

    @Query("DELETE FROM listening_track_identities")
    suspend fun deleteAll()
}

@Dao
interface LocalTrackBindingDao {
    @Insert
    suspend fun insert(binding: LocalTrackBindingEntity): Long

    @Query("SELECT * FROM local_track_bindings WHERE id = :id")
    suspend fun getById(id: Long): LocalTrackBindingEntity?

    @Query("SELECT * FROM local_track_bindings WHERE referenceKey = :referenceKey LIMIT 1")
    suspend fun getByReferenceKey(referenceKey: String): LocalTrackBindingEntity?

    @Query("SELECT * FROM local_track_bindings WHERE trackIdentityId = :trackIdentityId ORDER BY id")
    suspend fun getForTrackIdentity(trackIdentityId: Long): List<LocalTrackBindingEntity>

    @Query("DELETE FROM local_track_bindings WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM local_track_bindings ORDER BY trackIdentityId ASC, id ASC")
    suspend fun getAllForBackup(): List<LocalTrackBindingEntity>

    @Query("DELETE FROM local_track_bindings")
    suspend fun deleteAll()
}

@Dao
interface ListeningEventDao {
    @Insert
    suspend fun insert(event: ListeningEventEntity): Long

    @Insert
    suspend fun insert(events: List<ListeningEventEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringConflict(event: ListeningEventEntity): Long

    @Query("SELECT * FROM listening_events WHERE eventUuid = :eventUuid LIMIT 1")
    suspend fun getByUuid(eventUuid: String): ListeningEventEntity?

    @Query("SELECT * FROM listening_events WHERE id = :id")
    suspend fun getById(id: Long): ListeningEventEntity?

    @Query("SELECT * FROM listening_events WHERE playbackSessionId = :playbackSessionId LIMIT 1")
    suspend fun getByPlaybackSessionId(playbackSessionId: String): ListeningEventEntity?

    @Query("SELECT COUNT(*) FROM listening_events")
    suspend fun count(): Long

    @Query(
        "SELECT * FROM listening_events " +
            "WHERE publicationState != 'import_pending' " +
            "ORDER BY attributionAt ASC, id ASC LIMIT :limit OFFSET :offset"
    )
    suspend fun getBackupPage(limit: Int, offset: Int): List<ListeningEventEntity>

    @Query("DELETE FROM listening_events")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM listening_events WHERE publicationState = 'import_pending' AND id IN (SELECT eventId FROM listening_import_batch_events WHERE batchId = :batchId)")
    suspend fun countPendingForBatch(batchId: Long): Long

    @Query("UPDATE listening_events SET publicationState = 'import_published' WHERE publicationState = 'import_pending' AND id IN (SELECT eventId FROM listening_import_batch_events WHERE batchId = :batchId)")
    suspend fun publishForBatch(batchId: Long): Int

    @Query("DELETE FROM listening_events WHERE publicationState = 'import_pending' AND id IN (SELECT eventId FROM listening_import_batch_events WHERE batchId = :batchId) AND NOT EXISTS (SELECT 1 FROM listening_import_batch_events other WHERE other.eventId = listening_events.id AND other.batchId != :batchId)")
    suspend fun deleteUnsharedPendingForBatch(batchId: Long): Int
}

@Dao
interface LegacyListeningBaselineDao {
    @Insert
    suspend fun insert(baseline: LegacyListeningBaselineEntity)

    @Insert
    suspend fun insert(baselines: List<LegacyListeningBaselineEntity>)

    @Query("SELECT * FROM legacy_listening_baselines WHERE trackIdentityId = :trackIdentityId")
    suspend fun getByTrackIdentityId(trackIdentityId: Long): LegacyListeningBaselineEntity?

    @Query("SELECT * FROM legacy_listening_baselines WHERE legacyReferenceKey = :referenceKey LIMIT 1")
    suspend fun getByLegacyReferenceKey(referenceKey: String): LegacyListeningBaselineEntity?

    @Query("SELECT COUNT(*) FROM legacy_listening_baselines")
    suspend fun count(): Long

    @Query("SELECT * FROM legacy_listening_baselines ORDER BY trackIdentityId ASC")
    suspend fun getAllForBackup(): List<LegacyListeningBaselineEntity>

    @Query("DELETE FROM legacy_listening_baselines")
    suspend fun deleteAll()
}

@Dao
interface ListeningImportSourceDao {
    @Insert suspend fun insert(source: ListeningImportSourceEntity): Long
    @Query("SELECT * FROM listening_import_sources WHERE id = :id") suspend fun getById(id: Long): ListeningImportSourceEntity?
    @Query("SELECT * FROM listening_import_sources WHERE stableUuid = :stableUuid LIMIT 1") suspend fun getByStableUuid(stableUuid: String): ListeningImportSourceEntity?
    @Query("SELECT * FROM listening_import_sources ORDER BY id") suspend fun getAllForBackup(): List<ListeningImportSourceEntity>
    @Query("DELETE FROM listening_import_sources") suspend fun deleteAll()
}

@Dao
interface ListeningImportBatchDao {
    @Insert suspend fun insert(batch: ListeningImportBatchEntity): Long
    @Insert suspend fun insert(batches: List<ListeningImportBatchEntity>): List<Long>
    @Query("SELECT * FROM listening_import_batches WHERE id = :id") suspend fun getById(id: Long): ListeningImportBatchEntity?
    @Query("SELECT * FROM listening_import_batches WHERE status = 'published' ORDER BY id") suspend fun getPublishedForBackup(): List<ListeningImportBatchEntity>
    @Query("UPDATE listening_import_batches SET status = 'published', completedAt = :completedAt WHERE id = :id AND status = 'pending'") suspend fun publish(id: Long, completedAt: Long): Int
    @Query("UPDATE listening_import_batches SET status = 'cancelled', completedAt = :completedAt WHERE id = :id AND status = 'pending'") suspend fun cancel(id: Long, completedAt: Long): Int
    @Query("DELETE FROM listening_import_batches") suspend fun deleteAll()
}

@Dao
interface ListeningTrackExternalIdDao {
    @Insert suspend fun insert(entity: ListeningTrackExternalIdEntity): Long
    @Insert suspend fun insert(entities: List<ListeningTrackExternalIdEntity>): List<Long>
    @Query("SELECT * FROM listening_track_external_ids WHERE sourceType = :sourceType AND externalId = :externalId LIMIT 1") suspend fun find(sourceType: ListeningSource, externalId: String): ListeningTrackExternalIdEntity?
    @Query("SELECT * FROM listening_track_external_ids ORDER BY id") suspend fun getAllForBackup(): List<ListeningTrackExternalIdEntity>
    @Query("DELETE FROM listening_track_external_ids") suspend fun deleteAll()
}

@Dao
interface ImportedListeningEventEvidenceDao {
    @Insert suspend fun insert(entity: ImportedListeningEventEvidenceEntity)
    @Insert suspend fun insert(entities: List<ImportedListeningEventEvidenceEntity>)
    @Query("SELECT * FROM imported_listening_event_evidence WHERE sourceProfileId = :sourceProfileId AND fingerprintVersion = :fingerprintVersion AND fingerprint = :fingerprint AND duplicateOrdinal = :duplicateOrdinal LIMIT 1")
    suspend fun find(sourceProfileId: Long, fingerprintVersion: Int, fingerprint: String, duplicateOrdinal: Int): ImportedListeningEventEvidenceEntity?
    @Query("SELECT * FROM imported_listening_event_evidence WHERE eventId = :eventId")
    suspend fun getByEventId(eventId: Long): ImportedListeningEventEvidenceEntity?
    @Query("SELECT evidence.* FROM imported_listening_event_evidence evidence JOIN listening_events event ON event.id = evidence.eventId WHERE event.publicationState != 'import_pending' ORDER BY evidence.eventId") suspend fun getAllForBackup(): List<ImportedListeningEventEvidenceEntity>
    @Query("DELETE FROM imported_listening_event_evidence") suspend fun deleteAll()
}

@Dao
interface ListeningImportBatchEventDao {
    @Insert suspend fun insert(entity: ListeningImportBatchEventEntity)
    @Insert suspend fun insert(entities: List<ListeningImportBatchEventEntity>)
    @Query("SELECT COUNT(*) FROM listening_import_batch_events WHERE batchId = :batchId") suspend fun countForBatch(batchId: Long): Long
    @Query("""
        SELECT COUNT(*) FROM listening_import_batch_events link
        JOIN listening_import_batches batch ON batch.id = link.batchId
        JOIN listening_import_sources source ON source.id = batch.sourceProfileId
        JOIN listening_events event ON event.id = link.eventId
        WHERE link.batchId = :batchId AND (
            event.source != source.sourceType OR event.source = 'cdplaya' OR
            event.qualificationPolicy != batch.qualificationPolicy OR event.publicationState = 'native'
        )
    """)
    suspend fun countIncompatibleEventsForBatch(batchId: Long): Long
    @Query("""
        SELECT COUNT(*) FROM listening_import_batch_events link
        JOIN listening_import_batches batch ON batch.id = link.batchId
        JOIN imported_listening_event_evidence evidence ON evidence.eventId = link.eventId
        WHERE link.batchId = :batchId AND evidence.sourceProfileId != batch.sourceProfileId
    """)
    suspend fun countIncompatibleEvidenceForBatch(batchId: Long): Long
    @Query("""
        SELECT COUNT(*) FROM listening_import_batch_events link
        JOIN listening_import_batches batch ON batch.id = link.batchId
        WHERE link.eventId = :eventId AND batch.sourceProfileId != :sourceProfileId
    """)
    suspend fun countOtherSourceProfilesForEvent(eventId: Long, sourceProfileId: Long): Long
    @Query("""
        SELECT COUNT(DISTINCT link.eventId) FROM listening_import_batch_events link
        JOIN listening_import_batches batch ON batch.id = link.batchId
        WHERE link.batchId = :batchId AND EXISTS (
            SELECT 1 FROM listening_import_batch_events other
            JOIN listening_import_batches otherBatch ON otherBatch.id = other.batchId
            WHERE other.eventId = link.eventId AND other.batchId != link.batchId
                AND otherBatch.sourceProfileId != batch.sourceProfileId
        )
    """)
    suspend fun countEventsObservedByOtherSourceProfilesForBatch(batchId: Long): Long
    @Query("SELECT * FROM listening_import_batch_events WHERE batchId IN (SELECT id FROM listening_import_batches WHERE status = 'published') ORDER BY batchId, eventId") suspend fun getPublishedForBackup(): List<ListeningImportBatchEventEntity>
    @Query("DELETE FROM listening_import_batch_events WHERE batchId = :batchId") suspend fun deleteForBatch(batchId: Long)
    @Query("DELETE FROM listening_import_batch_events") suspend fun deleteAll()
}
