package io.github.rsgarrido.sazanami.lyrics

import android.net.Uri
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.player.RepeatMode
import io.github.rsgarrido.sazanami.ui.state.PlaybackUiState
import io.github.rsgarrido.sazanami.ui.state.PlaybackProgressUiState
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.Mockito.mock

class LyricsPlaybackControllerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test fun returnPublishesImmediatelyWhilePausedOnTheSameWordAndCue() = runBlocking {
        val repository = PlaybackLyricsRepository().apply {
            defaultResult = found(LrcParser().parse("[00:01]<00:01>Long <00:08>line"))
        }
        val fixture = fixture(repository, position = { 1500 })
        fixture.playback.value = playback(song(1), isPlaying = false)
        fixture.controller.setVisible(true)
        val initial = fixture.controller.awaitState<LyricsPlaybackUiState.Synced>()
        val initialWords = initial.karaoke.value
        fixture.controller.suspendAutoFollow()
        assertFalse((fixture.controller.uiState.value as LyricsPlaybackUiState.Synced).autoFollowEnabled)
        fixture.controller.returnToCurrentLine()
        // No ticker, next cue, next word, delay or await is needed to observe acceptance.
        val returned = fixture.controller.uiState.value as LyricsPlaybackUiState.Synced
        assertTrue(returned.autoFollowEnabled)
        assertEquals(initial.activeGroup, returned.activeGroup)
        assertEquals(initialWords, returned.karaoke.value)
        assertTrue(returned.followRequestId > initial.followRequestId)
        fixture.controller.returnToCurrentLine()
        assertTrue((fixture.controller.uiState.value as LyricsPlaybackUiState.Synced).followRequestId > returned.followRequestId)
        fixture.controller.suspendAutoFollow()
        assertFalse((fixture.controller.uiState.value as LyricsPlaybackUiState.Synced).autoFollowEnabled)
    }

    @Test fun directWordsPublishOnlyAtConfirmedBoundariesIncludingPausedAndRejectedSeeks() = runBlocking {
        val repository = PlaybackLyricsRepository().apply {
            defaultResult = found(LrcParser().parse("[00:01]<00:01>First <00:01.4>next <00:01.75>last\n[00:05]Other"))
        }
        val progress = MutableStateFlow(PlaybackProgressUiState(currentPosition = 1000, isDirect = true))
        var reads = 0
        val fixture = fixture(repository, position = { reads++; 0 }, authoritativeProgress = progress)
        fixture.playback.value = playback(song(1), isPlaying = false)
        fixture.controller.setVisible(true)
        val first = fixture.controller.awaitState<LyricsPlaybackUiState.Synced>()
        val firstWords = first.karaoke.value
        assertEquals(listOf(KaraokeLineState(0, 1000)), firstWords)
        progress.value = progress.value.copy(currentPosition = 1399)
        assertSame(first, fixture.controller.uiState.value)
        assertSame(firstWords, first.karaoke.value)
        progress.value = progress.value.copy(currentPosition = 1400)
        val middle = fixture.controller.uiState.value as LyricsPlaybackUiState.Synced
        val middleWords = middle.karaoke.value
        assertSame("Word boundaries must not invalidate structural player collectors", first, middle)
        assertEquals(first.activeGroup, middle.activeGroup)
        assertEquals(listOf(KaraokeLineState(0, 1400)), middle.karaoke.value)
        progress.value = progress.value.copy(isSeeking = true)
        fixture.controller.onSeek(5000)
        progress.value = progress.value.copy(currentPosition = 5000) // An unconfirmed destination.
        assertSame(middle, fixture.controller.uiState.value)
        fixture.controller.returnToCurrentLine()
        assertTrue((fixture.controller.uiState.value as LyricsPlaybackUiState.Synced).autoFollowEnabled)
        assertEquals(middleWords, (fixture.controller.uiState.value as LyricsPlaybackUiState.Synced).karaoke.value)
        progress.value = progress.value.copy(currentPosition = 1400, isSeeking = false) // Rejected/failed.
        assertEquals(middleWords, (fixture.controller.uiState.value as LyricsPlaybackUiState.Synced).karaoke.value)
        progress.value = progress.value.copy(currentPosition = 1750)
        assertEquals(listOf(KaraokeLineState(0, 1750)), (fixture.controller.uiState.value as LyricsPlaybackUiState.Synced).karaoke.value)
        progress.value = progress.value.copy(currentPosition = 1000)
        assertEquals(firstWords, (fixture.controller.uiState.value as LyricsPlaybackUiState.Synced).karaoke.value)
        progress.value = progress.value.copy(currentPosition = 0) // Repeat/restart.
        val restart = fixture.controller.uiState.value as LyricsPlaybackUiState.Synced
        assertNull(restart.activeGroup)
        assertTrue(restart.karaoke.value.isEmpty())
        progress.value = progress.value.copy(currentPosition = 1000)
        assertEquals(firstWords, (fixture.controller.uiState.value as LyricsPlaybackUiState.Synced).karaoke.value)
        assertEquals(0, reads)
        assertEquals(1, repository.lookupCount.get())
    }

    @Test fun sourceReplacementOffsetsAndTrackChangesCannotRetainOldWordStates() = runBlocking {
        val parser = LrcParser()
        fun enhanced(offset: Int, text: String): LyricsLookupResult {
            val source = parser.parseSource("[offset:$offset]\n[00:01]<00:01>$text <00:02>ending")
            return LyricsLookupResult.Found(SourcedLyrics(parser.project(source), LyricsSource.LocalOverride("saved"), source))
        }
        val repository = PlaybackLyricsRepository().apply { defaultResult = enhanced(0, "Original") }
        val progress = MutableStateFlow(PlaybackProgressUiState(currentPosition = 2200, isDirect = true))
        val fixture = fixture(repository, authoritativeProgress = progress)
        val targetSong = song(1)
        fixture.playback.value = playback(targetSong)
        fixture.controller.setVisible(true)
        val original = fixture.controller.awaitState<LyricsPlaybackUiState.Synced>()
        val originalWords = original.karaoke.value
        assertEquals(2000L, originalWords.single().activeTimestampMs)
        repository.defaultResult = enhanced(500, "Replacement")
        repository.changes.emit(LyricsInvalidation(trackKey = targetSong.toLyricsIdentity().trackKey))
        val replacement = fixture.controller.awaitState<LyricsPlaybackUiState.Synced> { it.activeGroup?.lines == listOf("Replacement ending") }
        assertEquals(1500L, replacement.karaoke.value.single().activeTimestampMs)
        progress.value = progress.value.copy(isSeeking = true)
        fixture.playback.value = playback(targetSong.copy(volumeName = "another-volume"))
        val next = fixture.controller.awaitState<LyricsPlaybackUiState.Synced> { it.song.volumeName == "another-volume" }
        assertNull(next.activeGroup)
        assertTrue(next.karaoke.value.isEmpty())
        progress.value = progress.value.copy(currentPosition = 2500, isSeeking = false)
        assertEquals(2500L, (fixture.controller.uiState.value as LyricsPlaybackUiState.Synced).karaoke.value.single().activeTimestampMs)
        fixture.playback.value = playback(song(2))
        assertEquals(2L, fixture.controller.awaitState<LyricsPlaybackUiState.Synced>().song.id)
        assertEquals(originalWords, original.karaoke.value)
    }

    @Test fun androidWordsUseTheExistingVisibleTickerAndPauseResumePositionSampling() = runBlocking {
        var position = 1000L
        // Reuse one qualified track: song() creates a fresh mock Uri on each call.
        val targetSong = song(1)
        val repository = PlaybackLyricsRepository().apply {
            defaultResult = found(LrcParser().parse("[00:01]<00:01>One <00:02>two"))
        }
        val fixture = fixture(repository, position = { position })
        fixture.playback.value = playback(targetSong, isPlaying = true)
        fixture.controller.setVisible(true)
        val initial = fixture.controller.awaitState<LyricsPlaybackUiState.Synced> { it.karaoke.value.singleOrNull()?.activeTimestampMs == 1000L }
        position = 2000
        fixture.controller.awaitState<LyricsPlaybackUiState.Synced> { it.karaoke.value.singleOrNull()?.activeTimestampMs == 2000L }
        fixture.playback.value = playback(targetSong, isPlaying = false)
        val paused = fixture.controller.uiState.value as LyricsPlaybackUiState.Synced
        assertSame("Pausing the same track must retain its loaded word-progress flow", initial.karaoke, paused.karaoke)
        val pausedWords = paused.karaoke.value
        fixture.controller.returnToCurrentLine()
        assertEquals(pausedWords,
            (fixture.controller.uiState.value as LyricsPlaybackUiState.Synced).karaoke.value)
        position = 1100
        fixture.playback.value = playback(targetSong, isPlaying = true)
        assertSame("Resuming the same track must retain its loaded word-progress flow", initial.karaoke,
            (fixture.controller.uiState.value as LyricsPlaybackUiState.Synced).karaoke)
        assertEquals(1000L, (fixture.controller.uiState.value as LyricsPlaybackUiState.Synced).karaoke.value.single().activeTimestampMs)
        assertEquals(1, repository.lookupCount.get())
    }

    @Test fun simultaneousLinesResolveIndependentlyAndInvalidTimingIsLineOnly() = runBlocking {
        val repository = PlaybackLyricsRepository().apply {
            defaultResult = found(LrcParser().parse("[00:01]<00:01>A<00:02>B\n[00:01]<00:01.5>C\n[00:01]<00:02>D<00:01>E"))
        }
        val fixture = fixture(repository, position = { 1000 })
        fixture.playback.value = playback(song(1))
        fixture.controller.setVisible(true)
        val state = fixture.controller.awaitState<LyricsPlaybackUiState.Synced>()
        assertEquals(listOf("AB", "C", "DE"), state.activeGroup?.lines)
        assertEquals(listOf(KaraokeLineState(0, 1000), KaraokeLineState(1, null)), state.karaoke.value)
    }

    @Test fun pausedAndroidSubWordSeekUsesReportedPositionWithoutCoarseTargetTolerance() = runBlocking {
        var reported = 1390L
        val progress = MutableStateFlow(PlaybackProgressUiState(currentPosition = 1390))
        val repository = PlaybackLyricsRepository().apply {
            defaultResult = found(LrcParser().parse("[00:01]<00:01>One<00:01.4>two"))
        }
        val fixture = fixture(repository, position = { reported }, authoritativeProgress = progress)
        fixture.playback.value = playback(song(1), isPlaying = false)
        fixture.controller.setVisible(true)
        val initialWords = fixture.controller.awaitState<LyricsPlaybackUiState.Synced>().karaoke.value
        fixture.controller.onSeek(1410)
        assertEquals(initialWords, (fixture.controller.uiState.value as LyricsPlaybackUiState.Synced).karaoke.value)
        // The projected request itself is not the reported Media3 playhead.
        progress.value = progress.value.copy(currentPosition = 1410)
        assertEquals(initialWords, (fixture.controller.uiState.value as LyricsPlaybackUiState.Synced).karaoke.value)
        reported = 1410
        fixture.controller.awaitState<LyricsPlaybackUiState.Synced> { it.karaoke.value.singleOrNull()?.activeTimestampMs == 1400L }
        reported = 1390
        progress.value = progress.value.copy(currentPosition = 1390)
        assertEquals(initialWords, (fixture.controller.uiState.value as LyricsPlaybackUiState.Synced).karaoke.value)
        assertFalse(fixture.playback.value.isPlaying)
    }

    @Test fun returnAlsoPublishesBeforeFirstCueDuringInstrumentalAndOnLastCue() = runBlocking {
        var position = 0L
        val repository = PlaybackLyricsRepository().apply {
            defaultResult = found(LrcParser().parse("[00:01]First\n[00:02]\n[00:03]Last"))
        }
        val fixture = fixture(repository, position = { position })
        fixture.playback.value = playback(song(1))
        fixture.controller.setVisible(true)
        for (time in listOf(0L, 2000L, 5000L)) {
            position = time
            fixture.controller.onSeek(time)
            fixture.controller.suspendAutoFollow()
            val before = fixture.controller.uiState.value as LyricsPlaybackUiState.Synced
            fixture.controller.returnToCurrentLine()
            val after = fixture.controller.uiState.value as LyricsPlaybackUiState.Synced
            assertTrue(after.autoFollowEnabled)
            assertEquals(before.activeGroup, after.activeGroup)
            assertTrue(after.followRequestId > before.followRequestId)
        }
    }

    @Test
    fun sameNumericIdAcrossVolumesAndMetadataRevisionsTriggersFreshLookup() = runBlocking {
        val repository = PlaybackLyricsRepository().apply { defaultResult = found(unsynced("Current")) }
        val fixture = fixture(repository)
        fixture.controller.setVisible(true)
        val original = song(7).copy(volumeName = "primary")
        fixture.playback.value = playback(original)
        fixture.controller.awaitState<LyricsPlaybackUiState.Unsynced>()
        fixture.playback.value = playback(original.copy(volumeName = "sd-card"))
        fixture.controller.awaitState<LyricsPlaybackUiState.Unsynced> { it.song.volumeName == "sd-card" }
        fixture.playback.value = playback(original.copy(volumeName = "sd-card", dateModifiedEpochSeconds = 123, title = "Updated"))
        fixture.controller.awaitState<LyricsPlaybackUiState.Unsynced> { it.song.title == "Updated" }
        assertEquals(3, repository.lookupCount.get())
    }

    @Test
    fun sourceSnapshotSurvivesControllerAndTargetedInvalidationReloadsWithoutTicksReadingTags() = runBlocking {
        val parser = LrcParser()
        val source = parser.parseSource("[00:01]<00:01>Original")
        val sourced = SourcedLyrics(parser.project(source), LyricsSource.Embedded("track", "USLT", "eng", "original"), source)
        val repository = PlaybackLyricsRepository().apply { defaultResult = LyricsLookupResult.Found(sourced) }
        val fixture = fixture(repository)
        val target = song(1)
        fixture.controller.setVisible(true)
        fixture.playback.value = playback(target, isPlaying = true)
        val state = fixture.controller.awaitState<LyricsPlaybackUiState.Synced>()
        assertEquals(sourced, state.sourcedLyrics)
        delay(40)
        assertEquals(1, repository.lookupCount.get())
        repository.changes.emit(LyricsInvalidation(trackKey = "different-track"))
        assertEquals(1, repository.lookupCount.get())
        repository.changes.emit(LyricsInvalidation(trackKey = target.toLyricsIdentity().trackKey))
        fixture.controller.awaitState<LyricsPlaybackUiState.Synced>()
        assertEquals(2, repository.lookupCount.get())
    }

    @Test
    fun unexpectedLookupFailureLeavesLoadingAndRescanCanRecover() = runBlocking {
        val repository = PlaybackLyricsRepository().apply { lookup = { throw IllegalStateException("index read failed") } }
        val fixture = fixture(repository)
        fixture.controller.setVisible(true)
        fixture.playback.value = playback(song(1))
        val failed = fixture.controller.awaitState<LyricsPlaybackUiState.Unavailable>()
        assertTrue((failed.reason as LyricsUnavailableReason.SourcesUnavailable).issues.any {
            it.kind == LyricsSourceIssue.Kind.LOOKUP_FAILURE
        })
        repository.lookup = { found(unsynced("Recovered")) }
        fixture.controller.rescan()
        assertEquals("Recovered", fixture.controller.awaitState<LyricsPlaybackUiState.Unsynced>().lyrics.lines.single().text)
    }

    @Test
    fun cancelledSameIdLookupCannotPublishAfterNewVolumeEvenIfReaderIgnoresCancellation() = runBlocking {
        val repository = PlaybackLyricsRepository().apply {
            lookup = { identity ->
                if (identity.volumeId == "primary") {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { delay(40) }
                    found(unsynced("Old volume"))
                } else found(unsynced("New volume"))
            }
        }
        val fixture = fixture(repository)
        fixture.controller.setVisible(true)
        val original = song(8).copy(volumeName = "primary")
        fixture.playback.value = playback(original)
        fixture.controller.awaitState<LyricsPlaybackUiState.Loading>()
        fixture.playback.value = playback(original.copy(volumeName = "sd-card"))
        fixture.controller.awaitState<LyricsPlaybackUiState.Unsynced>()
        delay(70)
        assertEquals("New volume", (fixture.controller.uiState.value as LyricsPlaybackUiState.Unsynced).lyrics.lines.single().text)
    }

    @Test
    fun pausedDirectSeeksFollowOnlyLatestConfirmedPositionWithoutPolling() = runBlocking {
        val repository = PlaybackLyricsRepository().apply {
            defaultResult = found(LyricsDocument.Synced(listOf(
                cue(1_000, "First"), cue(5_000, "Middle"), cue(8_000, "Last"))))
        }
        val progress = MutableStateFlow(PlaybackProgressUiState(currentPosition = 1000, isDirect = true))
        var reads = 0
        val fixture = fixture(repository, position = { reads++; 1000L }, authoritativeProgress = progress)
        fixture.playback.value = playback(song(1), isPlaying = false)
        fixture.controller.setVisible(true)
        fixture.controller.awaitState<LyricsPlaybackUiState.Synced> { it.activeGroup?.lines == listOf("First") }

        progress.value = progress.value.copy(isSeeking = true)
        fixture.controller.onSeek(5_000)
        fixture.controller.onSeek(8_000) // A superseding request is still not a confirmation.
        progress.value = progress.value.copy(currentPosition = 5000)
        assertEquals(listOf("First"), (fixture.controller.uiState.value as LyricsPlaybackUiState.Synced).activeGroup?.lines)
        progress.value = progress.value.copy(currentPosition = 8000, isSeeking = false)
        fixture.controller.awaitState<LyricsPlaybackUiState.Synced> { it.activeGroup?.lines == listOf("Last") }

        // An external paused seek has no local onSeek callback.
        progress.value = progress.value.copy(isSeeking = true)
        progress.value = progress.value.copy(currentPosition = 1000, isSeeking = false)
        fixture.controller.awaitState<LyricsPlaybackUiState.Synced> { it.activeGroup?.lines == listOf("First") }
        assertEquals(0, reads)
        assertFalse(fixture.playback.value.isPlaying)
    }

    @Test
    fun rejectedAndFailedDirectSeeksNeverLeaveOptimisticLyricsAndNavigationUsesCurrentPosition() = runBlocking<Unit> {
        val repository = PlaybackLyricsRepository().apply {
            defaultResult = found(LyricsDocument.Synced(listOf(cue(1_000, "First"), cue(5_000, "Second"))))
        }
        val progress = MutableStateFlow(PlaybackProgressUiState(currentPosition = 1000, isDirect = true))
        val fixture = fixture(repository, authoritativeProgress = progress)
        fixture.playback.value = playback(song(1))
        fixture.controller.setVisible(true)
        fixture.controller.awaitState<LyricsPlaybackUiState.Synced> { it.activeGroup?.lines == listOf("First") }
        fixture.controller.onSeek(5_000) // Rejected request: authority never changes.
        assertEquals(listOf("First"), (fixture.controller.uiState.value as LyricsPlaybackUiState.Synced).activeGroup?.lines)
        progress.value = progress.value.copy(isSeeking = true)
        fixture.controller.onSeek(5_000)
        progress.value = progress.value.copy(isSeeking = false) // Failed/terminal request retains the actual playhead.
        assertEquals(listOf("First"), (fixture.controller.uiState.value as LyricsPlaybackUiState.Synced).activeGroup?.lines)
        fixture.controller.setVisible(false)
        progress.value = progress.value.copy(currentPosition = 5000)
        fixture.controller.setVisible(true)
        fixture.controller.awaitState<LyricsPlaybackUiState.Synced> { it.activeGroup?.lines == listOf("Second") }
    }

    @Test
    fun activeDirectProgressAndAndroidSeeksUseTheirReportedPositions() = runBlocking {
        val repository = PlaybackLyricsRepository().apply {
            defaultResult = found(LyricsDocument.Synced(listOf(cue(1_000, "First"), cue(5_000, "Second"))))
        }
        val progress = MutableStateFlow(PlaybackProgressUiState(currentPosition = 1000, isDirect = true))
        var reportedPosition = 1000L
        val fixture = fixture(repository, position = { reportedPosition }, authoritativeProgress = progress)
        fixture.playback.value = playback(song(1), isPlaying = true)
        fixture.controller.setVisible(true)
        fixture.controller.awaitState<LyricsPlaybackUiState.Synced> { it.activeGroup?.lines == listOf("First") }
        progress.value = progress.value.copy(currentPosition = 5000)
        fixture.controller.awaitState<LyricsPlaybackUiState.Synced> { it.activeGroup?.lines == listOf("Second") }

        fixture.playback.value = playback(song(1), isPlaying = false)
        progress.value = progress.value.copy(isDirect = false, currentPosition = 1000)
        fixture.controller.onSeek(5_000)
        assertEquals(listOf("First"), (fixture.controller.uiState.value as LyricsPlaybackUiState.Synced).activeGroup?.lines)
        reportedPosition = 5000
        progress.value = progress.value.copy(currentPosition = 5000)
        assertEquals(listOf("Second"), (fixture.controller.uiState.value as LyricsPlaybackUiState.Synced).activeGroup?.lines)
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
        val fixture = fixture(repository, position = {
            reads.incrementAndGet()
            1_000L
        })
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
        var reportedPosition = 0L
        val fixture = fixture(repository, position = { reportedPosition })
        fixture.controller.setVisible(true)
        fixture.playback.value = playback(song(1))
        fixture.controller.awaitState<LyricsPlaybackUiState.Synced>()

        reportedPosition = 1500
        fixture.controller.onSeek(1_500)
        assertEquals(
            listOf("First"),
            (fixture.controller.uiState.value as LyricsPlaybackUiState.Synced)
                .activeGroup?.lines
        )

        reportedPosition = 2500
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
        var reportedPosition = 0L
        val fixture = fixture(repository, position = { reportedPosition })
        fixture.controller.setVisible(true)
        fixture.playback.value = playback(song(1))
        fixture.controller.awaitState<LyricsPlaybackUiState.Synced>()

        reportedPosition = 2500
        fixture.controller.onSeek(2_500)
        assertEquals(
            listOf("Second A", "Second B"),
            (fixture.controller.uiState.value as LyricsPlaybackUiState.Synced)
                .activeGroup?.lines
        )
        reportedPosition = 1200
        fixture.controller.onSeek(1_200)
        assertEquals(
            listOf("First"),
            (fixture.controller.uiState.value as LyricsPlaybackUiState.Synced)
                .activeGroup?.lines
        )
    }

    @Test
    fun seekRequestCannotOverrideReportedPositionAndConfirmedTicksStillAdvance() = runBlocking {
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
            listOf("First"),
            (fixture.controller.uiState.value as LyricsPlaybackUiState.Synced)
                .activeGroup?.lines
        )
        now = 1000L // An expired/rejected request still cannot invent the destination.
        fixture.controller.returnToCurrentLine()
        assertEquals(
            listOf("First"),
            (fixture.controller.uiState.value as LyricsPlaybackUiState.Synced)
                .activeGroup?.lines
        )

        reportedPosition = 5_050L
        fixture.controller.awaitState<LyricsPlaybackUiState.Synced> { it.activeGroup?.lines == listOf("Future") }
        assertEquals(
            listOf("Future"),
            (fixture.controller.uiState.value as LyricsPlaybackUiState.Synced)
                .activeGroup?.lines
        )
    }

    private fun fixture(
        repository: PlaybackLyricsRepository,
        monotonicTimeMs: () -> Long = { System.nanoTime() / 1_000_000L },
        position: () -> Long = { 0L },
        authoritativeProgress: StateFlow<PlaybackProgressUiState>? = null
    ): Fixture {
        val playback = MutableStateFlow(PlaybackUiState())
        val controller = LyricsPlaybackController(
            repository = repository,
            playbackState = playback,
            positionSource = LyricsPositionSource(position),
            scope = scope,
            tickerIntervalMs = 10L,
            monotonicTimeMs = monotonicTimeMs,
            authoritativeProgress = authoritativeProgress
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
    val changes = MutableSharedFlow<LyricsInvalidation>(extraBufferCapacity = 8)
    override val invalidations = changes
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
