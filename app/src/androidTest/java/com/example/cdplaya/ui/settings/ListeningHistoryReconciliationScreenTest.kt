package com.example.cdplaya.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.cdplaya.controller.LinkedHistoricalReconciliation
import com.example.cdplaya.controller.ListeningHistoryReconciliationUiState
import com.example.cdplaya.controller.ReconciliationConfirmation
import com.example.cdplaya.controller.ReconciliationReviewContent
import com.example.cdplaya.controller.ReconciliationReviewTab
import com.example.cdplaya.data.HistoricalReconciliationItem
import com.example.cdplaya.data.HistoricalReconciliationMetrics
import com.example.cdplaya.data.HistoricalReconciliationSource
import com.example.cdplaya.data.ListeningIdentityReconciliationCandidate
import com.example.cdplaya.data.ListeningIdentityReconciliationRatings
import com.example.cdplaya.data.LocalReconciliationTarget
import com.example.cdplaya.data.ReconciliationCandidateCategory
import com.example.cdplaya.data.ReconciliationCandidateDisposition
import com.example.cdplaya.data.ReconciliationCandidateEvidence
import com.example.cdplaya.data.ReconciliationMetadataRelation
import com.example.cdplaya.data.ReconciliationVersionRelation
import com.example.cdplaya.data.local.ListeningSource
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ListeningHistoryReconciliationScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun strongCandidateRequiresExplicitSelectionThenConfirmation() {
        var selected = 0
        var confirmed = 0
        val source = source(1, "Fictional Song")
        val target = target(10, "Fictional Song", "Album")
        val uiState = mutableStateOf(
            ReconciliationReviewContent(listOf(item(source, target)), emptyList())
        )
        composeRule.setContent {
            MaterialTheme {
                ListeningHistoryReconciliationScreen(
                    ListeningHistoryReconciliationUiState.Content(uiState.value),
                    actions(
                        onToggle = { id -> uiState.value = uiState.value.copy(expandedSourceId = id) },
                        onSelect = { _, _ -> selected++ },
                        onConfirmed = { confirmed++ }
                    )
                )
            }
        }
        composeRule.onNodeWithText("Fictional Song").performClick()
        composeRule.onAllNodesWithText("Title, artist, and album match").assertCountEquals(2)
        composeRule.onNodeWithText("Link history").performClick()
        composeRule.runOnIdle {
            assertEquals(1, selected)
            assertEquals(0, confirmed)
        }

        composeRule.runOnIdle {
            uiState.value = ReconciliationReviewContent(
                listOf(item(source, target)), emptyList(),
                confirmation = ReconciliationConfirmation.Link(
                    listOf(source), target,
                    listOf(ListeningIdentityReconciliationRatings(null, null,
                        com.example.cdplaya.data.ListeningIdentityReconciliationRatingState.NO_RATINGS))
                )
            )
        }
        composeRule.onNodeWithText("Link history").performClick()
        composeRule.runOnIdle { assertEquals(1, confirmed) }
    }

    @Test fun ambiguousCandidatesShowBothVersionsAndTextWarningWithoutPreselection() {
        val source = source(1, "Duplicate Title")
        val studio = target(10, "Duplicate Title", "Studio Album")
        val live = target(11, "Duplicate Title", "Live Album")
        val ambiguous = HistoricalReconciliationItem(
            source,
            listOf(candidate(studio, ReconciliationCandidateCategory.AMBIGUOUS),
                candidate(live, ReconciliationCandidateCategory.AMBIGUOUS)),
            ReconciliationCandidateDisposition.AMBIGUOUS,
            false
        )
        val uiState = mutableStateOf(ReconciliationReviewContent(listOf(ambiguous), emptyList()))
        composeRule.setContent {
            MaterialTheme {
                ListeningHistoryReconciliationScreen(
                    ListeningHistoryReconciliationUiState.Content(uiState.value),
                    actions(onToggle = { id -> uiState.value = uiState.value.copy(expandedSourceId = id) })
                )
            }
        }
        composeRule.onNodeWithText("Duplicate Title").performClick()
        composeRule.onNodeWithText("Studio Album", substring = true).assertExists()
        composeRule.onNodeWithText("Live Album", substring = true).assertExists()
        composeRule.onNodeWithText("Multiple library versions may match. No track has been selected.")
            .assertExists()
    }

    @Test fun linkedActionHasAccessibleText() {
        val source = source(2, "Remastered Song")
        val target = target(12, "Song", "Original Album")
        val linked = LinkedHistoricalReconciliation(source, target, 1)
        val uiState = mutableStateOf(
            ReconciliationReviewContent(
                emptyList(), listOf(linked), activeTab = ReconciliationReviewTab.LINKED
            )
        )
        composeRule.setContent {
            MaterialTheme {
                ListeningHistoryReconciliationScreen(
                    ListeningHistoryReconciliationUiState.Content(uiState.value),
                    actions(onLinkedToggle = { id ->
                        uiState.value = uiState.value.copy(expandedLinkedTargetId = id)
                    })
                )
            }
        }
        composeRule.onNodeWithText("Song").performClick()
        composeRule.onNodeWithContentDescription("Unlink Remastered Song from Song").assertExists()
    }

    @Test fun linkedHistoriesAreCompactlyGroupedAndExpandTransiently() {
        val target = target(12, "Canonical Song", "Current Album")
        val first = LinkedHistoricalReconciliation(source(2, "Imported One"), target, 1)
        val second = LinkedHistoricalReconciliation(source(3, "Imported Two"), target, 2)
        val uiState = mutableStateOf(
            ReconciliationReviewContent(
                emptyList(), listOf(first, second), activeTab = ReconciliationReviewTab.LINKED
            )
        )
        composeRule.setContent {
            MaterialTheme {
                ListeningHistoryReconciliationScreen(
                    ListeningHistoryReconciliationUiState.Content(uiState.value),
                    actions(onLinkedToggle = { id ->
                        uiState.value = uiState.value.copy(
                            expandedLinkedTargetId =
                                if (uiState.value.expandedLinkedTargetId == id) null else id
                        )
                    })
                )
            }
        }

        composeRule.onAllNodesWithText("Canonical Song").assertCountEquals(1)
        composeRule.onNodeWithText("2 imported histories · 6 historical plays").assertExists()
        composeRule.onAllNodesWithText("Imported One").assertCountEquals(0)
        composeRule.onNodeWithText("Canonical Song").performClick()
        composeRule.onNodeWithText("Imported One").assertExists()
        composeRule.onNodeWithText("Imported Two").assertExists()
    }

    @Test fun versionWarningHasAccessibleText() {
        val source = source(2, "Remastered Song")
        val target = target(12, "Song", "Original Album")
        val versionItem = HistoricalReconciliationItem(
            source,
            listOf(candidate(target, ReconciliationCandidateCategory.VERSION_SENSITIVE)),
            ReconciliationCandidateDisposition.SUGGESTED,
            false
        )
        val uiState = mutableStateOf(ReconciliationReviewContent(listOf(versionItem), emptyList()))
        composeRule.setContent {
            MaterialTheme {
                ListeningHistoryReconciliationScreen(
                    ListeningHistoryReconciliationUiState.Content(uiState.value),
                    actions(onToggle = { id -> uiState.value = uiState.value.copy(expandedSourceId = id) })
                )
            }
        }
        composeRule.onNodeWithText("Remastered Song").performClick()
        composeRule.onNodeWithText("This may be a different version of the song.").assertExists()
        composeRule.onNodeWithContentDescription(
            "Warning: This may be a different version of the song."
        ).assertExists()
    }

    private fun setContent(
        content: ReconciliationReviewContent,
        actions: ListeningHistoryReconciliationUiActions
    ) {
        composeRule.setContent {
            MaterialTheme {
                ListeningHistoryReconciliationScreen(
                    ListeningHistoryReconciliationUiState.Content(content),
                    actions
                )
            }
        }
    }

    private fun actions(
        onToggle: (Long) -> Unit = {},
        onLinkedToggle: (Long) -> Unit = {},
        onSelect: (List<Long>, LocalReconciliationTarget) -> Unit = { _, _ -> },
        onConfirmed: () -> Unit = {}
    ) = ListeningHistoryReconciliationUiActions(
        onEnter = {}, onBack = {}, onRetry = {}, onTabSelected = {},
        onToggleExpanded = onToggle, onToggleLinkedGroup = onLinkedToggle,
        onSkip = {}, onCandidateSelected = onSelect,
        onSearchRequested = {}, onSearchQueryChanged = {}, onSearchDismissed = {},
        onUnlinkRequested = {}, onConfirmationCancelled = {}, onConfirmed = onConfirmed,
        onMessageDismissed = {}
    )

    private fun item(source: HistoricalReconciliationSource, target: LocalReconciliationTarget) =
        HistoricalReconciliationItem(
            source,
            listOf(candidate(target, ReconciliationCandidateCategory.STRONG_METADATA)),
            ReconciliationCandidateDisposition.SUGGESTED,
            false
        )

    private fun candidate(target: LocalReconciliationTarget, category: ReconciliationCandidateCategory) =
        ListeningIdentityReconciliationCandidate(
            target,
            ReconciliationCandidateEvidence(
                ReconciliationMetadataRelation.EXACT,
                ReconciliationMetadataRelation.EXACT,
                ReconciliationMetadataRelation.EXACT,
                if (category == ReconciliationCandidateCategory.VERSION_SENSITIVE) {
                    ReconciliationVersionRelation.DIFFERENT
                } else ReconciliationVersionRelation.NONE,
                emptySet(), category
            )
        )

    private fun source(id: Long, title: String) = HistoricalReconciliationSource(
        id, title, "Fictional Artist", "", null,
        setOf(ListeningSource.SPOTIFY_IMPORT), false,
        HistoricalReconciliationMetrics(3, 3, 180_000, 3, 1, 2)
    )

    private fun target(id: Long, title: String, album: String) = LocalReconciliationTarget(
        id, id + 100, "ref-$id", title, "Fictional Artist", album, null,
        180_000, "$title.flac", "flac", "Music/Fictional Artist/$album"
    )
}
