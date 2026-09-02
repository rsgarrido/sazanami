package io.github.rsgarrido.sazanami.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicBottomNavigationVisibilityTest {
    @Test fun primaryLibraryDestinationShowsBottomNavigation() {
        assertTrue(visibility())
    }

    @Test fun reconciliationDestinationHidesBottomNavigation() {
        assertFalse(visibility(isListeningHistoryReconciliationVisible = true))
    }

    @Test fun adjacentNestedSettingsDestinationsRemainHidden() {
        assertFalse(visibility(isListeningHistoryImportVisible = true))
        assertFalse(visibility(isSettingsScreenVisible = true))
    }

    @Test fun librarySelectionHidesBottomNavigationWithoutChangingDestination() {
        assertFalse(visibility(isLibrarySelectionActive = true))
    }

    private fun visibility(
        isListeningHistoryImportVisible: Boolean = false,
        isListeningHistoryReconciliationVisible: Boolean = false,
        isSettingsScreenVisible: Boolean = false,
        isLibrarySelectionActive: Boolean = false
    ) = shouldShowPrimaryBottomNavigation(
        isPlayerExpanded = false,
        isFolderScreenVisible = false,
        isDiagnosticsScreenVisible = false,
        isEqualizerScreenVisible = false,
        isStatisticsScreenVisible = false,
        isListeningHistoryImportVisible = isListeningHistoryImportVisible,
        isListeningHistoryReconciliationVisible = isListeningHistoryReconciliationVisible,
        isSettingsScreenVisible = isSettingsScreenVisible,
        isTagEditorVisible = false,
        isLibrarySelectionActive = isLibrarySelectionActive
    )
}
