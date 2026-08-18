package com.example.cdplaya.data.importing

import java.time.Instant

enum class ImportProvider {
    SPOTIFY,
    LASTFM
}

enum class ImportedMediaType {
    MUSIC_TRACK,
    PODCAST_EPISODE,
    AUDIOBOOK,
    VIDEO,
    UNKNOWN
}

enum class ImportedTimestampEvidence {
    SOURCE_END_ONLY
}

enum class ImportedTriState {
    TRUE,
    FALSE,
    UNKNOWN
}

/** Session 1 preserves provider evidence but deliberately does not interpret completion. */
enum class ImportedCompletionEvidence {
    UNKNOWN
}

/**
 * Provider-neutral, non-persistent evidence produced by an import parser.
 *
 * Spotify only supplies an album-artist field. For Spotify v1, [trackArtist] and [albumArtist]
 * therefore contain that same provider value; no track-level artist credit is fabricated.
 */
data class ImportedListeningRecord(
    val provider: ImportProvider,
    val externalMediaId: String?,
    val mediaType: ImportedMediaType,
    val trackTitle: String?,
    val trackArtist: String?,
    val albumTitle: String?,
    val albumArtist: String?,
    val sourceStartedAt: Instant?,
    val sourceEndedAt: Instant,
    val timestampEvidence: ImportedTimestampEvidence,
    val listenedMs: Long,
    val skippedEvidence: ImportedTriState,
    val completionEvidence: ImportedCompletionEvidence,
    val providerReasonStart: String?,
    val providerReasonEnd: String?
)
