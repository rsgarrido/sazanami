package com.example.cdplaya.data.importing

import com.example.cdplaya.data.local.ImportedListeningSkippedState
import com.example.cdplaya.data.local.ListeningCompletionClassification
import com.example.cdplaya.data.local.ListeningQualificationPolicy
import com.example.cdplaya.data.local.ListeningQualificationReason

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

