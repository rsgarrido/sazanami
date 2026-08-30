package io.github.rsgarrido.sazanami.lyrics

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class LyricsIndexer(
    private val rootStore: LyricsRootStore,
    private val treeDataSource: LyricsTreeDataSource,
    private val indexStore: LyricsIndexStore,
    private val ioDispatcher: CoroutineDispatcher,
    private val clock: () -> Long = System::currentTimeMillis
) {
    suspend fun refresh(): LyricsIndexResult = withContext(ioDispatcher) {
        val roots = rootStore.roots.value.sortedBy(LyricsRoot::uri)
        val files = mutableListOf<IndexedLyricsFile>()
        val issues = mutableListOf<LyricsRootIssue>()
        val successfulRoots = mutableSetOf<String>()

        roots.forEach { root ->
            coroutineContext.ensureActive()
            val scanResult = try {
                treeDataSource.scanRoot(root)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                LyricsTreeScanResult.Failed(root.uri)
            }
            when (scanResult) {
                is LyricsTreeScanResult.Success -> {
                    successfulRoots += root.uri
                    files += scanResult.files.filter { file ->
                        hasLrcExtension(file.displayName)
                    }
                }
                is LyricsTreeScanResult.PermissionLost -> issues += LyricsRootIssue(
                    rootUri = scanResult.rootUri,
                    kind = LyricsRootIssue.Kind.PERMISSION_LOST
                )
                is LyricsTreeScanResult.Failed -> issues += LyricsRootIssue(
                    rootUri = scanResult.rootUri,
                    kind = LyricsRootIssue.Kind.SCAN_FAILED
                )
            }
        }

        val snapshot = LyricsIndexSnapshot(
            files = files
                .distinctBy(IndexedLyricsFile::documentUri)
                .sortedWith(indexedLyricsFileComparator),
            indexedRootUris = successfulRoots,
            issues = issues.sortedBy(LyricsRootIssue::rootUri),
            generatedAtEpochMs = clock()
        )
        indexStore.save(snapshot)
        LyricsIndexResult(snapshot)
    }
}
