package io.github.rsgarrido.sazanami.ui.queue

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.rsgarrido.sazanami.controller.PlaybackQueueCardUiState
import io.github.rsgarrido.sazanami.controller.PlaybackQueueHubUiState
import io.github.rsgarrido.sazanami.ui.player.mini.MiniPlayerQueueButton
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class QueueHubSheetTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun selectingInactiveCardDoesNotSwitchUntilResumeIsPressed() {
        var selectedQueueId by mutableStateOf("A")
        var switchCount = 0
        composeRule.setContent {
            MaterialTheme {
                QueueHubSheet(
                    state = state(selectedQueueId),
                    onDismiss = {},
                    onQueueSelected = { selectedQueueId = it },
                    onSwitchSelected = { switchCount += 1 },
                    onCreateFromCurrent = {},
                    onRename = { _, _ -> },
                    onDelete = {},
                    onMessageDismissed = {}
                )
            }
        }

        composeRule.onNodeWithText("Queue 2").performClick()
        composeRule.runOnIdle { assertEquals(0, switchCount) }
        composeRule.onNodeWithText("Resume").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, switchCount) }
    }

    @Test
    fun queueIconExposesTheQueueHubEntryAction() {
        var openCount = 0
        composeRule.setContent {
            MaterialTheme {
                MiniPlayerQueueButton(onClick = { openCount += 1 })
            }
        }

        composeRule.onNodeWithContentDescription("Open queues").performClick()

        composeRule.runOnIdle { assertEquals(1, openCount) }
    }

    private fun state(selectedQueueId: String): PlaybackQueueHubUiState {
        val cards = listOf(
            card("A", "Queue 1", active = true, selected = selectedQueueId == "A"),
            card("B", "Queue 2", active = false, selected = selectedQueueId == "B")
        )
        return PlaybackQueueHubUiState(
            isLoading = false,
            queues = cards,
            activeQueueId = "A",
            selectedQueueId = selectedQueueId,
            selectedEntries = emptyList(),
            selectedQueueEntryCount = 0
        )
    }

    private fun card(
        id: String,
        name: String,
        active: Boolean,
        selected: Boolean
    ) = PlaybackQueueCardUiState(
        queueId = id,
        name = name,
        entryCount = 0,
        currentPosition = null,
        currentTrack = null,
        representativeTrack = null,
        lastActiveAt = 1L,
        isActive = active,
        isSelected = selected
    )
}
