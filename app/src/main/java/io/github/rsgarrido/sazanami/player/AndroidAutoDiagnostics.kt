package io.github.rsgarrido.sazanami.player

import android.os.SystemClock
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.tracing.Trace
import java.util.concurrent.atomic.AtomicInteger

/** Opt in with adb shell setprop log.tag.SazanamiAuto DEBUG. Never logs position ticks. */
internal object AndroidAutoDiagnostics {
    private const val TAG = "SazanamiAuto"
    private val cookies = AtomicInteger()

    fun log(message: String) {
        if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(TAG, message)
    }

    // Async slices remain correctly paired even when a suspended read resumes on another thread.
    suspend fun <T> measure(stage: String, block: suspend () -> T): T {
        if (io.github.rsgarrido.sazanami.performance.PerformanceTracing.bypassForTests) return block()
        val cookie = cookies.incrementAndGet()
        val started = SystemClock.elapsedRealtime()
        Trace.beginAsyncSection("CDP.Auto.$stage", cookie)
        try {
            return block()
        } finally {
            Trace.endAsyncSection("CDP.Auto.$stage", cookie)
            log("$stage elapsedMs=${SystemClock.elapsedRealtime() - started}")
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
internal class AndroidAutoPlayerDiagnostics(private val player: Player) : Player.Listener {
    private var previousTimeline: Timeline = Timeline.EMPTY
    private var previousUids: List<Any> = emptyList()
    private var timelineEvents = 0

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        val window = Timeline.Window()
        val uids = (0 until timeline.windowCount).map { timeline.getWindow(it, window).uid }
        AndroidAutoDiagnostics.log(
            "timeline event=${++timelineEvents} reason=$reason changed=${timeline != previousTimeline} " +
                "uidsChanged=${uids != previousUids} windows=${timeline.windowCount} " +
                "index=${player.currentMediaItemIndex} mediaId=${player.currentMediaItem?.mediaId}"
        )
        previousTimeline = timeline
        previousUids = uids
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        AndroidAutoDiagnostics.log(
            "transition reason=$reason windows=${player.currentTimeline.windowCount} " +
                "index=${player.currentMediaItemIndex} mediaId=${mediaItem?.mediaId}"
        )
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        AndroidAutoDiagnostics.log("playing=$isPlaying timelineEvents=$timelineEvents")
    }
}
