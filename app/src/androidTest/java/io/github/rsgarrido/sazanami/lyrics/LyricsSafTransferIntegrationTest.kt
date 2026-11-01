package io.github.rsgarrido.sazanami.lyrics

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LyricsSafTransferIntegrationTest {
    private val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
    private val parser = LrcParser()
    @Test fun realCrossProcessStreamsClassifyContentIndependentlyOfExtension() = runBlocking {
        for ((name, timing) in listOf("plain.lrc" to LyricsTiming.PLAIN, "timed.txt" to LyricsTiming.LINE, "enhanced.txt" to LyricsTiming.WORD)) {
            val bytes = LyricsDocumentTransfer.importDocument(resolver, LyricsTransferFixtureProvider.uri(name))
            val source = parser.parseSource(bytes)
            assertEquals(timing, SourcedLyrics(parser.project(source), LyricsSource.LocalOverride("fixture")).timing)
        }
    }
    @Test fun realCreateDestinationWritesRetainReviewedBytesAndReportAdjustedName() = runBlocking {
        for ((name, destination) in listOf("plain.lrc" to "export.txt", "enhanced.txt" to "export.lrc")) {
            val bytes = LyricsDocumentTransfer.importDocument(resolver, LyricsTransferFixtureProvider.uri(name))
            val uri = LyricsTransferFixtureProvider.uri(destination)
            val actualName = LyricsDocumentTransfer.exportDocument(resolver, uri, bytes)
            assertEquals("provider-$destination", actualName)
            assertArrayEquals(bytes, LyricsDocumentTransfer.importDocument(resolver, uri))
        }
    }
    @Test fun cancellationPermissionMissingSizeAndEncodingHaveSpecificResults() = runBlocking {
        expect(LyricsEditError.IMPORT_CANCELLED) { LyricsDocumentTransfer.importDocument(resolver, null) }
        expect(LyricsEditError.IMPORT_READ) { LyricsDocumentTransfer.importDocument(resolver, LyricsTransferFixtureProvider.uri("denied")) }
        expect(LyricsEditError.IMPORT_READ) { LyricsDocumentTransfer.importDocument(resolver, LyricsTransferFixtureProvider.uri("missing")) }
        expect(LyricsEditError.TOO_LARGE) { LyricsDocumentTransfer.importDocument(resolver, LyricsTransferFixtureProvider.uri("oversized")) }
        expect(LyricsEditError.ENCODING) { LyricsDocumentTransfer.importDocument(resolver, LyricsTransferFixtureProvider.uri("invalid")) }
        expect(LyricsEditError.EMPTY) { LyricsDocumentTransfer.importDocument(resolver, LyricsTransferFixtureProvider.uri("empty")) }
    }
    @Test fun failedRealProviderWritesNeverReportSuccessfulExport() = runBlocking {
        val bytes = "Original export content".toByteArray()
        expect(LyricsEditError.EXPORT_CANCELLED) { LyricsDocumentTransfer.exportDocument(resolver, null, bytes) }
        expect(LyricsEditError.EXPORT_WRITE) { LyricsDocumentTransfer.exportDocument(resolver, LyricsTransferFixtureProvider.uri("denied"), bytes) }
        expect(LyricsEditError.EXPORT_WRITE) { LyricsDocumentTransfer.exportDocument(resolver, LyricsTransferFixtureProvider.uri("broken-output"), bytes) }
        assertEquals("Original export content", bytes.toString(Charsets.UTF_8))
    }
    private suspend fun expect(reason: LyricsEditError, action: suspend () -> Any?) {
        try { action(); fail("Expected $reason") }
        catch (failure: LyricsEditException) { assertEquals(reason, failure.reason) }
    }
}
