package io.github.rsgarrido.sazanami.ui.state

import androidx.compose.runtime.compositionLocalOf

/** Rendering preview only; never changes the authoritative progress flow or listening evidence. */
internal val LocalDirectSeekPreview = compositionLocalOf<Int?> { null }

internal data class DirectScrubAuthority(val enabled: Boolean,val generation: Long,val seekSerial: Long,val allowed: Boolean)

/** One bounded gesture. A MediaSession seek, track/mode change or lost capability cancels its commit. */
internal class DirectSeekScrub {
    private var down=false
    private var origin: DirectScrubAuthority?=null
    private var cancelled=false
    private var target: Int?=null
    val active get()=down
    val directGesture get()=origin?.enabled==true
    fun begin(authority: DirectScrubAuthority) {down=true;origin=authority;cancelled=false;target=null}
    fun observe(authority: DirectScrubAuthority) {if(down && (origin?.enabled==true || authority.enabled) && authority!=origin) cancelled=true}
    fun change(position: Int,authority: DirectScrubAuthority): Boolean {
        observe(authority)
        // Keep a completed/cancelled direct gesture as a tombstone until the next explicit begin.
        // A late touch callback must never fall through to the immediate command path.
        if(!down) return origin?.enabled==true
        if(origin?.enabled!=true && !authority.enabled) return false // Normal playback retains immediate routing.
        if(!cancelled) target=position
        return true
    }
    fun preview(): Int?=if(!cancelled && origin?.allowed==true) target else null
    fun cancel() {cancelled=true}
    fun end(authority: DirectScrubAuthority): Int? {
        observe(authority)
        val committed=if(down && origin?.enabled==true && origin?.allowed==true && !cancelled) target else null
        down=false;target=null;return committed
    }
}

/** The actual control completes this transaction, after its final value callback. Pointer-up does not commit. */
internal class DirectSeekDispatch(private val authority: ()->DirectScrubAuthority,
    private val commit: (Int)->Unit,private val previewChanged: (Int?)->Unit={}) {
    private val scrub=DirectSeekScrub()
    private var keyboard=false
    private var begun=0L
    private var completing=0L
    private var invalidThrough=0L
    private var observedAuthority=authority()
    private var publishing=false
    val directGesture get()=scrub.directGesture
    fun begin() {
        observe()
        check(begun<Long.MAX_VALUE)
        ++begun;keyboard=false
        // Completion callbacks are ordered by the control's drag consumer. A following pointer-down
        // must not overwrite a released gesture whose final value/completion is still queued.
        if(!scrub.active) start(completing+1)
    }
    private fun start(ordinal: Long) {
        completing=ordinal;scrub.begin(authority())
        if(ordinal<=invalidThrough) scrub.cancel()
        previewChanged(null)
    }
    fun change(position: Int) {
        if(keyboard) {immediate(position);return}
        if(!scrub.active && completing<begun) start(completing+1)
        if(scrub.change(position,authority())) previewChanged(scrub.preview()) else publish(position)
    }
    fun pointerUp() {} // Final pointer pass is not a barrier for the Slider's queued drag deltas.
    fun retire() {cancel();scrub.end(authority());completing=begun;keyboard=false}
    fun complete() {
        if(keyboard) {keyboard=false;return}
        if(!scrub.active) return
        observe()
        val target=scrub.end(authority());previewChanged(null)
        if(target!=null) publish(target)
    }
    fun cancel() {invalidThrough=begun;scrub.cancel();previewChanged(null)}
    fun observe() {
        val current=authority()
        val ownSerialPublication=publishing && current.copy(seekSerial=observedAuthority.seekSerial)==observedAuthority
        if(current!=observedAuthority && !ownSerialPublication && (current.enabled || observedAuthority.enabled)) cancel()
        observedAuthority=current;scrub.observe(current);previewChanged(scrub.preview())
    }
    fun keyboardInput() {cancel();keyboard=true}
    private fun publish(position: Int) {
        val before=authority();publishing=true
        try {commit(position)} finally {
            publishing=false
            val after=authority()
            if(after.copy(seekSerial=before.seekSerial)!=before && (before.enabled || after.enabled)) cancel()
            observedAuthority=after
        }
    }
    fun immediate(position: Int) {cancel();publish(position)} // Explicit semantics/buttons/external commands, not late touch deltas.
}
