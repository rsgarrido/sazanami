package io.github.rsgarrido.sazanami.data.importing

import io.github.rsgarrido.sazanami.data.local.ImportedListeningSkippedState
import io.github.rsgarrido.sazanami.data.local.ListeningCompletionClassification
import io.github.rsgarrido.sazanami.data.local.ListeningQualificationPolicy
import io.github.rsgarrido.sazanami.data.local.ListeningQualificationReason

/** Pure interpretation of provider evidence; it is not a persistent event. */
data class ImportedListeningPolicyResult(
    val qualifiedAsPlay: Boolean,
    val qualificationReason: ListeningQualificationReason,
    val qualificationPolicy: ListeningQualificationPolicy,
    val qualificationRuleVersion: Int,
    val completionClassification: ListeningCompletionClassification,
    val normalizedReasonStart: String?,
    val normalizedReasonEnd: String?,
    val skippedState: ImportedListeningSkippedState
)

