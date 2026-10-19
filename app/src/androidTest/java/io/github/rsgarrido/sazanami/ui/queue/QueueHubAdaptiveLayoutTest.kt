package io.github.rsgarrido.sazanami.ui.queue

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.pressBack
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.controller.PlaybackQueueCardUiState
import io.github.rsgarrido.sazanami.controller.PlaybackQueueEntryUiState
import io.github.rsgarrido.sazanami.controller.PlaybackQueueHubUiState
import io.github.rsgarrido.sazanami.data.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Real Material sheet and production queue content; callback sinks never invoke playback. */
class QueueHubAdaptiveLayoutTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun expandedSheetKeepsMaterialWidthCapAndBottomAnchorWhileCompactRowsKeepTheirSize() {
        // Do not cap the anchor modifier's incoming height in this bottom-anchoring test.
        val fixture = Fixture().apply { maximumHeight.value = Dp.Infinity }
        composeRule.setContent { fixture.Content() }
        val expanded = placedSheetGeometry()
        assertEquals("Sheet fills available native width up to 640 dp; $expanded",
            minOf(expanded.root.width, 640f * expanded.density), expanded.content.width, 1f)
        assertEquals("Sheet is centered; $expanded", expanded.root.center.x, expanded.content.center.x, 1f)
        assertEquals("Sheet is bottom anchored; $expanded", expanded.root.bottom, expanded.content.bottom, 1f)

        composeRule.runOnIdle { fixture.maximumWidth.value = 360.dp }
        val compact = placedSheetGeometry()
        assertEquals("Caller cap is 360 dp, limited by the native root; $compact",
            minOf(compact.root.width, 360f * compact.density), compact.content.width, 1f)
        assertEquals("Compact sheet is centered; $compact", compact.root.center.x, compact.content.center.x, 1f)
        assertEquals("Compact sheet is bottom anchored; $compact", compact.root.bottom, compact.content.bottom, 1f)
        val row = composeRule.onNodeWithTag("queue-entry-entry-1")
            .assertIsDisplayed().assertHasClickAction().fetchSemanticsNode()
        composeRule.runOnIdle {
            val density = row.layoutInfo.density
            // coordinates.size excludes the Row's top/bottom padding (48 dp content here).
            // LayoutInfo.height measures the full row, including that padding and its modifiers.
            val rowHeight = row.layoutInfo.height.toFloat()
            assertTrue("Row retains its 64 dp minimum: heightPx=$rowHeight density=$density",
                rowHeight >= 64f * density.density - 1f)
            if (density.fontScale <= 1f) {
                assertEquals("Normal-font compact row retains 64 dp; density=$density",
                    64f * density.density, rowHeight, 1f)
            }
        }
        composeRule.onNodeWithContentDescription(text(R.string.queue_hub_close)).assertIsDisplayed()
        composeRule.runOnIdle { assertTrue(fixture.calls.isEmpty()) }
        composeRule.onNodeWithText("Saved queue with a long descriptive name", useUnmergedTree = true)
            .performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals("B", fixture.selected.value)
            assertEquals(listOf("select:B"), fixture.calls)
        }
        composeRule.onNodeWithText(text(R.string.queue_hub_switch)).performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(listOf("select:B", "switch:B"), fixture.calls) }
    }

    @Test
    fun narrowingAnOpenSheetKeepsEntryScrollAnchorAndExistingPlayAction() {
        val fixture = Fixture()
        composeRule.setContent { fixture.Content() }
        composeRule.onNodeWithTag("queue-hub-entry-list").performScrollToIndex(15)
        val anchor = firstVisibleEntry()
        assertEquals("queue-entry-entry-15", anchor)
        composeRule.runOnIdle { fixture.maximumWidth.value = 360.dp }
        assertEquals(anchor, firstVisibleEntry())
        composeRule.runOnIdle {
            assertEquals("A", fixture.selected.value)
            assertTrue(fixture.calls.isEmpty())
            assertEquals(0, fixture.dismissals)
        }
        composeRule.onNodeWithTag(anchor).performClick()
        composeRule.runOnIdle { assertEquals(listOf("play:A:entry-15"), fixture.calls) }
    }

    @Test
    fun shortLargeTextSheetScrollsManagementSeparatelyAndKeepsRowsAndCloseReachable() {
        val fixture = Fixture().apply {
            maximumWidth.value = 420.dp
            maximumHeight.value = 500.dp
            fontScale.value = 2.5f
            selected.value = "B"
        }
        composeRule.setContent { fixture.Content() }
        val sheet = sheetBounds()
        val entries = composeRule.onNodeWithTag("queue-hub-entry-list")
        val accessories = composeRule.onNodeWithTag("queue-hub-accessories")
        val listBounds = entries.fetchSemanticsNode().boundsInRoot
        assertTrue("Management must leave a positive entry viewport", listBounds.height > 0f)
        assertInside(sheet, listBounds)
        composeRule.onNodeWithContentDescription(text(R.string.queue_hub_close)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.queue_hub_switch))
            .performScrollTo().assertIsDisplayed().performClick()
        entries.performScrollToIndex(1)
        val row = composeRule.onNodeWithTag("queue-entry-entry-1").fetchSemanticsNode()
        assertTrue("Swipe rows must grow for enlarged metadata", row.size.height > 64f * TestDensity)
        composeRule.onNode(
            hasContentDescription(text(R.string.queue_entry_actions)) and
                hasAnyAncestor(hasTestTag("queue-entry-entry-1")),
            useUnmergedTree = true
        ).assertIsDisplayed().performClick()
        composeRule.onNodeWithText(text(R.string.queue_remove)).performClick()
        // Scroll management back without disturbing the independently retained entry anchor.
        accessories.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -10_000f) }
        composeRule.onNodeWithContentDescription(text(R.string.queue_hub_new_from_current))
            .assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("switch:B", "remove:B:entry-1", "create"), fixture.calls)
            assertEquals(0, fixture.dismissals)
        }
    }

    @Test
    fun compactAndExpandedSheetRetainCloseBackAndScrimDismissal() {
        val fixture = Fixture().apply { maximumHeight.value = Dp.Infinity }
        composeRule.setContent { fixture.Content() }
        val expanded = placedSheetGeometry()
        composeRule.onNodeWithContentDescription(text(R.string.queue_hub_close)).performClick()
        composeRule.onNodeWithTag(SheetTag).assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(1, fixture.dismissals)
            fixture.maximumWidth.value = 360.dp
            fixture.visible.value = true
        }
        composeRule.onNodeWithTag(SheetTag).assertIsDisplayed()
        pressBack()
        composeRule.onNodeWithTag(SheetTag).assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(2, fixture.dismissals)
            // Reserve a side scrim in the real modal window, even on a narrow phone where the
            // default sheet legitimately fills the width. No dependency on a top/status-bar gap.
            fixture.maximumWidth.value = (expanded.root.width / expanded.density * 0.8f).dp
            fixture.visible.value = true
        }
        val dialog = composeRule.onNode(isDialog())
        val sheet = placedSheetGeometry()
        assertTrue("Fixture must expose a right-side scrim; $sheet", sheet.content.right < sheet.root.right)
        val tapInRoot = Offset((sheet.content.right + sheet.root.right) / 2f, sheet.root.center.y)
        val dialogBounds = dialog.fetchSemanticsNode().boundsInRoot
        assertTrue("Scrim tap must be inside the modal root: tap=$tapInRoot dialog=$dialogBounds; $sheet",
            dialogBounds.contains(tapInRoot))
        assertTrue("Scrim tap must be outside the placed sheet: tap=$tapInRoot; $sheet",
            !sheet.content.contains(tapInRoot))
        dialog.performTouchInput { click(tapInRoot - dialogBounds.topLeft) }
        composeRule.onNodeWithTag(SheetTag).assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(3, fixture.dismissals)
            assertTrue(fixture.calls.isEmpty())
        }
    }

    @Test
    fun unavailableTracksHintSharesManagementViewportInsteadOfObscuringEntriesAtLargeText() {
        val fixture = Fixture().apply {
            maximumWidth.value = 420.dp
            maximumHeight.value = 500.dp
            fontScale.value = 2.5f
            selected.value = "B"
            unavailableTracks.value = true
        }
        composeRule.setContent { fixture.Content() }
        val entries = composeRule.onNodeWithTag("queue-hub-entry-list")
        assertTrue(entries.fetchSemanticsNode().boundsInRoot.height > 0f)
        val hint = composeRule.onNodeWithText(text(R.string.queue_hub_tracks_unavailable))
            .performScrollTo().assertIsDisplayed()
        assertInside(composeRule.onNodeWithTag("queue-hub-accessories")
            .fetchSemanticsNode().boundsInRoot, hint.fetchSemanticsNode().boundsInRoot)
        entries.performScrollToIndex(12)
        assertEquals("queue-entry-entry-12", firstVisibleEntry())
        composeRule.onNodeWithContentDescription(text(R.string.queue_hub_close)).assertIsDisplayed()
        composeRule.runOnIdle {
            assertTrue(fixture.calls.isEmpty())
            assertEquals(0, fixture.dismissals)
        }
    }

    private fun sheetBounds() = composeRule.onNodeWithTag(SheetTag).fetchSemanticsNode().boundsInRoot

    private data class PlacedSheetGeometry(
        val content: Rect,
        val root: Rect,
        val taggedBounds: Rect,
        val density: Float,
        val fontScale: Float
    ) {
        override fun toString(): String =
            "contentRootPx=$content; rootPx=$root; taggedRootPx=$taggedBounds; density=$density; " +
                "fontScale=$fontScale; contentWidthDp=${content.width / density}; availableWidthDp=${root.width / density}"
    }

    private fun placedSheetGeometry(): PlacedSheetGeometry {
        val node = composeRule.onNodeWithTag(SheetTag).fetchSemanticsNode()
        return composeRule.runOnIdle {
            // The caller tag precedes Material's draggable anchor modifier. Read the actual
            // content coordinates after all modifiers, not that outer semantics rectangle.
            val coordinates = node.layoutInfo.coordinates
            val density = node.layoutInfo.density
            PlacedSheetGeometry(coordinates.boundsInRoot(),
                coordinates.findRootCoordinates().boundsInRoot(), node.boundsInRoot,
                density.density, density.fontScale)
        }
    }

    private fun firstVisibleEntry(): String {
        val viewport = composeRule.onNodeWithTag("queue-hub-entry-list")
            .fetchSemanticsNode().boundsInRoot
        return composeRule.onAllNodes(SemanticsMatcher("queue entry") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("queue-entry-") == true
        }, useUnmergedTree = true).fetchSemanticsNodes()
            .filter { it.boundsInRoot.bottom > viewport.top + 1f && it.boundsInRoot.top < viewport.bottom - 1f }
            .minBy { it.boundsInRoot.top }.config[SemanticsProperties.TestTag]
    }

    private fun text(resource: Int) = composeRule.activity.getString(resource)

    private fun assertInside(parent: Rect, child: Rect) {
        assertTrue("Expected $child inside $parent", child.left >= parent.left - 1f &&
            child.right <= parent.right + 1f && child.top >= parent.top - 1f &&
            child.bottom <= parent.bottom + 1f)
    }

    private class Fixture {
        val maximumWidth = mutableStateOf(Dp.Infinity)
        val maximumHeight = mutableStateOf(900.dp)
        val fontScale = mutableStateOf(1f)
        val visible = mutableStateOf(true)
        val selected = mutableStateOf("A")
        val unavailableTracks = mutableStateOf(false)
        val calls = mutableListOf<String>()
        var dismissals = 0
        private val songs = List(40) { index ->
            Song(index.toLong(), "Track $index — a long queue title", "Artist $index", "Album", index,
                180_000L, Uri.parse("content://fixture/song/$index"), "/music/$index.mp3", "/music", null)
        }
        private val entries = songs.mapIndexed { index, song ->
            PlaybackQueueEntryUiState("entry-$index", song, index == 0)
        }
        private val unavailableEntries = entries.map { it.copy(song = null) }

        @Composable
        fun Content() {
            // These caller locals do not replace the native modal owner's density/font scale.
            // Geometry expectations must read the actual modal layout, not TestDensity.
            CompositionLocalProvider(LocalDensity provides Density(TestDensity, fontScale.value)) {
                MaterialTheme {
                    if (visible.value) {
                        QueueHubSheet(
                            state = PlaybackQueueHubUiState(
                                isLoading = false,
                                queues = listOf(card("A", "Active queue", true),
                                    card("B", "Saved queue with a long descriptive name", false)),
                                activeQueueId = "A",
                                selectedQueueId = selected.value,
                                selectedEntries = if (unavailableTracks.value) unavailableEntries else entries,
                                selectedQueueEntryCount = entries.size
                            ),
                            onDismiss = { dismissals++; visible.value = false },
                            onQueueSelected = { calls += "select:$it"; selected.value = it },
                            onSwitchSelected = { calls += "switch:${selected.value}" },
                            onCreateFromCurrent = { calls += "create" },
                            onRename = { id, name -> calls += "rename:$id:$name" },
                            onDelete = { calls += "delete:$it" },
                            onRemoveEntry = { queue, entry -> calls += "remove:$queue:$entry" },
                            onPlayEntry = { queue, entry -> calls += "play:$queue:$entry" },
                            onReorderEntry = { queue, entry, index -> calls += "reorder:$queue:$entry:$index" },
                            onMessageDismissed = { calls += "clear-message" },
                            modifier = Modifier.widthIn(max = maximumWidth.value)
                                .heightIn(max = maximumHeight.value).testTag(SheetTag)
                        )
                    }
                }
            }
        }

        private fun card(id: String, name: String, active: Boolean) = PlaybackQueueCardUiState(
            queueId = id, name = name, entryCount = entries.size, currentPosition = 1,
            currentTrack = songs.first(), representativeTrack = songs.first(), lastActiveAt = 1L,
            isActive = active, isSelected = selected.value == id
        )
    }

    companion object {
        private const val TestDensity = 1f
        private const val SheetTag = "session5-queue-sheet"
    }
}
