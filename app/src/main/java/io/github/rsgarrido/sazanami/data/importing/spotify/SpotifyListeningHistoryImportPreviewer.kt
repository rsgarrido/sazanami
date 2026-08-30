package io.github.rsgarrido.sazanami.data.importing.spotify

import io.github.rsgarrido.sazanami.data.ListeningImportRepository
import io.github.rsgarrido.sazanami.data.importing.ImportFileFailureReason
import io.github.rsgarrido.sazanami.data.importing.ImportFileFormat
import io.github.rsgarrido.sazanami.data.importing.ImportProvider
import io.github.rsgarrido.sazanami.data.importing.ImportRecordDiagnostic
import io.github.rsgarrido.sazanami.data.importing.ImportRecordErrorReason
import io.github.rsgarrido.sazanami.data.importing.ImportedMediaType
import io.github.rsgarrido.sazanami.data.importing.ListeningImportAnalysis
import io.github.rsgarrido.sazanami.data.importing.ListeningImportDedupePlan
import io.github.rsgarrido.sazanami.data.importing.ListeningImportSelectionBuilder
import java.time.Instant
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class SpotifyListeningHistoryImportPreview(
    val analysis: ListeningImportAnalysis,
    val dedupe: ListeningImportDedupePlan
)

data class SpotifyImportAnalysisProgress(
    val recordsProcessed: Long
)

class SpotifyImportPreviewException(
    val fileIndex: Int,
    val format: ImportFileFormat,
    val reason: ImportFileFailureReason
) : IllegalStateException("A selected Spotify import input could not be analyzed.")

/**
 * Read-only analysis pass used by the UI. It shares the parser, fingerprint and persisted-evidence
 * contracts with execution, but creates no batch and inserts no listening event.
 */
class SpotifyListeningHistoryImportPreviewer(
    private val repository: ListeningImportRepository,
    private val sourceProfiles: SpotifyImportSourceProfileService,
    private val parser: SpotifyExtendedStreamingParser
) {
    suspend fun preview(
        inputs: List<ListeningImportStreamSource>,
        onProgress: suspend (SpotifyImportAnalysisProgress) -> Unit = {}
    ): SpotifyListeningHistoryImportPreview {
        require(inputs.isNotEmpty())
        val selection = ListeningImportSelectionBuilder()
        val analyses = ArrayList<ListeningImportAnalysis>(inputs.size)
        var recordsProcessed = 0L

        inputs.forEachIndexed { fileIndex, input ->
            val accumulator = Accumulator()
            selection.beginFile()
            val parseResult = parser.parseSuspending(input::openStream) { item ->
                currentCoroutineContext().ensureActive()
                recordsProcessed++
                accumulator.accept(item)
                if (item is SpotifyParseItem.ValidMusic) {
                    selection.accept(SpotifyListeningImportFingerprint.create(item.record))
                }
                if (recordsProcessed % PROGRESS_INTERVAL == 0L) {
                    onProgress(SpotifyImportAnalysisProgress(recordsProcessed))
                }
                SpotifyParseControl.CONTINUE
            }
            if (parseResult !is SpotifyFileParseResult.Completed) {
                val failed = parseResult as SpotifyFileParseResult.Failed
                throw SpotifyImportPreviewException(
                    fileIndex = fileIndex,
                    format = failed.format,
                    reason = failed.reason
                )
            }
            selection.endFile()
            analyses += accumulator.build(parseResult.format)
            onProgress(SpotifyImportAnalysisProgress(recordsProcessed))
        }

        val combined = ListeningImportAnalysis.combine(analyses)
        val selectionPlan = selection.build()
        val profile = sourceProfiles.getOrCreateDefault()
        val dedupe = repository.planDedupe(profile.id, selectionPlan)
        return SpotifyListeningHistoryImportPreview(combined, dedupe)
    }

    private class Accumulator {
        private var total = 0L
        private var music = 0L
        private var podcasts = 0L
        private var audiobooks = 0L
        private var videos = 0L
        private var unknown = 0L
        private var invalid = 0L
        private var zeroMsMusic = 0L
        private var earliest: Instant? = null
        private var latest: Instant? = null
        private val reasons = mutableMapOf<ImportRecordErrorReason, Long>()
        private val examples = mutableListOf<ImportRecordDiagnostic>()
        private var exactUniqueIds = true
        private val uniqueIds = mutableSetOf<String>()

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
                        ImportedMediaType.MUSIC_TRACK -> error("Music must be emitted as valid music.")
                    }
                }
                is SpotifyParseItem.Invalid -> {
                    invalid++
                    val reason = item.diagnostic.reason
                    reasons[reason] = (reasons[reason] ?: 0L) + 1L
                    if (examples.size < ListeningImportAnalysis.DEFAULT_DIAGNOSTIC_LIMIT) {
                        examples += item.diagnostic
                    }
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
            if (uniqueIds.size >= MAX_UNIQUE_IDS) {
                exactUniqueIds = false
                uniqueIds.clear()
            } else {
                uniqueIds += value
            }
        }
    }

    companion object {
        private const val PROGRESS_INTERVAL = 250L
        private const val MAX_UNIQUE_IDS = 100_000
    }
}
