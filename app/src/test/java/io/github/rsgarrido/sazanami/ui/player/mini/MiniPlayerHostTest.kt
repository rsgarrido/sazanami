package io.github.rsgarrido.sazanami.ui.player.mini

import android.net.Uri
import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.data.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class MiniPlayerHostTest {
    @Test
    fun defaultThemeUsesModernMiniPlayer() {
        assertEquals(
            MiniPlayerVariant.MODERN,
            miniPlayerVariantFor(PlayerTheme.DEFAULT)
        )
    }

    @Test
    fun everyRetroThemeUsesItsOwnMiniPlayerVariant() {
        val expected = mapOf(
            PlayerTheme.CLASSIC_WHEEL to MiniPlayerVariant.CLASSIC_WHEEL,
            PlayerTheme.POCKET_CASSETTE to MiniPlayerVariant.POCKET_CASSETTE,
            PlayerTheme.POCKET_FLIP to MiniPlayerVariant.POCKET_FLIP,
            PlayerTheme.RETRO_RACK to MiniPlayerVariant.RETRO_RACK
        )

        expected.forEach { (theme, variant) ->
            assertEquals(variant, miniPlayerVariantFor(theme))
        }
        assertEquals(expected.size, expected.values.toSet().size)
        assertTrue(expected.values.none { it == MiniPlayerVariant.MODERN })
    }

    @Test
    fun progressIsClampedAndHandlesMissingDuration() {
        assertEquals(0f, normalizedMiniPlayerProgress(10, 0), 0f)
        assertEquals(0f, normalizedMiniPlayerProgress(-10, 100), 0f)
        assertEquals(0.5f, normalizedMiniPlayerProgress(50, 100), 0f)
        assertEquals(1f, normalizedMiniPlayerProgress(150, 100), 0f)
    }

    @Test
    fun sharedCallbacksRemainAvailableToEveryVariant() {
        val events = mutableListOf<String>()
        val callbacks = MiniPlayerCallbacks(
            onPlayPauseClick = { events += "playPause" },
            onPreviousClick = { events += "previous" },
            onNextClick = { events += "next" },
            onQueueHubClick = { events += "queues" },
            onExpandClick = { events += "expand" }
        )

        callbacks.onPlayPauseClick()
        callbacks.onPreviousClick()
        callbacks.onNextClick()
        callbacks.onQueueHubClick()
        callbacks.onExpandClick()

        assertEquals(listOf("playPause", "previous", "next", "queues", "expand"), events)
    }

    @Test
    fun accessibilityLabelsDescribeOpenAndPlaybackActions() {
        val song = Song(
            id = 1L,
            title = "",
            artist = "",
            album = "",
            trackNumber = 1,
            duration = 0L,
            uri = mock(Uri::class.java),
            filePath = "song.mp3",
            folderPath = "",
            albumArtUri = null
        )

        assertEquals("Open player for Unknown Title", miniPlayerOpenContentDescription(song))
        assertEquals("Play", miniPlayerPlaybackContentDescription(isPlaying = false))
        assertEquals("Pause", miniPlayerPlaybackContentDescription(isPlaying = true))
    }
}
