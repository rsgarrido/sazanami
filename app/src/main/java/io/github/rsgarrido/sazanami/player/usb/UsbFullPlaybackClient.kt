package io.github.rsgarrido.sazanami.player.usb

/** Immutable low-frequency view of completed PCM, never decoded-ahead or elapsed-clock position. */
internal data class UsbFullProgress(val completedFrames: Long, val totalFrames: Long) {
    init { require(completedFrames>=0 && totalFrames>=0 && completedFrames<=totalFrames) }
}
internal interface FullUsbPlaybackEngineClient {
    suspend fun runIntegratedFull(uri: android.net.Uri,progress: (UsbFullProgress)->Unit): UsbSilentTransportExperimentResult
    fun stopIntegratedFull()
}
internal class UsbIntegratedStartException(val reason: String) : IllegalStateException(reason)
internal class UsbFullPlaybackClient(val progress: (UsbFullProgress)->Unit) {
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
