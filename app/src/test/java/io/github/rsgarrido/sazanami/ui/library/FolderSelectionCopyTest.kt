package io.github.rsgarrido.sazanami.ui.library

import org.junit.Assert.assertEquals
import org.junit.Test

class FolderSelectionCopyTest {
    @Test
    fun selectedFolderCountUsesSingularAndPluralCopy() {
        assertEquals("1 folder selected.", selectedFolderCountText(1))
        assertEquals("2 folders selected.", selectedFolderCountText(2))
    }
}
