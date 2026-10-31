package io.github.rsgarrido.sazanami.lyrics

/** Captured synchronously at the user action, never from a display-progress tick. */
data class LyricsPositionSnapshot(
    val trackKey: String?, val positionMs: Long, val ready: Boolean,
    val seeking: Boolean = false, val direct: Boolean = false, val confirmed: Boolean = true
) {
    fun positionFor(target: String): Long {
        if (trackKey != target) throw LyricsEditException(LyricsEditError.TRACK_MISMATCH)
        if (!ready || seeking || !confirmed || positionMs < 0) throw LyricsEditException(LyricsEditError.POSITION_UNAVAILABLE)
        return positionMs
    }
}

fun interface LyricsPositionCapture {
    fun capture(target: SongLyricsIdentity): LyricsPositionSnapshot
}
