package io.github.rsgarrido.sazanami.ui.queue

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import io.github.rsgarrido.sazanami.controller.PlaybackQueueCardUiState
import io.github.rsgarrido.sazanami.controller.PlaybackQueueEntryUiState
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
        composeRule.onNodeWithText("VIEWING").assertIsDisplayed()
        composeRule.onNodeWithText("Switch to this queue").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, switchCount) }
    }

    @Test
    fun scrollingTheEntryListDoesNotDismissOrDragTheExpandedSheet() {
        var dismissCount = 0
        val entries = List(40) { index ->
            PlaybackQueueEntryUiState(
                entryId = "entry-$index",
                song = null,
                isCurrent = index == 0
            )
        }
        composeRule.setContent {
            MaterialTheme {
                QueueHubSheet(
                    state = state("A").copy(
                        selectedEntries = entries,
                        selectedQueueEntryCount = entries.size
                    ),
                    onDismiss = { dismissCount += 1 },
                    onQueueSelected = {},
                    onSwitchSelected = {},
                    onCreateFromCurrent = {},
                    onRename = { _, _ -> },
                    onDelete = {},
                    onMessageDismissed = {}
                )
            }
        }

        composeRule.onNodeWithTag("queue-hub-entry-list").performTouchInput { swipeUp() }

        composeRule.runOnIdle { assertEquals(0, dismissCount) }
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

    @Test
    fun entryOverflowRemovesTheStableEntryId() {
        var removed: Pair<String, String>? = null
        composeRule.setContent {
            MaterialTheme {
                QueueHubSheet(
                    state = state("B").copy(
                        selectedEntries = listOf(
                            PlaybackQueueEntryUiState("duplicate-entry-2", null, false)
                        ),
                        selectedQueueEntryCount = 1
                    ),
                    onDismiss = {},
                    onQueueSelected = {},
                    onSwitchSelected = {},
                    onCreateFromCurrent = {},
                    onRename = { _, _ -> },
                    onDelete = {},
                    onRemoveEntry = { queueId, entryId -> removed = queueId to entryId },
                    onMessageDismissed = {}
                )
            }
        }

        composeRule.onAllNodesWithContentDescription("Actions for queue entry")[0].performClick()
        composeRule.onNodeWithText("Remove").performClick()

        composeRule.runOnIdle { assertEquals("B" to "duplicate-entry-2", removed) }
    }

    @Test
    fun shuffledQueueExplainsWhyDragReorderIsUnavailable() {
        val shuffledState = state("B").let { original ->
            original.copy(
                queues = original.queues.map { queue ->
                    if (queue.queueId == "B") queue.copy(shuffleEnabled = true) else queue
                },
                selectedEntries = listOf(PlaybackQueueEntryUiState("entry", null, false)),
                selectedQueueEntryCount = 1
            )
        }
        composeRule.setContent {
            MaterialTheme {
                QueueHubSheet(
                    state = shuffledState,
                    onDismiss = {},
                    onQueueSelected = {},
                    onSwitchSelected = {},
                    onCreateFromCurrent = {},
                    onRename = { _, _ -> },
                    onDelete = {},
                    onMessageDismissed = {}
                )
            }
        }

        composeRule.onNodeWithText("Turn off shuffle to reorder this queue.").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("Reorder queue entry").assertCountEquals(0)
    }

    @Test
    fun activeUpcomingRowTapPlaysStableEntryWhileInactiveTapDoesNothing() {
        var played: Pair<String, String>? = null
        var selected by mutableStateOf("A")
        composeRule.setContent {
            MaterialTheme {
                QueueHubSheet(
                    state = state(selected).copy(
                        selectedEntries = listOf(
                            PlaybackQueueEntryUiState("current", null, true),
                            PlaybackQueueEntryUiState("duplicate-2", null, false)
                        ),
                        selectedQueueEntryCount = 2
                    ),
                    onDismiss = {},
                    onQueueSelected = { selected = it },
                    onSwitchSelected = {},
                    onCreateFromCurrent = {},
                    onRename = { _, _ -> },
                    onDelete = {},
                    onPlayEntry = { queueId, entryId -> played = queueId to entryId },
                    onMessageDismissed = {}
                )
            }
        }

        composeRule.onAllNodesWithText("Unavailable track")[1].performClick()
        composeRule.runOnIdle { assertEquals("A" to "duplicate-2", played) }

        played = null
        composeRule.runOnIdle { selected = "B" }
        composeRule.onAllNodesWithText("Unavailable track")[1].performClick()
        composeRule.runOnIdle { assertEquals(null, played) }
    }

    @Test
    fun swipeRemovesUpcomingButNotCurrentEntry() {
        var removed: Pair<String, String>? = null
        composeRule.setContent {
            MaterialTheme {
                QueueHubSheet(
                    state = state("A").copy(
                        selectedEntries = listOf(
                            PlaybackQueueEntryUiState("current", null, true),
                            PlaybackQueueEntryUiState("upcoming", null, false)
                        ),
                        selectedQueueEntryCount = 2
                    ),
                    onDismiss = {},
                    onQueueSelected = {},
                    onSwitchSelected = {},
                    onCreateFromCurrent = {},
                    onRename = { _, _ -> },
                    onDelete = {},
                    onRemoveEntry = { queueId, entryId -> removed = queueId to entryId },
                    onMessageDismissed = {}
                )
            }
        }

        composeRule.onAllNodesWithText("Unavailable track")[0]
            .performTouchInput { swipeLeft() }
        composeRule.runOnIdle { assertEquals(null, removed) }

        composeRule.onAllNodesWithText("Unavailable track")[1]
            .performTouchInput { swipeLeft() }
        composeRule.runOnIdle { assertEquals("A" to "upcoming", removed) }
    }

    @Test
    fun removalSnackbarExposesUndoAction() {
        var undoCount = 0
        composeRule.setContent {
            MaterialTheme {
                QueueHubSheet(
                    state = state("A").copy(removalUndoEventId = 1L),
                    onDismiss = {},
                    onQueueSelected = {},
                    onSwitchSelected = {},
                    onCreateFromCurrent = {},
                    onRename = { _, _ -> },
                    onDelete = {},
                    onUndoRemove = { undoCount += 1 },
                    onMessageDismissed = {}
                )
            }
        }

        composeRule.onNodeWithText("Undo").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, undoCount) }
    }

    @Test
    fun dragHandleStartsReorderImmediatelyAndRowItselfDoesNotDrag() {
        var reorder: Triple<String, String, Int>? = null
        val entries = listOf(
            PlaybackQueueEntryUiState("first", null, false),
            PlaybackQueueEntryUiState("second", null, false)
        )
        composeRule.setContent {
            MaterialTheme {
                QueueHubSheet(
                    state = state("B").copy(
                        selectedEntries = entries,
                        selectedQueueEntryCount = entries.size
                    ),
                    onDismiss = {},
                    onQueueSelected = {},
                    onSwitchSelected = {},
                    onCreateFromCurrent = {},
                    onRename = { _, _ -> },
                    onDelete = {},
                    onReorderEntry = { queue, entry, order ->
                        reorder = Triple(queue, entry, order)
                    },
                    onMessageDismissed = {}
                )
            }
        }

        composeRule.onAllNodesWithText("Unavailable track")[0]
            .performTouchInput { swipeDown(durationMillis = 100) }
        composeRule.runOnIdle { assertEquals(null, reorder) }

        composeRule.onAllNodesWithContentDescription("Reorder queue entry")[0]
            .performTouchInput { swipeDown(durationMillis = 100) }
        composeRule.runOnIdle { assertEquals(Triple("B", "first", 1), reorder) }
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
