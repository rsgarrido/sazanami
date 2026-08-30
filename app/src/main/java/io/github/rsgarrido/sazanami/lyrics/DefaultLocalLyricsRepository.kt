package io.github.rsgarrido.sazanami.lyrics

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class DefaultLocalLyricsRepository(
    private val rootStore: LyricsRootStore,
    private val indexStore: LyricsIndexStore,
    private val indexer: LyricsIndexer,
    private val documentReader: LyricsDocumentReader,
    private val parser: LrcParser,
    private val ioDispatcher: CoroutineDispatcher
) : LocalLyricsRepository {
    override val roots = rootStore.roots
    private val refreshMutex = Mutex()

    override suspend fun loadCachedIndexSummary(): LyricsIndexSummary? =
        withContext(ioDispatcher) {
            val snapshot = indexStore.load() ?: return@withContext null
            val configuredUris = roots.value.mapTo(linkedSetOf(), LyricsRoot::uri)
            val cachedUris = snapshot.indexedRootUris +
                snapshot.issues.map(LyricsRootIssue::rootUri)
            if (cachedUris != configuredUris) return@withContext null
            LyricsIndexSummary(
                fileCount = snapshot.files.size,
                indexedRootUris = snapshot.indexedRootUris
            )
        }

    override suspend fun addRoot(root: LyricsRoot): LyricsIndexResult = withContext(ioDispatcher) {
        rootStore.addRoot(root)
        refreshIndex()
    }

    override suspend fun removeRoot(rootUri: String): LyricsIndexResult =
        withContext(ioDispatcher) {
            rootStore.removeRoot(rootUri)
            refreshIndex()
        }

    override suspend fun refreshIndex(): LyricsIndexResult =
        refreshMutex.withLock { indexer.refresh() }

    override suspend fun findLyrics(song: SongLyricsIdentity): LyricsLookupResult =
        withContext(ioDispatcher) {
            findLyrics(song, staleDocumentUri = null, mayRefreshStale = true)
        }

    private suspend fun findLyrics(
        song: SongLyricsIdentity,
        staleDocumentUri: String?,
        mayRefreshStale: Boolean
    ): LyricsLookupResult {
        val configuredRoots = roots.value
        if (configuredRoots.isEmpty()) return LyricsLookupResult.NoRootsConfigured

        val configuredUris = configuredRoots.mapTo(linkedSetOf(), LyricsRoot::uri)
        var snapshot = indexStore.load()
        if (
            snapshot == null ||
            snapshot.indexedRootUris + snapshot.issues.map(LyricsRootIssue::rootUri) !=
            configuredUris
        ) {
            snapshot = refreshIndex().snapshot
        }

        return when (val match = LocalLyricsMatcher.match(song, snapshot.files)) {
            is LyricsMatchResult.Match -> readMatch(
                song = song,
                file = match.file,
                mayRefreshStale = mayRefreshStale
            )
            is LyricsMatchResult.Ambiguous -> LyricsLookupResult.Ambiguous(
                match.candidates.map(IndexedLyricsFile::toCandidate)
            )
            LyricsMatchResult.NotFound -> {
                val issue = snapshot.issues.firstOrNull()
                when (issue?.kind) {
                    LyricsRootIssue.Kind.PERMISSION_LOST ->
                        LyricsLookupResult.PermissionLost(issue.rootUri)
                    LyricsRootIssue.Kind.SCAN_FAILED ->
                        LyricsLookupResult.RootScanError(issue.rootUri)
                    null -> staleDocumentUri?.let(LyricsLookupResult::StaleFile)
                        ?: LyricsLookupResult.NotFound
                }
            }
        }
    }

    private suspend fun readMatch(
        song: SongLyricsIdentity,
        file: IndexedLyricsFile,
        mayRefreshStale: Boolean
    ): LyricsLookupResult {
        return when (val readResult = documentReader.read(file.documentUri)) {
            is LyricsDocumentReadResult.Success -> {
                val document = parser.parse(readResult.bytes)
                if (!document.hasUsableLyrics()) {
                    LyricsLookupResult.InvalidLyrics(file.documentUri)
                } else {
                    LyricsLookupResult.Found(
                        SourcedLyrics(
                            document = document,
                            source = LyricsSource.LocalSidecar(
                                documentUri = file.documentUri,
                                displayName = file.displayName
                            )
                        )
                    )
                }
            }
            LyricsDocumentReadResult.Missing -> {
                if (mayRefreshStale) {
                    refreshIndex()
                    findLyrics(
                        song = song,
                        staleDocumentUri = file.documentUri,
                        mayRefreshStale = false
                    )
                } else {
                    LyricsLookupResult.StaleFile(file.documentUri)
                }
            }
            LyricsDocumentReadResult.PermissionLost ->
                LyricsLookupResult.PermissionLost(file.rootUri)
            LyricsDocumentReadResult.Failed ->
                LyricsLookupResult.ReadError(file.documentUri)
        }
    }

    private fun LyricsDocument.hasUsableLyrics(): Boolean = when (this) {
        is LyricsDocument.Synced -> cues.any { it.content is LyricCueContent.Text }
        is LyricsDocument.Unsynced -> lines.any { it.text.isNotBlank() }
    }
}
