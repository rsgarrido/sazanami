package io.github.rsgarrido.sazanami.player.usb

internal data class UsbFullControlIntent(val sequence: Long,val pauses: Long,val resumes: Long,val play: Boolean)

/** One per-track command intent, including commands during priming. Native progress alone confirms lifecycle state.
 * Short synchronized sections serialize bind/commands; the sink only publishes JNI intent and a nonblocking wake. */
internal class UsbFullPlaybackControl {
    private var sequence=0L;private var pauses=0L;private var resumes=0L;private var play=true
    private var closed=false
    private var sink: ((UsbFullControlIntent)->Boolean)?=null
    private var stopSink: (() -> Unit)?=null
    @Volatile var failure: String?=null;private set
    @get:Synchronized val playWhenReady get()=play && !closed
    @get:Synchronized val stopped get()=closed
    @Synchronized fun intent()=UsbFullControlIntent(sequence,pauses,resumes,play)
    @Synchronized fun request(playing: Boolean): Boolean {
        if(closed) return false
        if(sequence==Long.MAX_VALUE) {
            val error=IllegalStateException("Control sequence exhausted");fail(error);throw error
        }
        ++sequence;if(playing)++resumes else ++pauses;play=playing
        return deliver()
    }
    @Synchronized fun bind(command: (UsbFullControlIntent)->Boolean,stop: ()->Unit) {
        check(sink==null)
        sink=command;stopSink=stop
        if(closed) stop() else deliver()
    }
    private fun deliver(): Boolean {
        val command=sink ?: return true // Staged until this same source's native handle exists.
        try {
            if(command(intent())) return true
            closed=true;play=false;return false // Native terminal/Stop has precedence.
        } catch(error: Exception) {
            failure=error.javaClass.simpleName;closed=true;play=false
            try { stopSink?.invoke() } catch(stopError: Exception) { failure+="/${stopError.javaClass.simpleName}" }
            throw error
        } catch(error: LinkageError) {
            failure=error.javaClass.simpleName;closed=true;play=false
            try { stopSink?.invoke() } catch(stopError: Exception) { failure+="/${stopError.javaClass.simpleName}" }
            throw error
        }
    }
    @Synchronized fun stop() { closed=true;play=false;stopSink?.invoke() }
    @Synchronized fun fail(error: Exception) { failure=error.javaClass.simpleName;closed=true;play=false;stopSink?.invoke() }
    @Synchronized fun recordFailure(error: Throwable) { failure=error.javaClass.simpleName;closed=true;play=false }
    @Synchronized fun finish() { closed=true;play=false;sink=null;stopSink=null }
}
