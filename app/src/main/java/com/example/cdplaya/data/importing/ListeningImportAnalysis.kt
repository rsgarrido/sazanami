package com.example.cdplaya.data.importing

import java.time.Instant

enum class ImportFileFormat {
    SPOTIFY_EXTENDED_STREAMING_HISTORY,
    SPOTIFY_BASIC_ACCOUNT_HISTORY_UNSUPPORTED,
    UNKNOWN_JSON,
    MALFORMED_JSON
}

enum class ImportRecordErrorReason {
    MISSING_TIMESTAMP,
    INVALID_TIMESTAMP,
    FUTURE_TIMESTAMP,
    MISSING_LISTENED_DURATION,
    NEGATIVE_LISTENED_DURATION,
    INVALID_TRACK_URI,
    INVALID_MEDIA_URI,
    AMBIGUOUS_MEDIA_TYPE,
    MISSING_MUSIC_METADATA,
    INVALID_RECORD_SHAPE
}

data class ImportRecordDiagnostic(
    val recordIndex: Long,
    val reason: ImportRecordErrorReason
)

data class ListeningImportAnalysis(
    val provider: ImportProvider,
    val format: ImportFileFormat,
    val totalRecords: Long,
    val validMusicRecords: Long,
    val podcastRecords: Long,
    val audiobookRecords: Long,
    val videoRecords: Long,
    val unknownRecords: Long,
    val invalidRecords: Long,
    val zeroMsMusicRecords: Long,
    val earliestAt: Instant?,
    val latestAt: Instant?,
    /** Null means an exact bounded count was intentionally not retained. */
    val uniqueExternalTrackIds: Long?,
    val invalidReasonCounts: Map<ImportRecordErrorReason, Long>,
    val diagnosticExamples: List<ImportRecordDiagnostic>
) {
    companion object {
        /**
         * Combines factual counters without pretending to know cross-file external-ID uniqueness.
         */
        fun combine(analyses: List<ListeningImportAnalysis>): ListeningImportAnalysis {
            require(analyses.isNotEmpty())
            val provider = analyses.first().provider
            require(analyses.all { it.provider == provider })
            val format = analyses.first().format
            require(analyses.all { it.format == format })
            val reasons = ImportRecordErrorReason.entries.associateWith { reason ->
                analyses.sumOf { it.invalidReasonCounts[reason] ?: 0L }
            }.filterValues { it > 0L }
            return ListeningImportAnalysis(
                provider = provider,
                format = format,
                totalRecords = analyses.sumOf { it.totalRecords },
                validMusicRecords = analyses.sumOf { it.validMusicRecords },
                podcastRecords = analyses.sumOf { it.podcastRecords },
                audiobookRecords = analyses.sumOf { it.audiobookRecords },
                videoRecords = analyses.sumOf { it.videoRecords },
                unknownRecords = analyses.sumOf { it.unknownRecords },
                invalidRecords = analyses.sumOf { it.invalidRecords },
                zeroMsMusicRecords = analyses.sumOf { it.zeroMsMusicRecords },
                earliestAt = analyses.mapNotNull { it.earliestAt }.minOrNull(),
                latestAt = analyses.mapNotNull { it.latestAt }.maxOrNull(),
                uniqueExternalTrackIds = analyses.singleOrNull()?.uniqueExternalTrackIds,
                invalidReasonCounts = reasons,
                diagnosticExamples = analyses.flatMap { it.diagnosticExamples }
                    .take(DEFAULT_DIAGNOSTIC_LIMIT)
            )
        }

        const val DEFAULT_DIAGNOSTIC_LIMIT = 20
    }
}

enum class ImportFileFailureReason {
    UNSUPPORTED_FORMAT,
    UNKNOWN_FORMAT,
    MALFORMED_JSON,
    UNREADABLE_STREAM
}

sealed interface ListeningImportFileResult {
    data class Success(val analysis: ListeningImportAnalysis) : ListeningImportFileResult

    data class Failure(
        val format: ImportFileFormat,
        val reason: ImportFileFailureReason,
        val safeMessage: String,
        val partialAnalysis: ListeningImportAnalysis? = null
    ) : ListeningImportFileResult
}
