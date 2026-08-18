package com.example.cdplaya.data.importing.spotify

import com.example.cdplaya.data.importing.ImportProvider
import com.example.cdplaya.data.importing.ImportRecordDiagnostic
import com.example.cdplaya.data.importing.ImportRecordErrorReason
import com.example.cdplaya.data.importing.ImportedCompletionEvidence
import com.example.cdplaya.data.importing.ImportedListeningRecord
import com.example.cdplaya.data.importing.ImportedMediaType
import com.example.cdplaya.data.importing.ImportedTimestampEvidence
import com.example.cdplaya.data.importing.ImportedTriState
import java.time.Clock
import java.time.DateTimeException
import java.time.Instant

internal class SpotifyRecordNormalizer(
    private val clock: Clock
) {
    fun normalize(
        recordIndex: Long,
        dto: SpotifyExtendedStreamingRecordDto
    ): SpotifyParseItem {
        val timestampText = dto.ts.present()
            ?: return invalid(recordIndex, ImportRecordErrorReason.MISSING_TIMESTAMP)
        val endedAt = try {
            Instant.parse(timestampText)
        } catch (_: DateTimeException) {
            return invalid(recordIndex, ImportRecordErrorReason.INVALID_TIMESTAMP)
        }
        if (endedAt.isAfter(clock.instant())) {
            return invalid(recordIndex, ImportRecordErrorReason.FUTURE_TIMESTAMP)
        }
        val listenedMs = dto.msPlayed
            ?: return invalid(recordIndex, ImportRecordErrorReason.MISSING_LISTENED_DURATION)
        if (listenedMs < 0L) {
            return invalid(recordIndex, ImportRecordErrorReason.NEGATIVE_LISTENED_DURATION)
        }

        val uriEvidence = parseUriEvidence(recordIndex, dto)
        if (uriEvidence is UriEvidenceResult.Invalid) {
            return invalid(recordIndex, uriEvidence.reason)
        }
        val ids = (uriEvidence as UriEvidenceResult.Valid).ids
        val strongTypes = ids.keys
        if (strongTypes.size > 1) {
            return invalid(recordIndex, ImportRecordErrorReason.AMBIGUOUS_MEDIA_TYPE)
        }
        val mediaType = strongTypes.singleOrNull() ?: classifyDescriptiveEvidence(dto)
        if (mediaType == ImportedMediaType.MUSIC_TRACK && !dto.hasCredibleMusicMetadata()) {
            return invalid(recordIndex, ImportRecordErrorReason.MISSING_MUSIC_METADATA)
        }

        if (mediaType != ImportedMediaType.MUSIC_TRACK) {
            return SpotifyParseItem.UnsupportedMedia(
                recordIndex = recordIndex,
                mediaType = mediaType,
                sourceEndedAt = endedAt,
                listenedMs = listenedMs
            )
        }

        val artist = dto.albumArtistName.present()
        return SpotifyParseItem.ValidMusic(
            recordIndex = recordIndex,
            record = ImportedListeningRecord(
                provider = ImportProvider.SPOTIFY,
                externalMediaId = ids[ImportedMediaType.MUSIC_TRACK],
                mediaType = ImportedMediaType.MUSIC_TRACK,
                trackTitle = dto.trackName.present(),
                trackArtist = artist,
                albumTitle = dto.albumName.present(),
                albumArtist = artist,
                sourceStartedAt = null,
                sourceEndedAt = endedAt,
                timestampEvidence = ImportedTimestampEvidence.SOURCE_END_ONLY,
                listenedMs = listenedMs,
                skippedEvidence = when (dto.skipped) {
                    true -> ImportedTriState.TRUE
                    false -> ImportedTriState.FALSE
                    null -> ImportedTriState.UNKNOWN
                },
                completionEvidence = ImportedCompletionEvidence.UNKNOWN,
                providerReasonStart = dto.reasonStart.present(),
                providerReasonEnd = dto.reasonEnd.present()
            )
        )
    }

    private fun parseUriEvidence(
        recordIndex: Long,
        dto: SpotifyExtendedStreamingRecordDto
    ): UriEvidenceResult {
        val ids = mutableMapOf<ImportedMediaType, String>()
        val uriFields = listOf(
            UriField(dto.spotifyTrackUri, "track", ImportedMediaType.MUSIC_TRACK, true),
            UriField(dto.spotifyEpisodeUri, "episode", ImportedMediaType.PODCAST_EPISODE, false),
            UriField(dto.audiobookUri, "audiobook", ImportedMediaType.AUDIOBOOK, false),
            UriField(dto.audiobookChapterUri, "chapter", ImportedMediaType.AUDIOBOOK, false),
            UriField(dto.spotifyVideoUri, "video", ImportedMediaType.VIDEO, false)
        )
        uriFields.forEach { field ->
            val value = field.value.present() ?: return@forEach
            val match = SPOTIFY_URI.matchEntire(value)
            if (match == null || match.groupValues[1] != field.expectedType) {
                return UriEvidenceResult.Invalid(
                    if (field.trackField) ImportRecordErrorReason.INVALID_TRACK_URI
                    else ImportRecordErrorReason.INVALID_MEDIA_URI
                )
            }
            ids.putIfAbsent(field.mediaType, match.groupValues[2])
        }
        return UriEvidenceResult.Valid(ids)
    }

    private fun classifyDescriptiveEvidence(
        dto: SpotifyExtendedStreamingRecordDto
    ): ImportedMediaType = when {
        dto.hasAudiobookMetadata() -> ImportedMediaType.AUDIOBOOK
        dto.hasEpisodeMetadata() -> ImportedMediaType.PODCAST_EPISODE
        dto.hasVideoMetadata() -> ImportedMediaType.VIDEO
        dto.hasCredibleMusicMetadata() -> ImportedMediaType.MUSIC_TRACK
        else -> ImportedMediaType.UNKNOWN
    }

    private fun SpotifyExtendedStreamingRecordDto.hasCredibleMusicMetadata(): Boolean =
        trackName.present() != null && albumArtistName.present() != null

    private fun SpotifyExtendedStreamingRecordDto.hasEpisodeMetadata(): Boolean =
        episodeName.present() != null || episodeShowName.present() != null

    private fun SpotifyExtendedStreamingRecordDto.hasAudiobookMetadata(): Boolean =
        audiobookTitle.present() != null || audiobookChapterTitle.present() != null

    private fun SpotifyExtendedStreamingRecordDto.hasVideoMetadata(): Boolean =
        videoTitle.present() != null

    private fun invalid(index: Long, reason: ImportRecordErrorReason) =
        SpotifyParseItem.Invalid(ImportRecordDiagnostic(index, reason))

    private data class UriField(
        val value: String?,
        val expectedType: String,
        val mediaType: ImportedMediaType,
        val trackField: Boolean
    )

    private sealed interface UriEvidenceResult {
        data class Valid(val ids: Map<ImportedMediaType, String>) : UriEvidenceResult
        data class Invalid(val reason: ImportRecordErrorReason) : UriEvidenceResult
    }

    private companion object {
        val SPOTIFY_URI = Regex("spotify:([a-z]+):([A-Za-z0-9]{1,128})")
    }
}

private fun String?.present(): String? = this?.takeUnless { it.isBlank() }
