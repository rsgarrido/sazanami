package com.example.cdplaya.controller

import com.example.cdplaya.data.importing.ImportFileFormat
import com.example.cdplaya.data.importing.ImportProvider
import com.example.cdplaya.data.importing.ListeningImportAnalysis
import com.example.cdplaya.data.importing.ListeningImportDedupePlan
import com.example.cdplaya.data.importing.ListeningImportExecutionProgress
import com.example.cdplaya.data.importing.ListeningImportExecutionResult
import com.example.cdplaya.data.importing.spotify.SpotifyListeningHistoryImportPreview
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyListeningHistoryImportControllerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val operations = FakeOperations()
    private val controller = SpotifyListeningHistoryImportController(
        operations = operations,
        scope = scope,
        workDispatcher = Dispatchers.Unconfined
    )

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun initialStateHasNoSelectedFilesAndEntryChecksRecovery() {
        assertEquals(SpotifyImportUiState.Landing, controller.state.value)
        controller.enterWorkflow()
        assertEquals(SpotifyImportUiState.Landing, controller.state.value)
        assertEquals(1, operations.recoveryChecks)
    }

    @Test
    fun selectionSupportsOneOrManyAndCollapsesDuplicateReferences() {
        controller.selectFiles(listOf(file("one")))
        assertEquals(1, (controller.state.value as SpotifyImportUiState.FilesSelected).files.size)

        controller.selectFiles(listOf(file("one"), file("two"), file("one")))
        val selected = controller.state.value as SpotifyImportUiState.FilesSelected
        assertEquals(listOf("one", "two"), selected.files.map { it.transientKey })
    }

    @Test
    fun analyzeSuccessMovesSelectedThroughAnalyzingToPreview() {
        controller.selectFiles(listOf(file("one")))
        controller.analyze()
        val state = controller.state.value as SpotifyImportUiState.Preview
        assertEquals(7L, state.preview.dedupe.newOccurrences)
        assertEquals(1, operations.analyzeCalls)
    }

    @Test
    fun analyzeCancellationReturnsToSelectedFiles() {
        operations.suspendAnalyze = true
        controller.selectFiles(listOf(file("one")))
        controller.analyze()
        assertTrue(controller.state.value is SpotifyImportUiState.Analyzing)
        controller.cancelAnalysis()
        assertTrue(controller.state.value is SpotifyImportUiState.FilesSelected)
    }

    @Test
    fun analyzeFatalFailureUsesSafeErrorState() {
        operations.analyzeFailure = IllegalStateException("private failure")
        controller.selectFiles(listOf(file("one")))
        controller.analyze()
        val state = controller.state.value as SpotifyImportUiState.Error
        assertEquals(SpotifyImportUiError.FILE_ACCESS, state.error)
        assertEquals(SpotifyImportRetryAction.ANALYZE, state.retryAction)
    }

    @Test
    fun importMovesPreviewToActualSuccessAndDoubleTapIsIgnored() {
        controller.selectFiles(listOf(file("one")))
        controller.analyze()
        controller.importHistory()
        controller.importHistory()
        val state = controller.state.value as SpotifyImportUiState.Success
        assertEquals(6L, state.result.newPublished)
        assertEquals(1, operations.executeCalls)
    }

    @Test
    fun importCancellationWaitsForOperationCancellationBeforeCancelledState() {
        operations.suspendExecute = true
        controller.selectFiles(listOf(file("one")))
        controller.analyze()
        controller.importHistory()
        assertTrue(controller.state.value is SpotifyImportUiState.Importing)
        controller.cancelImport()
        assertTrue(controller.state.value is SpotifyImportUiState.Cancelled)
    }

    @Test
    fun importFailureIsRetryableWithoutSuccessClaim() {
        operations.executeFailure = IllegalStateException("database detail")
        controller.selectFiles(listOf(file("one")))
        controller.analyze()
        controller.importHistory()
        val state = controller.state.value as SpotifyImportUiState.Error
        assertEquals(SpotifyImportUiError.IMPORT_FAILED, state.error)
        assertEquals(SpotifyImportRetryAction.IMPORT, state.retryAction)
    }

    @Test
    fun zeroNewPreviewDoesNotExecutePersistence() {
        operations.preview = preview(new = 0, existing = 7)
        controller.selectFiles(listOf(file("one")))
        controller.analyze()
        controller.importHistory()
        assertTrue(controller.state.value is SpotifyImportUiState.Preview)
        assertEquals(0, operations.executeCalls)
    }

    @Test
    fun staleImportBlocksSelectionUntilExistingCleanupFinishes() {
        operations.pendingBatches = 2
        controller.enterWorkflow()
        assertEquals(2, (controller.state.value as SpotifyImportUiState.StaleImportRecovery).pendingBatchCount)
        controller.selectFiles(listOf(file("blocked")))
        assertTrue(controller.state.value is SpotifyImportUiState.StaleImportRecovery)
        controller.cleanStaleImport()
        assertEquals(SpotifyImportUiState.Landing, controller.state.value)
        assertEquals(1, operations.cleanupCalls)
    }

    @Test
    fun secondAnalyzeActionIsIgnoredWhileFirstIsActive() {
        operations.suspendAnalyze = true
        controller.selectFiles(listOf(file("one")))
        controller.analyze()
        controller.analyze()
        assertEquals(1, operations.analyzeCalls)
        controller.cancelAnalysis()
    }

    private fun file(key: String) = object : ListeningHistoryImportFile {
        override val transientKey = key
        override val displayName = "$key.json"
        override fun openStream(): InputStream = ByteArrayInputStream(byteArrayOf())
    }

    private class FakeOperations : SpotifyListeningHistoryImportOperations {
        var pendingBatches = 0
        var recoveryChecks = 0
        var cleanupCalls = 0
        var analyzeCalls = 0
        var executeCalls = 0
        var suspendAnalyze = false
        var suspendExecute = false
        var analyzeFailure: Throwable? = null
        var executeFailure: Throwable? = null
        var preview = preview(new = 7, existing = 0)

        override suspend fun unfinishedBatchCount(): Int {
            recoveryChecks++
            return pendingBatches
        }

        override suspend fun cleanUnfinishedBatches() {
            cleanupCalls++
            pendingBatches = 0
        }

        override suspend fun analyze(
            files: List<ListeningHistoryImportFile>,
            onProgress: suspend (Long) -> Unit
        ): SpotifyListeningHistoryImportPreview {
            analyzeCalls++
            analyzeFailure?.let { throw it }
            if (suspendAnalyze) awaitCancellation()
            onProgress(10)
            return preview
        }

        override suspend fun execute(
            files: List<ListeningHistoryImportFile>,
            onProgress: suspend (ListeningImportExecutionProgress) -> Unit
        ): ListeningImportExecutionResult {
            executeCalls++
            executeFailure?.let { throw it }
            if (suspendExecute) awaitCancellation()
            return result()
        }
    }

    companion object {
        private fun preview(new: Long, existing: Long) = SpotifyListeningHistoryImportPreview(
            analysis = ListeningImportAnalysis(
                provider = ImportProvider.SPOTIFY,
                format = ImportFileFormat.SPOTIFY_EXTENDED_STREAMING_HISTORY,
                totalRecords = 10,
                validMusicRecords = 7,
                podcastRecords = 1,
                audiobookRecords = 0,
                videoRecords = 0,
                unknownRecords = 1,
                invalidRecords = 1,
                zeroMsMusicRecords = 0,
                earliestAt = Instant.ofEpochMilli(1),
                latestAt = Instant.ofEpochMilli(2),
                uniqueExternalTrackIds = 7,
                invalidReasonCounts = emptyMap(),
                diagnosticExamples = emptyList()
            ),
            dedupe = ListeningImportDedupePlan(
                totalImportableRecords = 7,
                overlappingOccurrencesSuppressed = 0,
                newOccurrences = new,
                alreadyImportedOccurrences = existing
            )
        )

        private fun result() = ListeningImportExecutionResult(
            batchId = 1,
            selectedOccurrences = 7,
            overlappingOccurrencesSuppressed = 0,
            alreadyImported = 1,
            newPublished = 6,
            invalid = 1,
            unsupportedMedia = 2,
            sourceRangeStart = Instant.ofEpochMilli(1),
            sourceRangeEnd = Instant.ofEpochMilli(2)
        )
    }
}
