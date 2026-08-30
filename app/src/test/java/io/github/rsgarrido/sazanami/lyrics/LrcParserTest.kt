package io.github.rsgarrido.sazanami.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {
    private val parser = LrcParser()

    @Test
    fun parsesSupportedTimestampPrecisionsAndMinuteRanges() {
        val document = parser.parse(
            """
            [00:12]Whole
            [00:13.4]Tenths
            [00:14.56]Hundredths
            [00:15.789]Milliseconds
            [1:02.50]Single digit minute
            [72:03.001]Long duration
            """.trimIndent()
        ).synced()

        assertEquals(
            listOf(12_000L, 13_400L, 14_560L, 15_789L, 62_500L, 4_323_001L),
            document.cues.map(LyricCue::timestampMs)
        )
    }

    @Test
    fun expandsMultipleTimestampsAndKeepsDuplicateOrderStable() {
        val document = parser.parse(
            """
            [00:12.00][01:45.00]Repeated chorus
            [00:12.00]Second at duplicate
            """.trimIndent()
        ).synced()

        assertEquals(listOf(12_000L, 12_000L, 105_000L), document.cues.map { it.timestampMs })
        assertEquals(
            listOf("Repeated chorus", "Second at duplicate", "Repeated chorus"),
            document.cues.map { (it.content as LyricCueContent.Text).text }
        )
    }

    @Test
    fun ignoresMalformedTimestampsWithoutDiscardingValidLyrics() {
        val document = parser.parse(
            """
            [00:61.00]Invalid seconds
            [00:12.1234]Invalid precision
            [nope]Invalid tag
            [00:20.00]Valid
            """.trimIndent()
        ).synced()

        assertEquals(1, document.cues.size)
        assertEquals(20_000L, document.cues.single().timestampMs)
    }

    @Test
    fun retainsMetadataMixedWithLyrics() {
        val document = parser.parse(
            """
            [ar:Artist]
            [00:01.00]First
            [al:Album]
            [ti:Title]
            [by:Creator]
            [re:Editor]
            [ve:Version]
            [00:02.00]Second
            """.trimIndent()
        ).synced()

        assertEquals(
            LyricsMetadata(
                artist = "Artist",
                album = "Album",
                title = "Title",
                creator = "Creator",
                editor = "Editor",
                version = "Version"
            ),
            document.metadata
        )
        assertEquals(2, document.cues.size)
    }

    @Test
    fun appliesPositiveAndNegativeOffsets() {
        val positive = parser.parse("[offset:+250]\n[00:01.000]Line").synced()
        val negative = parser.parse("[offset:-150]\n[00:01.000]Line").synced()

        assertEquals(1_250L, positive.cues.single().timestampMs)
        assertEquals(850L, negative.cues.single().timestampMs)
        assertEquals(250L, positive.metadata.offsetMs)
        assertEquals(-150L, negative.metadata.offsetMs)
    }

    @Test
    fun clampsNegativeAdjustedTimestampToZero() {
        val document = parser.parse("[offset:-1500]\n[00:01.000]Line").synced()

        assertEquals(0L, document.cues.single().timestampMs)
    }

    @Test
    fun decodesUtf8BomAndCrLf() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            "[00:01.00]First\r\n[00:02.00]Second".toByteArray(Charsets.UTF_8)

        val document = parser.parse(bytes).synced()

        assertEquals(listOf("First", "Second"), document.visibleTexts())
    }

    @Test
    fun decodesUtf16LittleEndianBom() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
            "[00:01.00]日本語".toByteArray(Charsets.UTF_16LE)

        assertEquals(listOf("日本語"), parser.parse(bytes).synced().visibleTexts())
    }

    @Test
    fun decodesUtf16BigEndianBom() {
        val bytes = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) +
            "[00:01.00]日本語".toByteArray(Charsets.UTF_16BE)

        assertEquals(listOf("日本語"), parser.parse(bytes).synced().visibleTexts())
    }

    @Test
    fun preservesBlankTimedLineAsInstrumentalCue() {
        val document = parser.parse("[00:01.00]\n[00:02.00]Visible").synced()

        assertEquals(LyricCueContent.Instrumental, document.cues.first().content)
    }

    @Test
    fun representsPlainTextAsUnsynchronizedLyrics() {
        val document = parser.parse("First line\n日本語の歌詞\n\nLast line").unsynced()

        assertEquals(
            listOf("First line", "日本語の歌詞", "", "Last line"),
            document.lines.map(StaticLyricLine::text)
        )
    }

    @Test
    fun stripsEnhancedInlineWordTimestamps() {
        val document = parser.parse(
            "[00:12.00]<00:12.10>Hello <00:12.50>world"
        ).synced()

        assertEquals(listOf("Hello world"), document.visibleTexts())
    }

    @Test
    fun ordinaryMalformedInputDoesNotThrow() {
        val document = parser.parse(
            byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte(), 0xFF.toByte())
        )

        assertTrue(document is LyricsDocument.Unsynced)
    }

    private fun LyricsDocument.synced() = this as LyricsDocument.Synced

    private fun LyricsDocument.unsynced() = this as LyricsDocument.Unsynced

    private fun LyricsDocument.Synced.visibleTexts() = cues.mapNotNull {
        (it.content as? LyricCueContent.Text)?.text
    }
}
