package io.github.rsgarrido.sazanami.lyrics

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

class DefaultLocalLyricsRepository(
    private val rootStore: LyricsRootStore,
    private val indexStore: LyricsIndexStore,
    private val indexer: LyricsIndexer,
    private val documentReader: LyricsDocumentReader,
    private val parser: LrcParser,
    private val ioDispatcher: CoroutineDispatcher,
    private val embeddedReader: EmbeddedLyricsReader = EmbeddedLyricsReader { EmbeddedLyricsReadResult() },
    cacheClockMs: () -> Long = { System.nanoTime() / 1_000_000L },
    private val localOverrides: LyricsOverrideReader = LyricsOverrideReader { null }
) : LocalLyricsRepository {
    override val roots = rootStore.roots
    private val refreshMutex = Mutex()
    private val lookupMutex = Mutex()
    private val revision = AtomicLong()
    private val cache = LyricsResultCache(cacheClockMs)
    private val _invalidations = MutableSharedFlow<LyricsInvalidation>(extraBufferCapacity = 16)
    override val invalidations = _invalidations.asSharedFlow()

    override suspend fun invalidate(change: LyricsInvalidation) {
        cache.invalidate(change)
        revision.incrementAndGet()
        _invalidations.emit(change)
    }

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

    override suspend fun refreshIndex(): LyricsIndexResult {
        val result = refreshSnapshot()
        invalidate(LyricsInvalidation())
        return result
    }

    private suspend fun refreshSnapshot(): LyricsIndexResult = refreshMutex.withLock { indexer.refresh() }

    override suspend fun findLyrics(song: SongLyricsIdentity): LyricsLookupResult =
        withContext(ioDispatcher) {
            lookupMutex.withLock {
                repeat(2) {
                    coroutineContext.ensureActive()
                    val epoch = revision.get()
                    val key = LyricsResultCache.Key(song)
                    cache.get(key)?.let { return@withLock it }
                    localOverrides.find(song)?.let { local ->
                        if (revision.get() == epoch) {
                            cache.put(key, local)
                            return@withLock local
                        }
                        return@repeat
                    }
                    val external = controlled("external") {
                        findLyrics(song, staleDocumentUri = null, mayRefreshStale = true)
                    }
                    val resolved = resolveSources(song, external)
                    coroutineContext.ensureActive()
                    if (revision.get() == epoch) {
                        cache.put(key, resolved)
                        return@withLock resolved
                    }
                }
                LyricsLookupResult.Unavailable(listOf(LyricsSourceIssue(song.trackKey, LyricsSourceIssue.Kind.LOOKUP_FAILURE)))
            }
        }

    private suspend fun controlled(sourceId: String, action: suspend () -> LyricsLookupResult): LyricsLookupResult = try {
        action()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        LyricsLookupResult.Unavailable(listOf(LyricsSourceIssue(sourceId, LyricsSourceIssue.Kind.LOOKUP_FAILURE)))
    } catch (_: LinkageError) {
        LyricsLookupResult.Unavailable(listOf(LyricsSourceIssue(sourceId, LyricsSourceIssue.Kind.LOOKUP_FAILURE)))
    }

    private suspend fun resolveSources(song: SongLyricsIdentity, external: LyricsLookupResult): LyricsLookupResult {
        val externalLyrics = (external as? LyricsLookupResult.Found)?.lyrics
        // External timed lyrics always win; avoid unnecessary metadata I/O in this common path.
        if (externalLyrics?.document is LyricsDocument.Synced) return external
        val embedded = try {
            embeddedReader.read(song)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            FileEmbeddedLyricsReader.failure(song.trackKey, LyricsSourceIssue.Kind.PERMISSION_DENIED)
        } catch (_: Exception) {
            FileEmbeddedLyricsReader.failure(song.trackKey, LyricsSourceIssue.Kind.METADATA_FAILURE)
        } catch (_: LinkageError) {
            FileEmbeddedLyricsReader.failure(song.trackKey, LyricsSourceIssue.Kind.METADATA_FAILURE)
        }
        val issues = external.toIssues().toMutableList()
        issues += embedded.issues.take(LyricsLimits.DIAGNOSTICS)
        val usable = mutableListOf<SourcedLyrics>()
        if (embedded.candidates.size > LyricsLimits.CANDIDATES) {
            issues += LyricsSourceIssue(song.trackKey, LyricsSourceIssue.Kind.INPUT_LIMIT)
        } else {
            var characters = 0L
            for (candidate in embedded.candidates) {
                coroutineContext.ensureActive()
                characters += candidate.text.length
                if (characters > LyricsLimits.SOURCE_CHARACTERS) {
                    issues += LyricsSourceIssue(candidate.source.stableId, LyricsSourceIssue.Kind.INPUT_LIMIT)
                    break
                }
                try {
                    val context = coroutineContext
                    val source = parser.parseSource(candidate.text) { context.ensureActive() }
                    val document = parser.project(source)
                    if (document.hasUsableLyrics()) usable += SourcedLyrics(document, candidate.source, source,
                        revision = lyricsIdentity(source.revision, candidate.source.textEncoding.toString(),
                            song.reference?.dateModifiedEpochSeconds.toString(), song.reference?.fileSizeBytes.toString()))
                    else issues += LyricsSourceIssue(candidate.source.stableId, LyricsSourceIssue.Kind.INVALID_LYRICS)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: LyricsInputLimitException) {
                    issues += LyricsSourceIssue(candidate.source.stableId, LyricsSourceIssue.Kind.INPUT_LIMIT)
                } catch (_: Exception) {
                    issues += LyricsSourceIssue(candidate.source.stableId, LyricsSourceIssue.Kind.INVALID_LYRICS)
                }
            }
        }
        if (!embedded.selectionComplete) issues += LyricsSourceIssue("embedded", LyricsSourceIssue.Kind.AMBIGUOUS,
            candidates = usable.map(SourcedLyrics::source))
        val selectable = if (embedded.selectionComplete) usable else emptyList()
        val timed = preferredEmbedded(selectable.filter { it.document is LyricsDocument.Synced }, issues)
        val selected = timed ?: externalLyrics ?:
            preferredEmbedded(selectable.filter { it.document is LyricsDocument.Unsynced }, issues)
        if (selected != null) return LyricsLookupResult.Found(selected.copy(
            issues = issues.take(LyricsLimits.DIAGNOSTICS),
            alternatives = (listOfNotNull(externalLyrics) + usable).map(SourcedLyrics::source)
                .filter { it.stableId != selected.source.stableId }
        ))
        // Preserve legacy sidecar results for playback-only implementations with no embedded reader.
        if (embedded.issues.isEmpty() && embedded.candidates.isEmpty()) return external
        return LyricsLookupResult.Unavailable(issues.take(LyricsLimits.DIAGNOSTICS))
    }

    /** Prefer language-neutral and then undescribed versions; never pick a language by frame order. */
    private fun preferredEmbedded(candidates: List<SourcedLyrics>, issues: MutableList<LyricsSourceIssue>): SourcedLyrics? {
        if (candidates.isEmpty()) return null
        fun rank(lyrics: SourcedLyrics): Int {
            val source = lyrics.source as LyricsSource.Embedded
            val neutral = source.language.orEmpty().trim().lowercase(java.util.Locale.ROOT) in setOf("", "und", "xxx")
            return (if (neutral) 0 else 2) + if (source.description.isNullOrBlank()) 0 else 1
        }
        val minimum = candidates.minOf(::rank)
        val best = candidates.filter { rank(it) == minimum }
        if (best.size == 1) return best.single()
        issues += LyricsSourceIssue("embedded", LyricsSourceIssue.Kind.AMBIGUOUS, candidates = best.map(SourcedLyrics::source))
        return null
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
            snapshot = refreshSnapshot().snapshot
        }

        val result = when (val match = LocalLyricsMatcher.match(song, snapshot.files)) {
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
        return if (result is LyricsLookupResult.Found && snapshot.issues.isNotEmpty()) {
            result.copy(lyrics = result.lyrics.copy(issues = result.lyrics.issues + snapshot.issues
                .take(LyricsLimits.DIAGNOSTICS).map { issue ->
                    LyricsSourceIssue(issue.rootUri, if (issue.kind == LyricsRootIssue.Kind.PERMISSION_LOST)
                        LyricsSourceIssue.Kind.PERMISSION_DENIED else LyricsSourceIssue.Kind.SCAN_FAILURE)
                }))
        } else result
    }

    private suspend fun readMatch(
        song: SongLyricsIdentity,
        file: IndexedLyricsFile,
        mayRefreshStale: Boolean
    ): LyricsLookupResult {
        return when (val readResult = documentReader.read(file.documentUri)) {
            is LyricsDocumentReadResult.Success -> {
                val context = coroutineContext
                val source = try {
                    parser.parseSource(readResult.bytes) { context.ensureActive() }
                } catch (_: LyricsInputLimitException) {
                    return LyricsLookupResult.Unavailable(listOf(LyricsSourceIssue(file.documentUri, LyricsSourceIssue.Kind.INPUT_LIMIT)))
                }
                val document = parser.project(source)
                if (!document.hasUsableLyrics()) {
                    LyricsLookupResult.InvalidLyrics(file.documentUri)
                } else {
                    LyricsLookupResult.Found(
                        SourcedLyrics(
                            document = document,
                            source = LyricsSource.LocalSidecar(
                                documentUri = file.documentUri,
                                displayName = file.displayName
                            ),
                            sourceDocument = source,
                            revision = lyricsIdentity(source.revision, file.sizeBytes.toString(), file.lastModifiedEpochMs.toString())
                        )
                    )
                }
            }
            LyricsDocumentReadResult.Missing -> {
                if (mayRefreshStale) {
                    refreshSnapshot()
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
            LyricsDocumentReadResult.TooLarge -> LyricsLookupResult.Unavailable(listOf(
                LyricsSourceIssue(file.documentUri, LyricsSourceIssue.Kind.INPUT_LIMIT)))
        }
    }

    private fun LyricsDocument.hasUsableLyrics(): Boolean = when (this) {
        is LyricsDocument.Synced -> cues.any { it.content is LyricCueContent.Text }
        is LyricsDocument.Unsynced -> lines.any { it.text.isNotBlank() }
    }
}

internal fun LyricsLookupResult.toIssues(): List<LyricsSourceIssue> = when (this) {
    is LyricsLookupResult.Found -> lyrics.issues
    is LyricsLookupResult.Unavailable -> issues
    LyricsLookupResult.NoRootsConfigured -> listOf(LyricsSourceIssue("external", LyricsSourceIssue.Kind.NO_ROOTS))
    LyricsLookupResult.NotFound -> listOf(LyricsSourceIssue("external", LyricsSourceIssue.Kind.NOT_FOUND))
    is LyricsLookupResult.Ambiguous -> listOf(LyricsSourceIssue("external", LyricsSourceIssue.Kind.AMBIGUOUS,
        externalCandidates = candidates.take(LyricsLimits.CANDIDATES)))
    is LyricsLookupResult.PermissionLost -> listOf(LyricsSourceIssue(rootUri, LyricsSourceIssue.Kind.PERMISSION_DENIED))
    is LyricsLookupResult.RootScanError -> listOf(LyricsSourceIssue(rootUri, LyricsSourceIssue.Kind.SCAN_FAILURE))
    is LyricsLookupResult.StaleFile -> listOf(LyricsSourceIssue(documentUri, LyricsSourceIssue.Kind.STALE_FILE))
    is LyricsLookupResult.ReadError -> listOf(LyricsSourceIssue(documentUri, LyricsSourceIssue.Kind.READ_FAILURE))
    is LyricsLookupResult.InvalidLyrics -> listOf(LyricsSourceIssue(documentUri, LyricsSourceIssue.Kind.INVALID_LYRICS))
}
