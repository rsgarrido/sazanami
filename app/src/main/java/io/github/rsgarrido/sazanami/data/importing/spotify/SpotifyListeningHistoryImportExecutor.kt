package io.github.rsgarrido.sazanami.data.importing.spotify

import android.database.sqlite.SQLiteConstraintException
import io.github.rsgarrido.sazanami.data.ListeningImportRepository
import io.github.rsgarrido.sazanami.data.importing.ImportOccurrenceKey
import io.github.rsgarrido.sazanami.data.importing.ListeningImportDuplicateOrdinalAssigner
import io.github.rsgarrido.sazanami.data.importing.ListeningImportExecutionPhase
import io.github.rsgarrido.sazanami.data.importing.ListeningImportExecutionProgress
import io.github.rsgarrido.sazanami.data.importing.ListeningImportExecutionResult
import io.github.rsgarrido.sazanami.data.importing.ListeningImportFailureCategory
import io.github.rsgarrido.sazanami.data.importing.ListeningImportSelectionBuilder
import io.github.rsgarrido.sazanami.data.importing.ListeningImportSelectionPlan
import io.github.rsgarrido.sazanami.data.importing.PreparedListeningOccurrence
import io.github.rsgarrido.sazanami.data.local.ListeningImportBatchEntity
import io.github.rsgarrido.sazanami.data.local.ListeningImportBatchStatus
import io.github.rsgarrido.sazanami.data.local.ListeningQualificationPolicy
import java.io.InputStream
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Reopenable, provider-neutral input boundary; Session 4 can adapt SAF Uris to this contract. */
fun interface ListeningImportStreamSource {
    fun openStream(): InputStream
}

class SpotifyImportSourceException(
    val category: ListeningImportFailureCategory
) : IllegalStateException("Spotify import input could not be processed.")

/**
 * Two-pass streaming executor. The first pass establishes max-per-file multiplicity; the second
 * reparses and commits at most [chunkSize] selected occurrence decisions per transaction.
 */
class SpotifyListeningHistoryImportExecutor(
    private val repository: ListeningImportRepository,
    private val sourceProfiles: SpotifyImportSourceProfileService,
    private val parser: SpotifyExtendedStreamingParser,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val batchUuid: () -> String = { UUID.randomUUID().toString() },
    private val createdAppVersion: String = "unknown",
    private val chunkSize: Int = DEFAULT_CHUNK_SIZE
) {
    init {
        require(chunkSize in 1..MAX_CHUNK_SIZE)
    }

    suspend fun execute(
        inputs: List<ListeningImportStreamSource>,
        onProgress: suspend (ListeningImportExecutionProgress) -> Unit = {}
    ): ListeningImportExecutionResult = executionMutex.withLock {
        require(inputs.isNotEmpty()) { "At least one Spotify history input is required." }
        val profile = sourceProfiles.getOrCreateDefault()
        val analysis = analyze(inputs, onProgress)
        val startedAt = nowMillis()
        val batchId = repository.createBatch(
            ListeningImportBatchEntity(
                stableUuid = batchUuid(),
                sourceProfileId = profile.id,
                status = ListeningImportBatchStatus.PENDING,
                parserVersion = PARSER_VERSION,
                qualificationPolicy = ListeningQualificationPolicy.SPOTIFY,
                qualificationRuleVersion = SpotifyImportPolicy.QUALIFICATION_RULE_VERSION,
                startedAt = startedAt,
                completedAt = null,
                sourceRangeStart = analysis.earliestAt?.toEpochMilli(),
                sourceRangeEnd = analysis.latestAt?.toEpochMilli(),
                parsedCount = analysis.records,
                insertedCount = 0,
                duplicateCount = 0,
                ignoredCount = analysis.unsupported +
                    analysis.selection.summary.overlappingOccurrencesSuppressed,
                invalidCount = analysis.invalid,
                exactMatchCount = 0,
                ambiguousMatchCount = 0,
                unmatchedCount = 0,
                qualifiedCount = 0,
                failureCategory = null,
                createdAppVersion = createdAppVersion
            )
        )

        try {
            var recordsProcessed = 0L
            var selected = 0L
            var alreadyImported = 0L
            var newPending = 0L
            var chunksCompleted = 0
            val chunk = ArrayList<PreparedListeningOccurrence>(chunkSize)

            suspend fun flushChunk() {
                if (chunk.isEmpty()) return
                currentCoroutineContext().ensureActive()
                val result = persistWithRaceRetry(batchId, chunk.toList())
                selected += result.selectedOccurrences
                alreadyImported += result.alreadyImported
                newPending += result.newPending
                chunksCompleted++
                chunk.clear()
                onProgress(
                    ListeningImportExecutionProgress(
                        phase = ListeningImportExecutionPhase.IMPORTING,
                        recordsProcessed = recordsProcessed,
                        selectedOccurrences = selected,
                        alreadyImported = alreadyImported,
                        newPending = newPending,
                        chunksCompleted = chunksCompleted
                    )
                )
            }

            inputs.forEachIndexed { fileIndex, input ->
                val ordinals = ListeningImportDuplicateOrdinalAssigner()
                val parseResult = parser.parseSuspending(input::openStream) { item ->
                    currentCoroutineContext().ensureActive()
                    recordsProcessed++
                    if (item is SpotifyParseItem.ValidMusic) {
                        val fingerprint = SpotifyListeningImportFingerprint.create(item.record)
                        val key = ordinals.assign(fingerprint)
                        if (analysis.selection.isOccurrenceOwner(fileIndex, fingerprint)) {
                            chunk += PreparedListeningOccurrence(
                                key = key,
                                record = item.record,
                                policy = SpotifyImportPolicy.evaluate(item.record)
                            )
                            if (chunk.size == chunkSize) flushChunk()
                        }
                    }
                    SpotifyParseControl.CONTINUE
                }
                parseResult.requireCompleted()
            }
            flushChunk()
            check(selected == analysis.selection.summary.selectedMusicOccurrences) {
                "Execution selection changed since the analysis pass."
            }
            onProgress(
                ListeningImportExecutionProgress(
                    phase = ListeningImportExecutionPhase.PUBLISHING,
                    recordsProcessed = recordsProcessed,
                    selectedOccurrences = selected,
                    alreadyImported = alreadyImported,
                    newPending = newPending,
                    chunksCompleted = chunksCompleted
                )
            )
            val published = repository.publishBatch(
                batchId = batchId,
                expectedPendingEventCount = newPending,
                expectedObservedEventCount = selected,
                completedAt = nowMillis()
            )
            check(published.toLong() == newPending)
            onProgress(
                ListeningImportExecutionProgress(
                    phase = ListeningImportExecutionPhase.COMPLETED,
                    recordsProcessed = recordsProcessed,
                    selectedOccurrences = selected,
                    alreadyImported = alreadyImported,
                    newPending = newPending,
                    chunksCompleted = chunksCompleted
                )
            )
            ListeningImportExecutionResult(
                batchId = batchId,
                selectedOccurrences = selected,
                overlappingOccurrencesSuppressed =
                    analysis.selection.summary.overlappingOccurrencesSuppressed,
                alreadyImported = alreadyImported,
                newPublished = newPending,
                invalid = analysis.invalid,
                unsupportedMedia = analysis.unsupported,
                sourceRangeStart = analysis.earliestAt,
                sourceRangeEnd = analysis.latestAt
            )
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                repository.cancelPendingBatch(batchId, nowMillis())
            }
            throw cancelled
        } catch (failure: Throwable) {
            withContext(NonCancellable) {
                repository.failPendingBatch(
                    batchId,
                    nowMillis(),
                    when (failure) {
                        is SpotifyImportSourceException -> failure.category.storageValue
                        else -> ListeningImportFailureCategory.PERSISTENCE.storageValue
                    }
                )
            }
            throw failure
        }
    }

    private suspend fun analyze(
        inputs: List<ListeningImportStreamSource>,
        onProgress: suspend (ListeningImportExecutionProgress) -> Unit
    ): AnalysisPass {
        val builder = ListeningImportSelectionBuilder()
        var records = 0L
        var invalid = 0L
        var unsupported = 0L
        var earliest: Instant? = null
        var latest: Instant? = null
        inputs.forEach { input ->
            builder.beginFile()
            val parseResult = parser.parseSuspending(input::openStream) { item ->
                currentCoroutineContext().ensureActive()
                records++
                when (item) {
                    is SpotifyParseItem.ValidMusic -> {
                        builder.accept(SpotifyListeningImportFingerprint.create(item.record))
                        val at = item.record.sourceEndedAt
                        if (earliest == null || at < earliest) earliest = at
                        if (latest == null || at > latest) latest = at
                    }
                    is SpotifyParseItem.Invalid -> invalid++
                    is SpotifyParseItem.UnsupportedMedia -> unsupported++
                }
                SpotifyParseControl.CONTINUE
            }
            parseResult.requireCompleted()
            builder.endFile()
            onProgress(
                ListeningImportExecutionProgress(
                    ListeningImportExecutionPhase.ANALYZING,
                    records,
                    0,
                    0,
                    0,
                    0
                )
            )
        }
        return AnalysisPass(builder.build(), records, invalid, unsupported, earliest, latest)
    }

    private suspend fun persistWithRaceRetry(
        batchId: Long,
        chunk: List<PreparedListeningOccurrence>
    ) = try {
        repository.persistSpotifyChunk(batchId, chunk)
    } catch (_: SQLiteConstraintException) {
        // The first transaction was rolled back. Recheck once so a concurrently committed
        // occurrence or external mapping can become authoritative.
        repository.persistSpotifyChunk(batchId, chunk)
    }

    private fun SpotifyFileParseResult.requireCompleted() {
        if (this !is SpotifyFileParseResult.Completed) {
            throw SpotifyImportSourceException(ListeningImportFailureCategory.SOURCE_READ)
        }
    }

    private data class AnalysisPass(
        val selection: ListeningImportSelectionPlan,
        val records: Long,
        val invalid: Long,
        val unsupported: Long,
        val earliestAt: Instant?,
        val latestAt: Instant?
    )

    companion object {
        const val DEFAULT_CHUNK_SIZE = 500
        const val MAX_CHUNK_SIZE = 500
        const val PARSER_VERSION = 1

        private val executionMutex = Mutex()
    }
}
