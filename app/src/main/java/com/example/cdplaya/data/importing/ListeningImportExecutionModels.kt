package com.example.cdplaya.data.importing

import com.example.cdplaya.data.local.ImportedListeningMatchDisposition
import java.time.Instant

data class PreparedListeningOccurrence(
    val key: ImportOccurrenceKey,
    val record: ImportedListeningRecord,
    val policy: ImportedListeningPolicyResult
)

data class ListeningImportChunkResult(
    val selectedOccurrences: Int,
    val alreadyImported: Int,
    val newPending: Int,
    val exactExternalIdMatches: Int,
    val historicalIdentitiesCreated: Int,
    val qualifiedNewOccurrences: Int
)

enum class ListeningImportExecutionPhase {
    ANALYZING,
    IMPORTING,
    PUBLISHING,
    COMPLETED
}

data class ListeningImportExecutionProgress(
    val phase: ListeningImportExecutionPhase,
    val recordsProcessed: Long,
    val selectedOccurrences: Long,
    val alreadyImported: Long,
    val newPending: Long,
    val chunksCompleted: Int
)

data class ListeningImportExecutionResult(
    val batchId: Long,
    val selectedOccurrences: Long,
    val overlappingOccurrencesSuppressed: Long,
    val alreadyImported: Long,
    val newPublished: Long,
    val invalid: Long,
    val unsupportedMedia: Long,
    val sourceRangeStart: Instant?,
    val sourceRangeEnd: Instant?
)

enum class ListeningImportFailureCategory(val storageValue: String) {
    SOURCE_READ("source_read"),
    INVALID_EXECUTION_INPUT("invalid_execution_input"),
    PERSISTENCE("persistence")
}

data class ListeningImportIdentityResolution(
    val trackIdentityId: Long,
    val disposition: ImportedListeningMatchDisposition
)
