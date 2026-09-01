package io.github.rsgarrido.sazanami.ui.library

import io.github.rsgarrido.sazanami.data.Playlist
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistQueueActionsTest {
    @Test
    fun playNextIsPresentAndUsesTheSharedPlaylistQueueCallback() {
        val playlist = Playlist(playlistId = 4L, name = "Road Trip", songCount = 3)
        var playedNext: Playlist? = null
        val actions = playlistQueueActions(
            playlist = playlist,
            queueUi = LibraryQueueUiEnvironment(
                onPlayPlaylistNext = { playedNext = it }
            ),
            onAddToQueue = {}
        )

        assertEquals(
            listOf("Play next", "Add to queue", "Add to another queue...", "Play in new queue"),
            actions.map { action -> action.label }
        )
        actions.first().onClick()
        assertEquals(playlist, playedNext)
    }
}
