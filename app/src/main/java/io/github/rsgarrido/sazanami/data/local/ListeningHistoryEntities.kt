package io.github.rsgarrido.sazanami.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "listening_track_identities",
    indices = [
        Index(value = ["normalizedArtist", "normalizedTitle", "durationMsSnapshot"]),
        Index(value = ["normalizedAlbum"]),
        Index(value = ["metadataKey"])
    ]
)
data class ListeningTrackIdentityEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val titleSnapshot: String,
    val artistSnapshot: String,
    val albumSnapshot: String,
    val albumArtistSnapshot: String?,
    val durationMsSnapshot: Long?,
    val normalizedTitle: String,
    val normalizedArtist: String,
    val normalizedAlbum: String,
    val metadataKey: String?,
    val metadataKeyVersion: Int,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "local_track_bindings",
    foreignKeys = [
        ForeignKey(
            entity = ListeningTrackIdentityEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackIdentityId"],
            onDelete = ForeignKey.NO_ACTION
        )
    ],
    indices = [
        Index(value = ["trackIdentityId"]),
        Index(value = ["referenceKey"], unique = true),
        Index(value = ["volumeName", "mediaStoreId"]),
        Index(value = ["portableKey"])
    ]
)
data class LocalTrackBindingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val trackIdentityId: Long,
    val referenceKey: String,
    val mediaStoreId: Long?,
    val volumeName: String?,
    val contentUri: String?,
    val relativePath: String?,
    val displayName: String?,
    val absolutePath: String?,
    val fileSizeBytes: Long?,
    val dateModifiedEpochSeconds: Long?,
    val durationMsSnapshot: Long?,
    val legacyStableKey: String?,
    val portableKey: String?,
    val portableKeyVersion: Int?,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val missingSince: Long?
)

@Entity(
    tableName = "listening_identity_reconciliations",
    foreignKeys = [
        ForeignKey(
            entity = ListeningTrackIdentityEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceIdentityId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = ListeningTrackIdentityEntity::class,
            parentColumns = ["id"],
            childColumns = ["targetIdentityId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index(value = ["targetIdentityId"])]
)
data class ListeningIdentityReconciliationEntity(
    @PrimaryKey
    val sourceIdentityId: Long,
    val targetIdentityId: Long,
    val reconciledAt: Long
)

@Entity(
    tableName = "listening_events",
    foreignKeys = [
        ForeignKey(
            entity = ListeningTrackIdentityEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackIdentityId"],
            onDelete = ForeignKey.NO_ACTION
        ),
        ForeignKey(
            entity = LocalTrackBindingEntity::class,
            parentColumns = ["id"],
            childColumns = ["localTrackBindingId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["eventUuid"], unique = true),
        Index(value = ["playbackSessionId"], unique = true),
        Index(value = ["source", "sourceEventKey"], unique = true),
        Index(value = ["trackIdentityId", "attributionAt"]),
        Index(value = ["localTrackBindingId"]),
        Index(value = ["qualifiedAsPlay", "publicationState", "attributionAt"]),
        Index(value = ["source", "publicationState", "attributionAt"]),
        Index(value = ["publicationState", "attributionAt"])
    ]
)
data class ListeningEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val eventUuid: String,
    val source: ListeningSource,
    val trackIdentityId: Long,
    val localTrackBindingId: Long?,
    val playbackSessionId: String?,
    val startedAt: Long?,
    val endedAt: Long?,
    val attributionAt: Long = startedAt ?: endedAt
        ?: throw IllegalArgumentException("An event needs an attribution timestamp"),
    val timestampEvidence: ListeningTimestampEvidence = ListeningTimestampEvidence.NATIVE_EXACT,
    val listenedMs: Long,
    val trackDurationMs: Long?,
    val qualifiedAsPlay: Boolean,
    val qualificationReason: ListeningQualificationReason,
    val qualificationRuleVersion: Int,
    val qualificationPolicy: ListeningQualificationPolicy = ListeningQualificationPolicy.NATIVE,
    val endReason: ListeningEndReason?,
    val completionClassification: ListeningCompletionClassification =
        if (endReason == ListeningEndReason.NATURAL_END) ListeningCompletionClassification.NATIVE_NATURAL
        else ListeningCompletionClassification.NONE,
    val publicationState: ListeningEventPublicationState = ListeningEventPublicationState.NATIVE,
    val sourceEventKey: String?,
    @Deprecated("Legacy Room 10/backup v8 compatibility only; use listening_import_batch_events")
    val importBatchId: Long?,
    val createdAt: Long
) {
    init {
        require(listenedMs >= 0L) { "Listening time cannot be negative" }
        require(startedAt == null || endedAt == null || endedAt >= startedAt) {
            "A finalized listening event must not end before it starts"
        }
        require(timestampEvidence != ListeningTimestampEvidence.NATIVE_EXACT ||
            (startedAt != null && endedAt != null)) { "Native exact timestamps must be present" }
        require(timestampEvidence != ListeningTimestampEvidence.SOURCE_END_ONLY ||
            (startedAt == null && endedAt != null && attributionAt == endedAt)) {
            "Source-end-only evidence must use the exact end for attribution"
        }
    }
}

@Entity(tableName = "listening_import_sources", indices = [
    Index(value = ["stableUuid"], unique = true),
    Index(value = ["sourceType"]),
    Index(value = ["sourceType", "accountIdentityDigest"], unique = true),
    Index(value = ["displayLabel", "id"])
])
data class ListeningImportSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val stableUuid: String,
    val sourceType: ListeningSource,
    val displayLabel: String,
    val accountIdentityDigest: String?,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "listening_import_batches", foreignKeys = [ForeignKey(
    entity = ListeningImportSourceEntity::class, parentColumns = ["id"],
    childColumns = ["sourceProfileId"], onDelete = ForeignKey.NO_ACTION
)], indices = [Index(value = ["stableUuid"], unique = true), Index(value = ["sourceProfileId"]),
    Index(value = ["status"]), Index(value = ["startedAt"])])
data class ListeningImportBatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val stableUuid: String,
    val sourceProfileId: Long,
    val status: ListeningImportBatchStatus,
    val parserVersion: Int,
    val qualificationPolicy: ListeningQualificationPolicy,
    val qualificationRuleVersion: Int,
    val startedAt: Long,
    val completedAt: Long?,
    val sourceRangeStart: Long?, val sourceRangeEnd: Long?,
    val parsedCount: Long = 0, val insertedCount: Long = 0, val duplicateCount: Long = 0,
    val ignoredCount: Long = 0, val invalidCount: Long = 0, val exactMatchCount: Long = 0,
    val ambiguousMatchCount: Long = 0, val unmatchedCount: Long = 0, val qualifiedCount: Long = 0,
    val failureCategory: String? = null,
    val createdAppVersion: String
)

@Entity(tableName = "listening_track_external_ids", foreignKeys = [ForeignKey(
    entity = ListeningTrackIdentityEntity::class, parentColumns = ["id"],
    childColumns = ["trackIdentityId"], onDelete = ForeignKey.CASCADE
)], indices = [Index(value = ["trackIdentityId"]), Index(value = ["sourceType", "externalId"], unique = true)])
data class ListeningTrackExternalIdEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackIdentityId: Long,
    val sourceType: ListeningSource,
    val externalId: String,
    val createdAt: Long,
    val lastSeenAt: Long
)

@Entity(tableName = "imported_listening_event_evidence", foreignKeys = [
    ForeignKey(entity = ListeningEventEntity::class, parentColumns = ["id"], childColumns = ["eventId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = ListeningImportSourceEntity::class, parentColumns = ["id"], childColumns = ["sourceProfileId"], onDelete = ForeignKey.NO_ACTION)
], indices = [Index(value = ["sourceProfileId"]),
    Index(value = ["sourceProfileId", "fingerprintVersion", "fingerprint", "duplicateOrdinal"], unique = true)])
data class ImportedListeningEventEvidenceEntity(
    @PrimaryKey val eventId: Long,
    val sourceProfileId: Long,
    val fingerprintVersion: Int,
    val fingerprint: String,
    val duplicateOrdinal: Int,
    val normalizedReasonStart: String?,
    val normalizedReasonEnd: String?,
    val skippedState: ImportedListeningSkippedState,
    val matchDispositionAtImport: ImportedListeningMatchDisposition
)

@Entity(tableName = "listening_import_batch_events", primaryKeys = ["batchId", "eventId"], foreignKeys = [
    ForeignKey(entity = ListeningImportBatchEntity::class, parentColumns = ["id"], childColumns = ["batchId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = ListeningEventEntity::class, parentColumns = ["id"], childColumns = ["eventId"], onDelete = ForeignKey.CASCADE)
], indices = [Index(value = ["eventId"])])
data class ListeningImportBatchEventEntity(val batchId: Long, val eventId: Long)

@Entity(
    tableName = "legacy_listening_baselines",
    foreignKeys = [
        ForeignKey(
            entity = ListeningTrackIdentityEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackIdentityId"],
            onDelete = ForeignKey.NO_ACTION
        )
    ],
    indices = [Index(value = ["legacyReferenceKey"], unique = true)]
)
data class LegacyListeningBaselineEntity(
    @PrimaryKey
    val trackIdentityId: Long,
    val historicalPlayCount: Int,
    val firstKnownPlayedAt: Long,
    val lastKnownPlayedAt: Long,
    val legacyReferenceKey: String,
    val migratedAt: Long
)
