package io.github.rsgarrido.sazanami.player

import io.github.rsgarrido.sazanami.player.usb.*

internal enum class IntegratedDirectPhase { IDLE, PREPARING, PLAYING, STOPPING, STOPPED, ENDED, ERROR }
internal data class IntegratedDirectPlaybackState(
    val enabled: Boolean=false, val phase: IntegratedDirectPhase=IntegratedDirectPhase.IDLE,
    val generation: Long=0, val libraryId: Long?=null, val completedFrames: Long=0, val totalFrames: Long=0,
    val metadataDurationMs: Long=0, val error: String?=null, val notice: String?=null,
    val normalReleased: Boolean=false, val cleanupSafe: Boolean=true,
    val engine: UsbSilentTransportExperimentResult?=null
) {
    val positionMs get()=completedFramePositionMs(completedFrames).coerceAtMost(durationMs)
    val durationMs get()=if (totalFrames>0) completedFramePositionMs(totalFrames) else metadataDurationMs.coerceAtLeast(0)
    val busy get()=phase in setOf(IntegratedDirectPhase.PREPARING,IntegratedDirectPhase.PLAYING,IntegratedDirectPhase.STOPPING)
}
internal fun completedFramePositionMs(frames: Long): Long {
    require(frames>=0)
    val seconds=frames/44100L
    return if (seconds>Long.MAX_VALUE/1000) Long.MAX_VALUE else seconds*1000+(frames%44100)*1000/44100
}
internal fun integratedDirectFailure(result: UsbSilentTransportExperimentResult): String = when {
    result.status==UsbSilentStatus.CANCELLED -> "DIRECT_STOPPED"
    result.flacSource?.reasons?.any { it in setOf("UNSUPPORTED_FLAC_FORMAT","NOT_NATIVE_FLAC") }==true -> "UNSUPPORTED_TRACK"
    result.configuration?.accessFailure==UsbClockQueryStatus.DEVICE_DISCONNECTED -> "DIRECT_USB_DEVICE_UNAVAILABLE"
    result.configuration?.accessFailure==UsbClockQueryStatus.PERMISSION_LOST -> "DIRECT_USB_PERMISSION_FAILED"
    result.status==UsbSilentStatus.USB_CONFIGURATION_FAILED || result.status==UsbSilentStatus.BLOCKED -> "DIRECT_USB_CONFIGURATION_FAILED"
    result.status in setOf(UsbSilentStatus.FLAC_SOURCE_FAILED,UsbSilentStatus.FLAC_INPUT_OPEN_FAILED,UsbSilentStatus.QUEUE_PRIME_FAILED,
        UsbSilentStatus.FLAC_MD5_MISMATCH,UsbSilentStatus.FLAC_MD5_UNAVAILABLE,UsbSilentStatus.STREAMINFO_TOTAL_MISMATCH) -> "DIRECT_SOURCE_FAILED"
    !result.integratedCleanupSafe() -> "DIRECT_RESOURCE_CLEANUP_FAILED"
    else -> "DIRECT_TRANSPORT_FAILED"
}

internal fun integratedTerminalState(previous: IntegratedDirectPlaybackState,result: UsbSilentTransportExperimentResult): IntegratedDirectPlaybackState {
    val safe=result.integratedCleanupSafe()
    val total=(result.flacSource?.count("totalSamples") ?: previous.totalFrames).coerceAtLeast(0)
    val completed=(result.transport?.count("framesCompleted") ?: previous.completedFrames).coerceIn(0,total)
    val success=result.status==UsbSilentStatus.FULL_FLAC_USB_TRANSPORT_VERIFIED && result.transport?.verified==true && safe && total>0 && completed==total
    val stopped=result.status==UsbSilentStatus.CANCELLED && safe
    return previous.copy(engine=result,totalFrames=total,completedFrames=completed,cleanupSafe=safe,
        phase=if(success) IntegratedDirectPhase.ENDED else if(stopped) IntegratedDirectPhase.STOPPED else IntegratedDirectPhase.ERROR,
        error=if(success || stopped) null else integratedDirectFailure(result))
}

internal val integratedDirectCommands=intArrayOf(
    androidx.media3.common.Player.COMMAND_GET_CURRENT_MEDIA_ITEM,androidx.media3.common.Player.COMMAND_GET_TIMELINE,
    androidx.media3.common.Player.COMMAND_GET_METADATA,androidx.media3.common.Player.COMMAND_SET_MEDIA_ITEM,
    androidx.media3.common.Player.COMMAND_PREPARE,androidx.media3.common.Player.COMMAND_STOP)
