package io.github.rsgarrido.sazanami.lyrics

/** Validated playback ranges. Source parsing and offset application have already happened. */
class KaraokeTiming internal constructor(val segments: List<TimedLyricSegment>)

/** Only boundary changes are published; no raw position ticks enter structural UI state. */
data class KaraokeLineState(val cueIndex: Int, val activeTimestampMs: Long?)

enum class KaraokeSegmentState { UPCOMING, ACTIVE, COMPLETED }

object KaraokeResolver {
    /** Empty projection means ordinary LRC or parser-rejected word timing: use line highlighting. */
    fun prepare(cue: LyricCue): KaraokeTiming? {
        val text = (cue.content as? LyricCueContent.Text)?.text ?: return null
        val segments = cue.segments
        if (segments.isEmpty() || cue.sourceTimestampMs < 0L) return null
        var end = 0
        var effectiveTime = cue.timestampMs
        var sourceTime = cue.sourceTimestampMs
        var visible = false
        for (segment in segments) {
            if (segment.startChar < end || segment.endCharExclusive < segment.startChar ||
                segment.endCharExclusive > text.length || segment.timestampMs < effectiveTime ||
                segment.sourceTimestampMs < sourceTime || !text.isUtf16Boundary(segment.startChar) ||
                !text.isUtf16Boundary(segment.endCharExclusive)) return null
            visible = visible || segment.startChar < segment.endCharExclusive
            end = segment.endCharExclusive
            effectiveTime = segment.timestampMs
            sourceTime = segment.sourceTimestampMs
        }
        return if (visible) KaraokeTiming(segments) else null
    }

    /** Active interval: t <= position < nextDistinctT. Equal timestamps are active together.
     * The final timestamp lasts to cue exit.
     * Zero-width end markers still close the preceding segment, without inventing visible text.
     */
    fun resolve(timing: KaraokeTiming, positionMs: Long): Long? {
        val segments = timing.segments
        var low = 0
        var high = segments.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (segments[middle].timestampMs <= positionMs) low = middle + 1 else high = middle
        }
        return segments.getOrNull(low - 1)?.timestampMs
    }

    fun state(segment: TimedLyricSegment, activeTimestampMs: Long?): KaraokeSegmentState = when {
        activeTimestampMs == null || segment.timestampMs > activeTimestampMs -> KaraokeSegmentState.UPCOMING
        segment.timestampMs == activeTimestampMs -> KaraokeSegmentState.ACTIVE
        else -> KaraokeSegmentState.COMPLETED
    }

    private fun String.isUtf16Boundary(index: Int): Boolean = index in 0..length &&
        !(index > 0 && index < length && this[index - 1].isHighSurrogate() && this[index].isLowSurrogate())
}
