package io.github.rsgarrido.sazanami.data.listening

import kotlin.math.min

object ListeningQualificationRuleV1 {
    const val VERSION = 1
    const val MAXIMUM_THRESHOLD_MS = 240_000L

    /** Returns null when duration cannot provide time-threshold qualification evidence. */
    fun thresholdMs(trackDurationMs: Long?): Long? {
        if (trackDurationMs == null || trackDurationMs <= 0L) return null

        val ceilingHalf = trackDurationMs / 2L + trackDurationMs % 2L
        return min(ceilingHalf, MAXIMUM_THRESHOLD_MS)
    }

    fun isTimeQualified(trackDurationMs: Long?, listenedMs: Long): Boolean {
        val threshold = thresholdMs(trackDurationMs) ?: return false
        return listenedMs >= threshold
    }
}
