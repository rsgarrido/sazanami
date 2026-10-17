package io.github.rsgarrido.sazanami.player.usb

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Immutable low-frequency view of completed PCM, never decoded-ahead or elapsed-clock position. */
internal enum class UsbFullTransportPhase { STARTING, PLAYING, PAUSING, PAUSED, RESUMING, STOPPING, STOPPED, ENDED, FAILED, SEEK_DRAINING, SOURCE_REPOSITIONING, SEEK_PRIMING }
internal data class UsbFullProgress(val completedFrames: Long, val totalFrames: Long, val sampleRateHz: Long,
    val transportPhase: UsbFullTransportPhase=UsbFullTransportPhase.PLAYING,val controlCounters: Map<String,Long> = emptyMap()) {
    val sourcePositionFrame get()=controlCounters["sourcePositionFrame"] ?: completedFrames
    val sourceEpoch get()=controlCounters["seekSourceEpoch"] ?: 0L
    val seekable get()=controlCounters["sourceSeekCapable"]==1L
    init { require(UsbDirectPcmFormat.supportsRate(sampleRateHz)); require(completedFrames>=0 && totalFrames>=0 &&
        (completedFrames<=totalFrames || (controlCounters["seekConfirmed"] ?: 0L)>0L) && sourcePositionFrame in 0L..totalFrames) }
}
/** Floor milliseconds to original sample frame. Targets at/beyond duration clamp to the final real frame. */
internal fun directSeekTargetFrame(positionMs: Long,rate: Long,total: Long): Long {
    require(positionMs>=0 && UsbDirectPcmFormat.supportsRate(rate) && total>0)
    val seconds=positionMs/1000;val fraction=(positionMs%1000)*rate/1000
    if(seconds>(total-1)/rate) return total-1
    val base=seconds*rate
    return if(fraction>total-1-base) total-1 else base+fraction
}
internal interface FullUsbPlaybackEngineClient {
    suspend fun runIntegratedFull(uri: android.net.Uri,control: UsbFullPlaybackControl=UsbFullPlaybackControl(),progress: (UsbFullProgress)->Unit): UsbSilentTransportExperimentResult
    fun stopIntegratedFull()
}
internal class UsbIntegratedStartException(val reason: String) : IllegalStateException(reason)
internal class UsbFullPlaybackClient(val progress: (UsbFullProgress)->Unit,val control: UsbFullPlaybackControl,
    val owner: UsbActivationOwner, val attachment: UsbAudioInspectionToken) {
    @Volatile var detached = false; private set
    var job: Job? = null; private set
    var result: UsbSilentTransportExperimentResult?=null; private set
    fun complete(value: UsbSilentTransportExperimentResult) { check(result==null);result=value }
    fun detached(token: UsbAudioInspectionToken) { if (token == attachment) detached = true }
    fun launchOwned(scope: CoroutineScope, operation: suspend () -> Unit): Job {
        check(job == null)
        return scope.launch(start=CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) { operation() }
        }.also { job=it }
    }
    /** The controller's operation owns cleanup. Cancelling its waiter requests stop, never cancels that owner. */
    suspend fun awaitResult(stop: () -> Unit): UsbSilentTransportExperimentResult {
        val worker = job
        try { worker?.join() }
        catch (_: CancellationException) {
            runCatching(stop).exceptionOrNull()?.let(control::recordFailure)
            withContext(NonCancellable) { worker?.join() }
        }
        return result ?: throw UsbIntegratedStartException("DIRECT_RESOURCE_CLEANUP_UNVERIFIED")
    }
}

/** Disposal permission, not playback success or proof that the absent DAC restored its configuration. */
internal class UsbDetachedResourceRelease private constructor(
    private val owner: UsbActivationOwner, val attachment: UsbAudioInspectionToken, val stream: Long,
    private val evidence: UsbSilentTransportExperimentResult
) {
    fun matches(attempt: UsbActivationOwner, result: UsbSilentTransportExperimentResult) =
        owner === attempt && attachment.generation == result.generation && stream == result.streamGeneration &&
            result.copy(detachedResourceRelease=null) == evidence

    companion object {
        internal fun verified(owner: UsbActivationOwner, attachment: UsbAudioInspectionToken,
            result: UsbSilentTransportExperimentResult, detached: Boolean, sourceAbsent: Boolean,
            inputAbsent: Boolean, connectionAbsent: Boolean, nativeRegistriesIdle: Boolean): UsbDetachedResourceRelease? {
            if (result.configuration?.generation != result.generation) return null
            if (!detached || result.generation != attachment.generation || !nativeRegistriesIdle ||
                !result.nativeExecutionJoined || !result.nativeMemoryReleased) return null
            if (connectionAbsent && (result.transport != null || result.connectionCloseRequired)) return null
            if (!connectionAbsent && result.configuration?.closeStatus != UsbAudioCleanupStatus.SUCCEEDED) return null
            if (!inputAbsent && result.originalFlacFdClosed != true) return null
            if (!sourceAbsent && result.flacSource?.let { source ->
                source.counters["generation"] == result.generation && source.counters["streamGeneration"] == result.streamGeneration &&
                    listOf("producerStopped", "producerJoined", "decoderReleased", "nativeFlacFdClosed", "queueReleased")
                        .all { source.counters[it] == 1L }
            } != true) return null
            val native = result.transport
            if (native != null && (native.generation != result.generation || native.streamGeneration != result.streamGeneration ||
                    result.outstandingAfterConnectionClose != 0L)) return null
            return UsbDetachedResourceRelease(owner, attachment, result.streamGeneration, result.copy(detachedResourceRelease=null))
        }
    }
}
internal fun UsbSilentTransportExperimentResult.integratedCleanupSafe(): Boolean {
    val c=configuration ?: return false
    if (!nativeMemoryReleased || connectionCloseRequired) return false
    if (originalFlacFdClosed==false && !(status==UsbSilentStatus.FLAC_INPUT_OPEN_FAILED && flacSource==null && !c.acClaim.attempted && !c.asClaim.attempted)) return false
    if (!c.acClaim.attempted && !c.asClaim.attempted) return c.accessFailure==null &&
        (c.targetStatus==UsbTargetConfigurationStatus.BLOCKED || c.closeStatus==UsbAudioCleanupStatus.SUCCEEDED) &&
        transport==null && flacSource?.count("producerJoined")!=0L
    return transport?.shutdownAccounted!=false && c.restorationStatus==UsbConfigurationRestorationStatus.RESTORED &&
        (!c.asClaim.attempted || c.asRelease.returned==true) && c.acRelease.returned==true && c.closeStatus==UsbAudioCleanupStatus.SUCCEEDED
}
