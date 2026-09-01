package io.github.rsgarrido.sazanami.ui.queue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueHubReorderLogicTest {
    @Test
    fun activeTargetClampsImmediatelyAfterCurrent() {
        assertEquals(
            1,
            clampQueueReorderTarget(
                requestedIndex = 0,
                lastIndex = 4,
                activeCurrentIndex = 0
            )
        )
        assertEquals(0, clampQueueReorderTarget(0, 4, activeCurrentIndex = null))
    }

    @Test
    fun edgeAutoScrollPointsUpDownAndStopsInTheCenter() {
        assertEquals(-18f, queueDragAutoScrollDelta(0f, 0f, 500f, 72f, 18f))
        assertEquals(18f, queueDragAutoScrollDelta(500f, 0f, 500f, 72f, 18f))
        assertEquals(0f, queueDragAutoScrollDelta(250f, 0f, 500f, 72f, 18f))
    }

    @Test
    fun repeatedBottomFramesKeepScrollingUntilTheActualListEnd() {
        val stableDraggedEntryId = "entry-17"
        val deltas = List(5) {
            queueDragAutoScrollDelta(
                pointerY = 560f,
                viewportStart = 0f,
                viewportEnd = 500f,
                edgeZonePx = 72f,
                maxScrollPerFramePx = 18f,
                canScrollForward = true
            )
        }

        assertEquals(listOf(18f, 18f, 18f, 18f, 18f), deltas)
        assertEquals("entry-17", stableDraggedEntryId)
        assertEquals(
            0f,
            queueDragAutoScrollDelta(
                pointerY = 560f,
                viewportStart = 0f,
                viewportEnd = 500f,
                edgeZonePx = 72f,
                maxScrollPerFramePx = 18f,
                canScrollForward = false
            )
        )
    }

    @Test
    fun topEdgeAlsoClampsOnlyAtTheActualListStart() {
        assertTrue(
            queueDragAutoScrollDelta(72f, 0f, 500f, 72f, 18f) < 0f
        )
        assertTrue(
            queueDragAutoScrollDelta(48f, 0f, 500f, 72f, 18f) < 0f
        )
        assertEquals(
            -18f,
            queueDragAutoScrollDelta(-20f, 0f, 500f, 72f, 18f, canScrollBackward = true)
        )
        assertEquals(
            0f,
            queueDragAutoScrollDelta(-20f, 0f, 500f, 72f, 18f, canScrollBackward = false)
        )
    }

    @Test
    fun repeatedUpwardFramesSurviveTransientZeroConsumptionUntilListStart() {
        val stableDraggedEntryId = "entry-17"
        repeat(5) {
            assertTrue(
                queueDragCanContinueAutoScrolling(
                    requestedDelta = -18f,
                    consumedDelta = 0f,
                    canScrollBackward = true,
                    canScrollForward = true
                )
            )
            assertEquals("entry-17", stableDraggedEntryId)
        }
        assertFalse(
            queueDragCanContinueAutoScrolling(
                requestedDelta = -18f,
                consumedDelta = 0f,
                canScrollBackward = false,
                canScrollForward = true
            )
        )
    }

    @Test
    fun stableDraggedEntrySurvivesBothAutoScrollDirections() {
        val stableDraggedEntryId = "duplicate-instance-2"
        assertTrue(queueDragCanContinueAutoScrolling(-18f, -18f, true, true))
        assertEquals("duplicate-instance-2", stableDraggedEntryId)
        assertTrue(queueDragCanContinueAutoScrolling(18f, 18f, true, true))
        assertEquals("duplicate-instance-2", stableDraggedEntryId)
    }
}
