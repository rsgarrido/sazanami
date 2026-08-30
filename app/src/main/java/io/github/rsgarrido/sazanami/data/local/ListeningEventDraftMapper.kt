package io.github.rsgarrido.sazanami.data.local

import io.github.rsgarrido.sazanami.data.listening.FinalizedListeningEventDraft

/** Room boundary kept separate from the pure listening-session recorder. */
fun FinalizedListeningEventDraft.toEntity(): ListeningEventEntity = ListeningEventEntity(
    eventUuid = eventUuid,
    source = source,
    trackIdentityId = trackIdentityId,
    localTrackBindingId = localTrackBindingId,
    playbackSessionId = playbackSessionId,
    startedAt = startedAt,
    endedAt = endedAt,
    attributionAt = attributionAt,
    timestampEvidence = timestampEvidence,
    listenedMs = listenedMs,
    trackDurationMs = trackDurationMs,
    qualifiedAsPlay = qualifiedAsPlay,
    qualificationReason = qualificationReason,
    qualificationRuleVersion = qualificationRuleVersion,
    qualificationPolicy = qualificationPolicy,
    endReason = endReason,
    completionClassification = completionClassification,
    publicationState = publicationState,
    sourceEventKey = sourceEventKey,
    importBatchId = importBatchId,
    createdAt = createdAt
)
