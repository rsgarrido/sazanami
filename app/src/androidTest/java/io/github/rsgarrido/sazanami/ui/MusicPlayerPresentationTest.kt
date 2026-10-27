package io.github.rsgarrido.sazanami.ui

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.tooling.CompositionData
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.controller.PlaybackQueueHubUiState
import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.lyrics.LyricsPlaybackUiState
import io.github.rsgarrido.sazanami.player.RepeatMode
import io.github.rsgarrido.sazanami.ui.player.ArtworkViewerEntryTag
import io.github.rsgarrido.sazanami.ui.player.PlayerEndpointBounds
import io.github.rsgarrido.sazanami.ui.player.PlayerBoundsMeasurement
import io.github.rsgarrido.sazanami.ui.player.PlayerLyricsTransitionState
import io.github.rsgarrido.sazanami.ui.player.PlayerMorphHost
import io.github.rsgarrido.sazanami.ui.player.PlayerMorphState
import io.github.rsgarrido.sazanami.ui.player.PlayerMorphLayoutFrame
import io.github.rsgarrido.sazanami.ui.player.PlayerPresentation
import io.github.rsgarrido.sazanami.ui.player.PlayerDeviceKind
import io.github.rsgarrido.sazanami.ui.player.PlayerPresentationIdentity
import io.github.rsgarrido.sazanami.ui.player.ResolvedPlayerPresentation
import io.github.rsgarrido.sazanami.ui.player.TabletLandscapePlayerCandidate
import io.github.rsgarrido.sazanami.ui.player.TabletLandscapePlayerContext
import io.github.rsgarrido.sazanami.ui.player.resolvePlayerPresentation
import io.github.rsgarrido.sazanami.ui.player.PlayerPresentationViewport
import io.github.rsgarrido.sazanami.ui.player.rememberPlayerPresentationBoundary
import io.github.rsgarrido.sazanami.ui.player.pocketadvance.PocketAdvancePlayerMorphBounds
import io.github.rsgarrido.sazanami.ui.player.pocketadvance.PocketAdvanceMorphGeometry
import io.github.rsgarrido.sazanami.ui.player.pocketadvance.minimumPocketAdvanceSize
import io.github.rsgarrido.sazanami.ui.player.classicwheel.ClassicWheelMenuScreen
import io.github.rsgarrido.sazanami.ui.player.classicwheel.ClassicWheelMenuState
import io.github.rsgarrido.sazanami.ui.player.modern.DefaultPlayerMorphBounds
import io.github.rsgarrido.sazanami.ui.player.modern.DefaultPlayerMorphGeometry
import io.github.rsgarrido.sazanami.ui.player.modern.DefaultPlayerArrangement
import io.github.rsgarrido.sazanami.ui.player.modern.DefaultPlayerLayout
import io.github.rsgarrido.sazanami.ui.player.modern.DefaultPlayerInsets
import io.github.rsgarrido.sazanami.ui.player.modern.ModernArtworkTransitionStyle
import io.github.rsgarrido.sazanami.ui.player.modern.ModernPlayerAppearance
import io.github.rsgarrido.sazanami.ui.player.modern.ModernControlSize
import io.github.rsgarrido.sazanami.ui.player.modern.ModernLayoutDensity
import io.github.rsgarrido.sazanami.ui.player.modern.ModernLandscapeLayout
import io.github.rsgarrido.sazanami.ui.player.modern.ModernSeekbarStyle
import io.github.rsgarrido.sazanami.ui.player.modern.ModernWaveformDensity
import io.github.rsgarrido.sazanami.ui.player.modern.ModernArtworkCarouselState
import io.github.rsgarrido.sazanami.ui.player.modern.ModernWaveformSize
import io.github.rsgarrido.sazanami.ui.player.modern.resolveDefaultPlayerLayout
import io.github.rsgarrido.sazanami.ui.player.rememberPlayerLyricsTransitionState
import io.github.rsgarrido.sazanami.ui.player.rememberPlayerMorphState
import io.github.rsgarrido.sazanami.ui.player.theme.defaultTokens
import io.github.rsgarrido.sazanami.ui.state.PlaybackProgressUiState
import io.github.rsgarrido.sazanami.ui.state.DirectSeekGestureEnvironment
import io.github.rsgarrido.sazanami.ui.state.DirectScrubAuthority
import io.github.rsgarrido.sazanami.ui.state.LocalDirectSeekGestureEnvironment
import io.github.rsgarrido.sazanami.ui.theme.SazanamiTheme
import io.github.rsgarrido.sazanami.ui.navigation.MainDestination
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MusicPlayerPresentationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun pocketAdvanceRealMiniTapMorphReversalAndControlsKeepPrimaryAccents() {
        val fixture = advanceFixture()
        composeRule.setContent {
            SazanamiTheme {
                val accent = rememberAppShellAccent(fixture.theme.value, fixture.theme.value.defaultTokens())
                CompositionLocalProvider(LocalAppShellAccent provides accent) { fixture.Content() }
            }
        }
        val initial = awaitAdvanceSnapshot(fixture)
        val controller = fixture.morph
        val endpoints = fixture.endpoints
        composeRule.onNodeWithTag("pocketAdvanceMiniPlay").performTouchInput { click() }
        composeRule.runOnIdle { assertEquals(listOf("play-pause"), fixture.calls) }
        composeRule.mainClock.autoAdvance = false
        try {
            composeRule.onNodeWithTag("pocketAdvanceMini").performTouchInput { click() }
            composeRule.mainClock.advanceTimeBy(120)
            val opening = composeRule.runOnIdle {
                assertTrue(controller.progress > 0f && controller.progress < 1f)
                assertFalse(controller.isDragging)
                assertEquals(PlayerPresentationIdentity.PocketAdvance, fixture.resolvedIdentity)
                controller.progress
            }
            val shell = composeRule.onNodeWithTag("pocketAdvanceMorphShell", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            assertTrue(shell.width > initial.mini.shell.width)
            assertTrue(shell.width < initial.expanded.shell.width)
            val movingTrack = composeRule.onNodeWithTag("pocketAdvanceMorphProgress", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            assertRectNear(io.github.rsgarrido.sazanami.ui.player.pocketadvance.interpolatePocketAdvanceRect(
                initial.mini.progressTrack, initial.expanded.progressTrack, opening)
                .translate(initial.frame.originInRoot), movingTrack)
            composeRule.runOnIdle { controller.collapse() }
            composeRule.mainClock.advanceTimeBy(64)
            composeRule.runOnIdle {
                assertTrue(controller.progress < opening)
                controller.expand()
            }
            composeRule.mainClock.advanceTimeBy(400)
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
        val expanded = awaitAdvanceSnapshot(fixture)
        val art = composeRule.onNodeWithTag("pocketAdvanceMorphArtwork", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertRectNear(expanded.expanded.artwork.translate(expanded.frame.originInRoot), art)
        val track = composeRule.onNodeWithTag("pocketAdvanceMorphProgress", useUnmergedTree = true).fetchSemanticsNode()
        assertRectNear(expanded.expanded.progressTrack.translate(expanded.frame.originInRoot), track.boundsInRoot)
        assertFalse(track.config.contains(SemanticsActions.SetProgress))
        assertTrue(composeRule.onNodeWithTag("pocketAdvanceSeek", useUnmergedTree = true).fetchSemanticsNode().config.contains(SemanticsActions.SetProgress))
        composeRule.onNodeWithTag("pocketAdvanceMorphPlay").performTouchInput { click() }
        composeRule.onNodeWithContentDescription(text(R.string.player_more_actions)).performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("play-pause", "play-pause", "more"), fixture.calls)
            assertSame(controller, fixture.morph)
            assertSame(endpoints, fixture.endpoints)
            assertEquals(PlayerTheme.CLASSIC_WHEEL, fixture.theme.value)
            assertEquals(resolveAppShellAccent(PlayerTheme.CLASSIC_WHEEL, PlayerTheme.CLASSIC_WHEEL.defaultTokens()),
                fixture.observedShellAccent)
            assertEquals(1f, controller.progress, 0f)
        }
        composeRule.onNodeWithTag("pocketAdvanceCollapse").performTouchInput { click() }
        composeRule.onNodeWithTag("pocketAdvanceMini").assertIsDisplayed()
        composeRule.runOnIdle { assertTrue(controller.isCollapsedAndIdle) }
    }

    @Test
    fun pocketAdvanceCapturedMiniDragSurvivesHiddenSourceAndCancelRestoresCollapsed() {
        val fixture = advanceFixture()
        composeRule.setContent { fixture.Content() }
        awaitAdvanceSnapshot(fixture)
        val source = composeRule.onNodeWithTag("pocketAdvanceMini").fetchSemanticsNode().boundsInRoot
        composeRule.onRoot().performTouchInput {
            down(source.center)
            moveTo(source.center - Offset(0f, 48f))
        }
        val before = composeRule.runOnIdle {
            assertTrue(fixture.morph.isDragging)
            assertTrue(fixture.morph.progress > 0f)
            fixture.morph.progress
        }
        // Separate event batches force recomposition/input ownership to change during capture.
        composeRule.onRoot().performTouchInput { moveBy(Offset(0f, -24f)) }
        composeRule.runOnIdle {
            assertTrue(fixture.morph.isDragging)
            assertTrue(fixture.morph.progress > before)
        }
        composeRule.onRoot().performTouchInput { cancel() }
        composeRule.runOnIdle {
            assertTrue(fixture.morph.isCollapsedAndIdle)
            assertTrue(fixture.calls.isEmpty())
            assertTrue(fixture.seeks.isEmpty())
        }
    }

    @Test
    fun pocketAdvanceRealDragExpandsAndCollapseCaptureSurvivesItsRevealWindow() {
        val fixture = advanceFixture()
        composeRule.setContent { fixture.Content() }
        awaitAdvanceSnapshot(fixture)
        val source = composeRule.onNodeWithTag("pocketAdvanceMini").fetchSemanticsNode().boundsInRoot
        composeRule.onRoot().performTouchInput {
            down(source.center)
            moveTo(source.center - Offset(0f, 48f))
        }
        composeRule.runOnIdle { assertTrue(fixture.morph.isDragging) }
        composeRule.onRoot().performTouchInput { moveBy(Offset(0f, -48f)); up() }
        composeRule.runOnIdle { assertEquals(1f, fixture.morph.progress, 0f) }

        val artwork = composeRule.onNodeWithTag("pocketAdvanceMorphArtwork", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        composeRule.onRoot().performTouchInput {
            down(artwork.center)
            moveTo(artwork.center + Offset(0f, 48f))
        }
        composeRule.runOnIdle { assertTrue(fixture.morph.isDragging) }
        composeRule.onRoot().performTouchInput { moveBy(Offset(0f, 72f)) }
        composeRule.runOnIdle {
            assertTrue(fixture.morph.progress < 0.68f)
            assertTrue("The captured collapse must survive hiding its gesture reveal", fixture.morph.isDragging)
        }
        composeRule.onRoot().performTouchInput { up() }
        composeRule.runOnIdle {
            assertTrue(fixture.morph.isCollapsedAndIdle)
            assertTrue(fixture.calls.isEmpty())
            assertTrue(fixture.seeks.isEmpty())
        }
    }

    @Test
    fun pocketAdvanceResizeAndPortraitFallbackSettleOnePairAndResetLyrics() {
        val fixture = advanceFixture()
        composeRule.setContent { fixture.Content() }
        val source = awaitAdvanceSnapshot(fixture)
        val controller = fixture.morph
        val outgoingEndpoints = fixture.endpoints
        composeRule.runOnIdle {
            controller.beginDragWithRange(500f)
            controller.dragBy(-200f)
        }
        composeRule.runOnIdle {
            assertTrue(controller.isDragging)
            fixture.viewport.value = DpSize(1180.dp, 740.dp)
        }
        val resized = awaitAdvanceSnapshot(fixture)
        composeRule.runOnIdle {
            assertTrue(controller.isCollapsedAndIdle)
            assertSame(controller, fixture.morph)
            assertNotSame(outgoingEndpoints, fixture.endpoints)
            assertNotSame(source.owner, resized.owner)
            assertEquals(1L, fixture.presentationEpoch)
            controller.expand()
        }
        composeRule.runOnIdle { fixture.lyrics.openLyrics() }
        composeRule.runOnIdle {
            assertTrue(fixture.lyrics.lyricsComposed)
            fixture.viewport.value = DpSize(720.dp, 1180.dp)
        }
        composeRule.runOnIdle {
            assertEquals(PlayerPresentationIdentity.Existing(PlayerTheme.CLASSIC_WHEEL), fixture.resolvedIdentity)
            assertEquals(PlayerPresentation.Expanded, controller.settledPresentation)
            assertEquals(1f, controller.progress, 0f)
            assertFalse(controller.isDragging)
            assertEquals(0f, fixture.lyrics.progress, 0f)
            assertFalse(fixture.lyricsVisible.value)
            assertEquals(PlayerTheme.CLASSIC_WHEEL, fixture.theme.value)
            fixture.viewport.value = DpSize(1200.dp, 720.dp)
        }
        awaitAdvanceSnapshot(fixture)
        composeRule.runOnIdle {
            assertEquals(PlayerPresentationIdentity.PocketAdvance, fixture.resolvedIdentity)
            assertEquals(1f, controller.progress, 0f)
            fixture.nominateAdvance.value = false
        }
        composeRule.runOnIdle {
            assertEquals(PlayerPresentationIdentity.Existing(PlayerTheme.CLASSIC_WHEEL), fixture.resolvedIdentity)
            assertEquals(1f, controller.progress, 0f)
            assertEquals(PlayerTheme.CLASSIC_WHEEL, fixture.theme.value)
            assertTrue(fixture.calls.isEmpty())
            assertTrue(fixture.seeks.isEmpty())
        }
    }

    @Test
    fun pocketAdvanceRecreationRestoresSettledExpandedWithFreshGeometry() {
        val fixture = advanceFixture()
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { fixture.Content() }
        val first = awaitAdvanceSnapshot(fixture)
        composeRule.onNodeWithTag("pocketAdvanceMini").performTouchInput { click() }
        composeRule.runOnIdle { assertEquals(1f, fixture.morph.progress, 0f) }
        val oldController = fixture.morph
        restoration.emulateSavedInstanceStateRestore()
        val restored = awaitAdvanceSnapshot(fixture)
        composeRule.runOnIdle {
            assertNotSame(oldController, fixture.morph)
            assertNotSame(first.owner, restored.owner)
            assertEquals(PlayerPresentationIdentity.PocketAdvance, fixture.resolvedIdentity)
            assertEquals(PlayerPresentation.Expanded, fixture.morph.settledPresentation)
            assertEquals(1f, fixture.morph.progress, 0f)
            assertTrue(fixture.calls.isEmpty())
        }
        composeRule.onNodeWithTag("pocketAdvanceMorphPlay").performTouchInput { click() }
        composeRule.runOnIdle { assertEquals(listOf("play-pause"), fixture.calls) }
    }

    @Test
    fun pocketAdvanceSharedVisualsRouteArtworkArtistAlbumAndLyricsWithoutStealingCollapse() {
        val fixture = advanceFixture()
        composeRule.setContent { fixture.Content() }
        awaitAdvanceSnapshot(fixture)
        composeRule.onNodeWithTag("pocketAdvanceMini").performTouchInput { click() }
        // The interactive viewer entry remains accessible in the merged tree; the passive
        // morph image below is queried unmerged only to inject touch at its exact bounds.
        assertTrue(composeRule.onNodeWithTag(ArtworkViewerEntryTag).fetchSemanticsNode()
            .config.contains(SemanticsActions.OnClick))
        composeRule.onNodeWithTag("pocketAdvanceMorphArtwork", useUnmergedTree = true).performTouchInput { click() }
        composeRule.onNodeWithTag("pocketAdvanceArtist").performTouchInput { click() }
        composeRule.onNodeWithTag("pocketAdvanceAlbum").performTouchInput { click() }
        composeRule.runOnIdle {
            assertEquals(listOf(fixture.current.value), fixture.artworks)
            assertEquals(listOf(fixture.current.value), fixture.openedArtists)
            assertEquals(listOf(fixture.current.value), fixture.openedAlbums)
            assertFalse(fixture.lyrics.lyricsComposed)
        }
        // Swiping on the same clickable artwork must cancel its click and open shared lyrics.
        composeRule.onNodeWithTag("pocketAdvanceMorphArtwork", useUnmergedTree = true).performTouchInput {
            down(center); moveBy(Offset(0f, -48f)); up()
        }
        composeRule.runOnIdle {
            assertTrue(fixture.lyrics.lyricsComposed)
            assertEquals(1f, fixture.lyrics.progress, 0f)
            assertEquals(1, fixture.artworks.size)
            assertEquals(1f, fixture.morph.progress, 0f)
            fixture.lyrics.snapToExpanded()
        }
        composeRule.onNodeWithTag("pocketAdvanceMorphArtwork", useUnmergedTree = true).performTouchInput {
            down(center); moveBy(Offset(0f, 48f))
        }
        composeRule.runOnIdle { assertTrue(fixture.morph.isDragging) }
        composeRule.onRoot().performTouchInput { cancel() }
        composeRule.runOnIdle {
            assertFalse(fixture.lyrics.lyricsComposed)
            assertEquals(1, fixture.artworks.size)
            assertTrue(fixture.calls.isEmpty())
        }
    }

    @Test
    fun pocketAdvanceBelowRailBreakpointKeepsTheMiniAnchorThroughImmersion() {
        val belowRailViewport = DpSize(800.dp, 640.dp)
        val fixture = advanceFixture().apply {
            viewport.value = belowRailViewport
            // tabletFixture fixes Rail independently of viewport. This synthetic host consumes
            // system insets, so resolve its actual 800dp usable width with zero local insets.
            shellLayout.value = resolveMusicShellLayout(
                belowRailViewport.width, belowRailViewport.height, fontScale = fontScale.value)
        }
        composeRule.setContent { fixture.Content() }
        val collapsed = awaitAdvanceSnapshot(fixture)
        composeRule.runOnIdle {
            assertFalse(
                "Expected bottom navigation for viewport=${fixture.viewport.value}, shell=${fixture.shellLayout.value}",
                fixture.shellLayout.value.usesRail)
            assertEquals(PlayerPresentationIdentity.PocketAdvance, fixture.resolvedIdentity)
        }
        composeRule.onNodeWithTag("pocketAdvanceMini").performTouchInput { click() }
        val expanded = awaitAdvanceSnapshot(fixture)
        assertEquals("Expansion must retain every compact mini anchor", collapsed.mini, expanded.mini)
        composeRule.onNodeWithTag("pocketAdvanceCollapse").performTouchInput { click() }
        val returned = awaitAdvanceSnapshot(fixture)
        assertEquals("Collapse must return to the same compact mini anchors", collapsed.mini, returned.mini)
        composeRule.runOnIdle {
            assertTrue(fixture.morph.isCollapsedAndIdle)
            assertEquals(PlayerTheme.CLASSIC_WHEEL, fixture.theme.value)
        }
    }

    @Test
    fun pocketAdvanceLyricsRejectsCancelledHorizontalAndSeekGestures() {
        val fixture = advanceFixture()
        // No commit on cancellation is the Direct seek contract; normal seeking publishes on down/move.
        val environment = DirectSeekGestureEnvironment({ DirectScrubAuthority(true, 1, 0, true) }, {})
        composeRule.setContent {
            CompositionLocalProvider(LocalDirectSeekGestureEnvironment provides environment) { fixture.Content() }
        }
        awaitAdvanceSnapshot(fixture)
        composeRule.onNodeWithTag("pocketAdvanceMini").performTouchInput { click() }
        composeRule.onNodeWithTag("pocketAdvanceDisplayDrag", useUnmergedTree = true).performTouchInput {
            down(center); moveBy(Offset(48f, -8f)); up()
        }
        composeRule.onNodeWithTag("pocketAdvanceDisplayDrag", useUnmergedTree = true).performTouchInput {
            down(center); moveBy(Offset(0f, -48f)); cancel()
        }
        composeRule.onNodeWithTag("pocketAdvanceSeek", useUnmergedTree = true).performTouchInput {
            down(center); moveBy(Offset(0f, -48f)); cancel()
        }
        composeRule.runOnIdle {
            assertFalse(fixture.lyrics.lyricsComposed)
            assertEquals(1f, fixture.morph.progress, 0f)
            assertFalse(fixture.morph.isDragging)
            assertTrue("Cancelled Direct seek must not commit; actual seeks=${fixture.seeks}", fixture.seeks.isEmpty())
            assertTrue("Rejected gestures must not invoke player actions; actual calls=${fixture.calls}", fixture.calls.isEmpty())
        }
        composeRule.onNodeWithTag("pocketAdvanceSeek", useUnmergedTree = true).performTouchInput { click(center) }
        composeRule.runOnIdle {
            assertEquals("The next seek tap must commit exactly once; actual seeks=${fixture.seeks}", 1, fixture.seeks.size)
            assertTrue("A midpoint tap must seek near 90 seconds; actual seeks=${fixture.seeks}", fixture.seeks.single() in 85_000..95_000)
            assertFalse(fixture.lyrics.lyricsComposed)
            assertEquals(1f, fixture.morph.progress, 0f)
            assertFalse(fixture.morph.isDragging)
            assertTrue("Seeking must not invoke other player actions; actual calls=${fixture.calls}", fixture.calls.isEmpty())
        }
    }

    @Test
    fun eachBoundsRememberSurvivesRecompositionAndEveryThemeChange() {
        val probe = LifetimeProbe()
        composeRule.setContent { probe.Content() }
        val initial = composeRule.runOnIdle { probe.bounds.toList() }
        val measured = Rect(0f, 400f, 300f, 480f)
        composeRule.runOnIdle {
            probe.default.updateMiniSurface(measured)
            probe.tick.value++
        }
        for (theme in PlayerTheme.entries) {
            composeRule.runOnIdle { probe.theme.value = theme }
            composeRule.runOnIdle {
                initial.zip(probe.bounds).forEach { (before, after) -> assertSame(before, after) }
                assertEquals(measured, probe.default.miniSurface)
            }
        }
    }

    @Test
    fun classicMenuResetsOnThemeAndExpandedCompositionWhileBoundsStayIndependent() {
        val probe = LifetimeProbe()
        composeRule.setContent { probe.Content() }
        val initialBounds = composeRule.runOnIdle { probe.bounds.toList() }
        val firstMenu = composeRule.runOnIdle { probe.menu }
        composeRule.runOnIdle {
            probe.menu.openMainMenu()
            probe.tick.value++
        }
        composeRule.runOnIdle {
            assertSame(firstMenu, probe.menu)
            assertEquals(ClassicWheelMenuScreen.MainMenu, probe.menu.currentScreen)
            probe.theme.value = PlayerTheme.DEFAULT
        }
        val themedMenu = composeRule.runOnIdle {
            assertNotSame(firstMenu, probe.menu)
            assertEquals(ClassicWheelMenuScreen.NowPlaying, probe.menu.currentScreen)
            probe.menu
        }
        composeRule.runOnIdle { probe.morph.snapTo(PlayerPresentation.Expanded) }
        val expandedMenu = composeRule.runOnIdle {
            assertNotSame(themedMenu, probe.menu)
            probe.menu.openMainMenu()
            probe.menu
        }
        composeRule.runOnIdle { probe.tick.value++ }
        composeRule.runOnIdle {
            assertSame(expandedMenu, probe.menu)
            probe.morph.snapTo(PlayerPresentation.Collapsed)
        }
        composeRule.runOnIdle {
            assertNotSame(expandedMenu, probe.menu)
            initialBounds.zip(probe.bounds).forEach { (before, after) -> assertSame(before, after) }
        }
    }

    @Test
    fun suppliedMorphLyricsAndEndpointOwnersSurviveTrackThemeAndEditorChanges() {
        val fixture = mount()
        val morph = fixture.morph
        val lyrics = fixture.lyrics
        val endpoints = fixture.endpoints
        composeRule.runOnIdle {
            fixture.current.value = fixture.songs.last()
            fixture.theme.value = PlayerTheme.POCKET_DISC
            fixture.metadataVisible.value = true
            fixture.lyrics.beginOpeningDrag()
            fixture.lyrics.dragOpeningBy(-100f, 400f)
        }
        composeRule.runOnIdle {
            assertSame(morph, fixture.morph)
            assertSame(lyrics, fixture.lyrics)
            assertSame(endpoints, fixture.endpoints)
            assertEquals(0.25f, fixture.lyrics.progress, 0f)
            fixture.metadataVisible.value = false
            fixture.theme.value = PlayerTheme.CLASSIC_WHEEL
        }
        composeRule.runOnIdle {
            assertSame(morph, fixture.morph)
            assertSame(lyrics, fixture.lyrics)
            assertSame(endpoints, fixture.endpoints)
            assertEquals(0.25f, fixture.lyrics.progress, 0f)
        }
    }

    @Test
    fun preparedSlotsStaySeparateInTheirExistingCompositionOrderAndUseFinalPadding() {
        val fixture = mount()
        composeRule.runOnIdle {
            assertEquals(37.dp, fixture.receivedPadding)
            fixture.padding.value = 91.dp
        }
        composeRule.runOnIdle { assertEquals(91.dp, fixture.receivedPadding) }
        val tags = tags(composeRule.onRoot(useUnmergedTree = true).fetchSemanticsNode())
        assertTrue(tags.indexOf("body") < tags.indexOf("navigation"))
        assertTrue(tags.indexOf("navigation") < tags.indexOf("metadata-dialog"))
        assertTrue(tags.indexOf("metadata-dialog") < tags.indexOf("transient"))
        composeRule.runOnIdle {
            fixture.metadataVisible.value = true
            fixture.overlaysVisible.value = false
        }
        composeRule.onNodeWithTag("body").assertDoesNotExist()
        composeRule.onNodeWithTag("editor").assertExists()
        composeRule.onNodeWithTag("metadata-dialog").assertExists()
        composeRule.onNodeWithTag("transient").assertDoesNotExist()
    }

    @Test
    fun miniVisibilityAndEveryThemeUseTheSuppliedCurrentSongWithoutResettingTheOwner() {
        val fixture = mount()
        val owner = fixture.morph
        composeRule.runOnIdle { fixture.miniVisible.value = true }
        for (theme in PlayerTheme.entries) {
            composeRule.runOnIdle { fixture.theme.value = theme }
            mini(fixture).assertExists()
            composeRule.runOnIdle { assertSame(owner, fixture.morph) }
        }
        composeRule.runOnIdle { fixture.miniVisible.value = false }
        mini(fixture).assertDoesNotExist()
        composeRule.onNodeWithTag("body").assertExists()
        composeRule.runOnIdle { fixture.navigationVisible.value = false }
        composeRule.onNodeWithTag("navigation").assertDoesNotExist()
    }

    @Test
    fun miniControlsQueueAndTimerReachTheSuppliedEntryCallbacks() {
        val fixture = mount()
        composeRule.runOnIdle {
            fixture.miniVisible.value = true
            fixture.timerActive.value = true
        }
        composeRule.onNodeWithContentDescription(text(R.string.player_play)).performClick()
        composeRule.onNodeWithContentDescription(text(R.string.player_open_queues)).performClick()
        composeRule.onNode(hasText("Timer fixture") and hasClickAction()).performClick()
        composeRule.runOnIdle { assertEquals(listOf("play-pause", "queue", "sleep"), fixture.calls) }
        mini(fixture).performClick()
        composeRule.runOnIdle { assertEquals(PlayerPresentation.Expanded, fixture.morph.targetPresentation) }
    }

    @Test
    fun oneResolvedPairDrivesMiniMorphAndExpandedWhilePrimarySelectionStaysSeparate() {
        // An existing complete pair exercises nomination without adding unfinished landscape UI.
        val fixture = Fixture(PlayerPresentation.Collapsed).apply {
            viewport.value = DpSize(1200.dp, 800.dp)
            deviceKind.value = PlayerDeviceKind.TABLET
            nominatedTheme.value = PlayerTheme.RETRO_RACK
            miniVisible.value = true
        }
        composeRule.setContent {
            SazanamiTheme {
                // Same appearance boundary as MusicScreen; the nominated player receives its own tokens.
                val accent = rememberAppShellAccent(fixture.theme.value, fixture.theme.value.defaultTokens())
                CompositionLocalProvider(LocalAppShellAccent provides accent) { fixture.Content() }
            }
        }
        val owner = fixture.morph
        val endpoints = fixture.endpoints
        composeRule.runOnIdle {
            assertEquals(resolveAppShellAccent(PlayerTheme.DEFAULT, PlayerTheme.DEFAULT.defaultTokens()),
                fixture.observedShellAccent)
        }
        composeRule.onNodeWithText(fixture.current.value!!.title.uppercase()).assertIsDisplayed()
        composeRule.mainClock.autoAdvance = false
        try {
            mini(fixture).performClick()
            composeRule.mainClock.advanceTimeBy(120)
            val beforePrimaryChange = composeRule.runOnIdle {
                assertTrue(owner.progress > 0f && owner.progress < 1f)
                assertEquals(PlayerTheme.DEFAULT, fixture.theme.value)
                owner.progress
            }
            composeRule.runOnIdle {
                // Changing global appearance during a morph must not reselect the nominated pair.
                fixture.theme.value = PlayerTheme.CLASSIC_WHEEL
            }
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.runOnIdle {
                assertSame(owner, fixture.morph)
                assertSame(endpoints, fixture.endpoints)
                assertTrue(owner.progress >= beforePrimaryChange && owner.progress < 1f)
                assertEquals(PlayerPresentation.Expanded, owner.targetPresentation)
            }
            mini(fixture).assertDoesNotExist() // Its composed source has yielded input to the morph.
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
        composeRule.onNodeWithText(text(R.string.player_rack_main_deck)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(text(R.string.player_more_actions)).performClick()
        composeRule.runOnIdle {
            assertEquals(PlayerTheme.CLASSIC_WHEEL, fixture.theme.value)
            assertEquals(PlayerPresentation.Expanded, owner.settledPresentation)
            assertSame(owner, fixture.morph)
            assertSame(endpoints, fixture.endpoints)
            assertEquals(listOf("more"), fixture.calls)
        }
        composeRule.onNodeWithText(text(R.string.player_rack_main_deck)).assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(resolveAppShellAccent(PlayerTheme.CLASSIC_WHEEL, PlayerTheme.CLASSIC_WHEEL.defaultTokens()),
                fixture.observedShellAccent)
        }
        composeRule.onNodeWithContentDescription(text(R.string.player_collapse)).performClick()
        composeRule.onNodeWithText(fixture.current.value!!.title.uppercase()).assertIsDisplayed()
        composeRule.runOnIdle {
            assertSame(owner, fixture.morph)
            assertSame(endpoints, fixture.endpoints)
            assertTrue(owner.isCollapsedAndIdle)
            assertEquals(listOf("more"), fixture.calls)
        }
    }

    @Test
    fun miniVerticalDragExpandsTheSuppliedMorphStateThroughTheExistingProtocol() {
        val fixture = mount()
        composeRule.runOnIdle { fixture.miniVisible.value = true }
        mini(fixture).performTouchInput {
            swipe(center, center - Offset(0f, 300f), durationMillis = 200)
        }
        composeRule.runOnIdle {
            assertEquals(PlayerPresentation.Expanded, fixture.morph.targetPresentation)
            assertFalse(fixture.morph.isDragging)
        }
    }

    @Test
    fun expandedDefaultEntriesAndTrackReplacementKeepLiveSongTargets() {
        val fixture = mountExpandedDefault()
        val artistLabel = text(R.string.library_search_go_artist)
        val albumLabel = text(R.string.player_open_current_album)
        val artistAction = SemanticsMatcher("Current artist navigation action") {
            it.config.getOrNull(SemanticsActions.OnClick)?.label == artistLabel
        }
        val albumAction = SemanticsMatcher("Current album navigation action") {
            it.config.getOrNull(SemanticsActions.OnClick)?.label == albumLabel
        }
        composeRule.onNodeWithContentDescription(text(R.string.player_open_queues)).assertIsDisplayed().performClick()
        composeRule.onNodeWithContentDescription(text(R.string.player_more_actions)).assertIsDisplayed().performClick()
        composeRule.onNode(hasText(fixture.current.value!!.artist) and artistAction).assertIsDisplayed().performClick()
        composeRule.onNode(hasText(fixture.current.value!!.album) and albumAction).assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(ArtworkViewerEntryTag).assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("queue", "more"), fixture.calls)
            assertSame(fixture.current.value, fixture.openedArtists.single())
            assertSame(fixture.current.value, fixture.openedAlbums.single())
            assertSame(fixture.current.value, fixture.artworks.single())
            fixture.current.value = fixture.songs.last()
            fixture.previous.value = fixture.songs[1]
            fixture.next.value = null
        }
        awaitExpandedSnapshot(fixture, requireLiveArtwork = true)
        composeRule.onNodeWithContentDescription(text(R.string.player_open_queues)).assertIsDisplayed().performClick()
        composeRule.onNodeWithContentDescription(text(R.string.player_more_actions)).assertIsDisplayed().performClick()
        composeRule.onNode(hasText(fixture.current.value!!.artist) and artistAction).assertIsDisplayed().performClick()
        composeRule.onNode(hasText(fixture.current.value!!.album) and albumAction).assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(ArtworkViewerEntryTag).assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("queue", "more", "queue", "more"), fixture.calls)
            assertEquals(2, fixture.openedArtists.size)
            assertEquals(2, fixture.openedAlbums.size)
            assertEquals(2, fixture.artworks.size)
            assertSame(fixture.current.value, fixture.openedArtists.last())
            assertSame(fixture.current.value, fixture.openedAlbums.last())
            assertSame(fixture.current.value, fixture.artworks.last())
        }
    }

    @Test
    fun defaultCarouselUsesThePreviousAndNextInputsAndTheirTransportCallbacks() {
        val fixture = mountExpandedDefault()
        val artwork = composeRule.onNodeWithTag(ArtworkViewerEntryTag).assertIsDisplayed()
        artwork.performClick()
        composeRule.runOnIdle { assertSame(fixture.current.value, fixture.artworks.single()) }
        artwork.performTouchInput { swipeLeft(durationMillis = 350) }
        composeRule.runOnIdle {
            assertEquals(listOf("next"), fixture.calls)
            fixture.current.value = fixture.next.value
            fixture.previous.value = fixture.songs[1]
            fixture.next.value = null
        }
        awaitExpandedSnapshot(fixture, requireLiveArtwork = true)
        composeRule.onNodeWithTag(ArtworkViewerEntryTag).assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertSame(fixture.songs.last(), fixture.current.value)
            assertEquals(2, fixture.artworks.size)
            assertSame(fixture.current.value, fixture.artworks.last())
        }
        composeRule.onNodeWithTag(ArtworkViewerEntryTag).performTouchInput { swipeRight(durationMillis = 350) }
        composeRule.runOnIdle { assertEquals(listOf("next", "previous"), fixture.calls) }
    }

    @Test
    fun defaultMorphAnchorsFollowAnInsetHostWithoutReplacingPresentationOwners() {
        val fixture = Fixture(PlayerPresentation.Expanded).apply { miniVisible.value = true }
        composeRule.setContent { fixture.Content() }
        val morph = fixture.morph
        val lyrics = fixture.lyrics
        val endpoints = fixture.endpoints

        fun assertAnchorsInsideHost() {
            val host = composeRule.onNodeWithTag("player-host").fetchSemanticsNode().boundsInRoot
            val artwork = composeRule.onNodeWithTag(ArtworkViewerEntryTag)
                .fetchSemanticsNode().boundsInRoot
            val artist = composeRule.onNode(hasText(fixture.current.value!!.artist) and hasClickAction())
                .fetchSemanticsNode().boundsInRoot
            val playPause = composeRule.onNodeWithContentDescription(text(R.string.player_play))
                .fetchSemanticsNode().boundsInRoot
            assertEquals(host.center.x, artwork.center.x, 1f)
            assertEquals(host.left + fixture.contentPaddingPx, artist.left, 1f)
            assertEquals(host.center.x, playPause.center.x, 1f)
            composeRule.runOnIdle {
                assertSame(morph, fixture.morph)
                assertSame(lyrics, fixture.lyrics)
                assertSame(endpoints, fixture.endpoints)
                assertEquals(1f, fixture.morph.progress, 0f)
                assertEquals(PlayerPresentation.Expanded, fixture.morph.settledPresentation)
                assertTrue(fixture.calls.isEmpty())
            }
        }

        assertAnchorsInsideHost()
        composeRule.runOnIdle {
            fixture.hostStartInset.value = 32.dp
            fixture.hostTopInset.value = 24.dp
            fixture.hostEndInset.value = 16.dp
        }
        assertAnchorsInsideHost()
        composeRule.runOnIdle {
            fixture.hostStartInset.value = 16.dp
            fixture.hostTopInset.value = 12.dp
            fixture.hostEndInset.value = 8.dp
        }
        assertAnchorsInsideHost()
    }

    @Test
    fun railMiniAndContentStayBesideChromeAndSourceDoesNotMoveWhenPlayerExpands() {
        val fixture = Fixture(PlayerPresentation.Collapsed).apply {
            miniVisible.value = true
            adaptiveNavigation.value = true
            shellLayout.value = MusicShellLayout(MusicNavigationMode.Rail,
                MusicShellInsets(left = 12.dp, top = 24.dp, right = 16.dp, bottom = 24.dp))
        }
        composeRule.setContent { fixture.Content() }
        val host = composeRule.onNodeWithTag("player-host").fetchSemanticsNode().boundsInRoot
        val pane = composeRule.onNodeWithTag("body-pane").fetchSemanticsNode().boundsInRoot
        val source = composeRule.runOnIdle { (fixture.endpoints.mini as PlayerBoundsMeasurement.Measured).bounds }
        val generation = composeRule.runOnIdle { fixture.endpoints.layoutFrame!!.generation }
        val home = composeRule.onNodeWithContentDescription(text(R.string.navigation_home))
            .fetchSemanticsNode().boundsInRoot
        val pixelsPerDp = fixture.contentPaddingPx / 16f
        assertEquals(host.left + 110f * pixelsPerDp, pane.left, 1f)
        assertEquals(host.right - 16f * pixelsPerDp, pane.right, 1f)
        assertTrue(home.right < pane.left)
        assertTrue(source.left >= pane.left)
        assertTrue(source.right <= pane.right)
        assertTrue(source.width <= 840f * pixelsPerDp + 1f)
        assertTrue(source.bottom <= host.bottom - 32f * pixelsPerDp + 1f)

        mini(fixture).performClick()
        composeRule.onNodeWithContentDescription(text(R.string.navigation_home)).assertDoesNotExist()
        val artwork = composeRule.onNodeWithTag(ArtworkViewerEntryTag).fetchSemanticsNode().boundsInRoot
        val playPause = composeRule.onNodeWithContentDescription(text(R.string.player_play))
            .fetchSemanticsNode().boundsInRoot
        assertEquals(host.center.x, artwork.center.x, 1f)
        assertEquals(host.center.x, playPause.center.x, 1f)
        composeRule.runOnIdle {
            assertEquals(generation, fixture.endpoints.layoutFrame!!.generation)
            assertEquals(source, (fixture.endpoints.mini as PlayerBoundsMeasurement.Measured).bounds)
            assertEquals(1f, fixture.morph.progress, 0f)
            assertTrue(fixture.calls.isEmpty())
            fixture.morph.collapse()
        }
        mini(fixture).assertExists()
        composeRule.onNodeWithContentDescription(text(R.string.navigation_home)).assertExists()
        composeRule.runOnIdle {
            assertEquals(source, (fixture.endpoints.mini as PlayerBoundsMeasurement.Measured).bounds)
        }
    }

    @Test
    fun shellSwitchesWhileExpandedKeepFullHostAlignmentAndPresentationOwners() {
        val fixture = Fixture(PlayerPresentation.Expanded).apply { miniVisible.value = true }
        composeRule.setContent { fixture.Content() }
        val morph = fixture.morph
        val lyrics = fixture.lyrics
        val endpoints = fixture.endpoints
        for (layout in listOf(
            MusicShellLayout(MusicNavigationMode.Rail),
            MusicShellLayout(),
            MusicShellLayout(MusicNavigationMode.Rail, MusicShellInsets(right = 16.dp, bottom = 24.dp))
        )) {
            val generation = composeRule.runOnIdle { fixture.endpoints.layoutFrame!!.generation }
            composeRule.runOnIdle { fixture.shellLayout.value = layout }
            val host = composeRule.onNodeWithTag("player-host").fetchSemanticsNode().boundsInRoot
            val artwork = composeRule.onNodeWithTag(ArtworkViewerEntryTag).fetchSemanticsNode().boundsInRoot
            assertEquals(host.center.x, artwork.center.x, 1f)
            composeRule.runOnIdle {
                assertSame(morph, fixture.morph)
                assertSame(lyrics, fixture.lyrics)
                assertSame(endpoints, fixture.endpoints)
                assertTrue(fixture.endpoints.layoutFrame!!.generation > generation)
                assertTrue(fixture.endpoints.mini is PlayerBoundsMeasurement.Measured)
                assertEquals(1f, fixture.morph.progress, 0f)
                assertTrue(fixture.calls.isEmpty())
            }
        }
    }

    @Test
    fun shellBoundaryDuringMiniDragKeepsProgressInputOwnershipAndReleaseTarget() {
        val fixture = Fixture(PlayerPresentation.Collapsed).apply {
            miniVisible.value = true
            adaptiveNavigation.value = true
        }
        composeRule.setContent { fixture.Content() }
        composeRule.runOnIdle { fixture.morph.beginDragWithRange(500f) }
        composeRule.runOnIdle { fixture.morph.dragBy(-180f) }
        val progress = composeRule.runOnIdle { fixture.morph.progress }
        composeRule.runOnIdle {
            fixture.shellLayout.value = MusicShellLayout(MusicNavigationMode.Rail)
            fixture.hostEndInset.value = 24.dp
        }
        mini(fixture).assertDoesNotExist() // The composed source yields semantics/input to the morph.
        composeRule.onNodeWithContentDescription(text(R.string.navigation_home)).assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(progress, fixture.morph.progress, 0f)
            assertTrue(fixture.morph.isDragging)
            assertTrue(fixture.endpoints.mini is PlayerBoundsMeasurement.Measured)
            assertTrue(fixture.calls.isEmpty())
            fixture.morph.endDrag(0f)
        }
        composeRule.runOnIdle {
            assertEquals(PlayerPresentation.Expanded, fixture.morph.settledPresentation)
            assertEquals(1f, fixture.morph.progress, 0f)
            assertTrue(fixture.calls.isEmpty())
        }
    }

    @Test
    fun balancedDestinationsStayInTheFullInsetHostWithArtworkLeftAndControlsRight() {
        val fixture = tabletFixture().apply {
            observeDefaultMorphGeometry = true
            hostStartInset.value = 32.dp
            hostTopInset.value = 24.dp
            hostEndInset.value = 16.dp
        }
        composeRule.setContent { fixture.Content() }
        val snapshot = assertExpandedGeometry(fixture)
        val host = snapshot.expanded.surface
        val play = composeRule.onNodeWithContentDescription(text(R.string.player_play))
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Exercise a translated host; ${snapshot.description}", host.left > 0f && host.top > 0f)
        assertTrue("Play/pause must be right of host center; ${snapshot.description}", play.center.x > host.center.x)
        assertTrue("Play/pause must be below metadata; ${snapshot.description}", play.top > snapshot.expanded.text.bottom)
        composeRule.runOnIdle {
            assertEquals(DefaultPlayerArrangement.Balanced, fixture.resolvedPlayerLayout!!.arrangement)
            assertEquals(host, (fixture.endpoints.expanded as PlayerBoundsMeasurement.Measured).bounds)
            assertTrue(fixture.endpoints.mini is PlayerBoundsMeasurement.Measured)
            assertEquals(1f, fixture.morph.progress, 0f)
            assertTrue(fixture.calls.isEmpty())
            assertTrue(fixture.seeks.isEmpty())
        }
    }

    @Test
    fun sameSizeAppearanceArrangementSwitchKeepsOwnersAndRefreshesMeasuredAnchors() {
        val fixture = tabletFixture().apply { viewport.value = DpSize(1200.dp, 450.dp) }
        composeRule.setContent { fixture.Content() }
        val morph = fixture.morph
        val lyrics = fixture.lyrics
        val endpoints = fixture.endpoints
        val host = composeRule.onNodeWithTag("player-host").fetchSemanticsNode().boundsInRoot
        val generation = composeRule.runOnIdle {
            assertEquals(DefaultPlayerArrangement.Balanced, fixture.resolvedPlayerLayout!!.arrangement)
            endpoints.layoutFrame!!.generation
        }
        composeRule.runOnIdle { fixture.appearance.value = tallerPlayerAppearance() }
        composeRule.runOnIdle {
            assertEquals(DefaultPlayerArrangement.Vertical, fixture.resolvedPlayerLayout!!.arrangement)
            assertSame(morph, fixture.morph)
            assertSame(lyrics, fixture.lyrics)
            assertSame(endpoints, fixture.endpoints)
            assertTrue(endpoints.layoutFrame!!.generation > generation)
            assertTrue(endpoints.mini is PlayerBoundsMeasurement.Measured)
            assertEquals(1f, morph.progress, 0f)
            assertEquals(PlayerPresentation.Expanded, morph.settledPresentation)
            assertTrue(fixture.calls.isEmpty())
            assertTrue(fixture.seeks.isEmpty())
        }
        assertEquals(host, composeRule.onNodeWithTag("player-host").fetchSemanticsNode().boundsInRoot)
        composeRule.runOnIdle { fixture.appearance.value = ModernPlayerAppearance.Default }
        val artwork = composeRule.onNodeWithTag(ArtworkViewerEntryTag).fetchSemanticsNode().boundsInRoot
        val play = composeRule.onNodeWithContentDescription(text(R.string.player_play))
            .fetchSemanticsNode().boundsInRoot
        assertTrue(artwork.center.x < host.center.x)
        assertTrue(play.center.x > host.center.x)
        composeRule.runOnIdle {
            assertEquals(DefaultPlayerArrangement.Balanced, fixture.resolvedPlayerLayout!!.arrangement)
            assertSame(morph, fixture.morph)
            assertTrue(fixture.calls.isEmpty())
        }
    }

    @Test
    fun arrangementSwitchDuringDragKeepsProgressAndAllowsReversalWithoutTransportCommands() {
        val fixture = tabletFixture(PlayerPresentation.Collapsed).apply {
            viewport.value = DpSize(1200.dp, 450.dp)
        }
        composeRule.setContent { fixture.Content() }
        val morph = fixture.morph
        val lyrics = fixture.lyrics
        val endpoints = fixture.endpoints
        composeRule.runOnIdle {
            morph.beginDragWithRange(500f)
            morph.dragBy(-220f)
        }
        val progress = composeRule.runOnIdle { morph.progress }
        val generation = composeRule.runOnIdle { endpoints.layoutFrame!!.generation }
        composeRule.runOnIdle { fixture.appearance.value = tallerPlayerAppearance() }
        mini(fixture).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(text(R.string.navigation_home)).assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(DefaultPlayerArrangement.Vertical, fixture.resolvedPlayerLayout!!.arrangement)
            assertSame(morph, fixture.morph)
            assertSame(lyrics, fixture.lyrics)
            assertSame(endpoints, fixture.endpoints)
            assertEquals(progress, morph.progress, 0f)
            assertTrue(morph.isDragging)
            assertTrue(endpoints.layoutFrame!!.generation > generation)
            assertTrue(endpoints.mini is PlayerBoundsMeasurement.Measured)
            // Reverse the existing captured drag, rather than starting a new controller.
            morph.dragBy(160f)
        }
        composeRule.runOnIdle {
            assertTrue(morph.progress < progress)
            morph.endDrag(0f)
        }
        mini(fixture).assertExists()
        composeRule.runOnIdle {
            assertEquals(PlayerPresentation.Collapsed, morph.settledPresentation)
            assertEquals(0f, morph.progress, 0f)
            assertTrue(fixture.calls.isEmpty())
            assertTrue(fixture.seeks.isEmpty())
        }
        mini(fixture).performClick()
        composeRule.runOnIdle {
            assertEquals(PlayerPresentation.Expanded, morph.settledPresentation)
            assertEquals(1f, morph.progress, 0f)
            assertSame(morph, fixture.morph)
            assertTrue(fixture.calls.isEmpty())
        }
    }

    @Test
    fun balancedLongMetadataAndLargeControlsRemainContainedAndUseExistingActionSinks() {
        val fixture = tabletFixture().apply {
            observeDefaultMorphGeometry = true
            viewport.value = DpSize(1200.dp, 760.dp)
            fontScale.value = 1.25f
            appearance.value = tallerPlayerAppearance()
            current.value = songs[1].copy(
                title = "A long title that wraps while retaining the existing metadata renderer ".repeat(3),
                artist = "An unusually long artist name with several collaborators ".repeat(3),
                album = "An unusually long album name with edition information ".repeat(3)
            )
        }
        composeRule.setContent { fixture.Content() }
        val snapshot = assertExpandedGeometry(fixture)
        val host = snapshot.expanded.surface
        for (description in listOf(R.string.player_play, R.string.player_previous_song,
            R.string.player_next_song, R.string.player_shuffle_off, R.string.player_repeat_off,
            R.string.player_open_queues, R.string.player_more_actions)) {
            assertInside(host, composeRule.onNodeWithContentDescription(text(description))
                .fetchSemanticsNode().boundsInRoot)
        }
        val seek = composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
        assertInside(host, seek.fetchSemanticsNode().boundsInRoot)
        seek.performSemanticsAction(SemanticsActions.SetProgress) { it(90_000f) }
        for (description in listOf(R.string.player_play, R.string.player_open_queues,
            R.string.player_more_actions, R.string.player_shuffle_off, R.string.player_repeat_off,
            R.string.player_next_song)) {
            composeRule.onNodeWithContentDescription(text(description)).performClick()
        }
        composeRule.runOnIdle {
            assertEquals(DefaultPlayerArrangement.Balanced, fixture.resolvedPlayerLayout!!.arrangement)
            assertEquals(listOf(90_000), fixture.seeks)
            assertEquals(listOf("play-pause", "queue", "more", "shuffle", "repeat", "next"), fixture.calls)
        }
    }

    @Test
    fun balancedCarouselKeepsArtworkAndMetadataOnTheSameCurrentTrack() {
        val fixture = tabletFixture()
        composeRule.setContent { fixture.Content() }
        composeRule.onNodeWithTag(ArtworkViewerEntryTag).performTouchInput {
            swipeLeft(durationMillis = 350)
        }
        composeRule.runOnIdle {
            assertEquals(listOf("next"), fixture.calls)
            fixture.current.value = fixture.songs.last()
            fixture.previous.value = fixture.songs[1]
            fixture.next.value = null
        }
        composeRule.onNode(hasText(fixture.current.value!!.artist) and hasClickAction()).performClick()
        composeRule.onNodeWithTag(ArtworkViewerEntryTag).performClick()
        composeRule.runOnIdle {
            assertSame(fixture.current.value, fixture.openedArtists.single())
            assertSame(fixture.current.value, fixture.artworks.single())
        }
        composeRule.onNodeWithTag(ArtworkViewerEntryTag).performTouchInput {
            swipeRight(durationMillis = 350)
        }
        composeRule.runOnIdle {
            assertEquals(listOf("next", "previous"), fixture.calls)
            assertTrue(fixture.seeks.isEmpty())
        }
    }

    @Test
    fun shortVerticalFallbackScrollsPersistentMorphContentWithoutShrinkingItOrDispatchingPlayback() {
        val fixture = tabletFixture().apply { viewport.value = DpSize(1200.dp, 380.dp) }
        composeRule.setContent { fixture.Content() }
        val artworkBefore = composeRule.onNodeWithTag(ArtworkViewerEntryTag).fetchSemanticsNode()
        val artworkWidth = artworkBefore.size.width
        val artworkTop = artworkBefore.positionInRoot.y
        composeRule.runOnIdle {
            assertEquals(DefaultPlayerArrangement.Vertical, fixture.resolvedPlayerLayout!!.arrangement)
            assertTrue(fixture.resolvedPlayerLayout!!.scrollVertical)
            assertTrue(fixture.calls.isEmpty())
            assertTrue(fixture.seeks.isEmpty())
        }

        // Use the real scroll state's accessibility action: it isolates scroll geometry from the
        // artwork's carousel and the player's vertical collapse/lyrics gesture recognizers.
        composeRule.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) {
            it(0f, 10_000f)
        }
        val artworkAfter = composeRule.onNodeWithTag(ArtworkViewerEntryTag).fetchSemanticsNode()
        assertEquals(artworkWidth, artworkAfter.size.width)
        assertTrue("Artwork must follow scroll instead of retaining its old morph position",
            artworkAfter.positionInRoot.y < artworkTop)
        val host = composeRule.onNodeWithTag("player-host").fetchSemanticsNode().boundsInRoot
        val play = composeRule.onNodeWithContentDescription(text(R.string.player_play))
        val seek = composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
        assertInside(host, play.fetchSemanticsNode().boundsInRoot)
        assertInside(host, seek.fetchSemanticsNode().boundsInRoot)
        composeRule.runOnIdle {
            assertTrue("Scrolling must not issue transport callbacks", fixture.calls.isEmpty())
            assertTrue("Scrolling must not issue a seek", fixture.seeks.isEmpty())
            assertEquals(1f, fixture.morph.progress, 0f)
        }
        play.performClick()
        seek.performSemanticsAction(SemanticsActions.SetProgress) { it(60_000f) }
        composeRule.runOnIdle {
            assertEquals(listOf("play-pause"), fixture.calls)
            assertEquals(listOf(60_000), fixture.seeks)
        }
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun wideWaveformUsesLowerFullHostProgressAndEveryExistingSeekbarStyle() {
        val fixture = tabletFixture().apply {
            observeDefaultMorphGeometry = true
            hostStartInset.value = 32.dp
            hostTopInset.value = 24.dp
            hostEndInset.value = 16.dp
            playerInsets.value = DefaultPlayerInsets(16.dp, 20.dp, 12.dp, 24.dp)
            progress.value = PlaybackProgressUiState(currentPosition = 60_000, duration = 180_000)
        }
        composeRule.setContent { fixture.Content() }
        val balanced = assertExpandedGeometry(fixture)
        val balancedProgress = placedProgressBounds()
        composeRule.runOnIdle { fixture.landscapeLayout.value = ModernLandscapeLayout.WIDE_WAVEFORM }
        for (style in ModernSeekbarStyle.entries) {
            composeRule.runOnIdle {
                fixture.appearance.value = fixture.appearance.value.copy(seekbar =
                    fixture.appearance.value.seekbar.copy(style = style, waveformDensity = ModernWaveformDensity.SPARSE))
            }
            val snapshot = assertExpandedGeometry(fixture, DefaultPlayerArrangement.WideWaveform)
            val progress = placedProgressBounds()
            val description = "style=$style progress=$progress; ${snapshot.description}"
            assertInside(snapshot.expanded.surface, progress)
            val host = snapshot.expanded.surface
            val density = snapshot.frame.density
            val foreground = Rect(host.left + 32f * density, host.top + 32f * density,
                host.right - 28f * density, host.bottom - 36f * density)
            assertInside(foreground, snapshot.expanded.artwork)
            assertInside(foreground, snapshot.expanded.text)
            assertInside(foreground, snapshot.expanded.playPause)
            assertInside(foreground, progress)
            assertEquals("Lower progress fills usable foreground; $description", foreground.left, progress.left, 1f)
            assertEquals("Lower progress fills usable foreground; $description", foreground.right, progress.right, 1f)
            assertTrue(description, progress.top > snapshot.expanded.artwork.bottom)
            assertTrue(description, progress.top > snapshot.expanded.playPause.bottom)
            assertTrue(description, progress.top > snapshot.expanded.text.bottom)
            assertTrue(description, progress.left < snapshot.expanded.artwork.right)
            assertTrue(description, progress.right > snapshot.expanded.text.left)
            assertTrue(description, progress.width > balancedProgress.width)
            assertTrue(description, progress.width > snapshot.expanded.text.width)
            assertTrue(description, snapshot.frame.generation > balanced.frame.generation)
            for (time in listOf(formatDuration(60_000), formatDuration(180_000))) {
                val label = composeRule.onNodeWithText(time, useUnmergedTree = true)
                    .fetchSemanticsNode().layoutInfo.coordinates.boundsInRoot()
                assertInside(snapshot.expanded.surface, label)
                assertTrue("Time must follow the track: label=$label; $description", label.top >= progress.bottom)
            }
            val seek = composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress), useUnmergedTree = true)
            assertEquals(60_000f, seek.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current, 0f)
            seek.performSemanticsAction(SemanticsActions.SetProgress) { it(90_000f) }
            seek.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            seek.performKeyInput { pressKey(Key.DirectionRight) }
        }
        composeRule.runOnIdle {
            assertEquals(ModernSeekbarStyle.entries.size * 2, fixture.seeks.size)
            fixture.seeks.chunked(2).forEach { requests ->
                assertEquals(90_000, requests[0])
                assertTrue("Right-arrow seeking must advance from 60 seconds: $requests", requests[1] > 60_000)
            }
            assertEquals(60_000, fixture.progress.value.currentPosition)
            assertTrue(fixture.calls.isEmpty())
        }
    }

    @Test
    fun widePreferenceSwitchAndFallbackKeepCurrentSnapshotOwnersAndPosition() {
        val fixture = tabletFixture().apply { observeDefaultMorphGeometry = true }
        composeRule.setContent { fixture.Content() }
        val initial = assertExpandedGeometry(fixture)
        val morph = fixture.morph
        val lyrics = fixture.lyrics
        val endpoints = fixture.endpoints
        val song = fixture.current.value
        val progress = fixture.progress.value
        val carousel = rememberedCarouselState(fixture.playerComposition)
        composeRule.runOnIdle { fixture.landscapeLayout.value = ModernLandscapeLayout.WIDE_WAVEFORM }
        val wide = assertExpandedGeometry(fixture, DefaultPlayerArrangement.WideWaveform)
        assertTrue(wide.frame.generation > initial.frame.generation)
        composeRule.runOnIdle { fixture.viewport.value = DpSize(1200.dp, 440.dp) }
        assertExpandedGeometry(fixture) // Insufficient room below the upper row: Balanced fallback.
        composeRule.runOnIdle { fixture.viewport.value = DpSize(412.dp, 850.dp) }
        composeRule.runOnIdle {
            assertEquals(DefaultPlayerArrangement.Vertical, fixture.resolvedPlayerLayout!!.arrangement)
            assertEquals(ModernLandscapeLayout.WIDE_WAVEFORM, fixture.landscapeLayout.value)
        }
        val portraitGeneration = composeRule.runOnIdle { fixture.endpoints.layoutFrame!!.generation }
        composeRule.runOnIdle { fixture.landscapeLayout.value = ModernLandscapeLayout.BALANCED }
        composeRule.runOnIdle { assertEquals(portraitGeneration, fixture.endpoints.layoutFrame!!.generation) }
        composeRule.runOnIdle { fixture.landscapeLayout.value = ModernLandscapeLayout.WIDE_WAVEFORM }
        composeRule.runOnIdle { assertEquals(portraitGeneration, fixture.endpoints.layoutFrame!!.generation) }
        composeRule.runOnIdle { fixture.viewport.value = DpSize(1200.dp, 720.dp) }
        assertExpandedGeometry(fixture, DefaultPlayerArrangement.WideWaveform)
        composeRule.runOnIdle {
            assertSame(morph, fixture.morph)
            assertSame(lyrics, fixture.lyrics)
            assertSame(endpoints, fixture.endpoints)
            assertSame(carousel, rememberedCarouselState(fixture.playerComposition))
            assertSame(song, fixture.current.value)
            assertEquals(progress, fixture.progress.value)
            assertEquals(1f, morph.progress, 0f)
            assertTrue(fixture.calls.isEmpty())
            assertTrue(fixture.seeks.isEmpty())
        }
        // Even while lyrics owns the presentation, changing arrangement must not close it.
        composeRule.runOnIdle { fixture.lyrics.openLyrics() }
        composeRule.runOnIdle { fixture.landscapeLayout.value = ModernLandscapeLayout.BALANCED }
        composeRule.runOnIdle { assertTrue(fixture.lyricsVisible.value) }
        composeRule.runOnIdle { fixture.lyrics.snapToExpanded() }
        assertExpandedGeometry(fixture)
    }

    @Test
    fun wideSwitchDuringMorphKeepsDragReversalAndTheNextExpansion() {
        val fixture = tabletFixture(PlayerPresentation.Collapsed)
        composeRule.setContent { fixture.Content() }
        val morph = fixture.morph
        composeRule.runOnIdle { morph.beginDragWithRange(500f); morph.dragBy(-220f) }
        val progress = composeRule.runOnIdle { morph.progress }
        val generation = composeRule.runOnIdle { fixture.endpoints.layoutFrame!!.generation }
        composeRule.runOnIdle { fixture.landscapeLayout.value = ModernLandscapeLayout.WIDE_WAVEFORM }
        mini(fixture).assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(DefaultPlayerArrangement.WideWaveform, fixture.resolvedPlayerLayout!!.arrangement)
            assertEquals(progress, morph.progress, 0f)
            assertTrue(morph.isDragging)
            assertTrue(fixture.endpoints.layoutFrame!!.generation > generation)
            morph.dragBy(160f)
        }
        composeRule.runOnIdle { morph.endDrag(0f) }
        mini(fixture).performClick()
        composeRule.runOnIdle {
            assertSame(morph, fixture.morph)
            assertEquals(PlayerPresentation.Expanded, morph.settledPresentation)
            assertEquals(1f, morph.progress, 0f)
            assertTrue(fixture.calls.isEmpty())
            assertTrue(fixture.seeks.isEmpty())
        }
    }

    @Test
    fun wideLongMetadataLargeControlsAndTallTracksRetainActionsAndTrackTargets() {
        // The long artist must exist in the library as well as the current-song presentation;
        // production intentionally offers no artist navigation for an absent library artist.
        val fixture = tabletFixture(transformSong = { song ->
            if (song.id == 2L) song.copy(title = "Long title with several words ".repeat(8),
                artist = "Artist and collaborators ".repeat(6), album = "Album edition ".repeat(6))
            else song
        }).apply {
            observeDefaultMorphGeometry = true
            landscapeLayout.value = ModernLandscapeLayout.WIDE_WAVEFORM
            viewport.value = DpSize(1200.dp, 760.dp)
            fontScale.value = 1.25f
            appearance.value = tallerPlayerAppearance()
        }
        composeRule.setContent { fixture.Content() }
        for (size in ModernWaveformSize.entries) {
            composeRule.runOnIdle { fixture.appearance.value = fixture.appearance.value.copy(
                seekbar = fixture.appearance.value.seekbar.copy(style = ModernSeekbarStyle.WAVEFORM_PEAKS,
                    waveformSize = size, waveformDensity = ModernWaveformDensity.SPARSE)) }
            val snapshot = assertExpandedGeometry(fixture, DefaultPlayerArrangement.WideWaveform)
            val seek = placedProgressBounds()
            assertInside(snapshot.expanded.surface, seek)
            assertTrue(snapshot.description, seek.top > snapshot.expanded.artwork.bottom)
            for (description in listOf(R.string.player_play, R.string.player_previous_song,
                R.string.player_next_song, R.string.player_shuffle_off, R.string.player_repeat_off,
                R.string.player_open_queues, R.string.player_more_actions)) {
                val bounds = composeRule.onNodeWithContentDescription(text(description)).fetchSemanticsNode().boundsInRoot
                assertInside(snapshot.expanded.surface, bounds)
                assertTrue("Upper control overlaps progress: $bounds seek=$seek; ${snapshot.description}", bounds.bottom < seek.top)
            }
        }
        // A merged host can contain this text and a no-op click. Require the intended action,
        // independently of the actual metadata geometry checked above.
        val artistLabel = text(R.string.library_search_go_artist)
        val albumLabel = text(R.string.player_open_current_album)
        composeRule.onNode(hasText(fixture.current.value!!.artist) and
            SemanticsMatcher("Current artist navigation action") {
                it.config.getOrNull(SemanticsActions.OnClick)?.label == artistLabel
            }).performClick()
        composeRule.onNode(hasText(fixture.current.value!!.album) and
            SemanticsMatcher("Current album navigation action") {
                it.config.getOrNull(SemanticsActions.OnClick)?.label == albumLabel
            }).performClick()
        composeRule.onNodeWithTag(ArtworkViewerEntryTag).performClick()
        for (description in listOf(R.string.player_play, R.string.player_open_queues, R.string.player_more_actions,
            R.string.player_shuffle_off, R.string.player_repeat_off, R.string.player_next_song)) {
            composeRule.onNodeWithContentDescription(text(description)).performClick()
        }
        composeRule.runOnIdle {
            assertEquals(listOf("play-pause", "queue", "more", "shuffle", "repeat", "next"), fixture.calls)
            assertEquals("Artist navigation must dispatch exactly once", 1, fixture.openedArtists.size)
            assertSame(fixture.current.value, fixture.openedArtists.single())
            assertEquals("Album navigation must dispatch exactly once", 1, fixture.openedAlbums.size)
            assertSame(fixture.current.value, fixture.openedAlbums.single())
            assertEquals("Artwork navigation must dispatch exactly once", 1, fixture.artworks.size)
            assertSame(fixture.current.value, fixture.artworks.single())
            fixture.current.value = fixture.songs.last()
            fixture.previous.value = fixture.songs[1]
            fixture.next.value = null
        }
        assertExpandedGeometry(fixture, DefaultPlayerArrangement.WideWaveform)
        composeRule.onNodeWithTag(ArtworkViewerEntryTag).performTouchInput { swipeRight(durationMillis = 350) }
        composeRule.runOnIdle {
            assertEquals("previous", fixture.calls.last())
            assertEquals(7, fixture.calls.size)
            assertTrue(fixture.seeks.isEmpty())
        }
    }

    @Test
    fun wideSeekDisposalCancelsDirectScrubAndTheNextGestureCommitsOnce() {
        val fixture = tabletFixture().apply { landscapeLayout.value = ModernLandscapeLayout.WIDE_WAVEFORM }
        val environment = DirectSeekGestureEnvironment({ DirectScrubAuthority(true, 1, 0, true) }, {})
        composeRule.setContent {
            CompositionLocalProvider(LocalDirectSeekGestureEnvironment provides environment) { fixture.Content() }
        }
        fun seek() = composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress), useUnmergedTree = true)
        seek().performTouchInput { down(center); moveTo(Offset(width * 0.8f, centerY)) }
        composeRule.runOnIdle {
            assertTrue(fixture.seeks.isEmpty())
            fixture.landscapeLayout.value = ModernLandscapeLayout.BALANCED
        }
        seek().performTouchInput { up() }
        composeRule.runOnIdle { assertTrue(fixture.seeks.isEmpty()) }
        seek().performTouchInput { click(center) }
        composeRule.runOnIdle {
            assertEquals(1, fixture.seeks.size)
            assertTrue(fixture.seeks.single() in 60_000..120_000)
            assertTrue(fixture.calls.isEmpty())
        }
    }

    private data class AdvanceSnapshot(
        val owner: PocketAdvancePlayerMorphBounds,
        val frame: PlayerMorphLayoutFrame,
        val mini: PocketAdvanceMorphGeometry,
        val expanded: PocketAdvanceMorphGeometry
    )

    private fun advanceFixture() = tabletFixture(PlayerPresentation.Collapsed).apply {
        theme.value = PlayerTheme.CLASSIC_WHEEL
        deviceKind.value = PlayerDeviceKind.TABLET
        nominateAdvance.value = true
        observeAdvanceMorphGeometry = true
    }

    private fun awaitAdvanceSnapshot(fixture: Fixture): AdvanceSnapshot {
        var snapshot: AdvanceSnapshot? = null
        var readiness = "No Pocket Advance geometry observed"
        try {
            composeRule.waitUntil(timeoutMillis = 5_000) {
                snapshot = composeRule.runOnIdle {
                    val owners = mutableSetOf<PocketAdvancePlayerMorphBounds>()
                    fun collect(data: CompositionData) {
                        data.compositionGroups.forEach { group ->
                            owners.addAll(group.data.filterIsInstance<PocketAdvancePlayerMorphBounds>())
                            collect(group)
                        }
                    }
                    fixture.playerComposition?.let(::collect)
                    readiness = "owners=${owners.size}; identity=${fixture.resolvedIdentity}; " +
                        "viewport=${fixture.viewport.value}; lyricsProgress=${fixture.lyrics.progress}"
                    val owner = owners.singleOrNull() ?: return@runOnIdle null
                    val cached = owner.morphField("lastCompleteEndpoints")
                    val frame = fixture.endpoints.layoutFrame
                    val map = (owner.morphField("measurements\$delegate") as State<*>).value as Map<*, *>
                    val epoch = owner.morphField("identityEpoch")
                    // Inspect the real required element set; never fabricate or promote anchors here.
                    val unresolved = owner.javaClass.declaredClasses.single { it.simpleName == "Element" }
                        .enumConstants.mapNotNull { element ->
                            val measurement = map[element] ?: return@mapNotNull "$element: missing"
                            val rect = measurement.morphField("bounds") as Rect
                            val issues = buildList {
                                if (!listOf(rect.left, rect.top, rect.right, rect.bottom).all { it.isFinite() } ||
                                    rect.width <= 0f || rect.height <= 0f) add("invalid bounds=$rect")
                                if (measurement.morphField("frame") != frame) add("stale frame=${measurement.morphField("frame")}")
                                if (measurement.morphField("identityEpoch") != epoch) add("stale epoch=${measurement.morphField("identityEpoch")}")
                            }
                            if (issues.isEmpty()) null else "$element: ${issues.joinToString()}"
                        }
                    val currentMap = cached != null && cached.morphField("measurements") === map
                    readiness += "; frame=$frame; epoch=$epoch; " +
                        "cachedFrame=${cached?.morphField("frame")}; currentMeasurements=$currentMap; " +
                        "mini=${fixture.endpoints.mini}; expanded=${fixture.endpoints.expanded}; " +
                        "unresolved=${unresolved.joinToString()}"
                    // Preserve the original readiness contract and five-second limit.
                    if (cached == null || frame == null || cached.morphField("frame") != frame || !currentMap ||
                        fixture.endpoints.mini !is PlayerBoundsMeasurement.Measured ||
                        fixture.resolvedIdentity != PlayerPresentationIdentity.PocketAdvance) null
                    else AdvanceSnapshot(owner, frame,
                        cached.morphField("mini") as PocketAdvanceMorphGeometry,
                        cached.morphField("expanded") as PocketAdvanceMorphGeometry)
                }
                snapshot != null
            }
        } catch (timeout: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError("No current complete Pocket Advance snapshot: $readiness", timeout)
        }
        return requireNotNull(snapshot)
    }

    private fun assertRectNear(expected: Rect, actual: Rect) {
        assertEquals(expected.left, actual.left, 1f)
        assertEquals(expected.top, actual.top, 1f)
        assertEquals(expected.width, actual.width, 1f)
        assertEquals(expected.height, actual.height, 1f)
    }

    private fun placedProgressBounds(): Rect {
        val node = composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress),
            useUnmergedTree = true).fetchSemanticsNode()
        assertTrue("Measure the actual Slider, not a merged text container",
            node.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo) != null)
        val host = composeRule.onNodeWithTag("player-host").fetchSemanticsNode()
        assertTrue("Slider must have its own layout", node.layoutInfo !== host.layoutInfo)
        return composeRule.runOnIdle { node.layoutInfo.coordinates.boundsInRoot() }
    }

    private fun rememberedCarouselState(data: CompositionData?): ModernArtworkCarouselState {
        val owners = mutableSetOf<ModernArtworkCarouselState>()
        fun collect(data: CompositionData) {
            data.compositionGroups.forEach { group ->
                owners.addAll(group.data.filterIsInstance<ModernArtworkCarouselState>())
                collect(group)
            }
        }
        data?.let(::collect)
        return owners.single()
    }

    private data class ExpandedGeometrySnapshot(
        val frame: PlayerMorphLayoutFrame,
        val arrangement: DefaultPlayerArrangement,
        val expanded: DefaultPlayerMorphGeometry
    ) {
        val description: String get() =
            "arrangement=$arrangement; generation=${frame.generation}; origin=${frame.originInRoot}; " +
                "expandedRoot=$expanded; gapPx=${expanded.text.left - expanded.artwork.right}"
    }

    private fun awaitExpandedSnapshot(fixture: Fixture,
        requireLiveArtwork: Boolean = false): ExpandedGeometrySnapshot {
        var captured: ExpandedGeometrySnapshot? = null
        var readiness = "No geometry observed"
        var liveArtworkCount = 0
        try {
            composeRule.waitUntil(timeoutMillis = 5_000) {
                captured = composeRule.runOnIdle {
                    val frame = fixture.endpoints.layoutFrame
                    val layout = fixture.resolvedPlayerLayout
                    val owners = rememberedDefaultMorphBounds(fixture.playerComposition)
                    val owner = owners.singleOrNull()
                    // No production API exposes the promoted snapshot. Keep only the read-only
                    // inspection needed to reject an old frame or measurements updated since promotion.
                    val measurements = owner?.let { (it.morphField("measurements\$delegate") as State<*>).value }
                    val cached = owner?.morphField("lastCompleteEndpoints")
                    val cachedFrame = cached?.morphField("frame") as? PlayerMorphLayoutFrame
                    val expanded = cached?.morphField("expanded") as? DefaultPlayerMorphGeometry
                    val sameMeasurements = cached != null && cached.morphField("measurements") === measurements
                    val expandedSurface = (fixture.endpoints.expanded as? PlayerBoundsMeasurement.Measured)?.bounds
                    val expectedBoundary: Any = if (fixture.adaptiveDefaultLayout.value) {
                        Pair(fixture.shellLayout.value, layout)
                    } else fixture.shellLayout.value
                    val boundaryMatches = frame != null && frame.layoutBoundary == expectedBoundary
                    // Production promotes only complete, valid seven-element snapshots from one
                    // frame. Matching both frame and immutable map identity verifies that contract
                    // still holds, without resolving geometry or changing the cache in the test.
                    val current = frame != null && cachedFrame == frame && sameMeasurements &&
                        boundaryMatches && fixture.endpoints.mini is PlayerBoundsMeasurement.Measured &&
                        expandedSurface == frame.boundsInRoot && cached?.morphField("surfaceInRoot") == expandedSurface
                    val settled = fixture.morph.progress == 1f &&
                        fixture.morph.targetPresentation == PlayerPresentation.Expanded &&
                        fixture.morph.settledPresentation == PlayerPresentation.Expanded &&
                        !fixture.morph.isDragging && !fixture.morph.isAnimating
                    readiness = "arrangement=${layout?.arrangement}; owners=${owners.size}; " +
                        "frameGeneration=${frame?.generation}; snapshotGeneration=${cachedFrame?.generation}; " +
                        "boundaryMatches=$boundaryMatches; boundary=${frame?.layoutBoundary}; expectedBoundary=$expectedBoundary; " +
                        "currentMeasurements=$sameMeasurements; currentSnapshot=$current; settled=$settled; " +
                        "progress=${fixture.morph.progress}; mini=${fixture.endpoints.mini}; expandedSurface=$expandedSurface"
                    if (!current || !settled || expanded == null ||
                        fixture.adaptiveDefaultLayout.value && layout == null) {
                        null
                    } else {
                        // The promoted geometry is host-local. Convert it exactly once so every
                        // comparison below, including normal semantics bounds, uses root pixels.
                        val origin = frame.originInRoot
                        ExpandedGeometrySnapshot(frame, layout?.arrangement ?: DefaultPlayerArrangement.Vertical, expanded.copy(
                            surface = expanded.surface.translate(origin),
                            artwork = expanded.artwork.translate(origin),
                            text = expanded.text.translate(origin),
                            playPause = expanded.playPause.translate(origin)
                        ))
                    }
                }
                captured != null && (!requireLiveArtwork || run {
                    // Only the current, settled shared artwork exposes this live entry action.
                    // Expanded measurement placeholders never expose it.
                    val artworkNodes = composeRule.onAllNodesWithTag(ArtworkViewerEntryTag).fetchSemanticsNodes()
                    liveArtworkCount = artworkNodes.size
                    artworkNodes.singleOrNull()?.config?.getOrNull(SemanticsActions.OnClick) != null
                })
            }
        } catch (timeout: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError("No settled current complete morph snapshot: $readiness; " +
                "liveArtworkRequired=$requireLiveArtwork; liveArtworkCount=$liveArtworkCount", timeout)
        }
        return requireNotNull(captured)
    }

    private fun assertExpandedGeometry(fixture: Fixture,
        expectedArrangement: DefaultPlayerArrangement = DefaultPlayerArrangement.Balanced): ExpandedGeometrySnapshot {
        val snapshot = awaitExpandedSnapshot(fixture)
        val expanded = snapshot.expanded
        val host = composeRule.onNodeWithTag("player-host").fetchSemanticsNode().boundsInRoot
        assertEquals(snapshot.description, expectedArrangement, snapshot.arrangement)
        assertEquals("Expanded surface must fill the actual host; ${snapshot.description}", host, expanded.surface)
        assertInside(host, expanded.artwork)
        assertInside(host, expanded.text)
        assertInside(host, expanded.playPause)
        assertTrue("Expanded artwork must end strictly before metadata; ${snapshot.description}",
            expanded.artwork.right < expanded.text.left)
        // Retain containment coverage for the rendered artwork and play/pause visuals as well.
        val renderedArtwork = composeRule.onNodeWithTag(ArtworkViewerEntryTag).fetchSemanticsNode().boundsInRoot
        assertInside(host, renderedArtwork)
        assertTrue("Rendered artwork must end strictly before metadata; artwork=$renderedArtwork; renderedGapPx=${expanded.text.left - renderedArtwork.right}; ${snapshot.description}",
            renderedArtwork.right < expanded.text.left)
        assertInside(host, composeRule.onNodeWithContentDescription(text(R.string.player_play))
            .fetchSemanticsNode().boundsInRoot)
        // Text can be aggregated into a full-host merged node. Verify its information/action
        // independently; its semantics rectangle is not the measured metadata destination.
        composeRule.onNode(hasText(fixture.current.value!!.title)).assertExists()
        composeRule.onNode(hasText(fixture.current.value!!.artist) and hasClickAction()).assertExists()
        return snapshot
    }

    private fun rememberedDefaultMorphBounds(data: CompositionData?): Set<DefaultPlayerMorphBounds> {
        val owners = mutableSetOf<DefaultPlayerMorphBounds>()
        fun collect(data: CompositionData) {
            data.compositionGroups.forEach { group ->
                owners.addAll(group.data.filterIsInstance<DefaultPlayerMorphBounds>())
                collect(group)
            }
        }
        data?.let(::collect)
        return owners
    }

    private fun Any.morphField(name: String): Any? =
        javaClass.getDeclaredField(name).apply { isAccessible = true }.get(this)

    private fun tabletFixture(initial: PlayerPresentation = PlayerPresentation.Expanded,
        transformSong: (Song) -> Song = { it }) =
        Fixture(initial, transformSong).apply {
            miniVisible.value = true
            adaptiveNavigation.value = true
            adaptiveDefaultLayout.value = true
            viewport.value = DpSize(1200.dp, 720.dp)
            shellLayout.value = MusicShellLayout(MusicNavigationMode.Rail)
        }

    private fun tallerPlayerAppearance() = ModernPlayerAppearance.Default.let { appearance ->
        appearance.copy(
            controls = appearance.controls.copy(size = ModernControlSize.LARGE),
            seekbar = appearance.seekbar.copy(waveformSize = ModernWaveformSize.TALL),
            layout = appearance.layout.copy(density = ModernLayoutDensity.RELAXED)
        )
    }

    private fun assertInside(host: Rect, child: Rect) {
        assertTrue("Expected $child inside $host", child.width > 0f && child.height > 0f &&
            child.left >= host.left - 1f && child.top >= host.top - 1f &&
            child.right <= host.right + 1f && child.bottom <= host.bottom + 1f)
    }

    private fun mount(initial: PlayerPresentation = PlayerPresentation.Collapsed) =
        Fixture(initial).also { fixture -> composeRule.setContent { fixture.Content() } }

    private fun mountExpandedDefault() = Fixture(PlayerPresentation.Expanded).apply {
        miniVisible.value = true
        observeDefaultMorphGeometry = true
    }.also { fixture ->
        composeRule.setContent { fixture.Content() }
        awaitExpandedSnapshot(fixture, requireLiveArtwork = true)
    }

    private fun text(id: Int) = composeRule.activity.getString(id)
    private fun mini(fixture: Fixture) = composeRule.onNodeWithContentDescription(
        composeRule.activity.getString(R.string.player_open_for, fixture.current.value!!.title)
    )

    private fun tags(node: SemanticsNode): List<String> =
        listOfNotNull(node.config.getOrNull(SemanticsProperties.TestTag)) + node.children.flatMap(::tags)

    private class LifetimeProbe {
        val theme = mutableStateOf(PlayerTheme.CLASSIC_WHEEL)
        val tick = mutableStateOf(0)
        lateinit var morph: PlayerMorphState
        lateinit var default: DefaultPlayerMorphBounds
        lateinit var menu: ClassicWheelMenuState
        var bounds: List<Any> = emptyList()

        @Composable
        fun Content() {
            morph = rememberPlayerMorphState()
            default = rememberMusicPlayerDefaultMorphBounds()
            val classic = rememberMusicPlayerClassicMorphBounds()
            menu = rememberMusicPlayerClassicWheelMenuState(theme.value, morph)
            val rack = rememberMusicPlayerRetroRackMorphBounds()
            val flip = rememberMusicPlayerPocketFlipMorphBounds()
            val cassette = rememberMusicPlayerPocketCassetteMorphBounds()
            val disc = rememberMusicPlayerPocketDiscMorphBounds()
            bounds = listOf(default, classic, rack, flip, cassette, disc)
            Text(tick.value.toString())
        }
    }

    private class Fixture(val initial: PlayerPresentation, transformSong: (Song) -> Song = { it }) {
        val songs = (1L..3L).map { id ->
            Song(id, "Track $id", "Artist $id", "Album $id", id.toInt(), 180_000L,
                Uri.parse("content://media/external/audio/media/$id"), "/music/$id.mp3", "/music",
                Uri.parse("content://fixture/artwork/$id")).let(transformSong)
        }
        val current = mutableStateOf<Song?>(songs[1])
        val previous = mutableStateOf<Song?>(songs[0])
        val next = mutableStateOf<Song?>(songs[2])
        val theme = mutableStateOf(PlayerTheme.DEFAULT)
        val nominatedTheme = mutableStateOf<PlayerTheme?>(null)
        val nominateAdvance = mutableStateOf(false)
        val deviceKind = mutableStateOf(PlayerDeviceKind.UNKNOWN)
        val miniVisible = mutableStateOf(false)
        val navigationVisible = mutableStateOf(true)
        val metadataVisible = mutableStateOf(false)
        val overlaysVisible = mutableStateOf(true)
        val timerActive = mutableStateOf(false)
        val lyricsVisible = mutableStateOf(false)
        val padding = mutableStateOf(37.dp)
        val hostStartInset = mutableStateOf(0.dp)
        val hostTopInset = mutableStateOf(0.dp)
        val hostEndInset = mutableStateOf(0.dp)
        val shellLayout = mutableStateOf(MusicShellLayout())
        val adaptiveNavigation = mutableStateOf(false)
        val adaptiveDefaultLayout = mutableStateOf(false)
        val viewport = mutableStateOf<DpSize?>(null)
        val fontScale = mutableStateOf(1f)
        val appearance = mutableStateOf(ModernPlayerAppearance.Default)
        val landscapeLayout = mutableStateOf(ModernLandscapeLayout.BALANCED)
        val playerInsets = mutableStateOf(DefaultPlayerInsets())
        // Opt-in observation of the existing bounds owner; never compose a second owner.
        var observeDefaultMorphGeometry = false
        var observeAdvanceMorphGeometry = false
        var resolvedIdentity: PlayerPresentationIdentity? = null
        var presentationEpoch = 0L
        var boundaryResetCount = 0
        var playerComposition: CompositionData? = null
        var resolvedPlayerLayout: DefaultPlayerLayout? = null
        var contentPaddingPx = 0f
        var receivedPadding: Dp? = null
        var observedShellAccent: Color? = null
        val progress = MutableStateFlow(PlaybackProgressUiState(duration = 180_000))
        val calls = mutableListOf<String>()
        val openedArtists = mutableListOf<Song>()
        val openedAlbums = mutableListOf<Song>()
        val artworks = mutableListOf<Song>()
        val trackInfo = mutableListOf<Song>()
        val favorites = mutableListOf<Song>()
        val seeks = mutableListOf<Int>()
        lateinit var morph: PlayerMorphState
        lateinit var lyrics: PlayerLyricsTransitionState
        lateinit var endpoints: PlayerEndpointBounds

        @Composable
        fun Content() {
            val inheritedDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides if (viewport.value != null) {
                // Fit a deterministic tablet-sized logical window on phone test devices.
                Density(0.25f, fontScale.value)
            } else inheritedDensity) {
                ContentInViewport()
            }
        }

        @Composable
        @OptIn(ExperimentalLayoutApi::class)
        private fun ContentInViewport() {
            contentPaddingPx = with(LocalDensity.current) { 16.dp.toPx() }
            morph = rememberPlayerMorphState(initial)
            lyrics = rememberPlayerLyricsTransitionState(lyricsVisible.value) { lyricsVisible.value = it }
            SazanamiTheme {
                BoxWithConstraints(
                    (viewport.value?.let { Modifier.requiredSize(it.width, it.height) }
                        ?: Modifier.fillMaxSize())
                        .padding(start = hostStartInset.value, top = hostTopInset.value,
                            end = hostEndInset.value)
                        .then(if (viewport.value != null) {
                            // Synthetic hosts have no system bars within their logical viewport.
                            Modifier.consumeWindowInsets(WindowInsets.systemBars)
                        } else Modifier)
                        .then(if (nominateAdvance.value && viewport.value != null) {
                            Modifier.consumeWindowInsets(WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout))
                        } else Modifier)
                ) {
                    val playerLayout = if (adaptiveDefaultLayout.value) {
                        resolveDefaultPlayerLayout(maxWidth, maxHeight,
                            insets = playerInsets.value,
                            fontScale = LocalDensity.current.fontScale, appearance = appearance.value,
                            preferredLandscapeLayout = landscapeLayout.value)
                    } else null
                    resolvedPlayerLayout = playerLayout
                    val requestedPresentation = resolvePlayerPresentation(
                        primaryTheme = theme.value,
                        primaryThemeTokens = theme.value.defaultTokens(),
                        context = TabletLandscapePlayerContext(deviceKind.value, 800, DpSize(maxWidth, maxHeight)),
                        landscapeCandidate = if (nominateAdvance.value) TabletLandscapePlayerCandidate(
                            ResolvedPlayerPresentation(PlayerPresentationIdentity.PocketAdvance, theme.value.defaultTokens()),
                            minimumPocketAdvanceSize(LocalDensity.current.fontScale)
                        ) else nominatedTheme.value?.let { nominated ->
                            TabletLandscapePlayerCandidate(
                                ResolvedPlayerPresentation(PlayerPresentationIdentity.Existing(nominated), nominated.defaultTokens()),
                                DpSize(840.dp, 480.dp)
                            )
                        }
                    )
                    val density = LocalDensity.current
                    val boundary = rememberPlayerPresentationBoundary(
                        requested = requestedPresentation,
                        viewport = PlayerPresentationViewport(constraints.maxWidth, constraints.maxHeight,
                            density.density, density.fontScale, 0, 0, 0, 0),
                        morphState = morph,
                        resetPresentationOverlays = {
                            lyrics.snapToExpanded()
                            boundaryResetCount++
                        }
                    )
                    val presentation = boundary.presentation
                    resolvedIdentity = presentation.identity
                    presentationEpoch = boundary.epoch
                    val player = prepareMusicPlayerPresentation(
                        songs = songs,
                        currentSong = current.value,
                        previousPreviewSong = previous.value,
                        nextPreviewSong = next.value,
                        isPlaying = false,
                        isShuffleEnabled = false,
                        repeatMode = RepeatMode.OFF,
                        playbackProgressUiState = progress,
                        lyricsPlaybackUiState = LyricsPlaybackUiState.Hidden,
                        onSongClick = { _, _ -> },
                        onPlayPauseClick = { calls += "play-pause" },
                        onPreviousClick = { calls += "previous" },
                        onNextClick = { calls += "next" },
                        onSeekChange = { seeks += it },
                        onSuspendLyricsAutoFollow = {},
                        onReturnLyricsToCurrentLine = {},
                        onRescanLyrics = {},
                        onShuffleClick = { calls += "shuffle" },
                        onRepeatClick = { calls += "repeat" },
                        queuedSongs = emptyList(),
                        upcomingSongs = emptyList(),
                        playbackQueueHubUiState = PlaybackQueueHubUiState(isLoading = false),
                        favoriteMembershipKeys = emptySet(),
                        onToggleFavoriteClick = { favorites += it },
                        isSleepTimerActive = timerActive.value,
                        sleepTimerDisplayText = "Timer fixture",
                        playerPresentation = presentation,
                        selectedModernArtworkTransitionStyle = ModernArtworkTransitionStyle.SLIDE,
                        selectedModernPlayerAppearance = appearance.value,
                        playerMorphState = morph,
                        lyricsTransitionState = lyrics,
                        isLyricsVisible = lyricsVisible.value,
                        isExpandedUpNextSheetVisible = false,
                        isQueueHubVisible = false,
                        isNowPlayingMorePresented = false,
                        isCreatePlaylistDialogVisible = false,
                        hasPendingPlaylistAdd = false,
                        hasPendingBulkPlaylistAdd = false,
                        isSleepTimerDialogVisible = false,
                        isArtworkViewerVisible = false,
                        shouldShowBottomMiniPlayer = miniVisible.value,
                        shouldShowMetadataEditor = metadataVisible.value,
                        shouldComposePlayerOverlays = overlaysVisible.value,
                        targetBottomContentPadding = padding.value,
                        bodyPresentation = { value ->
                            observedShellAccent = AppShellAccent
                            receivedPadding = value
                            Box(Modifier.fillMaxSize().testTag("body-pane")) {
                                Text("Body", Modifier.testTag("body"))
                            }
                        },
                        metadataEditorContent = { Text("Editor", Modifier.testTag("editor")) },
                        metadataDialogContent = { Text("Metadata dialog", Modifier.testTag("metadata-dialog")) },
                        transientOverlayContent = { Text("Transient", Modifier.testTag("transient")) },
                        bottomNavigationContent = {
                            if (adaptiveNavigation.value) {
                                AppNavigationChrome(
                                    shellLayout.value,
                                    navigationVisible.value && !morph.isExpandedOrTransitioning,
                                    MainDestination.HOME
                                ) { calls += "navigate-$it" }
                            } else if (navigationVisible.value) {
                                Text("Navigation", Modifier.align(Alignment.BottomCenter).testTag("navigation"))
                            }
                        },
                        onOpenMiniUpNext = { calls += "up-next" },
                        onShowQueueHub = { calls += "queue" },
                        onOpenSleepTimer = { calls += "sleep" },
                        onOpenLyricsSettings = { calls += "lyrics-settings" },
                        onShowExpandedMore = { calls += "more" },
                        onOpenCurrentAlbumClick = { openedAlbums += it },
                        onOpenCurrentArtistClick = { openedArtists += it },
                        onTrackInfoClick = { trackInfo += it },
                        onViewArtwork = { artworks += it },
                        shellLayout = shellLayout.value,
                        defaultPlayerLayout = playerLayout,
                        playerPresentationEpoch = boundary.epoch,
                        playerPresentationInputEnabled = boundary.inputEnabled,
                    )
                    PlayerMorphHost(
                        morph, Modifier.fillMaxSize().testTag("player-host"),
                        if (adaptiveDefaultLayout.value) Pair(shellLayout.value, playerLayout)
                        else shellLayout.value,
                        measurementIdentity = boundary.epoch
                    ) { measuredEndpoints ->
                        endpoints = measuredEndpoints
                        player(measuredEndpoints)
                        if (observeDefaultMorphGeometry || observeAdvanceMorphGeometry) {
                            playerComposition = currentComposer.compositionData
                        }
                    }
                }
            }
        }
    }
}
