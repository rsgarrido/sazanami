package com.example.cdplaya.data.importing.spotify

import com.example.cdplaya.data.importing.PreparedListeningOccurrence
import com.example.cdplaya.data.local.ListeningEventEntity
import com.example.cdplaya.data.local.ListeningEventPublicationState
import com.example.cdplaya.data.local.ListeningSource
import com.example.cdplaya.data.local.ListeningTimestampEvidence

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
