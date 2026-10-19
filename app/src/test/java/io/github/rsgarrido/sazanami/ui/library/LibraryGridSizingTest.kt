package io.github.rsgarrido.sazanami.ui.library

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryGridSizingTest {
    @Test
    fun exactCountPreferencesKeepTheirPhoneFootprintAndBoundWideArtwork() {
        for (preference in LibraryGridColumns.supported) {
            val spacing = when (preference) { 2 -> 12.dp; 3 -> 10.dp; else -> 8.dp }
            val cap = exactLibraryGridMaximumWidth(preference, spacing, spacing)
            assertTrue(cap > 412.dp)
            for (width in listOf(360.dp, 412.dp, 800.dp, 1200.dp, 2400.dp)) {
                val gridWidth = minOf(width, cap)
                val tile = (gridWidth - spacing * 2 - spacing * (preference - 1)) / preference
                assertTrue(tile > 0.dp && tile <= 256.dp)
                if (width <= 412.dp) assertEquals(width, gridWidth)
            }
        }
    }

    @Test
    fun exactViewOptionsRetainTheirContractAndPersistedNormalization() {
        assertEquals(listOf(2, 3, 4, 5), libraryViewOptions(false).mapNotNull { it.gridColumnCount })
        for (count in 2..5) assertEquals(count, LibraryGridColumns.normalize(count))
        assertEquals(LibraryGridColumns.DEFAULT, LibraryGridColumns.normalize(9))
        // Responsive playlists already expose one responsive option, not a promised exact count.
        assertEquals(1, libraryViewOptions(true).count { it.viewMode == LibraryViewMode.GRID })
    }

    @Test
    fun artistAlbumsUseAvailablePaneWidthWithNoFiveColumnNormalization() {
        assertEquals(2, artistAlbumGridColumns(412.dp))
        assertEquals(2, artistAlbumGridColumns(599.dp))
        assertEquals(3, artistAlbumGridColumns(600.dp))
        val wideCount = artistAlbumGridColumns(1400.dp)
        assertTrue(wideCount > 5)
        val tile = (1400.dp - 32.dp - 12.dp * (wideCount - 1)) / wideCount
        assertTrue(tile >= 160.dp && tile < 200.dp)
        assertTrue(artistAlbumGridColumns(2400.dp) > wideCount)
        assertEquals(2, artistAlbumGridColumns(412.dp)) // Returning to compact is deterministic.
    }

    @Test
    fun largeTextReducesAutomaticDensityWhileCompactKeepsItsExistingCount() {
        val normal = artistAlbumGridColumns(1100.dp, 1f)
        val large = artistAlbumGridColumns(1100.dp, 2f)
        assertTrue(large < normal)
        assertTrue((1100.dp - 32.dp - 12.dp * (large - 1)) / large >= 320.dp)
        assertEquals(2, artistAlbumGridColumns(412.dp, 2f))
    }

    @Test
    fun automaticCollectionMinimumsScaleForWideLargeTextWithoutChangingCompactDensity() {
        for (minimum in listOf(148.dp, 160.dp)) {
            assertEquals(minimum, collectionGridMinimumWidth(599.dp, minimum, 2f))
            assertEquals(minimum * 2, collectionGridMinimumWidth(600.dp, minimum, 2f))
            assertEquals(minimum, collectionGridMinimumWidth(1100.dp, minimum, 0.85f))
            assertTrue(collectionGridMinimumWidth(1100.dp, minimum, 2f) >
                collectionGridMinimumWidth(1100.dp, minimum, 1.5f))
        }
    }

    @Test
    fun invalidConstraintsNeverGenerateAnInvalidAutomaticCount() {
        for (width in listOf(0.dp, (-20).dp, Dp.Unspecified, Dp.Infinity)) {
            assertEquals(2, artistAlbumGridColumns(width))
        }
    }
}
