package io.github.rsgarrido.sazanami.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryStartupStageTest {
    @Test
    fun firstRunFolderOnboardingAppearsAfterPermission() {
        assertEquals(
            LibraryStartupStage.FOLDER_SELECTION,
            resolveLibraryStartupStage(
                hasAudioAccess = true,
                initialFolderSelectionCompleted = false,
                initialLibraryReady = false,
                folderArtworkOnboardingComplete = false
            )
        )
    }

    @Test
    fun configuredUserBypassesFolderOnboarding() {
        assertEquals(
            LibraryStartupStage.LIBRARY_LOADING,
            resolveLibraryStartupStage(
                hasAudioAccess = true,
                initialFolderSelectionCompleted = true,
                initialLibraryReady = false,
                folderArtworkOnboardingComplete = false
            )
        )
    }

    @Test
    fun completedEmptySelectionDoesNotRestartFolderOnboarding() {
        assertEquals(
            LibraryStartupStage.FOLDER_ARTWORK,
            resolveLibraryStartupStage(
                hasAudioAccess = true,
                initialFolderSelectionCompleted = true,
                initialLibraryReady = true,
                folderArtworkOnboardingComplete = false
            )
        )
    }
}
