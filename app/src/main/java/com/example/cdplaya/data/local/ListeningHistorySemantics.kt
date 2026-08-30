package com.example.cdplaya.data.local

internal fun ListeningSource.requiredQualificationPolicy(): ListeningQualificationPolicy = when (this) {
    ListeningSource.CDPLAYA -> ListeningQualificationPolicy.CDPLAYA
    ListeningSource.SPOTIFY_IMPORT -> ListeningQualificationPolicy.SPOTIFY
    ListeningSource.LASTFM_IMPORT -> ListeningQualificationPolicy.LASTFM
}

internal fun ListeningEventEntity.requireSupportedSemantics() {
    require(qualificationPolicy == source.requiredQualificationPolicy()) {
        "Listening event qualification policy is incompatible with its source."
    }
    if (source == ListeningSource.CDPLAYA) {
        require(publicationState == ListeningEventPublicationState.NATIVE) {
            "Native listening events must use native publication."
        }
        require(timestampEvidence == ListeningTimestampEvidence.NATIVE_EXACT &&
            startedAt != null && endedAt != null && attributionAt == startedAt) {
            "Native listening events require exact native timestamps."
        }
        require(completionClassification != ListeningCompletionClassification.SOURCE_DOCUMENTED_NATURAL) {
            "Native listening events cannot claim source-documented completion."
        }
        require(
            (completionClassification == ListeningCompletionClassification.NATIVE_NATURAL) ==
                (endReason == ListeningEndReason.NATURAL_END)
        ) { "Native completion classification is inconsistent with the end reason." }
    } else {
        require(publicationState != ListeningEventPublicationState.NATIVE) {
            "Imported listening events cannot use native publication."
        }
        require(completionClassification != ListeningCompletionClassification.NATIVE_NATURAL) {
            "Imported listening events cannot claim native completion."
        }
    }
}

internal fun ListeningImportSourceEntity.requireSupportedImportSource() {
    require(sourceType != ListeningSource.CDPLAYA) {
        "Sazanami cannot be used as an import source profile."
    }
}

internal fun ListeningTrackExternalIdEntity.requireSupportedExternalSource() {
    require(sourceType != ListeningSource.CDPLAYA) {
        "Sazanami cannot be used as an external catalog source."
    }
}

internal fun ListeningImportBatchEntity.requireCompatibleWith(source: ListeningImportSourceEntity) {
    source.requireSupportedImportSource()
    require(sourceProfileId == source.id) { "Import batch references a different source profile." }
    require(qualificationPolicy == source.sourceType.requiredQualificationPolicy()) {
        "Import batch qualification policy is incompatible with its source profile."
    }
}
