package io.github.rsgarrido.sazanami.data.importing.spotify

import io.github.rsgarrido.sazanami.data.importing.ImportProvider
import io.github.rsgarrido.sazanami.data.importing.ImportedListeningPolicyResult
import io.github.rsgarrido.sazanami.data.importing.ImportedListeningRecord
import io.github.rsgarrido.sazanami.data.importing.ImportedTriState
import io.github.rsgarrido.sazanami.data.local.ImportedListeningSkippedState
import io.github.rsgarrido.sazanami.data.local.ListeningCompletionClassification
import io.github.rsgarrido.sazanami.data.local.ListeningQualificationPolicy
import io.github.rsgarrido.sazanami.data.local.ListeningQualificationReason

/** Sazanami's versioned interpretation of Spotify import evidence. */
object SpotifyImportPolicy {
    const val QUALIFICATION_RULE_VERSION = 1
    const val QUALIFIED_LISTENED_MS = 30_000L

    fun evaluate(record: ImportedListeningRecord): ImportedListeningPolicyResult {
        require(record.provider == ImportProvider.SPOTIFY)
        val reasonStart = SpotifyReasonTokenNormalizer.normalize(record.providerReasonStart)
        val reasonEnd = SpotifyReasonTokenNormalizer.normalize(record.providerReasonEnd)
        val skippedState = record.skippedEvidence.toPersistenceState()
        val naturalCompletion = reasonEnd == NATURAL_COMPLETION_TOKEN &&
            skippedState == ImportedListeningSkippedState.FALSE
        val qualifiedByDuration = record.listenedMs >= QUALIFIED_LISTENED_MS
        return ImportedListeningPolicyResult(
            qualifiedAsPlay = qualifiedByDuration || naturalCompletion,
            qualificationReason = when {
                qualifiedByDuration -> ListeningQualificationReason.TIME_THRESHOLD
                naturalCompletion -> ListeningQualificationReason.NATURAL_END
                else -> ListeningQualificationReason.NONE
            },
            qualificationPolicy = ListeningQualificationPolicy.SPOTIFY,
            qualificationRuleVersion = QUALIFICATION_RULE_VERSION,
            completionClassification = if (naturalCompletion) {
                ListeningCompletionClassification.SOURCE_DOCUMENTED_NATURAL
            } else {
                ListeningCompletionClassification.NONE
            },
            normalizedReasonStart = reasonStart,
            normalizedReasonEnd = reasonEnd,
            skippedState = skippedState
        )
    }

    private const val NATURAL_COMPLETION_TOKEN = "trackdone"
}

/** Fingerprint-v1 and policy-v1 share this deliberately minimal token normalization. */
object SpotifyReasonTokenNormalizer {
    fun normalize(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }
}

private fun ImportedTriState.toPersistenceState(): ImportedListeningSkippedState = when (this) {
    ImportedTriState.TRUE -> ImportedListeningSkippedState.TRUE
    ImportedTriState.FALSE -> ImportedListeningSkippedState.FALSE
    ImportedTriState.UNKNOWN -> ImportedListeningSkippedState.UNKNOWN
}

