package io.github.rsgarrido.sazanami.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalLyricsRepositoryTest {
    @Test
    fun cachedIndexSummaryReturnsPersistedCountWithoutScanning() = runBlocking {
        val root = root()
        val fixture = fixture(root)
        fixture.indexStore.snapshot = LyricsIndexSnapshot(
            files = (0 until 30).map { index ->
                file("content://file/$index", "Track $index.lrc", "Album")
            },
            indexedRootUris = setOf(root.uri),
            generatedAtEpochMs = 1L
        )

        assertEquals(
            LyricsIndexSummary(30, setOf(root.uri)),
            fixture.repository.loadCachedIndexSummary()
        )
    }

    @Test
    fun cachedIndexSummaryRejectsMissingOrRootStaleSnapshots() = runBlocking {
        val root = root()
        val fixture = fixture(root)

        assertEquals(null, fixture.repository.loadCachedIndexSummary())

        fixture.indexStore.snapshot = LyricsIndexSnapshot(
            files = listOf(file("content://file", "Track.lrc", "Album")),
            indexedRootUris = setOf("content://different-root"),
            generatedAtEpochMs = 1L
        )
        assertEquals(null, fixture.repository.loadCachedIndexSummary())
    }

    @Test
    fun addingAndRemovingRootRefreshesThePersistedIndex() = runBlocking {
        val fixture = fixture()
        val root = root()
        fixture.source.results[root.uri] = LyricsTreeScanResult.Success(
            listOf(file("content://file", "Track.lrc", "Album"))
        )

        fixture.repository.addRoot(root)
        assertEquals(1, fixture.indexStore.snapshot?.files?.size)

        fixture.repository.removeRoot(root.uri)
        assertTrue(fixture.indexStore.snapshot?.files?.isEmpty() == true)
    }

    @Test
    fun noConfiguredRootsIsExplicit() = runBlocking {
        assertEquals(
            LyricsLookupResult.NoRootsConfigured,
            fixture().repository.findLyrics(song())
        )
    }

    @Test
    fun successfulSyncedAndUnsyncedLyricsAreReturnedWithSource() = runBlocking {
        val synced = fixtureWithFile("[00:01.00]Line")
        val unsynced = fixtureWithFile("First\nSecond")

        assertTrue(synced.repository.findLyrics(song()) is LyricsLookupResult.Found)
        val result = unsynced.repository.findLyrics(song()) as LyricsLookupResult.Found
        assertTrue(result.lyrics.document is LyricsDocument.Unsynced)
        assertTrue(result.lyrics.source is LyricsSource.LocalSidecar)
    }

    @Test
    fun ambiguousAndMissingMatchesAreExplicit() = runBlocking {
        val fixture = fixtureWithFiles(
            file("content://one", "Track.lrc", "A"),
            file("content://two", "Track.lrc", "B")
        )

        assertTrue(fixture.repository.findLyrics(song()) is LyricsLookupResult.Ambiguous)
        assertEquals(
            LyricsLookupResult.NotFound,
            fixture.repository.findLyrics(song("Other.flac"))
        )
    }

    @Test
    fun permissionLossAndStreamFailureAreExplicit() = runBlocking {
        val permission = fixtureWithFile("[00:01.00]Line")
        permission.reader.results["content://file"] = LyricsDocumentReadResult.PermissionLost
        val failed = fixtureWithFile("[00:01.00]Line")
        failed.reader.results["content://file"] = LyricsDocumentReadResult.Failed

        assertTrue(permission.repository.findLyrics(song()) is LyricsLookupResult.PermissionLost)
        assertTrue(failed.repository.findLyrics(song()) is LyricsLookupResult.ReadError)
    }

    @Test
    fun lostRootPermissionIsReturnedWhenNoCandidateExists() = runBlocking {
        val root = root()
        val fixture = fixture(root)
        fixture.source.results[root.uri] = LyricsTreeScanResult.PermissionLost(root.uri)

        assertTrue(fixture.repository.findLyrics(song()) is LyricsLookupResult.PermissionLost)
    }

    @Test
    fun parserWithNoUsableLyricsIsInvalid() = runBlocking {
        val fixture = fixtureWithFile("[ar:Artist]\n\n")

        assertTrue(fixture.repository.findLyrics(song()) is LyricsLookupResult.InvalidLyrics)
    }

    @Test
    fun disappearedFileRefreshesAndReturnsStaleWhenNotRecovered() = runBlocking {
        val fixture = fixtureWithFile("[00:01.00]Line")
        fixture.reader.results["content://file"] = LyricsDocumentReadResult.Missing
        fixture.source.results[root().uri] = LyricsTreeScanResult.Success(emptyList())

        assertEquals(
            LyricsLookupResult.StaleFile("content://file"),
            fixture.repository.findLyrics(song())
        )
    }

    @Test
    fun staleIndexRefreshCanFindReplacementAndRetry() = runBlocking {
        val fixture = fixtureWithFile("[00:01.00]Old")
        fixture.reader.results["content://file"] = LyricsDocumentReadResult.Missing
        val replacement = file("content://replacement", "Track.lrc", "Album")
        fixture.source.results[root().uri] = LyricsTreeScanResult.Success(listOf(replacement))
        fixture.reader.results[replacement.documentUri] =
            LyricsDocumentReadResult.Success("[00:02.00]New".toByteArray())

        val result = fixture.repository.findLyrics(song()) as LyricsLookupResult.Found
        val source = result.lyrics.source as LyricsSource.LocalSidecar

        assertEquals("content://replacement", source.documentUri)
    }

    @Test
    fun existingUtf16ParserSupportWorksThroughRepository() = runBlocking {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
            "[00:01.00]日本語".toByteArray(Charsets.UTF_16LE)
        val fixture = fixtureWithFileBytes(bytes)

        assertTrue(fixture.repository.findLyrics(song()) is LyricsLookupResult.Found)
    }

    private fun fixtureWithFile(text: String) = fixtureWithFileBytes(text.toByteArray())

    private fun fixtureWithFileBytes(bytes: ByteArray): Fixture {
        val fixture = fixtureWithFiles(file("content://file", "Track.lrc", "Album"))
        fixture.reader.results["content://file"] = LyricsDocumentReadResult.Success(bytes)
        return fixture
    }

    private fun fixtureWithFiles(vararg files: IndexedLyricsFile): Fixture {
        val root = root()
        val fixture = fixture(root)
        fixture.source.results[root.uri] = LyricsTreeScanResult.Success(files.toList())
        fixture.indexStore.snapshot = LyricsIndexSnapshot(
            files = files.toList(),
            indexedRootUris = setOf(root.uri),
            generatedAtEpochMs = 1L
        )
        return fixture
    }

    private fun fixture(vararg roots: LyricsRoot): Fixture {
        val rootStore = MutableRootStore(roots.toList())
        val source = FakeTreeDataSource()
        val indexStore = MemoryIndexStore()
        val reader = FakeDocumentReader()
        val indexer = LyricsIndexer(
            rootStore,
            source,
            indexStore,
            Dispatchers.Unconfined
        )
        val repository = DefaultLocalLyricsRepository(
            rootStore,
            indexStore,
            indexer,
            reader,
            LrcParser(),
            Dispatchers.Unconfined
        )
        return Fixture(repository, source, indexStore, reader)
    }

    private fun root() = LyricsRoot("content://root", "Root")

    private fun song(name: String = "Track.flac") = SongLyricsIdentity(
        audioFileName = name,
        relativeDirectory = "Music/Album",
        fallbackDirectory = "",
        volumeId = null
    )

    private fun file(uri: String, name: String, directory: String) = IndexedLyricsFile(
        documentUri = uri,
        rootUri = root().uri,
        displayName = name,
        normalizedStem = normalizeFileStem(name),
        relativeDirectory = directory
    )

    private data class Fixture(
        val repository: DefaultLocalLyricsRepository,
        val source: FakeTreeDataSource,
        val indexStore: MemoryIndexStore,
        val reader: FakeDocumentReader
    )
}
