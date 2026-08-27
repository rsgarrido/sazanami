package com.example.cdplaya.data

import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

class BatchMetadataReliabilityTest {
    @Test
    fun `retry executes only retryable failures and never rewrites successes`() {
        val attempts = mutableListOf<List<String>>()
        val controller = controller(execute = { plan, songs, _, _, _ ->
            attempts += plan.selectedTargets.map(BatchMetadataTargetId::displayName)
            if (attempts.size == 1) {
                result(
                    plan,
                    targetResult(plan, "one", BatchTargetStatus.SUCCESS, songs),
                    targetResult(plan, "two", BatchTargetStatus.WRITE_FAILED, songs),
                    targetResult(plan, "three", BatchTargetStatus.MISSING, songs)
                )
            } else {
                result(plan, targetResult(plan, "two", BatchTargetStatus.SUCCESS, songs))
            }
        })
        val songs = listOf(song("one"), song("two"), song("three"))

        controller.begin(plan("one", "two", "three"), songs, null, false)
        controller.awaitState<BatchMetadataOperationState.Complete>()
        controller.retryFailed(songs)
        controller.awaitState<BatchMetadataOperationState.Complete>()

        assertEquals(listOf(listOf("one", "two", "three"), listOf("two")), attempts)
        val complete = controller.state.value as BatchMetadataOperationState.Complete
        assertEquals(2, complete.result.successCount)
        assertEquals(BatchTargetStatus.MISSING,
            complete.result.targetResults.single { it.target.displayName == "three" }.status)
    }

    @Test
    fun `retry reuses frozen intent and fresh target resolution can reject a mismatch`() {
        val mutableGenre = mutableListOf("Rock")
        val reviewed = plan("one", values = mutableGenre)
        val capturedValues = mutableListOf<List<String>>()
        var attempt = 0
        val controller = controller(execute = { retryPlan, songs, _, _, _ ->
            capturedValues += (retryPlan.fieldChanges.getValue(BatchMetadataField.GENRE).intent
                as BatchEditIntent.Set).value.let { it as BatchMetadataValue.MultiValue }.values
            attempt++
            if (attempt == 1) {
                result(retryPlan,
                    targetResult(retryPlan, "one", BatchTargetStatus.WRITE_FAILED, songs))
            } else {
                result(retryPlan,
                    targetResult(retryPlan, "one", BatchTargetStatus.IDENTITY_MISMATCH, songs))
            }
        })
        val songs = listOf(song("one"))

        controller.begin(reviewed, songs, null, false)
        controller.awaitState<BatchMetadataOperationState.Complete>()
        mutableGenre[0] = "Changed UI value"
        controller.retryFailed(songs)
        controller.awaitState<BatchMetadataOperationState.Complete>()

        assertEquals(listOf(listOf("Rock"), listOf("Rock")), capturedValues)
        val complete = controller.state.value as BatchMetadataOperationState.Complete
        assertEquals(BatchTargetStatus.IDENTITY_MISMATCH,
            complete.result.targetResults.single().status)
    }

    @Test
    fun `unprocessed targets require an explicit continue action`() {
        val attempts = mutableListOf<List<String>>()
        val controller = controller(execute = { attemptPlan, songs, _, _, _ ->
            attempts += attemptPlan.selectedTargets.map(BatchMetadataTargetId::displayName)
            if (attempts.size == 1) {
                BatchMetadataExecutionResult(
                    attemptPlan,
                    listOf(
                        targetResult(attemptPlan, "one", BatchTargetStatus.SUCCESS, songs),
                        targetResult(attemptPlan, "two", BatchTargetStatus.NOT_PROCESSED, songs)
                    ),
                    wasCancelled = true
                )
            } else {
                result(attemptPlan,
                    targetResult(attemptPlan, "two", BatchTargetStatus.SUCCESS, songs))
            }
        })
        val songs = listOf(song("one"), song("two"))

        controller.begin(plan("one", "two"), songs, null, false)
        controller.awaitState<BatchMetadataOperationState.Complete>()
        controller.retryFailed(songs)
        assertEquals(1, attempts.size)
        controller.continueUnprocessed(songs)
        controller.awaitState<BatchMetadataOperationState.Complete>()

        assertEquals(listOf(listOf("one", "two"), listOf("two")), attempts)
    }

    @Test
    fun `scan warning preserves verified writes and refresh retry performs no writes`() {
        var writes = 0
        var scans = 0
        var refreshes = 0
        val controller = controller(
            execute = { attemptPlan, songs, _, _, _ ->
                writes++
                result(attemptPlan,
                    targetResult(attemptPlan, "one", BatchTargetStatus.SUCCESS, songs))
            },
            scan = {
                scans++
                if (scans == 1) BatchPostWriteStageResult(
                    BatchPostWriteStageStatus.TIMED_OUT,
                    "timeout"
                ) else BatchPostWriteStageResult.Success
            },
            refresh = { refreshes++; BatchPostWriteStageResult.Success }
        )

        controller.begin(plan("one"), listOf(song("one")), null, false)
        var complete = controller.awaitState<BatchMetadataOperationState.Complete>()
        assertEquals(BatchTerminalOutcome.REFRESH_WARNING, complete.terminalOutcome)
        assertEquals(1, complete.result.successCount)

        controller.retryPostWrite()
        complete = controller.awaitState()
        assertEquals(BatchTerminalOutcome.SUCCESS, complete.terminalOutcome)
        assertEquals(1, writes)
        assertEquals(2, scans)
        assertEquals(2, refreshes)
    }

    @Test
    fun `cancel during scan records refresh warning without changing write results`() {
        var pendingScan: Continuation<BatchPostWriteStageResult>? = null
        val controller = controller(
            execute = { attemptPlan, songs, _, _, _ ->
                result(attemptPlan,
                    targetResult(attemptPlan, "one", BatchTargetStatus.SUCCESS, songs))
            },
            scan = { suspendCancellableCoroutine { pendingScan = it } }
        )

        controller.begin(plan("one"), listOf(song("one")), null, false)
        controller.awaitState<BatchMetadataOperationState.PostProcessing>()
        controller.cancel()

        val complete = controller.state.value as BatchMetadataOperationState.Complete
        assertEquals(1, complete.result.successCount)
        assertEquals(BatchTerminalOutcome.REFRESH_WARNING, complete.terminalOutcome)
        pendingScan?.resume(BatchPostWriteStageResult.Success)
        assertEquals(complete, controller.state.value)
    }

    @Test
    fun `busy controller ignores a duplicate begin from recreated UI`() {
        var pendingScan: Continuation<BatchPostWriteStageResult>? = null
        var writes = 0
        val controller = controller(
            execute = { attemptPlan, songs, _, _, _ ->
                writes++
                result(attemptPlan,
                    targetResult(attemptPlan, "one", BatchTargetStatus.SUCCESS, songs))
            },
            scan = { suspendCancellableCoroutine { pendingScan = it } }
        )

        controller.begin(plan("one"), listOf(song("one")), null, false)
        controller.awaitState<BatchMetadataOperationState.PostProcessing>()
        controller.begin(plan("one"), listOf(song("one")), null, false)

        assertEquals(1, writes)
        pendingScan?.resume(BatchPostWriteStageResult.Success)
        controller.awaitState<BatchMetadataOperationState.Complete>()
    }

    @Test
    fun `cancel during preparation writes nothing and stale preparation cannot restart it`() {
        var pendingArtwork: Continuation<PreparedBatchArtwork?>? = null
        var writes = 0
        val controller = controller(
            prepare = { suspendCancellableCoroutine { pendingArtwork = it } },
            execute = { attemptPlan, _, _, _, _ -> writes++; result(attemptPlan) }
        )
        val pickerUri = mock(Uri::class.java)

        controller.begin(plan("one"), listOf(song("one")), pickerUri, false)
        controller.awaitState<BatchMetadataOperationState.Preparing>()
        controller.cancel()

        val complete = controller.awaitState<BatchMetadataOperationState.Complete>()
        assertEquals(BatchTerminalOutcome.CANCELLED, complete.terminalOutcome)
        assertEquals(0, writes)
        pendingArtwork?.resume(
            PreparedBatchArtwork(byteArrayOf(1), "image/jpeg", 1, 1, "hash")
        )
        assertEquals(complete, controller.state.value)
        assertEquals(0, writes)
    }

    @Test
    fun `permission denial is distinct retryable and large requests are grouped before writes`() {
        var writes = 0
        val controller = controller(
            permissionBatchSize = 2,
            execute = { attemptPlan, _, _, _, _ ->
                writes++
                result(attemptPlan)
            }
        )
        val songs = (1..5).map { song("song-$it") }

        controller.begin(plan(*songs.map(Song::displayName).toTypedArray()), songs, null, true)
        val awaiting = controller.state.value as BatchMetadataOperationState.AwaitingPermission
        assertEquals(listOf(2, 2, 1), awaiting.permissionBatches.map(List<Uri>::size))
        assertNotNull(controller.consumePermissionRequest(awaiting.operationId, 0))
        controller.onPermissionResult(awaiting.operationId, 0, false)

        val complete = controller.state.value as BatchMetadataOperationState.Complete
        assertTrue(complete.result.targetResults.all {
            it.status == BatchTargetStatus.PERMISSION_DENIED && it.isRetryableFailure()
        })
        assertEquals(0, writes)
    }

    @Test
    fun `permission denial on retry preserves earlier successful targets`() {
        var writes = 0
        val controller = controller(execute = { attemptPlan, songs, _, _, _ ->
            writes++
            result(
                attemptPlan,
                targetResult(attemptPlan, "one", BatchTargetStatus.SUCCESS, songs),
                targetResult(attemptPlan, "two", BatchTargetStatus.WRITE_FAILED, songs)
            )
        })
        val songs = listOf(song("one"), song("two"))

        controller.begin(plan("one", "two"), songs, null, true)
        var awaiting = controller.awaitState<BatchMetadataOperationState.AwaitingPermission>()
        controller.consumePermissionRequest(awaiting.operationId, awaiting.batchIndex)
        controller.onPermissionResult(awaiting.operationId, awaiting.batchIndex, true)
        controller.awaitState<BatchMetadataOperationState.Complete>()

        controller.retryFailed(songs)
        awaiting = controller.awaitState()
        controller.consumePermissionRequest(awaiting.operationId, awaiting.batchIndex)
        controller.onPermissionResult(awaiting.operationId, awaiting.batchIndex, false)

        val complete = controller.awaitState<BatchMetadataOperationState.Complete>()
        assertEquals(1, writes)
        assertEquals(BatchTargetStatus.SUCCESS,
            complete.result.targetResults.single { it.target.displayName == "one" }.status)
        assertEquals(BatchTargetStatus.PERMISSION_DENIED,
            complete.result.targetResults.single { it.target.displayName == "two" }.status)
        assertEquals(BatchTerminalOutcome.PARTIAL_SUCCESS, complete.terminalOutcome)
    }

    @Test
    fun `process interruption marker restores warning and never resumes writes`() {
        val store = FakeInterruptionStore("old-operation")
        var writes = 0
        val controller = controller(
            store = store,
            execute = { attemptPlan, _, _, _, _ -> writes++; result(attemptPlan) }
        )

        assertEquals(
            BatchMetadataOperationState.Interrupted("old-operation"),
            controller.state.value
        )
        assertEquals(0, writes)
        controller.dismiss()
        assertNull(controller.state.value)
        assertNull(store.activeId)
    }

    @Test
    fun `artwork retry reuses prepared snapshot without reopening picker uri`() {
        val pickerUri = mock(Uri::class.java)
        val prepared = PreparedBatchArtwork(byteArrayOf(1, 2, 3), "image/jpeg", 3, 3, "hash")
        var preparations = 0
        val received = mutableListOf<PreparedBatchArtwork?>()
        var attempt = 0
        val controller = controller(
            prepare = {
                preparations++
                prepared
            },
            execute = { attemptPlan, songs, artwork, _, _ ->
                received += artwork
                attempt++
                result(
                    attemptPlan,
                    targetResult(
                        attemptPlan,
                        "one",
                        if (attempt == 1) BatchTargetStatus.WRITE_FAILED
                        else BatchTargetStatus.SUCCESS,
                        songs
                    )
                )
            }
        )
        val songs = listOf(song("one"))
        val artworkPlan = plan("one").copy(
            artworkChange = BatchArtworkChange(
                BatchInitialValue.Common(BatchArtworkValue.None),
                BatchEditIntent.Set(
                    BatchArtworkValue.Present(
                        BatchArtworkReference("picker", "content://expired-after-preparation")
                    )
                )
            )
        )

        controller.begin(artworkPlan, songs, pickerUri, false)
        controller.awaitState<BatchMetadataOperationState.Complete>()
        controller.retryFailed(songs)
        controller.awaitState<BatchMetadataOperationState.Complete>()

        assertEquals(1, preparations)
        assertEquals(listOf("hash", "hash"), received.map { it?.hash })
    }

    private fun controller(
        execute: suspend (
            BatchMetadataPlan,
            List<Song>,
            PreparedBatchArtwork?,
            BatchCancellationSignal,
            (BatchMetadataProgress) -> Unit
        ) -> BatchMetadataExecutionResult,
        scan: suspend (List<Song>) -> BatchPostWriteStageResult = {
            BatchPostWriteStageResult.Success
        },
        refresh: suspend (List<Song>) -> BatchPostWriteStageResult = {
            BatchPostWriteStageResult.Success
        },
        prepare: suspend (Uri) -> PreparedBatchArtwork? = { null },
        store: FakeInterruptionStore = FakeInterruptionStore(),
        permissionBatchSize: Int = 500
    ) = BatchMetadataOperationController(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        artworkPreparer = BatchArtworkPreparer(prepare),
        executor = BatchPlanExecutor(execute),
        scanner = BatchSuccessfulTargetScanner(scan),
        refresher = BatchSuccessfulTargetRefresher(refresh),
        interruptionStore = store,
        permissionBatchSize = permissionBatchSize,
        idFactory = object {
            var value = 0
            fun next() = "operation-${++value}"
        }::next
    )

    private inline fun <reified T : BatchMetadataOperationState>
        BatchMetadataOperationController.awaitState(): T {
        repeat(200) {
            (state.value as? T)?.let { return it }
            Thread.sleep(10)
        }
        throw AssertionError("Expected ${T::class.simpleName}, was ${state.value}")
    }

    private class FakeInterruptionStore(initial: String? = null) : BatchInterruptionStore {
        var activeId: String? = initial
        override fun interruptedOperationId(): String? = activeId
        override fun markActive(operationId: String) { activeId = operationId }
        override fun clear(operationId: String) {
            if (activeId == operationId) activeId = null
        }
    }

    private fun plan(
        vararg keys: String,
        values: MutableList<String> = mutableListOf("Rock")
    ) = BatchMetadataPlan(
        selectedTargets = keys.map(::target),
        fieldChanges = mapOf(
            BatchMetadataField.GENRE to BatchMetadataFieldChange(
                BatchInitialValue.Mixed,
                BatchEditIntent.Set(BatchMetadataValue.MultiValue(values))
            )
        ),
        artworkChange = null
    )

    private fun result(
        plan: BatchMetadataPlan,
        vararg results: BatchTargetResult
    ) = BatchMetadataExecutionResult(plan, results.toList(), wasCancelled = false)

    private fun targetResult(
        plan: BatchMetadataPlan,
        key: String,
        status: BatchTargetStatus,
        songs: List<Song>
    ): BatchTargetResult {
        val target = plan.selectedTargets.single { it.displayName == key }
        return BatchTargetResult(
            target,
            status,
            resolvedSong = songs.firstOrNull { it.displayName == key }
        )
    }

    private fun target(key: String): BatchMetadataTargetId {
        val song = song(key)
        return BatchMetadataTargetId(
            referenceKey = song.membershipKey(),
            mediaStoreId = song.id,
            filePath = song.filePath,
            volumeName = song.volumeName,
            displayName = key,
            title = key,
            artist = song.artist,
            contentUri = song.uri.toString(),
            relativePath = song.relativePath,
            durationMs = song.duration
        )
    }

    private fun song(key: String): Song {
        val uri = mock(Uri::class.java)
        `when`(uri.toString()).thenReturn("content://media/external/audio/$key")
        return Song(
            id = key.hashCode().toLong(),
            title = key,
            artist = "Artist",
            album = "Album",
            trackNumber = 1,
            duration = 1_000,
            uri = uri,
            filePath = "/music/$key.flac",
            folderPath = "/music",
            albumArtUri = null,
            volumeName = "external",
            displayName = key,
            relativePath = "Music/"
        )
    }
}
