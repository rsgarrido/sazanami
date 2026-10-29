package io.github.rsgarrido.sazanami.ui.player.pocketadvance

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PocketAdvanceLayoutTest {
    @Test
    fun minimumComesFromVisibleArtworkMetadataSeekAndHardwareBudgets() {
        assertEquals(DpSize(784.dp, 412.dp), minimumPocketAdvanceSize(1f))
        // At the limit the shell still contains both 48dp D-pad/face targets, a 144dp album,
        // readable metadata, the 48dp seek target, and the surrounding bezel and brand row.
        val minimum = minimumPocketAdvanceSize(1f)
        val contentWidth = minimum.width - PocketAdvanceLayout.OuterInset * 2 -
            PocketAdvanceLayout.ShellInset * 2 - PocketAdvanceLayout.ControlWing * 2 -
            PocketAdvanceLayout.ScreenGap * 2 - PocketAdvanceLayout.ScreenInset * 2
        assertEquals(PocketAdvanceLayout.Artwork + PocketAdvanceLayout.ScreenGap + PocketAdvanceLayout.MetadataWidth, contentWidth)
    }

    @Test
    fun largeTextIncreasesBothDimensionsInsteadOfShrinkingArtworkOrTargets() {
        val normal = minimumPocketAdvanceSize(1f)
        val large = minimumPocketAdvanceSize(2f)
        assertTrue(large.width > normal.width)
        assertTrue(large.height > normal.height)
        assertEquals(176f, large.width.value - normal.width.value, .001f)
        assertTrue(PocketAdvanceLayout.ControlWing >= 48.dp * 3)
        assertTrue(PocketAdvanceLayout.SeekHeight >= 48.dp)
    }

    @Test
    fun invalidOrSmallTextScaleCannotProduceAnInvalidOrSmallerBudget() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, 0f, -1f, .8f).forEach {
            assertEquals(minimumPocketAdvanceSize(1f), minimumPocketAdvanceSize(it))
        }
    }

    @Test
    fun enlargedShellFillsTypicalLandscapeHeightWithoutStretchingNearSquareWindows() {
        assertEquals(680.dp, PocketAdvanceLayout.shellHeight(680.dp, 1180.dp, 1f))
        val nearSquare = PocketAdvanceLayout.shellHeight(720.dp, 800.dp, 1f)
        assertTrue(nearSquare < 720.dp)
        assertTrue(nearSquare >= minimumPocketAdvanceSize(1f).height - PocketAdvanceLayout.OuterInset * 2)
    }

    @Test
    fun shellUsesTheWholeContentBudgetAtMinimumAndNeverExceedsAvailableHeight() {
        val minimum = minimumPocketAdvanceSize(1f)
        val innerHeight = minimum.height - PocketAdvanceLayout.OuterInset * 2
        assertEquals(innerHeight,
            PocketAdvanceLayout.shellHeight(innerHeight, minimum.width - PocketAdvanceLayout.OuterInset * 2, 1f))
        assertEquals(250.dp, PocketAdvanceLayout.shellHeight(250.dp, 1120.dp, 1f))
    }
}
