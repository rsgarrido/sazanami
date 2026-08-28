package com.example.cdplaya.ui.player.modern

import android.net.Uri
import com.example.cdplaya.data.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock

class ModernArtworkReadinessTest {
    @Test
    fun phoneSizedPreloadIsExpandedExactBoundedAndNeverOriginalSize() {
        val policy = modernArtworkPreloadPolicy(
            viewportWidthPx = 1_080,
            viewportHeightPx = 2_400,
            density = 3f,
            appearance = ModernPlayerAppearance.Default
        )

        assertTrue(requireNotNull(policy.targetSizePx) > MODERN_MINI_ARTWORK_REFERENCE_DP * 3f)
        assertTrue(requireNotNull(policy.targetSizePx) <= MAX_MODERN_ARTWORK_PRELOAD_TARGET_PX)
        assertTrue(policy.exactSize)
    }

    @Test
    fun preloadTargetRespectsConfiguredArtworkSize() {
        fun target(size: ModernArtworkSize): Int = requireNotNull(
            modernArtworkPreloadPolicy(
                viewportWidthPx = 1_600,
                viewportHeightPx = 2_560,
                density = 2f,
                appearance = ModernPlayerAppearance.Default.copy(
                    artwork = ModernArtworkAppearance(size = size)
                )
            ).targetSizePx
        )

        assertTrue(target(ModernArtworkSize.COMPACT) < target(ModernArtworkSize.STANDARD))
        assertTrue(target(ModernArtworkSize.STANDARD) < target(ModernArtworkSize.LARGE))
    }

    @Test
    fun invalidViewportDoesNotRequestAnOriginalOrUnboundedDecode() {
        val policy = modernArtworkPreloadPolicy(
            viewportWidthPx = 1,
            viewportHeightPx = 1,
            density = 3f,
            appearance = ModernPlayerAppearance.Default
        )

        assertNull(policy.targetSizePx)
        assertFalse(policy.exactSize)
    }

    @Test
    fun currentAndImmediateNeighborsAreTheOnlyPreloadCandidates() {
        val current = song(2, "content://artwork/current")
        val previous = song(1, "content://artwork/previous")
        val next = song(3, "content://artwork/next")

        val selected = selectModernArtworkPreloadSongs(current, previous, next)

        assertEquals(listOf(2L, 3L, 1L), selected.map(Song::id))
        assertTrue(selected.size <= MAX_MODERN_ARTWORK_PRELOAD_COUNT)
    }

    @Test
    fun preloadCandidatesAreBoundedByDistinctArtworkIdentity() {
        val current = song(2, "content://artwork/shared")
        val previous = song(1, "content://artwork/previous")
        val nextWithSameArtwork = song(3, "content://artwork/shared")

        val selected = selectModernArtworkPreloadSongs(
            current,
            previous,
            nextWithSameArtwork
        )

        assertEquals(listOf(2L, 1L), selected.map(Song::id))
    }

    @Test
    fun activeMorphOwnsCurrentRequestWhilePreloaderOnlyWarmsNeighbors() {
        val current = song(2, "content://artwork/current")
        val previous = song(1, "content://artwork/previous")
        val next = song(3, "content://artwork/next")

        val selected = selectModernArtworkPreloadSongs(
            currentSong = current,
            previousSong = previous,
            nextSong = next,
            includeCurrentSong = false
        )

        assertEquals(listOf(3L, 1L), selected.map(Song::id))
    }

    @Test
    fun missingArtworkUsesPlaceholderUntilAValidCurrentLayerExists() {
        val state = ModernArtworkReadinessState<String>(currentArtworkIdentity = null)

        assertNull(preferredModernArtworkReadyLayer(state))
    }

    @Test
    fun temporaryLayerIsVisibleUntilExpandedLayerReplacesIt() {
        val initial = ModernArtworkReadinessState<String>(
            currentArtworkIdentity = "artwork-b"
        )
        val temporary = acceptModernArtworkReadyLayer(
            initial,
            ModernArtworkReadyLayer(
                artworkIdentity = "artwork-b",
                quality = ModernArtworkQuality.Temporary,
                value = "mini-b"
            )
        )
        val expanded = acceptModernArtworkReadyLayer(
            temporary,
            ModernArtworkReadyLayer(
                artworkIdentity = "artwork-b",
                quality = ModernArtworkQuality.Expanded,
                value = "expanded-b"
            )
        )

        assertEquals("mini-b", preferredModernArtworkReadyLayer(temporary)?.value)
        assertEquals("expanded-b", preferredModernArtworkReadyLayer(expanded)?.value)
    }

    @Test
    fun rapidSongChangesRejectStaleTemporaryAndExpandedResults() {
        val currentA = acceptModernArtworkReadyLayer(
            ModernArtworkReadinessState<String>(currentArtworkIdentity = "artwork-a"),
            ModernArtworkReadyLayer(
                artworkIdentity = "artwork-a",
                quality = ModernArtworkQuality.Expanded,
                value = "expanded-a"
            )
        )
        val currentB = ModernArtworkReadinessState<String>(
            currentArtworkIdentity = "artwork-b"
        )
        val currentC = ModernArtworkReadinessState<String>(
            currentArtworkIdentity = "artwork-c"
        )
        val staleTemporaryB = acceptModernArtworkReadyLayer(
            currentC,
            ModernArtworkReadyLayer(
                artworkIdentity = "artwork-b",
                quality = ModernArtworkQuality.Temporary,
                value = "mini-b"
            )
        )
        val staleExpandedB = acceptModernArtworkReadyLayer(
            staleTemporaryB,
            ModernArtworkReadyLayer(
                artworkIdentity = "artwork-b",
                quality = ModernArtworkQuality.Expanded,
                value = "expanded-b"
            )
        )
        val resolvedC = acceptModernArtworkReadyLayer(
            staleExpandedB,
            ModernArtworkReadyLayer(
                artworkIdentity = "artwork-c",
                quality = ModernArtworkQuality.Expanded,
                value = "expanded-c"
            )
        )

        assertEquals("expanded-a", preferredModernArtworkReadyLayer(currentA)?.value)
        assertNull(preferredModernArtworkReadyLayer(currentB))
        assertEquals(currentC, staleTemporaryB)
        assertEquals(currentC, staleExpandedB)
        assertEquals("expanded-c", preferredModernArtworkReadyLayer(resolvedC)?.value)
    }

    private fun song(id: Long, artworkIdentity: String): Song {
        val songUri = mock(Uri::class.java)
        val artworkUri = mock(Uri::class.java)
        doReturn(artworkIdentity).`when`(artworkUri).toString()
        return Song(
            id = id,
            title = "Song $id",
            artist = "Artist",
            album = "Album",
            trackNumber = 1,
            duration = 1_000,
            uri = songUri,
            filePath = "Music/$id.flac",
            folderPath = "Music",
            albumArtUri = artworkUri
        )
    }
}
