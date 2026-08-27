package com.example.cdplaya.data

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

data class PreparedBatchArtwork(
    val bytes: ByteArray,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val hash: String
)

sealed interface BatchArtworkExecutionEdit {
    data object Untouched : BatchArtworkExecutionEdit
    data class Replace(val artwork: PreparedBatchArtwork) : BatchArtworkExecutionEdit
    data object Clear : BatchArtworkExecutionEdit
}

enum class BatchTargetStatus {
    SUCCESS,
    MISSING,
    IDENTITY_MISMATCH,
    UNSUPPORTED,
    WRITE_FAILED,
    VERIFICATION_FAILED,
    PERMISSION_DENIED,
    NOT_PROCESSED
}

data class BatchTargetResult(
    val target: BatchMetadataTargetId,
    val status: BatchTargetStatus,
    val reason: String? = null,
    val resolvedSong: Song? = null
)

data class BatchMetadataExecutionResult(
    val frozenPlan: BatchMetadataPlan,
    val targetResults: List<BatchTargetResult>,
    val wasCancelled: Boolean
) {
    val successCount: Int get() = targetResults.count { it.status == BatchTargetStatus.SUCCESS }
    val failureCount: Int get() = targetResults.count {
        it.status != BatchTargetStatus.SUCCESS && it.status != BatchTargetStatus.NOT_PROCESSED
    }
    val notProcessedCount: Int get() = targetResults.count {
        it.status == BatchTargetStatus.NOT_PROCESSED
    }
}

data class BatchMetadataProgress(
    val completedCount: Int,
    val totalCount: Int,
    val currentTarget: BatchMetadataTargetId?
)

/** Cooperative cancellation: checked only between targets so an active file write can verify. */
class BatchCancellationSignal {
    private val requested = AtomicBoolean(false)
    val isCancellationRequested: Boolean get() = requested.get()
    fun cancel() { requested.set(true) }
}

sealed interface BatchTargetResolution {
    data class Resolved(val song: Song) : BatchTargetResolution
    data class Missing(val reason: String) : BatchTargetResolution
    data class Mismatch(val reason: String) : BatchTargetResolution
}

fun interface BatchTargetResolver {
    fun resolve(target: BatchMetadataTargetId, currentSongs: List<Song>): BatchTargetResolution
}

fun interface BatchCapabilityReader {
    fun capabilities(song: Song): MetadataFormatCapabilities
}

enum class ExplicitPatchFailureKind { WRITE, VERIFICATION }

data class ExplicitMetadataPatchResult(
    val wasSuccessful: Boolean,
    val message: String,
    val failureKind: ExplicitPatchFailureKind? = null
)

internal fun interface BatchTargetPatchWriter {
    fun write(
        song: Song,
        edits: Map<org.jaudiotagger.tag.FieldKey, MetadataTextEdit>,
        artworkEdit: BatchArtworkExecutionEdit
    ): ExplicitMetadataPatchResult
}

/** Executes the frozen target order sequentially; per-target failures never imply rollback. */
internal class BatchMetadataExecutor(
    private val resolver: BatchTargetResolver,
    private val capabilityReader: BatchCapabilityReader,
    private val writer: BatchTargetPatchWriter
) {
    fun execute(
        plan: BatchMetadataPlan,
        currentSongs: List<Song>,
        preparedArtwork: PreparedBatchArtwork?,
        cancellationSignal: BatchCancellationSignal,
        onProgress: (BatchMetadataProgress) -> Unit = {}
    ): BatchMetadataExecutionResult {
        val frozenPlan = plan.frozenCopy()
        val frozenArtwork = preparedArtwork?.copy(bytes = preparedArtwork.bytes.copyOf())
        val artworkEdit = frozenPlan.toExecutionArtworkEdit(frozenArtwork)
        val results = mutableListOf<BatchTargetResult>()
        val total = frozenPlan.selectedTargets.size

        if (frozenPlan.changeCount == 0) {
            return BatchMetadataExecutionResult(frozenPlan, emptyList(), wasCancelled = false)
        }

        frozenPlan.selectedTargets.forEachIndexed { index, target ->
            if (cancellationSignal.isCancellationRequested) {
                frozenPlan.selectedTargets.drop(index).forEach { remaining ->
                    results += BatchTargetResult(
                        target = remaining,
                        status = BatchTargetStatus.NOT_PROCESSED,
                        reason = "Cancelled before this file started."
                    )
                }
                return BatchMetadataExecutionResult(frozenPlan, results, wasCancelled = true)
            }

            onProgress(BatchMetadataProgress(results.size, total, target))
            val resolution = try {
                resolver.resolve(target, currentSongs)
            } catch (exception: Exception) {
                results += BatchTargetResult(
                    target,
                    BatchTargetStatus.WRITE_FAILED,
                    exception.message ?: "The target could not be safely resolved."
                )
                onProgress(BatchMetadataProgress(results.size, total, null))
                return@forEachIndexed
            }
            val resolved = resolution as? BatchTargetResolution.Resolved
            if (resolved == null) {
                results += when (resolution) {
                    is BatchTargetResolution.Missing -> BatchTargetResult(
                        target, BatchTargetStatus.MISSING, resolution.reason
                    )
                    is BatchTargetResolution.Mismatch -> BatchTargetResult(
                        target, BatchTargetStatus.IDENTITY_MISMATCH, resolution.reason
                    )
                    is BatchTargetResolution.Resolved -> error("Handled above")
                }
                onProgress(BatchMetadataProgress(results.size, total, null))
                return@forEachIndexed
            }

            val required = frozenPlan.requiredCapabilities()
            val currentCapabilities = try {
                capabilityReader.capabilities(resolved.song)
            } catch (exception: Exception) {
                results += BatchTargetResult(
                    target,
                    BatchTargetStatus.WRITE_FAILED,
                    exception.message ?: "The file capabilities could not be rechecked.",
                    resolved.song
                )
                onProgress(BatchMetadataProgress(results.size, total, null))
                return@forEachIndexed
            }
            val unsupported = required.firstOrNull { !currentCapabilities.supports(it) }
            if (unsupported != null) {
                results += BatchTargetResult(
                    target,
                    BatchTargetStatus.UNSUPPORTED,
                    "The file no longer supports ${unsupported.name.lowercase().replace('_', ' ')}.",
                    resolved.song
                )
                onProgress(BatchMetadataProgress(results.size, total, null))
                return@forEachIndexed
            }

            val writeResult = try {
                writer.write(
                    resolved.song,
                    frozenPlan.toMetadataTextEdits(),
                    artworkEdit
                )
            } catch (exception: Exception) {
                ExplicitMetadataPatchResult(
                    wasSuccessful = false,
                    message = exception.message ?: "The metadata write failed.",
                    failureKind = ExplicitPatchFailureKind.WRITE
                )
            } catch (error: LinkageError) {
                ExplicitMetadataPatchResult(
                    wasSuccessful = false,
                    message = error.message ?: "The metadata writer is unavailable on this device.",
                    failureKind = ExplicitPatchFailureKind.WRITE
                )
            }
            val postAttemptTarget = target.withCurrentFileEvidence()
            results += if (writeResult.wasSuccessful) {
                BatchTargetResult(
                    postAttemptTarget,
                    BatchTargetStatus.SUCCESS,
                    resolvedSong = resolved.song
                )
            } else {
                BatchTargetResult(
                    target = postAttemptTarget,
                    status = if (writeResult.failureKind == ExplicitPatchFailureKind.VERIFICATION) {
                        BatchTargetStatus.VERIFICATION_FAILED
                    } else {
                        BatchTargetStatus.WRITE_FAILED
                    },
                    reason = writeResult.message,
                    resolvedSong = resolved.song
                )
            }
            onProgress(BatchMetadataProgress(results.size, total, null))
        }

        return BatchMetadataExecutionResult(frozenPlan, results, wasCancelled = false)
    }
}

class LibraryBatchTargetResolver : BatchTargetResolver {
    override fun resolve(
        target: BatchMetadataTargetId,
        currentSongs: List<Song>
    ): BatchTargetResolution {
        val song = currentSongs.firstOrNull { it.membershipKey() == target.referenceKey }
            ?: return BatchTargetResolution.Missing("The track is no longer in the current library.")
        if (song.id != target.mediaStoreId || song.filePath != target.filePath ||
            (target.volumeName.isNotBlank() && song.volumeName != target.volumeName) ||
            (target.displayName.isNotBlank() && song.displayName != target.displayName) ||
            (target.contentUri.isNotBlank() && song.uri.toString() != target.contentUri) ||
            (target.relativePath.isNotBlank() && song.relativePath != target.relativePath) ||
            (target.durationMs > 0L && song.duration != target.durationMs) ||
            (target.title.isNotBlank() && song.title != target.title) ||
            (target.artist.isNotBlank() && song.artist != target.artist)
        ) {
            return BatchTargetResolution.Mismatch("The track identity or path changed after review.")
        }
        val file = File(song.filePath)
        if (!file.isFile) return BatchTargetResolution.Missing("The audio file no longer exists.")
        if (target.fileSizeBytes > 0L && file.length() != target.fileSizeBytes) {
            return BatchTargetResolution.Mismatch("The audio file size changed after review.")
        }
        val modifiedSeconds = file.lastModified() / 1_000L
        if (target.dateModifiedEpochSeconds > 0L &&
            modifiedSeconds != target.dateModifiedEpochSeconds
        ) {
            return BatchTargetResolution.Mismatch("The audio file was replaced or modified after review.")
        }
        return BatchTargetResolution.Resolved(song)
    }
}

private fun BatchMetadataTargetId.withCurrentFileEvidence(): BatchMetadataTargetId {
    val file = File(filePath)
    return if (file.isFile) {
        copy(
            fileSizeBytes = file.length(),
            dateModifiedEpochSeconds = file.lastModified() / 1_000L
        )
    } else {
        this
    }
}

internal fun BatchMetadataPlan.frozenCopy(): BatchMetadataPlan = copy(
    selectedTargets = selectedTargets.map { it.copy() },
    fieldChanges = fieldChanges.mapValues { (_, change) ->
        change.copy(
            initial = change.initial.copyMetadataValue(),
            intent = change.intent.copyMetadataValue()
        )
    }.toMap(),
    artworkChange = artworkChange?.copy()
)

internal fun BatchMetadataPlan.toMetadataTextEdits(): Map<org.jaudiotagger.tag.FieldKey, MetadataTextEdit> =
    fieldChanges.map { (field, change) ->
        val edit = when (val intent = change.intent) {
            BatchEditIntent.Clear -> MetadataTextEdit(emptyList(), MetadataTextOperation.CLEAR)
            is BatchEditIntent.Set -> MetadataTextEdit(
                values = when (val value = intent.value) {
                    is BatchMetadataValue.Text -> listOf(value.value)
                    is BatchMetadataValue.MultiValue -> value.values.ifEmpty { listOf("") }
                },
                operation = MetadataTextOperation.SET
            )
            BatchEditIntent.Untouched -> error("Frozen plans contain only explicit changes")
        }
        field.fieldKey to edit
    }.toMap()

private fun BatchMetadataPlan.toExecutionArtworkEdit(
    preparedArtwork: PreparedBatchArtwork?
): BatchArtworkExecutionEdit = when (val intent = artworkChange?.intent) {
    null, BatchEditIntent.Untouched -> BatchArtworkExecutionEdit.Untouched
    BatchEditIntent.Clear -> BatchArtworkExecutionEdit.Clear
    is BatchEditIntent.Set -> BatchArtworkExecutionEdit.Replace(
        requireNotNull(preparedArtwork) { "Replacement artwork was not prepared before execution" }
    )
}

private fun BatchMetadataPlan.requiredCapabilities(): Set<EditableMetadataField> = buildSet {
    fieldChanges.keys.forEach { add(it.requiredCapability) }
    if (artworkChange != null) add(EditableMetadataField.ARTWORK)
}

private fun BatchInitialValue<BatchMetadataValue>.copyMetadataValue(): BatchInitialValue<BatchMetadataValue> =
    when (this) {
        is BatchInitialValue.Common -> BatchInitialValue.Common(value.copyValue())
        BatchInitialValue.Mixed -> BatchInitialValue.Mixed
    }

private fun BatchEditIntent<BatchMetadataValue>.copyMetadataValue(): BatchEditIntent<BatchMetadataValue> =
    when (this) {
        BatchEditIntent.Clear -> BatchEditIntent.Clear
        is BatchEditIntent.Set -> BatchEditIntent.Set(value.copyValue())
        BatchEditIntent.Untouched -> BatchEditIntent.Untouched
    }

private fun BatchMetadataValue.copyValue(): BatchMetadataValue = when (this) {
    is BatchMetadataValue.Text -> copy()
    is BatchMetadataValue.MultiValue -> copy(values = values.toList())
}
