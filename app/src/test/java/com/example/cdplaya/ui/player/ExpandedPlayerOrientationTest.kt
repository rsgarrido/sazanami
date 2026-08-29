package com.example.cdplaya.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class ExpandedPlayerOrientationTest {
    @Test
    fun enterRequestsPortraitOnceAndExitRestoresPriorOrientation() {
        var orientation = PRIOR
        val writes = mutableListOf<Int>()
        val session = ExpandedPlayerOrientationSession(
            readOrientation = { orientation },
            writeOrientation = { orientation = it; writes += it },
            portraitOrientation = PORTRAIT
        )

        session.enter()
        session.enter()
        assertEquals(PORTRAIT, orientation)
        assertEquals(listOf(PORTRAIT), writes)

        session.exit()
        session.exit()
        assertEquals(PRIOR, orientation)
        assertEquals(listOf(PORTRAIT, PRIOR), writes)
    }

    @Test
    fun existingPortraitRequestIsCapturedAndRestoredWithoutRedundantWrites() {
        var orientation = PORTRAIT
        val writes = mutableListOf<Int>()
        val session = ExpandedPlayerOrientationSession(
            readOrientation = { orientation },
            writeOrientation = { orientation = it; writes += it },
            portraitOrientation = PORTRAIT
        )

        session.enter()
        session.exit()

        assertEquals(PORTRAIT, orientation)
        assertEquals(emptyList<Int>(), writes)
    }

    private companion object {
        const val PRIOR = -1
        const val PORTRAIT = 7
    }
}
