package io.github.rsgarrido.sazanami.lyrics

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import org.junit.Assert.*
import org.junit.Test

class LyricsDocumentTransferTest {
    @Test fun readsPlainStandardAndEnhancedByContentAndPreservesBytes() {
        listOf("Plain\r\nText", "[00:01]Line", "[xx:Keep]\n[00:01]<00:01>Word").forEach { text ->
            val bytes = text.toByteArray()
            assertArrayEquals(bytes, LyricsDocumentTransfer.read(ByteArrayInputStream(bytes)))
            val out = ByteArrayOutputStream()
            LyricsDocumentTransfer.write(bytes, out)
            assertArrayEquals(bytes, out.toByteArray())
        }
    }
    @Test fun utf16BomAndUnchangedUnknownMetadataSurviveExport() {
        val bytes = LyricsEncoding.UTF16_LE.bom() + "[xx:keep]\r\nPlain".toByteArray(Charsets.UTF_16LE)
        assertArrayEquals(bytes, LyricsDocumentTransfer.read(ByteArrayInputStream(bytes)))
    }
    @Test fun exportExtensionAndMimeFollowParsedContentRatherThanImportFilename() {
        assertEquals("txt", LyricsExportFormat.forSource(LrcParser().parseSource("Plain original text")).extension)
        val timed = LyricsExportFormat.forSource(LrcParser().parseSource("[00:01]<00:01>Original line"))
        assertEquals("lrc", timed.extension)
        assertEquals("application/octet-stream", timed.mimeType)
    }
    @Test fun rejectsOversizedEmptyAndInvalidEncoding() {
        listOf(ByteArray(LyricsEditingLimits.DOCUMENT_BYTES + 1), byteArrayOf(), byteArrayOf(0xff.toByte())).forEach { bytes ->
            try { LyricsDocumentTransfer.read(ByteArrayInputStream(bytes)); fail("Expected bounded/format rejection") }
            catch (_: LyricsEditException) { /* Typed validation failure, no editor state mutation. */ }
        }
    }
    @Test fun partialWriteAndCloseFailuresPropagateWithoutClaimingSuccess() {
        val output = object : OutputStream() {
            var written = 0
            override fun write(value: Int) { if (++written > 3) throw IOException("Synthetic full provider") }
        }
        try { LyricsDocumentTransfer.write("Synthetic content".toByteArray(), output); fail("Expected provider failure") }
        catch (_: IOException) { }
        val closeFailure = object : ByteArrayOutputStream() { override fun close() { throw IOException("Synthetic close failure") } }
        try { closeFailure.use { LyricsDocumentTransfer.write("Content".toByteArray(), it) }; fail("Expected close failure") }
        catch (_: IOException) { }
    }
}
