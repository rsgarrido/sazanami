package io.github.rsgarrido.sazanami.player.usb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** The IO coroutine is an isolated synchronous native execution context, not a microframe scheduler.
 * Its async child is ALWAYS awaited before restoration/close. Only the native worker submits/reaps.
 */
internal class UsbNativeSilentTransport(private val borrowedFd: Int, private val plan: UsbSilentTransportPlan,
    private val generation: Long, private val stream: Long, private val accessFailure: () -> UsbClockQueryStatus?,
    private val mode: UsbPcmDiagnosticMode = UsbPcmDiagnosticMode.ZERO,
    private val prefixSource: UsbFlacSourceLifecycle? = null) : UsbSilentTransportBackend {
    var progressListener: ((UsbFullProgress)->Unit)?=null
    var playbackControl: UsbFullPlaybackControl?=null
    @Volatile private var handle = 0L
    @Volatile private var stopRequested = 0
    override var usbCleanupSafe = true; private set
    override var memoryReleased = true; private set
    override var executionJoined = true; private set
    fun requestStop(authority: Boolean = true) {
        stopRequested = if (authority) 2 else 1
        prefixSource?.requestStop()
        val current = handle
        if (current != 0L) {
            val stopped=runCatching { UsbFsDiagnostics.stopSilentTransport(current, generation, stream, stopRequested) }
            // Integrated control errors remain visible in the final result; cleanup continues on the same handle.
            stopped.exceptionOrNull()?.let { playbackControl?.recordFailure(it) }
        }
    }
    override suspend fun run(): UsbIsoTransportResult {
        val parent = currentCoroutineContext()
        var authority: UsbClockQueryStatus? = null
        var result = UsbIsoTransportResult(generation, stream, exceptionType = "NativeUnavailable")
        withContext(NonCancellable) {
            try {
                authority = accessFailure()
                if (authority != null || !parent.isActive || stopRequested != 0) return@withContext
                val created = UsbFsDiagnostics.createSilentTransport(borrowedFd, plan, generation, stream, mode, prefixSource?.handle ?: 0L)
                if (created < 0) {
                    val errno = -created
                    authority = when (errno) { 19L -> UsbClockQueryStatus.DEVICE_DISCONNECTED; 1L, 13L -> UsbClockQueryStatus.PERMISSION_LOST
                        9L -> UsbClockQueryStatus.TRANSPORT_ERROR; else -> null }
                    result = UsbIsoTransportResult(generation, stream, counters = mapOf("createErrno" to errno), exceptionType = "NativeCreateIoctlFailure")
                    return@withContext
                }
                check(created > 0L)
                handle = created
                executionJoined = false
                usbCleanupSafe = false; memoryReleased = false
                if (stopRequested != 0) requestStop(stopRequested == 2)
                playbackControl?.bind({ intent -> UsbFsDiagnostics.setFullPlayWhenReady(handle,generation,stream,intent) },{ requestStop(authority=false) })
                coroutineScope {
                    val worker = async(Dispatchers.IO) { UsbFsDiagnostics.runSilentTransport(handle, generation, stream) }
                    var nextProgressAt=0L
                    try {
                        while (!worker.isCompleted) {
                            val failure = accessFailure()
                            if(playbackControl?.failure!=null) requestStop(authority=false)
                            if (failure != null) { authority = failure; requestStop() }
                            else if (!parent.isActive) requestStop(authority = false)
                            val listener=progressListener
                            val now=android.os.SystemClock.elapsedRealtime()
                            if (listener!=null && authority==null && mode==UsbPcmDiagnosticMode.FLAC_FULL && now>=nextProgressAt) {
                                nextProgressAt=now+250L
                                val progress=UsbFsDiagnostics.fullProgress(handle,generation,stream)
                                withContext(Dispatchers.Main.immediate) { listener(progress) }
                            }
                            delay(5) // Authority observation only. No packet scheduling on Kotlin.
                        }
                        result = worker.await()
                    } catch(error: Exception) {
                        playbackControl?.recordFailure(error);throw error
                    } catch(error: LinkageError) {
                        playbackControl?.recordFailure(error);throw error
                    } finally {
                        withContext(NonCancellable) {
                            if (!worker.isCompleted) requestStop(authority = false)
                            // A failed child can cancel coroutineScope even inside the outer shield.
                            // Preserve join evidence when monitoring or JNI snapshot decoding throws.
                            runCatching { worker.await() }.getOrNull()?.let { result = it }
                            worker.join()
                            executionJoined = true
                        }
                    }
                }
            } catch (error: Exception) { result = result.copy(exceptionType = error.javaClass.simpleName) }
            catch (error: LinkageError) { result = result.copy(exceptionType = error.javaClass.simpleName) }
            finally {
                playbackControl?.finish()
                if (handle != 0L) {
                    memoryReleased = runCatching { UsbFsDiagnostics.destroySilentTransport(handle, generation, stream, false) }.getOrDefault(false)
                    usbCleanupSafe = memoryReleased
                    if (memoryReleased) handle = 0
                }
                prefixSource?.finish()
            }
        }
        if ((result.count("integrityMask") and (1L shl 13)) != 0L || result.count("submitErrno") == 19L || result.count("reapErrno") == 19L)
            authority = UsbClockQueryStatus.DEVICE_DISCONNECTED
        else if (listOf(result.count("submitErrno"), result.count("reapErrno")).any { it == 13L || it == 1L }) authority = UsbClockQueryStatus.PERMISSION_LOST
        else if (listOf(result.count("submitErrno"), result.count("reapErrno")).any { it == 9L }) authority = UsbClockQueryStatus.TRANSPORT_ERROR
        return result.copy(authorityFailure = authority,exceptionType=if(playbackControl?.failure!=null) "DirectPlaybackControlFailure" else result.exceptionType)
    }
    override fun connectionClosed(succeeded: Boolean) {
        val current = handle
        if (current != 0L && succeeded) {
            memoryReleased = UsbFsDiagnostics.destroySilentTransport(current, generation, stream, true)
            if (memoryReleased) handle = 0
        }
    }
}
