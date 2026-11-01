package io.github.rsgarrido.sazanami.lyrics

import java.io.File
import java.util.concurrent.CancellationException
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.id3.ID3v24Frame
import org.jaudiotagger.tag.id3.ID3v24Tag
import org.jaudiotagger.tag.id3.framebody.FrameBodyUSLT
import org.jaudiotagger.tag.mp4.Mp4Tag
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EmbeddedLyricsReaderTest {
    @get:Rule val temporary = TemporaryFolder()
    private val reader = FileEmbeddedLyricsReader()

    /** Generated metadata envelopes exercise actual container readers, not mocked tags.
     * These tiny fixtures are not a claim about audio decoding or playable device compatibility. */
    @Test fun realContainerReadersExtractSyntheticLyricsWithoutChangingAnyBytes() {
        for (extension in listOf("mp3", "flac", "ogg", "m4a", "wav", "aiff")) {
            val file = fixture("synthetic.$extension")
            val before = file.readBytes()
            val result = reader.read(file, "track-$extension")
            assertEquals("$extension: ${result.issues}", 1, result.candidates.size)
            val candidate = result.candidates.single()
            assertTrue(candidate.text.contains("Paper"))
            val source = LrcParser().parseSource(candidate.text)
            val projection = LrcParser().project(source) as LyricsDocument.Synced
            assertEquals(1250L, projection.cues.first().timestampMs)
            assertEquals(2, projection.cues.first().segments.size)
            assertArrayEquals(before, file.readBytes())
        }
    }

    @Test fun realMp3RetainsDistinctFrameLanguagesAndDescriptions() {
        val candidates = reader.read(fixture("multiple.mp3"), "track").candidates
        assertEquals(2, candidates.size)
        assertEquals(setOf("eng", "spa"), candidates.map { it.source.language }.toSet())
        assertEquals(setOf("draft"), candidates.map { it.source.description }.toSet())
        assertEquals(2, candidates.map { it.source.stableId }.distinct().size)
    }

    @Test fun actualPlainUsltRemainsUntimedAndReadOnlyFilesNeedNoWriteAccess() {
        val file = fixture("plain.mp3")
        file.setWritable(false, false)
        try {
            val candidate = reader.read(file, "track").candidates.single()
            assertTrue(LrcParser().parse(candidate.text) is LyricsDocument.Unsynced)
            assertEquals("Plain original line\nAnother quiet line", candidate.text)
        } finally { file.setWritable(true, true) }
    }

    @Test fun actualTagTypesRetainPlainStandardAndEnhancedTextIncludingWhitespace() {
        val texts = listOf("  Plain\ntext  ", "[00:01]Standard", "[00:01]<00:01>Word <00:02>timing")
        for (text in texts) {
            val tags = listOf(ID3v24Tag(), VorbisCommentTag.createNewTag(), Mp4Tag())
            tags.forEach { tag ->
                tag.setField(FieldKey.LYRICS, text)
                assertEquals(text, reader.extract(tag, "track").candidates.single().text)
            }
        }
    }

    @Test fun oversizedCandidateDoesNotHideAnotherUsableFrameAndCountsAreBounded() {
        val tag = ID3v24Tag()
        addFrame(tag, "eng", "large", "x".repeat(LyricsLimits.SOURCE_CHARACTERS + 1))
        addFrame(tag, "eng", "small", "Small original text")
        val result = reader.extract(tag, "track")
        assertEquals("Small original text", result.candidates.single().text)
        assertTrue(result.issues.any { it.kind == LyricsSourceIssue.Kind.INPUT_LIMIT })
        val many = ID3v24Tag()
        repeat(20) { addFrame(many, "eng", "version $it", "Version $it") }
        val truncated = reader.extract(many, "track")
        assertEquals(LyricsLimits.CANDIDATES, truncated.candidates.size)
        assertFalse(truncated.selectionComplete)
    }

    @Test fun sourceIdsAreQualifiedAndIndependentOfLanguageFrameOrder() {
        val a = ID3v24Tag().also { addFrame(it, "eng", "", "A"); addFrame(it, "spa", "", "B") }
        val b = ID3v24Tag().also { addFrame(it, "spa", "", "B"); addFrame(it, "eng", "", "A") }
        assertEquals(reader.extract(a, "track").candidates.map { it.source.stableId }.toSet(),
            reader.extract(b, "track").candidates.map { it.source.stableId }.toSet())
        assertNotEquals(reader.extract(a, "track").candidates.first().source.stableId,
            reader.extract(a, "other-volume").candidates.first().source.stableId)
    }

    @Test fun nativeSyltIsReportedAsUnsupportedRatherThanMisreadAsLrcText() {
        val tag = ID3v24Tag()
        val frame = ID3v24Frame("SYLT")
        frame.body = org.jaudiotagger.tag.id3.framebody.FrameBodySYLT(3, "eng", 2, 1, "Original", byteArrayOf(65, 0, 0, 0, 0, 1))
        tag.addField(frame)
        val result = reader.extract(tag, "track")
        assertTrue(result.candidates.isEmpty())
        assertTrue(result.issues.any { it.kind == LyricsSourceIssue.Kind.UNSUPPORTED_TIMING })
    }

    @Test fun missingUnsupportedMalformedAndOversizedMetadataHaveTypedFailures() {
        assertTrue(reader.extract(ID3v24Tag(), "track").issues.any { it.kind == LyricsSourceIssue.Kind.NOT_FOUND })
        assertTrue(reader.extract(org.jaudiotagger.tag.id3.ID3v1Tag(), "track").issues.any { it.kind == LyricsSourceIssue.Kind.NOT_FOUND })
        assertTrue(reader.read(File("absent.mp3"), "track").issues.any { it.kind == LyricsSourceIssue.Kind.READ_FAILURE })
        assertTrue(reader.read(File("audio.opus"), "track").issues.any { it.kind == LyricsSourceIssue.Kind.UNSUPPORTED_FORMAT })
        val corrupt = temporary.newFile("corrupt.wav").apply { writeText("broken") }
        assertTrue(reader.read(corrupt, "track").issues.any { it.kind == LyricsSourceIssue.Kind.READ_FAILURE })
        val oversized = temporary.newFile("oversized.mp3").apply {
            // Declared ID3 metadata exceeds the preflight budget; no huge allocation is needed.
            writeBytes(byteArrayOf(73, 68, 51, 4, 0, 0, 9, 0, 0, 0))
        }
        assertTrue(reader.read(oversized, "track").issues.any { it.kind == LyricsSourceIssue.Kind.INPUT_LIMIT })
    }

    @Test(expected = CancellationException::class) fun fileReadingPropagatesCancellation() {
        reader.read(fixture("synthetic.mp3"), "track") { throw CancellationException() }
    }

    private fun addFrame(tag: ID3v24Tag, language: String, description: String, text: String) {
        val frame = ID3v24Frame("USLT")
        frame.body = FrameBodyUSLT(3.toByte(), language, description, text)
        tag.addField(frame)
    }

    private fun fixture(name: String): File = temporary.newFile(name).also { target ->
        requireNotNull(javaClass.getResourceAsStream("/lyrics/$name")).use { source ->
            target.outputStream().use { output -> source.copyTo(output) }
        }
    }
}
