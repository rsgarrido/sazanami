package io.github.rsgarrido.sazanami.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySelectionChromePolicyTest {
    @Test fun activeTopLevelSelectionReplacesRegularHeader() {
        assertTrue(
            shouldShowLibrarySelectionHeader(
                selectionActive = true,
                isLibraryDetail = false,
                hasAudioAccess = true,
                bindingMatchesSelection = true
            )
        )
    }

    @Test fun inactiveOrUnboundSelectionKeepsRegularHeader() {
        assertFalse(
            shouldShowLibrarySelectionHeader(false, false, true, true)
        )
        assertFalse(
            shouldShowLibrarySelectionHeader(true, false, true, false)
        )
    }

    @Test fun detailsAndPermissionFallbackDoNotShowContextualHeader() {
        assertFalse(
            shouldShowLibrarySelectionHeader(true, true, true, true)
        )
        assertFalse(
            shouldShowLibrarySelectionHeader(true, false, false, true)
        )
    }
}
