package io.github.rsgarrido.sazanami.lyrics

import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsIndexerTest {
    @Test
    fun indexesMultipleRootsNestedFilesAndCaseInsensitiveExtensions() = runBlocking {
        val first = root("content://root/a")
        val second = root("content://root/b")
        val fixture = fixture(first, second)
        fixture.source.results[first.uri] = LyricsTreeScanResult.Success(
            listOf(file(first, "nested/deep", "One.LRC"))
        )
        fixture.source.results[second.uri] = LyricsTreeScanResult.Success(
            listOf(
                file(second, "", "Two.lRc"),
                file(second, "", "cover.jpg")
            )
        )

        val snapshot = fixture.indexer.refresh().snapshot

        assertEquals(listOf("One.LRC", "Two.lRc"), snapshot.files.map { it.displayName })
        assertEquals(setOf(first.uri, second.uri), snapshot.indexedRootUris)
    }

    @Test
    fun duplicateDocumentUriIsRemovedAndOrderingIsDeterministic() = runBlocking {
        val root = root("content://root")
        val fixture = fixture(root)
        val duplicate = file(root, "Z", "Track.lrc", "content://same")
        fixture.source.results[root.uri] = LyricsTreeScanResult.Success(
            listOf(
                duplicate,
                file(root, "A", "First.lrc", "content://first"),
                duplicate.copy(relativeDirectory = "A")
            )
        )

        val snapshot = fixture.indexer.refresh().snapshot

        assertEquals(listOf("content://first", "content://same"), snapshot.files.map { it.documentUri })
    }

    @Test
    fun failedRootDoesNotEraseSuccessfulRootAndLostPermissionIsRepresented() = runBlocking {
        val good = root("content://good")
        val failed = root("content://failed")
        val lost = root("content://lost")
        val fixture = fixture(good, failed, lost)
        fixture.source.results[good.uri] =
            LyricsTreeScanResult.Success(listOf(file(good, "", "Good.lrc")))
        fixture.source.results[failed.uri] = LyricsTreeScanResult.Failed(failed.uri)
        fixture.source.results[lost.uri] = LyricsTreeScanResult.PermissionLost(lost.uri)

        val snapshot = fixture.indexer.refresh().snapshot

        assertEquals(1, snapshot.files.size)
        assertEquals(
            listOf(LyricsRootIssue.Kind.SCAN_FAILED, LyricsRootIssue.Kind.PERMISSION_LOST),
            snapshot.issues.map(LyricsRootIssue::kind)
        )
    }

    @Test
    fun inaccessibleChildCanBeOmittedWithoutLosingAccessibleFiles() = runBlocking {
        val root = root("content://root")
        val fixture = fixture(root)
        fixture.source.results[root.uri] = LyricsTreeScanResult.Success(
            listOf(file(root, "accessible", "Visible.lrc"))
        )

        assertEquals(1, fixture.indexer.refresh().snapshot.files.size)
    }

    @Test
    fun removedRootNoLongerContributesFiles() = runBlocking {
        val first = root("content://first")
        val second = root("content://second")
        val fixture = fixture(first, second)
        fixture.source.results[first.uri] =
            LyricsTreeScanResult.Success(listOf(file(first, "", "First.lrc")))
        fixture.source.results[second.uri] =
            LyricsTreeScanResult.Success(listOf(file(second, "", "Second.lrc")))

        fixture.rootStore.removeRoot(first.uri)
        val snapshot = fixture.indexer.refresh().snapshot

        assertEquals(listOf("Second.lrc"), snapshot.files.map(IndexedLyricsFile::displayName))
    }

    @Test
    fun cancellationPropagates() {
        val root = root("content://root")
        val fixture = fixture(root)
        fixture.source.throwCancellation = true

        assertThrows(CancellationException::class.java) {
            runBlocking { fixture.indexer.refresh() }
        }
    }

    private fun fixture(vararg roots: LyricsRoot): Fixture {
        val rootStore = MutableRootStore(roots.toList())
        val source = FakeTreeDataSource()
        val indexStore = MemoryIndexStore()
        return Fixture(
            rootStore,
            source,
            LyricsIndexer(
                rootStore = rootStore,
                treeDataSource = source,
                indexStore = indexStore,
                ioDispatcher = Dispatchers.Unconfined,
                clock = { 123L }
            )
        )
    }

    private fun root(uri: String) = LyricsRoot(uri, uri.substringAfterLast('/'))

    private fun file(
        root: LyricsRoot,
        directory: String,
        name: String,
        uri: String = "${root.uri}/$directory/$name"
    ) = IndexedLyricsFile(
        documentUri = uri,
        rootUri = root.uri,
        displayName = name,
        normalizedStem = normalizeFileStem(name),
        relativeDirectory = directory
    )

    private data class Fixture(
        val rootStore: MutableRootStore,
        val source: FakeTreeDataSource,
        val indexer: LyricsIndexer
    )
}
