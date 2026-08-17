package com.example.cdplaya.data

import androidx.room.withTransaction
import com.example.cdplaya.data.listening.FinalizedListeningEventDraft
import com.example.cdplaya.data.local.AppDatabase
import com.example.cdplaya.data.local.LegacyListeningBaselineDao
import com.example.cdplaya.data.local.LegacyListeningBaselineEntity
import com.example.cdplaya.data.local.ListeningEventDao
import com.example.cdplaya.data.local.ListeningEventEntity
import com.example.cdplaya.data.local.ListeningTrackIdentityDao
import com.example.cdplaya.data.local.ListeningTrackIdentityEntity
import com.example.cdplaya.data.local.LocalTrackBindingDao
import com.example.cdplaya.data.local.LocalTrackBindingEntity
import com.example.cdplaya.data.local.toEntity
import com.example.cdplaya.data.local.ListeningImportSourceEntity
import com.example.cdplaya.data.local.ListeningImportBatchEntity
import com.example.cdplaya.data.local.ListeningImportBatchStatus
import com.example.cdplaya.data.local.ListeningTrackExternalIdEntity
import com.example.cdplaya.data.local.ImportedListeningEventEvidenceEntity
import com.example.cdplaya.data.local.ListeningImportBatchEventEntity
import com.example.cdplaya.data.local.ListeningSource
import com.example.cdplaya.data.local.requireCompatibleWith
import com.example.cdplaya.data.local.requireSupportedExternalSource
import com.example.cdplaya.data.local.requireSupportedImportSource
import com.example.cdplaya.data.local.requireSupportedSemantics

class ListeningTrackIdentityRepository(
    private val identityDao: ListeningTrackIdentityDao,
    private val bindingDao: LocalTrackBindingDao
) {
    suspend fun insertIdentity(identity: ListeningTrackIdentityEntity): Long =
        identityDao.insert(identity)

    suspend fun getIdentity(id: Long): ListeningTrackIdentityEntity? = identityDao.getById(id)

    suspend fun insertLocalBinding(binding: LocalTrackBindingEntity): Long =
        bindingDao.insert(binding)

    suspend fun getLocalBinding(referenceKey: String): LocalTrackBindingEntity? =
        bindingDao.getByReferenceKey(referenceKey)
}

class ListeningEventRepository(
    private val eventDao: ListeningEventDao
) {
    suspend fun insert(event: ListeningEventEntity): Long {
        event.requireSupportedSemantics()
        return eventDao.insert(event)
    }

    /** Returns false when a uniqueness constraint proves this finalized attempt was already stored. */
    suspend fun insertFinalizedDraft(draft: FinalizedListeningEventDraft): Boolean {
        val event = draft.toEntity()
        event.requireSupportedSemantics()
        return eventDao.insertIgnoringConflict(event) != -1L
    }

    suspend fun getByUuid(eventUuid: String): ListeningEventEntity? =
        eventDao.getByUuid(eventUuid)

    suspend fun getByPlaybackSessionId(playbackSessionId: String): ListeningEventEntity? =
        eventDao.getByPlaybackSessionId(playbackSessionId)

    suspend fun count(): Long = eventDao.count()
}

data class NativeListeningTrack(
    val trackIdentityId: Long,
    val localTrackBindingId: Long?
)

/**
 * Resolves only an exact durable local reference key. Metadata is stored as a snapshot but is never
 * used to merge two local bindings, so duplicate-looking files remain separate histories.
 */
class ListeningNativeTrackResolver(
    private val database: AppDatabase,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    suspend fun resolveOrCreate(referenceKey: String, reference: SongReference): NativeListeningTrack {
        require(referenceKey.isNotBlank()) { "Reference key cannot be blank" }
        return database.withTransaction {
            val bindingDao = database.localTrackBindingDao()
            val existing = bindingDao.getByReferenceKey(referenceKey)
            if (existing != null) {
                return@withTransaction NativeListeningTrack(existing.trackIdentityId, existing.id)
            }

            val safeReference = reference.normalizedForPersistence()
            val now = nowMillis()
            val identityId = database.listeningTrackIdentityDao().insert(
                ListeningTrackIdentityEntity(
                    titleSnapshot = safeReference.title,
                    artistSnapshot = safeReference.artist,
                    albumSnapshot = safeReference.album,
                    albumArtistSnapshot = safeReference.albumArtist.takeIf { it.isNotBlank() },
                    durationMsSnapshot = safeReference.duration.takeIf { it > 0L },
                    normalizedTitle = safeReference.title.identityNormalized(),
                    normalizedArtist = safeReference.artist.identityNormalized(),
                    normalizedAlbum = safeReference.album.identityNormalized(),
                    metadataKey = safeReference.portableKey.takeIf { it.isNotBlank() },
                    metadataKeyVersion = safeReference.portableKeyVersion,
                    createdAt = now,
                    updatedAt = now
                )
            )
            val bindingId = bindingDao.insert(
                LocalTrackBindingEntity(
                    trackIdentityId = identityId,
                    referenceKey = referenceKey,
                    mediaStoreId = safeReference.mediaStoreId,
                    volumeName = safeReference.volumeName.takeIf { it.isNotBlank() },
                    contentUri = safeReference.contentUri.takeIf { it.isNotBlank() },
                    relativePath = safeReference.relativePath.takeIf { it.isNotBlank() },
                    displayName = safeReference.displayName.takeIf { it.isNotBlank() },
                    absolutePath = null,
                    fileSizeBytes = safeReference.fileSizeBytes.takeIf { it > 0L },
                    dateModifiedEpochSeconds = safeReference.dateModifiedEpochSeconds.takeIf { it > 0L },
                    durationMsSnapshot = safeReference.duration.takeIf { it > 0L },
                    legacyStableKey = safeReference.legacyStableKey.takeIf { it.isNotBlank() },
                    portableKey = safeReference.portableKey.takeIf { it.isNotBlank() },
                    portableKeyVersion = safeReference.portableKeyVersion,
                    firstSeenAt = now,
                    lastSeenAt = now,
                    missingSince = null
                )
            )
            NativeListeningTrack(identityId, bindingId)
        }
    }
}

class LegacyListeningBaselineRepository(
    private val baselineDao: LegacyListeningBaselineDao
) {
    suspend fun insert(baseline: LegacyListeningBaselineEntity) = baselineDao.insert(baseline)

    suspend fun getByTrackIdentityId(trackIdentityId: Long): LegacyListeningBaselineEntity? =
        baselineDao.getByTrackIdentityId(trackIdentityId)

    suspend fun count(): Long = baselineDao.count()
}

/** Source-neutral persistence foundation. Parsing and service-specific policy remain outside it. */
class ListeningImportRepository(private val database: AppDatabase) {
    suspend fun createSourceProfile(source: ListeningImportSourceEntity): Long {
        source.requireSupportedImportSource()
        return database.listeningImportSourceDao().insert(source)
    }

    suspend fun getSourceProfile(stableUuid: String): ListeningImportSourceEntity? =
        database.listeningImportSourceDao().getByStableUuid(stableUuid)

    suspend fun createBatch(batch: ListeningImportBatchEntity): Long = database.withTransaction {
        val source = requireNotNull(database.listeningImportSourceDao().getById(batch.sourceProfileId)) {
            "Import batch source profile does not exist."
        }
        batch.requireCompatibleWith(source)
        database.listeningImportBatchDao().insert(batch)
    }

    suspend fun insertEvent(event: ListeningEventEntity): Long {
        require(event.source != ListeningSource.CDPLAYA) { "Import repository accepts imported events only." }
        event.requireSupportedSemantics()
        return database.listeningEventDao().insert(event)
    }

    suspend fun insertExternalId(externalId: ListeningTrackExternalIdEntity): Long {
        externalId.requireSupportedExternalSource()
        return database.listeningTrackExternalIdDao().insert(externalId)
    }

    suspend fun findExternalId(source: com.example.cdplaya.data.local.ListeningSource, externalId: String) =
        database.listeningTrackExternalIdDao().find(source, externalId)

    suspend fun insertEvidence(evidence: ImportedListeningEventEvidenceEntity) = database.withTransaction {
        val source = requireNotNull(database.listeningImportSourceDao().getById(evidence.sourceProfileId)) {
            "Imported evidence source profile does not exist."
        }
        val event = requireNotNull(database.listeningEventDao().getById(evidence.eventId)) {
            "Imported evidence event does not exist."
        }
        source.requireSupportedImportSource()
        event.requireSupportedSemantics()
        require(event.source == source.sourceType) {
            "Imported evidence source profile is incompatible with its event."
        }
        require(database.listeningImportBatchEventDao().countOtherSourceProfilesForEvent(
            evidence.eventId, evidence.sourceProfileId
        ) == 0L) {
            "Imported evidence source profile is incompatible with an observing batch."
        }
        database.importedListeningEventEvidenceDao().insert(evidence)
    }

    suspend fun findEvidence(
        sourceProfileId: Long,
        fingerprintVersion: Int,
        fingerprint: String,
        duplicateOrdinal: Int
    ) = database.importedListeningEventEvidenceDao().find(
        sourceProfileId, fingerprintVersion, fingerprint, duplicateOrdinal
    )

    suspend fun observeEvent(batchId: Long, eventId: Long) = database.withTransaction {
        val batch = requireNotNull(database.listeningImportBatchDao().getById(batchId)) {
            "Import batch does not exist."
        }
        val source = requireNotNull(database.listeningImportSourceDao().getById(batch.sourceProfileId)) {
            "Import batch source profile does not exist."
        }
        val event = requireNotNull(database.listeningEventDao().getById(eventId)) {
            "Observed listening event does not exist."
        }
        batch.requireCompatibleWith(source)
        event.requireSupportedSemantics()
        require(event.source == source.sourceType) {
            "Import batch cannot observe an event from another source."
        }
        require(database.listeningImportBatchEventDao().countOtherSourceProfilesForEvent(
            eventId, source.id
        ) == 0L) {
            "Import batch cannot observe an event owned by another source profile."
        }
        val evidence = database.importedListeningEventEvidenceDao().getByEventId(eventId)
        require(evidence == null || evidence.sourceProfileId == source.id) {
            "Import batch cannot observe evidence from another source profile."
        }
        database.listeningImportBatchEventDao().insert(ListeningImportBatchEventEntity(batchId, eventId))
    }

    suspend fun publishBatch(
        batchId: Long,
        expectedPendingEventCount: Long,
        expectedObservedEventCount: Long,
        completedAt: Long
    ): Int = database.withTransaction {
        val batch = requireNotNull(database.listeningImportBatchDao().getById(batchId))
        require(batch.status == ListeningImportBatchStatus.PENDING) { "Import batch is not pending." }
        val source = requireNotNull(database.listeningImportSourceDao().getById(batch.sourceProfileId)) {
            "Import batch source profile does not exist."
        }
        batch.requireCompatibleWith(source)
        require(database.listeningImportBatchEventDao().countForBatch(batchId) == expectedObservedEventCount) {
            "Import batch observation count changed before publication."
        }
        require(database.listeningEventDao().countPendingForBatch(batchId) == expectedPendingEventCount) {
            "Import batch pending-event count changed before publication."
        }
        require(database.listeningImportBatchEventDao().countIncompatibleEventsForBatch(batchId) == 0L) {
            "Import batch contains an event incompatible with its source profile."
        }
        require(database.listeningImportBatchEventDao().countIncompatibleEvidenceForBatch(batchId) == 0L) {
            "Import batch contains evidence from another source profile."
        }
        require(database.listeningImportBatchEventDao()
            .countEventsObservedByOtherSourceProfilesForBatch(batchId) == 0L) {
            "Import batch contains an event observed by another source profile."
        }
        val published = database.listeningEventDao().publishForBatch(batchId)
        check(published.toLong() == expectedPendingEventCount)
        check(database.listeningImportBatchDao().publish(batchId, completedAt) == 1)
        published
    }

    suspend fun cancelPendingBatch(batchId: Long, completedAt: Long): Int = database.withTransaction {
        val batch = requireNotNull(database.listeningImportBatchDao().getById(batchId))
        require(batch.status == ListeningImportBatchStatus.PENDING) { "Import batch is not pending." }
        val deleted = database.listeningEventDao().deleteUnsharedPendingForBatch(batchId)
        database.listeningImportBatchEventDao().deleteForBatch(batchId)
        check(database.listeningImportBatchDao().cancel(batchId, completedAt) == 1)
        deleted
    }
}
