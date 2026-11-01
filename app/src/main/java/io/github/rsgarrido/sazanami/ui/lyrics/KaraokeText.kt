package io.github.rsgarrido.sazanami.ui.lyrics

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import io.github.rsgarrido.sazanami.lyrics.KaraokeLineState
import io.github.rsgarrido.sazanami.lyrics.KaraokeResolver
import io.github.rsgarrido.sazanami.lyrics.KaraokeSegmentState
import io.github.rsgarrido.sazanami.lyrics.KaraokeTiming

internal data class KaraokeTextLine(val cueIndex: Int, val text: String, val timing: KaraokeTiming?)
internal data class KaraokeTextStyles(val upcoming: SpanStyle, val completed: SpanStyle, val active: SpanStyle)

/** One Text node retains natural wrapping, punctuation and exact UTF-16 source ranges. */
internal fun buildKaraokeText(lines: List<KaraokeTextLine>, progress: List<KaraokeLineState>, styles: KaraokeTextStyles): AnnotatedString =
    buildAnnotatedString {
        val byCue = progress.associateBy { it.cueIndex }
        lines.forEachIndexed { index, line ->
            if (index > 0) append('\n')
            val offset = length
            append(line.text)
            val timing = line.timing ?: return@forEachIndexed
            val current = byCue[line.cueIndex] ?: return@forEachIndexed
            // Untimed prefixes/gaps remain upcoming; no guessed timing or whitespace tokenization.
            addStyle(styles.upcoming, offset, length)
            timing.segments.forEach { segment ->
                if (segment.startChar != segment.endCharExclusive) addStyle(
                    when (KaraokeResolver.state(segment, current.activeTimestampMs)) {
                        KaraokeSegmentState.UPCOMING -> styles.upcoming
                        KaraokeSegmentState.COMPLETED -> styles.completed
                        KaraokeSegmentState.ACTIVE -> styles.active
                    }, offset + segment.startChar, offset + segment.endCharExclusive)
            }
        }
    }
