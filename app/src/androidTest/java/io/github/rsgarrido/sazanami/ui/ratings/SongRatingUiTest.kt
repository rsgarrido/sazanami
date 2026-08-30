package io.github.rsgarrido.sazanami.ui.ratings

import android.net.Uri
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.controller.SongRatingDialogState
import io.github.rsgarrido.sazanami.controller.SongRatingUiError
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.SongRating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SongRatingUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun starControlHasFiveAccessibleMinimumTargetsAndSelectsOneThroughFive() {
        val selected = mutableStateOf<Int?>(null)
        composeRule.setContent {
            MaterialTheme {
                StarRatingControl(
                    selectedRating = selected.value,
                    onRatingSelected = { selected.value = it },
                    enabled = true,
                    modifier = Modifier.width(260.dp)
                )
            }
        }

        composeRule.onAllNodesWithContentDescription("star", substring = true)
            .assertCountEquals(5)
        (1..5).forEach { value ->
            composeRule.onNodeWithTag("rating_star_$value")
                .assertWidthIsAtLeast(48.dp)
                .performClick()
                .assertIsSelected()
            composeRule.runOnIdle { assertEquals(value, selected.value) }
        }
    }

    @Test
    fun disabledStarsCannotChangeSelection() {
        var clicks = 0
        composeRule.setContent {
            MaterialTheme {
                StarRatingControl(3, { clicks++ }, enabled = false)
            }
        }
        composeRule.onNodeWithContentDescription("5 stars").assertIsNotEnabled().performClick()
        composeRule.runOnIdle { assertEquals(0, clicks) }
    }

    @Test
    fun narrowAndTwoTimesFontKeepEveryStarAndDialogActionReachable() {
        composeRule.setContent {
            MaterialTheme {
                val currentConfiguration = LocalConfiguration.current
                val currentDensity = LocalDensity.current
                val largeConfiguration = Configuration(currentConfiguration).apply {
                    fontScale = 2f
                    screenWidthDp = 280
                }
                CompositionLocalProvider(
                    LocalConfiguration provides largeConfiguration,
                    LocalDensity provides Density(currentDensity.density, fontScale = 2f)
                ) {
                    SongRatingDialog(
                        state = dialogState(persisted = rating(3), selected = 3),
                        onDismiss = {},
                        onRatingSelected = {},
                        onSave = {},
                        onClear = {}
                    )
                }
            }
        }

        var previousRight = Float.NEGATIVE_INFINITY
        (1..5).forEach { value ->
            val star = composeRule.onNodeWithTag("rating_star_$value")
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
            val bounds = star.fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.left >= previousRight)
            previousRight = bounds.right
        }
        composeRule.onNodeWithText("Clear rating").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Save rating").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun dialogCoversLoadingUnratedRatedSavingAndRetryableErrors() {
        val state = mutableStateOf(dialogState(isLoading = true))
        var saved = false
        var cleared = false
        var dismissed = false
        composeRule.setContent {
            MaterialTheme {
                SongRatingDialog(
                    state = state.value,
                    onDismiss = { dismissed = true },
                    onRatingSelected = { value ->
                        state.value = state.value.copy(selectedValue = value)
                    },
                    onSave = { saved = true },
                    onClear = { cleared = true }
                )
            }
        }
        composeRule.onNodeWithTag("rating_loading").assertIsDisplayed()
        composeRule.onNodeWithText("Save rating").assertIsNotEnabled()
        composeRule.runOnIdle { state.value = dialogState(isLoading = false) }
        composeRule.onNodeWithText("Clear rating").assertDoesNotExist()
        composeRule.onNodeWithText("Save rating").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("5 stars").performClick()
        composeRule.onNodeWithText("Save rating").assertIsEnabled().performClick()
        composeRule.runOnIdle { assertTrue(saved) }

        composeRule.runOnIdle {
            state.value = dialogState(
                persisted = rating(5),
                selected = 5,
                error = SongRatingUiError.CLEAR
            )
        }
        composeRule.onNodeWithText("Couldn’t clear rating.").assertIsDisplayed()
        composeRule.onNodeWithText("Clear rating").performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.runOnIdle {
            assertTrue(cleared)
            assertTrue(dismissed)
        }
    }

    private fun dialogState(
        persisted: SongRating? = null,
        selected: Int? = null,
        isLoading: Boolean = false,
        error: SongRatingUiError? = null
    ) = SongRatingDialogState(
        song = Song(
            id = 1,
            title = "A very long Unicode title – 東京",
            artist = "Björk",
            album = "Album",
            trackNumber = 1,
            duration = 1_000,
            uri = Uri.parse("content://song/1"),
            filePath = "/music/1.mp3",
            folderPath = "/music",
            albumArtUri = null
        ),
        persistedRating = persisted,
        selectedValue = selected,
        isLoading = isLoading,
        error = error
    )

    private fun rating(value: Int) = SongRating(1, value, 1, 1)
}
