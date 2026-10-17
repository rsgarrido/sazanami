package io.github.rsgarrido.sazanami.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentWidthTest {
    @Test
    fun albumAndPlaylistKeepPhoneFractionsRatherThanApplyingTheCapBeforeTheFraction() {
        for ((fraction, maximum) in listOf(0.84f to 360.dp, 0.72f to 320.dp)) {
            for (width in listOf(280.dp, 320.dp, 372.dp)) {
                assertEquals(width * fraction, boundedArtworkSize(width, fraction, maximum))
            }
        }
    }

    @Test
    fun detailArtworkCannotOutgrowItsIntendedCapOnWidePanes() {
        for (width in listOf(600.dp, 840.dp, 1400.dp)) {
            assertEquals(360.dp, boundedArtworkSize(width, 0.84f, 360.dp))
            assertEquals(320.dp, boundedArtworkSize(width, 0.72f, 320.dp))
            assertTrue(boundedArtworkSize(width, 0.84f, 360.dp) <= width)
        }
    }

    @Test
    fun absentOrCollapsedConstraintsDoNotInventArtworkDimensions() {
        for (width in listOf(0.dp, (-10).dp, Dp.Unspecified)) {
            assertEquals(0.dp, boundedArtworkSize(width, 0.84f, 360.dp))
        }
    }
}
