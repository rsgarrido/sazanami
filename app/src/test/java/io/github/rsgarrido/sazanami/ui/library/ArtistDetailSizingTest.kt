package io.github.rsgarrido.sazanami.ui.library

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistDetailSizingTest {
    @Test
    fun phoneWidthUsesSeventyPercentOfAvailableContentWidth() {
        assertEquals(252.dp, artistDetailHeroImageSize(360.dp))
    }

    @Test
    fun tabletWidthRespectsResponsiveMaximum() {
        assertEquals(432.dp, artistDetailHeroImageSize(800.dp))
    }

    @Test
    fun resultNeverExceedsAvailableWidth() {
        listOf(1.dp, 120.dp, 360.dp, 800.dp).forEach { availableWidth ->
            assertTrue(artistDetailHeroImageSize(availableWidth) <= availableWidth)
        }
    }

    @Test
    fun invalidOrTinyConstraintsRemainSafe() {
        assertEquals(0.dp, artistDetailHeroImageSize((-10).dp))
        assertEquals(0.dp, artistDetailHeroImageSize(Dp.Unspecified))
        assertEquals(0.7.dp, artistDetailHeroImageSize(1.dp))
    }
}
