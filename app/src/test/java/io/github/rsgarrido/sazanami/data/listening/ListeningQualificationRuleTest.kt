package io.github.rsgarrido.sazanami.data.listening

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningQualificationRuleTest {
    @Test
    fun representativeDurationsUseCeilingHalfCappedAtFourMinutes() {
        assertEquals(30_000L, ListeningQualificationRuleV1.thresholdMs(60_000L))
        assertEquals(120_000L, ListeningQualificationRuleV1.thresholdMs(240_000L))
        assertEquals(210_000L, ListeningQualificationRuleV1.thresholdMs(420_000L))
        assertEquals(240_000L, ListeningQualificationRuleV1.thresholdMs(720_000L))
    }

    @Test
    fun exactBoundaryQualifiesButOneMillisecondBelowDoesNot() {
        val duration = 60_000L

        assertFalse(ListeningQualificationRuleV1.isTimeQualified(duration, 29_999L))
        assertTrue(ListeningQualificationRuleV1.isTimeQualified(duration, 30_000L))
        assertTrue(ListeningQualificationRuleV1.isTimeQualified(duration, 30_001L))
    }

    @Test
    fun oddAndSubSecondDurationsUseIntegerCeilingHalf() {
        assertEquals(501L, ListeningQualificationRuleV1.thresholdMs(1_001L))
        assertEquals(1L, ListeningQualificationRuleV1.thresholdMs(1L))
        assertFalse(ListeningQualificationRuleV1.isTimeQualified(1_001L, 500L))
        assertTrue(ListeningQualificationRuleV1.isTimeQualified(1_001L, 501L))
    }

    @Test
    fun unknownZeroAndNegativeDurationsHaveNoTimeThreshold() {
        assertNull(ListeningQualificationRuleV1.thresholdMs(null))
        assertNull(ListeningQualificationRuleV1.thresholdMs(0L))
        assertNull(ListeningQualificationRuleV1.thresholdMs(-1L))
        assertFalse(ListeningQualificationRuleV1.isTimeQualified(null, Long.MAX_VALUE))
        assertFalse(ListeningQualificationRuleV1.isTimeQualified(0L, Long.MAX_VALUE))
        assertFalse(ListeningQualificationRuleV1.isTimeQualified(-1L, Long.MAX_VALUE))
    }

    @Test
    fun maximumLongDurationIsHandledWithoutOverflow() {
        assertEquals(240_000L, ListeningQualificationRuleV1.thresholdMs(Long.MAX_VALUE))
        assertEquals(1, ListeningQualificationRuleV1.VERSION)
    }
}
