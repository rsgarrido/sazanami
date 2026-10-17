package io.github.rsgarrido.sazanami.ui.player

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.ui.player.modern.DefaultPlayerMorphBounds
import io.github.rsgarrido.sazanami.ui.player.modern.DefaultPlayerMorphGeometry
import io.github.rsgarrido.sazanami.ui.player.modern.interpolateMorphRect
import io.github.rsgarrido.sazanami.ui.player.modern.resolveDefaultPlayerMorphGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlayerMorphHostMeasurementTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun sameViewportBoundaryRefreshesUnmovedDestinationsAndPromotesACompleteLocalSnapshot() {
        val rail = mutableStateOf(false)
        val measured = mutableMapOf<String, Rect>()
        lateinit var endpoints: PlayerEndpointBounds
        lateinit var morph: PlayerMorphState
        lateinit var elements: DefaultPlayerMorphBounds
        var geometry: DefaultPlayerMorphGeometry? = null
        fun Modifier.measure(name: String, update: (Rect) -> Unit): Modifier = onGloballyPositioned {
            val rect = it.boundsInRoot()
            measured[name] = rect
            update(rect)
        }
        composeRule.setContent {
            morph = rememberPlayerMorphState()
            // Nonzero origin exercises the Session 1 conversion as well as the new boundary.
            Box(Modifier.fillMaxSize().padding(start = 24.dp, top = 16.dp)) {
                PlayerMorphHost(morph, Modifier.fillMaxSize(), layoutBoundary = rail.value) { bounds ->
                    endpoints = bounds
                    elements = remember(bounds) { DefaultPlayerMorphBounds { bounds.layoutFrame } }
                    geometry = resolveDefaultPlayerMorphGeometry(0.5f, bounds, elements)
                    val sourceX = if (rail.value) 110.dp else 12.dp
                    Box(Modifier.offset(sourceX, 220.dp).size(180.dp, 68.dp)
                        .measure("miniSurface") { bounds.updateMini(it); elements.updateMiniSurface(it) })
                    Box(Modifier.offset(sourceX + 8.dp, 228.dp).size(52.dp)
                        .measure("miniArtwork", elements::updateMiniArtwork))
                    Box(Modifier.offset(sourceX + 64.dp, 232.dp).size(50.dp, 40.dp)
                        .measure("miniText", elements::updateMiniText))
                    Box(Modifier.offset(sourceX + 124.dp, 230.dp).size(48.dp)
                        .measure("miniPlayPause", elements::updateMiniPlayPause))
                    // These three positions/constraints are identical on both sides of the switch.
                    Box(Modifier.offset(30.dp, 8.dp).size(100.dp)
                        .measure("expandedArtwork", elements::updateExpandedArtwork))
                    Box(Modifier.offset(16.dp, 112.dp).size(180.dp, 48.dp)
                        .measure("expandedText", elements::updateExpandedText))
                    Box(Modifier.offset(76.dp, 164.dp).size(56.dp)
                        .measure("expandedPlayPause", elements::updateExpandedPlayPause))
                }
            }
        }
        val endpointOwner = endpoints
        val elementOwner = elements
        val morphOwner = morph
        fun assertCurrentSnapshot() = composeRule.runOnIdle {
            val frame = endpoints.layoutFrame!!
            fun local(name: String) = measured.getValue(name).translate(-frame.originInRoot.x, -frame.originInRoot.y)
            assertEquals(DefaultPlayerMorphGeometry(
                interpolateMorphRect(local("miniSurface"), frame.boundsInRoot.translate(-frame.originInRoot.x, -frame.originInRoot.y), 0.5f),
                interpolateMorphRect(local("miniArtwork"), local("expandedArtwork"), 0.5f),
                interpolateMorphRect(local("miniText"), local("expandedText"), 0.5f),
                interpolateMorphRect(local("miniPlayPause"), local("expandedPlayPause"), 0.5f)
            ), geometry)
            assertSame(endpointOwner, endpoints)
            assertSame(elementOwner, elements)
            assertSame(morphOwner, morph)
        }
        assertCurrentSnapshot()
        val firstFrame = composeRule.runOnIdle { endpoints.layoutFrame!! }
        val destination = measured.getValue("expandedArtwork")
        composeRule.runOnIdle { rail.value = true }
        assertCurrentSnapshot()
        composeRule.runOnIdle {
            assertEquals(firstFrame.boundsInRoot, endpoints.layoutFrame!!.boundsInRoot)
            assertTrue(endpoints.layoutFrame!!.generation > firstFrame.generation)
            assertEquals(destination, measured.getValue("expandedArtwork"))
        }
        composeRule.runOnIdle { rail.value = false }
        assertCurrentSnapshot()
    }
}
