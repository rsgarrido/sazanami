package io.github.rsgarrido.sazanami.player.bitperfect.flac

import android.content.ContentResolver
import android.net.Uri
import io.github.rsgarrido.sazanami.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class FlacVerificationUiState(val running: Boolean = false, val result: FlacVerificationResult? = null)
/** Screen-owned structured execution. This controller never owns a USB device or player. */
internal class FlacVerificationController(private val resolver: ContentResolver, private val scope: CoroutineScope) {
    private val mutableState = MutableStateFlow(FlacVerificationUiState())
    val state = mutableState.asStateFlow()
    private var generation = 0L
    private var closed = false
    private var job: Job? = null
    private var cancellation: FlacVerificationCancellation? = null
    fun verify(uri: Uri) {
        if (!BuildConfig.DEBUG || closed || mutableState.value.running) return
        val request = ++generation
        val flag = FlacVerificationCancellation().also { cancellation = it }
        mutableState.value = FlacVerificationUiState(running = true)
        job = scope.launch {
            val parent = currentCoroutineContext()
            val result = withContext(NonCancellable) {
                coroutineScope {
                    val worker = async(Dispatchers.IO) { verifyReadOnly(uri, flag) }
                    try {
                        while (!worker.isCompleted) {
                            if (!parent.isActive) flag.cancelled = true
                            delay(25)
                        }
                        worker.await()
                    } finally {
                        if (!worker.isCompleted) flag.cancelled = true
                        // The owning job cannot finish while JNI still holds its duplicate/decoder.
                        worker.await()
                    }
                }
            }
            if (!closed && generation == request && parent.isActive && !flag.cancelled)
                mutableState.value = FlacVerificationUiState(result = result)
        }
    }
    private fun verifyReadOnly(uri: Uri, flag: FlacVerificationCancellation): FlacVerificationResult {
        var result = FlacVerificationResult(FlacVerificationStatus.INPUT_OPEN_FAILED)
        val descriptor = try { resolver.openFileDescriptor(uri, "r") }
        catch (error: Exception) { return result.copy(exceptionType = error.javaClass.simpleName) }
            ?: return result
        try {
            result = if (flag.cancelled) FlacVerificationResult(FlacVerificationStatus.CANCELLED)
                else FlacNativeVerifier.verifySelectedDocument(descriptor.fd, flag)
        } finally {
            val closed = runCatching { descriptor.close() }.isSuccess
            result = result.copy(originalFdClosed = closed)
        }
        return result
    }
    fun close() {
        closed = true; ++generation; cancellation?.cancelled = true; job?.cancel()
    }
}
