package io.github.rsgarrido.sazanami.ui.settings

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.ui.state.SLEEP_TIMER_OPTIONS_MINUTES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SleepTimerAdaptiveLayoutTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun compactAndWideDialogKeepMaterialWidthAndDispatchTheSameTimerAction() {
        val width = mutableStateOf(360.dp)
        val visible = mutableStateOf(true)
        val starts = mutableListOf<Int>()
        var dismissals = 0
        composeRule.setContent {
            // The native Dialog supplies its own layout density and available width.
            MaterialTheme {
                if (visible.value) SleepTimerDialog(
                    isTimerActive = false,
                    sleepTimerDisplayText = "Timer fixture",
                    onStartTimerClick = { starts += it },
                    onCancelTimerClick = { error("Unexpected timer cancellation") },
                    onDismiss = { dismissals++; visible.value = false },
                    modifier = Modifier.widthIn(max = width.value).testTag("timer-surface")
                )
            }
        }
        val close = composeRule.activity.getString(R.string.common_close)
        val compact = placedTimerGeometry()
        assertEquals("Compact content fits its 360 dp cap and native window; $compact",
            minOf(compact.root.width, 360f * compact.density), compact.content.width, 1f)
        assertEquals("Compact content is centered; $compact", compact.root.center.x, compact.content.center.x, 1f)
        composeRule.onNodeWithText(close).assertIsDisplayed()
        composeRule.runOnIdle { width.value = 1200.dp }
        val wide = placedTimerGeometry()
        assertEquals("Wider caller cap still respects Material's 560 dp maximum and native window; $wide",
            minOf(wide.root.width, 560f * wide.density), wide.content.width, 1f)
        assertEquals("Wide content is centered; $wide", wide.root.center.x, wide.content.center.x, 1f)
        // A platform-default dialog window can be narrower than both caller caps. Equal widths
        // are then correct; the exact native-window-aware expectations above still detect errors.
        assertTrue("Increasing the caller cap must not shrink content: compact=$compact wide=$wide",
            wide.content.width >= compact.content.width - 1f)
        composeRule.onNodeWithText(close).assertIsDisplayed()
        composeRule.runOnIdle {
            assertTrue(starts.isEmpty())
            assertEquals(0, dismissals)
        }
        val minutes = SLEEP_TIMER_OPTIONS_MINUTES.last()
        val option = composeRule.activity.resources.getQuantityString(
            R.plurals.sleep_timer_minutes_option, minutes, minutes
        )
        composeRule.onNodeWithText(option).performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(minutes), starts)
            assertEquals(1, dismissals)
        }
        composeRule.onNodeWithTag("timer-surface").assertDoesNotExist()
    }

    private data class PlacedTimerGeometry(
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

    private fun placedTimerGeometry(): PlacedTimerGeometry {
        val node = composeRule.onNodeWithTag("timer-surface").fetchSemanticsNode()
        return composeRule.runOnIdle {
            // BasicAlertDialog applies this tag to its content Box, not the presentation window.
            val coordinates = node.layoutInfo.coordinates
            val density = node.layoutInfo.density
            PlacedTimerGeometry(coordinates.boundsInRoot(),
                coordinates.findRootCoordinates().boundsInRoot(), node.boundsInRoot,
                density.density, density.fontScale)
        }
    }

    @Test
    fun shortLargeTextDialogScrollsChoicesAndRetainsStateAcrossResize() {
        val width = mutableStateOf(360.dp)
        val height = mutableStateOf(360.dp)
        val visible = mutableStateOf(true)
        var cancellations = 0
        var dismissals = 0
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(0.5f, 1.5f)) {
                MaterialTheme {
                    if (visible.value) SleepTimerDialog(
                        isTimerActive = true,
                        sleepTimerDisplayText = "Active timer fixture",
                        onStartTimerClick = { error("Resizing must not start a timer") },
                        onCancelTimerClick = { cancellations++ },
                        onDismiss = { dismissals++; visible.value = false },
                        modifier = Modifier.widthIn(max = width.value).heightIn(max = height.value)
                            .testTag("timer-surface")
                    )
                }
            }
        }
        val cancel = composeRule.activity.getString(R.string.sleep_timer_cancel)
        val close = composeRule.activity.getString(R.string.common_close)
        composeRule.onNodeWithText(close).assertIsDisplayed()
        composeRule.onNodeWithText(cancel).performScrollTo().assertIsDisplayed()
        val range = composeRule.onNode(hasScrollAction()).fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange]
        composeRule.runOnIdle {
            assertTrue("The bounded choice list must actually scroll", range.value() > 0f)
            width.value = 480.dp
            height.value = 320.dp
        }
        composeRule.onNodeWithTag("timer-surface").assertExists()
        composeRule.onNodeWithText(close).assertIsDisplayed()
        val resizedRange = composeRule.onNode(hasScrollAction()).fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange]
        composeRule.runOnIdle {
            assertTrue("Resizing retains a scrolled choice list", resizedRange.value() > 0f)
            assertEquals(0, cancellations)
            assertEquals(0, dismissals)
        }
        composeRule.onNodeWithText(cancel).performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(1, cancellations)
            assertEquals(1, dismissals)
        }
    }

    @Test
    fun backStillDismissesTheModalBeforeTheUnderlyingPlayer() {
        val visible = mutableStateOf(true)
        var playerBacks = 0
        var dismissals = 0
        composeRule.setContent {
            MaterialTheme {
                BackHandler { playerBacks++ }
                if (visible.value) SleepTimerDialog(
                    isTimerActive = false,
                    sleepTimerDisplayText = "Timer fixture",
                    onStartTimerClick = { error("Back must not start a timer") },
                    onCancelTimerClick = { error("Back must not cancel a timer") },
                    onDismiss = { dismissals++; visible.value = false },
                    modifier = Modifier.testTag("timer-surface")
                )
            }
        }
        composeRule.onNodeWithTag("timer-surface").assertExists()
        Espresso.pressBack()
        composeRule.onNodeWithTag("timer-surface").assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(1, dismissals)
            assertEquals(0, playerBacks)
        }
    }
}
