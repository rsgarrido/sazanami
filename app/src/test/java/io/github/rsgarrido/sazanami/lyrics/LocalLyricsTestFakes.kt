package io.github.rsgarrido.sazanami.lyrics

import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow

internal class MutableRootStore(initial: List<LyricsRoot>) : LyricsRootStore {
    override val roots = MutableStateFlow(initial)

    override suspend fun addRoot(root: LyricsRoot) {
        if (roots.value.none { it.uri == root.uri }) {
            roots.value = (roots.value + root).sortedBy(LyricsRoot::uri)
        }
    }

    override suspend fun removeRoot(rootUri: String) {
        roots.value = roots.value.filterNot { it.uri == rootUri }
    }
}

internal class FakeTreeDataSource : LyricsTreeDataSource {
    val results = mutableMapOf<String, LyricsTreeScanResult>()
    var throwCancellation = false

    override suspend fun scanRoot(root: LyricsRoot): LyricsTreeScanResult {
        if (throwCancellation) throw CancellationException("cancelled")
        return results[root.uri] ?: LyricsTreeScanResult.Success(emptyList())
    }
}

internal class MemoryIndexStore : LyricsIndexStore {
    var snapshot: LyricsIndexSnapshot? = null

    override suspend fun load(): LyricsIndexSnapshot? = snapshot
    override suspend fun save(snapshot: LyricsIndexSnapshot) {
        this.snapshot = snapshot
    }
    override suspend fun clear() {
        snapshot = null
    }
}

internal class FakeDocumentReader : LyricsDocumentReader {
    val results = mutableMapOf<String, LyricsDocumentReadResult>()

    override suspend fun read(documentUri: String): LyricsDocumentReadResult =
        results[documentUri] ?: LyricsDocumentReadResult.Missing
}
