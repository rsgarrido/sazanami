package io.github.rsgarrido.sazanami.player

internal class PlaybackStateCheckpointPolicy(
    private val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS
) {
    private var lastCheckpointMillis = 0L

    fun shouldCheckpoint(isPlaying: Boolean, nowMillis: Long): Boolean {
        return isPlaying && nowMillis - lastCheckpointMillis >= intervalMillis
    }

    fun recordCheckpoint(nowMillis: Long) {
        lastCheckpointMillis = nowMillis
    }

    companion object {
        const val DEFAULT_INTERVAL_MILLIS = 15_000L
    }
}
