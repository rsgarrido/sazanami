package io.github.rsgarrido.sazanami.ui.settings

import io.github.rsgarrido.sazanami.lyrics.IndexedLyricsFile
import io.github.rsgarrido.sazanami.lyrics.LocalLyricsRepository
import io.github.rsgarrido.sazanami.lyrics.LyricsIndexResult
import io.github.rsgarrido.sazanami.lyrics.LyricsIndexSnapshot
import io.github.rsgarrido.sazanami.lyrics.LyricsIndexSummary
import io.github.rsgarrido.sazanami.lyrics.LyricsLookupResult
import io.github.rsgarrido.sazanami.lyrics.LyricsRoot
import io.github.rsgarrido.sazanami.lyrics.SongLyricsIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsSettingsControllerTest {
    @Test
    fun persistedThirtyFileIndexInitializesSettingsWithThirty() {
        val repository = FakeSettingsRepository(cachedFileCount = 30)

        val controller = controller(repository)

        assertEquals(30, controller.state.value.indexedFileCount)
        assertEquals(1, repository.loadCount)
    }

    @Test
    fun noPersistedIndexInitializesWithZero() {
        val controller = controller(FakeSettingsRepository(cachedFileCount = null))

        assertEquals(0, controller.state.value.indexedFileCount)
    }

    @Test
    fun unreadablePersistedIndexFailsSafely() {
        val repository = FakeSettingsRepository(
            cachedFileCount = 30,
            failCachedLoad = true
        )

        val controller = controller(repository)

        assertEquals(0, controller.state.value.indexedFileCount)
        assertEquals(0, repository.refreshCount)
    }

    @Test
    fun rescanReplacesDisplayedCount() {
        val repository = FakeSettingsRepository(cachedFileCount = 30)
        val controller = controller(repository)
        repository.nextScanFileCount = 12

        controller.rescan()

        assertEquals(12, controller.state.value.indexedFileCount)
        assertEquals(1, repository.refreshCount)
    }

    @Test
    fun removingRootUpdatesDisplayedCount() {
        val repository = FakeSettingsRepository(cachedFileCount = 30)
        val controller = controller(repository)
        repository.nextScanFileCount = 0

        controller.removeRoot(ROOT.uri)

        assertEquals(0, controller.state.value.indexedFileCount)
        assertEquals(emptyList<LyricsRoot>(), repository.roots.value)
        assertEquals(1, repository.removeCount)
    }

    @Test
    fun controllerRecreationRestoresPersistedCountWithoutScan() {
        val repository = FakeSettingsRepository(cachedFileCount = 30)

        val first = controller(repository)
        val recreated = controller(repository)

        assertEquals(30, first.state.value.indexedFileCount)
        assertEquals(30, recreated.state.value.indexedFileCount)
        assertEquals(2, repository.loadCount)
        assertEquals(0, repository.refreshCount)
    }

    @Test
    fun openingSettingsNeverInvokesRefreshOrTreeTraversal() {
        val repository = FakeSettingsRepository(cachedFileCount = 30)

        controller(repository)

        assertEquals(1, repository.loadCount)
        assertEquals(0, repository.refreshCount)
        assertEquals(0, repository.addCount)
        assertEquals(0, repository.removeCount)
    }

    private fun controller(repository: FakeSettingsRepository) =
        LyricsSettingsController(
            repository = repository,
            retainReadAccess = { Result.failure(IllegalStateException("unused")) },
            hasReadAccess = { true },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        )

    private class FakeSettingsRepository(
        cachedFileCount: Int?,
        private val failCachedLoad: Boolean = false
    ) : LocalLyricsRepository {
        override val roots = MutableStateFlow(listOf(ROOT))
        var cachedFileCount = cachedFileCount
        var nextScanFileCount = cachedFileCount ?: 0
        var loadCount = 0
        var refreshCount = 0
        var addCount = 0
        var removeCount = 0

        override suspend fun loadCachedIndexSummary(): LyricsIndexSummary? {
            loadCount += 1
            if (failCachedLoad) error("Malformed cache")
            return cachedFileCount?.let {
                LyricsIndexSummary(it, roots.value.mapTo(linkedSetOf(), LyricsRoot::uri))
            }
        }

        override suspend fun addRoot(root: LyricsRoot): LyricsIndexResult {
            addCount += 1
            roots.value = roots.value + root
            return result(nextScanFileCount)
        }

        override suspend fun removeRoot(rootUri: String): LyricsIndexResult {
            removeCount += 1
            roots.value = roots.value.filterNot { it.uri == rootUri }
            return result(nextScanFileCount)
        }

        override suspend fun refreshIndex(): LyricsIndexResult {
            refreshCount += 1
            cachedFileCount = nextScanFileCount
            return result(nextScanFileCount)
        }

        override suspend fun findLyrics(song: SongLyricsIdentity): LyricsLookupResult =
            error("Tree lookup must not run from settings")

        private fun result(fileCount: Int) = LyricsIndexResult(
            LyricsIndexSnapshot(
                files = List(fileCount) { index ->
                    IndexedLyricsFile(
                        documentUri = "content://lyrics/$index",
                        rootUri = ROOT.uri,
                        displayName = "$index.lrc",
                        normalizedStem = index.toString(),
                        relativeDirectory = ""
                    )
                },
                indexedRootUris = roots.value.mapTo(linkedSetOf(), LyricsRoot::uri),
                generatedAtEpochMs = 1L
            )
        )
    }

    private companion object {
        val ROOT = LyricsRoot("content://root", "Music")
    }
}
