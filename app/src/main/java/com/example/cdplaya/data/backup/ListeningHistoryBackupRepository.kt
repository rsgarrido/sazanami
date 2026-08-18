package com.example.cdplaya.data.backup

import androidx.room.withTransaction
import com.example.cdplaya.data.local.AppDatabase
import com.example.cdplaya.data.local.LegacyListeningBaselineEntity
import com.example.cdplaya.data.local.ListeningEndReason
import com.example.cdplaya.data.local.ListeningEventEntity
import com.example.cdplaya.data.local.ListeningQualificationReason
import com.example.cdplaya.data.local.ListeningSource
import com.example.cdplaya.data.local.ListeningTrackIdentityEntity
import com.example.cdplaya.data.local.LocalTrackBindingEntity
import com.example.cdplaya.data.local.SongRatingEntity
import com.example.cdplaya.data.local.ListeningTimestampEvidence
import com.example.cdplaya.data.local.ListeningQualificationPolicy
import com.example.cdplaya.data.local.ListeningCompletionClassification
import com.example.cdplaya.data.local.ListeningEventPublicationState
import com.example.cdplaya.data.local.ListeningImportBatchStatus
import com.example.cdplaya.data.local.ImportedListeningSkippedState
import com.example.cdplaya.data.local.ImportedListeningMatchDisposition
import com.example.cdplaya.data.local.ListeningImportSourceEntity
import com.example.cdplaya.data.local.ListeningImportBatchEntity
import com.example.cdplaya.data.local.ListeningTrackExternalIdEntity
import com.example.cdplaya.data.local.ImportedListeningEventEvidenceEntity
import com.example.cdplaya.data.local.ListeningImportBatchEventEntity

data class CanonicalHistoryAndRatingsBackup(
    val history: BackupListeningHistoryV2,
    val ratings: BackupSongRatings
)

class ListeningHistoryBackupRepository(
    private val database: AppDatabase
) {
    suspend fun exportWithRatings(): CanonicalHistoryAndRatingsBackup = database.withTransaction {
        val history = export()
        val exportedIdentityIds = history.identities.mapTo(HashSet()) { it.backupIdentityId }
        val entries = database.songRatingDao().getAllForBackup().map { entity ->
            require(entity.trackIdentityId in exportedIdentityIds) {
                "A song rating references an identity absent from canonical history export."
            }
            BackupSongRating(
                trackIdentityBackupId = entity.trackIdentityId,
                rating = entity.rating,
                ratedAt = entity.ratedAt,
                updatedAt = entity.updatedAt
            )
        }
        CanonicalHistoryAndRatingsBackup(
            history = history,
            ratings = BackupSongRatings(entries = entries)
        )
    }

    suspend fun export(): BackupListeningHistoryV2 = database.withTransaction {
        val eventEntities = buildList {
            var offset = 0
            do {
                val page = database.listeningEventDao().getBackupPage(EVENT_PAGE_SIZE, offset)
                addAll(page)
                offset += page.size
            } while (page.size == EVENT_PAGE_SIZE)
        }
        val bindingEntities = database.localTrackBindingDao().getAllForBackup()
        val baselineEntities = database.legacyListeningBaselineDao().getAllForBackup()
        val ratedIdentityIds = database.songRatingDao().getAllForBackup()
            .mapTo(HashSet()) { it.trackIdentityId }
        val retainedIdentityIds = buildSet {
            eventEntities.mapTo(this) { it.trackIdentityId }
            bindingEntities.mapTo(this) { it.trackIdentityId }
            baselineEntities.mapTo(this) { it.trackIdentityId }
            addAll(ratedIdentityIds)
        }
        val identities = database.listeningTrackIdentityDao().getAll()
            .filter { it.id in retainedIdentityIds }
            .map { entity ->
            BackupListeningTrackIdentity(
                backupIdentityId = entity.id,
                titleSnapshot = entity.titleSnapshot,
                artistSnapshot = entity.artistSnapshot,
                albumSnapshot = entity.albumSnapshot,
                albumArtistSnapshot = entity.albumArtistSnapshot,
                durationMsSnapshot = entity.durationMsSnapshot,
                normalizedTitle = entity.normalizedTitle,
                normalizedArtist = entity.normalizedArtist,
                normalizedAlbum = entity.normalizedAlbum,
                metadataKey = entity.metadataKey,
                metadataKeyVersion = entity.metadataKeyVersion,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt
            )
        }
        val bindings = bindingEntities.map { entity ->
            BackupLocalTrackBinding(
                backupBindingId = entity.id,
                trackIdentityBackupId = entity.trackIdentityId,
                referenceKey = entity.referenceKey,
                mediaStoreId = entity.mediaStoreId,
                volumeName = entity.volumeName,
                contentUri = entity.contentUri,
                relativePath = entity.relativePath,
                displayName = entity.displayName,
                absolutePath = entity.absolutePath,
                fileSizeBytes = entity.fileSizeBytes,
                dateModifiedEpochSeconds = entity.dateModifiedEpochSeconds,
                durationMsSnapshot = entity.durationMsSnapshot,
                legacyStableKey = entity.legacyStableKey,
                portableKey = entity.portableKey,
                portableKeyVersion = entity.portableKeyVersion,
                firstSeenAt = entity.firstSeenAt,
                lastSeenAt = entity.lastSeenAt,
                missingSince = entity.missingSince
            )
        }
        val baselines = baselineEntities.map { entity ->
            BackupLegacyListeningBaseline(
                trackIdentityBackupId = entity.trackIdentityId,
                historicalPlayCount = entity.historicalPlayCount,
                firstKnownPlayedAt = entity.firstKnownPlayedAt,
                lastKnownPlayedAt = entity.lastKnownPlayedAt,
                legacyReferenceKey = entity.legacyReferenceKey,
                migratedAt = entity.migratedAt
            )
        }
        val events = eventEntities.map(ListeningEventEntity::toBackup)
        val eventIds = eventEntities.mapTo(HashSet()) { it.id }
        val eventUuidsById = eventEntities.associate { it.id to it.eventUuid }
        val batchEntities = database.listeningImportBatchDao().getPublishedForBackup()
        val evidenceEntities = database.importedListeningEventEvidenceDao().getAllForBackup()
        val retainedSourceIds = buildSet {
            batchEntities.mapTo(this) { it.sourceProfileId }
            evidenceEntities.mapTo(this) { it.sourceProfileId }
        }
        val importSources = database.listeningImportSourceDao().getAllForBackup()
            .filter { it.id in retainedSourceIds }
            .map { entity ->
            BackupListeningImportSource(entity.id, entity.stableUuid, entity.sourceType.storageValue,
                entity.displayLabel, entity.accountIdentityDigest, entity.createdAt, entity.updatedAt)
        }
        val importBatches = batchEntities.map { entity ->
            BackupListeningImportBatch(entity.id, entity.stableUuid, entity.sourceProfileId,
                entity.status.storageValue, entity.parserVersion, entity.qualificationPolicy.storageValue,
                entity.qualificationRuleVersion, entity.startedAt, entity.completedAt,
                entity.sourceRangeStart, entity.sourceRangeEnd, entity.parsedCount, entity.insertedCount,
                entity.duplicateCount, entity.ignoredCount, entity.invalidCount, entity.exactMatchCount,
                entity.ambiguousMatchCount, entity.unmatchedCount, entity.qualifiedCount,
                entity.failureCategory, entity.createdAppVersion)
        }
        val externalTrackIds = database.listeningTrackExternalIdDao().getAllForBackup()
            .filter { it.trackIdentityId in retainedIdentityIds }
            .map { entity ->
            BackupListeningTrackExternalId(entity.trackIdentityId, entity.sourceType.storageValue,
                entity.externalId, entity.createdAt, entity.lastSeenAt)
        }
        val evidence = evidenceEntities.map { entity ->
            BackupImportedListeningEventEvidence(requireNotNull(eventUuidsById[entity.eventId]),
                entity.sourceProfileId, entity.fingerprintVersion, entity.fingerprint,
                entity.duplicateOrdinal, entity.normalizedReasonStart, entity.normalizedReasonEnd,
                entity.skippedState.storageValue, entity.matchDispositionAtImport.storageValue)
        }
        val observations = database.listeningImportBatchEventDao().getPublishedForBackup()
            .filter { it.eventId in eventIds }
            .map { entity ->
            BackupListeningImportBatchEvent(entity.batchId, requireNotNull(eventUuidsById[entity.eventId]))
        }
        BackupListeningHistoryV2(
            identities = identities,
            bindings = bindings,
            baselines = baselines,
            events = events,
            importSources = importSources,
            importBatches = importBatches,
            externalTrackIds = externalTrackIds,
            importedEventEvidence = evidence,
            batchEventObservations = observations
        ).let { history -> history.copy(summary = history.recordsSummary()) }
    }

    suspend fun restore(history: BackupListeningHistoryV2) {
        val validated = ListeningHistoryBackupValidator.validate(history)
        database.withTransaction {
            restoreValidatedWithinTransaction(validated)
        }
    }

    suspend fun restoreValidatedWithinTransaction(
        history: BackupListeningHistoryV2
    ): Map<Long, Long> {
        database.listeningImportBatchEventDao().deleteAll()
        database.importedListeningEventEvidenceDao().deleteAll()
        database.listeningImportBatchDao().deleteAll()
        database.listeningTrackExternalIdDao().deleteAll()
        database.listeningImportSourceDao().deleteAll()
        database.listeningEventDao().deleteAll()
        database.legacyListeningBaselineDao().deleteAll()
        database.localTrackBindingDao().deleteAll()
        database.listeningTrackIdentityDao().deleteAll()

        val identityIds = HashMap<Long, Long>(history.identities.size)
        history.identities.forEach { backup ->
            val restoredId = database.listeningTrackIdentityDao().insert(
                ListeningTrackIdentityEntity(
                    titleSnapshot = backup.titleSnapshot,
                    artistSnapshot = backup.artistSnapshot,
                    albumSnapshot = backup.albumSnapshot,
                    albumArtistSnapshot = backup.albumArtistSnapshot,
                    durationMsSnapshot = backup.durationMsSnapshot,
                    normalizedTitle = backup.normalizedTitle,
                    normalizedArtist = backup.normalizedArtist,
                    normalizedAlbum = backup.normalizedAlbum,
                    metadataKey = backup.metadataKey,
                    metadataKeyVersion = backup.metadataKeyVersion,
                    createdAt = backup.createdAt,
                    updatedAt = backup.updatedAt
                )
            )
            identityIds[backup.backupIdentityId] = restoredId
        }

        val sourceProfileIds = HashMap<Long, Long>(history.importSources.size)
        history.importSources.forEach { backup ->
            sourceProfileIds[backup.backupSourceProfileId] = database.listeningImportSourceDao().insert(
                ListeningImportSourceEntity(stableUuid = backup.stableUuid,
                    sourceType = ListeningSource.fromStorageValue(backup.sourceType),
                    displayLabel = backup.displayLabel, accountIdentityDigest = backup.accountIdentityDigest,
                    createdAt = backup.createdAt, updatedAt = backup.updatedAt)
            )
        }

        val batchIds = HashMap<Long, Long>(history.importBatches.size)
        history.importBatches.forEach { backup ->
            batchIds[backup.backupBatchId] = database.listeningImportBatchDao().insert(
                ListeningImportBatchEntity(stableUuid = backup.stableUuid,
                    sourceProfileId = sourceProfileIds.getValue(backup.sourceProfileBackupId),
                    status = ListeningImportBatchStatus.fromStorageValue(backup.status),
                    parserVersion = backup.parserVersion,
                    qualificationPolicy = ListeningQualificationPolicy.fromStorageValue(backup.qualificationPolicy),
                    qualificationRuleVersion = backup.qualificationRuleVersion, startedAt = backup.startedAt,
                    completedAt = backup.completedAt, sourceRangeStart = backup.sourceRangeStart,
                    sourceRangeEnd = backup.sourceRangeEnd, parsedCount = backup.parsedCount,
                    insertedCount = backup.insertedCount, duplicateCount = backup.duplicateCount,
                    ignoredCount = backup.ignoredCount, invalidCount = backup.invalidCount,
                    exactMatchCount = backup.exactMatchCount, ambiguousMatchCount = backup.ambiguousMatchCount,
                    unmatchedCount = backup.unmatchedCount, qualifiedCount = backup.qualifiedCount,
                    failureCategory = backup.failureCategory, createdAppVersion = backup.createdAppVersion)
            )
        }

        val bindingIds = HashMap<Long, Long>(history.bindings.size)
        history.bindings.forEach { backup ->
            val restoredId = database.localTrackBindingDao().insert(
                LocalTrackBindingEntity(
                    trackIdentityId = identityIds.getValue(backup.trackIdentityBackupId),
                    referenceKey = backup.referenceKey,
                    mediaStoreId = backup.mediaStoreId,
                    volumeName = backup.volumeName,
                    contentUri = backup.contentUri,
                    relativePath = backup.relativePath,
                    displayName = backup.displayName,
                    absolutePath = backup.absolutePath,
                    fileSizeBytes = backup.fileSizeBytes,
                    dateModifiedEpochSeconds = backup.dateModifiedEpochSeconds,
                    durationMsSnapshot = backup.durationMsSnapshot,
                    legacyStableKey = backup.legacyStableKey,
                    portableKey = backup.portableKey,
                    portableKeyVersion = backup.portableKeyVersion,
                    firstSeenAt = backup.firstSeenAt,
                    lastSeenAt = backup.lastSeenAt,
                    missingSince = backup.missingSince
                )
            )
            bindingIds[backup.backupBindingId] = restoredId
        }

        history.baselines.chunked(RESTORE_BATCH_SIZE).forEach { batch ->
            database.legacyListeningBaselineDao().insert(batch.map { backup ->
                LegacyListeningBaselineEntity(
                    trackIdentityId = identityIds.getValue(backup.trackIdentityBackupId),
                    historicalPlayCount = backup.historicalPlayCount,
                    firstKnownPlayedAt = backup.firstKnownPlayedAt,
                    lastKnownPlayedAt = backup.lastKnownPlayedAt,
                    legacyReferenceKey = backup.legacyReferenceKey,
                    migratedAt = backup.migratedAt
                )
            })
        }
        history.externalTrackIds.chunked(RESTORE_BATCH_SIZE).forEach { batch ->
            batch.forEach { backup -> database.listeningTrackExternalIdDao().insert(
                ListeningTrackExternalIdEntity(trackIdentityId = identityIds.getValue(backup.trackIdentityBackupId),
                    sourceType = ListeningSource.fromStorageValue(backup.sourceType), externalId = backup.externalId,
                    createdAt = backup.createdAt, lastSeenAt = backup.lastSeenAt)
            ) }
        }
        val eventIds = HashMap<String, Long>(history.events.size)
        history.events.chunked(RESTORE_BATCH_SIZE).forEach { batch ->
            val entities = batch.map { backup ->
                ListeningEventEntity(
                    eventUuid = backup.eventUuid,
                    source = ListeningSource.fromStorageValue(backup.source),
                    trackIdentityId = identityIds.getValue(backup.trackIdentityBackupId),
                    localTrackBindingId = backup.localTrackBindingBackupId?.let(bindingIds::getValue),
                    playbackSessionId = backup.playbackSessionId,
                    startedAt = backup.startedAt,
                    endedAt = backup.endedAt,
                    attributionAt = backup.attributionAt,
                    timestampEvidence = ListeningTimestampEvidence.fromStorageValue(backup.timestampEvidence),
                    listenedMs = backup.listenedMs,
                    trackDurationMs = backup.trackDurationMs,
                    qualifiedAsPlay = backup.qualifiedAsPlay,
                    qualificationReason = ListeningQualificationReason.fromStorageValue(
                        backup.qualificationReason
                    ),
                    qualificationRuleVersion = backup.qualificationRuleVersion,
                    qualificationPolicy = ListeningQualificationPolicy.fromStorageValue(backup.qualificationPolicy),
                    endReason = backup.endReason?.let(ListeningEndReason::fromStorageValue),
                    completionClassification = ListeningCompletionClassification.fromStorageValue(backup.completionClassification),
                    publicationState = ListeningEventPublicationState.fromStorageValue(backup.publicationState),
                    sourceEventKey = backup.sourceEventKey,
                    importBatchId = backup.importBatchId,
                    createdAt = backup.createdAt
                )
            }
            val restoredIds = database.listeningEventDao().insert(entities)
            batch.zip(restoredIds).forEach { (backup, id) -> eventIds[backup.eventUuid] = id }
        }
        history.importedEventEvidence.chunked(RESTORE_BATCH_SIZE).forEach { batch ->
            batch.forEach { backup -> database.importedListeningEventEvidenceDao().insert(
                ImportedListeningEventEvidenceEntity(eventId = eventIds.getValue(backup.eventUuid),
                    sourceProfileId = sourceProfileIds.getValue(backup.sourceProfileBackupId),
                    fingerprintVersion = backup.fingerprintVersion, fingerprint = backup.fingerprint,
                    duplicateOrdinal = backup.duplicateOrdinal, normalizedReasonStart = backup.normalizedReasonStart,
                    normalizedReasonEnd = backup.normalizedReasonEnd,
                    skippedState = ImportedListeningSkippedState.fromStorageValue(backup.skippedState),
                    matchDispositionAtImport = ImportedListeningMatchDisposition.fromStorageValue(backup.matchDispositionAtImport))
            ) }
        }
        history.batchEventObservations.chunked(RESTORE_BATCH_SIZE).forEach { batch ->
            database.listeningImportBatchEventDao().insert(batch.map { backup ->
                ListeningImportBatchEventEntity(batchIds.getValue(backup.batchBackupId), eventIds.getValue(backup.eventUuid))
            })
        }
        return identityIds
    }

    suspend fun restoreRatingsValidatedWithinTransaction(
        ratings: BackupSongRatings,
        identityIds: Map<Long, Long>
    ) {
        database.songRatingDao().deleteAll()
        ratings.entries.chunked(RESTORE_BATCH_SIZE).forEach { batch ->
            database.songRatingDao().insert(batch.map { backup ->
                SongRatingEntity(
                    trackIdentityId = identityIds.getValue(backup.trackIdentityBackupId),
                    rating = backup.rating,
                    ratedAt = backup.ratedAt,
                    updatedAt = backup.updatedAt
                )
            })
        }
    }

    companion object {
        const val EVENT_PAGE_SIZE = 1_000
        const val RESTORE_BATCH_SIZE = 500
    }
}

private fun ListeningEventEntity.toBackup() = BackupListeningEvent(
    eventUuid = eventUuid,
    source = source.storageValue,
    trackIdentityBackupId = trackIdentityId,
    localTrackBindingBackupId = localTrackBindingId,
    playbackSessionId = playbackSessionId,
    startedAt = startedAt,
    endedAt = endedAt,
    attributionAt = attributionAt,
    timestampEvidence = timestampEvidence.storageValue,
    listenedMs = listenedMs,
    trackDurationMs = trackDurationMs,
    qualifiedAsPlay = qualifiedAsPlay,
    qualificationReason = qualificationReason.storageValue,
    qualificationRuleVersion = qualificationRuleVersion,
    qualificationPolicy = qualificationPolicy.storageValue,
    endReason = endReason?.storageValue,
    completionClassification = completionClassification.storageValue,
    publicationState = publicationState.storageValue,
    sourceEventKey = sourceEventKey,
    importBatchId = importBatchId,
    createdAt = createdAt
)
