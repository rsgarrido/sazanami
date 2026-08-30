package io.github.rsgarrido.sazanami.lyrics

import android.net.Uri
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.player.RepeatMode
import io.github.rsgarrido.sazanami.ui.state.PlaybackUiState
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class LyricsPlaybackControllerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun currentSongTriggersLookupAndMapsSyncedLyrics() = runBlocking {
        val repository = PlaybackLyricsRepository()
        repository.defaultResult = found(synced())
        val fixture = fixture(repository)
        fixture.controller.setVisible(true)

        fixture.playback.value = playback(song(1))
        val state = fixture.controller.awaitState<LyricsPlaybackUiState.Synced>()

        assertEquals(1L, state.song.id)
        assertEquals(1, repository.lookupCount.get())
    }

    @Test
    fun rapidSongChangeCancelsStaleLookupAndNeverPublishesOldLyrics() = runBlocking {
        val repository = PlaybackLyricsRepository()
        val oldCancelled = AtomicBoolean(false)
        repository.lookup = { identity ->
            if (identity.audioFileName == "old.flac") {
                suspendCancellableCoroutine { continuation ->
                    continuation.invokeOnCancellation { oldCancelled.set(true) }
                }
            } else {
                found(unsynced("New lyrics"))
            }
        }
        val fixture = fixture(repository)
        fixture.controller.setVisible(true)
        fixture.playback.value = playback(song(1, "old.flac"))
        fixture.controller.awaitState<LyricsPlaybackUiState.Loading>()

        fixture.playback.value = playback(song(2, "new.flac"))
        val state = fixture.controller.awaitState<LyricsPlaybackUiState.Unsynced>()

        assertEquals(2L, state.song.id)
        assertEquals("New lyrics", state.lyrics.lines.single().text)
        assertTrue(oldCancelled.get())
    }

    @Test
    fun unsyncedLookupMapsToUnsyncedState() = runBlocking {
        val repository = PlaybackLyricsRepository()
        repository.defaultResult = found(unsynced("Static"))
        val fixture = fixture(repository)
        fixture.controller.setVisible(true)
        fixture.playback.value = playback(song(1))

        assertTrue(
            fixture.controller.awaitState<LyricsPlaybackUiState.Unsynced>()
                .lyrics.lines.isNotEmpty()
        )
    }

    @Test
    fun everyRepositoryFailureMapsToItsUiReason() = runBlocking {
        val cases = listOf(
            LyricsLookupResult.NoRootsConfigured to LyricsUnavailableReason.NoRootsConfigured::class,
            LyricsLookupResult.NotFound to LyricsUnavailableReason.NotFound::class,
            LyricsLookupResult.Ambiguous(emptyList()) to LyricsUnavailableReason.Ambiguous::class,
            LyricsLookupResult.PermissionLost("root") to
                    LyricsUnavailableReason.PermissionLost::class,
            LyricsLookupResult.RootScanError("root") to
                    LyricsUnavailableReason.RootScanError::class,
            LyricsLookupResult.StaleFile("file") to LyricsUnavailableReason.StaleFile::class,
            LyricsLookupResult.ReadError("file") to LyricsUnavailableReason.ReadError::class,
            LyricsLookupResult.InvalidLyrics("file") to LyricsUnavailableReason.InvalidLyrics::class
        )

        cases.forEachIndexed { index, (result, expectedClass) ->
            val repository = PlaybackLyricsRepository().apply { defaultResult = result }
            val fixture = fixture(repository)
            fixture.controller.setVisible(true)
            fixture.playback.value = playback(song(index.toLong() + 1))

            val state = fixture.controller.awaitState<LyricsPlaybackUiState.Unavailable>()
            assertEquals(expectedClass, state.reason::class)
        }
    }

    @Test
    fun rescanRefreshesIndexAndRetriesCurrentSong() = runBlocking {
        val repository = PlaybackLyricsRepository()
        repository.defaultResult = LyricsLookupResult.NotFound
        val fixture = fixture(repository)
        fixture.controller.setVisible(true)
        fixture.playback.value = playback(song(1))
        fixture.controller.awaitState<LyricsPlaybackUiState.Unavailable>()
        repository.defaultResult = found(unsynced("After rescan"))

        fixture.controller.rescan()
        val state = fixture.controller.awaitState<LyricsPlaybackUiState.Unsynced>()

        assertEquals("After rescan", state.lyrics.lines.single().text)
        assertEquals(1, repository.refreshCount.get())
    }

    @Test
    fun trackChangeRestoresAutoFollow() = runBlocking {
        val repository = PlaybackLyricsRepository().apply {
            defaultResult = found(synced())
        }
        val fixture = fixture(repository)
        fixture.controller.setVisible(true)
        fixture.playback.value = playback(song(1))
        fixture.controller.awaitState<LyricsPlaybackUiState.Synced>()
        fixture.controller.suspendAutoFollow()
        assertFalse(
            (fixture.controller.uiState.value as LyricsPlaybackUiState.Synced)
                .autoFollowEnabled
        )

        fixture.playback.value = playback(song(2))
        val state = fixture.controller.awaitState<LyricsPlaybackUiState.Synced> {
            it.song.id == 2L
        }

        assertTrue(state.autoFollowEnabled)
    }

    @Test
    fun highFrequencyTickerRunsOnlyWhenVisibleAndPlaying() = runBlocking {
        val reads = AtomicInteger()
        val repository = PlaybackLyricsRepository().apply {
            defaultResult = found(synced())
        }
        val fixture = fixture(repository) {
            reads.incrementAndGet()
            1_000L
        }
        fixture.playback.value = playback(song(1), isPlaying = true)
        delay(30)
        val hiddenReads = reads.get()
        delay(35)
        assertEquals(hiddenReads, reads.get())

        fixture.controller.setVisible(true)
        fixture.controller.awaitState<LyricsPlaybackUiState.Synced>()
        delay(100)
        assertTrue("hidden=$hiddenReads current=${reads.get()}", reads.get() > hiddenReads)

        fixture.playback.value = playback(song(1), isPlaying = false)
        delay(20)
        val pausedReads = reads.get()
        delay(35)
        assertEquals(pausedReads, reads.get())
    }

    @Test
    fun seekRecalculatesImmediatelyAndInstrumentalCueClearsHighlight() = runBlocking {
        val document = LyricsDocument.Synced(
            listOf(
                cue(1_000, "First"),
                LyricCue(2_000, LyricCueContent.Instrumental),
                cue(3_000, "Third")
            )
        )
        val repository = PlaybackLyricsRepository().apply {
            defaultResult = found(document)
        }
        val fixture = fixture(repository)
        fixture.controller.setVisible(true)
        fixture.playback.value = playback(song(1))
        fixture.controller.awaitState<LyricsPlaybackUiState.Synced>()

        fixture.controller.onSeek(1_500)
        assertEquals(
            listOf("First"),
            (fixture.controller.uiState.value as LyricsPlaybackUiState.Synced)
                .activeGroup?.lines
        )

        fixture.controller.onSeek(2_500)
        assertNull(
            (fixture.controller.uiState.value as LyricsPlaybackUiState.Synced).activeGroup
        )
    }

    @Test
    fun duplicateTimestampLinesHighlightTogetherAndBackwardSeekWorks() = runBlocking {
        val repository = PlaybackLyricsRepository().apply {
            defaultResult = found(
                LyricsDocument.Synced(
                    listOf(
                        cue(1_000, "First"),
                        cue(2_000, "Second A"),
                        cue(2_000, "Second B")
                    )
                )
            )
        }
        val fixture = fixture(repository)
        fixture.controller.setVisible(true)
        fixture.playback.value = playback(song(1))
        fixture.controller.awaitState<LyricsPlaybackUiState.Synced>()

        fixture.controller.onSeek(2_500)
        assertEquals(
            listOf("Second A", "Second B"),
            (fixture.controller.uiState.value as LyricsPlaybackUiState.Synced)
                .activeGroup?.lines
        )
        fixture.controller.onSeek(1_200)
        assertEquals(
            listOf("First"),
            (fixture.controller.uiState.value as LyricsPlaybackUiState.Synced)
                .activeGroup?.lines
        )
    }

    @Test
    fun optimisticSeekIsNotOverwrittenByStalePreSeekTicks() = runBlocking {
        var now = 0L
        var reportedPosition = 1_100L
        val repository = PlaybackLyricsRepository().apply {
            defaultResult = found(
                LyricsDocument.Synced(
                    listOf(cue(1_000, "First"), cue(5_000, "Future"))
                )
            )
        }
        val fixture = fixture(
            repository = repository,
            position = { reportedPosition },
            monotonicTimeMs = { now }
        )
        fixture.controller.setVisible(true)
        fixture.playback.value = playback(song(1), isPlaying = true)
        fixture.controller.awaitState<LyricsPlaybackUiState.Synced>()

        fixture.controller.onSeek(5_000)
        assertEquals(
            listOf("Future"),
            (fixture.controller.uiState.value as LyricsPlaybackUiState.Synced)
                .activeGroup?.lines
        )
        delay(30)
        assertEquals(
            listOf("Future"),
            (fixture.controller.uiState.value as LyricsPlaybackUiState.Synced)
                .activeGroup?.lines
        )

        reportedPosition = 5_050L
        delay(20)
        assertEquals(
            listOf("Future"),
            (fixture.controller.uiState.value as LyricsPlaybackUiState.Synced)
                .activeGroup?.lines
        )
    }

    private fun fixture(
        repository: PlaybackLyricsRepository,
        monotonicTimeMs: () -> Long = { System.nanoTime() / 1_000_000L },
        position: () -> Long = { 0L }
    ): Fixture {
        val playback = MutableStateFlow(PlaybackUiState())
        val controller = LyricsPlaybackController(
            repository = repository,
            playbackState = playback,
            positionSource = LyricsPositionSource(position),
            scope = scope,
            tickerIntervalMs = 10L,
            monotonicTimeMs = monotonicTimeMs
        )
        return Fixture(controller, playback)
    }

    private fun playback(song: Song, isPlaying: Boolean = false) = PlaybackUiState(
        isConnected = true,
        currentSong = song,
        isPlaying = isPlaying,
        repeatMode = RepeatMode.OFF
    )

    private fun song(id: Long, name: String = "track.flac") = Song(
        id = id,
        title = "Song $id",
        artist = "Artist",
        album = "Album",
        trackNumber = 1,
        duration = 10_000,
        uri = mock(Uri::class.java),
        filePath = "/Music/$name",
        folderPath = "/Music",
        albumArtUri = null,
        displayName = name,
        relativePath = "Music/"
    )

    private fun synced() = LyricsDocument.Synced(
        listOf(cue(1_000, "Line"))
    )

    private fun unsynced(text: String) = LyricsDocument.Unsynced(
        listOf(StaticLyricLine(text))
    )

    private fun found(document: LyricsDocument) = LyricsLookupResult.Found(
        SourcedLyrics(
            document,
            LyricsSource.LocalSidecar("content://lyrics", "track.lrc")
        )
    )

    private fun cue(timestamp: Long, text: String) =
        LyricCue(timestamp, LyricCueContent.Text(text))

    private suspend inline fun <reified T : LyricsPlaybackUiState>
            LyricsPlaybackController.awaitState(
        crossinline predicate: (T) -> Boolean = { true }
    ): T = withTimeout(1_000) {
        while (true) {
            val value = uiState.value
            if (value is T && predicate(value)) return@withTimeout value
            delay(1)
        }
        error("unreachable")
    }

    private data class Fixture(
        val controller: LyricsPlaybackController,
        val playback: MutableStateFlow<PlaybackUiState>
    )
}

private class PlaybackLyricsRepository : LocalLyricsRepository {
    override val roots: StateFlow<List<LyricsRoot>> = MutableStateFlow(emptyList())
    val lookupCount = AtomicInteger()
    val refreshCount = AtomicInteger()
    var defaultResult: LyricsLookupResult = LyricsLookupResult.NotFound
    var lookup: suspend (SongLyricsIdentity) -> LyricsLookupResult = { defaultResult }

    override suspend fun loadCachedIndexSummary(): LyricsIndexSummary? = null

    override suspend fun addRoot(root: LyricsRoot): LyricsIndexResult =
        error("Not used")

    override suspend fun removeRoot(rootUri: String): LyricsIndexResult =
        error("Not used")

    override suspend fun refreshIndex(): LyricsIndexResult {
        refreshCount.incrementAndGet()
        return LyricsIndexResult(
            LyricsIndexSnapshot(emptyList(), emptySet(), generatedAtEpochMs = 0)
        )
    }

    override suspend fun findLyrics(song: SongLyricsIdentity): LyricsLookupResult {
        lookupCount.incrementAndGet()
        return lookup(song)
    }
}
