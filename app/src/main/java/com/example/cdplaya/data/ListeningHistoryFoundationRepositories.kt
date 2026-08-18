package com.example.cdplaya.data

import androidx.room.withTransaction
import com.example.cdplaya.data.importing.ImportOccurrenceDecision
import com.example.cdplaya.data.importing.ImportOccurrenceKey
import com.example.cdplaya.data.importing.ListeningImportDedupePlan
import com.example.cdplaya.data.importing.ListeningImportDedupePlanner
import com.example.cdplaya.data.importing.ListeningImportSelectionPlan
import com.example.cdplaya.data.importing.ListeningImportChunkResult
import com.example.cdplaya.data.importing.ListeningImportIdentityResolution
import com.example.cdplaya.data.importing.PreparedListeningOccurrence
import com.example.cdplaya.data.importing.spotify.SpotifyImportedEventMapper
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
import com.example.cdplaya.data.local.ImportedListeningMatchDisposition
import com.example.cdplaya.data.local.requireCompatibleWith
import com.example.cdplaya.data.local.requireSupportedExternalSource
import com.example.cdplaya.data.local.requireSupportedImportSource
import com.example.cdplaya.data.local.requireSupportedSemantics
import java.util.UUID

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
class ListeningImportRepository(
    private val database: AppDatabase,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val eventUuid: () -> String = { UUID.randomUUID().toString() }
) {
    suspend fun createSourceProfile(source: ListeningImportSourceEntity): Long {
        source.requireSupportedImportSource()
        return database.listeningImportSourceDao().insert(source)
    }

    suspend fun getSourceProfile(stableUuid: String): ListeningImportSourceEntity? =
        database.listeningImportSourceDao().getByStableUuid(stableUuid)

    /** Internal recovery surface for unfinished batches; IDs never leave the controller layer. */
    suspend fun getPendingBatchIdsForSourceProfile(sourceProfileId: Long): List<Long> {
        require(sourceProfileId > 0)
        return database.listeningImportBatchDao().getPendingIdsForSourceProfile(sourceProfileId)
    }

    /** Race-safe source-profile reuse by caller-owned stable UUID. */
    suspend fun getOrCreateSourceProfile(
        source: ListeningImportSourceEntity
    ): ListeningImportSourceEntity {
        source.requireSupportedImportSource()
        require(source.id == 0L) { "A new source-profile template cannot already have a database ID." }
        require(source.stableUuid.isNotBlank()) { "Source profile stable UUID cannot be blank." }
        return database.withTransaction {
            val dao = database.listeningImportSourceDao()
            dao.getByStableUuid(source.stableUuid)?.also {
                require(it.sourceType == source.sourceType) {
                    "The source profile stable UUID belongs to another provider."
                }
                return@withTransaction it
            }
            dao.insertIgnoringConflict(source)
            requireNotNull(dao.getByStableUuid(source.stableUuid)).also {
                require(it.sourceType == source.sourceType) {
                    "The source profile stable UUID belongs to another provider."
                }
            }
        }
    }

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

    /**
     * Rechecks dedupe evidence and persists all dependent state in one bounded transaction.
     * A pending occurrence from another unfinished batch is rejected instead of being published.
     */
    suspend fun persistSpotifyChunk(
        batchId: Long,
        occurrences: List<PreparedListeningOccurrence>
    ): ListeningImportChunkResult {
        require(occurrences.isNotEmpty())
        require(occurrences.size <= ListeningImportDedupePlanner.MAX_LOOKUP_BATCH_SIZE)
        require(occurrences.map { it.key }.toSet().size == occurrences.size) {
            "An import chunk cannot contain duplicate occurrence keys."
        }
        return database.withTransaction {
            val batch = requireNotNull(database.listeningImportBatchDao().getById(batchId))
            require(batch.status == ListeningImportBatchStatus.PENDING) { "Import batch is not pending." }
            val source = requireNotNull(database.listeningImportSourceDao().getById(batch.sourceProfileId))
            batch.requireCompatibleWith(source)
            require(source.sourceType == ListeningSource.SPOTIFY_IMPORT)

            val requestedByKey = occurrences.associateBy(PreparedListeningOccurrence::key)
            val existingEvidence = occurrences.groupBy { it.key.fingerprintVersion }
                .flatMap { (version, values) ->
                    database.importedListeningEventEvidenceDao().findEvidenceForFingerprints(
                        sourceProfileId = source.id,
                        fingerprintVersion = version,
                        fingerprints = values.map { it.key.fingerprint }.distinct()
                    )
                }.filter { evidence ->
                    ImportOccurrenceKey(
                        evidence.fingerprintVersion,
                        evidence.fingerprint,
                        evidence.duplicateOrdinal
                    ) in requestedByKey
                }
            val evidenceByKey = existingEvidence.associateBy { evidence ->
                ImportOccurrenceKey(evidence.fingerprintVersion, evidence.fingerprint, evidence.duplicateOrdinal)
            }
            val existingEvents = if (existingEvidence.isEmpty()) emptyMap() else {
                database.listeningEventDao().getByIds(existingEvidence.map { it.eventId })
                    .associateBy { it.id }
            }
            existingEvidence.forEach { evidence ->
                val existingEvent = requireNotNull(existingEvents[evidence.eventId])
                require(existingEvent.publicationState != com.example.cdplaya.data.local.ListeningEventPublicationState.IMPORT_PENDING) {
                    "An occurrence is owned by an unfinished import batch."
                }
                require(existingEvent.source == ListeningSource.SPOTIFY_IMPORT)
            }

            val newOccurrences = occurrences.filter { it.key !in evidenceByKey }
            val externalIds = newOccurrences.mapNotNull { it.record.externalMediaId }.distinct()
            val externalDao = database.listeningTrackExternalIdDao()
            val externalMappings = if (externalIds.isEmpty()) emptyMap() else {
                externalDao.findAll(ListeningSource.SPOTIFY_IMPORT, externalIds)
                    .associateBy { it.externalId }.toMutableMap()
            }
            if (externalIds.isNotEmpty()) {
                externalDao.updateLastSeen(ListeningSource.SPOTIFY_IMPORT, externalIds, nowMillis())
            }

            val missingExternalIds = externalIds.filterNot { it in externalMappings }
            val firstByMissingExternalId = missingExternalIds.associateWith { externalId ->
                newOccurrences.first { it.record.externalMediaId == externalId }
            }
            val identityRequests = buildList {
                missingExternalIds.forEach { externalId ->
                    add(IdentityRequest(externalId, firstByMissingExternalId.getValue(externalId)))
                }
                newOccurrences.filter { it.record.externalMediaId == null }.forEach { occurrence ->
                    add(IdentityRequest(null, occurrence))
                }
            }
            val now = nowMillis()
            val identityIds = if (identityRequests.isEmpty()) emptyList() else {
                database.listeningTrackIdentityDao().insert(identityRequests.map { request ->
                    request.occurrence.toHistoricalIdentity(now)
                })
            }
            val newIdentityByExternalId = mutableMapOf<String, Long>()
            val uriLessIdentityByKey = mutableMapOf<ImportOccurrenceKey, Long>()
            identityRequests.zip(identityIds).forEach { (request, identityId) ->
                if (request.externalId == null) {
                    uriLessIdentityByKey[request.occurrence.key] = identityId
                } else {
                    newIdentityByExternalId[request.externalId] = identityId
                }
            }
            if (newIdentityByExternalId.isNotEmpty()) {
                externalDao.insert(newIdentityByExternalId.map { (externalId, identityId) ->
                    ListeningTrackExternalIdEntity(
                        trackIdentityId = identityId,
                        sourceType = ListeningSource.SPOTIFY_IMPORT,
                        externalId = externalId,
                        createdAt = now,
                        lastSeenAt = now
                    )
                })
            }

            val resolutions = newOccurrences.map { occurrence ->
                val externalId = occurrence.record.externalMediaId
                when {
                    externalId == null -> ListeningImportIdentityResolution(
                        requireNotNull(uriLessIdentityByKey[occurrence.key]),
                        ImportedListeningMatchDisposition.CREATED_HISTORICAL_IDENTITY
                    )
                    externalId in externalMappings -> ListeningImportIdentityResolution(
                        externalMappings.getValue(externalId).trackIdentityId,
                        ImportedListeningMatchDisposition.EXACT_EXTERNAL_ID
                    )
                    else -> ListeningImportIdentityResolution(
                        requireNotNull(newIdentityByExternalId[externalId]),
                        ImportedListeningMatchDisposition.CREATED_HISTORICAL_IDENTITY
                    )
                }
            }
            val eventIds = if (newOccurrences.isEmpty()) emptyList() else {
                database.listeningEventDao().insert(newOccurrences.zip(resolutions).map { (occurrence, resolution) ->
                    SpotifyImportedEventMapper.map(occurrence, resolution.trackIdentityId, eventUuid(), now)
                        .also { it.requireSupportedSemantics() }
                })
            }
            if (eventIds.isNotEmpty()) {
                database.importedListeningEventEvidenceDao().insert(
                    newOccurrences.zip(resolutions).zip(eventIds).map { (prepared, eventId) ->
                        val (occurrence, resolution) = prepared
                        ImportedListeningEventEvidenceEntity(
                            eventId = eventId,
                            sourceProfileId = source.id,
                            fingerprintVersion = occurrence.key.fingerprintVersion,
                            fingerprint = occurrence.key.fingerprint,
                            duplicateOrdinal = occurrence.key.duplicateOrdinal,
                            normalizedReasonStart = occurrence.policy.normalizedReasonStart,
                            normalizedReasonEnd = occurrence.policy.normalizedReasonEnd,
                            skippedState = occurrence.policy.skippedState,
                            matchDispositionAtImport = resolution.disposition
                        )
                    }
                )
            }
            val newEventIdByKey = newOccurrences.map(PreparedListeningOccurrence::key)
                .zip(eventIds).toMap()
            val observedEventIds = occurrences.map { occurrence ->
                evidenceByKey[occurrence.key]?.eventId
                    ?: requireNotNull(newEventIdByKey[occurrence.key])
            }
            database.listeningImportBatchEventDao().insert(
                observedEventIds.map { ListeningImportBatchEventEntity(batchId, it) }
            )

            val exactMatches = resolutions.count {
                it.disposition == ImportedListeningMatchDisposition.EXACT_EXTERNAL_ID
            }
            val historicalOccurrences = resolutions.size - exactMatches
            val qualified = newOccurrences.count { it.policy.qualifiedAsPlay }
            check(database.listeningImportBatchDao().addProgress(
                id = batchId,
                inserted = newOccurrences.size.toLong(),
                duplicates = existingEvidence.size.toLong(),
                exactMatches = exactMatches.toLong(),
                historicalCreated = historicalOccurrences.toLong(),
                qualified = qualified.toLong()
            ) == 1)
            ListeningImportChunkResult(
                selectedOccurrences = occurrences.size,
                alreadyImported = existingEvidence.size,
                newPending = newOccurrences.size,
                exactExternalIdMatches = exactMatches,
                historicalIdentitiesCreated = identityRequests.size,
                qualifiedNewOccurrences = qualified
            )
        }
    }

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

    /**
     * Looks up a bounded key set using one indexed IN query per fingerprint version. Callers must
     * keep [keys] within SQLite's bind-variable limit; [planDedupe] uses batches of 500.
     */
    suspend fun findExistingOccurrenceKeys(
        sourceProfileId: Long,
        keys: List<ImportOccurrenceKey>
    ): Set<ImportOccurrenceKey> {
        require(keys.size <= ListeningImportDedupePlanner.MAX_LOOKUP_BATCH_SIZE)
        val source = requireNotNull(database.listeningImportSourceDao().getById(sourceProfileId)) {
            "Import source profile does not exist."
        }
        source.requireSupportedImportSource()
        return queryExistingOccurrenceKeys(sourceProfileId, keys)
    }

    suspend fun planDedupe(
        sourceProfileId: Long,
        selection: ListeningImportSelectionPlan,
        onDecision: suspend (ImportOccurrenceDecision) -> Unit = {}
    ): ListeningImportDedupePlan {
        val source = requireNotNull(database.listeningImportSourceDao().getById(sourceProfileId)) {
            "Import source profile does not exist."
        }
        source.requireSupportedImportSource()
        return ListeningImportDedupePlanner().plan(
            sourceProfileId = sourceProfileId,
            selection = selection,
            findExisting = { profileId, keys -> queryExistingOccurrenceKeys(profileId, keys) },
            onDecision = onDecision
        )
    }

    private suspend fun queryExistingOccurrenceKeys(
        sourceProfileId: Long,
        keys: List<ImportOccurrenceKey>
    ): Set<ImportOccurrenceKey> {
        if (keys.isEmpty()) return emptySet()
        val requested = keys.toHashSet()
        return keys.groupBy(ImportOccurrenceKey::fingerprintVersion).flatMap { (version, versionKeys) ->
            database.importedListeningEventEvidenceDao().findOccurrenceKeys(
                sourceProfileId = sourceProfileId,
                fingerprintVersion = version,
                fingerprints = versionKeys.map(ImportOccurrenceKey::fingerprint).distinct()
            ).map { row ->
                ImportOccurrenceKey(row.fingerprintVersion, row.fingerprint, row.duplicateOrdinal)
            }
        }.filterTo(mutableSetOf()) { it in requested }
    }

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
        val candidateIdentityIds = database.listeningEventDao().getPendingTrackIdentityIdsForBatch(batchId)
        val deleted = database.listeningEventDao().deleteUnsharedPendingForBatch(batchId)
        database.listeningImportBatchEventDao().deleteForBatch(batchId)
        candidateIdentityIds.chunked(ListeningImportDedupePlanner.MAX_LOOKUP_BATCH_SIZE).forEach {
            database.listeningTrackIdentityDao().deleteUnreferenced(it)
        }
        database.listeningTrackIdentityDao().deleteAllUnreferenced()
        check(database.listeningImportBatchDao().cancel(batchId, completedAt) == 1)
        deleted
    }

    suspend fun failPendingBatch(
        batchId: Long,
        completedAt: Long,
        failureCategory: String
    ): Int = database.withTransaction {
        require(failureCategory.matches(Regex("[a-z_]{1,64}")))
        val batch = requireNotNull(database.listeningImportBatchDao().getById(batchId))
        require(batch.status == ListeningImportBatchStatus.PENDING) { "Import batch is not pending." }
        val candidateIdentityIds = database.listeningEventDao().getPendingTrackIdentityIdsForBatch(batchId)
        val deleted = database.listeningEventDao().deleteUnsharedPendingForBatch(batchId)
        database.listeningImportBatchEventDao().deleteForBatch(batchId)
        candidateIdentityIds.chunked(ListeningImportDedupePlanner.MAX_LOOKUP_BATCH_SIZE).forEach {
            database.listeningTrackIdentityDao().deleteUnreferenced(it)
        }
        database.listeningTrackIdentityDao().deleteAllUnreferenced()
        check(database.listeningImportBatchDao().fail(batchId, completedAt, failureCategory) == 1)
        deleted
    }
}

private data class IdentityRequest(
    val externalId: String?,
    val occurrence: PreparedListeningOccurrence
)

private fun PreparedListeningOccurrence.toHistoricalIdentity(now: Long): ListeningTrackIdentityEntity {
    val title = record.trackTitle.orEmpty()
    val artist = record.trackArtist.orEmpty()
    val album = record.albumTitle.orEmpty()
    return ListeningTrackIdentityEntity(
        titleSnapshot = title,
        artistSnapshot = artist,
        albumSnapshot = album,
        albumArtistSnapshot = record.albumArtist,
        durationMsSnapshot = null,
        normalizedTitle = title.identityNormalized(),
        normalizedArtist = artist.identityNormalized(),
        normalizedAlbum = album.identityNormalized(),
        metadataKey = null,
        metadataKeyVersion = SongIdentity.PORTABLE_KEY_VERSION,
        createdAt = now,
        updatedAt = now
    )
}
