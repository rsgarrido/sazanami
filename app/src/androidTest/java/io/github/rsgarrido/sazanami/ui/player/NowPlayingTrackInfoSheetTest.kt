package io.github.rsgarrido.sazanami.ui.player

import android.net.Uri
import android.graphics.Rect as AndroidRect
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.CoordinatesProvider
import androidx.test.espresso.action.GeneralClickAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Tap
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.player.audioquality.AudioQualityInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

class NowPlayingTrackInfoSheetTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun songMetadataAppearsBeforeAudioAndLongLocationsRemainScrollable() {
        val longPath = "/storage/emulated/0/Music/" + "Long album folder/".repeat(12) + "track.flac"
        val pending = CompletableDeferred<AudioQualityInfo>()
        composeRule.setContent {
            MaterialTheme {
                NowPlayingTrackInfoSheet(NowPlayingTrackInfoRequest(song(1).copy(filePath = longPath)), {},
                    loadAudio = { pending.await() })
            }
        }
        try {
            composeRule.onNodeWithText("Track information").assertExists()
            composeRule.onNodeWithTag(TrackInfoDetailsTag).performScrollToNode(hasText("File"))
            composeRule.onNodeWithTag(TrackInfoDetailsTag).performScrollToNode(hasText(longPath))
            composeRule.onNodeWithText(longPath).assertExists()
            composeRule.runOnIdle { pending.complete(AudioQualityInfo("FLAC", 16, 44_100, 830, "audio/flac", 2)) }
            composeRule.onNodeWithTag(TrackInfoDetailsTag).performScrollToNode(hasText("44.1 kHz"))
            composeRule.onNodeWithText("44.1 kHz").assertExists()
            composeRule.onNodeWithTag(TrackInfoDetailsTag).performScrollToNode(hasText("Stereo / 2 channels"))
            composeRule.onNodeWithText("Stereo / 2 channels").assertExists()
        } finally {
            pending.complete(AudioQualityInfo(null, null, null, null))
        }
    }

    @Test fun oldExtractionCannotPopulateANewInformationTarget() {
        val firstStarted = CompletableDeferred<Unit>()
        val oldAudio = CompletableDeferred<AudioQualityInfo>()
        val request = mutableStateOf(NowPlayingTrackInfoRequest(song(1)))
        composeRule.setContent {
            MaterialTheme {
                NowPlayingTrackInfoSheet(request.value, {}, loadAudio = { target ->
                    if (target.id == 1L) withContext(NonCancellable) {
                        firstStarted.complete(Unit)
                        oldAudio.await()
                    } else AudioQualityInfo("FLAC", 24, 48_000, 1000)
                })
            }
        }
        try {
            composeRule.waitUntil { firstStarted.isCompleted }
            composeRule.runOnIdle { request.value = NowPlayingTrackInfoRequest(song(2)) }
            composeRule.onNodeWithTag(TrackInfoDetailsTag).performScrollToNode(hasText("48 kHz"))
            composeRule.runOnIdle { oldAudio.complete(AudioQualityInfo("FLAC", 16, 96_000, 2000)) }
            composeRule.onNodeWithText("48 kHz").assertExists()
            composeRule.onNodeWithText("96 kHz").assertDoesNotExist()
        } finally {
            oldAudio.complete(AudioQualityInfo(null, null, null, null))
        }
    }

    @Test fun backCloseAndScrimDismissOnlyTheInformationSheet() {
        val visible = mutableStateOf(true)
        val target = song(1).copy(artist = "", album = "", albumArtist = "", trackNumber = 0,
            duration = 0, filePath = "", fileSizeBytes = 0, year = null, genres = emptyList())
        val currentSong = mutableStateOf(target)
        lateinit var player: PlayerMorphState
        var playerClicks = 0
        var playerBacks = 0
        var dismissed = 0
        composeRule.setContent {
            MaterialTheme {
                player = rememberPlayerMorphState(PlayerPresentation.Expanded)
                BackHandler { playerBacks++; player.collapse() }
                Box(Modifier.fillMaxSize().testTag("track-information-player").clickable {
                    playerClicks++
                    currentSong.value = song(2)
                    player.collapse()
                }) { Text(currentSong.value.title) }
                if (visible.value) NowPlayingTrackInfoSheet(NowPlayingTrackInfoRequest(target),
                    onDismiss = { dismissed++; visible.value = false },
                    loadAudio = { AudioQualityInfo(null, null, null, null) })
            }
        }
        fun assertPlayerIntact(expectedDismissals: Int) {
            composeRule.onNodeWithTag(TrackInfoSheetTag).assertDoesNotExist()
            composeRule.onNodeWithTag("track-information-player").assertIsDisplayed()
            composeRule.onNodeWithText(target.title).assertIsDisplayed()
            composeRule.runOnIdle {
                assertEquals(expectedDismissals, dismissed)
                assertEquals(0, playerClicks)
                assertEquals(0, playerBacks)
                assertSame(target, currentSong.value)
                assertEquals(PlayerPresentation.Expanded, player.targetPresentation)
                assertEquals(PlayerPresentation.Expanded, player.settledPresentation)
                assertEquals(1f, player.progress, 0f)
            }
        }
        val closeDescription = composeRule.activity.getString(R.string.common_close)
        composeRule.onNodeWithTag(TrackInfoSheetTag).assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription(closeDescription).assertCountEquals(1)
        composeRule.onNodeWithText("Done").assertDoesNotExist()
        Espresso.pressBack()
        assertPlayerIntact(1)
        composeRule.runOnIdle { visible.value = true }
        composeRule.onNodeWithContentDescription(closeDescription).assertIsDisplayed().performClick()
        assertPlayerIntact(2)
        composeRule.runOnIdle { visible.value = true }
        // Auto-advancing Compose synchronization finishes Material's show animation before placement is read.
        composeRule.waitForIdle()
        val sheetNode = composeRule.onNodeWithTag(TrackInfoSheetTag).assertIsDisplayed().fetchSemanticsNode()
        Espresso.onView(isRoot()).inRoot(isDialog()).perform(GeneralClickAction(
            Tap.SINGLE,
            CoordinatesProvider { dialogDecor ->
                // The caller tag precedes Material's anchor modifier. Use the placed layout,
                // as QueueHubAdaptiveLayoutTest does, rather than the outer semantics rectangle.
                val coordinates = sheetNode.layoutInfo.coordinates
                assertTrue("Sheet coordinates must remain attached", coordinates.isAttached)
                val rootCoordinates = coordinates.findRootCoordinates()
                val sheet = coordinates.boundsInRoot()
                val root = rootCoordinates.boundsInRoot()
                val originOnScreen = rootCoordinates.localToScreen(Offset.Zero)
                val density = sheetNode.layoutInfo.density
                val geometry = "sheetRootPx=$sheet; rootPx=$root; originScreenPx=$originOnScreen; " +
                    "density=${density.density}; fontScale=${density.fontScale}"
                assertTrue("Root must have a valid screen origin; $geometry",
                    originOnScreen.x.isFinite() && originOnScreen.y.isFinite())
                assertTrue("Settled sheet must have visible content; $geometry", sheet.width > 0f && sheet.height > 0f)
                val activityDecor = composeRule.activity.window.decorView
                val activityPosition = IntArray(2)
                activityDecor.getLocationOnScreen(activityPosition)
                val visibleScreen = AndroidRect()
                activityDecor.getWindowVisibleDisplayFrame(visibleScreen)
                assertTrue("Activity must have a visible viewport; $geometry", visibleScreen.intersect(
                    AndroidRect(activityPosition[0], activityPosition[1],
                        activityPosition[0] + activityDecor.width, activityPosition[1] + activityDecor.height)))
                val dialogPosition = IntArray(2)
                dialogDecor.getLocationOnScreen(dialogPosition)
                assertTrue("Scrim must lie inside the native modal window; $geometry", visibleScreen.intersect(
                    AndroidRect(dialogPosition[0], dialogPosition[1],
                        dialogPosition[0] + dialogDecor.width, dialogPosition[1] + dialogDecor.height)))
                val scrim = Rect(
                    max(root.left, visibleScreen.left - originOnScreen.x),
                    max(root.top, visibleScreen.top - originOnScreen.y),
                    min(root.right, visibleScreen.right - originOnScreen.x),
                    min(sheet.top, min(root.bottom, visibleScreen.bottom - originOnScreen.y))
                )
                assertTrue("The compact sheet must leave a real visible scrim above it; " +
                    "$geometry; visibleScreenPx=$visibleScreen; scrimRootPx=$scrim",
                    scrim.width > 0f && scrim.height > 0f)
                val tapInRoot = scrim.center
                assertTrue("Scrim tap must be inside the root; $geometry", root.contains(tapInRoot))
                assertTrue("Scrim tap must be outside the placed sheet; $geometry", !sheet.contains(tapInRoot))
                // Both measurements are root pixels. Translate the chosen point to screen pixels once.
                val tapOnScreen = tapInRoot + originOnScreen
                assertTrue("Scrim tap must avoid system bars; $geometry",
                    visibleScreen.contains(tapOnScreen.x.toInt(), tapOnScreen.y.toInt()))
                floatArrayOf(tapOnScreen.x, tapOnScreen.y)
            },
            Press.FINGER
        ))
        assertPlayerIntact(3)
    }

    @Test fun headerStaysFixedAndFastMetadataFlingsNeverMoveOrDismissTheModal() {
        val longPath = "/Music/" + "Album folder/".repeat(180) + "track.flac"
        var dismissed = 0
        composeRule.setContent {
            MaterialTheme {
                NowPlayingTrackInfoSheet(NowPlayingTrackInfoRequest(song(1).copy(filePath = longPath)),
                    onDismiss = { dismissed++ },
                    loadAudio = { AudioQualityInfo("FLAC", 16, 44_100, 830, "audio/flac", 2) })
            }
        }
        val headerBounds = composeRule.onNodeWithTag(TrackInfoHeaderTag).fetchSemanticsNode().boundsInRoot
        val sheetBounds = composeRule.onNodeWithTag(TrackInfoSheetTag).fetchSemanticsNode().boundsInRoot
        composeRule.onNodeWithTag(TrackInfoDetailsTag).performScrollToNode(hasText("Stereo / 2 channels"))
        composeRule.onNodeWithText("Stereo / 2 channels").assertIsDisplayed()
        composeRule.onNodeWithText("Track information").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.common_close)).assertIsDisplayed()
        composeRule.onNodeWithTag(TrackInfoDetailsTag).performTouchInput {
            swipe(Offset(center.x, height * 0.85f), Offset(center.x, height * 0.15f), durationMillis = 80)
        }
        composeRule.onNodeWithTag(TrackInfoDetailsTag).performScrollToNode(hasText("Track"))
        composeRule.onNodeWithTag(TrackInfoDetailsTag).performTouchInput {
            swipe(Offset(center.x, height * 0.15f), Offset(center.x, height * 0.85f), durationMillis = 80)
        }
        composeRule.onNodeWithTag(TrackInfoHeaderTag).performTouchInput {
            val start = Offset(width * 0.25f, height * 0.4f)
            swipe(start, start + Offset(0f, 200f), durationMillis = 120)
        }
        composeRule.onNodeWithTag(TrackInfoSheetTag).performTouchInput {
            swipe(Offset(width * 0.25f, 4f), Offset(width * 0.25f, 204f), durationMillis = 120)
        }
        composeRule.onNodeWithTag(TrackInfoSheetTag).assertExists()
        assertEquals(headerBounds, composeRule.onNodeWithTag(TrackInfoHeaderTag).fetchSemanticsNode().boundsInRoot)
        assertEquals(sheetBounds, composeRule.onNodeWithTag(TrackInfoSheetTag).fetchSemanticsNode().boundsInRoot)
        composeRule.onAllNodes(SemanticsMatcher("sheet expand/collapse actions") {
            it.config.contains(SemanticsActions.Expand) || it.config.contains(SemanticsActions.Collapse)
        }).assertCountEquals(0)
        composeRule.runOnIdle { assertEquals(0, dismissed) }
    }

    private fun song(id: Long) = Song(id, "Track $id", "Artist", "Album", 1001, 185_000L,
        Uri.parse("content://media/external/audio/media/$id"), "/music/track.flac", "/music", null,
        volumeName = "external", fileSizeBytes = 1536, albumArtist = "Album Artist", year = 2024,
        genres = listOf("Rock", "Metal"))
}
