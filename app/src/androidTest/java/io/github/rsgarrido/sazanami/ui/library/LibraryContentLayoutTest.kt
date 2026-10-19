package io.github.rsgarrido.sazanami.ui.library

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.ui.BoundedArtworkFrame
import io.github.rsgarrido.sazanami.ui.home.homePinnedCardWidth
import io.github.rsgarrido.sazanami.ui.readableContentWidth
import io.github.rsgarrido.sazanami.ui.theme.SazanamiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LibraryContentLayoutTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun exactGridChoicesBoundArtworkPreserveMetadataRulesAndKeepTheScrollAnchor() {
        val width = mutableStateOf(412.dp)
        val preference = mutableStateOf(2)
        val songs = (1L..60L).map { id ->
            Song(id, "Track $id", "Artist $id", "Album $id", id.toInt(), 180_000L,
                Uri.parse("content://fixture/$id"), "/music/$id.mp3", "/music", null)
        }
        lateinit var state: LazyGridState
        var pixelsPerDp = 0f
        var playCalls = 0
        composeRule.setContent {
            state = rememberLazyGridState()
            pixelsPerDp = LocalDensity.current.density
            SazanamiTheme {
                Box(Modifier.requiredWidth(width.value).height(400.dp)) {
                    SongGrid(
                        songs, null, preference.value, emptySet(), emptySet(),
                        onSongClick = { _, _ -> playCalls++ },
                        onPlayNextClick = {}, onAddToQueueClick = {}, onToggleFavoriteClick = {},
                        onAddToPlaylistClick = {}, onEditSongTagsClick = {},
                        bottomContentPadding = 120.dp, gridState = state
                    )
                }
            }
        }
        val owner = state
        for (available in listOf(412.dp, 1100.dp)) {
            for (count in 2..5) {
                composeRule.runOnIdle { width.value = available; preference.value = count }
                composeRule.runOnIdle {
                    val firstRow = state.layoutInfo.visibleItemsInfo.filter { it.row == 0 }
                    assertEquals(count, firstRow.size)
                    assertTrue(firstRow.all { it.size.width <= 256f * pixelsPerDp + 1f })
                    assertSame(owner, state)
                    assertEquals(0, playCalls)
                }
                if (count <= 3) composeRule.onNodeWithText("Artist 1").assertExists()
                else composeRule.onNodeWithText("Artist 1").assertDoesNotExist()
            }
        }
        composeRule.runOnIdle { preference.value = 2; width.value = 412.dp }
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(20)
        val key = composeRule.runOnIdle { state.layoutInfo.visibleItemsInfo.first().key }
        composeRule.runOnIdle { width.value = 1100.dp }
        composeRule.runOnIdle {
            assertSame(owner, state)
            assertEquals(key, state.layoutInfo.visibleItemsInfo.first().key)
            assertEquals(20, state.firstVisibleItemIndex)
            assertEquals(0, playCalls)
        }
    }

    @Test
    fun homeAndCollectionSharedArtworkCanReturnResizeAndReverseUsingTheirExistingRegistry() {
        val detail = mutableStateOf(false)
        val width = mutableStateOf(1100.dp)
        val sourceScope = mutableStateOf(LibrarySharedArtworkSourceScope.LIBRARY_COLLECTION)
        var pixelsPerDp = 0f
        composeRule.setContent {
            pixelsPerDp = LocalDensity.current.density
            SazanamiTheme {
                // An inset content pane, with one existing shared registry for both endpoints.
                Box(Modifier.requiredWidth(width.value).fillMaxSize().padding(start = 24.dp)) {
                    LibrarySharedTransitionHost(
                        targetState = detail.value,
                        detailSourceScopes = { LibrarySharedArtworkDetailSourceScopes(album = sourceScope.value) },
                        transitionSpec = { fadeIn() togetherWith fadeOut() }
                    ) { showingDetail ->
                        if (showingDetail) {
                            Column(Modifier.fillMaxSize().readableContentWidth().verticalScroll(rememberScrollState())) {
                                BoundedArtworkFrame(0.84f, 360.dp) { artwork ->
                                    Box(artwork.librarySharedArtwork(albumDetailSharedArtworkKey("album"))
                                        .clip(RoundedCornerShape(26.dp)).background(Color.Gray)
                                        .testTag("detail-artwork"))
                                }
                            }
                        } else {
                            val key = LibrarySharedArtworkKey.Album("album", sourceScope.value)
                            val size = if (sourceScope.value == LibrarySharedArtworkSourceScope.HOME_PINNED) {
                                homePinnedCardWidth(width.value - 56.dp) - 12.dp
                            } else 256.dp
                            if (sourceScope.value == LibrarySharedArtworkSourceScope.HOME_PINNED) {
                                Box(Modifier.size(size).librarySharedArtwork(key)
                                    .background(Color.Gray).testTag("source-artwork"))
                            } else {
                                LibrarySharedArtworkSource(key, RoundedCornerShape(18.dp), Modifier.size(size),
                                    LibrarySharedArtworkSourceSlotTreatment.NEUTRAL_SURFACE,
                                    hasResolvedArtwork = false) { artwork ->
                                    Box(artwork.background(Color.Gray).testTag("source-artwork"))
                                }
                            }
                        }
                    }
                }
            }
        }
        for (scope in LibrarySharedArtworkSourceScope.entries) {
            composeRule.runOnIdle { sourceScope.value = scope }
            composeRule.onNodeWithTag("source-artwork").assertExists()
            composeRule.runOnIdle { detail.value = true }
            for (available in listOf(1100.dp, 412.dp, 800.dp)) {
                composeRule.runOnIdle { width.value = available }
                val artwork = composeRule.onNodeWithTag("detail-artwork").fetchSemanticsNode().size
                assertTrue(artwork.width <= 360f * pixelsPerDp + 1f)
                assertEquals(artwork.width, artwork.height)
            }
            composeRule.runOnIdle { detail.value = false }
            composeRule.onNodeWithTag("source-artwork").assertExists()
            composeRule.mainClock.autoAdvance = false
            composeRule.runOnIdle { detail.value = true }
            composeRule.mainClock.advanceTimeBy(64)
            composeRule.runOnIdle { detail.value = false; width.value = 700.dp }
            composeRule.mainClock.advanceTimeBy(500)
            composeRule.mainClock.autoAdvance = true
            composeRule.onNodeWithTag("source-artwork").assertExists()
            composeRule.onNodeWithTag("detail-artwork").assertDoesNotExist()
        }
    }
}
