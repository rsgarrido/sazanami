package io.github.rsgarrido.sazanami.ui.library

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.rsgarrido.sazanami.controller.PlaybackQueueCardUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AddToAnotherQueueDialogTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun activeQueueIsExcludedAndSelectingInactiveQueueDoesNotSwitch() {
        var selectedId: String? = null
        composeRule.setContent {
            MaterialTheme {
                AddToAnotherQueueDialog(
                    queues = listOf(card("active", "Playing", true), card("saved", "Road trip", false)),
                    activeQueueId = "active",
                    onQueueSelected = { selectedId = it },
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithText("Playing").assertDoesNotExist()
        composeRule.onNodeWithText("Road trip").assertIsDisplayed()
        composeRule.onNodeWithText("Add").performClick()
        composeRule.runOnIdle { assertEquals("saved", selectedId) }
    }

    @Test
    fun noInactiveQueuesShowsHelpfulEmptyState() {
        composeRule.setContent {
            MaterialTheme {
                AddToAnotherQueueDialog(
                    queues = listOf(card("active", "Playing", true)),
                    activeQueueId = "active",
                    onQueueSelected = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithText(
            "There are no inactive queues. The only available queue is already playing."
        ).assertIsDisplayed()
    }

    private fun card(id: String, name: String, active: Boolean) = PlaybackQueueCardUiState(
        queueId = id,
        name = name,
        entryCount = 2,
        currentPosition = 1,
        currentTrack = null,
        representativeTrack = null,
        lastActiveAt = 1L,
        isActive = active,
        isSelected = active
    )
}
