package io.github.rsgarrido.sazanami.ui

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.core.app.ActivityOptionsCompat
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.data.BatchArtworkReference
import io.github.rsgarrido.sazanami.data.BatchEditIntent
import io.github.rsgarrido.sazanami.data.BatchArtworkValue
import io.github.rsgarrido.sazanami.data.BatchMetadataEditorState
import io.github.rsgarrido.sazanami.data.BatchMetadataExecutionResult
import io.github.rsgarrido.sazanami.data.BatchMetadataField
import io.github.rsgarrido.sazanami.data.BatchMetadataOperationState
import io.github.rsgarrido.sazanami.data.BatchMetadataPlan
import io.github.rsgarrido.sazanami.data.BatchMetadataProgress
import io.github.rsgarrido.sazanami.data.BatchPostWriteStageResult
import io.github.rsgarrido.sazanami.data.BatchPostWriteStageStatus
import io.github.rsgarrido.sazanami.data.BatchTargetResult
import io.github.rsgarrido.sazanami.data.BatchTargetStatus
import io.github.rsgarrido.sazanami.data.BatchTerminalOutcome
import io.github.rsgarrido.sazanami.data.EditableSongTags
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.deriveBatchMetadataEditorState
import io.github.rsgarrido.sazanami.ui.tageditor.BatchMetadataActions
import io.github.rsgarrido.sazanami.ui.tageditor.BatchMetadataEditorContext
import io.github.rsgarrido.sazanami.ui.tageditor.TagEditorActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MusicMetadataPresentationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun singleEditorKeepsReadKeysAndDraftAndDelegatesSaveWithSelectedArtwork() {
        val fixture = Fixture(listOf(song(1), song(2)))
        val target = fixture.songs.first()
        val artwork = Uri.parse("content://artwork/selected")
        fixture.tagTarget.value = target
        fixture.artwork.value = artwork
        composeRule.setContent { fixture.Content() }
        composeRule.onNodeWithText(text(R.string.metadata_edit_tags)).assertExists()
        field(R.string.metadata_title).performScrollTo().performTextReplacement("Edited title")
        composeRule.runOnIdle {
            assertTrue(fixture.dirty.value)
            fixture.currentSongId.value = null
            fixture.tagTarget.value = target.copy(title = "Refreshed library title")
        }
        field(R.string.metadata_title).assertExists()
        composeRule.onNodeWithText("Edited title").assertExists()
        composeRule.runOnIdle {
            assertEquals(1, fixture.tagReads)
            assertEquals(1, fixture.capabilityReads)
        }
        composeRule.onNodeWithText(text(R.string.metadata_save)).performScrollTo().performClick()
        composeRule.runOnIdle {
            val saved = fixture.saves.single()
            assertSame(fixture.tagTarget.value, saved.song)
            assertEquals("Edited title", saved.tags.title)
            assertSame(artwork, saved.artwork)
        }
    }

    @Test
    fun singleCancelAndDiscardKeepUsingScreenOwnedCloseAndDiscardPaths() {
        val fixture = Fixture(listOf(song(1), song(2)))
        val target = fixture.songs.first()
        fixture.tagTarget.value = target
        composeRule.setContent { fixture.Content() }
        composeRule.onNodeWithText(text(R.string.metadata_cancel)).performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(1, fixture.tagCloseRequests)
            assertNull(fixture.tagTarget.value)
            fixture.tagTarget.value = target
        }
        field(R.string.metadata_title).performScrollTo().performTextReplacement("Unsaved title")
        composeRule.onNodeWithText(text(R.string.metadata_cancel)).performScrollTo().performClick()
        composeRule.onNodeWithText(text(R.string.metadata_discard_confirm_title)).assertExists()
        composeRule.onNodeWithText(text(R.string.metadata_keep_editing)).performClick()
        composeRule.runOnIdle {
            assertSame(target, fixture.tagTarget.value)
            assertTrue(fixture.dirty.value)
            assertFalse(fixture.discardVisible.value)
        }
        composeRule.onNodeWithText("Unsaved title").assertExists()
        composeRule.onNodeWithText(text(R.string.metadata_cancel)).performScrollTo().performClick()
        composeRule.onNodeWithText(text(R.string.metadata_discard)).performClick()
        composeRule.runOnIdle {
            assertEquals(1, fixture.discards)
            assertNull(fixture.tagTarget.value)
            assertFalse(fixture.dirty.value)
            assertNull(fixture.artwork.value)
        }
    }

    @Test
    fun tagArtworkUsesTheScreenRegisteredGetContentLauncherAcrossUnmounts() {
        val fixture = Fixture(listOf(song(1), song(2)))
        val target = fixture.songs.first()
        fixture.tagTarget.value = target
        composeRule.setContent { fixture.Content() }
        composeRule.onNodeWithText(text(R.string.metadata_change_artwork)).performClick()
        var requestCode = 0
        val artwork = Uri.parse("content://artwork/late-result")
        composeRule.runOnIdle {
            val request = fixture.registry.requests.single()
            assertTrue(request.contract is ActivityResultContracts.GetContent)
            assertEquals("image/*", request.input)
            requestCode = request.code
            fixture.showPresentation.value = false
        }
        composeRule.runOnIdle {
            fixture.registry.dispatchResult(requestCode, artwork)
            assertSame(artwork, fixture.artwork.value)
            assertSame(target, fixture.tagTarget.value)
            fixture.showPresentation.value = true
        }
        composeRule.onNodeWithText(text(R.string.metadata_new_artwork_selected)).assertExists()
        composeRule.onNodeWithText(text(R.string.metadata_choose_different_artwork)).performClick()
        composeRule.runOnIdle { assertEquals(requestCode, fixture.registry.requests.last().code) }
    }

    @Test
    fun batchEditorUpdatesOwnerStateUsesOpenDocumentAndAppliesTheExistingAction() {
        val fixture = Fixture(listOf(song(1), song(2)))
        fixture.batchEditor.value = fixture.initialBatch
        fixture.batchContext.value = BatchMetadataEditorContext.Album("/music", "Album", null)
        composeRule.setContent { fixture.Content() }
        composeRule.onNodeWithText(text(R.string.metadata_edit_album_metadata)).assertExists()
        field(R.string.metadata_album).performScrollTo().performTextReplacement("Changed album")
        composeRule.onNodeWithText(text(R.string.metadata_choose_replacement)).performScrollTo().performClick()
        val artwork = Uri.parse("content://artwork/batch")
        composeRule.runOnIdle {
            val request = fixture.registry.requests.single()
            assertTrue(request.contract is ActivityResultContracts.OpenDocument)
            assertEquals(listOf("image/*"), (request.input as Array<*>).toList())
            fixture.registry.dispatchResult(request.code, artwork)
        }
        composeRule.onNodeWithText(text(R.string.metadata_review_apply)).performScrollTo().performClick()
        composeRule.onNodeWithText(text(R.string.metadata_apply)).performClick()
        composeRule.onNodeWithText(text(R.string.metadata_preparing_batch)).assertExists()
        composeRule.runOnIdle {
            val expected = fixture.initialBatch.set(BatchMetadataField.ALBUM, "Changed album")
                .replaceArtwork(BatchArtworkReference(artwork.toString(), artwork.toString())).plan()
            assertEquals(expected, fixture.applied.single())
            assertEquals(expected, (fixture.execution.value as BatchMetadataOperationState.Preparing).plan)
            assertEquals(BatchEditIntent.Set(BatchArtworkValue.Present(
                BatchArtworkReference(artwork.toString(), artwork.toString())
            )), fixture.batchEditor.value?.artwork?.intent)
        }
    }

    @Test
    fun executionHasPriorityAndSwitchingPresentationPreservesScreenOwnedState() {
        val fixture = Fixture(listOf(song(1), song(2)))
        val target = fixture.songs.first()
        val editedBatch = fixture.initialBatch.set(BatchMetadataField.ALBUM, "Pending album")
        val context = BatchMetadataEditorContext.Album("/music", "Album", null)
        val artwork = Uri.parse("content://artwork/pending")
        fixture.tagTarget.value = target
        fixture.artwork.value = artwork
        fixture.batchEditor.value = editedBatch
        fixture.batchContext.value = context
        fixture.execution.value = BatchMetadataOperationState.Running(
            "operation", editedBatch.plan(), BatchMetadataProgress(0, 2, null)
        )
        composeRule.setContent { fixture.Content() }
        composeRule.onNodeWithText(text(R.string.metadata_updating)).assertExists()
        composeRule.onNodeWithText(text(R.string.metadata_edit_tags)).assertDoesNotExist()
        composeRule.onNodeWithText(text(R.string.metadata_edit_album_metadata)).assertDoesNotExist()
        composeRule.onNodeWithText(text(R.string.metadata_cancel)).performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("cancel"), fixture.batchCalls)
            assertSame(editedBatch, fixture.batchEditor.value)
            assertSame(context, fixture.batchContext.value)
            assertSame(target, fixture.tagTarget.value)
            assertSame(artwork, fixture.artwork.value)
            fixture.execution.value = null
        }
        composeRule.onNodeWithText(text(R.string.metadata_edit_album_metadata)).assertExists()
        composeRule.onNodeWithText(text(R.string.metadata_edit_tags)).assertDoesNotExist()
        composeRule.runOnIdle { fixture.showPresentation.value = false }
        composeRule.runOnIdle {
            assertSame(editedBatch, fixture.batchEditor.value)
            assertSame(context, fixture.batchContext.value)
            assertSame(target, fixture.tagTarget.value)
            assertSame(artwork, fixture.artwork.value)
            fixture.showPresentation.value = true
        }
        composeRule.onNodeWithText(text(R.string.metadata_edit_album_metadata)).assertExists()
        composeRule.runOnIdle { fixture.batchEditor.value = null }
        composeRule.onNodeWithText(text(R.string.metadata_edit_tags)).assertExists()
        composeRule.runOnIdle { assertSame(artwork, fixture.artwork.value) }
    }

    @Test
    fun completedBatchDelegatesRetryContinueRefreshAndScreenCoordinatedClose() {
        val fixture = Fixture(listOf(song(1), song(2)))
        val plan = fixture.initialBatch.plan()
        fixture.batchEditor.value = fixture.initialBatch
        fixture.execution.value = BatchMetadataOperationState.Complete(
            "operation", plan,
            BatchMetadataExecutionResult(plan, listOf(
                BatchTargetResult(plan.selectedTargets[0], BatchTargetStatus.WRITE_FAILED),
                BatchTargetResult(plan.selectedTargets[1], BatchTargetStatus.NOT_PROCESSED)
            ), wasCancelled = false),
            BatchPostWriteStageResult.Success,
            BatchPostWriteStageResult(BatchPostWriteStageStatus.TIMED_OUT),
            BatchTerminalOutcome.PARTIAL_SUCCESS
        )
        composeRule.setContent { fixture.Content() }
        composeRule.onNodeWithText(text(R.string.metadata_retry_failed)).performScrollTo().performClick()
        composeRule.onNodeWithText(text(R.string.metadata_continue_unprocessed)).performScrollTo().performClick()
        composeRule.onNodeWithText(text(R.string.metadata_retry_refresh)).performScrollTo().performClick()
        composeRule.onNodeWithText(text(R.string.metadata_done)).performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("retry", "continue", "refresh", "close"), fixture.batchCalls)
            assertEquals(1, fixture.resultCloseRequests)
            assertNull(fixture.execution.value)
            assertNull(fixture.batchEditor.value)
            assertSame(BatchMetadataEditorContext.SongSelection, fixture.batchContext.value)
        }
    }

    @Test
    fun batchCancelAndDiscardDelegateToTheSameScreenCloseCallback() {
        val fixture = Fixture(listOf(song(1), song(2)))
        fixture.batchEditor.value = fixture.initialBatch
        composeRule.setContent { fixture.Content() }
        composeRule.onNodeWithText(text(R.string.metadata_cancel)).performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(1, fixture.batchCloseRequests)
            fixture.batchEditor.value = fixture.initialBatch.set(BatchMetadataField.ALBUM, "Changed")
        }
        composeRule.onNodeWithText(text(R.string.metadata_discard_changes)).performScrollTo().performClick()
        composeRule.onNodeWithText(text(R.string.metadata_keep_editing)).performClick()
        composeRule.runOnIdle {
            assertEquals(1, fixture.batchCloseRequests)
            assertEquals(1, fixture.batchEditor.value?.plan()?.changeCount)
        }
        composeRule.onNodeWithText(text(R.string.metadata_discard_changes)).performScrollTo().performClick()
        composeRule.onNodeWithText(text(R.string.metadata_discard)).performClick()
        composeRule.runOnIdle {
            assertEquals(2, fixture.batchCloseRequests)
            assertNull(fixture.batchEditor.value)
        }
    }

    @Test
    fun batchSelectionPassesSelectedSongsAndBlocksCloseDuringPreparation() {
        val fixture = Fixture(listOf(song(1), song(2)))
        fixture.selectionVisible.value = true
        composeRule.setContent { fixture.Content() }
        fixture.songs.forEach { composeRule.onNodeWithText(it.title).performClick() }
        composeRule.onNodeWithText(text(R.string.metadata_continue_count, 2)).performClick()
        composeRule.onNodeWithContentDescription(text(R.string.metadata_back)).assertIsNotEnabled()
        composeRule.runOnIdle {
            assertEquals(fixture.songs, fixture.preparedSongs.single())
            assertTrue(fixture.preparing.value)
            assertTrue(fixture.selectionVisible.value)
            fixture.preparing.value = false
        }
        composeRule.onNodeWithContentDescription(text(R.string.metadata_back)).performClick()
        composeRule.runOnIdle { assertFalse(fixture.selectionVisible.value) }
    }

    @Test
    fun emptyPresentationAndEditorStatesMatchExistingBottomNavigationSuppression() {
        val fixture = Fixture(listOf(song(1), song(2)))
        composeRule.setContent { fixture.Content() }
        composeRule.onNodeWithText(text(R.string.metadata_edit_tags)).assertDoesNotExist()
        composeRule.onNodeWithText(text(R.string.metadata_edit_metadata)).assertDoesNotExist()
        composeRule.runOnIdle {
            assertTrue(fixture.bottomNavigationVisible())
            fixture.tagTarget.value = fixture.songs.first()
            assertFalse(fixture.bottomNavigationVisible())
            fixture.tagTarget.value = null
            fixture.batchEditor.value = fixture.initialBatch
            assertFalse(fixture.bottomNavigationVisible())
            fixture.batchEditor.value = null
            fixture.execution.value = BatchMetadataOperationState.Interrupted("operation")
            assertFalse(fixture.bottomNavigationVisible())
            fixture.execution.value = null
            fixture.selectionVisible.value = true
            assertTrue(fixture.bottomNavigationVisible())
        }
    }

    private fun field(label: Int) = composeRule.onNode(hasSetTextAction() and hasText(text(label)))
    private fun text(resource: Int, vararg args: Any) = composeRule.activity.getString(resource, *args)

    private data class SavedTags(val song: Song, val tags: EditableSongTags, val artwork: Uri?)
    private data class PickerRequest(val code: Int, val contract: ActivityResultContract<*, *>, val input: Any?)

    private class RecordingRegistry : ActivityResultRegistry() {
        val requests = mutableListOf<PickerRequest>()
        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?
        ) {
            requests += PickerRequest(requestCode, contract, input)
        }
    }

    private class Fixture(val songs: List<Song>) {
        val registry = RecordingRegistry()
        val tagTarget = mutableStateOf<Song?>(null)
        val artwork = mutableStateOf<Uri?>(null)
        val saving = mutableStateOf(false)
        val dirty = mutableStateOf(false)
        val discardVisible = mutableStateOf(false)
        val initialBatch = deriveBatchMetadataEditorState(songs, ::tags)
        val batchEditor = mutableStateOf<BatchMetadataEditorState?>(null)
        val batchContext = mutableStateOf<BatchMetadataEditorContext>(BatchMetadataEditorContext.SongSelection)
        val execution = mutableStateOf<BatchMetadataOperationState?>(null)
        val selectionVisible = mutableStateOf(false)
        val preparing = mutableStateOf(false)
        val showPresentation = mutableStateOf(true)
        val currentSongId = mutableStateOf<Long?>(songs.first().id)
        val saves = mutableListOf<SavedTags>()
        val applied = mutableListOf<BatchMetadataPlan>()
        val batchCalls = mutableListOf<String>()
        val preparedSongs = mutableListOf<List<Song>>()
        var tagReads = 0
        var capabilityReads = 0
        var tagCloseRequests = 0
        var batchCloseRequests = 0
        var resultCloseRequests = 0
        var discards = 0

        fun bottomNavigationVisible() = shouldShowPrimaryBottomNavigation(
            isPlayerExpanded = false, isFolderScreenVisible = false, isDiagnosticsScreenVisible = false,
            isEqualizerScreenVisible = false, isStatisticsScreenVisible = false,
            isListeningHistoryImportVisible = false, isListeningHistoryReconciliationVisible = false,
            isSettingsScreenVisible = false,
            isTagEditorVisible = tagTarget.value != null || batchEditor.value != null || execution.value != null
        )

        @Composable
        fun Content() {
            val owner = remember {
                object : ActivityResultRegistryOwner {
                    override val activityResultRegistry: ActivityResultRegistry = registry
                }
            }
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                // Registration remains above the conditional presenter, just as it does in MusicScreen.
                val tagPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
                    if (it != null) artwork.value = it
                }
                val batchPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
                    if (it != null) batchEditor.value = batchEditor.value?.replaceArtwork(
                        BatchArtworkReference(it.toString(), it.toString())
                    )
                }
                val batchActions = BatchMetadataActions(
                    apply = {
                        applied += it
                        execution.value = BatchMetadataOperationState.Preparing("operation", it)
                    },
                    cancel = { batchCalls += "cancel" },
                    retryFailed = { batchCalls += "retry" },
                    continueUnprocessed = { batchCalls += "continue" },
                    retryRefresh = { batchCalls += "refresh" },
                    closeResults = { batchCalls += "close" }
                )
                MaterialTheme {
                    if (showPresentation.value) {
                        MusicMetadataPresentation(
                            songForTagEdit = tagTarget.value, batchEditorState = batchEditor.value,
                            batchExecutionState = execution.value, batchEditorContext = batchContext.value,
                            currentSongId = currentSongId.value, isTagSaveInProgress = saving.value,
                            selectedArtworkUri = artwork.value,
                            tagEditorActions = TagEditorActions { song, edited, uri -> saves += SavedTags(song, edited, uri) },
                            batchMetadataActions = batchActions,
                            onReadEditableSongTags = { tagReads++; tags(it) },
                            onGetUnsupportedTagEditingMessage = { capabilityReads++; null },
                            onRequestCloseTagEditor = {
                                tagCloseRequests++
                                if (!saving.value) {
                                    if (dirty.value || artwork.value != null) discardVisible.value = true
                                    else tagTarget.value = null
                                }
                            },
                            onChooseTagArtwork = { tagPicker.launch("image/*") },
                            onUnsavedTagChangesChanged = { dirty.value = it },
                            onBatchEditorStateChanged = { batchEditor.value = it },
                            onChooseBatchArtwork = { batchPicker.launch(arrayOf("image/*")) },
                            onCloseBatchEditor = {
                                batchCloseRequests++
                                batchEditor.value = null
                                batchContext.value = BatchMetadataEditorContext.SongSelection
                            },
                            onCloseBatchResults = {
                                resultCloseRequests++
                                batchActions.closeResults()
                                execution.value = null
                                batchEditor.value = null
                                batchContext.value = BatchMetadataEditorContext.SongSelection
                            }
                        )
                    }
                    MusicMetadataDialogs(
                        isBatchSongSelectionVisible = selectionVisible.value, songs = songs,
                        isBatchPreparationInProgress = preparing.value,
                        isDiscardTagChangesDialogVisible = discardVisible.value,
                        onDismissBatchSelection = { if (!preparing.value) selectionVisible.value = false },
                        onContinueBatchSelection = { preparedSongs += it; preparing.value = true },
                        onDismissTagDiscard = { discardVisible.value = false },
                        onConfirmTagDiscard = {
                            discards++
                            discardVisible.value = false
                            dirty.value = false
                            artwork.value = null
                            tagTarget.value = null
                        }
                    )
                }
            }
        }
    }

    private fun song(id: Long) = Song(
        id, "Track $id", "Artist", "Album", 1, 120_000L,
        Uri.parse("content://media/external/audio/media/$id"), "/music/$id.flac", "/music", null,
        volumeName = "external"
    )

    companion object {
        private fun tags(song: Song) = EditableSongTags(song.title, song.artist, song.album, "1", "2026")
    }
}
