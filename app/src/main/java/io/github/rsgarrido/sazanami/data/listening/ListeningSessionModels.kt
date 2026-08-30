package io.github.rsgarrido.sazanami.data.listening

import io.github.rsgarrido.sazanami.data.local.ListeningEndReason
import io.github.rsgarrido.sazanami.data.local.ListeningQualificationReason
import io.github.rsgarrido.sazanami.data.local.ListeningSource
import io.github.rsgarrido.sazanami.data.local.ListeningTimestampEvidence
import io.github.rsgarrido.sazanami.data.local.ListeningQualificationPolicy
import io.github.rsgarrido.sazanami.data.local.ListeningCompletionClassification
import io.github.rsgarrido.sazanami.data.local.ListeningEventPublicationState

data class ListeningSessionStart(
    val playbackSessionId: String,
    val trackIdentityId: Long,
    val localTrackBindingId: Long?,
    val trackDurationMs: Long?
) {
    init {
        require(playbackSessionId.isNotBlank()) { "Playback session ID cannot be blank" }
    }
}

data class ListeningSessionSnapshot(
    val playbackSessionId: String,
    val trackIdentityId: Long,
    val localTrackBindingId: Long?,
    val startedAt: Long,
    val trackDurationMs: Long?,
    /** Includes elapsed time in the currently open playing segment. */
    val accumulatedListenedMs: Long,
    val isPlaying: Boolean,
    val qualifiedAsPlay: Boolean,
    val qualificationReason: ListeningQualificationReason,
    val qualificationRuleVersion: Int
)

sealed interface StartListeningSessionResult {
    data class Started(val snapshot: ListeningSessionSnapshot) : StartListeningSessionResult
    data class AlreadyActive(val snapshot: ListeningSessionSnapshot) : StartListeningSessionResult
    data object AlreadyFinalized : StartListeningSessionResult
    data class Rejected(
        val reason: StartListeningSessionRejection,
        val activePlaybackSessionId: String
    ) : StartListeningSessionResult
}

enum class StartListeningSessionRejection {
    CONFLICTING_SAME_SESSION,
    DIFFERENT_SESSION_ACTIVE
}

enum class ListeningSessionCommandResult {
    APPLIED,
    DUPLICATE,
    NO_ACTIVE_SESSION,
    SESSION_MISMATCH
}

sealed interface FinalizeListeningSessionResult {
    data class Finalized(
        val draft: FinalizedListeningEventDraft
    ) : FinalizeListeningSessionResult

    data class SessionMismatch(
        val activePlaybackSessionId: String
    ) : FinalizeListeningSessionResult

    data object AlreadyFinalized : FinalizeListeningSessionResult
    data object NoActiveSession : FinalizeListeningSessionResult
}

data class FinalizedListeningEventDraft(
    val eventUuid: String,
    val source: ListeningSource,
    val trackIdentityId: Long,
    val localTrackBindingId: Long?,
    val playbackSessionId: String,
    val startedAt: Long,
    val endedAt: Long,
    val attributionAt: Long,
    val timestampEvidence: ListeningTimestampEvidence,
    val listenedMs: Long,
    val trackDurationMs: Long?,
    val qualifiedAsPlay: Boolean,
    val qualificationReason: ListeningQualificationReason,
    val qualificationRuleVersion: Int,
    val qualificationPolicy: ListeningQualificationPolicy,
    val endReason: ListeningEndReason,
    val completionClassification: ListeningCompletionClassification,
    val publicationState: ListeningEventPublicationState,
    val sourceEventKey: String? = null,
    val importBatchId: Long? = null,
    val createdAt: Long
) {
    init {
        require(eventUuid.isNotBlank()) { "Event UUID cannot be blank" }
        require(source == ListeningSource.NATIVE) {
            "Native recorder drafts must use the native listening source"
        }
        require(listenedMs >= 0L) { "Listening time cannot be negative" }
        require(endedAt >= startedAt) { "A finalized listening event must not end before it starts" }
        require(attributionAt == startedAt) { "Native attribution must use the exact start" }
        require(timestampEvidence == ListeningTimestampEvidence.NATIVE_EXACT)
        require(qualificationPolicy == ListeningQualificationPolicy.NATIVE)
        require(publicationState == ListeningEventPublicationState.NATIVE)
        require(sourceEventKey == null) { "Native recorder drafts cannot have an import event key" }
        require(importBatchId == null) { "Native recorder drafts cannot have an import batch" }
    }
}
