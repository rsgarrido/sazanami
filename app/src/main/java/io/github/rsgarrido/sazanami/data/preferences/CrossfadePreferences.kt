package io.github.rsgarrido.sazanami.data.preferences

object CrossfadePreferences {
    const val MIN_DURATION_MS = 1_000
    const val MAX_DURATION_MS = 12_000
    const val DEFAULT_DURATION_MS = 5_000

    fun clampDurationMs(durationMs: Int): Int =
        durationMs.coerceIn(MIN_DURATION_MS, MAX_DURATION_MS)
}
