package io.github.rsgarrido.sazanami.ui.home

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutSizingTest {
    @Test
    fun normalPhoneRetainsFourStatisticsAndTheOriginalFourPinSlots() {
        for (width in listOf(328.dp, 360.dp, 380.dp, 400.dp)) {
            assertEquals(4, homeStatisticsColumns(width, 1f))
            assertEquals((width - 24.dp) / 4, homePinnedCardWidth(width))
        }
    }

    @Test
    fun wideGroupsAndPinnedArtworkStopGrowingWhileEveryPinStillFits() {
        for (width in listOf(700.dp, 1080.dp, 2400.dp)) {
            assertTrue(homeStatisticsMaximumWidth(1f) < width)
            assertEquals(4, homeStatisticsColumns(width, 1f))
            val card = homePinnedCardWidth(width)
            assertTrue(card < (width - 24.dp) / 4)
            for (pinCount in 1..4) {
                assertTrue(card * pinCount + 8.dp * (pinCount - 1) <= width)
            }
        }
        assertEquals(homePinnedCardWidth(700.dp), homePinnedCardWidth(2400.dp))
    }

    @Test
    fun largeTextAndNarrowWindowsWrapStatisticsWithoutDroppingAny() {
        assertEquals(4, homeStatisticsColumns(306.dp, 1f))
        assertEquals(2, homeStatisticsColumns(305.dp, 1f))
        assertEquals(2, homeStatisticsColumns(380.dp, 1.5f))
        assertEquals(1, homeStatisticsColumns(380.dp, 3f))
        assertEquals(4, homeStatisticsColumns(1080.dp, 2f))
    }

    @Test
    fun smallOrUnavailablePinSpaceCannotProduceNegativeCardSizes() {
        assertEquals(0.dp, homePinnedCardWidth(0.dp))
        assertEquals(0.dp, homePinnedCardWidth(Dp.Unspecified))
        assertEquals(0.dp, homePinnedCardWidth((-8).dp))
    }
}
