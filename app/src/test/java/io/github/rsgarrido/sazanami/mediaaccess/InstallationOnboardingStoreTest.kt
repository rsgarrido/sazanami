package io.github.rsgarrido.sazanami.mediaaccess

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class InstallationOnboardingStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun completionSurvivesSameInstallationStoreRecreation() {
        val noBackupDirectory = temporaryFolder.newFolder("no_backup")
        InstallationOnboardingStore(noBackupDirectory, MARKER).apply {
            markLibraryFolderSelectionCompleted()
        }

        val restartedStore = InstallationOnboardingStore(noBackupDirectory, MARKER)

        assertTrue(restartedStore.isLibraryFolderSelectionCompleted())
        assertTrue(
            restartedStore.markerFile().toPath().startsWith(noBackupDirectory.toPath())
        )
    }

    @Test
    fun freshInstallationWithoutNoBackupMarkerRequiresOnboarding() {
        val restoredPreferencesDirectory = temporaryFolder.newFolder("restored_preferences")
        restoredPreferencesDirectory.resolve("app_preferences.preferences_pb").createNewFile()
        val freshNoBackupDirectory = temporaryFolder.newFolder("fresh_no_backup")

        val store = InstallationOnboardingStore(freshNoBackupDirectory, MARKER)

        assertFalse(store.isLibraryFolderSelectionCompleted())
    }

    @Test
    fun legacyCompletionMigratesOnlyForAnInPlacePackageUpdate() {
        assertTrue(
            shouldMigrateLegacyOnboardingCompletion(
                legacyCompletionPresent = true,
                hasMeaningfulLegacyFolderSelection = false,
                firstInstallTimeMillis = 100,
                lastUpdateTimeMillis = 200
            )
        )
        assertFalse(
            shouldMigrateLegacyOnboardingCompletion(
                legacyCompletionPresent = true,
                hasMeaningfulLegacyFolderSelection = true,
                firstInstallTimeMillis = 200,
                lastUpdateTimeMillis = 200
            )
        )
    }

    @Test
    fun meaningfulLegacyFolderSelectionCanMigrateOnInPlaceUpdate() {
        assertTrue(
            shouldMigrateLegacyOnboardingCompletion(
                legacyCompletionPresent = false,
                hasMeaningfulLegacyFolderSelection = true,
                firstInstallTimeMillis = 100,
                lastUpdateTimeMillis = 200
            )
        )
    }

    private companion object {
        const val MARKER = "library_folders_complete"
    }
}
