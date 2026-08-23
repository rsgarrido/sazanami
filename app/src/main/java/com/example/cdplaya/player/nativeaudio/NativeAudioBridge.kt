package com.example.cdplaya.player.nativeaudio

import android.os.ParcelFileDescriptor
import android.os.Process
import androidx.annotation.Keep
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Thin JNI boundary for CDPlaya's native offline audio decoder.
 *
 * The native side owns extraction, MediaCodec decode, PCM sampling, RMS bucketing,
 * and waveform normalization. Kotlin receives only the final bar array.
 */
@Keep
internal object NativeAudioBridge {
    private val libraryLoaded: Boolean = runCatching {
        System.loadLibrary(LIBRARY_NAME)
        nativeApiVersion() == NATIVE_API_VERSION
    }.getOrDefault(false)

    val isAvailable: Boolean
        get() = libraryLoaded

    /**
     * Takes ownership of [descriptor]. The descriptor remains open until the native worker
     * has actually stopped, even if the calling coroutine is cancelled meanwhile.
     */
    suspend fun decodeWaveform(
        descriptor: ParcelFileDescriptor,
        offset: Long,
        length: Long,
        fallbackDurationUs: Long,
        barCount: Int
    ): FloatArray? {
        if (!libraryLoaded || descriptor.fd < 0 || offset < 0L || length <= 0L || barCount <= 0) {
            closeQuietly(descriptor)
            return null
        }

        val sessionHandle = runCatching { nativeCreateSession() }.getOrNull() ?: 0L
        if (sessionHandle == 0L) {
            closeQuietly(descriptor)
            return null
        }

        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation {
                runCatching { nativeCancelSession(sessionHandle) }
            }

            val worker = Thread(
                {
                    runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND) }
                    val result = runCatching {
                        nativeDecodeWaveform(
                            handle = sessionHandle,
                            fd = descriptor.fd,
                            offset = offset,
                            length = length,
                            fallbackDurationUs = fallbackDurationUs,
                            barCount = barCount
                        )
                    }.getOrNull()

                    // CancellableContinuation is thread-safe: cancellation and resume may race,
                    // and only one will win. Use the stable public resume API rather than the
                    // internal tryResume/completeResume token API.
                    continuation.resume(result)

                    runCatching { nativeDestroySession(sessionHandle) }
                    closeQuietly(descriptor)
                },
                "cdplaya-native-waveform"
            ).apply {
                isDaemon = true
            }

            runCatching { worker.start() }.onFailure {
                runCatching { nativeDestroySession(sessionHandle) }
                closeQuietly(descriptor)
                continuation.resume(null)
            }
        }
    }

    private fun closeQuietly(descriptor: ParcelFileDescriptor) {
        runCatching { descriptor.close() }
    }

    private external fun nativeApiVersion(): Int

    private external fun nativeCreateSession(): Long

    private external fun nativeCancelSession(handle: Long)

    private external fun nativeDecodeWaveform(
        handle: Long,
        fd: Int,
        offset: Long,
        length: Long,
        fallbackDurationUs: Long,
        barCount: Int
    ): FloatArray?

    private external fun nativeDestroySession(handle: Long)

    private const val LIBRARY_NAME = "cdplaya_native"
    private const val NATIVE_API_VERSION = 1
}
