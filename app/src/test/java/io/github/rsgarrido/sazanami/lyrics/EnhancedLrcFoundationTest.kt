package io.github.rsgarrido.sazanami.lyrics

import org.junit.Assert.*
import org.junit.Test

class EnhancedLrcFoundationTest {
    private val parser = LrcParser()

    @Test fun spansUseExactUtf16RangesIncludingSpacesPunctuationAndSyllables() {
        val source = parser.parseSource("[offset:+250]\n[00:12]Intro <00:12.0>日本<00:12.4>語、 <00:12.7>한국어🙂!")
        val line = source.lines[1]
        assertEquals("Intro 日本語、 한국어🙂!", line.displayedText)
        assertEquals(listOf("日本", "語、 ", "한국어🙂!"), line.segments.map {
            line.displayedText.substring(it.startChar, it.endCharExclusive)
        })
        val cue = (parser.project(source) as LyricsDocument.Synced).cues.single()
        assertEquals(12250L, cue.timestampMs)
        assertEquals(listOf(12250L, 12650L, 12950L), cue.segments.map { it.timestampMs })
        assertEquals(listOf(12000L, 12400L, 12700L), cue.segments.map { it.sourceTimestampMs })
        assertTrue(line.wordTimingReliable)
    }

    @Test fun malformedOrDecreasingOrAmbiguousSegmentsFallBackWithoutLosingReadableText() {
        val cases = listOf(
            "[00:01]<00:02>First <00:01>second",
            "[00:01][00:03]<00:01>Repeated",
            "[00:01]<00:01>First <bad>second",
            "[00:01]<00:01>First <00:02",
            "[00:01]<00:00.5>Early",
            "[00:01]<00:61>Invalid seconds"
        )
        cases.forEach { raw ->
            val source = parser.parseSource(raw)
            assertEquals(raw, source.originalText)
            assertFalse(source.lines.single().wordTimingReliable)
            val document = parser.project(source) as LyricsDocument.Synced
            assertTrue(document.cues.all { it.segments.isEmpty() })
            assertTrue(document.cues.all { it.content is LyricCueContent.Text })
            assertTrue(source.diagnostics.isNotEmpty())
        }
    }

    @Test fun duplicateAndEmptySegmentsAndMixedLinesRetainTheirOrder() {
        val source = parser.parseSource("Untimed\n[00:01]<00:01><00:01>One<00:02>\n[00:03]Standard\n[00:04]\n")
        val enhanced = source.lines[1]
        assertEquals(listOf("", "One", ""), enhanced.segments.map {
            enhanced.displayedText.substring(it.startChar, it.endCharExclusive)
        })
        val cues = (parser.project(source) as LyricsDocument.Synced).cues
        assertEquals(3, cues.first().segments.size)
        assertTrue(cues[1].segments.isEmpty())
        assertEquals(LyricCueContent.Instrumental, cues.last().content)
        assertEquals("Untimed", source.lines.first().rawText)
    }
}
