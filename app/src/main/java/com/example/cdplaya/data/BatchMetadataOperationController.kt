package com.example.cdplaya.data

import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class BatchWriteOutcome {
    SUCCESS,
    PARTIAL_SUCCESS,
    CANCELLED,
    FAILED
}

enum class BatchTerminalOutcome {
    SUCCESS,
    PARTIAL_SUCCESS,
    CANCELLED,
    FAILED,
    PERMISSION_DENIED,
    REFRESH_WARNING
}

enum class BatchPostWriteStageStatus {
    NOT_REQUIRED,
    WAITING,
    SUCCESS,
    TIMED_OUT,
    FAILED,
    CANCELLED
}

data class BatchPostWriteStageResult(
    val status: BatchPostWriteStageStatus,
    val message: String? = null
) {
    val hasWarning: Boolean
        get() = status == BatchPostWriteStageStatus.TIMED_OUT ||
            status == BatchPostWriteStageStatus.FAILED ||
            status == BatchPostWriteStageStatus.CANCELLED

    companion object {
        val NotRequired = BatchPostWriteStageResult(BatchPostWriteStageStatus.NOT_REQUIRED)
        val Waiting = BatchPostWriteStageResult(BatchPostWriteStageStatus.WAITING)
        val Success = BatchPostWriteStageResult(BatchPostWriteStageStatus.SUCCESS)
    }
}

sealed interface BatchMetadataOperationState {
    val operationId: String

    data class Interrupted(
        override val operationId: String
    ) : BatchMetadataOperationState

    data class Preparing(
        override val operationId: String,
        val plan: BatchMetadataPlan
    ) : BatchMetadataOperationState

    data class AwaitingPermission(
        override val operationId: String,
        val plan: BatchMetadataPlan,
        val permissionBatches: List<List<Uri>>,
        val batchIndex: Int,
        val requestLaunched: Boolean
    ) : BatchMetadataOperationState {
        val currentUris: List<Uri> get() = permissionBatches[batchIndex]
    }

    data class Running(
        override val operationId: String,
        val plan: BatchMetadataPlan,
        val progress: BatchMetadataProgress,
        val cancellationRequested: Boolean = false
    ) : BatchMetadataOperationState

    data class PostProcessing(
        override val operationId: String,
        val plan: BatchMetadataPlan,
        val result: BatchMetadataExecutionResult,
        val scan: BatchPostWriteStageResult,
        val refresh: BatchPostWriteStageResult
    ) : BatchMetadataOperationState

    data class Complete(
        override val operationId: String,
        val plan: BatchMetadataPlan,
        val result: BatchMetadataExecutionResult,
        val scan: BatchPostWriteStageResult,
        val refresh: BatchPostWriteStageResult,
        val terminalOutcome: BatchTerminalOutcome
    ) : BatchMetadataOperationState
}

fun interface BatchArtworkPreparer {
    suspend fun prepare(uri: Uri): PreparedBatchArtwork?
}

fun interface BatchPlanExecutor {
    suspend fun execute(
        plan: BatchMetadataPlan,
        songs: List<Song>,
        artwork: PreparedBatchArtwork?,
        cancellationSignal: BatchCancellationSignal,
        onProgress: (BatchMetadataProgress) -> Unit
    ): BatchMetadataExecutionResult
}

fun interface BatchSuccessfulTargetScanner {
    suspend fun scan(songs: List<Song>): BatchPostWriteStageResult
}

fun interface BatchSuccessfulTargetRefresher {
    suspend fun refresh(songs: List<Song>): BatchPostWriteStageResult
}

interface BatchInterruptionStore {
    fun interruptedOperationId(): String?
    fun markActive(operationId: String)
    fun clear(operationId: String)
}

internal class BatchMetadataOperationController(
    private val scope: CoroutineScope,
    private val artworkPreparer: BatchArtworkPreparer,
    private val executor: BatchPlanExecutor,
    private val scanner: BatchSuccessfulTargetScanner,
    private val refresher: BatchSuccessfulTargetRefresher,
    private val interruptionStore: BatchInterruptionStore,
    private val permissionBatchSize: Int = DEFAULT_PERMISSION_BATCH_SIZE,
    private val idFactory: () -> String = { UUID.randomUUID().toString() }
) {
    private val mutableState = MutableStateFlow<BatchMetadataOperationState?>(
        interruptionStore.interruptedOperationId()?.let(BatchMetadataOperationState::Interrupted)
    )
    val state: StateFlow<BatchMetadataOperationState?> = mutableState.asStateFlow()

    private var active: ActiveOperation? = null
    private var operationJob: Job? = null
    private var cancellationSignal: BatchCancellationSignal? = null

    fun begin(
        reviewedPlan: BatchMetadataPlan,
        songs: List<Song>,
        artworkUri: Uri?,
        requiresWritePermission: Boolean
    ) {
        if (mutableState.value.isBusy()) return
        val operationId = idFactory()
        val frozenPlan = reviewedPlan.frozenCopy()
        interruptionStore.markActive(operationId)
        mutableState.value = BatchMetadataOperationState.Preparing(operationId, frozenPlan)
        operationJob = scope.launch {
            val preparedArtwork = try {
                if (artworkUri == null) null else artworkPreparer.prepare(artworkUri)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                null
            }
            if ((mutableState.value as? BatchMetadataOperationState.Preparing)
                    ?.operationId != operationId
            ) return@launch
            if (artworkUri != null && preparedArtwork == null) {
                finishBeforeWrites(
                    operationId,
                    frozenPlan,
                    BatchTargetStatus.NOT_PROCESSED,
                    "Replacement artwork could not be prepared. Reselect artwork before retrying."
                )
                return@launch
            }
            active = ActiveOperation(
                rootPlan = frozenPlan,
                attemptPlan = frozenPlan,
                songs = songs.toList(),
                preparedArtwork = preparedArtwork?.frozenCopy(),
                aggregateResult = null,
                requiresWritePermission = requiresWritePermission,
                previousScan = BatchPostWriteStageResult.NotRequired,
                previousRefresh = BatchPostWriteStageResult.NotRequired
            )
            requestPermissionOrExecute(operationId)
        }
    }

    fun consumePermissionRequest(operationId: String, batchIndex: Int): List<Uri>? {
        val awaiting = mutableState.value as? BatchMetadataOperationState.AwaitingPermission
            ?: return null
        if (awaiting.operationId != operationId || awaiting.batchIndex != batchIndex ||
            awaiting.requestLaunched
        ) return null
        mutableState.value = awaiting.copy(requestLaunched = true)
        return awaiting.currentUris
    }

    fun onPermissionResult(
        operationId: String,
        batchIndex: Int,
        granted: Boolean,
        denialReason: String? = null
    ) {
        val awaiting = mutableState.value as? BatchMetadataOperationState.AwaitingPermission
            ?: return
        if (awaiting.operationId != operationId || awaiting.batchIndex != batchIndex) return
        if (!granted) {
            finishBeforeWrites(
                operationId,
                awaiting.plan,
                BatchTargetStatus.PERMISSION_DENIED,
                denialReason ?: "Write permission was denied."
            )
            return
        }
        val nextIndex = batchIndex + 1
        if (nextIndex < awaiting.permissionBatches.size) {
            mutableState.value = awaiting.copy(batchIndex = nextIndex, requestLaunched = false)
        } else {
            executeActive(operationId)
        }
    }

    fun cancel() {
        when (val current = mutableState.value) {
            is BatchMetadataOperationState.Preparing,
            is BatchMetadataOperationState.AwaitingPermission -> {
                val plan = when (current) {
                    is BatchMetadataOperationState.Preparing -> current.plan
                    is BatchMetadataOperationState.AwaitingPermission -> current.plan
                    else -> error("Handled above")
                }
                operationJob?.cancel()
                finishBeforeWrites(
                    current.operationId,
                    plan,
                    BatchTargetStatus.NOT_PROCESSED,
                    "Cancelled before this file started.",
                    wasCancelled = true
                )
            }
            is BatchMetadataOperationState.Running -> {
                cancellationSignal?.cancel()
                mutableState.value = current.copy(cancellationRequested = true)
            }
            is BatchMetadataOperationState.PostProcessing -> {
                val cancelledScan = if (current.scan.status == BatchPostWriteStageStatus.SUCCESS) {
                    current.scan
                } else {
                    BatchPostWriteStageResult(
                        BatchPostWriteStageStatus.CANCELLED,
                        "Stopped waiting for MediaStore. Metadata writes remain verified."
                    )
                }
                complete(
                    current.operationId,
                    current.plan,
                    current.result,
                    cancelledScan,
                    BatchPostWriteStageResult(
                        BatchPostWriteStageStatus.CANCELLED,
                        "The library view may remain stale until the next rescan."
                    )
                )
            }
            is BatchMetadataOperationState.Complete,
            is BatchMetadataOperationState.Interrupted,
            null -> Unit
        }
    }

    fun retryFailed(currentSongs: List<Song>) {
        val complete = mutableState.value as? BatchMetadataOperationState.Complete ?: return
        val targets = complete.result.targetResults
            .filter(BatchTargetResult::isRetryableFailure)
            .map(BatchTargetResult::target)
        startRetry(complete, targets, currentSongs)
    }

    fun continueUnprocessed(currentSongs: List<Song>) {
        val complete = mutableState.value as? BatchMetadataOperationState.Complete ?: return
        val targets = complete.result.targetResults
            .filter { it.status == BatchTargetStatus.NOT_PROCESSED }
            .map(BatchTargetResult::target)
        startRetry(complete, targets, currentSongs)
    }

    fun retryPostWrite() {
        val complete = mutableState.value as? BatchMetadataOperationState.Complete ?: return
        if (!complete.scan.hasWarning && !complete.refresh.hasWarning) return
        val successfulSongs = complete.result.successfulSongs()
        if (successfulSongs.isEmpty()) return
        val operationId = idFactory()
        active = active?.copy(songs = successfulSongs)
        interruptionStore.markActive(operationId)
        postProcess(operationId, complete.plan, complete.result, successfulSongs)
    }

    fun dismiss() {
        val current = mutableState.value ?: return
        if (current.isBusy()) return
        interruptionStore.clear(current.operationId)
        operationJob?.cancel()
        operationJob = null
        cancellationSignal = null
        active = null
        mutableState.value = null
    }

    private fun requestPermissionOrExecute(operationId: String) {
        val operation = active ?: return
        if (!isCurrent(operationId)) return
        val permissionBatches = if (operation.requiresWritePermission) {
            buildBatchPermissionGroups(
                operation.attemptPlan,
                operation.songs,
                permissionBatchSize
            )
        } else {
            emptyList()
        }
        if (permissionBatches.isEmpty()) {
            executeActive(operationId)
        } else {
            mutableState.value = BatchMetadataOperationState.AwaitingPermission(
                operationId,
                operation.attemptPlan,
                permissionBatches,
                batchIndex = 0,
                requestLaunched = false
            )
        }
    }

    private fun executeActive(operationId: String) {
        val operation = active ?: return
        if (!isCurrent(operationId)) return
        val signal = BatchCancellationSignal()
        cancellationSignal = signal
        mutableState.value = BatchMetadataOperationState.Running(
            operationId,
            operation.attemptPlan,
            BatchMetadataProgress(0, operation.attemptPlan.selectedTrackCount, null)
        )
        operationJob = scope.launch {
            val attemptResult = withContext(Dispatchers.IO) {
                executor.execute(
                    operation.attemptPlan,
                    operation.songs,
                    operation.preparedArtwork,
                    signal
                ) { progress ->
                    scope.launch {
                        val running = mutableState.value as? BatchMetadataOperationState.Running
                        if (running?.operationId == operationId) {
                            mutableState.value = running.copy(progress = progress)
                        }
                    }
                }
            }
            if (!isCurrent(operationId)) return@launch
            cancellationSignal = null
            val aggregate = operation.aggregateResult?.mergeAttempt(attemptResult) ?: attemptResult
            active = operation.copy(aggregateResult = aggregate)
            val newlySuccessfulSongs = attemptResult.successfulSongs()
            if (newlySuccessfulSongs.isEmpty()) {
                complete(
                    operationId,
                    operation.rootPlan,
                    aggregate,
                    operation.previousScan,
                    operation.previousRefresh
                )
            } else {
                // Retrying writes may follow an earlier scan warning. Rescan every successful
                // target, but never rewrite it, so the final refresh status covers the aggregate.
                postProcess(
                    operationId,
                    operation.rootPlan,
                    aggregate,
                    aggregate.successfulSongs()
                )
            }
        }
    }

    private fun postProcess(
        operationId: String,
        plan: BatchMetadataPlan,
        result: BatchMetadataExecutionResult,
        successfulSongs: List<Song>
    ) {
        mutableState.value = BatchMetadataOperationState.PostProcessing(
            operationId,
            plan,
            result,
            BatchPostWriteStageResult.Waiting,
            BatchPostWriteStageResult.Waiting
        )
        operationJob = scope.launch {
            val scanResult = try {
                scanner.scan(successfulSongs)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                BatchPostWriteStageResult(
                    BatchPostWriteStageStatus.FAILED,
                    exception.message ?: "MediaStore scan failed."
                )
            }
            val postScan = mutableState.value as? BatchMetadataOperationState.PostProcessing
            if (postScan?.operationId != operationId) return@launch
            mutableState.value = postScan.copy(scan = scanResult)
            val refreshResult = try {
                refresher.refresh(successfulSongs)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                BatchPostWriteStageResult(
                    BatchPostWriteStageStatus.FAILED,
                    exception.message ?: "Library refresh failed."
                )
            }
            if ((mutableState.value as? BatchMetadataOperationState.PostProcessing)
                    ?.operationId != operationId
            ) return@launch
            complete(operationId, plan, result, scanResult, refreshResult)
        }
    }

    private fun startRetry(
        complete: BatchMetadataOperationState.Complete,
        targets: List<BatchMetadataTargetId>,
        currentSongs: List<Song>
    ) {
        if (targets.isEmpty()) return
        val previous = active ?: return
        val operationId = idFactory()
        val retryPlan = complete.plan.copy(selectedTargets = targets.map(BatchMetadataTargetId::copy))
        interruptionStore.markActive(operationId)
        active = previous.copy(
            attemptPlan = retryPlan,
            songs = currentSongs.toList(),
            aggregateResult = complete.result
        )
        mutableState.value = BatchMetadataOperationState.Preparing(operationId, retryPlan)
        requestPermissionOrExecute(operationId)
    }

    private fun finishBeforeWrites(
        operationId: String,
        plan: BatchMetadataPlan,
        status: BatchTargetStatus,
        reason: String,
        wasCancelled: Boolean = false
    ) {
        val attemptResult = BatchMetadataExecutionResult(
            plan,
            plan.selectedTargets.map { BatchTargetResult(it, status, reason) },
            wasCancelled
        )
        val operation = active
        val result = operation?.aggregateResult?.mergeAttempt(attemptResult) ?: attemptResult
        active = operation?.copy(aggregateResult = result)
        complete(
            operationId,
            operation?.rootPlan ?: plan,
            result,
            BatchPostWriteStageResult.NotRequired,
            BatchPostWriteStageResult.NotRequired
        )
    }

    private fun complete(
        operationId: String,
        plan: BatchMetadataPlan,
        result: BatchMetadataExecutionResult,
        scan: BatchPostWriteStageResult,
        refresh: BatchPostWriteStageResult
    ) {
        if (!isCurrent(operationId)) return
        interruptionStore.clear(operationId)
        active = active?.copy(previousScan = scan, previousRefresh = refresh)
        mutableState.value = BatchMetadataOperationState.Complete(
            operationId,
            plan,
            result,
            scan,
            refresh,
            terminalOutcome(result, scan, refresh)
        )
    }

    private fun isCurrent(operationId: String): Boolean =
        mutableState.value?.operationId == operationId

    private data class ActiveOperation(
        val rootPlan: BatchMetadataPlan,
        val attemptPlan: BatchMetadataPlan,
        val songs: List<Song>,
        val preparedArtwork: PreparedBatchArtwork?,
        val aggregateResult: BatchMetadataExecutionResult?,
        val requiresWritePermission: Boolean,
        val previousScan: BatchPostWriteStageResult,
        val previousRefresh: BatchPostWriteStageResult
    )

    companion object {
        internal const val DEFAULT_PERMISSION_BATCH_SIZE = 500
    }
}

internal fun buildBatchPermissionGroups(
    plan: BatchMetadataPlan,
    songs: List<Song>,
    maximumBatchSize: Int
): List<List<Uri>> {
    require(maximumBatchSize > 0)
    val selectedKeys = plan.selectedTargets.mapTo(mutableSetOf()) { it.referenceKey }
    return songs.asSequence()
        .filter { it.membershipKey() in selectedKeys }
        .map(Song::uri)
        .distinct()
        .toList()
        .chunked(maximumBatchSize)
}

internal fun BatchTargetResult.isRetryableFailure(): Boolean = when (status) {
    BatchTargetStatus.WRITE_FAILED,
    BatchTargetStatus.VERIFICATION_FAILED,
    BatchTargetStatus.PERMISSION_DENIED -> true
    BatchTargetStatus.SUCCESS,
    BatchTargetStatus.MISSING,
    BatchTargetStatus.IDENTITY_MISMATCH,
    BatchTargetStatus.UNSUPPORTED,
    BatchTargetStatus.NOT_PROCESSED -> false
}

internal fun BatchMetadataExecutionResult.successfulSongs(): List<Song> = targetResults
    .filter { it.status == BatchTargetStatus.SUCCESS }
    .mapNotNull(BatchTargetResult::resolvedSong)
    .distinctBy(Song::membershipKey)

internal fun BatchMetadataExecutionResult.mergeAttempt(
    attempt: BatchMetadataExecutionResult
): BatchMetadataExecutionResult {
    val replacements = attempt.targetResults.associateBy { it.target.referenceKey }
    val merged = targetResults.map { previous ->
        replacements[previous.target.referenceKey] ?: previous
    }
    return copy(
        targetResults = merged,
        wasCancelled = merged.any { it.status == BatchTargetStatus.NOT_PROCESSED }
    )
}

internal fun terminalOutcome(
    result: BatchMetadataExecutionResult,
    scan: BatchPostWriteStageResult,
    refresh: BatchPostWriteStageResult
): BatchTerminalOutcome {
    if (scan.hasWarning || refresh.hasWarning) return BatchTerminalOutcome.REFRESH_WARNING
    if (result.successCount == 0 &&
        result.targetResults.any { it.status == BatchTargetStatus.PERMISSION_DENIED }
    ) {
        return BatchTerminalOutcome.PERMISSION_DENIED
    }
    return when (result.writeOutcome) {
        BatchWriteOutcome.SUCCESS -> BatchTerminalOutcome.SUCCESS
        BatchWriteOutcome.PARTIAL_SUCCESS -> BatchTerminalOutcome.PARTIAL_SUCCESS
        BatchWriteOutcome.CANCELLED -> BatchTerminalOutcome.CANCELLED
        BatchWriteOutcome.FAILED -> BatchTerminalOutcome.FAILED
    }
}

internal val BatchMetadataExecutionResult.writeOutcome: BatchWriteOutcome
    get() = when {
        targetResults.isNotEmpty() && targetResults.all { it.status == BatchTargetStatus.SUCCESS } ->
            BatchWriteOutcome.SUCCESS
        wasCancelled && targetResults.any { it.status == BatchTargetStatus.NOT_PROCESSED } ->
            BatchWriteOutcome.CANCELLED
        successCount > 0 -> BatchWriteOutcome.PARTIAL_SUCCESS
        else -> BatchWriteOutcome.FAILED
    }

private fun BatchMetadataOperationState?.isBusy(): Boolean = when (this) {
    is BatchMetadataOperationState.Preparing,
    is BatchMetadataOperationState.AwaitingPermission,
    is BatchMetadataOperationState.Running,
    is BatchMetadataOperationState.PostProcessing -> true
    is BatchMetadataOperationState.Complete,
    is BatchMetadataOperationState.Interrupted,
    null -> false
}

private fun PreparedBatchArtwork.frozenCopy(): PreparedBatchArtwork =
    copy(bytes = bytes.copyOf())
