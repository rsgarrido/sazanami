package com.example.cdplaya.data.importing.spotify

import com.example.cdplaya.data.importing.ImportProvider
import com.example.cdplaya.data.importing.ImportedListeningPolicyResult
import com.example.cdplaya.data.importing.ImportedListeningRecord
import com.example.cdplaya.data.importing.ImportedTriState
import com.example.cdplaya.data.local.ImportedListeningSkippedState
import com.example.cdplaya.data.local.ListeningCompletionClassification
import com.example.cdplaya.data.local.ListeningQualificationPolicy
import com.example.cdplaya.data.local.ListeningQualificationReason

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

