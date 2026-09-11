package io.github.rsgarrido.sazanami.ui.player.retrorack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetroRackLayoutProfileTest {
    @Test
    fun `normal phone gives deck and spectrum nearly half of usable height`() {
        val profile = buildRetroRackLayoutProfile(
            screenHeightDp = 800,
            screenWidthDp = 400,
            fontScale = 1f
        )
        val estimatedQueueHeight = 800 - 20 - 14 -
                profile.mainDeckHeightDp - profile.spectrumHeightDp

        assertFalse(profile.compact)
        assertEquals(258, profile.mainDeckHeightDp)
        assertEquals(98, profile.displayHeightDp)
        assertEquals(124, profile.spectrumHeightDp)
        assertEquals(384, estimatedQueueHeight)
    }

    @Test
    fun `compact phone preserves a practical queue`() {
        val profile = buildRetroRackLayoutProfile(
            screenHeightDp = 640,
            screenWidthDp = 350,
            fontScale = 1f
        )
        val estimatedQueueHeight = 640 - 12 - 8 -
                profile.mainDeckHeightDp - profile.spectrumHeightDp

        assertTrue(profile.compact)
        assertEquals(234, profile.mainDeckHeightDp)
        assertEquals(86, profile.displayHeightDp)
        assertEquals(104, profile.spectrumHeightDp)
        assertEquals(282, estimatedQueueHeight)
    }

    @Test
    fun `short and tall screens receive distinct upper module sizing`() {
        val short = buildRetroRackLayoutProfile(600, 400, 1f)
        val tall = buildRetroRackLayoutProfile(900, 400, 1f)

        assertEquals(218, short.mainDeckHeightDp)
        assertEquals(90, short.spectrumHeightDp)
        assertEquals(270, tall.mainDeckHeightDp)
        assertEquals(136, tall.spectrumHeightDp)
    }

    @Test
    fun `large text expands deck and display without changing spectrum`() {
        val regular = buildRetroRackLayoutProfile(800, 400, 1f)
        val largeText = buildRetroRackLayoutProfile(800, 400, 1.3f)

        assertEquals(regular.mainDeckHeightDp + 14, largeText.mainDeckHeightDp)
        assertEquals(regular.displayHeightDp + 10, largeText.displayHeightDp)
        assertEquals(regular.spectrumHeightDp, largeText.spectrumHeightDp)
    }
}
