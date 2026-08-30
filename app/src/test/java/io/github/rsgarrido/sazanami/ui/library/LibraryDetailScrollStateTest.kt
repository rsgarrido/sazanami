package io.github.rsgarrido.sazanami.ui.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryDetailScrollStateTest {
    @Test
    fun compactTitleAppearsAfterScrollableHeaderLeavesTheViewport() {
        assertFalse(shouldShowCompactLibraryDetailTitle(firstVisibleItemIndex = 0))
        assertTrue(shouldShowCompactLibraryDetailTitle(firstVisibleItemIndex = 1))
    }
}
