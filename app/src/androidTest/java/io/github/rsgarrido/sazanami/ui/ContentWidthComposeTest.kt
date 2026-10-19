package io.github.rsgarrido.sazanami.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

class ContentWidthComposeTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun readableConstraintWorksUnderFillWidthAndKeepsChildOwnerAcrossResizing() {
        val width = mutableStateOf(412.dp)
        var pixelsPerDp = 0f
        var fontScale = 1f
        var parentOrigin = Offset.Zero
        var childOrigin = Offset.Zero
        var childWidth = 0
        lateinit var owner: Any
        composeRule.setContent {
            pixelsPerDp = LocalDensity.current.density
            fontScale = LocalDensity.current.fontScale
            Box(Modifier.requiredWidth(width.value).onGloballyPositioned { parentOrigin = it.positionInRoot() }) {
                Box(Modifier.readableContentWidth()) {
                    owner = remember { Any() }
                    Box(Modifier.fillMaxWidth().height(48.dp).onGloballyPositioned {
                        childOrigin = it.positionInRoot()
                        childWidth = it.size.width
                    })
                }
            }
        }
        val original = owner
        for (available in listOf(412.dp, 1100.dp, 2400.dp, 600.dp, 412.dp)) {
            composeRule.runOnIdle { width.value = available }
            composeRule.runOnIdle {
                val expected = minOf(available, ReadableContentMaximumWidth * fontScale.coerceAtLeast(1f)).value * pixelsPerDp
                assertEquals(expected, childWidth.toFloat(), 1f)
                assertEquals((available.value * pixelsPerDp - expected) / 2f,
                    childOrigin.x - parentOrigin.x, 1f)
                assertSame(original, owner)
            }
        }
    }

    @Test
    fun artworkFramesKeepTheirPhoneFractionsAndRealWideLayoutCaps() {
        val width = mutableStateOf(372.dp)
        val fraction = mutableStateOf(0.84f)
        val maximum = mutableStateOf(360.dp)
        var pixelsPerDp = 0f
        var artworkWidth = 0
        var artworkHeight = 0
        composeRule.setContent {
            pixelsPerDp = LocalDensity.current.density
            // Detail heroes are lazy-list items with unbounded vertical measurement as well.
            Column(Modifier.requiredWidth(width.value).verticalScroll(rememberScrollState())) {
                BoundedArtworkFrame(fraction.value, maximum.value) { artworkModifier ->
                    Box(artworkModifier.onGloballyPositioned {
                        artworkWidth = it.size.width
                        artworkHeight = it.size.height
                    })
                }
            }
        }
        for ((ratio, cap) in listOf(0.84f to 360.dp, 0.72f to 320.dp)) {
            for (available in listOf(372.dp, 1100.dp, 320.dp)) {
                composeRule.runOnIdle {
                    fraction.value = ratio
                    maximum.value = cap
                    width.value = available
                }
                composeRule.runOnIdle {
                    assertEquals(minOf(available * ratio, cap).value * pixelsPerDp, artworkWidth.toFloat(), 1f)
                    assertEquals(artworkWidth, artworkHeight)
                }
            }
        }
    }
}
