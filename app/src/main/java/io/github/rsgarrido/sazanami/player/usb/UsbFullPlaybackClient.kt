package io.github.rsgarrido.sazanami.player.usb

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
internal class UsbFullPlaybackClient(val progress: (UsbFullProgress)->Unit,val control: UsbFullPlaybackControl) {
    var result: UsbSilentTransportExperimentResult?=null
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
