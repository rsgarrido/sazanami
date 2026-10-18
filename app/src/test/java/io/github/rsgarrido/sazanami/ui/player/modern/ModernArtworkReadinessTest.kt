package io.github.rsgarrido.sazanami.ui.player.modern

import android.net.Uri
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.data.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock

class ModernArtworkReadinessTest {
    @Test
    fun wideArtworkUsesTheExistingBoundedPreloadPolicyBeforeAnchorsRefresh() {
        for (size in ModernArtworkSize.entries) {
            val appearance = ModernPlayerAppearance.Default.copy(artwork = ModernArtworkAppearance(size = size))
            val layout = resolveDefaultPlayerLayout(1280.dp, 500.dp, appearance = appearance,
                preferredLandscapeLayout = ModernLandscapeLayout.WIDE_WAVEFORM)
            assertEquals(DefaultPlayerArrangement.WideWaveform, layout.arrangement)
            val policy = modernArtworkPreloadPolicy(2560, 1000, 2f, appearance, layout)
            assertEquals((layout.artworkSize.value * 2f).toInt(), policy.targetSizePx)
            assertTrue(policy.exactSize)
            assertTrue(policy.targetSizePx!! <= MAX_MODERN_ARTWORK_PRELOAD_TARGET_PX)
        }
    }

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
    fun balancedPreloadUsesTheDestinationArtworkSizeAndExistingPhysicalSizeCacheKey() {
        val appearance = ModernPlayerAppearance.Default.copy(
            artwork = ModernArtworkAppearance(size = ModernArtworkSize.LARGE)
        )
        val layout = resolveDefaultPlayerLayout(1280.dp, 800.dp, appearance = appearance)
        val policy = modernArtworkPreloadPolicy(
            viewportWidthPx = 3200,
            viewportHeightPx = 2000,
            density = 2.5f,
            appearance = appearance,
            playerLayout = layout
        )
        val oldVerticalBudget = modernArtworkPreloadPolicy(3200, 2000, 2.5f, appearance)

        assertEquals(DefaultPlayerArrangement.Balanced, layout.arrangement)
        assertEquals(1180, policy.targetSizePx)
        assertTrue(policy.exactSize)
        assertTrue(requireNotNull(policy.targetSizePx) > requireNotNull(oldVerticalBudget.targetSizePx))
        assertEquals(
            "modern-expanded:1180:content://artwork/current",
            modernExpandedArtworkMemoryCacheKey("content://artwork/current", requireNotNull(policy.targetSizePx))
        )
    }

    @Test
    fun resizingCanResolveANewPreloadTargetBeforeDestinationAnchorsExist() {
        val appearance = ModernPlayerAppearance.Default.copy(
            artwork = ModernArtworkAppearance(size = ModernArtworkSize.LARGE)
        )
        val wide = resolveDefaultPlayerLayout(1280.dp, 800.dp, appearance = appearance)
        val shortWithTaskbar = resolveDefaultPlayerLayout(
            1280.dp, 520.dp,
            insets = DefaultPlayerInsets(top = 24.dp, bottom = 48.dp),
            appearance = appearance
        )
        val portrait = resolveDefaultPlayerLayout(412.dp, 850.dp, appearance = appearance)
        // Resolve from each replacement layout, without consulting any previous measured anchor.
        val targets = listOf(
            modernArtworkPreloadPolicy(2560, 1600, 2f, appearance, wide),
            modernArtworkPreloadPolicy(2560, 1040, 2f, appearance, shortWithTaskbar),
            modernArtworkPreloadPolicy(824, 1700, 2f, appearance, portrait)
        )

        assertEquals(DefaultPlayerArrangement.Balanced, shortWithTaskbar.arrangement)
        assertEquals(DefaultPlayerArrangement.Vertical, portrait.arrangement)
        assertEquals(listOf(944, 848, 760), targets.map { it.targetSizePx })
        assertTrue(targets.all { it.exactSize })
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
