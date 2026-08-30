package com.example.cdplaya.data.backup

import com.example.cdplaya.data.local.ListeningEndReason
import com.example.cdplaya.data.local.ListeningQualificationReason
import com.example.cdplaya.data.local.ListeningSource
import com.example.cdplaya.data.local.ListeningTimestampEvidence
import com.example.cdplaya.data.local.ListeningQualificationPolicy
import com.example.cdplaya.data.local.ListeningCompletionClassification
import com.example.cdplaya.data.local.ListeningEventPublicationState
import com.example.cdplaya.data.local.ListeningImportBatchStatus
import com.example.cdplaya.data.local.ImportedListeningSkippedState
import com.example.cdplaya.data.local.ImportedListeningMatchDisposition
import com.example.cdplaya.data.local.requiredQualificationPolicy

object ListeningHistoryBackupValidator {
    fun validate(history: BackupListeningHistoryV2): BackupListeningHistoryV2 {
        require(history.formatVersion == BackupListeningHistoryV2.CURRENT_FORMAT_VERSION) {
            "Unsupported listening-history backup format version ${history.formatVersion}."
        }

        val identities = history.identities.associateByUnique(
            BackupListeningTrackIdentity::backupIdentityId,
            "backup identity ID"
        )
        require(identities.keys.none { it <= 0L }) {
            "Listening-history backup identity IDs must be positive."
        }
        history.identities.forEach { identity ->
            require(identity.metadataKeyVersion > 0) {
                "Listening-history metadata-key version is invalid."
            }
            require(identity.durationMsSnapshot == null || identity.durationMsSnapshot >= 0L) {
                "Listening-history identity duration is invalid."
            }
            require(identity.updatedAt >= identity.createdAt) {
                "Listening-history identity timestamps are invalid."
            }
        }

        val bindings = history.bindings.associateByUnique(
            BackupLocalTrackBinding::backupBindingId,
            "backup binding ID"
        )
        require(bindings.keys.none { it <= 0L }) {
            "Listening-history backup binding IDs must be positive."
        }
        require(history.bindings.map { it.referenceKey }.distinct().size == history.bindings.size) {
            "Listening-history binding reference keys must be unique."
        }
        history.bindings.forEach { binding ->
            require(binding.trackIdentityBackupId in identities) {
                "Listening-history binding references a missing identity."
            }
            require(binding.referenceKey.isNotBlank()) {
                "Listening-history binding reference key is invalid."
            }
            require(binding.lastSeenAt >= binding.firstSeenAt) {
                "Listening-history binding timestamps are invalid."
            }
            require(binding.fileSizeBytes == null || binding.fileSizeBytes >= 0L) {
                "Listening-history binding file size is invalid."
            }
            require(binding.durationMsSnapshot == null || binding.durationMsSnapshot >= 0L) {
                "Listening-history binding duration is invalid."
            }
        }

        require(
            history.baselines.map { it.trackIdentityBackupId }.distinct().size ==
                history.baselines.size
        ) { "Listening-history baselines must reference unique identities." }
        require(
            history.baselines.map { it.legacyReferenceKey }.distinct().size ==
                history.baselines.size
        ) { "Listening-history baseline reference keys must be unique." }
        history.baselines.forEach { baseline ->
            require(baseline.trackIdentityBackupId in identities) {
                "Listening-history baseline references a missing identity."
            }
            require(baseline.historicalPlayCount > 0) {
                "Listening-history baseline play count is invalid."
            }
            require(baseline.lastKnownPlayedAt >= baseline.firstKnownPlayedAt) {
                "Listening-history baseline timestamps are invalid."
            }
            require(baseline.legacyReferenceKey.isNotBlank()) {
                "Listening-history baseline reference key is invalid."
            }
        }

        require(history.events.map { it.eventUuid }.distinct().size == history.events.size) {
            "Listening-history event UUIDs must be unique."
        }
        val sessionIds = history.events.mapNotNull { it.playbackSessionId }
        require(sessionIds.distinct().size == sessionIds.size) {
            "Listening-history playback-session IDs must be unique."
        }
        val sourceEventKeys = history.events.mapNotNull { event ->
            event.sourceEventKey?.let { event.source to it }
        }
        require(sourceEventKeys.distinct().size == sourceEventKeys.size) {
            "Listening-history source-event keys must be unique within each source."
        }
        history.events.forEach { event ->
            require(event.eventUuid.isNotBlank()) {
                "Listening-history event UUID is invalid."
            }
            require(event.trackIdentityBackupId in identities) {
                "Listening-history event references a missing identity."
            }
            event.localTrackBindingBackupId?.let { bindingId ->
                val binding = bindings[bindingId]
                    ?: throw IllegalArgumentException(
                        "Listening-history event references a missing binding."
                    )
                require(binding.trackIdentityBackupId == event.trackIdentityBackupId) {
                    "Listening-history event binding belongs to a different identity."
                }
            }
            require(event.listenedMs >= 0L) {
                "Listening-history event listening time is invalid."
            }
            require(event.startedAt == null || event.endedAt == null || event.endedAt >= event.startedAt) {
                "Listening-history event timestamps are invalid."
            }
            require(event.trackDurationMs == null || event.trackDurationMs >= 0L) {
                "Listening-history event duration is invalid."
            }
            require(event.qualificationRuleVersion > 0) {
                "Listening-history qualification-rule representation is unsupported."
            }
            require(event.playbackSessionId == null || event.playbackSessionId.isNotBlank()) {
                "Listening-history playback-session ID is invalid."
            }
            require(event.sourceEventKey == null || event.sourceEventKey.isNotBlank()) {
                "Listening-history source-event key is invalid."
            }
            val source = parseEnumValue("source", event.source, ListeningSource::fromStorageValue)
            val timestampEvidence = parseEnumValue("timestamp evidence", event.timestampEvidence, ListeningTimestampEvidence::fromStorageValue)
            val qualificationPolicy = parseEnumValue("qualification policy", event.qualificationPolicy, ListeningQualificationPolicy::fromStorageValue)
            val completion = parseEnumValue("completion classification", event.completionClassification, ListeningCompletionClassification::fromStorageValue)
            val publication = parseEnumValue("publication state", event.publicationState, ListeningEventPublicationState::fromStorageValue)
            require(publication != ListeningEventPublicationState.IMPORT_PENDING) {
                "Pending listening events cannot appear in a completed backup."
            }
            require(timestampEvidence != ListeningTimestampEvidence.NATIVE_EXACT ||
                (event.startedAt != null && event.endedAt != null && event.attributionAt == event.startedAt)) {
                "Native exact timestamp evidence is inconsistent."
            }
            require(timestampEvidence != ListeningTimestampEvidence.SOURCE_END_ONLY ||
                (event.startedAt == null && event.endedAt != null && event.attributionAt == event.endedAt)) {
                "Source-end-only timestamp evidence is inconsistent."
            }
            parseEnum(
                "qualification reason",
                event.qualificationReason,
                ListeningQualificationReason::fromStorageValue
            )
            val endReason = event.endReason?.let {
                parseEnumValue("end reason", it, ListeningEndReason::fromStorageValue)
            }
            require(qualificationPolicy == source.requiredQualificationPolicy()) {
                "Listening-history event qualification policy is incompatible with its source."
            }
            if (source == ListeningSource.CDPLAYA) {
                require(publication == ListeningEventPublicationState.NATIVE &&
                    timestampEvidence == ListeningTimestampEvidence.NATIVE_EXACT &&
                    event.startedAt != null && event.endedAt != null &&
                    event.attributionAt == event.startedAt) {
                    "Native listening-history event semantics are inconsistent."
                }
                require(completion != ListeningCompletionClassification.SOURCE_DOCUMENTED_NATURAL &&
                    (completion == ListeningCompletionClassification.NATIVE_NATURAL) ==
                    (endReason == ListeningEndReason.NATURAL_END)) {
                    "Native listening-history completion is inconsistent with its end reason."
                }
            } else {
                require(publication == ListeningEventPublicationState.IMPORT_PUBLISHED) {
                    "Completed imported listening history must be published."
                }
                require(completion != ListeningCompletionClassification.NATIVE_NATURAL) {
                    "Imported listening history cannot claim native completion."
                }
            }
        }

        val sources = history.importSources.associateByUnique(BackupListeningImportSource::backupSourceProfileId, "source profile ID")
        require(history.importSources.map { it.stableUuid }.distinct().size == history.importSources.size) {
            "Listening import source stable UUIDs must be unique."
        }
        val digests = history.importSources.mapNotNull { source ->
            source.accountIdentityDigest?.let { source.sourceType to it }
        }
        require(digests.distinct().size == digests.size) { "Listening import source account digests must be unique per source type." }
        history.importSources.forEach { source ->
            require(source.backupSourceProfileId > 0 && source.stableUuid.isNotBlank() && source.displayLabel.isNotBlank())
            require(source.updatedAt >= source.createdAt)
            require(parseEnumValue("import source", source.sourceType, ListeningSource::fromStorageValue) != ListeningSource.CDPLAYA) {
                "Sazanami cannot be used as an import source profile."
            }
        }
        val batches = history.importBatches.associateByUnique(BackupListeningImportBatch::backupBatchId, "import batch ID")
        require(history.importBatches.map { it.stableUuid }.distinct().size == history.importBatches.size) { "Listening import batch UUIDs must be unique." }
        history.importBatches.forEach { batch ->
            require(batch.sourceProfileBackupId in sources) { "Listening import batch references a missing source profile." }
            require(parseEnumValue("import batch status", batch.status, ListeningImportBatchStatus::fromStorageValue) == ListeningImportBatchStatus.PUBLISHED) {
                "Only published listening import batches may be backed up."
            }
            require(batch.completedAt != null && batch.completedAt >= batch.startedAt)
            require(listOf(batch.parsedCount,batch.insertedCount,batch.duplicateCount,batch.ignoredCount,batch.invalidCount,
                batch.exactMatchCount,batch.ambiguousMatchCount,batch.unmatchedCount,batch.qualifiedCount).all { it >= 0 })
            val source = sources.getValue(batch.sourceProfileBackupId)
            val sourceType = ListeningSource.fromStorageValue(source.sourceType)
            val policy = parseEnumValue("batch qualification policy", batch.qualificationPolicy, ListeningQualificationPolicy::fromStorageValue)
            require(policy == sourceType.requiredQualificationPolicy()) {
                "Listening import batch qualification policy is incompatible with its source profile."
            }
        }
        val externalKeys = history.externalTrackIds.map { it.sourceType to it.externalId }
        require(externalKeys.distinct().size == externalKeys.size) { "Listening external IDs must be unique per source." }
        history.externalTrackIds.forEach { external ->
            require(external.trackIdentityBackupId in identities && external.externalId.isNotBlank() && external.lastSeenAt >= external.createdAt)
            require(parseEnumValue("external ID source", external.sourceType, ListeningSource::fromStorageValue) != ListeningSource.CDPLAYA) {
                "Sazanami cannot be used as an external catalog source."
            }
        }
        val eventsByUuid = history.events.associateBy { it.eventUuid }
        val evidenceKeys = history.importedEventEvidence.map { listOf(it.sourceProfileBackupId, it.fingerprintVersion, it.fingerprint, it.duplicateOrdinal) }
        require(evidenceKeys.distinct().size == evidenceKeys.size) { "Imported event evidence fingerprint ordinals must be unique." }
        require(history.importedEventEvidence.map { it.eventUuid }.distinct().size == history.importedEventEvidence.size) { "Imported events may have only one evidence row." }
        history.importedEventEvidence.forEach { evidence ->
            val event = eventsByUuid[evidence.eventUuid] ?: throw IllegalArgumentException("Imported evidence references a missing event.")
            val source = sources[evidence.sourceProfileBackupId]
                ?: throw IllegalArgumentException("Imported evidence references a missing source profile.")
            require(source.sourceType == event.source) { "Imported evidence source profile is incompatible with its event." }
            require(evidence.fingerprintVersion > 0 && evidence.fingerprint.isNotBlank() && evidence.duplicateOrdinal >= 0)
            require(event.publicationState == "import_published" && event.source != "cdplaya") { "Imported evidence points to an incompatible native event." }
            parseEnum("skipped state", evidence.skippedState, ImportedListeningSkippedState::fromStorageValue)
            parseEnum("match disposition", evidence.matchDispositionAtImport, ImportedListeningMatchDisposition::fromStorageValue)
        }
        val linkKeys = history.batchEventObservations.map { it.batchBackupId to it.eventUuid }
        val evidenceByEventUuid = history.importedEventEvidence.associateBy { it.eventUuid }
        require(linkKeys.distinct().size == linkKeys.size) { "Listening batch-event links must be unique." }
        require(history.batchEventObservations.all { it.batchBackupId in batches && it.eventUuid in eventsByUuid }) {
            "Listening batch-event link references a missing record."
        }
        val observationProfilesByEvent = history.batchEventObservations.groupBy { it.eventUuid }
            .mapValues { (_, links) ->
                links.map { link ->
                    sources.getValue(batches.getValue(link.batchBackupId).sourceProfileBackupId)
                        .backupSourceProfileId
                }.toSet()
            }
        require(observationProfilesByEvent.values.all { it.size == 1 }) {
            "Listening event observations must belong to one import source profile."
        }
        history.batchEventObservations.forEach { link ->
            require(link.batchBackupId in batches && link.eventUuid in eventsByUuid) { "Listening batch-event link references a missing record." }
            val batch = batches.getValue(link.batchBackupId)
            val source = sources.getValue(batch.sourceProfileBackupId)
            val event = eventsByUuid.getValue(link.eventUuid)
            require(source.sourceType == event.source && event.source != ListeningSource.CDPLAYA.storageValue) {
                "Listening batch-event link crosses import sources."
            }
            require(evidenceByEventUuid[event.eventUuid]?.sourceProfileBackupId == null ||
                evidenceByEventUuid.getValue(event.eventUuid).sourceProfileBackupId == batch.sourceProfileBackupId) {
                "Listening batch-event link crosses import source profiles."
            }
        }
        history.events.filter { it.source == "cdplaya" }.forEach { event ->
            require(event.publicationState == "native" && event.eventUuid !in history.importedEventEvidence.map { it.eventUuid }.toSet()) {
                "Native listening events cannot carry import state."
            }
        }

        val reconciliationSources = history.reconciliations.map {
            it.sourceIdentityBackupId
        }
        require(reconciliationSources.distinct().size == reconciliationSources.size) {
            "Listening identity reconciliation sources must be unique."
        }
        val reconciliationTargets = history.reconciliations.mapTo(HashSet()) {
            it.targetIdentityBackupId
        }
        require(reconciliationSources.none { it in reconciliationTargets }) {
            "Listening identity reconciliations cannot contain chains or mixed roles."
        }
        val boundIdentityIds = history.bindings.mapTo(HashSet()) { it.trackIdentityBackupId }
        val importedHistoryIdentityIds = history.events.asSequence()
            .filter { it.source != ListeningSource.CDPLAYA.storageValue }
            .filter { it.publicationState == ListeningEventPublicationState.IMPORT_PUBLISHED.storageValue }
            .mapTo(HashSet()) { it.trackIdentityBackupId }
        history.reconciliations.forEach { reconciliation ->
            require(reconciliation.sourceIdentityBackupId in identities) {
                "Listening identity reconciliation references a missing source identity."
            }
            require(reconciliation.targetIdentityBackupId in identities) {
                "Listening identity reconciliation references a missing target identity."
            }
            require(reconciliation.sourceIdentityBackupId != reconciliation.targetIdentityBackupId) {
                "Listening identity reconciliation cannot link an identity to itself."
            }
            require(reconciliation.sourceIdentityBackupId !in boundIdentityIds) {
                "Listening identity reconciliation source cannot have a local binding."
            }
            require(reconciliation.sourceIdentityBackupId in importedHistoryIdentityIds) {
                "Listening identity reconciliation source has no published imported history."
            }
            require(reconciliation.targetIdentityBackupId in boundIdentityIds) {
                "Listening identity reconciliation target has no local binding evidence."
            }
            require(reconciliation.reconciledAt >= 0L) {
                "Listening identity reconciliation timestamp is invalid."
            }
        }

        val expectedSummary = history.recordsSummary()
        require(history.summary == expectedSummary) {
            "Listening-history backup summary does not match its records."
        }
        return history
    }
}

internal fun BackupListeningHistoryV2.recordsSummary() = BackupListeningHistorySummary(
    identityCount = identities.size.toLong(),
    bindingCount = bindings.size.toLong(),
    baselineCount = baselines.size.toLong(),
    eventCount = events.size.toLong(),
    qualifiedEventCount = events.count { it.qualifiedAsPlay }.toLong(),
    nonQualifiedEventCount = events.count { !it.qualifiedAsPlay }.toLong(),
    earliestDetailedEventAt = events.minOfOrNull { it.attributionAt },
    latestDetailedEventAt = events.maxOfOrNull { it.attributionAt }
)

private inline fun <T, K> List<T>.associateByUnique(
    keySelector: (T) -> K,
    label: String
): Map<K, T> {
    val result = associateBy(keySelector)
    require(result.size == size) { "Listening-history $label values must be unique." }
    return result
}

private inline fun <T> parseEnum(
    label: String,
    value: String,
    parser: (String) -> T
) {
    try {
        parser(value)
    } catch (_: IllegalStateException) {
        throw IllegalArgumentException("Listening-history $label value is unsupported.")
    }
}

private inline fun <T> parseEnumValue(label: String, value: String, parser: (String) -> T): T = try {
    parser(value)
} catch (_: IllegalStateException) {
    throw IllegalArgumentException("Listening-history $label value is unsupported.")
}
