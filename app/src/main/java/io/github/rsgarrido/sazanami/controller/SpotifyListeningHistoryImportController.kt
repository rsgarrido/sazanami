package io.github.rsgarrido.sazanami.controller

import io.github.rsgarrido.sazanami.data.ListeningImportRepository
import io.github.rsgarrido.sazanami.data.importing.ImportFileFailureReason
import io.github.rsgarrido.sazanami.data.importing.ImportFileFormat
import io.github.rsgarrido.sazanami.data.importing.ListeningImportExecutionProgress
import io.github.rsgarrido.sazanami.data.importing.ListeningImportExecutionResult
import io.github.rsgarrido.sazanami.data.importing.spotify.ListeningImportStreamSource
import io.github.rsgarrido.sazanami.data.importing.spotify.SpotifyImportPreviewException
import io.github.rsgarrido.sazanami.data.importing.spotify.SpotifyImportSourceException
import io.github.rsgarrido.sazanami.data.importing.spotify.SpotifyImportSourceProfileService
import io.github.rsgarrido.sazanami.data.importing.spotify.SpotifyListeningHistoryImportExecutor
import io.github.rsgarrido.sazanami.data.importing.spotify.SpotifyListeningHistoryImportPreview
import io.github.rsgarrido.sazanami.data.importing.spotify.SpotifyListeningHistoryImportPreviewer
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface ListeningHistoryImportFile {
    /** Transient identity used only to collapse duplicate picker results. */
    val transientKey: String
    val displayName: String
    fun openStream(): InputStream
}

enum class SpotifyImportUiError {
    ACCOUNT_DATA_FORMAT,
    UNKNOWN_JSON,
    MALFORMED_JSON,
    FILE_ACCESS,
    NO_MUSIC,
    IMPORT_FAILED
}

enum class SpotifyImportRetryAction { ANALYZE, IMPORT }

sealed interface SpotifyImportUiState {
    data object Landing : SpotifyImportUiState
    data object CheckingRecovery : SpotifyImportUiState
    data class StaleImportRecovery(
        val pendingBatchCount: Int?,
        val cleanupFailed: Boolean = false
    ) : SpotifyImportUiState
    data object CleaningStaleImport : SpotifyImportUiState
    data class FilesSelected(
        val files: List<ListeningHistoryImportFile>,
        val cancellationMessage: Boolean = false
    ) : SpotifyImportUiState
    data class Analyzing(
        val files: List<ListeningHistoryImportFile>,
        val recordsProcessed: Long = 0L
    ) : SpotifyImportUiState
    data class Preview(
        val files: List<ListeningHistoryImportFile>,
        val preview: SpotifyListeningHistoryImportPreview
    ) : SpotifyImportUiState
    data class Importing(
        val files: List<ListeningHistoryImportFile>,
        val preview: SpotifyListeningHistoryImportPreview,
        val progress: ListeningImportExecutionProgress? = null
    ) : SpotifyImportUiState
    data class Cancelling(val files: List<ListeningHistoryImportFile>) : SpotifyImportUiState
    data class Cancelled(val files: List<ListeningHistoryImportFile>) : SpotifyImportUiState
    data class Success(val result: ListeningImportExecutionResult) : SpotifyImportUiState
    data class Error(
        val files: List<ListeningHistoryImportFile>,
        val error: SpotifyImportUiError,
        val failedDisplayName: String? = null,
        val retryAction: SpotifyImportRetryAction
    ) : SpotifyImportUiState
}

interface SpotifyListeningHistoryImportOperations {
    suspend fun unfinishedBatchCount(): Int
    suspend fun cleanUnfinishedBatches()
    suspend fun analyze(
        files: List<ListeningHistoryImportFile>,
        onProgress: suspend (Long) -> Unit
    ): SpotifyListeningHistoryImportPreview
    suspend fun execute(
        files: List<ListeningHistoryImportFile>,
        onProgress: suspend (ListeningImportExecutionProgress) -> Unit
    ): ListeningImportExecutionResult
}

class DefaultSpotifyListeningHistoryImportOperations(
    private val repository: ListeningImportRepository,
    private val previewer: SpotifyListeningHistoryImportPreviewer,
    private val executor: SpotifyListeningHistoryImportExecutor,
    private val nowMillis: () -> Long = System::currentTimeMillis
) : SpotifyListeningHistoryImportOperations {
    override suspend fun unfinishedBatchCount(): Int {
        val source = repository.getSourceProfile(SpotifyImportSourceProfileService.DEFAULT_STABLE_UUID)
            ?: return 0
        return repository.getPendingBatchIdsForSourceProfile(source.id).size
    }

    override suspend fun cleanUnfinishedBatches() {
        val source = repository.getSourceProfile(SpotifyImportSourceProfileService.DEFAULT_STABLE_UUID)
            ?: return
        repository.getPendingBatchIdsForSourceProfile(source.id).forEach { batchId ->
            repository.cancelPendingBatch(batchId, nowMillis())
        }
        check(repository.getPendingBatchIdsForSourceProfile(source.id).isEmpty()) {
            "Unfinished Spotify import cleanup did not reach a stable state."
        }
    }

    override suspend fun analyze(
        files: List<ListeningHistoryImportFile>,
        onProgress: suspend (Long) -> Unit
    ): SpotifyListeningHistoryImportPreview = previewer.preview(
        inputs = files.map { file -> ListeningImportStreamSource(file::openStream) },
        onProgress = { progress -> onProgress(progress.recordsProcessed) }
    )

    override suspend fun execute(
        files: List<ListeningHistoryImportFile>,
        onProgress: suspend (ListeningImportExecutionProgress) -> Unit
    ): ListeningImportExecutionResult = executor.execute(
        inputs = files.map { file -> ListeningImportStreamSource(file::openStream) },
        onProgress = onProgress
    )
}

class SpotifyListeningHistoryImportController(
    private val operations: SpotifyListeningHistoryImportOperations,
    private val scope: CoroutineScope,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val _state = MutableStateFlow<SpotifyImportUiState>(SpotifyImportUiState.Landing)
    val state: StateFlow<SpotifyImportUiState> = _state.asStateFlow()
    private var operationJob: Job? = null

    fun enterWorkflow() {
        if (_state.value != SpotifyImportUiState.Landing) return
        _state.value = SpotifyImportUiState.CheckingRecovery
        operationJob = scope.launch {
            try {
                val pending = withContext(workDispatcher) { operations.unfinishedBatchCount() }
                _state.value = if (pending == 0) {
                    SpotifyImportUiState.Landing
                } else {
                    SpotifyImportUiState.StaleImportRecovery(pending)
                }
            } catch (_: Throwable) {
                _state.value = SpotifyImportUiState.StaleImportRecovery(pendingBatchCount = null)
            }
        }
    }

    fun selectFiles(files: List<ListeningHistoryImportFile>) {
        if (_state.value !is SpotifyImportUiState.Landing &&
            _state.value !is SpotifyImportUiState.FilesSelected &&
            _state.value !is SpotifyImportUiState.Preview &&
            _state.value !is SpotifyImportUiState.Error &&
            _state.value !is SpotifyImportUiState.Cancelled &&
            _state.value !is SpotifyImportUiState.Success
        ) return
        val distinct = files.distinctBy(ListeningHistoryImportFile::transientKey)
        if (distinct.isNotEmpty()) _state.value = SpotifyImportUiState.FilesSelected(distinct)
    }

    fun analyze() {
        val selected = _state.value as? SpotifyImportUiState.FilesSelected ?: return
        val files = selected.files
        _state.value = SpotifyImportUiState.Analyzing(files)
        operationJob = scope.launch {
            try {
                val preview = withContext(workDispatcher) {
                    operations.analyze(files) { processed ->
                        val current = _state.value
                        if (current is SpotifyImportUiState.Analyzing && current.files === files) {
                            _state.value = current.copy(recordsProcessed = processed)
                        }
                    }
                }
                _state.value = if (preview.analysis.validMusicRecords == 0L) {
                    SpotifyImportUiState.Error(
                        files,
                        SpotifyImportUiError.NO_MUSIC,
                        retryAction = SpotifyImportRetryAction.ANALYZE
                    )
                } else {
                    SpotifyImportUiState.Preview(files, preview)
                }
            } catch (_: CancellationException) {
                _state.value = SpotifyImportUiState.FilesSelected(files)
            } catch (failure: Throwable) {
                _state.value = analysisError(files, failure)
            }
        }
    }

    fun cancelAnalysis() {
        if (_state.value !is SpotifyImportUiState.Analyzing) return
        operationJob?.cancel()
    }

    fun importHistory() {
        val previewState = _state.value as? SpotifyImportUiState.Preview ?: return
        if (previewState.preview.dedupe.newOccurrences == 0L) return
        val files = previewState.files
        _state.value = SpotifyImportUiState.Importing(files, previewState.preview)
        operationJob = scope.launch {
            try {
                val result = withContext(workDispatcher) {
                    operations.execute(files) { progress ->
                        val current = _state.value
                        if (current is SpotifyImportUiState.Importing) {
                            _state.value = current.copy(progress = progress)
                        }
                    }
                }
                _state.value = SpotifyImportUiState.Success(result)
            } catch (_: CancellationException) {
                _state.value = SpotifyImportUiState.Cancelled(files)
            } catch (failure: Throwable) {
                _state.value = SpotifyImportUiState.Error(
                    files = files,
                    error = if (failure is SpotifyImportSourceException) {
                        SpotifyImportUiError.FILE_ACCESS
                    } else {
                        SpotifyImportUiError.IMPORT_FAILED
                    },
                    retryAction = SpotifyImportRetryAction.IMPORT
                )
            }
        }
    }

    fun cancelImport() {
        val current = _state.value as? SpotifyImportUiState.Importing ?: return
        _state.value = SpotifyImportUiState.Cancelling(current.files)
        operationJob?.cancel()
    }

    fun retry() {
        val error = _state.value as? SpotifyImportUiState.Error ?: return
        _state.value = SpotifyImportUiState.FilesSelected(error.files)
        when (error.retryAction) {
            SpotifyImportRetryAction.ANALYZE -> analyze()
            SpotifyImportRetryAction.IMPORT -> analyze()
        }
    }

    fun cleanStaleImport() {
        if (_state.value !is SpotifyImportUiState.StaleImportRecovery) return
        _state.value = SpotifyImportUiState.CleaningStaleImport
        operationJob = scope.launch {
            try {
                withContext(workDispatcher) { operations.cleanUnfinishedBatches() }
                _state.value = SpotifyImportUiState.Landing
            } catch (_: Throwable) {
                val pending = runCatching {
                    withContext(workDispatcher) { operations.unfinishedBatchCount() }
                }.getOrNull()
                _state.value = SpotifyImportUiState.StaleImportRecovery(
                    pendingBatchCount = pending,
                    cleanupFailed = true
                )
            }
        }
    }

    fun returnToSelectedFiles() {
        when (val current = _state.value) {
            is SpotifyImportUiState.Preview -> _state.value = SpotifyImportUiState.FilesSelected(current.files)
            is SpotifyImportUiState.Error -> _state.value = SpotifyImportUiState.FilesSelected(current.files)
            is SpotifyImportUiState.Cancelled -> _state.value = SpotifyImportUiState.FilesSelected(current.files)
            else -> Unit
        }
    }

    fun reset() {
        if (_state.value is SpotifyImportUiState.Analyzing ||
            _state.value is SpotifyImportUiState.Importing ||
            _state.value is SpotifyImportUiState.Cancelling ||
            _state.value is SpotifyImportUiState.CleaningStaleImport ||
            _state.value is SpotifyImportUiState.CheckingRecovery
        ) return
        operationJob = null
        _state.value = SpotifyImportUiState.Landing
    }

    private fun analysisError(
        files: List<ListeningHistoryImportFile>,
        failure: Throwable
    ): SpotifyImportUiState.Error {
        val previewFailure = failure as? SpotifyImportPreviewException
        val error = when {
            previewFailure?.format == ImportFileFormat.SPOTIFY_BASIC_ACCOUNT_HISTORY_UNSUPPORTED ->
                SpotifyImportUiError.ACCOUNT_DATA_FORMAT
            previewFailure?.reason == ImportFileFailureReason.MALFORMED_JSON ->
                SpotifyImportUiError.MALFORMED_JSON
            previewFailure?.reason == ImportFileFailureReason.UNREADABLE_STREAM ->
                SpotifyImportUiError.FILE_ACCESS
            previewFailure?.reason == ImportFileFailureReason.UNKNOWN_FORMAT ||
                previewFailure?.reason == ImportFileFailureReason.UNSUPPORTED_FORMAT ->
                SpotifyImportUiError.UNKNOWN_JSON
            else -> SpotifyImportUiError.FILE_ACCESS
        }
        return SpotifyImportUiState.Error(
            files = files,
            error = error,
            failedDisplayName = previewFailure?.fileIndex?.let(files::getOrNull)?.displayName,
            retryAction = SpotifyImportRetryAction.ANALYZE
        )
    }
}
