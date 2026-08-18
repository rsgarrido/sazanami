package com.example.cdplaya.data.importing

import com.example.cdplaya.data.importing.spotify.SpotifyExtendedStreamingParser
import com.example.cdplaya.data.importing.spotify.SpotifyFileParseResult
import com.example.cdplaya.data.importing.spotify.SpotifyParseControl
import com.example.cdplaya.data.importing.spotify.SpotifyParseItem
import java.io.InputStream
import java.time.Instant

class ListeningImportAnalyzer(
    private val spotifyParser: SpotifyExtendedStreamingParser,
    private val diagnosticLimit: Int = ListeningImportAnalysis.DEFAULT_DIAGNOSTIC_LIMIT,
    private val uniqueExternalIdLimit: Int = DEFAULT_UNIQUE_EXTERNAL_ID_LIMIT
) {
    init {
        require(diagnosticLimit >= 0)
        require(uniqueExternalIdLimit >= 0)
    }

    fun analyzeSpotify(openStream: () -> InputStream): ListeningImportFileResult {
        val accumulator = Accumulator(diagnosticLimit, uniqueExternalIdLimit)
        val result = spotifyParser.parse(openStream) { item ->
            accumulator.accept(item)
            SpotifyParseControl.CONTINUE
        }
        return when (result) {
            is SpotifyFileParseResult.Completed -> ListeningImportFileResult.Success(
                accumulator.build(result.format)
            )
            is SpotifyFileParseResult.Failed -> ListeningImportFileResult.Failure(
                format = result.format,
                reason = result.reason,
                safeMessage = result.safeMessage,
                partialAnalysis = accumulator.build(result.format).takeIf {
                    it.totalRecords > 0L
                }
            )
            is SpotifyFileParseResult.Stopped -> error("Analyzer never stops parsing early.")
        }
    }

    private class Accumulator(
        private val diagnosticLimit: Int,
        private val uniqueExternalIdLimit: Int
    ) {
        var total = 0L
        var music = 0L
        var podcasts = 0L
        var audiobooks = 0L
        var videos = 0L
        var unknown = 0L
        var invalid = 0L
        var zeroMsMusic = 0L
        var earliest: Instant? = null
        var latest: Instant? = null
        val reasons = mutableMapOf<ImportRecordErrorReason, Long>()
        val examples = mutableListOf<ImportRecordDiagnostic>()
        var exactUniqueIds = true
        val uniqueIds = mutableSetOf<String>()

        fun accept(item: SpotifyParseItem) {
            total++
            when (item) {
                is SpotifyParseItem.ValidMusic -> {
                    music++
                    includeTime(item.record.sourceEndedAt)
                    if (item.record.listenedMs == 0L) zeroMsMusic++
                    item.record.externalMediaId?.let(::includeExternalId)
                }
                is SpotifyParseItem.UnsupportedMedia -> {
                    includeTime(item.sourceEndedAt)
                    when (item.mediaType) {
                        ImportedMediaType.PODCAST_EPISODE -> podcasts++
                        ImportedMediaType.AUDIOBOOK -> audiobooks++
                        ImportedMediaType.VIDEO -> videos++
                        ImportedMediaType.UNKNOWN -> unknown++
                        ImportedMediaType.MUSIC_TRACK -> error("Music must be emitted as ValidMusic.")
                    }
                }
                is SpotifyParseItem.Invalid -> {
                    invalid++
                    val reason = item.diagnostic.reason
                    reasons[reason] = (reasons[reason] ?: 0L) + 1L
                    if (examples.size < diagnosticLimit) examples += item.diagnostic
                }
            }
        }

        fun build(format: ImportFileFormat) = ListeningImportAnalysis(
            provider = ImportProvider.SPOTIFY,
            format = format,
            totalRecords = total,
            validMusicRecords = music,
            podcastRecords = podcasts,
            audiobookRecords = audiobooks,
            videoRecords = videos,
            unknownRecords = unknown,
            invalidRecords = invalid,
            zeroMsMusicRecords = zeroMsMusic,
            earliestAt = earliest,
            latestAt = latest,
            uniqueExternalTrackIds = uniqueIds.size.toLong().takeIf { exactUniqueIds },
            invalidReasonCounts = reasons.toMap(),
            diagnosticExamples = examples.toList()
        )

        private fun includeTime(value: Instant) {
            if (earliest == null || value < earliest) earliest = value
            if (latest == null || value > latest) latest = value
        }

        private fun includeExternalId(value: String) {
            if (!exactUniqueIds || value in uniqueIds) return
            if (uniqueIds.size >= uniqueExternalIdLimit) {
                exactUniqueIds = false
                uniqueIds.clear()
            } else {
                uniqueIds += value
            }
        }
    }

    companion object {
        const val DEFAULT_UNIQUE_EXTERNAL_ID_LIMIT = 100_000
    }
}
