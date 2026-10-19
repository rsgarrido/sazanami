package io.github.rsgarrido.sazanami.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeStatisticsHeaderTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun statisticsKeepFourAcrossOnPhoneAndWrapAllValuesForLargeText() {
        val fontScale = mutableStateOf(1f)
        composeRule.setContent {
            MaterialTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale.value)) {
                    Box(androidx.compose.ui.Modifier.requiredWidth(412.dp)) {
                        HomeStatistics(17, 3, 11, 9)
                    }
                }
            }
        }
        fun values() = listOf("17", "3", "11", "9").map {
            composeRule.onNodeWithText(it).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        }
        val normal = values()
        normal.forEach { assertEquals(normal.first().center.y, it.center.y, 1f) }
        composeRule.runOnIdle { fontScale.value = 2f }
        val large = values()
        assertEquals(large[0].center.y, large[1].center.y, 1f)
        assertEquals(large[2].center.y, large[3].center.y, 1f)
        assertTrue(large[2].top > large[0].bottom)
        assertTrue(large[0].right < large[1].left)
    }

    @Test
    fun headerHasSeparateStatisticsAndSettingsTargetsAtNarrowLargeTextWidth() {
        var statisticsOpened = 0
        var settingsOpened = 0
        composeRule.setContent {
            MaterialTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, fontScale = 2f)
                ) {
                    Box(modifier = androidx.compose.ui.Modifier.width(280.dp)) {
                        HomeHeader(
                            onStatisticsClick = { statisticsOpened++ },
                            onSettingsClick = { settingsOpened++ }
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("Sazanami").assertIsDisplayed()
        val statistics = composeRule.onNodeWithContentDescription("Open listening statistics")
            .assertIsDisplayed()
            .assertHasClickAction()
        val settings = composeRule.onNodeWithContentDescription("Settings")
            .assertIsDisplayed()
            .assertHasClickAction()

        val minimumTouchPx = 48f * composeRule.activity.resources.displayMetrics.density
        val statisticsBounds = statistics.fetchSemanticsNode().boundsInRoot
        val settingsBounds = settings.fetchSemanticsNode().boundsInRoot
        assertTrue(statisticsBounds.width >= minimumTouchPx)
        assertTrue(statisticsBounds.height >= minimumTouchPx)
        assertTrue(settingsBounds.width >= minimumTouchPx)
        assertTrue(settingsBounds.height >= minimumTouchPx)
        assertTrue(statisticsBounds.right <= settingsBounds.left)

        statistics.performClick()
        settings.performClick()
        composeRule.runOnIdle {
            assertEquals(1, statisticsOpened)
            assertEquals(1, settingsOpened)
        }
        composeRule.onNodeWithText("Listening statistics").assertDoesNotExist()
        composeRule.onNodeWithText("See your listening time and activity").assertDoesNotExist()
    }
}
