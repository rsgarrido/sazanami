package io.github.rsgarrido.sazanami.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.rsgarrido.sazanami.data.FolderSelectionMode
import io.github.rsgarrido.sazanami.data.LibraryFolder
import io.github.rsgarrido.sazanami.ui.theme.SazanamiTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FolderSelectionOnboardingTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun preselectedMusicStillWaitsForExplicitContinue() {
        var continued = false
        composeRule.setContent {
            SazanamiTheme {
                onboardingScreen(onContinue = { continued = true })
            }
        }

        composeRule.onNodeWithText("Choose your music folders").assertIsDisplayed()
        composeRule.onNodeWithText("1 folder root(s) selected.").assertIsDisplayed()
        assertFalse(continued)

        composeRule.onNodeWithText("Continue").performClick()
        assertTrue(continued)
    }

    @Test
    fun optionalFolderArtworkActionsAreVisibleAndIndependent() {
        var allowRequested = false
        var skipped = false
        composeRule.setContent {
            SazanamiTheme {
                onboardingScreen(
                    onChooseFolderArtwork = { allowRequested = true },
                    onSkipFolderArtwork = { skipped = true }
                )
            }
        }

        composeRule.onNodeWithText("Folder artwork (optional)").assertIsDisplayed()
        composeRule.onNodeWithText("Allow folder artwork access").performClick()
        composeRule.onNodeWithText("Not now").performClick()

        assertTrue(allowRequested)
        assertTrue(skipped)
    }

    @Composable
    private fun onboardingScreen(
        onContinue: () -> Unit = {},
        onChooseFolderArtwork: () -> Unit = {},
        onSkipFolderArtwork: () -> Unit = {}
    ) = FolderSelectionScreen(
        libraryFolders = listOf(
            LibraryFolder(
                path = "/storage/emulated/0/Music",
                name = "Music",
                songCount = 3
            )
        ),
        folderSelectionMode = FolderSelectionMode.CUSTOM,
        selectedLibraryFolders = setOf("/storage/emulated/0/Music"),
        excludedLibraryFolders = emptySet(),
        onBackClick = {},
        onFolderToggle = {},
        onSelectAllClick = {},
        onClearSelectionClick = {},
        onChooseFolderArtwork = onChooseFolderArtwork,
        onSkipFolderArtwork = onSkipFolderArtwork,
        isInitialOnboarding = true,
        onContinueClick = onContinue
    )
}
