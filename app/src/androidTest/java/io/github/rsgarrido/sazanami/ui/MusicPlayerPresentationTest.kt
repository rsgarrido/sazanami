package io.github.rsgarrido.sazanami.ui

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.systemBars
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
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
import io.github.rsgarrido.sazanami.ui.player.classicwheel.ClassicWheelMenuScreen
import io.github.rsgarrido.sazanami.ui.player.classicwheel.ClassicWheelMenuState
import io.github.rsgarrido.sazanami.ui.player.modern.DefaultPlayerMorphBounds
import io.github.rsgarrido.sazanami.ui.player.modern.DefaultPlayerMorphGeometry
import io.github.rsgarrido.sazanami.ui.player.modern.DefaultPlayerArrangement
import io.github.rsgarrido.sazanami.ui.player.modern.DefaultPlayerLayout
import io.github.rsgarrido.sazanami.ui.player.modern.ModernArtworkTransitionStyle
import io.github.rsgarrido.sazanami.ui.player.modern.ModernPlayerAppearance
import io.github.rsgarrido.sazanami.ui.player.modern.ModernControlSize
import io.github.rsgarrido.sazanami.ui.player.modern.ModernLayoutDensity
import io.github.rsgarrido.sazanami.ui.player.modern.ModernWaveformSize
import io.github.rsgarrido.sazanami.ui.player.modern.resolveDefaultPlayerLayout
import io.github.rsgarrido.sazanami.ui.player.rememberPlayerLyricsTransitionState
import io.github.rsgarrido.sazanami.ui.player.rememberPlayerMorphState
import io.github.rsgarrido.sazanami.ui.player.theme.defaultTokens
import io.github.rsgarrido.sazanami.ui.state.PlaybackProgressUiState
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
        val fixture = mount(PlayerPresentation.Expanded)
        composeRule.onNodeWithContentDescription(text(R.string.player_open_queues)).performClick()
        composeRule.onNodeWithContentDescription(text(R.string.player_more_actions)).performClick()
        composeRule.onNode(hasText(fixture.current.value!!.artist) and hasClickAction()).performClick()
        composeRule.onNode(hasText(fixture.current.value!!.album) and hasClickAction()).performClick()
        composeRule.onNodeWithTag(ArtworkViewerEntryTag).performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("queue", "more"), fixture.calls)
            assertSame(fixture.current.value, fixture.openedArtists.single())
            assertSame(fixture.current.value, fixture.openedAlbums.single())
            assertSame(fixture.current.value, fixture.artworks.single())
            fixture.current.value = fixture.songs.last()
        }
        composeRule.onNode(hasText(fixture.current.value!!.artist) and hasClickAction()).performClick()
        composeRule.onNodeWithTag(ArtworkViewerEntryTag).performClick()
        composeRule.runOnIdle {
            assertSame(fixture.current.value, fixture.openedArtists.last())
            assertSame(fixture.current.value, fixture.artworks.last())
        }
    }

    @Test
    fun defaultCarouselUsesThePreviousAndNextInputsAndTheirTransportCallbacks() {
        val fixture = mount(PlayerPresentation.Expanded)
        val artwork = composeRule.onNodeWithTag(ArtworkViewerEntryTag)
        artwork.performTouchInput { swipeLeft(durationMillis = 350) }
        composeRule.runOnIdle {
            assertEquals(listOf("next"), fixture.calls)
            fixture.current.value = fixture.next.value
            fixture.previous.value = fixture.songs[1]
            fixture.next.value = null
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
        val snapshot = assertBalancedExpandedGeometry(fixture)
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
        val snapshot = assertBalancedExpandedGeometry(fixture)
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

    private data class BalancedExpandedGeometry(
        val frame: PlayerMorphLayoutFrame,
        val arrangement: DefaultPlayerArrangement,
        val expanded: DefaultPlayerMorphGeometry
    ) {
        val description: String get() =
            "arrangement=$arrangement; generation=${frame.generation}; origin=${frame.originInRoot}; " +
                "expandedRoot=$expanded; gapPx=${expanded.text.left - expanded.artwork.right}"
    }

    private fun assertBalancedExpandedGeometry(fixture: Fixture): BalancedExpandedGeometry {
        var captured: BalancedExpandedGeometry? = null
        var readiness = "No geometry observed"
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
                    val expectedBoundary = Pair(fixture.shellLayout.value, layout)
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
                    if (!current || !settled || frame == null || layout == null || expanded == null) {
                        null
                    } else {
                        // The promoted geometry is host-local. Convert it exactly once so every
                        // comparison below, including normal semantics bounds, uses root pixels.
                        val origin = frame.originInRoot
                        BalancedExpandedGeometry(frame, layout.arrangement, expanded.copy(
                            surface = expanded.surface.translate(origin),
                            artwork = expanded.artwork.translate(origin),
                            text = expanded.text.translate(origin),
                            playPause = expanded.playPause.translate(origin)
                        ))
                    }
                }
                captured != null
            }
        } catch (timeout: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError("No settled current complete morph snapshot: $readiness", timeout)
        }
        val snapshot = requireNotNull(captured)
        val expanded = snapshot.expanded
        val host = composeRule.onNodeWithTag("player-host").fetchSemanticsNode().boundsInRoot
        assertEquals(snapshot.description, DefaultPlayerArrangement.Balanced, snapshot.arrangement)
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

    private fun tabletFixture(initial: PlayerPresentation = PlayerPresentation.Expanded) =
        Fixture(initial).apply {
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

    private class Fixture(val initial: PlayerPresentation) {
        val songs = (1L..3L).map { id ->
            Song(id, "Track $id", "Artist $id", "Album $id", id.toInt(), 180_000L,
                Uri.parse("content://media/external/audio/media/$id"), "/music/$id.mp3", "/music",
                Uri.parse("content://fixture/artwork/$id"))
        }
        val current = mutableStateOf<Song?>(songs[1])
        val previous = mutableStateOf<Song?>(songs[0])
        val next = mutableStateOf<Song?>(songs[2])
        val theme = mutableStateOf(PlayerTheme.DEFAULT)
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
        // Opt-in observation of the existing bounds owner; never compose a second owner.
        var observeDefaultMorphGeometry = false
        var playerComposition: CompositionData? = null
        var resolvedPlayerLayout: DefaultPlayerLayout? = null
        var contentPaddingPx = 0f
        var receivedPadding: Dp? = null
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
                ) {
                    val playerLayout = if (adaptiveDefaultLayout.value) {
                        resolveDefaultPlayerLayout(maxWidth, maxHeight,
                            fontScale = LocalDensity.current.fontScale, appearance = appearance.value)
                    } else null
                    resolvedPlayerLayout = playerLayout
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
                        selectedPlayerTheme = theme.value,
                        selectedPlayerThemeTokens = theme.value.defaultTokens(),
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
                    )
                    PlayerMorphHost(
                        morph, Modifier.fillMaxSize().testTag("player-host"),
                        if (adaptiveDefaultLayout.value) Pair(shellLayout.value, playerLayout)
                        else shellLayout.value
                    ) { measuredEndpoints ->
                        endpoints = measuredEndpoints
                        player(measuredEndpoints)
                        if (observeDefaultMorphGeometry) {
                            playerComposition = currentComposer.compositionData
                        }
                    }
                }
            }
        }
    }
}
