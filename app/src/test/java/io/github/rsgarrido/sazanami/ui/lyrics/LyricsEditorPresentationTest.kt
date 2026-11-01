package io.github.rsgarrido.sazanami.ui.lyrics

import org.junit.Assert.*
import org.junit.Test

class LyricsEditorPresentationTest {
    @Test fun fullyVisibleSelectionDoesNotJumpAndUsesIdentity() {
        val visible = listOf(LyricsVisibleRow("first", 0, 50), LyricsVisibleRow("selected", 50, 50))
        assertNull(selectedLyricsScrollTarget(1, "selected", visible, 0, 200))
        // Same numeric index, different identity after deletion or undo: reveal the actual selection.
        assertEquals(1, selectedLyricsScrollTarget(1, "replacement", visible, 0, 200))
    }

    @Test fun offscreenAndClippedRowsAreRevealedIncludingLastLine() {
        assertEquals(99, selectedLyricsScrollTarget(99, "last", listOf(LyricsVisibleRow("other", 0, 48)), 0, 200))
        assertEquals(4, selectedLyricsScrollTarget(4, "selected", listOf(LyricsVisibleRow("selected", 180, 48)), 0, 200))
        assertEquals(0, selectedLyricsScrollTarget(0, "selected", listOf(LyricsVisibleRow("selected", -20, 48)), 0, 200))
    }

    @Test fun oversizedLineAndUnmeasuredViewportDoNotCauseScrollLoops() {
        assertNull(selectedLyricsScrollTarget(4, "long", listOf(LyricsVisibleRow("long", 0, 900)), 0, 200))
        assertNull(selectedLyricsScrollTarget(4, "long", emptyList(), 0, 0))
    }

    @Test fun offsetSummaryUsesTheExistingSignedConvention() {
        assertEquals("+200", signedLyricsOffset(200))
        assertEquals("-100", signedLyricsOffset(-100))
        assertEquals("0", signedLyricsOffset(0))
    }
}
