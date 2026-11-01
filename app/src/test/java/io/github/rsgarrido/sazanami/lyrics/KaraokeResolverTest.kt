package io.github.rsgarrido.sazanami.lyrics

import org.junit.Assert.*
import org.junit.Test

class KaraokeResolverTest {
    private val parser = LrcParser()
    private fun cue(raw: String) = (parser.parse(raw) as LyricsDocument.Synced).cues.first()
    private fun states(cue: LyricCue, position: Long): List<KaraokeSegmentState> {
        val timing = requireNotNull(KaraokeResolver.prepare(cue))
        val current = KaraokeResolver.resolve(timing, position)
        return cue.segments.map { KaraokeResolver.state(it, current) }
    }

    @Test fun exactBoundariesPauseAndBidirectionalSeeksAreStateless() {
        val cue = cue("[00:12]<00:12>Stay <00:12.40>with <00:12.75>me")
        val upcoming = KaraokeSegmentState.UPCOMING
        val active = KaraokeSegmentState.ACTIVE
        val completed = KaraokeSegmentState.COMPLETED
        assertEquals(listOf(upcoming, upcoming, upcoming), states(cue, 11999))
        assertEquals(listOf(active, upcoming, upcoming), states(cue, 12000))
        assertEquals(listOf(active, upcoming, upcoming), states(cue, 12399))
        assertEquals(listOf(completed, active, upcoming), states(cue, 12400))
        assertEquals(listOf(completed, active, upcoming), states(cue, 12749))
        assertEquals(listOf(completed, completed, active), states(cue, 12750))
        assertEquals(listOf(completed, completed, active), states(cue, 20000))
        repeat(3) { assertEquals(listOf(completed, active, upcoming), states(cue, 12401)) }
        assertEquals(listOf(active, upcoming, upcoming), states(cue, 12001))
    }

    @Test fun delayedFirstWordAndUntimedPrefixDoNotInventTiming() {
        val cue = cue("[00:01]Prefix <00:02>timed")
        assertEquals(7, cue.segments.single().startChar)
        assertEquals(listOf(KaraokeSegmentState.UPCOMING), states(cue, 1999))
        assertEquals(listOf(KaraokeSegmentState.ACTIVE), states(cue, 2000))
    }

    @Test fun equalTimestampsAndZeroWidthMarkersHaveDeterministicBoundaries() {
        val cue = cue("[00:01]<00:01><00:01>One<00:01> two<00:02>")
        assertEquals(listOf(KaraokeSegmentState.ACTIVE, KaraokeSegmentState.ACTIVE,
            KaraokeSegmentState.ACTIVE, KaraokeSegmentState.UPCOMING), states(cue, 1000))
        assertEquals(listOf(KaraokeSegmentState.COMPLETED, KaraokeSegmentState.COMPLETED,
            KaraokeSegmentState.COMPLETED, KaraokeSegmentState.ACTIVE), states(cue, 2000))
        val empty = cue.copy(segments = listOf(TimedLyricSegment(1000, 1000, 0, 0)))
        assertNull(KaraokeResolver.prepare(empty))
    }

    @Test fun malformedAndUnreliableTimingFallsBackWithoutChangingSource() {
        listOf("[00:01]Standard", "[00:01]<00:02>One<00:01>two",
            "[00:01][00:03]<00:01>Repeated", "[00:01]<bad>Text").forEach {
            val source = parser.parseSource(it)
            val bytes = source.serializeUnchanged()
            val document = parser.project(source) as LyricsDocument.Synced
            document.cues.forEach { cue -> assertNull(KaraokeResolver.prepare(cue)) }
            assertArrayEquals(bytes, source.serializeUnchanged())
        }
        val valid = cue("[00:01]<00:01>One <00:02>two")
        val bad = listOf(
            listOf(TimedLyricSegment(1000, 1000, -1, 2)),
            listOf(TimedLyricSegment(1000, 1000, 0, 100)),
            listOf(TimedLyricSegment(1000, 1000, 4, 2)),
            listOf(TimedLyricSegment(1000, 1000, 0, 5), TimedLyricSegment(2000, 2000, 4, 7)),
            listOf(TimedLyricSegment(2000, 2000, 0, 4), TimedLyricSegment(1000, 1000, 4, 7)),
            listOf(TimedLyricSegment(-1, -1, 0, 7)),
            listOf(TimedLyricSegment(999, 999, 0, 7))
        )
        bad.forEach { assertNull(KaraokeResolver.prepare(valid.copy(segments = it))) }
    }

    @Test fun offsetsAreAppliedOnceIncludingClampedNegativeTimes() {
        val positive = cue("[offset:+250]\n[00:01]<00:01>One<00:02>two")
        assertEquals(1250L, positive.timestampMs)
        assertEquals(KaraokeSegmentState.UPCOMING, states(positive, 1249).first())
        assertEquals(KaraokeSegmentState.ACTIVE, states(positive, 1250).first())
        val negative = cue("[offset:-1500]\n[00:01]<00:01>One<00:02>two")
        assertEquals(0L, negative.timestampMs)
        assertEquals(listOf(KaraokeSegmentState.COMPLETED, KaraokeSegmentState.ACTIVE), states(negative, 500))
    }

    @Test fun utf16RangesPreserveMultilingualCombiningEmojiAndLongText() {
        val parts = listOf("¡Luz! e\u0301 ", "日本語 한국어 中文🙂, ", "🌊 ".repeat(1000))
        val raw = "[00:01]<00:01>${parts[0]}<00:02>${parts[1]}<00:03>${parts[2]}"
        val source = parser.parseSource(raw)
        val cue = (parser.project(source) as LyricsDocument.Synced).cues.single()
        val timing = requireNotNull(KaraokeResolver.prepare(cue))
        val text = (cue.content as LyricCueContent.Text).text
        assertEquals(parts.joinToString(""), text)
        assertEquals(parts, timing.segments.map { text.substring(it.startChar, it.endCharExclusive) })
        assertEquals(raw, source.originalText)
        val emoji = LyricCue(1000, LyricCueContent.Text("🙂!"), segments = listOf(TimedLyricSegment(1000, 1000, 0, 1)))
        assertNull(KaraokeResolver.prepare(emoji))
    }

    @Test fun duplicateCueTimesKeepIndependentLineIdentitiesAndInstrumentalHasNoWords() {
        val document = parser.parse("[00:01]<00:01>First<00:02> line\n[00:01]<00:01.5>Second line\n[00:03]") as LyricsDocument.Synced
        assertNotEquals(document.cues[0].sourceLineId, document.cues[1].sourceLineId)
        assertEquals(1000L, KaraokeResolver.resolve(requireNotNull(KaraokeResolver.prepare(document.cues[0])), 1000))
        assertNull(KaraokeResolver.resolve(requireNotNull(KaraokeResolver.prepare(document.cues[1])), 1000))
        assertNull(KaraokeResolver.prepare(document.cues.last()))
        assertNull(ActiveLyricResolver.resolve(document.cues, 3000))
    }
}
