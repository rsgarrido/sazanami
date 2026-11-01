package io.github.rsgarrido.sazanami.lyrics

import org.junit.Assert.*
import org.junit.Test

class LyricsDocumentEditorTest {
    private val parser = LrcParser()
    private val editor = LyricsDocumentEditor(parser)

    @Test fun untouchedBytesAndUnrelatedRowsSurviveOneLineEdit() {
        val bytes = byteArrayOf(0xff.toByte(), 0xfe.toByte()) +
            "[unknown:Keep  this]\r\n[00:01.20][00:03.456] First  line\n\r\nUntimed\r[00:05.000]<00:05.000>Word <00:05.500>spans".toByteArray(Charsets.UTF_16LE)
        val source = parser.parseSource(bytes)
        assertArrayEquals(bytes, source.serializeUnchanged())
        val row = source.lines[1]
        val changed = editor.changeRow(source, row.id, " Changed  line", "00:01.200, 00:03.456")
        assertEquals("[00:01.20][00:03.456] Changed  line", changed.lines[1].rawText)
        assertEquals(source.lines.map { it.id }, changed.lines.map { it.id })
        source.lines.indices.filter { it != 1 }.forEach { index ->
            assertEquals(source.lines[index].rawText, changed.lines[index].rawText)
            assertEquals(source.lines[index].newline, changed.lines[index].newline)
        }
        assertEquals(source.lines.last().segments, changed.lines.last().segments)
        assertEquals(LyricsEncoding.UTF16_LE, changed.encoding)
        assertTrue(changed.hasBom)
    }

    @Test fun enhancedTextRequiresChoiceAndOnlyAffectedMarkersAreRemoved() {
        val source = parser.parseSource("[00:01]<00:01>One <00:01.5>word\n[00:02]<00:02>Other")
        val row = source.lines.first()
        expect(LyricsEditError.WORD_TIMING_CHOICE) { editor.changeRow(source, row.id, "New words", "00:01.000") }
        val changed = editor.changeRow(source, row.id, "New words", "00:01.000", removeWordTimings = true)
        assertTrue(changed.lines.first().segments.isEmpty())
        assertEquals(source.lines.last().rawText, changed.lines.last().rawText)
        assertEquals(source.lines.last().segments, changed.lines.last().segments)
    }

    @Test fun changingOnlyLineTimeRetainsInlineSyntaxAndRepeatedTimesAreExplicit() {
        val source = parser.parseSource("[00:01]<00:01>One <00:02>two")
        val changed = editor.changeRow(source, source.lines.first().id, "One two", "00:00.500")
        assertEquals("[00:00.500]<00:01>One <00:02>two", changed.originalText)
        val repeated = parser.parseSource("[00:01.2][00:02.30]Repeat")
        expect(LyricsEditError.RAW_REQUIRED) { editor.stamp(repeated, repeated.lines.first().id, 3000) }
        assertArrayEquals(repeated.serializeUnchanged(), editor.changeRow(repeated, repeated.lines.first().id,
            "Repeat", "00:01.200, 00:02.300").serializeUnchanged())
        assertEquals("[00:03.000][00:02.30]Repeat", editor.changeRow(repeated, repeated.lines.first().id,
            "Repeat", "00:03.000, 00:02.300").originalText)
    }

    @Test fun insertRemoveAndReorderPreserveIdentitiesAndNeverConcatenateRows() {
        val source = parser.parseSource("[unknown:keep]\r\nFirst\nSecond")
        val moved = editor.move(source, source.lines.last().id, -1)
        assertEquals(listOf("[unknown:keep]", "Second", "First"), moved.lines.map { it.rawText })
        assertEquals(source.lines.last().id, moved.lines[1].id)
        val inserted = editor.insert(moved, moved.lines.last().id)
        assertEquals(4, inserted.lines.size)
        val removed = editor.remove(inserted, inserted.lines.last().id)
        assertEquals(moved.lines.map { it.rawText }, removed.lines.map { it.rawText })
    }

    @Test fun stampSubtractsOffsetAndProjectionAppliesItExactlyOnceAfterReload() {
        for (offset in listOf(-250L, 0L, 250L)) {
            val source = editor.offset(parser.parseSource("First\nSecond"), offset)
            val row = source.lines.first { it.rawText == "First" }
            val stamped = editor.stamp(source, row.id, 1500)
            assertEquals(1500 - offset, stamped.lines.first { it.id == row.id }.timestamps.single().timestampMs)
            val loaded = parser.parseSource(stamped.serializeUnchanged())
            assertEquals(1500L, (parser.project(loaded) as LyricsDocument.Synced).cues.single().timestampMs)
            assertEquals(offset, loaded.metadata.offsetMs)
        }
    }

    @Test fun offsetNormalizesOnlyOffsetTagsAndMovesLineAndWordTimesTogether() {
        val source = parser.parseSource("[offset:100]\n[xx:Keep]\n[offset:200]\n[00:01]<00:01>One")
        val changed = editor.offset(source, -100)
        assertEquals(1, changed.lines.count { it.metadata?.key == "offset" })
        assertEquals("[xx:Keep]", changed.lines[0].rawText)
        val cue = (parser.project(changed) as LyricsDocument.Synced).cues.single()
        assertEquals(900L, cue.timestampMs)
        assertEquals(900L, cue.segments.single().timestampMs)
        assertEquals(1000L, cue.sourceTimestampMs)
        assertEquals(source.lines.last().rawText, changed.lines.last().rawText)
    }

    @Test fun timestampsRejectNegativeOverflowAndInvalidSecondsWithoutChangingSource() {
        listOf("-01:00", "00:60.000", "9999999999999999999999:00", "00:01.1234", "one", "").forEach { text ->
            expect(LyricsEditError.INVALID_TIMESTAMP) { LyricsDocumentEditor.parseTime(text) }
        }
        assertEquals(62345L, LyricsDocumentEditor.parseTime("01:02.345"))
        val source = editor.offset(parser.parseSource("First"), 2000)
        expect(LyricsEditError.INVALID_TIMESTAMP) { editor.stamp(source, source.lines.last().id, 1000) }
    }

    @Test fun malformedButRetainableRowsAndUnknownMetadataRemainWarnings() {
        val source = editor.parse("[unrecognized:keep]\n[bad timestamp]\n[00:01]Good\nUntimed".toByteArray())
        editor.validate(source)
        val raw = editor.raw(source, source.originalText + "\nMore")
        assertTrue(raw.originalText.startsWith(source.originalText))
        assertTrue(raw.diagnostics.any { it.code == LyricsDiagnosticCode.UNKNOWN_METADATA })
        assertTrue(raw.diagnostics.any { it.code == LyricsDiagnosticCode.UNSUPPORTED_LINE })
    }

    @Test fun fatalEmptyInvalidEncodingAndOversizedDocumentsHaveSpecificOutcomes() {
        expect(LyricsEditError.EMPTY) { editor.validate(parser.parseSource("[ar:Only metadata]")) }
        expect(LyricsEditError.ENCODING) { editor.parse(byteArrayOf(0xc3.toByte(), 0x28)) }
        expect(LyricsEditError.TOO_LARGE) { editor.parse(ByteArray(LyricsEditingLimits.DOCUMENT_BYTES + 1)) }
        expect(LyricsEditError.INVALID_OFFSET) { editor.validate(parser.parseSource("[offset:no]\nText")) }
        expect(LyricsEditError.IMPORT_FORMAT) { editor.parse("binary\u0000data".toByteArray()) }
    }

    private fun expect(reason: LyricsEditError, block: () -> Unit) {
        try { block(); fail("Expected $reason") } catch (failure: LyricsEditException) { assertEquals(reason, failure.reason) }
    }
}
