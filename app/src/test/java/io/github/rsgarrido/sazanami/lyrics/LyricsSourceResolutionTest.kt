package io.github.rsgarrido.sazanami.lyrics

import io.github.rsgarrido.sazanami.data.SongReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LyricsSourceResolutionTest {
    @Test fun localPlainOverrideWinsOverAllTimedSourcesAndRemovalRestoresDiscovery() = runBlocking {
        val fixture = fixture("[00:01]External", candidates = listOf(candidate("[00:01]Embedded")))
        val source = LrcParser().parseSource("Intentional local plain lyrics")
        fixture.local = LyricsLookupResult.Found(SourcedLyrics(LrcParser().project(source), LyricsSource.LocalOverride("saved"), source, "revision"))
        val found = fixture.repository.findLyrics(song()) as LyricsLookupResult.Found
        assertEquals(LyricsSource.LocalOverride("saved"), found.lyrics.source)
        assertEquals(LyricsTiming.PLAIN, found.lyrics.timing)
        assertEquals(0, fixture.embeddedReads)
        fixture.local = null
        fixture.repository.invalidate(LyricsInvalidation(song().trackKey))
        val restored = fixture.repository.findLyrics(song()) as LyricsLookupResult.Found
        assertTrue(restored.lyrics.source is LyricsSource.LocalSidecar)
        assertEquals(LyricsTiming.LINE, restored.lyrics.timing)
    }
    @Test fun approvedPrecedenceUsesSynchronizationBeforePlainTextAndKeepsExternalTimedFirst() = runBlocking {
        val cases = listOf(
            Triple("[00:01]External", "[00:01]<00:01>Embedded", "external"),
            Triple("External plain", "[00:01]Embedded", "embedded"),
            Triple("External plain", "Embedded plain", "external")
        )
        cases.forEach { (external, embedded, expected) ->
            val fixture = fixture(external, candidates = listOf(candidate(embedded)))
            val found = fixture.repository.findLyrics(song()) as LyricsLookupResult.Found
            assertEquals(expected == "external", found.lyrics.source is LyricsSource.LocalSidecar)
            assertNotNull(found.lyrics.sourceDocument)
            if (external.startsWith("[")) assertEquals(0, fixture.embeddedReads)
        }
    }

    @Test fun embeddedPlainLineAndWordTimingWorkWithoutConfiguredRoots() = runBlocking {
        listOf("Plain text" to LyricsTiming.PLAIN, "[00:01]Line" to LyricsTiming.LINE,
            "[00:01]<00:01>Word" to LyricsTiming.WORD).forEach { (text, timing) ->
            val found = fixture(candidates = listOf(candidate(text))).repository.findLyrics(song()) as LyricsLookupResult.Found
            assertEquals(timing, found.lyrics.timing)
            assertTrue(found.lyrics.source is LyricsSource.Embedded)
            assertTrue(found.lyrics.issues.any { it.kind == LyricsSourceIssue.Kind.NO_ROOTS })
            assertEquals(text, found.lyrics.sourceDocument?.originalText)
        }
    }

    @Test fun invalidAndUnreadableSidecarsDoNotMaskEmbeddedLyrics() = runBlocking {
        for (external in listOf("[offset:0]\n[broken]", "External")) {
            val fixture = fixture(external, candidates = listOf(candidate("[00:01]Embedded")))
            if (external == "External") fixture.documents.results["content://sidecar"] = LyricsDocumentReadResult.PermissionLost
            val found = fixture.repository.findLyrics(song()) as LyricsLookupResult.Found
            assertTrue(found.lyrics.source is LyricsSource.Embedded)
            assertTrue(found.lyrics.issues.any { it.kind in setOf(LyricsSourceIssue.Kind.INVALID_LYRICS, LyricsSourceIssue.Kind.PERMISSION_DENIED) })
        }
    }

    @Test fun missingOrAmbiguousExternalCandidatesRemainInspectableDuringFallback() = runBlocking {
        val missing = fixture(candidates = listOf(candidate("Embedded")), roots = true)
        val result = missing.repository.findLyrics(song()) as LyricsLookupResult.Found
        assertTrue(result.lyrics.issues.any { it.kind == LyricsSourceIssue.Kind.NOT_FOUND })
        val ambiguous = fixture("External", candidates = listOf(candidate("Embedded")))
        ambiguous.index.snapshot = ambiguous.index.snapshot!!.copy(files = listOf(
            file("content://one", "A"), file("content://two", "B")))
        val found = ambiguous.repository.findLyrics(song()) as LyricsLookupResult.Found
        val issue = found.lyrics.issues.single { it.kind == LyricsSourceIssue.Kind.AMBIGUOUS }
        assertEquals(setOf("content://one", "content://two"), issue.externalCandidates.map { it.documentUri }.toSet())
    }

    @Test fun languageOrderDoesNotSelectAnArbitraryVersionAndNeutralDefaultIsExplicit() = runBlocking {
        val english = candidate("English original", language = "eng")
        val spanish = candidate("Texto original", language = "spa")
        for (candidates in listOf(listOf(english, spanish), listOf(spanish, english))) {
            val result = fixture(candidates = candidates).repository.findLyrics(song()) as LyricsLookupResult.Unavailable
            val issue = result.issues.single { it.kind == LyricsSourceIssue.Kind.AMBIGUOUS }
            assertEquals(setOf(english.source, spanish.source), issue.candidates.toSet())
        }
        val neutral = candidate("Neutral text", language = "und")
        val selected = fixture(candidates = listOf(english, neutral, spanish)).repository.findLyrics(song()) as LyricsLookupResult.Found
        assertEquals(neutral.source, selected.lyrics.source)
        assertEquals(2, selected.lyrics.alternatives.size)
    }

    @Test fun failedOrMalformedEmbeddedMetadataDoesNotMaskExternalPlainAndRetainsIssues() = runBlocking {
        val fixture = fixture("External plain", embeddedIssues = listOf(LyricsSourceIssue("embedded", LyricsSourceIssue.Kind.METADATA_FAILURE)))
        val found = fixture.repository.findLyrics(song()) as LyricsLookupResult.Found
        assertTrue(found.lyrics.source is LyricsSource.LocalSidecar)
        assertTrue(found.lyrics.issues.any { it.kind == LyricsSourceIssue.Kind.METADATA_FAILURE })
        val malformed = fixture(candidates = listOf(candidate("[broken]"), candidate("Readable", description = "valid")))
        val fallback = malformed.repository.findLyrics(song()) as LyricsLookupResult.Found
        assertTrue(fallback.lyrics.issues.any { it.kind == LyricsSourceIssue.Kind.INVALID_LYRICS })
    }

    @Test fun truncationCannotSelectTheFirstLanguageByAccident() = runBlocking {
        val fixture = fixture("External plain", candidates = listOf(candidate("[00:01]Timed")), selectionComplete = false)
        val found = fixture.repository.findLyrics(song()) as LyricsLookupResult.Found
        assertTrue(found.lyrics.source is LyricsSource.LocalSidecar)
        assertTrue(found.lyrics.issues.any { it.kind == LyricsSourceIssue.Kind.AMBIGUOUS })
    }

    @Test fun cacheReusesSourceDocumentsButQualifiedRevisionAndTargetedInvalidationReload() = runBlocking {
        val fixture = fixture(candidates = listOf(candidate("Embedded")))
        val a = song()
        val b = song().copy(trackKey = "sd-card", volumeId = "sd-card")
        val first = fixture.repository.findLyrics(a) as LyricsLookupResult.Found
        val second = fixture.repository.findLyrics(a) as LyricsLookupResult.Found
        assertSame(first.lyrics.sourceDocument, second.lyrics.sourceDocument)
        fixture.repository.findLyrics(b)
        assertEquals(2, fixture.embeddedReads)
        fixture.repository.invalidate(LyricsInvalidation(trackKey = a.trackKey))
        fixture.repository.findLyrics(b)
        assertEquals(2, fixture.embeddedReads) // Other tracks retain their entries.
        fixture.repository.findLyrics(a)
        assertEquals(3, fixture.embeddedReads)
        fixture.repository.findLyrics(a.copy(reference = SongReference(dateModifiedEpochSeconds = 9)))
        assertEquals(4, fixture.embeddedReads)
        fixture.repository.refreshIndex()
        fixture.repository.findLyrics(b)
        assertEquals(5, fixture.embeddedReads)
    }

    @Test fun positiveAndNegativeCacheEntriesExpireAndLruStaysBounded() = runBlocking {
        val fixture = fixture(candidates = listOf(candidate("Embedded")))
        fixture.repository.findLyrics(song())
        fixture.now = 30_001
        fixture.repository.findLyrics(song())
        assertEquals(2, fixture.embeddedReads)
        repeat(9) { fixture.repository.findLyrics(song().copy(trackKey = "track-$it")) }
        fixture.repository.findLyrics(song())
        assertEquals(12, fixture.embeddedReads)
        val missing = fixture(embeddedIssues = listOf(LyricsSourceIssue("embedded", LyricsSourceIssue.Kind.NOT_FOUND)))
        missing.repository.findLyrics(song())
        missing.repository.findLyrics(song())
        assertEquals(1, missing.embeddedReads)
        missing.now = 5_001
        missing.repository.findLyrics(song())
        assertEquals(2, missing.embeddedReads)
    }

    @Test fun unexpectedIndexFailureIsTypedAndStillAllowsEmbeddedFallback() = runBlocking {
        val roots = MutableRootStore(listOf(LyricsRoot("content://root", "Root")))
        val index = object : LyricsIndexStore {
            override suspend fun load(): LyricsIndexSnapshot? = throw IllegalStateException("broken index")
            override suspend fun save(snapshot: LyricsIndexSnapshot) = Unit
            override suspend fun clear() = Unit
        }
        val repository = DefaultLocalLyricsRepository(roots, index,
            LyricsIndexer(roots, FakeTreeDataSource(), index, Dispatchers.Unconfined), FakeDocumentReader(),
            LrcParser(), Dispatchers.Unconfined, EmbeddedLyricsReader { EmbeddedLyricsReadResult(listOf(candidate("Embedded"))) })
        val found = repository.findLyrics(song()) as LyricsLookupResult.Found
        assertTrue(found.lyrics.issues.any { it.kind == LyricsSourceIssue.Kind.LOOKUP_FAILURE })
    }

    @Test fun largeDocumentsCannotAccumulatePastTheCacheMemoryBudget() = runBlocking {
        val text = ("x".repeat(2000) + "\n").repeat(750)
        val fixture = fixture(candidates = listOf(candidate(text)))
        val a = song()
        val b = song().copy(trackKey = "other-track")
        fixture.repository.findLyrics(a)
        fixture.repository.findLyrics(b)
        fixture.repository.findLyrics(a)
        assertEquals(3, fixture.embeddedReads)
    }

    @Test(expected = CancellationException::class) fun embeddedCancellationIsNotConvertedToNotFound() = runBlocking {
        val fixture = fixture()
        fixture.embeddedAction = { throw CancellationException() }
        fixture.repository.findLyrics(song())
        Unit
    }

    private class Fixture {
        val root = LyricsRoot("content://root", "Root")
        val roots = MutableRootStore(emptyList())
        val index = MemoryIndexStore()
        val tree = FakeTreeDataSource()
        val documents = FakeDocumentReader()
        var now = 0L
        var embeddedReads = 0
        var local: LyricsLookupResult? = null
        var embeddedAction: suspend () -> EmbeddedLyricsReadResult = { EmbeddedLyricsReadResult() }
        val repository = DefaultLocalLyricsRepository(roots, index,
            LyricsIndexer(roots, tree, index, Dispatchers.Unconfined), documents, LrcParser(), Dispatchers.Unconfined,
            EmbeddedLyricsReader { embeddedReads++; embeddedAction() }, { now }, localOverrides = LyricsOverrideReader { local })
    }

    private fun fixture(external: String? = null, candidates: List<EmbeddedLyricsCandidate> = emptyList(),
        embeddedIssues: List<LyricsSourceIssue> = emptyList(), roots: Boolean = external != null,
        selectionComplete: Boolean = true): Fixture = Fixture().apply {
        if (roots) this.roots.roots.value = listOf(root)
        index.snapshot = LyricsIndexSnapshot(if (external == null) emptyList() else listOf(file("content://sidecar", "")),
            if (roots) setOf(root.uri) else emptySet(), generatedAtEpochMs = 1)
        tree.results[root.uri] = LyricsTreeScanResult.Success(index.snapshot!!.files)
        if (external != null) documents.results["content://sidecar"] = LyricsDocumentReadResult.Success(external.toByteArray())
        embeddedAction = { EmbeddedLyricsReadResult(candidates, embeddedIssues, selectionComplete) }
    }

    private fun song() = SongLyricsIdentity("Track.flac", relativeDirectory = "", fallbackDirectory = "", volumeId = "primary", trackKey = "primary-track")
    private fun file(uri: String, directory: String) = IndexedLyricsFile(uri, "content://root", "Track.lrc", "track", directory)
    private fun candidate(text: String, language: String? = null, description: String? = null) =
        EmbeddedLyricsCandidate(LyricsSource.Embedded("primary-track", "LYRICS", language, description), text)
}
