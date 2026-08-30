package io.github.rsgarrido.sazanami.data.local

import androidx.room.TypeConverter

enum class ListeningSource(val storageValue: String) {
    CDPLAYA("cdplaya"),
    SPOTIFY_IMPORT("spotify_import"),
    LASTFM_IMPORT("lastfm_import");

    companion object {
        fun fromStorageValue(value: String): ListeningSource =
            entries.firstOrNull { it.storageValue == value }
                ?: error("Unknown listening source: $value")
    }
}

enum class ListeningQualificationReason(val storageValue: String) {
    NONE("none"),
    TIME_THRESHOLD("time_threshold"),
    NATURAL_END("natural_end");

    companion object {
        fun fromStorageValue(value: String): ListeningQualificationReason =
            entries.firstOrNull { it.storageValue == value }
                ?: error("Unknown listening qualification reason: $value")
    }
}

enum class ListeningEndReason(val storageValue: String) {
    NATURAL_END("natural_end"),
    TRANSITION("transition"),
    STOPPED("stopped"),
    ERROR("error"),
    UNKNOWN("unknown");

    companion object {
        fun fromStorageValue(value: String): ListeningEndReason =
            entries.firstOrNull { it.storageValue == value }
                ?: error("Unknown listening end reason: $value")
    }
}

enum class ListeningTimestampEvidence(val storageValue: String) {
    NATIVE_EXACT("native_exact"),
    SOURCE_END_ONLY("source_end_only");

    companion object {
        fun fromStorageValue(value: String) = entries.firstOrNull { it.storageValue == value }
            ?: error("Unknown listening timestamp evidence: $value")
    }
}

enum class ListeningQualificationPolicy(val storageValue: String) {
    CDPLAYA("cdplaya"),
    SPOTIFY("spotify"),
    LASTFM("lastfm"),
    OTHER_IMPORT("other_import");

    companion object {
        fun fromStorageValue(value: String) = entries.firstOrNull { it.storageValue == value }
            ?: error("Unknown listening qualification policy: $value")
    }
}

enum class ListeningCompletionClassification(val storageValue: String) {
    NONE("none"),
    NATIVE_NATURAL("native_natural"),
    SOURCE_DOCUMENTED_NATURAL("source_documented_natural");

    companion object {
        fun fromStorageValue(value: String) = entries.firstOrNull { it.storageValue == value }
            ?: error("Unknown listening completion classification: $value")
    }
}

enum class ListeningEventPublicationState(val storageValue: String) {
    NATIVE("native"),
    IMPORT_PENDING("import_pending"),
    IMPORT_PUBLISHED("import_published");

    companion object {
        fun fromStorageValue(value: String) = entries.firstOrNull { it.storageValue == value }
            ?: error("Unknown listening publication state: $value")
    }
}

enum class ListeningImportBatchStatus(val storageValue: String) {
    PENDING("pending"), PUBLISHED("published"), FAILED("failed"),
    CANCELLED("cancelled"), DELETING("deleting");
    companion object {
        fun fromStorageValue(value: String) = entries.firstOrNull { it.storageValue == value }
            ?: error("Unknown listening import batch status: $value")
    }
}

enum class ImportedListeningSkippedState(val storageValue: String) {
    UNKNOWN("unknown"), FALSE("false"), TRUE("true");
    companion object {
        fun fromStorageValue(value: String) = entries.firstOrNull { it.storageValue == value }
            ?: error("Unknown imported skipped state: $value")
    }
}

enum class ImportedListeningMatchDisposition(val storageValue: String) {
    /** Legacy value retained for backup/restore compatibility. */
    EXACT("exact"),
    EXACT_EXTERNAL_ID("exact_external_id"),
    CREATED_HISTORICAL_IDENTITY("created_historical_identity"),
    AMBIGUOUS("ambiguous"),
    UNMATCHED("unmatched");
    companion object {
        fun fromStorageValue(value: String) = entries.firstOrNull { it.storageValue == value }
            ?: error("Unknown imported match disposition: $value")
    }
}

class ListeningHistoryTypeConverters {
    @TypeConverter
    fun listeningSourceToString(value: ListeningSource): String = value.storageValue

    @TypeConverter
    fun stringToListeningSource(value: String): ListeningSource =
        ListeningSource.fromStorageValue(value)

    @TypeConverter
    fun qualificationReasonToString(value: ListeningQualificationReason): String =
        value.storageValue

    @TypeConverter
    fun stringToQualificationReason(value: String): ListeningQualificationReason =
        ListeningQualificationReason.fromStorageValue(value)

    @TypeConverter
    fun endReasonToString(value: ListeningEndReason): String = value.storageValue

    @TypeConverter
    fun stringToEndReason(value: String): ListeningEndReason =
        ListeningEndReason.fromStorageValue(value)

    @TypeConverter fun timestampEvidenceToString(value: ListeningTimestampEvidence) = value.storageValue
    @TypeConverter fun stringToTimestampEvidence(value: String) = ListeningTimestampEvidence.fromStorageValue(value)
    @TypeConverter fun qualificationPolicyToString(value: ListeningQualificationPolicy) = value.storageValue
    @TypeConverter fun stringToQualificationPolicy(value: String) = ListeningQualificationPolicy.fromStorageValue(value)
    @TypeConverter fun completionClassificationToString(value: ListeningCompletionClassification) = value.storageValue
    @TypeConverter fun stringToCompletionClassification(value: String) = ListeningCompletionClassification.fromStorageValue(value)
    @TypeConverter fun publicationStateToString(value: ListeningEventPublicationState) = value.storageValue
    @TypeConverter fun stringToPublicationState(value: String) = ListeningEventPublicationState.fromStorageValue(value)
    @TypeConverter fun importBatchStatusToString(value: ListeningImportBatchStatus) = value.storageValue
    @TypeConverter fun stringToImportBatchStatus(value: String) = ListeningImportBatchStatus.fromStorageValue(value)
    @TypeConverter fun skippedStateToString(value: ImportedListeningSkippedState) = value.storageValue
    @TypeConverter fun stringToSkippedState(value: String) = ImportedListeningSkippedState.fromStorageValue(value)
    @TypeConverter fun matchDispositionToString(value: ImportedListeningMatchDisposition) = value.storageValue
    @TypeConverter fun stringToMatchDisposition(value: String) = ImportedListeningMatchDisposition.fromStorageValue(value)
}
