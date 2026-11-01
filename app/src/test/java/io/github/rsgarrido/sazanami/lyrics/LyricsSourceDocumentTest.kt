package io.github.rsgarrido.sazanami.lyrics

import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class LyricsSourceDocumentTest {
    private val parser = LrcParser()

    @Test fun originalBytesMetadataMixedContentAndNewlinesSurviveUnchangedExport() {
        val text = "[ti:Synthetic]\r\n[custom:keep me]\nUntimed text\r[00:02.000][00:01.2]Timed\r\n[00:03]\n\n[broken"
        for (encoding in LyricsEncoding.entries) {
            val bytes = encoding.bom() + text.toByteArray(encoding.charset)
            val source = parser.parseSource(bytes)
            assertArrayEquals(bytes, source.serializeUnchanged())
            assertArrayEquals(bytes, source.encodeLines(source.lines))
            assertEquals(encoding, source.encoding)
            assertTrue(source.hasBom)
            assertEquals(text, source.originalText)
            assertEquals("custom", source.lines[1].metadata?.key)
            assertEquals("Untimed text", source.lines[2].rawText)
            assertEquals(listOf("[00:02.000]", "[00:01.2]"), source.lines[3].timestamps.map { it.raw })
            assertTrue(source.diagnostics.any { it.code == LyricsDiagnosticCode.UNKNOWN_METADATA })
            assertTrue(source.diagnostics.any { it.code == LyricsDiagnosticCode.UNSUPPORTED_LINE })
            assertEquals(source.lines.size, source.lines.map { it.id }.distinct().size)
            val cues = (parser.project(source) as LyricsDocument.Synced).cues
            assertEquals(listOf(1200L, 2000L, 3000L), cues.map { it.timestampMs })
            assertEquals(LyricCueContent.Instrumental, cues.last().content)
            assertEquals(cues[0].sourceLineId, cues[1].sourceLineId)
        }
    }

    @Test fun rewritePreservesUnrelatedRawLinesAndLineIdentityWithoutDoubleOffset() {
        val source = parser.parseSource("[offset:+250]\r\n[custom:x]\r\n[00:01.00]Before\n")
        val edited = source.lines.map { if (it.kind == LyricsSourceLineKind.TIMED) it.copy(rawText = "[00:01.00]After") else it }
        assertEquals(source.lines.map { it.id }, edited.map { it.id })
        val reparsed = parser.reparseLines(source, edited)
        assertEquals(edited.map { it.id }, reparsed.lines.map { it.id })
        val bytes = source.encodeLines(edited)
        assertEquals("[offset:+250]\r\n[custom:x]\r\n[00:01.00]After\n", bytes.toString(Charsets.UTF_8))
        val reloaded = parser.parseSource(bytes)
        val cue = (parser.project(reloaded) as LyricsDocument.Synced).cues.single()
        assertEquals(1000L, cue.sourceTimestampMs)
        assertEquals(1250L, cue.timestampMs)
        assertEquals(250L, reloaded.metadata.offsetMs)
        assertArrayEquals(bytes, reloaded.serializeUnchanged())
        val defensiveCopy = source.serializeUnchanged()
        defensiveCopy[0] = 0
        assertEquals('['.code.toByte(), source.serializeUnchanged()[0])
    }

    @Test fun structuredReorderRetainsRowIdentitiesAndRecomputesChangedTimingRanges() {
        val source = parser.parseSource("[custom:keep]\n[00:01]<00:01>One\n[00:02]Two\n")
        val rows = listOf(source.lines[0], source.lines[2],
            source.lines[1].copy(rawText = "[00:03]<00:03>Changed"), source.lines[3])
        val reparsed = parser.reparseLines(source, rows)
        assertEquals(rows.map { it.id }, reparsed.lines.map { it.id })
        assertEquals("Changed", reparsed.lines[2].displayedText)
        val cue = (parser.project(reparsed) as LyricsDocument.Synced).cues.last()
        assertEquals(source.lines[1].id, cue.sourceLineId)
        assertEquals(3000L, cue.sourceTimestampMs)
        assertEquals(7, cue.segments.single().endCharExclusive)
        assertEquals(source.lines[0].id, reparsed.diagnostics.single().lineId)
    }

    @Test fun negativeOffsetClampsOnlyProjectionAndRetainsUnclampedSegmentSourceTimes() {
        val source = parser.parseSource("[offset:-1500]\n[00:01]<00:01>One<00:01.1>two")
        val cue = (parser.project(source) as LyricsDocument.Synced).cues.single()
        assertEquals(1000L, source.lines[1].timestamps.single().timestampMs)
        assertEquals(0L, cue.timestampMs)
        assertEquals(listOf(1000L, 1100L), cue.segments.map { it.sourceTimestampMs })
        assertEquals(listOf(0L, 0L), cue.segments.map { it.timestampMs })
        assertEquals(-1500L, source.metadata.offsetMs)
    }

    @Test fun invalidEncodedBytesHaveDiagnosticsButExactBytePreservation() {
        val bytes = byteArrayOf(0xff.toByte(), 10, 0xfe.toByte())
        val source = parser.parseSource(bytes)
        assertTrue(source.diagnostics.any { it.code == LyricsDiagnosticCode.INVALID_ENCODING })
        assertArrayEquals(bytes, source.serializeUnchanged())
        assertArrayEquals(bytes, source.encodeLines(source.lines.map { it.copy(id = "copy-${it.id}") }))
    }

    @Test fun sourceAndLineAndTokenLimitsRejectWithoutBuildingUnboundedPlaybackData() {
        expectLimit { parser.parseSource(ByteArray(LyricsLimits.SOURCE_BYTES + 1)) }
        expectLimit { parser.parseSource("x".repeat(LyricsLimits.LINE_CHARACTERS + 1)) }
        expectLimit { parser.parseSource("\n".repeat(LyricsLimits.LINES)) }
        expectLimit { parser.parseSource(("[00:01]".repeat(1000) + "x\n").repeat(41)) }
        expectLimit { parser.parseSource(("[00:01]" + "<00:01>x".repeat(1000) + "\n").repeat(81)) }
        val manyWarnings = parser.parseSource("[unknown:x]\n".repeat(400))
        assertEquals(LyricsLimits.DIAGNOSTICS, manyWarnings.diagnostics.size)
    }

    @Test(expected = CancellationException::class)
    fun parsingPropagatesCancellation() {
        parser.parseSource("[00:01]Text") { throw CancellationException() }
    }

    private fun expectLimit(block: () -> Unit) {
        try { block(); fail("Expected an input limit") } catch (_: LyricsInputLimitException) { }
    }
}
