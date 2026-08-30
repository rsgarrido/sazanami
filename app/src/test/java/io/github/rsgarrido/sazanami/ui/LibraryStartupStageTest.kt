package io.github.rsgarrido.sazanami.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun restoredPreferencesCannotBypassExplicitFolderConfirmation() {
        assertEquals(
            LibraryStartupStage.FOLDER_SELECTION,
            resolveLibraryStartupStage(
                hasAudioAccess = true,
                initialFolderSelectionCompleted = false,
                initialLibraryReady = true,
                folderArtworkOnboardingComplete = true
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

    @Test
    fun continuingWithoutArtworkChoiceRecordsOptionalSkipBeforeConfirmation() {
        var skipped = false
        var confirmed = false

        confirmInitialFolderOnboarding(
            folderArtworkOnboardingComplete = false,
            onSkipFolderArtwork = { skipped = true },
            onConfirmLibraryFolders = { confirmed = true }
        )

        assertTrue(skipped)
        assertTrue(confirmed)
    }

    @Test
    fun grantedArtworkChoiceIsPreservedWhenConfirmingFolders() {
        var skipped = false
        var confirmed = false

        confirmInitialFolderOnboarding(
            folderArtworkOnboardingComplete = true,
            onSkipFolderArtwork = { skipped = true },
            onConfirmLibraryFolders = { confirmed = true }
        )

        assertFalse(skipped)
        assertTrue(confirmed)
    }
}
