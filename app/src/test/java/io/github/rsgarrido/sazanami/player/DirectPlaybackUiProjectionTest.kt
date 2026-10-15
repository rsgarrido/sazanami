package io.github.rsgarrido.sazanami.player

import android.net.Uri
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.ui.state.PlaybackUiState
import io.github.rsgarrido.sazanami.ui.state.UiMessage
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock

class DirectPlaybackUiProjectionTest {
    @Test fun unavailableEarlierWindowCannotShiftTheCurrentOccurrenceOrHideItsUpcomingSong() {
        val ui=project(state(IntegratedDirectPhase.PAUSED).copy(queueMediaIds=listOf("404","2","1"),queueIndex=1))
        assertEquals(nativeSong,ui.currentSong);assertEquals(listOf(androidSong),ui.upcomingSongs)
    }
    @Test fun dormantSavedPositionIsNotClampedByUnverifiedLibraryDurationOrTreatedAsNativeProgress() {
        val dormant=state(IntegratedDirectPhase.IDLE).copy(idlePositionMs=5000,metadataDurationMs=3000)
        assertEquals(5000L,dormant.positionMs);assertEquals(0L,dormant.completedFrames)
        assertFalse(project(dormant).isPlaying)
        assertEquals(0L,dormant.copy(phase=IntegratedDirectPhase.PREPARING).positionMs)
    }
    @Test fun fullTimelineProjectsDuplicateUpcomingOccurrencesAndSavedPolicyWithoutTraversal() {
        val ui=project(state(IntegratedDirectPhase.PAUSED).copy(queueMediaIds=listOf("2","1","2"),
            queueIndex=0,queueShuffle=true,queueRepeat=androidx.media3.common.Player.REPEAT_MODE_ALL))
        assertEquals(listOf(androidSong,nativeSong),ui.upcomingSongs)
        assertTrue(ui.queuedSongs.isEmpty());assertTrue(ui.isShuffleEnabled)
        assertEquals(RepeatMode.ALL,ui.repeatMode);assertFalse(ui.isPlaying)
    }
    private val androidSong = song(1)
    private val nativeSong = song(2)

    @Test fun transitionIntentNeverClaimsNativeConfirmation() {
        for (phase in IntegratedDirectPhase.entries) {
            for (intent in listOf(false, true)) {
                val direct = state(phase).copy(playWhenReady = intent)
                val ui = project(direct)
                assertEquals(phase == IntegratedDirectPhase.PLAYING, ui.isPlaying)
                assertEquals(direct.busy && phase != IntegratedDirectPhase.STOPPING && intent, ui.playPauseIntent)
            }
        }
        val android = PlaybackUiState(currentSong = androidSong, isPlaying = true)
        val restored = directPlaybackUiState(android.copy(playPauseIntent = false,
            playbackStatus = UiMessage.Text(R.string.player_playback_failed)),
            IntegratedDirectPlaybackState(), listOf(androidSong))
        assertTrue(restored.isPlaying)
        assertEquals(androidSong, restored.currentSong)
        assertNull(restored.playPauseIntent)
        assertNull(restored.playbackStatus)
    }

    @Test fun emptyDirectSelectionClearsOnlyPlaybackAssociation() {
        val previous = PlaybackUiState(currentSong = androidSong, isPlaying = true,
            queuedSongs = listOf(androidSong), isShuffleEnabled = true, repeatMode = RepeatMode.ALL)
        val empty = directPlaybackUiState(previous, state(IntegratedDirectPhase.IDLE).copy(libraryId = null),
            listOf(androidSong, nativeSong))
        assertNull(empty.currentSong)
        assertFalse(empty.isPlaying)
        assertEquals(previous.queuedSongs, empty.queuedSongs)
        assertEquals(previous.repeatMode, empty.repeatMode)
        assertEquals(previous.isShuffleEnabled, empty.isShuffleEnabled)
        val loaded = directPlaybackUiState(empty, state(IntegratedDirectPhase.PREPARING), listOf(nativeSong))
        assertEquals(nativeSong, loaded.currentSong)
        // A known matching selection remains visible during a library refresh.
        assertEquals(nativeSong, directPlaybackUiState(loaded, state(IntegratedDirectPhase.PLAYING), emptyList()).currentSong)
        assertNull(directPlaybackUiState(previous, state(IntegratedDirectPhase.IDLE), emptyList()).currentSong)
    }

    @Test fun preparationTerminationAndRestorationHaveDistinctLocalizedStatus() {
        val expected = mapOf(
            IntegratedDirectPhase.PREPARING to R.string.external_audio_preparing,
            IntegratedDirectPhase.ENDED to R.string.player_playback_ended,
            IntegratedDirectPhase.STOPPED to R.string.player_playback_stopped,
            IntegratedDirectPhase.ERROR to R.string.player_playback_failed
        )
        expected.forEach { (phase, resource) ->
            assertEquals(UiMessage.Text(resource), project(state(phase)).playbackStatus)
        }
        assertEquals(UiMessage.Text(R.string.usb_settings_cleanup_failed),
            project(state(IntegratedDirectPhase.ERROR).copy(cleanupSafe = false)).playbackStatus)
        assertEquals(UiMessage.Text(R.string.usb_settings_returning),
            project(state(IntegratedDirectPhase.ERROR).copy(switching = DirectOutputSwitch.TO_ANDROID)).playbackStatus)
        val error = project(state(IntegratedDirectPhase.ERROR))
        assertNull(directPlaybackUiState(error, state(IntegratedDirectPhase.PLAYING), listOf(nativeSong)).playbackStatus)
        assertNull(project(state(IntegratedDirectPhase.PAUSED)).playbackStatus)
        assertEquals(project(state(IntegratedDirectPhase.PREPARING)).playbackStatus,
            project(state(IntegratedDirectPhase.PREPARING).copy(completedFrames = 48000)).playbackStatus)
    }

    @Test fun sourcePositionAndPendingSeekAreProjectedWithoutCumulativeFrameConfusion() {
        val direct = state(IntegratedDirectPhase.SEEKING).copy(completedFrames = 480000,
            totalFrames = 480000, sampleRateHz = 48000, sourcePositionFrame = 48000, pendingSeekSerial = 7)
        val pending = directPlaybackProgress(direct)
        assertEquals(1000, pending.currentPosition)
        assertTrue(pending.isSeeking)
        assertTrue(pending.isDirect)
        val confirmed = directPlaybackProgress(direct.copy(phase = IntegratedDirectPhase.PAUSED, sourcePositionFrame = 0))
        assertEquals(0, confirmed.currentPosition)
        assertFalse(confirmed.isSeeking)
        assertEquals(10000, confirmed.duration)
    }

    private fun project(direct: IntegratedDirectPlaybackState) = directPlaybackUiState(
        PlaybackUiState(currentSong = androidSong), direct, listOf(androidSong, nativeSong))
    private fun state(phase: IntegratedDirectPhase) = IntegratedDirectPlaybackState(
        enabled = true, normalReleased = true, phase = phase, libraryId = nativeSong.id)
    private fun song(id: Long) = Song(id, "Song $id", "Artist", "Album", 1, 10000,
        mock(Uri::class.java), "/music/$id.flac", "/music", null)
}
