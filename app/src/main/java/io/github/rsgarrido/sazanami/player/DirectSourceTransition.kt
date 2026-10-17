package io.github.rsgarrido.sazanami.player

import io.github.rsgarrido.sazanami.player.usb.UsbFullPlaybackControl
import io.github.rsgarrido.sazanami.player.usb.UsbFullProgress
import io.github.rsgarrido.sazanami.player.usb.UsbFullTransportPhase
import io.github.rsgarrido.sazanami.player.usb.directSeekTargetFrame

internal enum class DirectSourceTransitionStatus {
    WAITING_FOR_CLEANUP, PREPARING, READY, COMPLETED, UNCHANGED, FAILED, SUPERSEDED, CANCELLED, REJECTED
}

/** Requested identity is separate from the accepted item and native generation in player state. */
internal data class DirectSourceTransitionState(
    val requestId: Long,
    val mediaId: String,
    val itemInstanceId: String?,
    val playWhenReady: Boolean,
    val startPositionMs: Long,
    val status: DirectSourceTransitionStatus,
    val error: String? = null
)

/** READY confirms native readiness; COMPLETED is verified early EOF; UNCHANGED selects no new run. */
internal data class DirectSourceTransitionResult(
    val requestId: Long,
    val status: DirectSourceTransitionStatus,
    val nativeGeneration: Long? = null,
    val error: String? = null
)

/** One run's initial intent. The existing mailbox is bound BEFORE native execution starts. */
internal class DirectSourceStart(val positionMs: Long, playing: Boolean) {
    val control = UsbFullPlaybackControl()
    var playWhenReady = playing
        private set
    var positionPending = positionMs > 0L
        private set
    private var staged = false
    private var targetFrame = 0L
    private var seekSerial = 0L
    var failure: String? = null
        private set

    init {
        require(positionMs >= 0L)
        // A nonzero start holds OUT admission until the new source's seek is confirmed.
        if (!playing || positionPending) control.request(false)
    }

    fun requestPlay(playing: Boolean): Boolean {
        playWhenReady = playing
        return control.request(playing && !positionPending)
    }

    fun requestSeek(frame: Long): Boolean {
        if (!control.seek(frame)) return false
        if (positionPending) {
            targetFrame = frame
            seekSerial = control.intent().seekSerial
            staged = true
        }
        return true
    }

    /** UsbAudioController supplies accepted STREAMINFO before constructing the native transport. */
    fun stagePosition(progress: UsbFullProgress) {
        if (!positionPending || staged || failure != null) return
        if (!progress.seekable) {
            fail("DIRECT_INITIAL_POSITION_UNSUPPORTED")
            return
        }
        if (progress.totalFrames <= 0L || progress.completedFrames != 0L || progress.sourcePositionFrame != 0L) {
            fail("DIRECT_INITIAL_POSITION_UNSATISFIED")
            return
        }
        targetFrame = directSeekTargetFrame(positionMs, progress.sampleRateHz, progress.totalFrames)
        if (!requestSeek(targetFrame)) fail("DIRECT_INITIAL_SEEK_REJECTED")
    }

    /** Called only after the ordinary generation/epoch/progress validation has succeeded. */
    fun confirmPosition(progress: UsbFullProgress) {
        if (!positionPending || !staged || failure != null) return
        if ((progress.controlCounters["confirmedSeekSerial"] ?: 0L) < seekSerial) return
        // Initial seeking runs with play=false, so its confirmed boundary must be quiet.
        require(progress.sourceEpoch > 0L && progress.sourcePositionFrame == targetFrame &&
            progress.transportPhase == UsbFullTransportPhase.PAUSED)
        positionPending = false
        if (playWhenReady && !control.request(true)) fail("DIRECT_INITIAL_RESUME_REJECTED")
    }

    private fun fail(reason: String) {
        failure = reason
        control.fail(IllegalStateException(reason))
    }
}
