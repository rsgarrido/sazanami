package io.github.rsgarrido.sazanami.player

import io.github.rsgarrido.sazanami.player.usb.*

internal enum class DirectOutputSwitch { TO_USB, TO_ANDROID }
internal enum class IntegratedDirectPhase { IDLE, PREPARING, PLAYING, PAUSING, PAUSED, RESUMING, STOPPING, STOPPED, ENDED, ERROR, SEEKING }
internal data class IntegratedDirectPlaybackState(
    val serviceAvailable: Boolean=false, val switching: DirectOutputSwitch?=null,
    val activation: UsbActivationEvidence?=null,
    val enabled: Boolean=false, val phase: IntegratedDirectPhase=IntegratedDirectPhase.IDLE,
    val generation: Long=0, val libraryId: Long?=null, val completedFrames: Long=0, val totalFrames: Long=0,
    val metadataDurationMs: Long=0, val error: String?=null, val notice: String?=null,
    val normalReleased: Boolean=false, val cleanupSafe: Boolean=true,
    val engine: UsbSilentTransportExperimentResult?=null,
    val sampleRateHz: Long=0, // Unknown until accepted STREAMINFO; never infer it from library duration.
    val playWhenReady: Boolean=true,
    val nativeTransportPhase: UsbFullTransportPhase=UsbFullTransportPhase.STARTING,
    val pauseRequests: Long=0,val resumeRequests: Long=0,val nativePauseCounters: Map<String,Long> = emptyMap(),
    val sourcePositionFrame: Long?=null,val sourceEpoch: Long=0,val seekCapable: Boolean=false,val pendingSeekSerial: Long=0
) {
    val confirmedDirect get()=enabled && normalReleased && switching != DirectOutputSwitch.TO_USB &&
        (activation == null || activation.reservation == UsbActivationReservation.PROTECTED)
    val positionMs get()=(if (UsbDirectPcmFormat.supportsRate(sampleRateHz)) completedFramePositionMs(sourcePositionFrame ?: completedFrames,sampleRateHz) else 0L).coerceAtMost(durationMs)
    val durationMs get()=if (totalFrames>0) {
        if (UsbDirectPcmFormat.supportsRate(sampleRateHz)) completedFramePositionMs(totalFrames,sampleRateHz) else 0L
    } else metadataDurationMs.coerceAtLeast(0)
    val busy get()=phase in setOf(IntegratedDirectPhase.PREPARING,IntegratedDirectPhase.PLAYING,IntegratedDirectPhase.PAUSING,
        IntegratedDirectPhase.PAUSED,IntegratedDirectPhase.RESUMING,IntegratedDirectPhase.STOPPING,IntegratedDirectPhase.SEEKING)
}
internal fun integratedProgressState(previous: IntegratedDirectPlaybackState,progress: UsbFullProgress,play: Boolean): IntegratedDirectPlaybackState {
    if(!previous.busy || previous.phase==IntegratedDirectPhase.STOPPING) return previous
    require(progress.completedFrames>=previous.completedFrames && (previous.sampleRateHz==0L || previous.sampleRateHz==progress.sampleRateHz) &&
        (previous.totalFrames==0L || previous.totalFrames==progress.totalFrames))
    val confirmed=progress.controlCounters["confirmedSeekSerial"] ?: 0L
    val pending=previous.pendingSeekSerial>confirmed
    if(pending) {
        require(progress.sourceEpoch>=previous.sourceEpoch)
        val sameEpoch=progress.sourceEpoch==previous.sourceEpoch
        if(sameEpoch) require(progress.sourcePositionFrame >= (previous.sourcePositionFrame ?: previous.completedFrames))
        // Old-epoch completions may advance during drain; a superseded target may not jump the playhead.
        return previous.copy(completedFrames=progress.completedFrames,nativePauseCounters=progress.controlCounters.toMap(),
            nativeTransportPhase=progress.transportPhase,phase=IntegratedDirectPhase.SEEKING,playWhenReady=play,
            sourcePositionFrame=if(sameEpoch) progress.sourcePositionFrame else previous.sourcePositionFrame)
    }
    require(progress.sourceEpoch>=previous.sourceEpoch)
    require(progress.sourceEpoch!=previous.sourceEpoch || progress.sourcePositionFrame >= (previous.sourcePositionFrame ?: previous.completedFrames))
    require(progress.sourceEpoch!=previous.sourceEpoch || previous.nativeTransportPhase!=UsbFullTransportPhase.PAUSED || progress.transportPhase!=UsbFullTransportPhase.PAUSED ||
        progress.completedFrames==previous.completedFrames)
    val phase=when(progress.transportPhase) {
        UsbFullTransportPhase.STARTING -> IntegratedDirectPhase.PREPARING
        UsbFullTransportPhase.PLAYING -> if(play) IntegratedDirectPhase.PLAYING else IntegratedDirectPhase.PAUSING
        UsbFullTransportPhase.PAUSING -> IntegratedDirectPhase.PAUSING
        UsbFullTransportPhase.PAUSED -> if(play) IntegratedDirectPhase.RESUMING else IntegratedDirectPhase.PAUSED
        UsbFullTransportPhase.RESUMING -> if(play) IntegratedDirectPhase.RESUMING else IntegratedDirectPhase.PAUSING
        UsbFullTransportPhase.SEEK_DRAINING,UsbFullTransportPhase.SOURCE_REPOSITIONING,UsbFullTransportPhase.SEEK_PRIMING -> IntegratedDirectPhase.SEEKING
        else -> IntegratedDirectPhase.STOPPING // Final success/failure still requires the joined/restored engine result.
    }
    return previous.copy(completedFrames=progress.completedFrames,totalFrames=progress.totalFrames,sampleRateHz=progress.sampleRateHz,
        nativeTransportPhase=progress.transportPhase,phase=phase,playWhenReady=play,nativePauseCounters=progress.controlCounters.toMap(),
        sourcePositionFrame=progress.sourcePositionFrame,sourceEpoch=progress.sourceEpoch,seekCapable=progress.seekable)
}
internal fun completedFramePositionMs(frames: Long, sampleRateHz: Long = UsbDirectPcmFormat.REFERENCE_RATE_HZ): Long {
    require(frames>=0 && UsbDirectPcmFormat.supportsRate(sampleRateHz))
    val seconds=frames/sampleRateHz
    val fraction=(frames%sampleRateHz)*1000/sampleRateHz
    if (seconds>Long.MAX_VALUE/1000) return Long.MAX_VALUE
    val whole=seconds*1000
    return if (whole>Long.MAX_VALUE-fraction) Long.MAX_VALUE else whole+fraction
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
    val seeked=(result.transport?.count("seekConfirmed") ?: 0L)>0L
    val cumulative=(result.transport?.count("framesCompleted") ?: previous.completedFrames).coerceAtLeast(0)
    val completed=if(seeked) cumulative else cumulative.coerceAtMost(total)
    val sourceRate=result.flacSource?.count("sampleRate") ?: previous.sampleRateHz
    val rateVerified=UsbDirectPcmFormat.supportsRate(sourceRate) && result.transport?.count("schedulerNominalRateHz")==sourceRate
    val success=rateVerified && result.status==UsbSilentStatus.FULL_FLAC_USB_TRANSPORT_VERIFIED && result.transport?.verified==true &&
        result.transport?.count("seekConfirmed")==0L && result.transport?.count("sourceSeekAttempts")==0L && safe && total>0 && completed==total
    val referenceUnavailableSuccess=rateVerified && result.status==UsbSilentStatus.FULL_FLAC_USB_TRANSPORT_COMPLETE_MD5_UNAVAILABLE &&
        result.configuration?.let { UsbFlacFullStatus.classify(it,result.transport,result.flacSource,result.nativeMemoryReleased)==
            UsbSilentStatus.FULL_FLAC_USB_TRANSPORT_COMPLETE_MD5_UNAVAILABLE }==true &&
        result.originalFlacFdClosed==true && safe && total>0 && completed==total
    val seekSuccess=rateVerified && result.status==UsbSilentStatus.SEEKED_SEGMENTS_USB_TRANSPORT_VERIFIED && result.transport?.seekVerified==true && safe &&
        result.flacSource?.seekComplete==true && result.transport?.count("sourcePositionFrame")==total
    val stopped=result.status==UsbSilentStatus.CANCELLED && safe
    val nativePhase=result.transport?.counters?.get("playbackTransportState")?.let { UsbFullTransportPhase.entries.getOrNull(it.toInt()) } ?: previous.nativeTransportPhase
    return previous.copy(engine=result,sampleRateHz=sourceRate,totalFrames=total,completedFrames=completed,cleanupSafe=safe,playWhenReady=false,nativeTransportPhase=nativePhase,
        nativePauseCounters=result.transport?.counters?.filterKeys { it in UsbIsoNativeStats.controlFields || it in UsbIsoNativeStats.seekFields } ?: previous.nativePauseCounters,
        sourcePositionFrame=if((result.transport?.count("seekConfirmed") ?: 0L)>0L) result.transport?.counters?.get("sourcePositionFrame") ?: previous.sourcePositionFrame else completed,
        phase=if(success || referenceUnavailableSuccess || seekSuccess) IntegratedDirectPhase.ENDED else if(stopped) IntegratedDirectPhase.STOPPED else IntegratedDirectPhase.ERROR,
        error=if(success || referenceUnavailableSuccess || seekSuccess || stopped) null else integratedDirectFailure(result))
}

internal val integratedDirectCommands=intArrayOf(
    androidx.media3.common.Player.COMMAND_GET_CURRENT_MEDIA_ITEM,androidx.media3.common.Player.COMMAND_GET_TIMELINE,
    androidx.media3.common.Player.COMMAND_GET_METADATA,androidx.media3.common.Player.COMMAND_SET_MEDIA_ITEM,
    androidx.media3.common.Player.COMMAND_PREPARE,androidx.media3.common.Player.COMMAND_STOP,androidx.media3.common.Player.COMMAND_PLAY_PAUSE,
    androidx.media3.common.Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
internal fun allowsDirectCommand(command: Int,internalController: Boolean): Boolean =
    command in if(internalController) integratedDirectCommands else intArrayOf(
        androidx.media3.common.Player.COMMAND_GET_CURRENT_MEDIA_ITEM,androidx.media3.common.Player.COMMAND_GET_METADATA,
        androidx.media3.common.Player.COMMAND_GET_TIMELINE,androidx.media3.common.Player.COMMAND_STOP,androidx.media3.common.Player.COMMAND_PLAY_PAUSE,
        androidx.media3.common.Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
