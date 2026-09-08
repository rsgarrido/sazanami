package io.github.rsgarrido.sazanami.widget

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.player.ListeningMediaItemMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class NowPlayingWidgetFoundationTest {
    @Test
    fun media3Player_mapsDirectlyToWidgetSnapshot() {
        val extras = Bundle().apply {
            putString(ListeningMediaItemMetadata.ITEM_INSTANCE_ID, "entry-9")
        }
        val mediaMetadata = MediaMetadata.Builder()
            .setTitle("Direct title")
            .setArtist("Direct artist")
            .setArtworkUri(Uri.parse("content://item/raw/song-9"))
            .setExtras(extras)
            .build()
        val item = MediaItem.Builder()
            .setMediaId("song-9")
            .setMediaMetadata(mediaMetadata)
            .build()
        val sessionMetadata = mediaMetadata.buildUpon()
            .setArtworkUri(Uri.parse("content://session/art/song-9"))
            .build()
        val player = Mockito.mock(Player::class.java)
        Mockito.`when`(player.currentMediaItem).thenReturn(item)
        Mockito.`when`(player.mediaMetadata).thenReturn(sessionMetadata)
        Mockito.`when`(player.isPlaying).thenReturn(true)
        Mockito.`when`(player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS)).thenReturn(true)
        Mockito.`when`(player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)).thenReturn(true)

        val snapshot = player.toNowPlayingWidgetSnapshot()

        assertEquals("song-9|entry-9", snapshot.mediaIdentity)
        assertEquals("Direct title", snapshot.title)
        assertEquals("Direct artist", snapshot.artist)
        assertEquals("content://session/art/song-9", snapshot.artworkUri)
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
        assertTrue(snapshot.isPlaying)
        assertTrue(snapshot.canPrevious)
        assertTrue(snapshot.canPlayPause)
        assertTrue(snapshot.canNext)
    }

    @Test
    fun artworkUri_prefersNormalizedSessionMetadataAndFallsBackToMediaItem() {
        assertEquals(
            "content://session/art",
            resolveWidgetArtworkUri("content://item/art", "content://session/art")
        )
        assertEquals(
            "content://item/art",
            resolveWidgetArtworkUri("content://item/art", null)
        )
        assertNull(resolveWidgetArtworkUri("", null))
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
    fun livePresentation_updatesAcrossPlayPauseAndBufferingStates() {
        assertTrue(shouldWidgetShowPause(true, playWhenReady = true, Player.STATE_READY))
        assertTrue(shouldWidgetShowPause(false, playWhenReady = true, Player.STATE_BUFFERING))
        assertFalse(shouldWidgetShowPause(false, playWhenReady = false, Player.STATE_READY))
        assertFalse(shouldWidgetShowPause(false, playWhenReady = true, Player.STATE_ENDED))
    }

    @Test
    fun publisherPublishesMediaTransitionsMetadataArtworkAndPlaybackChanges() {
        val deduplicator = WidgetSnapshotDeduplicator()
        val commonPeople = playerState(
            mediaId = "common-people",
            title = "Common People",
            artworkUri = "content://art/common"
        ).toSnapshot()
        val nextSong = playerState(
            mediaId = "next-song",
            itemInstanceId = "queue-entry-8",
            title = "Next Song",
            artworkUri = "content://art/next"
        ).toSnapshot()
        val correctedMetadata = playerState(
            mediaId = "next-song",
            itemInstanceId = "queue-entry-8",
            title = "Next Song (Remastered)",
            artworkUri = "content://art/next-remastered"
        ).toSnapshot()
        val playing = correctedMetadata.copy(isPlaying = true)

        assertTrue(deduplicator.shouldPublish(commonPeople))
        assertTrue(deduplicator.shouldPublish(nextSong))
        assertEquals("content://art/next", nextSong.artworkUri)
        assertTrue(deduplicator.shouldPublish(correctedMetadata))
        assertTrue(deduplicator.shouldPublish(playing))
        assertFalse(deduplicator.shouldPublish(playing))
    }

    @Test
    fun restorationRetainsColdPresentationUntilEmptyTimelineIsAuthoritative() {
        val lastPresentation = playerState(title = "Last Song").toSnapshot().asColdSnapshot()
        val deduplicator = WidgetSnapshotDeduplicator()
        assertTrue(deduplicator.shouldPublish(lastPresentation))
        assertEquals(
            WidgetEmptyStateDisposition.RETAIN_LAST_PRESENTATION,
            widgetEmptyStateDisposition(
                NowPlayingWidgetSnapshot.EMPTY,
                restorationComplete = false
            )
        )
        assertEquals(
            WidgetEmptyStateDisposition.PUBLISH,
            widgetEmptyStateDisposition(
                NowPlayingWidgetSnapshot.EMPTY,
                restorationComplete = true
            )
        )
        assertTrue(deduplicator.shouldPublish(NowPlayingWidgetSnapshot.EMPTY))
    }

    @Test
    fun restoredSessionReplacesColdPresentationWithLiveSnapshot() {
        val cold = playerState(
            mediaId = "old-song",
            title = "Old Song",
            isPlaying = true
        ).toSnapshot().asColdSnapshot()
        val restored = playerState(
            mediaId = "restored-song",
            itemInstanceId = "restored-entry",
            title = "Restored Song",
            isPlaying = true
        ).toSnapshot()
        val deduplicator = WidgetSnapshotDeduplicator()

        assertFalse(cold.isPlaying)
        assertEquals(
            WidgetEmptyStateDisposition.PUBLISH,
            widgetEmptyStateDisposition(restored, restorationComplete = false)
        )
        assertTrue(deduplicator.shouldPublish(cold))
        assertTrue(deduplicator.shouldPublish(restored))
        assertEquals("Restored Song", restored.title)
        assertTrue(restored.isPlaying)
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
        assertTrue(isMeaningfulWidgetPlayerEvent(Player.EVENT_MEDIA_METADATA_CHANGED))
        assertTrue(isMeaningfulWidgetPlayerEvent(Player.EVENT_TIMELINE_CHANGED))
        assertTrue(isMeaningfulWidgetPlayerEvent(Player.EVENT_PLAY_WHEN_READY_CHANGED))
        assertTrue(isMeaningfulWidgetPlayerEvent(Player.EVENT_PLAYBACK_STATE_CHANGED))
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
    fun rendererSelectsOnlyNormalizedAppOwnedContentUris() {
        val packageName = "io.github.rsgarrido.sazanami"
        val embedded = widgetHostArtworkUri(
            packageName,
            "content://$packageName.embeddedartwork/v2/source/art.png"
        )
        val visual = widgetHostArtworkUri(
            packageName,
            "content://$packageName.visualassets/library-artwork/art.webp"
        )

        assertEquals(
            "content://$packageName.embeddedartwork/v2/source/art.png",
            embedded.toString()
        )
        assertEquals(
            "content://$packageName.visualassets/library-artwork/art.webp",
            visual.toString()
        )
        assertEquals(
            WidgetArtworkPresentation.ARTWORK,
            widgetArtworkPresentation(artworkUriAvailable = true)
        )
        assertNull(widgetHostArtworkUri(packageName, null))
        assertNull(widgetHostArtworkUri(packageName, "file:///private/art.webp"))
        assertNull(widgetHostArtworkUri(packageName, "content://other.provider/art.webp"))
        assertEquals(
            WidgetArtworkPresentation.PLACEHOLDER,
            widgetArtworkPresentation(artworkUriAvailable = false)
        )
    }

    @Test
    fun mediaTransitionDoesNotCarryPreviousArtwork() {
        val previous = playerState(
            mediaId = "item-a",
            artworkUri = "content://sazanami.visualassets/art-a"
        ).toSnapshot()
        val current = playerState(mediaId = "item-b", artworkUri = null).toSnapshot()

        assertEquals("content://sazanami.visualassets/art-a", previous.artworkUri)
        assertNull(current.artworkUri)
    }

    @Test
    fun normalizedArtworkPublicationTriggersExactlyOneAdditionalRefresh() {
        val deduplicator = WidgetSnapshotDeduplicator()
        var refreshes = 0
        fun publish(snapshot: NowPlayingWidgetSnapshot) {
            if (deduplicator.shouldPublish(snapshot)) refreshes++
        }
        val notReady = playerState(
            mediaId = "item-a",
            artworkUri = null
        ).toSnapshot()
        val completed = notReady.copy(
            artworkUri = "content://io.github.rsgarrido.sazanami.visualassets/art-a"
        )

        publish(notReady)
        assertEquals(1, refreshes)
        publish(completed)
        assertEquals(2, refreshes)
        publish(completed)
        assertEquals(2, refreshes)
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
