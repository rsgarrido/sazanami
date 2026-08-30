package io.github.rsgarrido.sazanami.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.controller.LinkedHistoricalReconciliation
import io.github.rsgarrido.sazanami.controller.ListeningHistoryReconciliationUiState
import io.github.rsgarrido.sazanami.controller.ReconciliationConfirmation
import io.github.rsgarrido.sazanami.controller.ReconciliationReviewContent
import io.github.rsgarrido.sazanami.controller.ReconciliationReviewTab
import io.github.rsgarrido.sazanami.controller.ReconciliationSearchState
import io.github.rsgarrido.sazanami.data.HistoricalReconciliationItem
import io.github.rsgarrido.sazanami.data.HistoricalReconciliationMetrics
import io.github.rsgarrido.sazanami.data.HistoricalReconciliationSource
import io.github.rsgarrido.sazanami.data.ListeningIdentityReconciliationCandidate
import io.github.rsgarrido.sazanami.data.ListeningIdentityReconciliationRatings
import io.github.rsgarrido.sazanami.data.ListeningIdentityReconciliationRatingState
import io.github.rsgarrido.sazanami.data.LocalReconciliationTarget
import io.github.rsgarrido.sazanami.data.ReconciliationCandidateCategory
import io.github.rsgarrido.sazanami.data.ReconciliationCandidateDisposition
import io.github.rsgarrido.sazanami.data.ReconciliationCandidateEvidence
import io.github.rsgarrido.sazanami.data.ReconciliationMetadataRelation
import io.github.rsgarrido.sazanami.data.ReconciliationVersionRelation
import io.github.rsgarrido.sazanami.data.local.ListeningSource
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
                        io.github.rsgarrido.sazanami.data.ListeningIdentityReconciliationRatingState.NO_RATINGS))
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

    @Test fun narrowWidthLongUnicodeExpandedLinkedGroupRemainsAccessible() {
        val longAlbum = "非常に長い架空のアルバムタイトル Narrow Screen Archival Edition"
        val canonical = target(80, "復讐の歌 — Extended Title", longAlbum)
        val linked = LinkedHistoricalReconciliation(
            source(81, "歴史的なインポート曲 — Remastered 2015"),
            canonical,
            1
        )
        val content = ReconciliationReviewContent(
            reviewItems = emptyList(),
            linkedItems = listOf(linked),
            activeTab = ReconciliationReviewTab.LINKED,
            expandedLinkedTargetId = canonical.identityId
        )
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.width(320.dp).height(640.dp)) {
                    ListeningHistoryReconciliationScreen(
                        ListeningHistoryReconciliationUiState.Content(content),
                        actions()
                    )
                }
            }
        }

        composeRule.onAllNodesWithText("復讐の歌 — Extended Title").assertCountEquals(2)
        composeRule.onAllNodesWithText(longAlbum, substring = true).assertCountEquals(2)
        composeRule.onNodeWithText("歴史的なインポート曲 — Remastered 2015").assertExists()
        composeRule.onNodeWithContentDescription(
            "Unlink 歴史的なインポート曲 — Remastered 2015 from 復讐の歌 — Extended Title"
        ).assertExists()
    }

    @Test fun narrowWidthSearchAndRatingWarningDialogsRemainAccessible() {
        val historical = source(90, "Imported Song With A Very Long Historical Name")
        val local = target(91, "ローカル曲 — Long Search Result", "長いアルバム名 Search Edition")
        val state = mutableStateOf(
            ReconciliationReviewContent(
                reviewItems = listOf(item(historical, local)),
                linkedItems = emptyList(),
                search = ReconciliationSearchState(
                    sourceIds = listOf(historical.identityId),
                    query = "ローカル",
                    results = listOf(local)
                )
            )
        )
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.width(320.dp).height(640.dp)) {
                    ListeningHistoryReconciliationScreen(
                        ListeningHistoryReconciliationUiState.Content(state.value),
                        actions()
                    )
                }
            }
        }
        composeRule.onNodeWithText("Choose from library").assertExists()
        composeRule.onNodeWithText("ローカル曲 — Long Search Result").assertExists()

        composeRule.runOnIdle {
            state.value = state.value.copy(
                search = null,
                confirmation = ReconciliationConfirmation.Link(
                    sources = listOf(historical),
                    target = local,
                    ratings = listOf(
                        ListeningIdentityReconciliationRatings(
                            sourceRating = 2,
                            targetRating = 5,
                            state = ListeningIdentityReconciliationRatingState.CONFLICTING_RATINGS
                        )
                    )
                )
            )
        }
        composeRule.onNodeWithText("Link imported history?").assertExists()
        composeRule.onNodeWithText("different ratings", substring = true).assertExists()
        composeRule.onNodeWithText("5-star rating", substring = true).assertExists()
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
