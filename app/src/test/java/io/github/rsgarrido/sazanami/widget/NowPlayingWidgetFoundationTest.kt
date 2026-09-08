package io.github.rsgarrido.sazanami.widget

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.player.ListeningMediaItemMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class NowPlayingWidgetFoundationTest {
    @Test
    fun media3Player_mapsDirectlyToWidgetSnapshot() {
        val extras = Bundle().apply {
            putString(ListeningMediaItemMetadata.ITEM_INSTANCE_ID, "entry-9")
        }
        val item = MediaItem.Builder()
            .setMediaId("song-9")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Direct title")
                    .setArtist("Direct artist")
                    .setExtras(extras)
                    .build()
            )
            .build()
        val player = Mockito.mock(Player::class.java)
        Mockito.`when`(player.currentMediaItem).thenReturn(item)
        Mockito.`when`(player.isPlaying).thenReturn(true)
        Mockito.`when`(player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS)).thenReturn(true)
        Mockito.`when`(player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)).thenReturn(true)

        val snapshot = player.toNowPlayingWidgetSnapshot()

        assertEquals("song-9|entry-9", snapshot.mediaIdentity)
        assertEquals("Direct title", snapshot.title)
        assertEquals("Direct artist", snapshot.artist)
        assertTrue(snapshot.isPlaying)
        assertTrue(snapshot.canPrevious)
        assertTrue(snapshot.canPlayPause)
    }

    @Test
    fun playerProjection_mapsMetadataIdentityArtworkAndAvailability() {
        val snapshot = playerState(
            mediaId = "42",
            itemInstanceId = "queue-entry-7",
            title = "Sazanami",
            artist = "Rin",
            artworkUri = "content://art/42",
            isPlaying = true,
            hasNextItem = true
        ).toSnapshot()

        assertEquals("42|queue-entry-7", snapshot.mediaIdentity)
        assertEquals("Sazanami", snapshot.title)
        assertEquals("Rin", snapshot.artist)
        assertEquals("content://art/42", snapshot.artworkUri)
        assertTrue(snapshot.artworkCacheIdentity?.isNotBlank() == true)
        assertTrue(snapshot.isPlaying)
        assertTrue(snapshot.canPrevious)
        assertTrue(snapshot.canPlayPause)
        assertTrue(snapshot.canNext)
    }

    @Test
    fun playerProjection_emptyItemProducesExplicitEmptyState() {
        val snapshot = playerState(mediaId = "", itemInstanceId = null).toSnapshot()

        assertSame(NowPlayingWidgetSnapshot.EMPTY, snapshot)
        assertFalse(snapshot.hasMedia)
    }

    @Test
    fun playerProjection_blankMetadataUsesStableFallbackLabels() {
        val snapshot = playerState(title = "  ", artist = null).toSnapshot()

        assertEquals("Unknown title", snapshot.title)
        assertEquals("Unknown artist", snapshot.artist)
    }

    @Test
    fun playerProjection_reflectsPlaybackAndCommandAvailabilityTransitions() {
        val initial = playerState(isPlaying = false, hasNextItem = false).toSnapshot()
        val changed = playerState(
            isPlaying = true,
            previousCommandAvailable = false,
            playPauseCommandAvailable = false,
            hasNextItem = true
        ).toSnapshot()

        assertFalse(initial.isPlaying)
        assertFalse(initial.canNext)
        assertTrue(changed.isPlaying)
        assertFalse(changed.canPrevious)
        assertFalse(changed.canPlayPause)
        assertTrue(changed.canNext)
    }

    @Test
    fun coldSnapshot_neverClaimsPlaybackIsActive() {
        val cold = playerState(isPlaying = true).toSnapshot().asColdSnapshot()

        assertFalse(cold.isPlaying)
    }

    @Test
    fun coalescer_collapsesRedundantPendingEvents() {
        val scheduled = mutableListOf<() -> Unit>()
        var updates = 0
        val coalescer = WidgetUpdateCoalescer(
            scheduler = WidgetUpdateScheduler(scheduled::add),
            update = { updates++ }
        )

        repeat(5) { coalescer.request() }
        assertEquals(1, scheduled.size)
        assertEquals(0, updates)
        scheduled.removeFirst().invoke()
        assertEquals(1, updates)

        coalescer.request()
        assertEquals(1, scheduled.size)
    }

    @Test
    fun publisherFilter_ignoresPositionOnlyEvents() {
        assertTrue(isMeaningfulWidgetPlayerEvent(Player.EVENT_MEDIA_ITEM_TRANSITION))
        assertTrue(isMeaningfulWidgetPlayerEvent(Player.EVENT_TIMELINE_CHANGED))
        assertFalse(isMeaningfulWidgetPlayerEvent(Player.EVENT_POSITION_DISCONTINUITY))
    }

    @Test
    fun playPause_samplesLivePlayWhenReadyAtClickTime() {
        val target = FakeCommandTarget(playWhenReady = false)
        assertTrue(dispatchWidgetCommand(target, WidgetPlaybackCommand.PLAY_PAUSE))
        assertEquals(1, target.playCalls)

        target.playWhenReady = true
        assertTrue(dispatchWidgetCommand(target, WidgetPlaybackCommand.PLAY_PAUSE))
        assertEquals(1, target.pauseCalls)
    }

    @Test
    fun previousAndNext_routeOnlyThroughSessionCommandTarget() {
        val target = FakeCommandTarget(playWhenReady = false)

        assertTrue(dispatchWidgetCommand(target, WidgetPlaybackCommand.PREVIOUS))
        assertTrue(dispatchWidgetCommand(target, WidgetPlaybackCommand.NEXT))
        assertEquals(1, target.previousCalls)
        assertEquals(1, target.nextCalls)
    }

    @Test
    fun commandFailsSafelyUntilCurrentTimelineItemIsUsable() {
        val target = FakeCommandTarget(playWhenReady = false, hasUsableCurrentItem = false)

        assertFalse(dispatchWidgetCommand(target, WidgetPlaybackCommand.PLAY_PAUSE))
        assertEquals(0, target.playCalls)
        assertEquals(0, target.pauseCalls)
    }

    @Test
    fun commandFailsSafelyWhenSessionCommandIsUnavailable() {
        val target = FakeCommandTarget(playWhenReady = false, commandsAvailable = false)

        assertFalse(dispatchWidgetCommand(target, WidgetPlaybackCommand.PREVIOUS))
        assertFalse(dispatchWidgetCommand(target, WidgetPlaybackCommand.PLAY_PAUSE))
        assertFalse(dispatchWidgetCommand(target, WidgetPlaybackCommand.NEXT))
        assertEquals(0, target.previousCalls + target.playCalls + target.nextCalls)
    }

    @Test
    fun readiness_waitsForRestoredItemAndTimesOutWithoutOne() {
        val restored = WidgetPlaybackReadiness(hasCurrentItem = false)
        assertEquals(WidgetReadiness.WAITING, restored.state)
        assertEquals(WidgetReadiness.READY, restored.onPlayerState(hasCurrentItem = true))

        val unavailable = WidgetPlaybackReadiness(hasCurrentItem = false)
        assertEquals(WidgetReadiness.TIMED_OUT, unavailable.onTimeout())
        assertEquals(WidgetReadiness.TIMED_OUT, unavailable.onPlayerState(hasCurrentItem = true))
    }

    @Test
    fun artworkDecode_isBoundedAndMissingOrRevokedUsesPlaceholder() {
        assertEquals(16, calculateWidgetArtworkSampleSize(8_000, 4_000))
        assertEquals(1, calculateWidgetArtworkSampleSize(384, 384))
        assertEquals(256 to 128, calculateWidgetArtworkTargetSize(4_000, 2_000))
        assertEquals(
            WidgetArtworkPresentation.PLACEHOLDER,
            widgetArtworkPresentation(decodedArtworkAvailable = false)
        )
        assertEquals(
            WidgetArtworkPresentation.ARTWORK,
            widgetArtworkPresentation(decodedArtworkAvailable = true)
        )
    }

    @Test
    fun lateArtworkResult_isRejectedAfterMediaTransition() {
        assertTrue(isWidgetArtworkResultCurrent("item-a", "item-a"))
        assertFalse(isWidgetArtworkResultCurrent("item-a", "item-b"))
    }

    @Test
    fun responsiveLayout_selectsCompactAndStandardPresentations() {
        assertEquals(NowPlayingWidgetLayout.COMPACT, nowPlayingWidgetLayoutFor(56f))
        assertEquals(NowPlayingWidgetLayout.STANDARD, nowPlayingWidgetLayoutFor(120f))
        assertEquals(R.string.widget_play, widgetPlayPauseDescriptionResource(isPlaying = false))
        assertEquals(R.string.widget_pause, widgetPlayPauseDescriptionResource(isPlaying = true))
    }

    private fun playerState(
        mediaId: String = "42",
        itemInstanceId: String? = "queue-entry-7",
        title: String? = "Title",
        artist: String? = "Artist",
        artworkUri: String? = null,
        isPlaying: Boolean = false,
        previousCommandAvailable: Boolean = true,
        nextCommandAvailable: Boolean = true,
        playPauseCommandAvailable: Boolean = true,
        hasNextItem: Boolean = false
    ) = WidgetPlayerState(
        mediaId = mediaId,
        itemInstanceId = itemInstanceId,
        title = title,
        artist = artist,
        artworkUri = artworkUri,
        isPlaying = isPlaying,
        previousCommandAvailable = previousCommandAvailable,
        nextCommandAvailable = nextCommandAvailable,
        playPauseCommandAvailable = playPauseCommandAvailable,
        hasNextItem = hasNextItem
    )

    private class FakeCommandTarget(
        override var playWhenReady: Boolean,
        override val hasUsableCurrentItem: Boolean = true,
        private val commandsAvailable: Boolean = true
    ) : WidgetCommandTarget {
        var playCalls = 0
        var pauseCalls = 0
        var previousCalls = 0
        var nextCalls = 0

        override fun isCommandAvailable(command: Int): Boolean = commandsAvailable && when (command) {
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_PLAY_PAUSE -> true
            else -> false
        }

        override fun play() { playCalls++ }
        override fun pause() { pauseCalls++ }
        override fun seekToPrevious() { previousCalls++ }
        override fun seekToNextMediaItem() { nextCalls++ }
    }
}
