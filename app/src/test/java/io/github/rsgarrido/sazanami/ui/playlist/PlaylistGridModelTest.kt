package io.github.rsgarrido.sazanami.ui.playlist

import io.github.rsgarrido.sazanami.data.Playlist
import io.github.rsgarrido.sazanami.data.PlaylistMembershipBehavior
import io.github.rsgarrido.sazanami.data.PlaylistType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistGridModelTest {
    @Test
    fun generatedPlaylistMetadataUsesCompactRelativeTime() {
        val now = 10_000_000L
        val playlist = Playlist(
            playlistId = 1L,
            name = "Heavy Rotation",
            songCount = 20,
            type = PlaylistType.SMART,
            membershipBehavior = PlaylistMembershipBehavior.GENERATED_SMART_SNAPSHOT,
            generatedLastRefreshedAt = now - 61_000L
        )

        assertEquals("Smart \u2022 Updated 1m", playlistGridMetadataText(playlist, now))
    }

    @Test
    fun gridTilesHaveOneSharedFootprintModel() {
        assertTrue(PlaylistGridLayout.minimumTileWidth.value >= 148f)
        assertTrue(PlaylistGridLayout.metadataHeight.value > 0f)
        assertEquals(2, PlaylistGridLayout.titleMaxLines)
        assertEquals(1, PlaylistGridLayout.secondaryMaxLines)
    }
}
