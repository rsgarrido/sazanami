package io.github.rsgarrido.sazanami.data

import android.content.Context
import android.media.MediaScannerConnection
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

class PreferencesBatchInterruptionStore(context: Context) : BatchInterruptionStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    override fun interruptedOperationId(): String? =
        preferences.getString(ACTIVE_OPERATION_KEY, null)

    override fun markActive(operationId: String) {
        // Commit before any write can begin so process death cannot hide an interrupted batch.
        preferences.edit().putString(ACTIVE_OPERATION_KEY, operationId).commit()
    }

    override fun clear(operationId: String) {
        if (interruptedOperationId() == operationId) {
            preferences.edit().remove(ACTIVE_OPERATION_KEY).apply()
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "batch_metadata_recovery"
        const val ACTIVE_OPERATION_KEY = "active_operation_id"
    }
}

suspend fun scanBatchMetadataFiles(
    context: Context,
    songs: List<Song>,
    timeoutMs: Long = DEFAULT_BATCH_SCAN_TIMEOUT_MS
): BatchPostWriteStageResult {
    if (songs.isEmpty()) return BatchPostWriteStageResult.NotRequired
    val completed = withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine { continuation ->
            val remaining = AtomicInteger(songs.size)
            MediaScannerConnection.scanFile(
                context.applicationContext,
                songs.map(Song::filePath).toTypedArray(),
                null
            ) { _, _ ->
                if (remaining.decrementAndGet() == 0 && continuation.isActive) {
                    continuation.resume(Unit)
                }
            }
        }
    }
    return if (completed != null) {
        BatchPostWriteStageResult.Success
    } else {
        BatchPostWriteStageResult(
            BatchPostWriteStageStatus.TIMED_OUT,
            "MediaStore did not confirm every scan within ${timeoutMs / 1_000} seconds. " +
                "The metadata writes remain verified."
        )
    }
}

internal const val DEFAULT_BATCH_SCAN_TIMEOUT_MS = 15_000L
internal const val DEFAULT_BATCH_REFRESH_TIMEOUT_MS = 30_000L
