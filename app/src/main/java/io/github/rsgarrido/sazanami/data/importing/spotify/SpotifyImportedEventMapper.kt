package io.github.rsgarrido.sazanami.data.importing.spotify

import io.github.rsgarrido.sazanami.data.importing.PreparedListeningOccurrence
import io.github.rsgarrido.sazanami.data.local.ListeningEventEntity
import io.github.rsgarrido.sazanami.data.local.ListeningEventPublicationState
import io.github.rsgarrido.sazanami.data.local.ListeningSource
import io.github.rsgarrido.sazanami.data.local.ListeningTimestampEvidence

object SpotifyImportedEventMapper {
    fun map(
        occurrence: PreparedListeningOccurrence,
        trackIdentityId: Long,
        eventUuid: String,
        createdAt: Long
    ): ListeningEventEntity = ListeningEventEntity(
        eventUuid = eventUuid,
        source = ListeningSource.SPOTIFY_IMPORT,
        trackIdentityId = trackIdentityId,
        localTrackBindingId = null,
        playbackSessionId = null,
        startedAt = null,
        endedAt = occurrence.record.sourceEndedAt.toEpochMilli(),
        attributionAt = occurrence.record.sourceEndedAt.toEpochMilli(),
        timestampEvidence = ListeningTimestampEvidence.SOURCE_END_ONLY,
        listenedMs = occurrence.record.listenedMs,
        trackDurationMs = null,
        qualifiedAsPlay = occurrence.policy.qualifiedAsPlay,
        qualificationReason = occurrence.policy.qualificationReason,
        qualificationRuleVersion = occurrence.policy.qualificationRuleVersion,
        qualificationPolicy = occurrence.policy.qualificationPolicy,
        endReason = null,
        completionClassification = occurrence.policy.completionClassification,
        publicationState = ListeningEventPublicationState.IMPORT_PENDING,
        sourceEventKey = null,
        importBatchId = null,
        createdAt = createdAt
    )
}
