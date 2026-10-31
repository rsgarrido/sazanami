package io.github.rsgarrido.sazanami.ui

import io.github.rsgarrido.sazanami.ui.player.pockethorizon.*
import io.github.rsgarrido.sazanami.ui.player.pocketadvance.minimumPocketAdvanceSize
import android.net.Uri
import android.view.KeyEvent
import androidx.test.platform.app.InstrumentationRegistry
import io.github.rsgarrido.sazanami.ui.state.LocalDirectSeekPreview
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollTo
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
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.controller.PlaybackQueueHubUiState
import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.data.TabletLandscapeTheme
import io.github.rsgarrido.sazanami.data.PocketAdvanceAppearance
import io.github.rsgarrido.sazanami.data.PocketAdvanceConsoleFinish
import io.github.rsgarrido.sazanami.data.PocketAdvanceLcdPalette
import io.github.rsgarrido.sazanami.data.PocketAdvanceControlsFinish
import io.github.rsgarrido.sazanami.data.preferences.decodeAppPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.stableUiKey
import io.github.rsgarrido.sazanami.ui.library.LibraryAlbumGroup
import io.github.rsgarrido.sazanami.ui.library.sortedLibraryAlbumGroups
import io.github.rsgarrido.sazanami.ui.library.LibraryArtistGroup
import io.github.rsgarrido.sazanami.ui.library.LibrarySortDirection
import io.github.rsgarrido.sazanami.ui.library.LibrarySortOption
import io.github.rsgarrido.sazanami.ui.library.LibrarySortState
import io.github.rsgarrido.sazanami.ui.library.sortedLibraryArtistGroups
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
import io.github.rsgarrido.sazanami.ui.player.tabletLandscapePlayerCandidate
import io.github.rsgarrido.sazanami.ui.player.PlayerPresentationViewport
import io.github.rsgarrido.sazanami.ui.player.rememberPlayerPresentationBoundary
import io.github.rsgarrido.sazanami.ui.player.pocketadvance.PocketAdvanceBrowserPage
import io.github.rsgarrido.sazanami.ui.player.pocketadvance.PocketAdvanceNavigationState
import io.github.rsgarrido.sazanami.ui.player.pocketadvance.PocketAdvanceLcdMode
import io.github.rsgarrido.sazanami.ui.player.pocketadvance.PocketAdvanceRootEntry
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokenOverrides
import io.github.rsgarrido.sazanami.ui.player.theme.applyOverrides
import io.github.rsgarrido.sazanami.ui.player.pocketadvance.PocketAdvanceDefaultPalette
import io.github.rsgarrido.sazanami.ui.player.pocketadvance.PocketAdvancePalette
import io.github.rsgarrido.sazanami.ui.player.pocketadvance.LocalPocketAdvancePalette
import io.github.rsgarrido.sazanami.ui.player.pocketadvance.rememberPocketAdvanceNavigationState
import io.github.rsgarrido.sazanami.ui.player.pocketadvance.PocketAdvancePlayerMorphBounds
import io.github.rsgarrido.sazanami.ui.player.pocketadvance.PocketAdvanceMorphGeometry
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
import org.junit.Assert.assertNull
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
        collapseAdvanceThroughHeader()
        composeRule.onNodeWithTag("pocketAdvanceMini").assertIsDisplayed()
        composeRule.runOnIdle { assertTrue(controller.isCollapsedAndIdle) }
    }

    @Test
    fun pocketAdvanceBrowserRetainsAnchorsAndExclusivelyOwnsLcdAndConfirmInput() {
        val fixture = advanceFixture()
        composeRule.setContent { fixture.Content() }
        awaitAdvanceSnapshot(fixture)
        advanceControl("pocketAdvanceMini")
        val before = awaitAdvanceSnapshot(fixture)
        val navigation = fixture.advanceNavigation
        advanceControl("pocketAdvanceStart")
        advanceControl("pocketAdvanceDown")
        advanceControl("pocketAdvanceDown")
        advanceControl("pocketAdvanceDown")
        composeRule.onNodeWithTag("pocketAdvanceRootAlbums").assertIsSelected().assertIsEnabled()
        val browsing = awaitAdvanceSnapshot(fixture)
        assertSame(before.owner, browsing.owner)
        assertEquals(before.expanded, browsing.expanded)
        assertEquals(before.mini, browsing.mini)
        composeRule.onNodeWithTag("pocketAdvanceSeek").assertDoesNotExist()
        composeRule.onNodeWithTag("pocketAdvanceArtist").assertDoesNotExist()
        composeRule.onNodeWithTag("pocketAdvanceAlbum").assertDoesNotExist()
        composeRule.onAllNodesWithContentDescription(text(R.string.player_play)).assertCountEquals(0)
        composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)).assertCountEquals(0)
        // A real tap at the shared A owner's rectangle opens the selected catalogue,
        // never the invisible play/pause target above it.
        val primary = before.expanded.playPause.translate(before.frame.originInRoot)
        composeRule.onRoot().performTouchInput { click(primary.center) }
        composeRule.onNodeWithTag("pocketAdvanceAlbumCatalogue", useUnmergedTree = true).assertExists()
        composeRule.runOnIdle { assertTrue(fixture.songSelections.isEmpty()) }
        advanceControl("pocketAdvanceBack")
        composeRule.onNodeWithTag("pocketAdvanceBrowser", useUnmergedTree = true).performTouchInput {
            down(center); moveBy(Offset(0f, -48f)); up()
        }
        composeRule.runOnIdle {
            assertSame(navigation, fixture.advanceNavigation)
            assertEquals(PocketAdvanceLcdMode.MusicBrowser, navigation.lcdMode)
            assertEquals(1f, fixture.morph.progress, 0f)
            assertFalse(fixture.morph.isDragging)
            assertFalse(fixture.lyrics.lyricsComposed)
            assertTrue(fixture.calls.isEmpty()); assertTrue(fixture.seeks.isEmpty())
        }
        // Taps at old metadata/seek coordinates must not invoke the hidden destinations.
        listOf(before.expanded.artist, before.expanded.progressTrack).forEach { rect ->
            composeRule.onRoot().performTouchInput { click(rect.translate(before.frame.originInRoot).center) }
            if (composeRule.runOnIdle { navigation.lcdMode == PocketAdvanceLcdMode.NowPlaying }) {
                advanceControl("pocketAdvanceStart")
            }
        }
        composeRule.runOnIdle {
            assertTrue(fixture.openedArtists.isEmpty()); assertTrue(fixture.openedAlbums.isEmpty())
            assertTrue(fixture.calls.isEmpty()); assertTrue(fixture.seeks.isEmpty())
        }
        // Touch activation uses the actual row rather than the previous hardware selection.
        composeRule.onNodeWithTag("pocketAdvanceRootNowPlaying").performScrollTo().performTouchInput { click() }
        composeRule.runOnIdle { assertEquals(PocketAdvanceLcdMode.NowPlaying, navigation.lcdMode) }
        advanceControl("pocketAdvanceStart")
        advanceControl("pocketAdvancePlay")
        composeRule.runOnIdle {
            assertEquals(PocketAdvanceLcdMode.NowPlaying, navigation.lcdMode)
            assertTrue(fixture.calls.isEmpty()); assertTrue(fixture.seeks.isEmpty())
        }
        advanceControl("pocketAdvanceMorphPlay")
        composeRule.runOnIdle { assertEquals(listOf("play-pause"), fixture.calls) }
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun pocketAdvanceBrowserPaintsSelectionWithoutTheSharedNowPlayingLayer() {
        val fixture = advanceFixture()
        composeRule.setContent { fixture.Content() }
        awaitAdvanceSnapshot(fixture)
        advanceControl("pocketAdvanceMini")
        advanceControl("pocketAdvanceStart")
        advanceControl("pocketAdvanceDown")
        advanceControl("pocketAdvanceDown")
        val row = composeRule.onNodeWithTag("pocketAdvanceRootArtists")
        row.assertIsSelected()
        val pixels = row.captureToImage().toPixelMap()
        // The row edge is outside its text and rounded corners. Shared artwork must not cover it.
        val pixel = pixels[1, pixels.height / 2]
        assertEquals(PocketAdvanceDefaultPalette.Ink.red, pixel.red, 0.04f)
        assertEquals(PocketAdvanceDefaultPalette.Ink.green, pixel.green, 0.04f)
        assertEquals(PocketAdvanceDefaultPalette.Ink.blue, pixel.blue, 0.04f)
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun pocketAdvanceAppearanceRecolorsMiniHardwareAndNowPlayingWithoutReplacingGeometry() {
        val fixture = advanceFixture()
        composeRule.setContent { fixture.Content() }
        val mini = awaitAdvanceSnapshot(fixture)
        val controller = fixture.morph
        val originalMini = mini.mini.shell.translate(mini.frame.originInRoot)
        assertAdvancePalettePixel("pocketAdvanceMini", PocketAdvanceDefaultPalette.Shell,
            Offset(originalMini.center.x, originalMini.top + originalMini.height * .06f))
        val custom = PocketAdvanceAppearance(PocketAdvanceConsoleFinish.OCEAN_BLUE,
            PocketAdvanceLcdPalette.WARM_AMBER, PocketAdvanceControlsFinish.LIGHT_GRAY)
        val colors = PocketAdvancePalette.from(custom)
        composeRule.runOnIdle { fixture.advanceAppearance.value = custom }
        val recoloredMini = awaitAdvanceSnapshot(fixture)
        assertSame(mini.owner, recoloredMini.owner)
        assertEquals(mini.mini, recoloredMini.mini); assertEquals(mini.expanded, recoloredMini.expanded)
        assertAdvancePalettePixel("pocketAdvanceMini", colors.Shell,
            Offset(originalMini.center.x, originalMini.top + originalMini.height * .06f))
        advanceControl("pocketAdvanceMini")
        val expanded = awaitAdvanceSnapshot(fixture)
        composeRule.runOnIdle {
            assertEquals(PlayerPresentation.Expanded, controller.targetPresentation)
            assertEquals(1f, controller.progress, 0f)
            assertFalse(controller.isAnimating); assertFalse(controller.isDragging)
        }
        assertAdvancePaletteFraction("pocketAdvanceDpad", colors.Button, .5f, .5f)
        val buttonMiddle = androidx.compose.ui.graphics.lerp(colors.ButtonHighlight, colors.Button, .5f)
        assertAdvancePaletteFraction("pocketAdvanceMorphPlay", buttonMiddle, .28f, .5f)
        assertAdvancePaletteFraction("pocketAdvanceBack", buttonMiddle, .28f, .5f)
        assertAdvancePaletteFraction("pocketAdvanceStartFace", buttonMiddle, .15f, .5f)
        assertAdvancePaletteFraction("pocketAdvanceDisplay", colors.Screen, .94f, .5f)
        assertAdvanceTextColor("pocketAdvanceMorphTitle", colors.Ink)
        val shellTop = androidx.compose.ui.graphics.lerp(colors.Shell, colors.ShellHighlight, .48f)
        assertAdvancePaletteFraction("pocketAdvanceMorphShell",
            androidx.compose.ui.graphics.lerp(shellTop, colors.Shell, .12f), .5f, .06f)
        val epoch = fixture.presentationEpoch
        composeRule.runOnIdle { fixture.advanceAppearance.value = PocketAdvanceAppearance.Default }
        val reset = awaitAdvanceSnapshot(fixture)
        assertSame(expanded.owner, reset.owner)
        assertEquals(expanded.expanded, reset.expanded); assertEquals(expanded.mini, reset.mini)
        assertAdvancePaletteFraction("pocketAdvanceDpad", PocketAdvanceDefaultPalette.Button, .5f, .5f)
        assertAdvancePaletteFraction("pocketAdvanceDisplay", PocketAdvanceDefaultPalette.Screen, .94f, .5f)
        assertAdvanceTextColor("pocketAdvanceMorphTitle", PocketAdvanceDefaultPalette.Ink)
        composeRule.runOnIdle {
            assertSame(controller, fixture.morph); assertEquals(epoch, fixture.presentationEpoch)
            assertEquals(PlayerPresentation.Expanded, controller.targetPresentation)
            assertEquals(1f, controller.progress, 0f)
            assertTrue(fixture.calls.isEmpty()); assertTrue(fixture.seeks.isEmpty()); assertTrue(fixture.songSelections.isEmpty())
        }
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun pocketAdvanceAppearanceRecolorsSongsArtistsAndAlbumRecordsWhileRetainingTheirLocations() {
        val fixture = advanceFixture()
        val supplied = advanceAlbumLibrary(4, 25)
        fixture.librarySongs.value = supplied
        composeRule.setContent { fixture.Content() }
        awaitAdvanceSnapshot(fixture)
        advanceControl("pocketAdvanceMini")
        val original = awaitAdvanceSnapshot(fixture)
        val controller = fixture.morph
        val epoch = fixture.presentationEpoch
        openAdvanceSongs()
        repeat(10) { advanceControl("pocketAdvanceDown") }
        val song = supplied[10]
        val songsViewport = composeRule.runOnIdle {
            fixture.advanceNavigation.songsListState.firstVisibleItemIndex to fixture.advanceNavigation.songsListState.firstVisibleItemScrollOffset
        }
        composeRule.runOnIdle { fixture.advanceAppearance.value = PocketAdvanceAppearance(lcdPalette = PocketAdvanceLcdPalette.WARM_AMBER) }
        assertAdvanceSelectedRowColor(advanceSongTag(song), PocketAdvancePalette.from(fixture.advanceAppearance.value).Ink)
        composeRule.runOnIdle {
            assertEquals(song.stableUiKey(), fixture.advanceNavigation.selectedSongKey)
            assertEquals(songsViewport, fixture.advanceNavigation.songsListState.firstVisibleItemIndex to fixture.advanceNavigation.songsListState.firstVisibleItemScrollOffset)
        }
        advanceControl("pocketAdvanceBack")
        composeRule.onNodeWithTag("pocketAdvanceRootArtists").performScrollTo().performClick()
        advanceControl("pocketAdvanceDown")
        val artist = advanceArtistGroups(supplied)[1]
        composeRule.runOnIdle { fixture.advanceAppearance.value = fixture.advanceAppearance.value.copy(lcdPalette = PocketAdvanceLcdPalette.CLASSIC_GREEN) }
        val green = PocketAdvancePalette.from(fixture.advanceAppearance.value)
        assertAdvanceSelectedRowColor(advanceArtistTag(artist), green.Ink)
        advanceControl("pocketAdvanceNext")
        advanceControl("pocketAdvanceDown")
        val artistSong = artist.songs[1]
        composeRule.runOnIdle { fixture.advanceAppearance.value = fixture.advanceAppearance.value.copy(consoleFinish = PocketAdvanceConsoleFinish.GRAPHITE) }
        assertAdvanceSelectedRowColor(advanceArtistSongTag(artistSong), green.Ink)
        advanceControl("pocketAdvanceBack"); advanceControl("pocketAdvanceBack")
        composeRule.onNodeWithTag("pocketAdvanceRootAlbums").performScrollTo().performClick()
        advanceControl("pocketAdvanceDown")
        val album = advanceAlbumGroups(supplied)[1]
        composeRule.runOnIdle { fixture.advanceAppearance.value = fixture.advanceAppearance.value.copy(lcdPalette = PocketAdvanceLcdPalette.WARM_AMBER) }
        assertAdvanceSelectedRowColor(albumRowTag(album), PocketAdvancePalette.from(fixture.advanceAppearance.value).Ink)
        composeRule.onNodeWithTag(albumArtTag(album), useUnmergedTree = true).assertIsDisplayed()
        advanceControl("pocketAdvanceNext")
        repeat(10) { advanceControl("pocketAdvanceDown") }
        val track = album.songs[10]
        val viewport = composeRule.runOnIdle {
            fixture.advanceNavigation.albumTracksListState.firstVisibleItemIndex to fixture.advanceNavigation.albumTracksListState.firstVisibleItemScrollOffset
        }
        composeRule.runOnIdle { fixture.advanceAppearance.value = PocketAdvanceAppearance(PocketAdvanceConsoleFinish.BERRY,
            PocketAdvanceLcdPalette.CLASSIC_GREEN, PocketAdvanceControlsFinish.LIGHT_GRAY) }
        assertAdvanceSelectedRowColor(albumTrackTag(track), PocketAdvancePalette.from(fixture.advanceAppearance.value).Ink)
        val recolored = awaitAdvanceSnapshot(fixture)
        assertSame(original.owner, recolored.owner); assertEquals(original.expanded, recolored.expanded); assertEquals(original.mini, recolored.mini)
        advanceControl("pocketAdvanceStart"); advanceControl("pocketAdvanceStart")
        composeRule.runOnIdle {
            assertSame(controller, fixture.morph); assertEquals(epoch, fixture.presentationEpoch)
            assertEquals(PocketAdvanceBrowserPage.AlbumRecord(album.key), fixture.advanceNavigation.browserPage)
            assertEquals(track.stableUiKey(), fixture.advanceNavigation.selectedAlbumTrackKey)
            assertEquals(artistSong.stableUiKey(), fixture.advanceNavigation.selectedArtistSongKey)
            assertEquals(song.stableUiKey(), fixture.advanceNavigation.selectedSongKey)
            assertEquals(viewport, fixture.advanceNavigation.albumTracksListState.firstVisibleItemIndex to fixture.advanceNavigation.albumTracksListState.firstVisibleItemScrollOffset)
            assertTrue(fixture.calls.isEmpty()); assertTrue(fixture.seeks.isEmpty()); assertTrue(fixture.songSelections.isEmpty())
        }
    }

    @Test
    fun pocketAdvanceAppearanceUpdatesDoNotCancelHeldInputOrAnActivePlayerDrag() {
        val fixture = advanceFixture()
        val supplied = advanceLibrarySongs(30)
        fixture.librarySongs.value = supplied
        composeRule.setContent { fixture.Content() }
        awaitAdvanceSnapshot(fixture)
        advanceControl("pocketAdvanceMini")
        openAdvanceSongs()
        val controller = fixture.morph
        val epoch = fixture.presentationEpoch
        composeRule.mainClock.autoAdvance = false
        try {
            beginAdvanceHold("pocketAdvanceDown")
            composeRule.mainClock.advanceTimeBy(200)
            composeRule.runOnIdle { fixture.advanceAppearance.value = PocketAdvanceAppearance(PocketAdvanceConsoleFinish.FOREST_GREEN,
                PocketAdvanceLcdPalette.CLASSIC_GREEN, PocketAdvanceControlsFinish.DEEP_NAVY) }
            composeRule.mainClock.advanceTimeBy(200)
            assertAdvanceSongIndex(fixture, supplied, 2)
            composeRule.mainClock.advanceTimeBy(110)
            assertAdvanceSongIndex(fixture, supplied, 3)
            composeRule.onRoot().performTouchInput { up() }
            composeRule.mainClock.advanceTimeBy(600)
            assertAdvanceSongIndex(fixture, supplied, 3)
            composeRule.runOnIdle { controller.beginDragWithRange(500f); controller.dragBy(100f) }
            composeRule.mainClock.advanceTimeByFrame()
            val progress = composeRule.runOnIdle { assertTrue(controller.isDragging); controller.progress }
            composeRule.runOnIdle { fixture.advanceAppearance.value = PocketAdvanceAppearance.Default }
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.runOnIdle {
                assertSame(controller, fixture.morph); assertTrue(controller.isDragging)
                assertEquals(progress, controller.progress, 0f); assertEquals(epoch, fixture.presentationEpoch)
                assertEquals(supplied[3].stableUiKey(), fixture.advanceNavigation.selectedSongKey)
                assertTrue(fixture.calls.isEmpty()); assertTrue(fixture.seeks.isEmpty()); assertTrue(fixture.songSelections.isEmpty())
                controller.cancelDrag()
            }
            composeRule.mainClock.advanceTimeBy(600)
        } finally { composeRule.mainClock.autoAdvance = true }
    }

    @Test
    fun pocketAdvanceAppearanceDoesNotRestartAlbumOpeningOrLeakAcrossPortraitFallback() {
        val fixture = advanceFixture()
        val supplied = advanceAlbumLibrary(3, 20)
        fixture.librarySongs.value = supplied
        val custom = PocketAdvanceAppearance(PocketAdvanceConsoleFinish.OCEAN_BLUE,
            PocketAdvanceLcdPalette.CLASSIC_GREEN, PocketAdvanceControlsFinish.DEEP_NAVY)
        fixture.advanceAppearance.value = custom
        composeRule.setContent { fixture.Content() }
        awaitAdvanceSnapshot(fixture)
        advanceControl("pocketAdvanceMini")
        val controller = fixture.morph
        composeRule.runOnIdle { fixture.primaryTokenOverrides.value = PlayerThemeTokenOverrides(
            shellColor = Color.Red, displayBackgroundColor = Color.Blue, displayTextColor = Color.Yellow) }
        assertAdvancePaletteFraction("pocketAdvanceDisplay", PocketAdvancePalette.from(custom).Screen, .94f, .5f)
        openAdvanceAlbums()
        val album = advanceAlbumGroups(supplied).first()
        val tag = albumArtTag(album)
        val catalogue = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val epoch = fixture.presentationEpoch
        composeRule.mainClock.autoAdvance = false
        try {
            advanceControl("pocketAdvanceNext")
            composeRule.mainClock.advanceTimeByFrame(); composeRule.mainClock.advanceTimeBy(80)
            val middle = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            composeRule.runOnIdle { fixture.advanceAppearance.value = custom.copy(lcdPalette = PocketAdvanceLcdPalette.WARM_AMBER) }
            composeRule.mainClock.advanceTimeByFrame(); composeRule.mainClock.advanceTimeBy(80)
            val later = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue(catalogue.width > middle.width && middle.width > later.width)
            composeRule.mainClock.advanceTimeBy(160)
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).assertCountEquals(1)
            composeRule.runOnIdle {
                assertSame(controller, fixture.morph); assertEquals(epoch, fixture.presentationEpoch)
                assertEquals(PocketAdvanceBrowserPage.AlbumRecord(album.key), fixture.advanceNavigation.browserPage)
                fixture.viewport.value = DpSize(720.dp, 1180.dp)
            }
            composeRule.mainClock.advanceTimeBy(600)
            composeRule.runOnIdle {
                assertEquals(PlayerPresentationIdentity.Existing(PlayerTheme.CLASSIC_WHEEL), fixture.resolvedIdentity)
                assertEquals(PlayerTheme.CLASSIC_WHEEL, fixture.theme.value)
                assertEquals(Color.Red, fixture.primaryTokenOverrides.value.shellColor)
                assertEquals(Color.Blue, fixture.primaryTokenOverrides.value.displayBackgroundColor)
                assertEquals(custom.copy(lcdPalette = PocketAdvanceLcdPalette.WARM_AMBER), fixture.advanceAppearance.value)
                fixture.theme.value = PlayerTheme.POCKET_CASSETTE
                fixture.viewport.value = DpSize(1200.dp, 720.dp)
            }
            composeRule.mainClock.advanceTimeBy(600)
            composeRule.runOnIdle {
                assertEquals(PlayerPresentationIdentity.PocketAdvance, fixture.resolvedIdentity)
                assertEquals(PocketAdvanceConsoleFinish.OCEAN_BLUE, fixture.advanceAppearance.value.consoleFinish)
                assertSame(controller, fixture.morph); assertEquals(1f, controller.progress, 0f)
                assertTrue(fixture.calls.isEmpty()); assertTrue(fixture.seeks.isEmpty()); assertTrue(fixture.songSelections.isEmpty())
            }
        } finally { composeRule.mainClock.autoAdvance = true }
        advanceControl("pocketAdvanceStart")
        composeRule.onNodeWithTag(albumTrackTag(album.songs.first()), useUnmergedTree = true).assertIsSelected()
        assertAdvanceSelectedRowColor(albumTrackTag(album.songs.first()), PocketAdvancePalette.from(fixture.advanceAppearance.value).Ink)
    }

    @Test
    fun pocketAdvanceBrowserHeaderCollapseKeepsMorphGeometryAndRestoresSelectionOnStart() {
        val fixture = advanceFixture()
        composeRule.setContent { fixture.Content() }
        awaitAdvanceSnapshot(fixture)
        advanceControl("pocketAdvanceMini")
        val expanded = awaitAdvanceSnapshot(fixture)
        val controller = fixture.morph
        advanceControl("pocketAdvanceStart")
        advanceControl("pocketAdvanceUp")
        val header = composeRule.onNodeWithTag("pocketAdvanceHeader", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        composeRule.onRoot().performTouchInput { down(header.center); moveBy(Offset(0f, 48f)) }
        composeRule.onNodeWithTag("pocketAdvanceBrowser", useUnmergedTree = true).assertDoesNotExist()
        val progress = composeRule.runOnIdle {
            assertTrue(controller.isDragging)
            controller.progress
        }
        val moving = composeRule.onNodeWithTag("pocketAdvanceMorphProgress", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertRectNear(io.github.rsgarrido.sazanami.ui.player.pocketadvance.interpolatePocketAdvanceRect(
            expanded.mini.progressTrack, expanded.expanded.progressTrack, progress)
            .translate(expanded.frame.originInRoot), moving)
        composeRule.onRoot().performTouchInput { moveBy(Offset(0f, 72f)); up() }
        composeRule.runOnIdle {
            assertTrue(controller.isCollapsedAndIdle)
            assertEquals(PocketAdvanceLcdMode.NowPlaying, fixture.advanceNavigation.lcdMode)
            assertEquals(PocketAdvanceRootEntry.Albums, fixture.advanceNavigation.selectedRootEntry)
        }
        advanceControl("pocketAdvanceMini")
        composeRule.onNodeWithTag("pocketAdvanceBrowser", useUnmergedTree = true).assertDoesNotExist()
        val reopened = awaitAdvanceSnapshot(fixture)
        assertSame(expanded.owner, reopened.owner)
        assertEquals(expanded.expanded, reopened.expanded)
        advanceControl("pocketAdvanceStart")
        composeRule.onNodeWithTag("pocketAdvanceRootAlbums").assertIsSelected()
        composeRule.runOnIdle {
            assertSame(controller, fixture.morph)
            assertTrue(fixture.calls.isEmpty()); assertTrue(fixture.seeks.isEmpty())
        }
    }

    @Test
    fun pocketAdvanceBrowserReturnsOnlyAfterCancelledOrReversedCollapseSettles() {
        val fixture = advanceFixture()
        composeRule.setContent { fixture.Content() }
        awaitAdvanceSnapshot(fixture)
        advanceControl("pocketAdvanceMini")
        advanceControl("pocketAdvanceStart")
        advanceControl("pocketAdvanceUp")
        for (cancelGesture in listOf(true, false)) {
            val header = composeRule.onNodeWithTag("pocketAdvanceHeader", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            composeRule.onRoot().performTouchInput { down(header.center); moveBy(Offset(0f, 48f)) }
            composeRule.onNodeWithTag("pocketAdvanceBrowser", useUnmergedTree = true).assertDoesNotExist()
            composeRule.runOnIdle { assertTrue(fixture.morph.isDragging) }
            composeRule.onRoot().performTouchInput {
                if (cancelGesture) cancel() else { moveTo(header.center - Offset(0f, 24f)); up() }
            }
            composeRule.onNodeWithTag("pocketAdvanceRootAlbums").assertIsDisplayed().assertIsSelected()
            composeRule.runOnIdle {
                assertEquals(1f, fixture.morph.progress, 0f)
                assertFalse(fixture.morph.isDragging)
                assertFalse(fixture.morph.isAnimating)
                assertEquals(PocketAdvanceLcdMode.MusicBrowser, fixture.advanceNavigation.lcdMode)
                assertTrue(fixture.calls.isEmpty()); assertTrue(fixture.seeks.isEmpty())
            }
        }
    }

    @Test
    fun pocketAdvanceBrowserLocationSurvivesTrackChangeFallbackAndSavedStateRecreation() {
        val fixture = advanceFixture()
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { fixture.Content() }
        awaitAdvanceSnapshot(fixture)
        advanceControl("pocketAdvanceMini")
        advanceControl("pocketAdvanceStart")
        advanceControl("pocketAdvanceDown")
        advanceControl("pocketAdvanceDown")
        val navigation = fixture.advanceNavigation
        composeRule.runOnIdle { fixture.current.value = fixture.songs.first() }
        composeRule.runOnIdle {
            assertSame(navigation, fixture.advanceNavigation)
            assertEquals(PocketAdvanceLcdMode.MusicBrowser, navigation.lcdMode)
            fixture.viewport.value = DpSize(720.dp, 1180.dp)
        }
        composeRule.runOnIdle {
            assertEquals(PlayerPresentationIdentity.Existing(PlayerTheme.CLASSIC_WHEEL), fixture.resolvedIdentity)
            assertEquals(PocketAdvanceLcdMode.NowPlaying, navigation.lcdMode)
            assertEquals(PocketAdvanceRootEntry.Artists, navigation.selectedRootEntry)
            fixture.viewport.value = DpSize(1200.dp, 720.dp)
        }
        awaitAdvanceSnapshot(fixture)
        composeRule.onNodeWithTag("pocketAdvanceBrowser", useUnmergedTree = true).assertDoesNotExist()
        advanceControl("pocketAdvanceStart")
        composeRule.onNodeWithTag("pocketAdvanceRootArtists").assertIsSelected()
        restoration.emulateSavedInstanceStateRestore()
        awaitAdvanceSnapshot(fixture)
        composeRule.onNodeWithTag("pocketAdvanceBrowser", useUnmergedTree = true).assertDoesNotExist()
        composeRule.runOnIdle {
            assertNotSame(navigation, fixture.advanceNavigation)
            assertEquals(PocketAdvanceLcdMode.NowPlaying, fixture.advanceNavigation.lcdMode)
            assertEquals(PocketAdvanceRootEntry.Artists, fixture.advanceNavigation.selectedRootEntry)
            assertEquals(PlayerTheme.CLASSIC_WHEEL, fixture.theme.value)
            assertTrue(fixture.calls.isEmpty()); assertTrue(fixture.seeks.isEmpty())
        }
        advanceControl("pocketAdvanceStart")
        composeRule.onNodeWithTag("pocketAdvanceRootArtists").assertIsSelected()
    }

    @Test
    fun pocketAdvanceAlbumCataloguePreviewsSelectionAndFollowsTheMainAlbumSort() {
        val fixture = advanceFixture()
        val supplied = advanceAlbumLibrary(4, 4).filter { it.album != "Record 01" || it.trackNumber == 1 }.reversed()
        val groups = advanceAlbumGroups(supplied)
        fixture.librarySongs.value = supplied
        composeRule.setContent { fixture.Content() }
        awaitAdvanceSnapshot(fixture)
        advanceControl("pocketAdvanceMini")
        openAdvanceAlbums()
        composeRule.onNodeWithTag(albumRowTag(groups.first()), useUnmergedTree = true).assertIsSelected()
        composeRule.onNodeWithTag(albumArtTag(groups.first()), useUnmergedTree = true).assertIsDisplayed()
        advanceControl("pocketAdvanceUp")
        composeRule.onNodeWithTag(albumRowTag(groups.last()), useUnmergedTree = true).assertIsSelected()
        composeRule.onNodeWithTag(albumArtTag(groups.last()), useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(albumArtTag(groups.first()), useUnmergedTree = true).assertDoesNotExist()
        advanceControl("pocketAdvanceDown")
        advanceControl("pocketAdvanceDown")
        val selected = groups[1]
        composeRule.runOnIdle {
            assertEquals(selected.key, fixture.advanceNavigation.selectedAlbumKey)
            fixture.albumSortState.value = LibrarySortState(LibrarySortOption.SONG_COUNT, LibrarySortDirection.DESCENDING)
        }
        val sorted = advanceAlbumGroups(supplied, fixture.albumSortState.value)
        composeRule.onNodeWithTag(albumRowTag(selected), useUnmergedTree = true).assertIsSelected()
        composeRule.onNodeWithTag(albumArtTag(selected), useUnmergedTree = true).assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(0, fixture.advanceNavigation.selectedAlbumIndex(sorted.map { it.key }))
            assertTrue(fixture.songSelections.isEmpty()); assertTrue(fixture.calls.isEmpty()); assertTrue(fixture.seeks.isEmpty())
        }
        val position = composeRule.onNodeWithTag("pocketAdvanceAlbumIndex", useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsProperties.Text].single().text
        assertEquals(composeRule.activity.getString(R.string.pocket_advance_archive_position, 1, groups.size), position)
        advanceControl("pocketAdvancePrevious")
        composeRule.onNodeWithTag("pocketAdvanceRootAlbums").assertIsSelected()
    }

    @Test
    fun pocketAdvanceAlbumRecordPlaysTheCompleteMultiDiscContextExactlyOnce() {
        val fixture = advanceFixture()
        val supplied = advanceAlbumLibrary(2, 6, multiDisc = true).reversed()
        val group = advanceAlbumGroups(supplied).first()
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 6L), group.songs.map { it.id })
        fixture.librarySongs.value = supplied
        composeRule.setContent { fixture.Content() }
        awaitAdvanceSnapshot(fixture)
        advanceControl("pocketAdvanceMini")
        openAdvanceAlbums()
        advanceControl("pocketAdvancePlay")
        composeRule.onNodeWithTag("pocketAdvanceAlbumRecord", useUnmergedTree = true).assertExists()
        composeRule.runOnIdle {
            assertEquals(PocketAdvanceBrowserPage.AlbumRecord(group.key), fixture.advanceNavigation.browserPage)
            assertEquals(group.songs.size, fixture.advanceNavigation.albumTracksListState.layoutInfo.totalItemsCount)
            assertTrue(fixture.songSelections.isEmpty())
        }
        repeat(3) { advanceControl("pocketAdvanceDown") }
        val selected = group.songs[3]
        composeRule.onNodeWithTag(albumTrackTag(selected), useUnmergedTree = true).assertIsSelected()
        val row = composeRule.onNodeWithTag(albumTrackTag(selected)).fetchSemanticsNode()
        assertTrue(row.config[SemanticsProperties.Text].any { it.text.contains(composeRule.activity.getString(R.string.library_album_disc, 2)) })
        val confirm = composeRule.onNodeWithTag("pocketAdvancePlay").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        composeRule.runOnIdle { assertTrue(confirm()); assertTrue(confirm()) }
        composeRule.runOnIdle {
            assertEquals(listOf(selected to group.songs), fixture.songSelections)
            assertEquals(PocketAdvanceLcdMode.NowPlaying, fixture.advanceNavigation.lcdMode)
            assertEquals(PocketAdvanceBrowserPage.AlbumRecord(group.key), fixture.advanceNavigation.browserPage)
        }
        advanceControl("pocketAdvanceStart")
        composeRule.onNodeWithTag(albumTrackTag(selected), useUnmergedTree = true).assertIsSelected()
        advanceControl("pocketAdvanceNext")
        composeRule.runOnIdle { assertEquals(listOf(selected to group.songs, selected to group.songs), fixture.songSelections) }
        advanceControl("pocketAdvanceStart")
        advanceControl("pocketAdvanceBack")
        composeRule.onNodeWithTag(albumRowTag(group), useUnmergedTree = true).assertIsSelected()
        advanceControl("pocketAdvanceNext")
        composeRule.onNodeWithTag(albumTrackTag(selected), useUnmergedTree = true).assertIsSelected()
        composeRule.runOnIdle { assertTrue(fixture.calls.isEmpty()); assertTrue(fixture.seeks.isEmpty()) }
    }

    @Test
    fun pocketAdvanceAlbumRootRowsAndPreviewUseDirectTouchIdentity() {
        val fixture = advanceFixture()
        val supplied = advanceAlbumLibrary(30, 20)
        val groups = advanceAlbumGroups(supplied)
        fixture.librarySongs.value = supplied
        composeRule.setContent { fixture.Content() }
        awaitAdvanceSnapshot(fixture)
        advanceControl("pocketAdvanceMini")
        advanceControl("pocketAdvanceStart")
        composeRule.onNodeWithTag("pocketAdvanceRootAlbums").performScrollTo().performTouchInput { click() }
        composeRule.onNodeWithTag("pocketAdvanceAlbumCatalogue", useUnmergedTree = true).performScrollToIndex(20)
        val group = groups[21]
        composeRule.onNodeWithTag(albumRowTag(group), useUnmergedTree = true).performTouchInput { click() }
        composeRule.runOnIdle {
            assertEquals(PocketAdvanceBrowserPage.AlbumRecord(group.key), fixture.advanceNavigation.browserPage)
            assertEquals(group.key, fixture.advanceNavigation.selectedAlbumKey)
            assertTrue(fixture.songSelections.isEmpty())
        }
        composeRule.onNodeWithTag("pocketAdvanceAlbumRecord", useUnmergedTree = true).performScrollToIndex(10)
        composeRule.onNodeWithTag(albumTrackTag(group.songs[11]), useUnmergedTree = true).performTouchInput { click() }
        composeRule.runOnIdle { assertEquals(listOf(group.songs[11] to group.songs), fixture.songSelections) }
        advanceControl("pocketAdvanceStart")
        advanceControl("pocketAdvanceBack")
        composeRule.onNodeWithTag(albumArtTag(group), useUnmergedTree = true).performTouchInput { click() }
        composeRule.runOnIdle {
            assertEquals(PocketAdvanceBrowserPage.AlbumRecord(group.key), fixture.advanceNavigation.browserPage)
            assertEquals(1, fixture.songSelections.size)
            assertEquals(group.songs[11].stableUiKey(), fixture.advanceNavigation.selectedAlbumTrackKey)
            assertTrue(fixture.calls.isEmpty()); assertTrue(fixture.seeks.isEmpty())
        }
    }

    @Test
    fun pocketAdvanceAlbumViewportsSurviveStartBackCollapseAndRecreation() {
        val fixture = advanceFixture()
        val supplied = advanceAlbumLibrary(35, 30)
        val groups = advanceAlbumGroups(supplied)
        fixture.librarySongs.value = supplied
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { fixture.Content() }
        awaitAdvanceSnapshot(fixture)
        advanceControl("pocketAdvanceMini")
        val original = awaitAdvanceSnapshot(fixture)
        openAdvanceAlbums()
        val navigation = fixture.advanceNavigation
        composeRule.onNodeWithTag("pocketAdvanceAlbumCatalogue", useUnmergedTree = true).performScrollToIndex(15)
        val catalogueViewport = composeRule.runOnIdle { navigation.albumsListState.firstVisibleItemIndex to navigation.albumsListState.firstVisibleItemScrollOffset }
        val group = groups[16]
        composeRule.onNodeWithTag(albumRowTag(group), useUnmergedTree = true).performTouchInput { click() }
        repeat(12) { advanceControl("pocketAdvanceDown") }
        composeRule.onNodeWithTag("pocketAdvanceAlbumRecord", useUnmergedTree = true).performScrollToIndex(10)
        val beforeTouch = composeRule.runOnIdle { navigation.albumTracksListState.firstVisibleItemIndex to navigation.albumTracksListState.firstVisibleItemScrollOffset }
        composeRule.onNodeWithTag("pocketAdvanceAlbumRecord", useUnmergedTree = true).performTouchInput {
            down(Offset(centerX, height * .8f)); moveTo(Offset(centerX, height * .25f)); up()
        }
        composeRule.runOnIdle {
            assertTrue(beforeTouch != (navigation.albumTracksListState.firstVisibleItemIndex to navigation.albumTracksListState.firstVisibleItemScrollOffset))
            assertEquals(group.songs[12].stableUiKey(), navigation.selectedAlbumTrackKey)
            assertFalse(fixture.lyrics.lyricsComposed); assertFalse(fixture.morph.isDragging)
        }
        val trackViewport = composeRule.runOnIdle { navigation.albumTracksListState.firstVisibleItemIndex to navigation.albumTracksListState.firstVisibleItemScrollOffset }
        advanceControl("pocketAdvanceStart")
        advanceControl("pocketAdvanceStart")
        composeRule.runOnIdle { assertEquals(trackViewport, navigation.albumTracksListState.firstVisibleItemIndex to navigation.albumTracksListState.firstVisibleItemScrollOffset) }
        advanceControl("pocketAdvanceBack")
        composeRule.runOnIdle { assertEquals(catalogueViewport, navigation.albumsListState.firstVisibleItemIndex to navigation.albumsListState.firstVisibleItemScrollOffset) }
        advanceControl("pocketAdvanceNext")
        collapseAdvanceThroughHeader()
        composeRule.runOnIdle { assertTrue(fixture.morph.isCollapsedAndIdle); assertEquals(PocketAdvanceLcdMode.NowPlaying, navigation.lcdMode) }
        advanceControl("pocketAdvanceMini")
        val reopened = awaitAdvanceSnapshot(fixture)
        assertSame(original.owner, reopened.owner); assertEquals(original.expanded, reopened.expanded)
        advanceControl("pocketAdvanceStart")
        composeRule.runOnIdle {
            assertEquals(PocketAdvanceBrowserPage.AlbumRecord(group.key), navigation.browserPage)
            assertEquals(trackViewport, navigation.albumTracksListState.firstVisibleItemIndex to navigation.albumTracksListState.firstVisibleItemScrollOffset)
            fixture.current.value = fixture.songs.first().copy(title = "External track change")
        }
        restoration.emulateSavedInstanceStateRestore()
        awaitAdvanceSnapshot(fixture)
        composeRule.runOnIdle {
            assertEquals(PocketAdvanceLcdMode.NowPlaying, fixture.advanceNavigation.lcdMode)
            assertEquals(PocketAdvanceBrowserPage.AlbumRecord(group.key), fixture.advanceNavigation.browserPage)
        }
        advanceControl("pocketAdvanceStart")
        composeRule.runOnIdle {
            assertEquals(group.songs[12].stableUiKey(), fixture.advanceNavigation.selectedAlbumTrackKey)
            assertEquals(trackViewport, fixture.advanceNavigation.albumTracksListState.firstVisibleItemIndex to fixture.advanceNavigation.albumTracksListState.firstVisibleItemScrollOffset)
            assertTrue(fixture.songSelections.isEmpty()); assertTrue(fixture.calls.isEmpty()); assertTrue(fixture.seeks.isEmpty())
        }
    }

    @Test
    fun pocketAdvanceAlbumRecordReconcilesMetadataReadinessTracksAndAlbumRemoval() {
        val fixture = advanceFixture()
        val supplied = advanceAlbumLibrary(3, 6)
        val group = advanceAlbumGroups(supplied)[1]
        fixture.librarySongs.value = supplied
        composeRule.setContent { fixture.Content() }
        awaitAdvanceSnapshot(fixture)
        advanceControl("pocketAdvanceMini")
        openAdvanceAlbums()
        advanceControl("pocketAdvanceDown")
        advanceControl("pocketAdvanceNext")
        advanceControl("pocketAdvanceDown")
        val selected = group.songs[1]
        val updated = supplied.map { if (it.folderPath == group.key) it.copy(album = "Revised album", title = "Revised " + it.title,
            albumArtUri = Uri.parse("https://invalid.example/remote-cover"), trackNumber = if (it.id == selected.id) 99 else it.trackNumber) else it }
        composeRule.runOnIdle { fixture.librarySongs.value = updated }
        val currentGroup = advanceAlbumGroups(updated).single { it.key == group.key }
        composeRule.onNodeWithTag("pocketAdvanceAlbumRecord", useUnmergedTree = true).performScrollToIndex(5)
        composeRule.onNodeWithTag(albumTrackTag(selected), useUnmergedTree = true).assertIsSelected()
        composeRule.onNodeWithTag(albumArtTag(group), useUnmergedTree = true).assertIsDisplayed()
        composeRule.runOnIdle { fixture.libraryReady.value = false; fixture.librarySongs.value = emptyList() }
        composeRule.onNodeWithTag("pocketAdvanceAlbumRecordEmpty", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("pocketAdvancePlay").assertIsNotEnabled()
        composeRule.onNodeWithTag("pocketAdvanceNext").assertIsNotEnabled()
        advanceControl("pocketAdvancePlay")
        composeRule.runOnIdle {
            assertEquals(PocketAdvanceBrowserPage.AlbumRecord(group.key), fixture.advanceNavigation.browserPage)
            assertEquals(selected.stableUiKey(), fixture.advanceNavigation.selectedAlbumTrackKey)
            assertTrue(fixture.songSelections.isEmpty())
            fixture.librarySongs.value = updated; fixture.libraryReady.value = true
        }
        advanceControl("pocketAdvanceNext")
        val currentTrack = currentGroup.songs.single { it.stableUiKey() == selected.stableUiKey() }
        composeRule.runOnIdle { assertEquals(listOf(currentTrack to currentGroup.songs), fixture.songSelections) }
        advanceControl("pocketAdvanceStart")
        val removedTrack = updated.filterNot { it.stableUiKey() == selected.stableUiKey() }
        composeRule.runOnIdle { fixture.librarySongs.value = removedTrack }
        val remainingGroup = advanceAlbumGroups(removedTrack).single { it.key == group.key }
        composeRule.runOnIdle { assertEquals(remainingGroup.songs.last().stableUiKey(), fixture.advanceNavigation.selectedAlbumTrackKey) }
        val withoutAlbum = removedTrack.filterNot { it.folderPath == group.key }
        composeRule.runOnIdle { fixture.libraryReady.value = false; fixture.librarySongs.value = withoutAlbum }
        composeRule.onNodeWithTag("pocketAdvanceAlbumRecordEmpty", useUnmergedTree = true).assertExists()
        composeRule.runOnIdle { fixture.libraryReady.value = true }
        composeRule.onNodeWithTag("pocketAdvanceAlbumCatalogue", useUnmergedTree = true).assertExists()
        composeRule.runOnIdle {
            assertEquals(PocketAdvanceBrowserPage.AlbumCatalogue, fixture.advanceNavigation.browserPage)
            assertEquals(null, fixture.advanceNavigation.retainedAlbumKey)
            assertEquals(null, fixture.advanceNavigation.selectedAlbumTrackKey)
            assertEquals(advanceAlbumGroups(withoutAlbum).last().key, fixture.advanceNavigation.selectedAlbumKey)
            fixture.librarySongs.value = emptyList()
        }
        composeRule.onNodeWithTag("pocketAdvanceAlbumCatalogueEmpty", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("pocketAdvancePlay").assertIsNotEnabled()
        composeRule.runOnIdle {
            assertEquals(null, fixture.advanceNavigation.selectedAlbumKey)
            assertEquals(1, fixture.songSelections.size)
            assertTrue(fixture.calls.isEmpty()); assertTrue(fixture.seeks.isEmpty())
        }
    }

    @Test
    fun pocketAdvanceAlbumArtworkMovesContinuouslyAndConfirmationWorksDuringOpening() {
        val fixture = advanceFixture()
        val supplied = advanceAlbumLibrary(3, 8)
        val group = advanceAlbumGroups(supplied).first()
        fixture.librarySongs.value = supplied
        composeRule.setContent { fixture.Content() }
        awaitAdvanceSnapshot(fixture)
        advanceControl("pocketAdvanceMini")
        openAdvanceAlbums()
        val tag = albumArtTag(group)
        val catalogue = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val archive = composeRule.onNodeWithTag("pocketAdvanceAlbumArchive", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        composeRule.mainClock.autoAdvance = false
        try {
            advanceControl("pocketAdvancePlay")
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.mainClock.advanceTimeBy(80)
            val middle = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).assertCountEquals(1)
            composeRule.mainClock.advanceTimeBy(240)
            val record = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue(catalogue.width > middle.width && middle.width > record.width)
            assertTrue(record.top < catalogue.top)
            listOf(catalogue, middle, record).forEach { rect ->
                assertTrue(rect.left >= archive.left && rect.right <= archive.right && rect.top >= archive.top && rect.bottom <= archive.bottom)
            }
            advanceControl("pocketAdvanceBack")
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.mainClock.advanceTimeBy(80)
            val closing = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue(closing.width > record.width && closing.width < catalogue.width)
            // Reversing the short LCD transition keeps one cover and cannot dispatch playback.
            advanceControl("pocketAdvancePlay")
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.mainClock.advanceTimeBy(80)
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).assertCountEquals(1)
            composeRule.runOnIdle { assertTrue(fixture.songSelections.isEmpty()) }
            // The animation is decorative: an explicit accessible confirmation remains usable now.
            val confirm = composeRule.onNodeWithTag("pocketAdvancePlay").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
            composeRule.runOnIdle { assertTrue(confirm()); assertTrue(confirm()) }
            composeRule.mainClock.advanceTimeBy(600)
            composeRule.runOnIdle {
                assertEquals(listOf(group.songs.first() to group.songs), fixture.songSelections)
                assertEquals(PocketAdvanceLcdMode.NowPlaying, fixture.advanceNavigation.lcdMode)
                assertTrue(fixture.calls.isEmpty()); assertTrue(fixture.seeks.isEmpty())
            }
            composeRule.onNodeWithTag("pocketAdvanceAlbumArchive", useUnmergedTree = true).assertDoesNotExist()
        } finally { composeRule.mainClock.autoAdvance = true }
    }

    @Test
    fun pocketAdvanceAlbumHoldsKeepPreviewCurrentAndCancelAtPageAndOverlayBoundaries() {
        val fixture = advanceFixture()
        val supplied = advanceAlbumLibrary(8, 8)
        val groups = advanceAlbumGroups(supplied)
        fixture.librarySongs.value = supplied
        composeRule.setContent { fixture.Content() }
        awaitAdvanceSnapshot(fixture)
        advanceControl("pocketAdvanceMini")
        openAdvanceAlbums()
        composeRule.mainClock.autoAdvance = false
        try {
            beginAdvanceHold("pocketAdvanceDown")
            composeRule.runOnIdle { assertEquals(groups[1].key, fixture.advanceNavigation.selectedAlbumKey) }
            composeRule.mainClock.advanceTimeBy(400)
            composeRule.onNodeWithTag(albumArtTag(groups[2]), useUnmergedTree = true).assertIsDisplayed()
            composeRule.runOnIdle { fixture.queueVisible.value = true }
            composeRule.mainClock.advanceTimeBy(1_000)
            composeRule.runOnIdle { assertEquals(groups[2].key, fixture.advanceNavigation.selectedAlbumKey); fixture.queueVisible.value = false }
            composeRule.mainClock.advanceTimeBy(600)
            composeRule.runOnIdle { assertEquals(groups[2].key, fixture.advanceNavigation.selectedAlbumKey) }
            composeRule.onRoot().performTouchInput { up() }
            composeRule.mainClock.advanceTimeByFrame()
            beginAdvanceHold("pocketAdvanceDown")
            val upTarget = composeRule.onNodeWithTag("pocketAdvanceUp", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            composeRule.onRoot().performTouchInput { down(pointerId = 1, position = upTarget.center) }
            composeRule.mainClock.advanceTimeBy(600)
            composeRule.runOnIdle { assertEquals(groups[3].key, fixture.advanceNavigation.selectedAlbumKey) }
            composeRule.onRoot().performTouchInput { up(pointerId = 0); up(pointerId = 1) }
            composeRule.mainClock.advanceTimeByFrame()
            beginAdvanceHold("pocketAdvanceDown")
            composeRule.mainClock.advanceTimeBy(400)
            val selected = groups[5]
            composeRule.runOnIdle {
                assertEquals(selected.key, fixture.advanceNavigation.selectedAlbumKey)
                fixture.advanceNavigation.openAlbum(selected.key, 5)
            }
            composeRule.mainClock.advanceTimeBy(1_000)
            composeRule.runOnIdle { assertEquals(selected.songs.first().stableUiKey(), fixture.advanceNavigation.selectedAlbumTrackKey) }
            composeRule.onRoot().performTouchInput { up() }
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.runOnIdle { fixture.advanceNavigation.selectAlbumTrack(selected.songs[6].stableUiKey(), 6) }
            beginAdvanceHold("pocketAdvanceDown")
            composeRule.mainClock.advanceTimeBy(1_200)
            composeRule.runOnIdle { assertEquals(selected.songs.last().stableUiKey(), fixture.advanceNavigation.selectedAlbumTrackKey) }
            composeRule.onRoot().performTouchInput { up() }
            composeRule.mainClock.advanceTimeByFrame()
            advanceControl("pocketAdvanceDown")
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.runOnIdle { assertEquals(selected.songs.first().stableUiKey(), fixture.advanceNavigation.selectedAlbumTrackKey) }
            beginAdvanceHold("pocketAdvanceDown")
            composeRule.mainClock.advanceTimeBy(400)
            composeRule.runOnIdle { fixture.advanceNavigation.back() }
            composeRule.mainClock.advanceTimeBy(1_000)
            composeRule.runOnIdle {
                assertEquals(PocketAdvanceBrowserPage.AlbumCatalogue, fixture.advanceNavigation.browserPage)
                assertEquals(selected.key, fixture.advanceNavigation.selectedAlbumKey)
                assertEquals(selected.songs[2].stableUiKey(), fixture.advanceNavigation.selectedAlbumTrackKey)
                assertTrue(fixture.songSelections.isEmpty()); assertTrue(fixture.calls.isEmpty()); assertTrue(fixture.seeks.isEmpty())
            }
            composeRule.onRoot().performTouchInput { up() }
        } finally { composeRule.mainClock.autoAdvance = true }
    }

    @Test
    fun pocketAdvanceAlbumCatalogueAndTracksRevealAdjacentRowsWithoutPageJumps() {
        val fixture = advanceFixture()
        val supplied = advanceAlbumLibrary(40, 35)
        val groups = advanceAlbumGroups(supplied)
        fixture.librarySongs.value = supplied
        composeRule.setContent { fixture.Content() }
        awaitAdvanceSnapshot(fixture)
        advanceControl("pocketAdvanceMini")
        openAdvanceAlbums()
        val navigation = fixture.advanceNavigation
        assertAdvanceBrowseEdgeReveal("pocketAdvanceAlbumCatalogue", navigation.albumsListState,
            rowTag = { albumRowTag(groups[it]) }, select = { navigation.albumsCursor.select(groups[it].key, it) })
        val group = groups[requireNotNull(navigation.selectedAlbumIndex(groups.map { it.key }))]
        advanceControl("pocketAdvanceNext")
        assertAdvanceBrowseEdgeReveal("pocketAdvanceAlbumRecord", navigation.albumTracksListState,
            rowTag = { albumTrackTag(group.songs[it]) }, select = { navigation.selectAlbumTrack(group.songs[it].stableUiKey(), it) })
        composeRule.runOnIdle {
            assertEquals(group.songs.size, navigation.albumTracksListState.layoutInfo.totalItemsCount)
            assertTrue(fixture.songSelections.isEmpty()); assertFalse(fixture.lyrics.lyricsComposed); assertFalse(fixture.morph.isDragging)
        }
    }

    @Test
    fun pocketAdvanceAlbumArchiveYieldsToCancelledReversedCollapseAndPortraitFallback() {
        val fixture = advanceFixture()
        val supplied = advanceAlbumLibrary(3, 30)
        val group = advanceAlbumGroups(supplied)[1]
        fixture.librarySongs.value = supplied
        composeRule.setContent { fixture.Content() }
        awaitAdvanceSnapshot(fixture)
        advanceControl("pocketAdvanceMini")
        val original = awaitAdvanceSnapshot(fixture)
        openAdvanceAlbums()
        advanceControl("pocketAdvanceDown")
        advanceControl("pocketAdvanceNext")
        repeat(12) { advanceControl("pocketAdvanceDown") }
        val browsing = awaitAdvanceSnapshot(fixture)
        assertSame(original.owner, browsing.owner); assertEquals(original.expanded, browsing.expanded); assertEquals(original.mini, browsing.mini)
        val viewport = composeRule.runOnIdle { fixture.advanceNavigation.albumTracksListState.firstVisibleItemIndex to fixture.advanceNavigation.albumTracksListState.firstVisibleItemScrollOffset }
        for (cancelled in listOf(true, false)) {
            val header = composeRule.onNodeWithTag("pocketAdvanceHeader", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            composeRule.onRoot().performTouchInput { down(header.center); moveBy(Offset(0f, 48f)) }
            composeRule.onNodeWithTag("pocketAdvanceAlbumArchive", useUnmergedTree = true).assertDoesNotExist()
            composeRule.runOnIdle { assertTrue(fixture.morph.isDragging) }
            composeRule.onRoot().performTouchInput { if (cancelled) cancel() else { moveTo(header.center - Offset(0f, 24f)); up() } }
            composeRule.onNodeWithTag("pocketAdvanceAlbumRecord", useUnmergedTree = true).assertExists()
            composeRule.runOnIdle {
                assertEquals(1f, fixture.morph.progress, 0f)
                assertEquals(group.songs[12].stableUiKey(), fixture.advanceNavigation.selectedAlbumTrackKey)
                assertEquals(viewport, fixture.advanceNavigation.albumTracksListState.firstVisibleItemIndex to fixture.advanceNavigation.albumTracksListState.firstVisibleItemScrollOffset)
            }
        }
        composeRule.runOnIdle { fixture.viewport.value = DpSize(720.dp, 1180.dp) }
        composeRule.runOnIdle {
            assertEquals(PlayerPresentationIdentity.Existing(PlayerTheme.CLASSIC_WHEEL), fixture.resolvedIdentity)
            assertEquals(PocketAdvanceLcdMode.NowPlaying, fixture.advanceNavigation.lcdMode)
            fixture.viewport.value = DpSize(1200.dp, 720.dp)
        }
        awaitAdvanceSnapshot(fixture)
        advanceControl("pocketAdvanceStart")
        composeRule.onNodeWithTag("pocketAdvanceAlbumRecord", useUnmergedTree = true).assertExists()
        composeRule.runOnIdle {
            assertEquals(group.songs[12].stableUiKey(), fixture.advanceNavigation.selectedAlbumTrackKey)
            assertEquals(viewport, fixture.advanceNavigation.albumTracksListState.firstVisibleItemIndex to fixture.advanceNavigation.albumTracksListState.firstVisibleItemScrollOffset)
            assertTrue(fixture.songSelections.isEmpty()); assertTrue(fixture.calls.isEmpty()); assertTrue(fixture.seeks.isEmpty())
        }
    }

    @Test
    fun pocketAdvanceArtistsHardwareOpensTheCanonicalGroupAndPlaysItsCompleteOrderedContext() {
        val fixture = advanceFixture()
        val supplied = advanceArtistLibrary(3, 5).reversed()
        val groups = advanceArtistGroups(supplied)
        fixture.librarySongs.value = supplied
        composeRule.setContent { fixture.Content() }
        awaitAdvanceSnapshot(fixture)
        advanceControl("pocketAdvanceMini")
        openAdvanceArtists()
        composeRule.onNodeWithTag(advanceArtistTag(groups.first()), useUnmergedTree = true).assertIsSelected()
        advanceControl("pocketAdvanceUp")
        composeRule.onNodeWithTag(advanceArtistTag(groups.last()), useUnmergedTree = true).assertIsSelected()
        advanceControl("pocketAdvanceDown")
        advanceControl("pocketAdvanceDown")
        val group = groups[1]
        assertEquals(listOf(6L, 7L, 8L, 9L, 10L), group.songs.map { it.id })
        advanceControl("pocketAdvancePlay")
        composeRule.runOnIdle {
            assertEquals(PocketAdvanceBrowserPage.ArtistSongs(group.key), fixture.advanceNavigation.browserPage)
            assertTrue(fixture.songSelections.isEmpty())
        }
        composeRule.onNodeWithTag(advanceArtistSongTag(group.songs.first()), useUnmergedTree = true).assertIsSelected()
        advanceControl("pocketAdvanceUp")
        composeRule.onNodeWithTag(advanceArtistSongTag(group.songs.last()), useUnmergedTree = true).assertIsSelected()
        advanceControl("pocketAdvanceDown")
        advanceControl("pocketAdvanceDown")
        advanceControl("pocketAdvancePlay")
        composeRule.runOnIdle {
            assertEquals(listOf(group.songs[1] to group.songs), fixture.songSelections)
            assertEquals(PocketAdvanceLcdMode.NowPlaying, fixture.advanceNavigation.lcdMode)
        }
        advanceControl("pocketAdvanceStart")
        composeRule.onNodeWithTag(advanceArtistSongTag(group.songs[1]), useUnmergedTree = true).assertIsSelected()
        advanceControl("pocketAdvanceNext")
        composeRule.runOnIdle { assertEquals(listOf(group.songs[1] to group.songs, group.songs[1] to group.songs), fixture.songSelections) }
        advanceControl("pocketAdvanceStart")
        advanceControl("pocketAdvanceBack")
        composeRule.onNodeWithTag(advanceArtistTag(group), useUnmergedTree = true).assertIsSelected()
        advanceControl("pocketAdvancePrevious")
        composeRule.onNodeWithTag("pocketAdvanceRootArtists").assertIsSelected()
        advanceControl("pocketAdvanceNext")
        advanceControl("pocketAdvanceNext")
        composeRule.runOnIdle {
            assertEquals(PocketAdvanceBrowserPage.ArtistSongs(group.key), fixture.advanceNavigation.browserPage)
            assertEquals(2, fixture.songSelections.size)
            assertTrue(fixture.calls.isEmpty()); assertTrue(fixture.seeks.isEmpty())
        }
    }

    @Test
    fun pocketAdvanceArtistsFollowTheCurrentLibrarySortWithoutLosingCanonicalSelection() {
        val fixture = advanceFixture()
        val supplied = advanceArtistLibrary(3, 4).filter { song ->
            song.artist != "Artist 01" || song.trackNumber == 1
        }.filter { song -> song.artist != "Artist 03" || song.trackNumber <= 2 }.reversed()
        val countDescending = LibrarySortState(LibrarySortOption.SONG_COUNT, LibrarySortDirection.DESCENDING)
        val groups = advanceArtistGroups(supplied, countDescending)
        assertEquals(listOf("Artist 02", "Artist 03", "Artist 01"), groups.map { it.name })
        fixture.librarySongs.value = supplied
        fixture.artistSortState.value = countDescending
        composeRule.setContent { fixture.Content() }
        awaitAdvanceSnapshot(fixture)
        advanceControl("pocketAdvanceMini")
        openAdvanceArtists()
        val selected = groups.first()
        composeRule.onNodeWithTag(advanceArtistTag(selected), useUnmergedTree = true).assertIsSelected()
        composeRule.runOnIdle {
            fixture.artistSortState.value = LibrarySortState(LibrarySortOption.NAME, LibrarySortDirection.ASCENDING)
        }
        composeRule.onNodeWithTag(advanceArtistTag(selected), useUnmergedTree = true).assertIsSelected()
        composeRule.runOnIdle {
            assertEquals(1, fixture.advanceNavigation.selectedArtistIndex(advanceArtistGroups(supplied).map { it.key }))
        }
        advanceControl("pocketAdvancePlay")
        composeRule.runOnIdle {
            assertEquals(PocketAdvanceBrowserPage.ArtistSongs(selected.key), fixture.advanceNavigation.browserPage)
            assertTrue(fixture.songSelections.isEmpty())
        }
        advanceControl("pocketAdvancePlay")
        composeRule.runOnIdle {
            assertEquals(listOf(selected.songs.first() to selected.songs), fixture.songSelections)
            assertTrue(fixture.calls.isEmpty()); assertTrue(fixture.seeks.isEmpty())
        }
    }

    @Test
    fun pocketAdvanceArtistAndSongTouchTargetsActivateTheirOwnRowsExactlyOnce() {
        val fixture = advanceFixture()
        val supplied = advanceArtistLibrary(3, 5).reversed()
        val groups = advanceArtistGroups(supplied)
        fixture.librarySongs.value = supplied
        composeRule.setContent { fixture.Content() }
        awaitAdvanceSnapshot(fixture)
        advanceControl("pocketAdvanceMini")
        advanceControl("pocketAdvanceStart")
        composeRule.onNodeWithTag("pocketAdvanceBrowser", useUnmergedTree = true).performScrollToIndex(2)
        composeRule.onNodeWithTag("pocketAdvanceRootArtists", useUnmergedTree = true).performTouchInput { click() }
        composeRule.onNodeWithTag("pocketAdvanceArtists", useUnmergedTree = true).assertExists()
        composeRule.runOnIdle { assertEquals(PocketAdvanceRootEntry.Artists, fixture.advanceNavigation.selectedRootEntry) }
        // The touched group differs from the hardware highlight; its own identity must win.
        val group = groups[2]
        composeRule.onNodeWithTag(advanceArtistTag(group), useUnmergedTree = true).performScrollTo().performTouchInput { click() }
        composeRule.runOnIdle {
            assertEquals(group.key, fixture.advanceNavigation.selectedArtistKey)
            assertEquals(PocketAdvanceBrowserPage.ArtistSongs(group.key), fixture.advanceNavigation.browserPage)
            assertTrue(fixture.songSelections.isEmpty())
        }
        composeRule.onNodeWithTag("pocketAdvanceArtistSongs", useUnmergedTree = true).performScrollToIndex(3)
        composeRule.onNodeWithTag(advanceArtistSongTag(group.songs[3]), useUnmergedTree = true).performTouchInput { click() }
        composeRule.runOnIdle {
            assertEquals(listOf(group.songs[3] to group.songs), fixture.songSelections)
            assertEquals(group.songs[3].stableUiKey(), fixture.advanceNavigation.selectedArtistSongKey)
            assertEquals(PocketAdvanceLcdMode.NowPlaying, fixture.advanceNavigation.lcdMode)
            assertTrue(fixture.calls.isEmpty()); assertTrue(fixture.seeks.isEmpty())
        }
        advanceControl("pocketAdvanceStart")
        composeRule.onNodeWithTag(advanceArtistSongTag(group.songs[3]), useUnmergedTree = true).assertIsSelected()
    }

    @Test
    fun pocketAdvanceArtistsKeepSeparateTouchViewportsThroughBackStartAndSavedState() {
        val fixture = advanceFixture()
        val supplied = advanceArtistLibrary(40, 35).reversed()
        val groups = advanceArtistGroups(supplied)
        fixture.librarySongs.value = supplied
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { fixture.Content() }
        awaitAdvanceSnapshot(fixture)
        advanceControl("pocketAdvanceMini")
        openAdvanceArtists()
        val navigation = fixture.advanceNavigation
        val artistsState = navigation.artistsListState
        composeRule.onNodeWithTag("pocketAdvanceArtists", useUnmergedTree = true).performScrollToIndex(10)
        val beforeTouch = composeRule.runOnIdle { artistsState.firstVisibleItemIndex to artistsState.firstVisibleItemScrollOffset }
        composeRule.onNodeWithTag("pocketAdvanceArtists", useUnmergedTree = true).performTouchInput {
            down(Offset(centerX, height * .8f)); moveTo(Offset(centerX, height * .25f)); up()
        }
        composeRule.runOnIdle {
            assertTrue(beforeTouch != (artistsState.firstVisibleItemIndex to artistsState.firstVisibleItemScrollOffset))
            assertEquals(groups.first().key, navigation.selectedArtistKey)
            assertFalse(fixture.lyrics.lyricsComposed); assertFalse(fixture.morph.isDragging)
        }
        composeRule.onNodeWithTag("pocketAdvanceArtists", useUnmergedTree = true).performScrollToIndex(20)
        val artistsViewport = composeRule.runOnIdle { artistsState.firstVisibleItemIndex to artistsState.firstVisibleItemScrollOffset }
        val group = groups[21]
        composeRule.onNodeWithTag(advanceArtistTag(group), useUnmergedTree = true).performTouchInput { click() }
        repeat(12) { advanceControl("pocketAdvanceDown") }
        val tracksState = navigation.artistSongsListState
        composeRule.onNodeWithTag("pocketAdvanceArtistSongs", useUnmergedTree = true).performScrollToIndex(10)
        val tracksBeforeTouch = composeRule.runOnIdle { tracksState.firstVisibleItemIndex to tracksState.firstVisibleItemScrollOffset }
        composeRule.onNodeWithTag("pocketAdvanceArtistSongs", useUnmergedTree = true).performTouchInput {
            down(Offset(centerX, height * .8f)); moveTo(Offset(centerX, height * .25f)); up()
        }
        composeRule.runOnIdle {
            assertTrue(tracksBeforeTouch != (tracksState.firstVisibleItemIndex to tracksState.firstVisibleItemScrollOffset))
            assertEquals(group.songs[12].stableUiKey(), navigation.selectedArtistSongKey)
            assertFalse(fixture.lyrics.lyricsComposed); assertFalse(fixture.morph.isDragging)
            assertEquals(1f, fixture.morph.progress, 0f)
        }
        composeRule.onNodeWithTag("pocketAdvanceArtistSongs", useUnmergedTree = true).performScrollToIndex(20)
        val tracksViewport = composeRule.runOnIdle { tracksState.firstVisibleItemIndex to tracksState.firstVisibleItemScrollOffset }
        advanceControl("pocketAdvancePlay")
        composeRule.runOnIdle { assertEquals(listOf(group.songs[12] to group.songs), fixture.songSelections) }
        advanceControl("pocketAdvanceStart")
        composeRule.runOnIdle {
            assertEquals(group.songs[12].stableUiKey(), navigation.selectedArtistSongKey)
            assertEquals(tracksViewport, tracksState.firstVisibleItemIndex to tracksState.firstVisibleItemScrollOffset)
        }
        advanceControl("pocketAdvanceBack")
        composeRule.onNodeWithTag(advanceArtistTag(group), useUnmergedTree = true).assertIsSelected()
        composeRule.runOnIdle {
            assertEquals(artistsViewport, artistsState.firstVisibleItemIndex to artistsState.firstVisibleItemScrollOffset)
        }
        advanceControl("pocketAdvanceNext")
        composeRule.runOnIdle {
            assertSame(tracksState, navigation.artistSongsListState)
            assertEquals(tracksViewport, tracksState.firstVisibleItemIndex to tracksState.firstVisibleItemScrollOffset)
            fixture.current.value = fixture.songs.first().copy(title = "Changed outside the console")
        }
        restoration.emulateSavedInstanceStateRestore()
        awaitAdvanceSnapshot(fixture)
        composeRule.runOnIdle {
            assertNotSame(navigation, fixture.advanceNavigation)
            assertEquals(PocketAdvanceLcdMode.NowPlaying, fixture.advanceNavigation.lcdMode)
            assertEquals(PocketAdvanceBrowserPage.ArtistSongs(group.key), fixture.advanceNavigation.browserPage)
            assertEquals(group.songs[12].stableUiKey(), fixture.advanceNavigation.selectedArtistSongKey)
        }
        advanceControl("pocketAdvanceStart")
        composeRule.onNodeWithTag("pocketAdvanceArtistSongs", useUnmergedTree = true).assertExists()
        composeRule.runOnIdle {
            assertEquals(tracksViewport, fixture.advanceNavigation.artistSongsListState.firstVisibleItemIndex to
                fixture.advanceNavigation.artistSongsListState.firstVisibleItemScrollOffset)
        }
        advanceControl("pocketAdvanceBack")
        composeRule.runOnIdle {
            assertEquals(artistsViewport, fixture.advanceNavigation.artistsListState.firstVisibleItemIndex to
                fixture.advanceNavigation.artistsListState.firstVisibleItemScrollOffset)
            assertEquals(group.key, fixture.advanceNavigation.selectedArtistKey)
            assertFalse(fixture.lyrics.lyricsComposed); assertFalse(fixture.morph.isDragging)
            assertEquals(1, fixture.songSelections.size)
            assertTrue(fixture.calls.isEmpty()); assertTrue(fixture.seeks.isEmpty())
        }
    }

    @Test
    fun pocketAdvanceArtistsAndArtistSongsRevealAdjacentRowsAtBothViewportEdges() {
        val fixture = advanceFixture()
        val supplied = advanceArtistLibrary(30, 30)
        val groups = advanceArtistGroups(supplied)
        fixture.librarySongs.value = supplied
        composeRule.setContent { fixture.Content() }
        awaitAdvanceSnapshot(fixture)
        advanceControl("pocketAdvanceMini")
        openAdvanceArtists()
        val navigation = fixture.advanceNavigation
        assertAdvanceBrowseEdgeReveal(
            "pocketAdvanceArtists", navigation.artistsListState,
            rowTag = { index -> advanceArtistTag(groups[index]) },
            select = { index ->
                navigation.openArtist(groups[index].key, index)
                navigation.back()
            }
        )
        val group = groups[2]
        composeRule.runOnIdle { navigation.openArtist(group.key, 2) }
        composeRule.onNodeWithTag("pocketAdvanceArtistSongs", useUnmergedTree = true).assertExists()
        assertAdvanceBrowseEdgeReveal(
            "pocketAdvanceArtistSongs", navigation.artistSongsListState,
            rowTag = { index -> advanceArtistSongTag(group.songs[index]) },
            select = { index -> navigation.selectArtistSong(group.songs[index].stableUiKey(), index) }
        )
        composeRule.runOnIdle {
            assertFalse(fixture.lyrics.lyricsComposed); assertFalse(fixture.morph.isDragging)
            assertEquals(1f, fixture.morph.progress, 0f)
            assertTrue(fixture.songSelections.isEmpty()); assertTrue(fixture.calls.isEmpty()); assertTrue(fixture.seeks.isEmpty())
        }
    }

    @Test
    fun pocketAdvanceArtistSongsReconcileCanonicalIdentityReadinessAndConfirmedRemoval() {
        val fixture = advanceFixture()
        val supplied = advanceArtistLibrary(3, 4)
        val group = advanceArtistGroups(supplied)[1]
        fixture.librarySongs.value = supplied
        composeRule.setContent { fixture.Content() }
        awaitAdvanceSnapshot(fixture)
        advanceControl("pocketAdvanceMini")
        openAdvanceArtists()
        advanceControl("pocketAdvanceDown")
        advanceControl("pocketAdvanceNext")
        advanceControl("pocketAdvanceDown")
        val selected = group.songs[1]
        val updated = supplied.reversed().map {
            if (it in group.songs) it.copy(artist = "  ARTIST 02  ", title = "Updated " + it.title,
                trackNumber = if (it.id == selected.id) 99 else it.trackNumber) else it
        }
        val updatedGroup = advanceArtistGroups(updated).single { it.key == group.key }
        composeRule.runOnIdle { fixture.librarySongs.value = updated }
        composeRule.onNodeWithTag(advanceArtistSongTag(selected), useUnmergedTree = true).assertIsSelected()
        composeRule.runOnIdle {
            fixture.libraryReady.value = false
            fixture.librarySongs.value = emptyList()
        }
        composeRule.onNodeWithTag("pocketAdvanceArtistSongsEmpty", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("pocketAdvancePlay").assertIsNotEnabled()
        composeRule.onNodeWithTag("pocketAdvanceNext").assertIsNotEnabled()
        advanceControl("pocketAdvancePlay")
        advanceControl("pocketAdvanceNext")
        composeRule.runOnIdle {
            assertEquals(PocketAdvanceBrowserPage.ArtistSongs(group.key), fixture.advanceNavigation.browserPage)
            assertEquals(selected.stableUiKey(), fixture.advanceNavigation.selectedArtistSongKey)
            assertTrue(fixture.songSelections.isEmpty())
            fixture.librarySongs.value = updated
            fixture.libraryReady.value = true
        }
        composeRule.onNodeWithTag(advanceArtistSongTag(selected), useUnmergedTree = true).assertIsSelected()
        advanceControl("pocketAdvanceNext")
        val currentSelected = updatedGroup.songs.single { it.stableUiKey() == selected.stableUiKey() }
        composeRule.runOnIdle { assertEquals(listOf(currentSelected to updatedGroup.songs), fixture.songSelections) }
        advanceControl("pocketAdvanceStart")
        val remaining = updated.filterNot { it.stableUiKey() == selected.stableUiKey() }
        val remainingGroup = advanceArtistGroups(remaining).single { it.key == group.key }
        val neighbor = remainingGroup.songs.last()
        composeRule.runOnIdle { fixture.librarySongs.value = remaining }
        composeRule.onNodeWithTag(advanceArtistSongTag(neighbor), useUnmergedTree = true).assertIsSelected()
        advanceControl("pocketAdvancePlay")
        composeRule.runOnIdle { assertEquals(listOf(currentSelected to updatedGroup.songs, neighbor to remainingGroup.songs), fixture.songSelections) }
        advanceControl("pocketAdvanceStart")
        val withoutArtist = remaining.filterNot { it in remainingGroup.songs }
        composeRule.runOnIdle {
            fixture.libraryReady.value = false
            fixture.librarySongs.value = withoutArtist
        }
        composeRule.onNodeWithTag("pocketAdvanceArtistSongsEmpty", useUnmergedTree = true).assertExists()
        composeRule.runOnIdle {
            assertEquals(PocketAdvanceBrowserPage.ArtistSongs(group.key), fixture.advanceNavigation.browserPage)
            assertEquals(neighbor.stableUiKey(), fixture.advanceNavigation.selectedArtistSongKey)
            fixture.libraryReady.value = true
        }
        composeRule.onNodeWithTag("pocketAdvanceArtists", useUnmergedTree = true).assertExists()
        composeRule.runOnIdle {
            assertEquals(PocketAdvanceBrowserPage.Artists, fixture.advanceNavigation.browserPage)
            assertEquals(null, fixture.advanceNavigation.selectedArtistSongKey)
            assertEquals(advanceArtistGroups(withoutArtist).last().key, fixture.advanceNavigation.selectedArtistKey)
            assertEquals(2, fixture.songSelections.size)
            assertTrue(fixture.calls.isEmpty()); assertTrue(fixture.seeks.isEmpty())
        }
    }

    @Test
    fun pocketAdvanceArtistSongsPreserveMorphAnchorsThroughCollapseReversalAndPortraitFallback() {
        val fixture = advanceFixture()
        val supplied = advanceArtistLibrary(3, 30)
        val group = advanceArtistGroups(supplied)[1]
        fixture.librarySongs.value = supplied
        composeRule.setContent { fixture.Content() }
        awaitAdvanceSnapshot(fixture)
        advanceControl("pocketAdvanceMini")
        val initial = awaitAdvanceSnapshot(fixture)
        openAdvanceArtists()
        advanceControl("pocketAdvanceDown")
        advanceControl("pocketAdvanceNext")
        repeat(14) { advanceControl("pocketAdvanceDown") }
        composeRule.onNodeWithTag("pocketAdvanceArtistSongs", useUnmergedTree = true).performScrollToIndex(10)
        val navigation = fixture.advanceNavigation
        val viewport = composeRule.runOnIdle {
            navigation.artistSongsListState.firstVisibleItemIndex to navigation.artistSongsListState.firstVisibleItemScrollOffset
        }
        val browsing = awaitAdvanceSnapshot(fixture)
        assertSame(initial.owner, browsing.owner)
        assertEquals(initial.expanded, browsing.expanded); assertEquals(initial.mini, browsing.mini)
        composeRule.onNodeWithTag("pocketAdvanceSeek").assertDoesNotExist()
        for (cancelGesture in listOf(true, false)) {
            val header = composeRule.onNodeWithTag("pocketAdvanceHeader", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            composeRule.onRoot().performTouchInput { down(header.center); moveBy(Offset(0f, 48f)) }
            composeRule.onNodeWithTag("pocketAdvanceArtistSongs", useUnmergedTree = true).assertDoesNotExist()
            composeRule.runOnIdle { assertTrue(fixture.morph.isDragging) }
            composeRule.onRoot().performTouchInput {
                if (cancelGesture) cancel() else { moveTo(header.center - Offset(0f, 24f)); up() }
            }
            composeRule.onNodeWithTag("pocketAdvanceArtistSongs", useUnmergedTree = true).assertExists()
            composeRule.runOnIdle {
                assertEquals(1f, fixture.morph.progress, 0f)
                assertEquals(group.songs[14].stableUiKey(), navigation.selectedArtistSongKey)
                assertEquals(viewport, navigation.artistSongsListState.firstVisibleItemIndex to navigation.artistSongsListState.firstVisibleItemScrollOffset)
            }
        }
        collapseAdvanceThroughHeader()
        composeRule.runOnIdle {
            assertTrue(fixture.morph.isCollapsedAndIdle)
            assertEquals(PocketAdvanceLcdMode.NowPlaying, navigation.lcdMode)
        }
        advanceControl("pocketAdvanceMini")
        composeRule.onNodeWithTag("pocketAdvanceArtistSongs", useUnmergedTree = true).assertDoesNotExist()
        advanceControl("pocketAdvanceStart")
        composeRule.runOnIdle {
            assertEquals(PocketAdvanceBrowserPage.ArtistSongs(group.key), navigation.browserPage)
            assertEquals(viewport, navigation.artistSongsListState.firstVisibleItemIndex to navigation.artistSongsListState.firstVisibleItemScrollOffset)
            fixture.viewport.value = DpSize(720.dp, 1180.dp)
        }
        composeRule.runOnIdle {
            assertEquals(PlayerPresentationIdentity.Existing(PlayerTheme.CLASSIC_WHEEL), fixture.resolvedIdentity)
            assertEquals(PocketAdvanceLcdMode.NowPlaying, navigation.lcdMode)
            fixture.viewport.value = DpSize(1200.dp, 720.dp)
        }
        awaitAdvanceSnapshot(fixture)
        advanceControl("pocketAdvanceStart")
        composeRule.onNodeWithTag("pocketAdvanceArtistSongs", useUnmergedTree = true).assertExists()
        composeRule.runOnIdle {
            assertEquals(group.songs[14].stableUiKey(), navigation.selectedArtistSongKey)
            assertEquals(viewport, navigation.artistSongsListState.firstVisibleItemIndex to navigation.artistSongsListState.firstVisibleItemScrollOffset)
            assertFalse(fixture.lyrics.lyricsComposed)
            assertTrue(fixture.songSelections.isEmpty()); assertTrue(fixture.calls.isEmpty()); assertTrue(fixture.seeks.isEmpty())
        }
    }

    @Test
    fun pocketAdvanceArtistHoldsStopWhenAnotherRepeatablePageTakesOwnership() {
        val fixture = advanceFixture()
        val supplied = advanceArtistLibrary(30, 30)
        val groups = advanceArtistGroups(supplied)
        fixture.librarySongs.value = supplied
        composeRule.setContent { fixture.Content() }
        awaitAdvanceSnapshot(fixture)
        advanceControl("pocketAdvanceMini")
        openAdvanceArtists()
        composeRule.mainClock.autoAdvance = false
        try {
            beginAdvanceHold("pocketAdvanceDown")
            composeRule.runOnIdle { assertEquals(groups[1].key, fixture.advanceNavigation.selectedArtistKey) }
            composeRule.mainClock.advanceTimeBy(400)
            val group = groups[2]
            composeRule.runOnIdle {
                assertEquals(group.key, fixture.advanceNavigation.selectedArtistKey)
                fixture.advanceNavigation.openArtist(group.key, 2)
            }
            composeRule.mainClock.advanceTimeBy(1_000)
            composeRule.runOnIdle {
                assertEquals(PocketAdvanceBrowserPage.ArtistSongs(group.key), fixture.advanceNavigation.browserPage)
                assertEquals(group.songs.first().stableUiKey(), fixture.advanceNavigation.selectedArtistSongKey)
            }
            composeRule.onRoot().performTouchInput { up() }
            composeRule.mainClock.advanceTimeByFrame()
            beginAdvanceHold("pocketAdvanceDown")
            composeRule.mainClock.advanceTimeBy(400)
            composeRule.runOnIdle {
                assertEquals(group.songs[2].stableUiKey(), fixture.advanceNavigation.selectedArtistSongKey)
                fixture.advanceNavigation.back()
            }
            composeRule.mainClock.advanceTimeBy(1_000)
            composeRule.runOnIdle {
                assertEquals(PocketAdvanceBrowserPage.Artists, fixture.advanceNavigation.browserPage)
                assertEquals(group.key, fixture.advanceNavigation.selectedArtistKey)
                assertEquals(group.songs[2].stableUiKey(), fixture.advanceNavigation.selectedArtistSongKey)
                assertFalse(fixture.lyrics.lyricsComposed); assertFalse(fixture.morph.isDragging)
                assertTrue(fixture.songSelections.isEmpty()); assertTrue(fixture.calls.isEmpty()); assertTrue(fixture.seeks.isEmpty())
            }
            composeRule.onRoot().performTouchInput { up() }
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.runOnIdle {
                assertEquals(group.key, fixture.advanceNavigation.selectedArtistKey)
                assertEquals(group.songs[2].stableUiKey(), fixture.advanceNavigation.selectedArtistSongKey)
            }
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun pocketAdvanceSongsAAndRightDispatchExactlyOnceWithTheSuppliedLibraryOrder() {
        val fixture = advanceFixture()
        val supplied = listOf(fixture.songs[2], fixture.songs[0], fixture.songs[1])
        fixture.librarySongs.value = supplied
        composeRule.setContent { fixture.Content() }
        awaitAdvanceSnapshot(fixture)
        advanceControl("pocketAdvanceMini")
        advanceControl("pocketAdvanceBack")
        advanceControl("pocketAdvanceDown")
        advanceControl("pocketAdvanceNext")
        composeRule.onNodeWithTag(advanceSongTag(supplied.first()), useUnmergedTree = true).assertIsSelected()
        advanceControl("pocketAdvanceDown")
        composeRule.onNodeWithTag(advanceSongTag(supplied[1]), useUnmergedTree = true).assertIsSelected()
        advanceControl("pocketAdvancePlay")
        composeRule.runOnIdle {
            assertEquals(listOf(supplied[1] to supplied), fixture.songSelections)
            assertEquals(PocketAdvanceLcdMode.NowPlaying, fixture.advanceNavigation.lcdMode)
        }
        advanceControl("pocketAdvanceStart")
        composeRule.onNodeWithTag(advanceSongTag(supplied[1]), useUnmergedTree = true).assertIsSelected()
        advanceControl("pocketAdvanceNext")
        composeRule.runOnIdle { assertEquals(listOf(supplied[1] to supplied, supplied[1] to supplied), fixture.songSelections) }
        advanceControl("pocketAdvanceStart")
        advanceControl("pocketAdvancePrevious")
        composeRule.onNodeWithTag("pocketAdvanceRootSongs").assertIsSelected()
        composeRule.runOnIdle {
            assertEquals(PocketAdvanceBrowserPage.Root, fixture.advanceNavigation.browserPage)
            assertTrue(fixture.calls.isEmpty()); assertTrue(fixture.seeks.isEmpty())
        }
    }

    @Test
    fun pocketAdvanceLongSongsTouchScrollAndSpecificRowTapRestoreTheExactViewport() {
        val fixture = advanceFixture()
        val supplied = advanceLibrarySongs(80)
        fixture.librarySongs.value = supplied
        composeRule.setContent { fixture.Content() }
        awaitAdvanceSnapshot(fixture)
        advanceControl("pocketAdvanceMini")
        openAdvanceSongs()
        val navigation = fixture.advanceNavigation
        val listState = navigation.songsListState
        composeRule.onNodeWithTag("pocketAdvanceSongs", useUnmergedTree = true).performScrollToIndex(30)
        val beforeGesture = composeRule.runOnIdle { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
        composeRule.onNodeWithTag("pocketAdvanceSongs", useUnmergedTree = true).performTouchInput {
            down(Offset(centerX, height * .8f)); moveTo(Offset(centerX, height * .25f)); up()
        }
        composeRule.runOnIdle {
            assertTrue("A real vertical gesture must scroll the song list",
                beforeGesture != (listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset))
            assertEquals(supplied.first().stableUiKey(), navigation.selectedSongKey)
            assertFalse(fixture.lyrics.lyricsComposed)
            assertFalse(fixture.morph.isDragging)
            assertEquals(1f, fixture.morph.progress, 0f)
            assertTrue(fixture.songSelections.isEmpty())
        }
        composeRule.onNodeWithTag("pocketAdvanceSongs", useUnmergedTree = true).performScrollToIndex(45)
        val viewport = composeRule.runOnIdle { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
        composeRule.onNodeWithTag(advanceSongTag(supplied[46]), useUnmergedTree = true).performTouchInput { click() }
        composeRule.runOnIdle {
            assertEquals(listOf(supplied[46] to supplied), fixture.songSelections)
            assertEquals(PocketAdvanceLcdMode.NowPlaying, navigation.lcdMode)
            assertEquals(supplied[46].stableUiKey(), navigation.selectedSongKey)
        }
        advanceControl("pocketAdvanceStart")
        composeRule.onNodeWithTag(advanceSongTag(supplied[46]), useUnmergedTree = true).assertIsSelected()
        composeRule.runOnIdle {
            assertSame(listState, navigation.songsListState)
            assertEquals(viewport, listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset)
        }
        advanceControl("pocketAdvanceBack")
        composeRule.onNodeWithTag("pocketAdvanceRootSongs").assertIsSelected()
        advanceControl("pocketAdvanceNext")
        composeRule.onNodeWithTag(advanceSongTag(supplied[46]), useUnmergedTree = true).assertIsSelected()
        composeRule.runOnIdle {
            assertEquals(viewport, listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset)
            assertTrue(fixture.calls.isEmpty()); assertTrue(fixture.seeks.isEmpty())
            assertEquals(1, fixture.songSelections.size)
        }
    }

    @Test
    fun pocketAdvanceSongsReconcilesCurrentIdentityAndDefersDeletionDuringTransientEmptyData() {
        val fixture = advanceFixture()
        composeRule.setContent { fixture.Content() }
        awaitAdvanceSnapshot(fixture)
        advanceControl("pocketAdvanceMini")
        openAdvanceSongs()
        advanceControl("pocketAdvanceDown")
        val selected = fixture.songs[1]
        val reordered = listOf(fixture.songs[2], fixture.songs[0], selected)
        composeRule.runOnIdle { fixture.librarySongs.value = reordered }
        composeRule.onNodeWithTag(advanceSongTag(selected), useUnmergedTree = true).assertIsSelected()
        advanceControl("pocketAdvanceNext")
        composeRule.runOnIdle { assertEquals(listOf(selected to reordered), fixture.songSelections) }
        advanceControl("pocketAdvanceStart")
        val remaining = reordered.filterNot { it.stableUiKey() == selected.stableUiKey() }
        composeRule.runOnIdle { fixture.librarySongs.value = remaining }
        val neighbor = remaining.last()
        composeRule.onNodeWithTag(advanceSongTag(neighbor), useUnmergedTree = true).assertIsSelected()
        advanceControl("pocketAdvancePlay")
        composeRule.runOnIdle { assertEquals(listOf(selected to reordered, neighbor to remaining), fixture.songSelections) }
        advanceControl("pocketAdvanceStart")
        composeRule.runOnIdle {
            fixture.libraryReady.value = false
            fixture.librarySongs.value = emptyList()
        }
        composeRule.onNodeWithTag("pocketAdvancePlay").assertIsNotEnabled()
        composeRule.onNodeWithTag("pocketAdvanceNext").assertIsNotEnabled()
        composeRule.runOnIdle {
            assertEquals(PocketAdvanceBrowserPage.Songs, fixture.advanceNavigation.browserPage)
            assertEquals(neighbor.stableUiKey(), fixture.advanceNavigation.selectedSongKey)
            assertEquals(2, fixture.songSelections.size)
        }
        val refreshed = remaining.reversed().map { if (it.stableUiKey() == neighbor.stableUiKey()) it.copy(title = "Updated title") else it }
        composeRule.runOnIdle { fixture.librarySongs.value = refreshed; fixture.libraryReady.value = true }
        composeRule.onNodeWithTag(advanceSongTag(neighbor), useUnmergedTree = true).assertIsSelected()
        advanceControl("pocketAdvanceNext")
        composeRule.runOnIdle {
            assertEquals(refreshed.first { it.stableUiKey() == neighbor.stableUiKey() } to refreshed, fixture.songSelections.last())
            assertEquals(3, fixture.songSelections.size)
        }
        advanceControl("pocketAdvanceStart")
        composeRule.runOnIdle { fixture.librarySongs.value = emptyList() }
        composeRule.onNodeWithTag("pocketAdvanceSongsEmpty", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("pocketAdvancePlay").assertIsNotEnabled()
        composeRule.onNodeWithTag("pocketAdvanceNext").assertIsNotEnabled()
        composeRule.runOnIdle {
            assertEquals(null, fixture.advanceNavigation.selectedSongKey)
            assertEquals(3, fixture.songSelections.size)
            assertTrue(fixture.calls.isEmpty()); assertTrue(fixture.seeks.isEmpty())
        }
    }

    @Test
    fun pocketAdvanceSongsCollapseCancellationAndReversalRetainViewportAndMorphAnchors() {
        val fixture = advanceFixture()
        val supplied = advanceLibrarySongs(60)
        fixture.librarySongs.value = supplied
        composeRule.setContent { fixture.Content() }
        awaitAdvanceSnapshot(fixture)
        advanceControl("pocketAdvanceMini")
        val initial = awaitAdvanceSnapshot(fixture)
        openAdvanceSongs()
        repeat(14) { advanceControl("pocketAdvanceDown") }
        composeRule.onNodeWithTag(advanceSongTag(supplied[14]), useUnmergedTree = true).assertIsSelected()
        val browsing = awaitAdvanceSnapshot(fixture)
        assertSame(initial.owner, browsing.owner)
        assertEquals(initial.expanded, browsing.expanded)
        val navigation = fixture.advanceNavigation
        val viewport = composeRule.runOnIdle {
            navigation.songsListState.firstVisibleItemIndex to navigation.songsListState.firstVisibleItemScrollOffset
        }
        for (cancelGesture in listOf(true, false)) {
            val header = composeRule.onNodeWithTag("pocketAdvanceHeader", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            composeRule.onRoot().performTouchInput { down(header.center); moveBy(Offset(0f, 48f)) }
            composeRule.onNodeWithTag("pocketAdvanceSongs", useUnmergedTree = true).assertDoesNotExist()
            composeRule.runOnIdle { assertTrue(fixture.morph.isDragging) }
            composeRule.onRoot().performTouchInput {
                if (cancelGesture) cancel() else { moveTo(header.center - Offset(0f, 24f)); up() }
            }
            composeRule.onNodeWithTag(advanceSongTag(supplied[14]), useUnmergedTree = true).assertIsSelected()
            composeRule.runOnIdle {
                assertEquals(1f, fixture.morph.progress, 0f)
                assertFalse(fixture.morph.isDragging)
                assertFalse(fixture.morph.isAnimating)
                assertEquals(viewport, navigation.songsListState.firstVisibleItemIndex to navigation.songsListState.firstVisibleItemScrollOffset)
            }
        }
        val header = composeRule.onNodeWithTag("pocketAdvanceHeader", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        composeRule.onRoot().performTouchInput { down(header.center); moveBy(Offset(0f, 48f)) }
        composeRule.onNodeWithTag("pocketAdvanceSongs", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onRoot().performTouchInput { moveBy(Offset(0f, 72f)); up() }
        composeRule.runOnIdle {
            assertTrue(fixture.morph.isCollapsedAndIdle)
            assertEquals(PocketAdvanceLcdMode.NowPlaying, navigation.lcdMode)
            assertEquals(PocketAdvanceBrowserPage.Songs, navigation.browserPage)
        }
        advanceControl("pocketAdvanceMini")
        composeRule.onNodeWithTag("pocketAdvanceSongs", useUnmergedTree = true).assertDoesNotExist()
        val reopened = awaitAdvanceSnapshot(fixture)
        assertSame(initial.owner, reopened.owner)
        assertEquals(initial.expanded, reopened.expanded)
        advanceControl("pocketAdvanceStart")
        composeRule.onNodeWithTag(advanceSongTag(supplied[14]), useUnmergedTree = true).assertIsSelected()
        composeRule.runOnIdle {
            assertEquals(viewport, navigation.songsListState.firstVisibleItemIndex to navigation.songsListState.firstVisibleItemScrollOffset)
            assertTrue(fixture.songSelections.isEmpty())
            assertTrue(fixture.calls.isEmpty()); assertTrue(fixture.seeks.isEmpty())
        }
    }

    @Test
    fun pocketAdvanceSongsSavedLocationAndViewportSurviveExternalTrackChangeAndRecreation() {
        val fixture = advanceFixture()
        val supplied = advanceLibrarySongs(40)
        fixture.librarySongs.value = supplied
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { fixture.Content() }
        awaitAdvanceSnapshot(fixture)
        advanceControl("pocketAdvanceMini")
        openAdvanceSongs()
        repeat(12) { advanceControl("pocketAdvanceDown") }
        composeRule.onNodeWithTag("pocketAdvanceSongs", useUnmergedTree = true).performScrollToIndex(10)
        val navigation = fixture.advanceNavigation
        val viewport = composeRule.runOnIdle {
            fixture.current.value = fixture.songs.first().copy(title = "External track change")
            navigation.songsListState.firstVisibleItemIndex to navigation.songsListState.firstVisibleItemScrollOffset
        }
        composeRule.runOnIdle {
            assertSame(navigation, fixture.advanceNavigation)
            assertEquals(PocketAdvanceLcdMode.MusicBrowser, navigation.lcdMode)
            assertEquals(supplied[12].stableUiKey(), navigation.selectedSongKey)
            assertEquals(viewport, navigation.songsListState.firstVisibleItemIndex to navigation.songsListState.firstVisibleItemScrollOffset)
        }
        restoration.emulateSavedInstanceStateRestore()
        awaitAdvanceSnapshot(fixture)
        composeRule.onNodeWithTag("pocketAdvanceSongs", useUnmergedTree = true).assertDoesNotExist()
        composeRule.runOnIdle {
            assertNotSame(navigation, fixture.advanceNavigation)
            assertEquals(PocketAdvanceLcdMode.NowPlaying, fixture.advanceNavigation.lcdMode)
            assertEquals(PocketAdvanceBrowserPage.Songs, fixture.advanceNavigation.browserPage)
            assertEquals(supplied[12].stableUiKey(), fixture.advanceNavigation.selectedSongKey)
        }
        advanceControl("pocketAdvanceStart")
        composeRule.onNodeWithTag(advanceSongTag(supplied[12]), useUnmergedTree = true).assertIsSelected()
        composeRule.runOnIdle {
            assertEquals(viewport, fixture.advanceNavigation.songsListState.firstVisibleItemIndex to fixture.advanceNavigation.songsListState.firstVisibleItemScrollOffset)
            assertTrue(fixture.songSelections.isEmpty())
            assertTrue(fixture.calls.isEmpty()); assertTrue(fixture.seeks.isEmpty())
        }
    }

    @Test
    fun pocketAdvanceSongsAuthoritativeEmptyCannotRestoreAnOldDetachedViewportKey() {
        val fixture = advanceFixture()
        val supplied = advanceLibrarySongs(80)
        fixture.librarySongs.value = supplied
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { fixture.Content() }
        awaitAdvanceSnapshot(fixture)
        advanceControl("pocketAdvanceMini")
        openAdvanceSongs()
        composeRule.onNodeWithTag("pocketAdvanceSongs", useUnmergedTree = true).performScrollToIndex(30)
        composeRule.runOnIdle {
            assertEquals(30, fixture.advanceNavigation.songsListState.firstVisibleItemIndex)
            fixture.librarySongs.value = emptyList()
        }
        composeRule.onNodeWithTag("pocketAdvanceSongsEmpty", useUnmergedTree = true).assertExists()
        composeRule.runOnIdle { assertEquals(null, fixture.advanceNavigation.selectedSongKey) }
        restoration.emulateSavedInstanceStateRestore()
        awaitAdvanceSnapshot(fixture)
        composeRule.runOnIdle {
            assertEquals(PocketAdvanceLcdMode.NowPlaying, fixture.advanceNavigation.lcdMode)
            fixture.librarySongs.value = supplied
        }
        advanceControl("pocketAdvanceStart")
        composeRule.onNodeWithTag(advanceSongTag(supplied.first()), useUnmergedTree = true).assertIsSelected()
        composeRule.runOnIdle {
            assertEquals(0, fixture.advanceNavigation.songsListState.firstVisibleItemIndex)
            assertEquals(0, fixture.advanceNavigation.songsListState.firstVisibleItemScrollOffset)
            assertEquals(supplied.first().stableUiKey(), fixture.advanceNavigation.selectedSongKey)
            assertTrue(fixture.songSelections.isEmpty())
            assertTrue(fixture.calls.isEmpty()); assertTrue(fixture.seeks.isEmpty())
        }
    }

    @Test
    fun pocketAdvanceSongsPointerTapMovesOnceAndHoldRepeatsOnlyAfterItsDelay() {
        val fixture = advanceFixture()
        val supplied = advanceLibrarySongs(40)
        fixture.librarySongs.value = supplied
        composeRule.setContent { fixture.Content() }
        awaitAdvanceSnapshot(fixture)
        advanceControl("pocketAdvanceMini")
        openAdvanceSongs()
        advanceControl("pocketAdvanceDown")
        assertAdvanceSongIndex(fixture, supplied, 1)
        composeRule.mainClock.autoAdvance = false
        try {
            beginAdvanceHold("pocketAdvanceDown")
            assertAdvanceSongIndex(fixture, supplied, 2)
            composeRule.mainClock.advanceTimeBy(320)
            assertAdvanceSongIndex(fixture, supplied, 2)
            composeRule.mainClock.advanceTimeBy(80)
            assertAdvanceSongIndex(fixture, supplied, 3)
            composeRule.mainClock.advanceTimeBy(112)
            assertAdvanceSongIndex(fixture, supplied, 4)
            composeRule.mainClock.advanceTimeBy(112)
            assertAdvanceSongIndex(fixture, supplied, 5)
            composeRule.onRoot().performTouchInput { up() }
            composeRule.mainClock.advanceTimeBy(600)
            // Release must neither repeat nor produce clickable's second activation.
            assertAdvanceSongIndex(fixture, supplied, 5)
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun pocketAdvanceSongsHoldStopsOnReleaseCancellationAndPermanentPointerExit() {
        val fixture = advanceFixture()
        val supplied = advanceLibrarySongs(40)
        fixture.librarySongs.value = supplied
        composeRule.setContent { fixture.Content() }
        awaitAdvanceSnapshot(fixture)
        advanceControl("pocketAdvanceMini")
        openAdvanceSongs()
        composeRule.mainClock.autoAdvance = false
        try {
            for (ending in listOf("release", "cancel", "exit")) {
                composeRule.runOnIdle { fixture.advanceNavigation.selectSong(supplied.first().stableUiKey(), 0) }
                val target = beginAdvanceHold("pocketAdvanceDown")
                composeRule.mainClock.advanceTimeBy(400)
                assertAdvanceSongIndex(fixture, supplied, 2)
                composeRule.onRoot().performTouchInput {
                    when (ending) {
                        "release" -> up()
                        "cancel" -> cancel()
                        else -> moveTo(Offset(target.right + target.width, target.center.y))
                    }
                }
                composeRule.mainClock.advanceTimeBy(600)
                assertAdvanceSongIndex(fixture, supplied, 2)
                if (ending == "exit") {
                    composeRule.onRoot().performTouchInput { moveTo(target.center) }
                    composeRule.mainClock.advanceTimeBy(600)
                    assertAdvanceSongIndex(fixture, supplied, 2)
                    composeRule.onRoot().performTouchInput { up() }
                    composeRule.mainClock.advanceTimeByFrame()
                    assertAdvanceSongIndex(fixture, supplied, 2)
                }
            }
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun pocketAdvanceSongsRepeatClampsAtBothEndsWhileSeparateTapsStillWrap() {
        val fixture = advanceFixture()
        val supplied = advanceLibrarySongs(5)
        fixture.librarySongs.value = supplied
        composeRule.setContent { fixture.Content() }
        awaitAdvanceSnapshot(fixture)
        advanceControl("pocketAdvanceMini")
        openAdvanceSongs()
        composeRule.mainClock.autoAdvance = false
        try {
            composeRule.runOnIdle { fixture.advanceNavigation.selectSong(supplied[3].stableUiKey(), 3) }
            beginAdvanceHold("pocketAdvanceDown")
            assertAdvanceSongIndex(fixture, supplied, 4)
            composeRule.mainClock.advanceTimeBy(2_000)
            assertAdvanceSongIndex(fixture, supplied, 4)
            composeRule.onRoot().performTouchInput { up() }
            composeRule.mainClock.advanceTimeByFrame()
            advanceControl("pocketAdvanceDown")
            composeRule.mainClock.advanceTimeByFrame()
            assertAdvanceSongIndex(fixture, supplied, 0)
            composeRule.runOnIdle { fixture.advanceNavigation.selectSong(supplied[1].stableUiKey(), 1) }
            beginAdvanceHold("pocketAdvanceUp")
            assertAdvanceSongIndex(fixture, supplied, 0)
            composeRule.mainClock.advanceTimeBy(2_000)
            assertAdvanceSongIndex(fixture, supplied, 0)
            composeRule.onRoot().performTouchInput { up() }
            composeRule.mainClock.advanceTimeByFrame()
            advanceControl("pocketAdvanceUp")
            composeRule.mainClock.advanceTimeByFrame()
            assertAdvanceSongIndex(fixture, supplied, 4)
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun pocketAdvanceSongsHeldDirectionStopsWhenPageModeOrCompositionChanges() {
        val fixture = advanceFixture()
        val supplied = advanceLibrarySongs(40)
        val composed = mutableStateOf(true)
        fixture.librarySongs.value = supplied
        composeRule.setContent {
            if (composed.value) fixture.Content() else Box(Modifier.fillMaxSize().testTag("disposed-player"))
        }
        awaitAdvanceSnapshot(fixture)
        advanceControl("pocketAdvanceMini")
        openAdvanceSongs()
        val navigation = fixture.advanceNavigation
        composeRule.mainClock.autoAdvance = false
        try {
            beginAdvanceHold("pocketAdvanceDown")
            composeRule.runOnIdle { navigation.back() }
            composeRule.mainClock.advanceTimeBy(1_000)
            composeRule.runOnIdle {
                assertEquals(PocketAdvanceBrowserPage.Root, navigation.browserPage)
                assertEquals(supplied[1].stableUiKey(), navigation.selectedSongKey)
            }
            composeRule.onRoot().performTouchInput { up() }
            composeRule.runOnIdle { navigation.confirmSelection() }
            composeRule.mainClock.advanceTimeByFrame()
            beginAdvanceHold("pocketAdvanceDown")
            composeRule.runOnIdle { navigation.showNowPlaying() }
            composeRule.mainClock.advanceTimeBy(1_000)
            composeRule.runOnIdle {
                assertEquals(PocketAdvanceLcdMode.NowPlaying, navigation.lcdMode)
                assertEquals(supplied[2].stableUiKey(), navigation.selectedSongKey)
            }
            composeRule.onRoot().performTouchInput { up() }
            composeRule.runOnIdle { navigation.openBrowser() }
            composeRule.mainClock.advanceTimeByFrame()
            beginAdvanceHold("pocketAdvanceDown")
            composeRule.runOnIdle { composed.value = false }
            composeRule.mainClock.advanceTimeBy(1_000)
            composeRule.onNodeWithTag("disposed-player").assertExists()
            composeRule.runOnIdle {
                assertEquals(supplied[3].stableUiKey(), navigation.selectedSongKey)
                assertTrue(fixture.songSelections.isEmpty())
                assertTrue(fixture.calls.isEmpty()); assertTrue(fixture.seeks.isEmpty())
            }
            composeRule.onRoot().performTouchInput { up() }
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun pocketAdvanceRootAndNowPlayingDirectionHoldsRemainSingleOrdinaryClicks() {
        val fixture = advanceFixture()
        fixture.librarySongs.value = advanceLibrarySongs(20)
        composeRule.setContent { fixture.Content() }
        awaitAdvanceSnapshot(fixture)
        advanceControl("pocketAdvanceMini")
        advanceControl("pocketAdvanceStart")
        composeRule.mainClock.autoAdvance = false
        try {
            beginAdvanceHold("pocketAdvanceDown")
            composeRule.mainClock.advanceTimeBy(1_000)
            composeRule.runOnIdle {
                assertEquals(PocketAdvanceRootEntry.NowPlaying, fixture.advanceNavigation.selectedRootEntry)
            }
            composeRule.onRoot().performTouchInput { up() }
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.runOnIdle { assertEquals(PocketAdvanceRootEntry.Songs, fixture.advanceNavigation.selectedRootEntry) }
            composeRule.runOnIdle { fixture.advanceNavigation.showNowPlaying() }
            composeRule.mainClock.advanceTimeByFrame()
            for (tag in listOf("pocketAdvanceUp", "pocketAdvanceDown")) {
                val before = composeRule.runOnIdle { fixture.calls.toList() }
                beginAdvanceHold(tag)
                composeRule.mainClock.advanceTimeBy(1_000)
                composeRule.runOnIdle { assertEquals(before, fixture.calls) }
                composeRule.onRoot().performTouchInput { up() }
                composeRule.mainClock.advanceTimeByFrame()
            }
            composeRule.runOnIdle {
                assertEquals(listOf("shuffle", "repeat"), fixture.calls)
                assertTrue(fixture.songSelections.isEmpty()); assertTrue(fixture.seeks.isEmpty())
            }
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun pocketAdvanceSongsHeldConfirmAndBackControlsNeverRepeat() {
        val fixture = advanceFixture()
        val supplied = advanceLibrarySongs(20)
        fixture.librarySongs.value = supplied
        composeRule.setContent { fixture.Content() }
        awaitAdvanceSnapshot(fixture)
        advanceControl("pocketAdvanceMini")
        openAdvanceSongs()
        composeRule.mainClock.autoAdvance = false
        try {
            for (tag in listOf("pocketAdvancePlay", "pocketAdvanceNext", "pocketAdvancePrevious", "pocketAdvanceBack", "pocketAdvanceStart")) {
                val before = composeRule.runOnIdle { fixture.songSelections.size }
                beginAdvanceHold(tag)
                composeRule.mainClock.advanceTimeBy(1_000)
                composeRule.runOnIdle {
                    assertEquals(PocketAdvanceLcdMode.MusicBrowser, fixture.advanceNavigation.lcdMode)
                    assertEquals(PocketAdvanceBrowserPage.Songs, fixture.advanceNavigation.browserPage)
                    assertEquals(before, fixture.songSelections.size)
                    assertEquals(supplied.first().stableUiKey(), fixture.advanceNavigation.selectedSongKey)
                }
                composeRule.onRoot().performTouchInput { up() }
                composeRule.mainClock.advanceTimeByFrame()
                composeRule.runOnIdle {
                    assertEquals(before + if (tag == "pocketAdvancePlay" || tag == "pocketAdvanceNext") 1 else 0,
                        fixture.songSelections.size)
                    if (fixture.advanceNavigation.lcdMode == PocketAdvanceLcdMode.NowPlaying) fixture.advanceNavigation.openBrowser()
                    if (fixture.advanceNavigation.browserPage == PocketAdvanceBrowserPage.Root) fixture.advanceNavigation.confirmSelection()
                }
                composeRule.mainClock.advanceTimeByFrame()
            }
            composeRule.runOnIdle {
                assertEquals(listOf(supplied.first() to supplied, supplied.first() to supplied), fixture.songSelections)
                assertTrue(fixture.calls.isEmpty()); assertTrue(fixture.seeks.isEmpty())
            }
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun pocketAdvanceSongsConcurrentDirectionsCannotStartIndependentRepeatLoops() {
        val fixture = advanceFixture()
        val supplied = advanceLibrarySongs(40)
        fixture.librarySongs.value = supplied
        composeRule.setContent { fixture.Content() }
        awaitAdvanceSnapshot(fixture)
        advanceControl("pocketAdvanceMini")
        openAdvanceSongs()
        composeRule.mainClock.autoAdvance = false
        try {
            beginAdvanceHold("pocketAdvanceDown")
            assertAdvanceSongIndex(fixture, supplied, 1)
            val upTarget = composeRule.onNodeWithTag("pocketAdvanceUp", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            composeRule.onRoot().performTouchInput { down(pointerId = 1, position = upTarget.center) }
            composeRule.mainClock.advanceTimeByFrame()
            assertAdvanceSongIndex(fixture, supplied, 1)
            composeRule.mainClock.advanceTimeBy(600)
            assertAdvanceSongIndex(fixture, supplied, 1)
            // The second pointer must not inherit ownership when the original owner is lifted.
            composeRule.onRoot().performTouchInput { up(pointerId = 0) }
            composeRule.mainClock.advanceTimeBy(1_000)
            assertAdvanceSongIndex(fixture, supplied, 1)
            composeRule.onRoot().performTouchInput { up(pointerId = 1) }
            composeRule.mainClock.advanceTimeByFrame()
            beginAdvanceHold("pocketAdvanceDown")
            composeRule.mainClock.advanceTimeBy(400)
            assertAdvanceSongIndex(fixture, supplied, 3)
            composeRule.onRoot().performTouchInput { up() }
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun pocketAdvanceSongsOverlayFenceCancelsAHoldUntilAFreshPointerDown() {
        val fixture = advanceFixture()
        val supplied = advanceLibrarySongs(40)
        fixture.librarySongs.value = supplied
        composeRule.setContent { fixture.Content() }
        awaitAdvanceSnapshot(fixture)
        advanceControl("pocketAdvanceMini")
        openAdvanceSongs()
        composeRule.mainClock.autoAdvance = false
        try {
            beginAdvanceHold("pocketAdvanceDown")
            assertAdvanceSongIndex(fixture, supplied, 1)
            composeRule.runOnIdle { fixture.queueVisible.value = true }
            composeRule.mainClock.advanceTimeBy(1_000)
            assertAdvanceSongIndex(fixture, supplied, 1)
            composeRule.runOnIdle { fixture.queueVisible.value = false }
            composeRule.mainClock.advanceTimeBy(1_000)
            assertAdvanceSongIndex(fixture, supplied, 1)
            composeRule.onRoot().performTouchInput { up() }
            composeRule.mainClock.advanceTimeByFrame()
            assertAdvanceSongIndex(fixture, supplied, 1)
            beginAdvanceHold("pocketAdvanceDown")
            composeRule.mainClock.advanceTimeBy(400)
            assertAdvanceSongIndex(fixture, supplied, 3)
            composeRule.onRoot().performTouchInput { up() }
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun pocketAdvanceSongsHeldDirectionYieldsToHeaderReversalAndCompleteCollapse() {
        val fixture = advanceFixture()
        val supplied = advanceLibrarySongs(40)
        fixture.librarySongs.value = supplied
        composeRule.setContent { fixture.Content() }
        awaitAdvanceSnapshot(fixture)
        advanceControl("pocketAdvanceMini")
        val initial = awaitAdvanceSnapshot(fixture)
        openAdvanceSongs()
        composeRule.mainClock.autoAdvance = false
        try {
            beginAdvanceHold("pocketAdvanceDown")
            val header = composeRule.onNodeWithTag("pocketAdvanceHeader", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            composeRule.onRoot().performTouchInput {
                down(pointerId = 1, position = header.center)
                moveTo(pointerId = 1, position = header.center + Offset(0f, 48f))
            }
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.runOnIdle { assertTrue(fixture.morph.isDragging) }
            composeRule.onNodeWithTag("pocketAdvanceSongs", useUnmergedTree = true).assertDoesNotExist()
            composeRule.mainClock.advanceTimeBy(1_000)
            composeRule.runOnIdle { assertEquals(supplied[1].stableUiKey(), fixture.advanceNavigation.selectedSongKey) }
            composeRule.onRoot().performTouchInput {
                up(pointerId = 0)
                moveTo(pointerId = 1, position = header.center - Offset(0f, 24f))
                up(pointerId = 1)
            }
            composeRule.mainClock.advanceTimeBy(600)
            assertAdvanceSongIndex(fixture, supplied, 1)
            composeRule.runOnIdle { assertEquals(1f, fixture.morph.progress, 0f) }
            beginAdvanceHold("pocketAdvanceDown")
            collapseAdvanceThroughHeader()
            composeRule.mainClock.advanceTimeBy(1_000)
            composeRule.runOnIdle {
                assertTrue(fixture.morph.isCollapsedAndIdle)
                assertEquals(PocketAdvanceLcdMode.NowPlaying, fixture.advanceNavigation.lcdMode)
                assertEquals(supplied[2].stableUiKey(), fixture.advanceNavigation.selectedSongKey)
            }
            composeRule.onRoot().performTouchInput { up() }
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
        advanceControl("pocketAdvanceMini")
        val reopened = awaitAdvanceSnapshot(fixture)
        assertSame(initial.owner, reopened.owner)
        assertEquals(initial.expanded, reopened.expanded)
        advanceControl("pocketAdvanceStart")
        assertAdvanceSongIndex(fixture, supplied, 2)
        composeRule.runOnIdle { assertFalse(fixture.lyrics.lyricsComposed) }
    }

    @Test
    fun pocketAdvanceSongsAdjacentEdgeNavigationRevealsOnlyTheMissingRowPixels() {
        val fixture = advanceFixture()
        val supplied = advanceLibrarySongs(80)
        fixture.librarySongs.value = supplied
        composeRule.setContent { fixture.Content() }
        awaitAdvanceSnapshot(fixture)
        advanceControl("pocketAdvanceMini")
        openAdvanceSongs()
        val navigation = fixture.advanceNavigation
        val listState = navigation.songsListState
        composeRule.onNodeWithTag("pocketAdvanceSongs", useUnmergedTree = true).performScrollToIndex(20)
        composeRule.runOnIdle { navigation.selectSong(supplied[20].stableUiKey(), 20) }
        val unchanged = composeRule.runOnIdle { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
        advanceControl("pocketAdvanceDown")
        composeRule.runOnIdle {
            assertEquals("A fully visible adjacent row must not move the viewport", unchanged,
                listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset)
        }
        val bottom = composeRule.runOnIdle {
            val info = listState.layoutInfo
            info.visibleItemsInfo.last { it.offset >= info.viewportStartOffset && it.offset + it.size <= info.viewportEndOffset }.index
        }
        composeRule.runOnIdle { navigation.selectSong(supplied[bottom].stableUiKey(), bottom) }
        var selected = bottom
        for ((tag, step) in listOf("pocketAdvanceDown" to 1, "pocketAdvanceUp" to -1)) {
            if (step < 0) {
                selected = composeRule.runOnIdle {
                    val info = listState.layoutInfo
                    info.visibleItemsInfo.first { it.offset >= info.viewportStartOffset }.index
                }
                composeRule.runOnIdle { navigation.selectSong(supplied[selected].stableUiKey(), selected) }
            }
            repeat(4) {
                val before = composeRule.runOnIdle {
                    val info = listState.layoutInfo
                    val stride = info.visibleItemsInfo.first().size + info.mainAxisItemSpacing
                    val nextIndex = selected + step
                    val anchor = info.visibleItemsInfo.firstOrNull { it.index == nextIndex }
                        ?: if (step > 0) info.visibleItemsInfo.last() else info.visibleItemsInfo.first()
                    val start = anchor.offset + (nextIndex - anchor.index) * stride
                    val required = when {
                        start < info.viewportStartOffset -> start - info.viewportStartOffset
                        start + anchor.size > info.viewportEndOffset -> start + anchor.size - info.viewportEndOffset
                        else -> 0
                    }
                    Triple(listState.firstVisibleItemIndex * stride + listState.firstVisibleItemScrollOffset, stride, required)
                }
                advanceControl(tag)
                selected += step
                composeRule.onNodeWithTag(advanceSongTag(supplied[selected]), useUnmergedTree = true).assertIsSelected().assertIsDisplayed()
                composeRule.runOnIdle {
                    val info = listState.layoutInfo
                    val after = listState.firstVisibleItemIndex * before.second + listState.firstVisibleItemScrollOffset
                    val displacement = after - before.first
                    assertEquals("Reveal only the row pixels outside the viewport", before.third.toFloat(), displacement.toFloat(), 1f)
                    assertTrue("An adjacent edge reveal must move in the selection direction: $displacement", displacement * step > 0)
                    assertTrue("An adjacent move must never jump a page: $displacement, stride=" + before.second,
                        displacement in (-before.second - 1)..(before.second + 1))
                    val row = info.visibleItemsInfo.single { it.index == selected }
                    assertTrue("The entire selected row must be visible", row.offset >= info.viewportStartOffset &&
                        row.offset + row.size <= info.viewportEndOffset)
                }
            }
        }
        composeRule.runOnIdle {
            assertFalse(fixture.lyrics.lyricsComposed); assertFalse(fixture.morph.isDragging)
            assertTrue(fixture.songSelections.isEmpty()); assertTrue(fixture.calls.isEmpty()); assertTrue(fixture.seeks.isEmpty())
        }
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
            fixture.tabletLandscapeTheme.value = TabletLandscapeTheme.NORMAL_PLAYER
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
    fun productionLandscapeSelectionKeepsNormalThemeIndependentAcrossCollapsedAndExpandedFallbacks() {
        val fixture = advanceFixture().apply { tabletLandscapeTheme.value = TabletLandscapeTheme.NORMAL_PLAYER }
        composeRule.setContent { fixture.Content() }
        composeRule.runOnIdle {
            assertEquals(PlayerPresentationIdentity.Existing(PlayerTheme.CLASSIC_WHEEL), fixture.resolvedIdentity)
            assertTrue(fixture.morph.isCollapsedAndIdle)
            fixture.tabletLandscapeTheme.value = TabletLandscapeTheme.POCKET_ADVANCE
        }
        awaitAdvanceSnapshot(fixture)
        val controller = fixture.morph
        val song = fixture.current.value
        val playbackPosition = fixture.progress.value
        composeRule.runOnIdle { assertTrue(controller.isCollapsedAndIdle) }
        advanceControl("pocketAdvanceMini")
        val beforePrimaryChange = awaitAdvanceSnapshot(fixture)
        val epoch = fixture.presentationEpoch
        composeRule.runOnIdle { fixture.theme.value = PlayerTheme.POCKET_CASSETTE }
        val afterPrimaryChange = awaitAdvanceSnapshot(fixture)
        composeRule.runOnIdle {
            assertEquals(PlayerPresentationIdentity.PocketAdvance, fixture.resolvedIdentity)
            assertSame(beforePrimaryChange.owner, afterPrimaryChange.owner)
            assertEquals(epoch, fixture.presentationEpoch)
            fixture.viewport.value = DpSize(720.dp, 1180.dp)
        }
        composeRule.runOnIdle {
            assertEquals(PlayerPresentationIdentity.Existing(PlayerTheme.POCKET_CASSETTE), fixture.resolvedIdentity)
            assertEquals(TabletLandscapeTheme.POCKET_ADVANCE, fixture.tabletLandscapeTheme.value)
            assertSame(controller, fixture.morph)
            assertEquals(PlayerPresentation.Expanded, controller.settledPresentation)
            fixture.viewport.value = DpSize(1200.dp, 720.dp)
        }
        awaitAdvanceSnapshot(fixture)
        composeRule.runOnIdle { fixture.tabletLandscapeTheme.value = TabletLandscapeTheme.NORMAL_PLAYER }
        composeRule.runOnIdle {
            assertEquals(PlayerPresentationIdentity.Existing(PlayerTheme.POCKET_CASSETTE), fixture.resolvedIdentity)
            assertSame(controller, fixture.morph)
            assertEquals(1f, controller.progress, 0f)
            fixture.tabletLandscapeTheme.value = TabletLandscapeTheme.POCKET_ADVANCE
        }
        awaitAdvanceSnapshot(fixture)
        collapseAdvanceThroughHeader()
        composeRule.runOnIdle {
            assertTrue(controller.isCollapsedAndIdle)
            fixture.tabletLandscapeTheme.value = TabletLandscapeTheme.NORMAL_PLAYER
        }
        composeRule.runOnIdle {
            assertEquals(PlayerPresentationIdentity.Existing(PlayerTheme.POCKET_CASSETTE), fixture.resolvedIdentity)
            assertTrue(controller.isCollapsedAndIdle)
            fixture.tabletLandscapeTheme.value = TabletLandscapeTheme.POCKET_ADVANCE
        }
        awaitAdvanceSnapshot(fixture)
        composeRule.runOnIdle {
            assertTrue(controller.isCollapsedAndIdle)
            assertSame(controller, fixture.morph)
            assertEquals(PlayerTheme.POCKET_CASSETTE, fixture.theme.value)
            assertEquals(song, fixture.current.value)
            assertEquals(playbackPosition, fixture.progress.value)
            assertTrue(fixture.calls.isEmpty()); assertTrue(fixture.seeks.isEmpty()); assertTrue(fixture.songSelections.isEmpty())
        }
    }

    @Test
    fun loadedLandscapeSelectionStartsWithPortraitFallbackAndSurvivesActivityRestoration() {
        val loaded = decodeAppPreferences(mutablePreferencesOf(
            stringPreferencesKey("selected_player_theme") to PlayerTheme.POCKET_CASSETTE.id,
            stringPreferencesKey("tablet_landscape_theme") to TabletLandscapeTheme.POCKET_ADVANCE.id
        ))
        assertTrue(loaded.isLoaded)
        val fixture = advanceFixture().apply {
            theme.value = loaded.selectedPlayerTheme
            tabletLandscapeTheme.value = loaded.tabletLandscapeTheme
            viewport.value = DpSize(720.dp, 1180.dp)
        }
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { fixture.Content() }
        composeRule.onNodeWithTag("pocketAdvanceExpanded", useUnmergedTree = true).assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(PlayerPresentationIdentity.Existing(PlayerTheme.POCKET_CASSETTE), fixture.resolvedIdentity)
            assertTrue(fixture.morph.isCollapsedAndIdle)
            fixture.morph.expand()
        }
        composeRule.runOnIdle { assertEquals(PlayerPresentation.Expanded, fixture.morph.settledPresentation) }
        val outgoing = fixture.morph
        restoration.emulateSavedInstanceStateRestore()
        composeRule.runOnIdle {
            assertNotSame(outgoing, fixture.morph)
            assertEquals(PlayerPresentationIdentity.Existing(PlayerTheme.POCKET_CASSETTE), fixture.resolvedIdentity)
            assertEquals(PlayerPresentation.Expanded, fixture.morph.settledPresentation)
            assertEquals(TabletLandscapeTheme.POCKET_ADVANCE, fixture.tabletLandscapeTheme.value)
            fixture.viewport.value = DpSize(1200.dp, 720.dp)
        }
        awaitAdvanceSnapshot(fixture)
        composeRule.runOnIdle {
            assertEquals(PlayerPresentationIdentity.PocketAdvance, fixture.resolvedIdentity)
            assertEquals(PlayerTheme.POCKET_CASSETTE, fixture.theme.value)
            assertEquals(1f, fixture.morph.progress, 0f)
            assertEquals(PocketAdvanceLcdMode.NowPlaying, fixture.advanceNavigation.lcdMode)
            assertTrue(fixture.calls.isEmpty()); assertTrue(fixture.seeks.isEmpty()); assertTrue(fixture.songSelections.isEmpty())
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
        collapseAdvanceThroughHeader()
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

    @OptIn(ExperimentalTestApi::class)
    private fun assertAdvancePalettePixel(tag: String, expected: Color, pointInRoot: Offset) {
        val node = composeRule.onNodeWithTag(tag, useUnmergedTree = true)
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val pixels = node.captureToImage().toPixelMap()
        val x = (pointInRoot.x - bounds.left).toInt().coerceIn(0, pixels.width - 1)
        val y = (pointInRoot.y - bounds.top).toInt().coerceIn(0, pixels.height - 1)
        val actual = pixels[x, y]
        assertEquals("Red in $tag", expected.red, actual.red, .06f)
        assertEquals("Green in $tag", expected.green, actual.green, .06f)
        assertEquals("Blue in $tag", expected.blue, actual.blue, .06f)
    }

    private fun assertAdvanceSelectedRowColor(tag: String, expected: Color) {
        val bounds = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertAdvancePalettePixel(tag, expected, Offset(bounds.left + 1f, bounds.center.y))
    }

    private fun assertAdvancePaletteFraction(tag: String, expected: Color, x: Float, y: Float) {
        val bounds = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertAdvancePalettePixel(tag, expected, Offset(bounds.left + bounds.width * x, bounds.top + bounds.height * y))
    }

    private fun assertAdvanceTextColor(tag: String, expected: Color) {
        // At the fixture's 0.25 density, glyphs may contain only antialiased pixels. Assert the
        // actual displayed Text's current paint color exactly, independently of glyph coverage.
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { getLayout ->
                assertTrue("Expected a current text layout in $tag", getLayout(layouts))
            }
        assertEquals("Expected one text layout in $tag", 1, layouts.size)
        assertEquals("Expected the current text color in $tag", expected, layouts.single().layoutInput.style.color)
    }

    private data class AdvanceSnapshot(
        val owner: PocketAdvancePlayerMorphBounds,
        val frame: PlayerMorphLayoutFrame,
        val mini: PocketAdvanceMorphGeometry,
        val expanded: PocketAdvanceMorphGeometry
    )

    private fun assertAdvanceBrowseEdgeReveal(
        listTag: String,
        listState: LazyListState,
        rowTag: (Int) -> String,
        select: (Int) -> Unit
    ) {
        composeRule.onNodeWithTag(listTag, useUnmergedTree = true).performScrollToIndex(10)
        composeRule.runOnIdle { select(10) }
        val unchanged = composeRule.runOnIdle { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
        advanceControl("pocketAdvanceDown")
        composeRule.onNodeWithTag(rowTag(11), useUnmergedTree = true).assertIsSelected().assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals("A fully visible adjacent row must not move $listTag", unchanged,
                listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset)
        }
        val bottom = composeRule.runOnIdle {
            val info = listState.layoutInfo
            info.visibleItemsInfo.last { it.offset >= info.viewportStartOffset && it.offset + it.size <= info.viewportEndOffset }.index
        }
        composeRule.runOnIdle { select(bottom) }
        var selected = bottom
        for ((tag, step) in listOf("pocketAdvanceDown" to 1, "pocketAdvanceUp" to -1)) {
            if (step < 0) {
                selected = composeRule.runOnIdle {
                    val info = listState.layoutInfo
                    info.visibleItemsInfo.first { it.offset >= info.viewportStartOffset }.index
                }
                composeRule.runOnIdle { select(selected) }
            }
            repeat(4) {
                val before = composeRule.runOnIdle {
                    val info = listState.layoutInfo
                    val stride = info.visibleItemsInfo.first().size + info.mainAxisItemSpacing
                    val nextIndex = selected + step
                    val anchor = info.visibleItemsInfo.firstOrNull { it.index == nextIndex }
                        ?: if (step > 0) info.visibleItemsInfo.last() else info.visibleItemsInfo.first()
                    val start = anchor.offset + (nextIndex - anchor.index) * stride
                    val required = when {
                        start < info.viewportStartOffset -> start - info.viewportStartOffset
                        start + anchor.size > info.viewportEndOffset -> start + anchor.size - info.viewportEndOffset
                        else -> 0
                    }
                    Triple(listState.firstVisibleItemIndex * stride + listState.firstVisibleItemScrollOffset, stride, required)
                }
                advanceControl(tag)
                selected += step
                composeRule.onNodeWithTag(rowTag(selected), useUnmergedTree = true).assertIsSelected().assertIsDisplayed()
                composeRule.runOnIdle {
                    val info = listState.layoutInfo
                    val after = listState.firstVisibleItemIndex * before.second + listState.firstVisibleItemScrollOffset
                    val displacement = after - before.first
                    assertEquals("Reveal only row pixels outside $listTag", before.third.toFloat(), displacement.toFloat(), 1f)
                    assertTrue("An edge reveal must move in the selection direction", displacement * step > 0)
                    assertTrue("An adjacent move must never jump a page", displacement in (-before.second - 1)..(before.second + 1))
                    val row = info.visibleItemsInfo.single { it.index == selected }
                    assertTrue("The whole selected row must be visible in $listTag", row.offset >= info.viewportStartOffset &&
                        row.offset + row.size <= info.viewportEndOffset)
                }
            }
        }
    }

    private fun advanceAlbumGroups(songs: List<Song>,
        sort: LibrarySortState = LibrarySortState(LibrarySortOption.TITLE, LibrarySortDirection.ASCENDING)) =
        sortedLibraryAlbumGroups(songs, sort)

    private fun advanceAlbumLibrary(albumCount: Int, tracksPerAlbum: Int, multiDisc: Boolean = false): List<Song> =
        (1..albumCount).flatMap { album -> (1..tracksPerAlbum).map { track ->
            val id = ((album - 1) * tracksPerAlbum + track).toLong()
            val disc = if (multiDisc && album == 1 && track > (tracksPerAlbum + 1) / 2) 2 else 1
            val folder = "/music/archive/record-$album" + if (multiDisc && album == 1) "/disc-$disc" else ""
            Song(id, "Archive track $id", "Performer $album", "Record " + album.toString().padStart(2, '0'),
                if (disc == 2) track - (tracksPerAlbum + 1) / 2 else track, 180_000L,
                Uri.parse("content://media/external/audio/media/$id"), "$folder/$id.flac", folder, null,
                albumArtist = "Archive Artist $album", volumeName = "external_primary", discNumber = disc)
        } }

    private fun albumRowTag(album: LibraryAlbumGroup) = "pocketAdvanceAlbumRow:" + album.key
    private fun albumArtTag(album: LibraryAlbumGroup) = "pocketAdvanceAlbumArtwork:" + album.key
    private fun albumTrackTag(song: Song) = "pocketAdvanceAlbumTrack:" + song.stableUiKey()
    private fun openAdvanceAlbums() {
        advanceControl("pocketAdvanceStart")
        repeat(3) { advanceControl("pocketAdvanceDown") }
        advanceControl("pocketAdvanceNext")
    }

    private fun advanceArtistGroups(
        songs: List<Song>,
        sortState: LibrarySortState = LibrarySortState(LibrarySortOption.NAME, LibrarySortDirection.ASCENDING)
    ): List<LibraryArtistGroup> = sortedLibraryArtistGroups(songs, sortState)

    private fun advanceArtistTag(group: LibraryArtistGroup): String = "pocketAdvanceArtistRow:" + group.key
    private fun advanceArtistSongTag(song: Song): String = "pocketAdvanceArtistSong:" + song.stableUiKey()

    private fun advanceArtistLibrary(artistCount: Int, songsPerArtist: Int): List<Song> =
        (1..artistCount).flatMap { artistIndex ->
            (1..songsPerArtist).map { track ->
                val id = ((artistIndex - 1) * songsPerArtist + track).toLong()
                Song(id, "Track " + (songsPerArtist - track).toString().padStart(2, '0'),
                    "Artist " + artistIndex.toString().padStart(2, '0'), "Album", track, 180_000L,
                    Uri.parse("content://media/external/audio/media/$id"), "/music/session3d/$id.mp3", "/music/session3d", null)
            }
        }

    private fun openAdvanceArtists() {
        advanceControl("pocketAdvanceStart")
        advanceControl("pocketAdvanceDown")
        advanceControl("pocketAdvanceDown")
        advanceControl("pocketAdvanceNext")
        composeRule.onNodeWithTag("pocketAdvanceArtists", useUnmergedTree = true).assertExists()
    }

    private fun beginAdvanceHold(tag: String): Rect {
        // The clock may have been paused immediately after a click changed Root to Songs.
        // Install that page's pointer handler before down; a later frame cannot replay the event.
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        val target = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        composeRule.onRoot().performTouchInput { down(target.center) }
        // Establish the repeat coroutine's first frame before advancing its delay.
        composeRule.mainClock.advanceTimeByFrame()
        return target
    }

    private fun assertAdvanceSongIndex(fixture: Fixture, songs: List<Song>, index: Int) {
        composeRule.runOnIdle {
            assertEquals(PocketAdvanceBrowserPage.Songs, fixture.advanceNavigation.browserPage)
            assertEquals(songs[index].stableUiKey(), fixture.advanceNavigation.selectedSongKey)
            assertEquals(index, fixture.advanceNavigation.selectedSongIndex(songs.map(Song::stableUiKey)))
            assertTrue(fixture.songSelections.isEmpty())
            assertTrue(fixture.calls.isEmpty()); assertTrue(fixture.seeks.isEmpty())
        }
    }

    private fun advanceSongTag(song: Song): String = "pocketAdvanceSong:${song.stableUiKey()}"

    private fun advanceLibrarySongs(count: Int): List<Song> = (1..count).map { index ->
        Song(index.toLong(), "Song ${count - index}", "Artist $index", "Album", index, 180_000L,
            Uri.parse("content://media/external/audio/media/$index"), "/music/session3c/$index.mp3", "/music/session3c", null)
    }

    private fun openAdvanceSongs() {
        advanceControl("pocketAdvanceStart")
        advanceControl("pocketAdvanceDown")
        advanceControl("pocketAdvanceNext")
    }

    private fun advanceControl(tag: String) {
        composeRule.onNodeWithTag(tag).performTouchInput { click() }
    }

    private fun collapseAdvanceThroughHeader() {
        val action = composeRule.onNodeWithTag("pocketAdvanceHeader", useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.CustomActions].single { it.label == text(R.string.player_collapse) }
        composeRule.runOnIdle { assertTrue(action.action()) }
    }

    private fun advanceFixture() = tabletFixture(PlayerPresentation.Collapsed).apply {
        theme.value = PlayerTheme.CLASSIC_WHEEL
        deviceKind.value = PlayerDeviceKind.TABLET
        tabletLandscapeTheme.value = TabletLandscapeTheme.POCKET_ADVANCE
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

    @Test fun horizonRuntimeDispatchesMatchedEndpointsAndCanonicalPlaybackThroughTheProductionSlot() {
        val fixture = horizonFixture()
        composeRule.setContent { fixture.Content() }
        awaitHorizon(fixture)
        composeRule.onNodeWithTag("pocketHorizonMiniPlayPause").performClick()
        composeRule.onNodeWithTag("pocketHorizonMiniExpand").performClick()
        composeRule.runOnIdle { assertEquals(PlayerPresentation.Expanded, fixture.morph.settledPresentation) }
        composeRule.onNodeWithTag("pocketHorizonPlayPause").performClick()
        composeRule.onNodeWithTag("pocketHorizonCategorySongs").performClick()
        composeRule.onNodeWithTag("pocketHorizonSong:" + fixture.songs.first().stableUiKey()).performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("play-pause", "play-pause"), fixture.calls)
            assertEquals(fixture.songs.first(), fixture.songSelections.single().first)
            assertEquals(fixture.songs, fixture.songSelections.single().second)
            assertEquals(fixture.songs[1], fixture.current.value)
            assertEquals(PocketHorizonCategory.NowPlaying, fixture.horizonNavigation.category)
        }
    }

    @Test fun horizonPremeasurementSurvivesBrowsingAndReversalUntilAnAuthoritativeCollapse() {
        val fixture = horizonFixture(PlayerPresentation.Expanded)
        composeRule.setContent { fixture.Content() }
        val before = awaitHorizon(fixture)
        composeRule.onNodeWithTag("pocketHorizonCategorySongs").performClick()
        val browsing = awaitHorizon(fixture)
        assertEquals(before.pair.expanded.artwork, browsing.pair.expanded.artwork)
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle { fixture.morph.beginDragWithRange(200f); fixture.morph.updateProgressFromDrag(.45f) }
        composeRule.mainClock.advanceTimeByFrame()
        val during = awaitHorizon(fixture)
        assertEquals(browsing.pair, during.pair) // Animated visuals never feed back into anchors.
        val p = composeRule.runOnIdle { fixture.morph.progress }
        val origin = during.pair.frame.originInRoot
        val expected = during.pair.interpolate(p).artwork.translate(origin.x, origin.y)
        val artwork = composeRule.onNodeWithTag("pocketHorizonMorphArtwork", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertEquals(expected.left, artwork.left, 1f)
        assertEquals(expected.top, artwork.top, 1f)
        composeRule.runOnIdle {
            assertEquals(PocketHorizonCategory.Songs, fixture.horizonNavigation.category)
            fixture.morph.cancelDrag()
        }
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.mainClock.autoAdvance = true
        composeRule.runOnIdle {
            assertEquals(PlayerPresentation.Expanded, fixture.morph.settledPresentation)
            assertEquals(PocketHorizonCategory.Songs, fixture.horizonNavigation.category)
            fixture.morph.collapse()
        }
        composeRule.runOnIdle { assertEquals(PocketHorizonCategory.NowPlaying, fixture.horizonNavigation.category) }
        awaitHorizon(fixture)
        composeRule.onNodeWithTag("pocketHorizonMiniExpand").performClick()
        composeRule.runOnIdle { assertEquals(PocketHorizonCategory.NowPlaying, fixture.horizonNavigation.category) }
    }

    @Test fun horizonDedicatedGesturesExpandAndCollapseWithoutPlaybackOrSeekCommands() {
        val fixture = horizonFixture()
        composeRule.setContent { fixture.Content() }
        awaitHorizon(fixture)
        composeRule.onNodeWithTag("pocketHorizonMiniExpand").performTouchInput {
            swipe(center, center + Offset(0f, -180f), 350)
        }
        composeRule.runOnIdle { assertEquals(PlayerPresentation.Expanded, fixture.morph.settledPresentation) }
        awaitHorizon(fixture)
        composeRule.onNodeWithTag("pocketHorizonCollapseRegion", useUnmergedTree = true).performTouchInput {
            swipe(center, center + Offset(0f, 180f), 350)
        }
        composeRule.runOnIdle {
            assertEquals(PlayerPresentation.Collapsed, fixture.morph.settledPresentation)
            assertTrue(fixture.calls.isEmpty()); assertTrue(fixture.seeks.isEmpty())
        }
    }

    @Test fun horizonSeekAndListScrollingHaveNoMorphOwnershipAndOverlayRetiresLateInput() {
        val fixture = horizonFixture(PlayerPresentation.Expanded)
        fixture.librarySongs.value = (1L..80L).map { id -> fixture.songs.first().copy(id = id,
            title = "Track " + id.toString().padStart(3, '0'),
            uri = Uri.parse("content://media/external/audio/media/$id"), filePath = "/music/$id.mp3") }
        composeRule.setContent { fixture.Content() }
        awaitHorizon(fixture)
        composeRule.onNodeWithTag("pocketHorizonSeek").performSemanticsAction(SemanticsActions.SetProgress) { it(70_000f) }
        val stale = composeRule.onNodeWithTag("pocketHorizonPlayPause").fetchSemanticsNode()
            .config[SemanticsActions.OnClick].action!!
        composeRule.onNodeWithTag("pocketHorizonCategorySongs").performClick()
        composeRule.onNodeWithTag("pocketHorizonCategorySongs").assertIsSelected()
        // The expanded overlay surface merges descendant semantics. Its accessible owner retains
        // the lazy scroll action, while the list's own test tag is in the unmerged tree.
        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)).assertIsDisplayed()
        composeRule.onAllNodesWithTag("pocketHorizonLibraryList", useUnmergedTree = true).assertCountEquals(1)
        val libraryList = composeRule.onNodeWithTag("pocketHorizonLibraryList", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(libraryList.fetchSemanticsNode().config.contains(SemanticsActions.ScrollToIndex))
        assertTrue(libraryList.fetchSemanticsNode().config.contains(SemanticsActions.ScrollBy))
        libraryList.performScrollToIndex(40)
        libraryList.performTouchInput { swipeUp() }
        composeRule.runOnIdle {
            assertEquals(PlayerPresentation.Expanded, fixture.morph.settledPresentation)
            assertEquals(PlayerPresentation.Expanded, fixture.morph.targetPresentation)
            assertFalse(fixture.morph.isDragging)
            assertFalse(fixture.morph.isAnimating)
            assertEquals(1f, fixture.morph.progress, 0f)
            assertTrue(fixture.horizonNavigation.anchor(PocketHorizonList.Songs).index >= 40)
            assertTrue(fixture.songSelections.isEmpty())
            assertEquals(listOf(70_000), fixture.seeks)
            fixture.queueVisible.value = true
        }
        composeRule.runOnIdle { stale(); assertTrue(fixture.calls.isEmpty()) }
        composeRule.runOnIdle { fixture.queueVisible.value = false; fixture.horizonNavigation.showNowPlaying() }
        composeRule.onNodeWithTag("pocketHorizonQueue").performClick()
        composeRule.onNodeWithTag("pocketHorizonMore").performClick()
        composeRule.runOnIdle { assertEquals(listOf("queue", "more"), fixture.calls) }
    }

    @Test fun horizonRealBoundariesSettleDragsRetireEpochsAndPreserveFallback() {
        val fixture = horizonFixture(PlayerPresentation.Expanded)
        composeRule.setContent { fixture.Content() }
        val old = awaitHorizon(fixture)
        val retiredAction = composeRule.onNodeWithTag("pocketHorizonPlayPause").fetchSemanticsNode()
            .config[SemanticsActions.OnClick].action!!
        composeRule.runOnIdle {
            fixture.horizonNavigation.openAlbum("/music")
            fixture.morph.beginDragWithRange(200f); fixture.morph.dragBy(70f)
            fixture.controlledLandscapeIdentity.value = PlayerPresentationIdentity.PocketAdvance
        }
        composeRule.runOnIdle {
            assertEquals(PlayerPresentationIdentity.PocketAdvance, fixture.resolvedIdentity)
            assertEquals(PlayerPresentation.Expanded, fixture.morph.settledPresentation)
            assertFalse(fixture.morph.isDragging)
            assertEquals(PocketHorizonCategory.NowPlaying, fixture.horizonNavigation.category)
            assertNull(old.mini.geometry())
            retiredAction()
            assertTrue(fixture.calls.isEmpty())
            fixture.controlledLandscapeIdentity.value = PlayerPresentationIdentity.PocketHorizon
        }
        awaitHorizon(fixture)
        val epoch = fixture.presentationEpoch
        composeRule.runOnIdle { fixture.hostStartInset.value = 24.dp; fixture.fontScale.value = 2f }
        awaitHorizon(fixture)
        composeRule.runOnIdle {
            assertTrue(fixture.presentationEpoch > epoch)
            fixture.viewport.value = DpSize(600.dp, 400.dp)
        }
        composeRule.runOnIdle {
            assertEquals(PlayerPresentationIdentity.Existing(fixture.theme.value), fixture.resolvedIdentity)
            assertFalse(fixture.morph.isDragging)
            fixture.viewport.value = DpSize(1200.dp, 900.dp)
        }
        awaitHorizon(fixture)
    }

    @Test fun horizonRestoresExpandedDetailsAndPremeasurementNeverConfirmsTemporaryRemoval() {
        val fixture = horizonFixture(PlayerPresentation.Expanded)
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { fixture.Content() }
        awaitHorizon(fixture)
        composeRule.runOnIdle { fixture.horizonNavigation.openAlbum("/music") }
        awaitHorizon(fixture)
        restoration.emulateSavedInstanceStateRestore()
        awaitHorizon(fixture)
        composeRule.runOnIdle {
            assertEquals(PlayerPresentation.Expanded, fixture.morph.settledPresentation)
            assertEquals("/music", fixture.horizonNavigation.albumKey)
            fixture.libraryReady.value = false; fixture.librarySongs.value = emptyList()
        }
        awaitHorizon(fixture)
        composeRule.runOnIdle {
            assertEquals("/music", fixture.horizonNavigation.albumKey)
            fixture.libraryReady.value = true; fixture.librarySongs.value = fixture.songs
        }
        composeRule.onNodeWithTag("pocketHorizonLibraryBack").performClick()
        composeRule.runOnIdle { assertNull(fixture.horizonNavigation.albumKey) }
    }

    @Test fun horizonProgressAndStructuralReplacementDuringMotionDoNotReuseStaleScopes() {
        val fixture = horizonFixture(PlayerPresentation.Expanded)
        composeRule.setContent { fixture.Content() }
        val originalSong = requireNotNull(fixture.current.value)
        val before = awaitHorizon(fixture)
        composeRule.runOnIdle { fixture.progress.value = fixture.progress.value.copy(currentPosition = 40_000) }
        val tick = awaitHorizon(fixture)
        assertSame(before.mini, tick.mini); assertSame(before.expanded, tick.expanded)
        composeRule.mainClock.autoAdvance = false
        try {
            composeRule.runOnIdle { fixture.morph.beginDragWithRange(200f); fixture.morph.updateProgressFromDrag(.6f) }
            composeRule.mainClock.advanceTimeByFrame()
            fun replaceStructuralContent(song: Song, outgoing: HorizonSnapshot): HorizonSnapshot {
                composeRule.runOnIdle { fixture.current.value = song }
                // runOnIdle does not drive recomposition with autoAdvance disabled. Apply the
                // structural change on the controlled frame before awaiting its actual placement.
                composeRule.mainClock.advanceTimeByFrame()
                val incoming = awaitHorizon(fixture)
                assertNotSame(outgoing.mini, incoming.mini)
                assertNotSame(outgoing.expanded, incoming.expanded)
                assertSame(outgoing.mini.owner, incoming.mini.owner)
                assertSame(fixture.endpoints, incoming.expanded.owner)
                assertEquals(fixture.endpoints.layoutFrame, incoming.mini.frame)
                assertEquals(incoming.mini.frame, incoming.expanded.frame)
                assertEquals(incoming.mini.frame, incoming.pair.frame)
                val expected = pocketHorizonMeasurementKey(song, fixture.playerLayoutDirection)
                assertEquals(expected, incoming.mini.contentKey)
                assertEquals(expected, incoming.expanded.contentKey)
                composeRule.runOnIdle {
                    assertNull(outgoing.mini.geometry())
                    assertNull(outgoing.expanded.geometry())
                    assertNull(resolvePocketHorizonEndpointPair(fixture.endpoints, outgoing.mini, outgoing.expanded))
                    assertNull(resolvePocketHorizonEndpointPair(fixture.endpoints, outgoing.mini, incoming.expanded))
                    assertNull(resolvePocketHorizonEndpointPair(fixture.endpoints, incoming.mini, outgoing.expanded))
                    assertEquals(.6f, fixture.morph.progress, .001f)
                    assertTrue(fixture.morph.isDragging)
                }
                return incoming
            }
            val metadata = replaceStructuralContent(originalSong.copy(title = "Changed during transition"), before)
            val artwork = replaceStructuralContent(requireNotNull(fixture.current.value).copy(
                albumArtUri = Uri.parse("content://fixture/artwork/replacement")), metadata)
            val revision = replaceStructuralContent(requireNotNull(fixture.current.value).copy(
                artworkEnrichmentVersion = originalSong.artworkEnrichmentVersion + 1), artwork)
            val track = replaceStructuralContent(fixture.songs.first(), revision)
            val restored = replaceStructuralContent(originalSong, track)
            assertNotSame(before.mini, restored.mini)
            assertNotSame(before.expanded, restored.expanded)
            composeRule.runOnIdle {
                // Returning to the old content key must not resurrect its old scope or anchors.
                assertNull(before.mini.geometry()); assertNull(before.expanded.geometry())
                fixture.morph.expand(); fixture.morph.collapse(); fixture.morph.expand()
            }
            composeRule.mainClock.advanceTimeBy(500)
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
        composeRule.runOnIdle {
            assertEquals(PlayerPresentation.Expanded, fixture.morph.settledPresentation)
            assertTrue(fixture.calls.isEmpty())
        }
    }

    @Test fun horizonOverlayCancelsPendingDirectSeekThroughTheRealPresentationHost() {
        val fixture = horizonFixture(PlayerPresentation.Expanded)
        val preview = mutableStateOf<Int?>(null)
        val environment = DirectSeekGestureEnvironment({ DirectScrubAuthority(true, 1L, 0L, true) }) { preview.value = it }
        composeRule.setContent {
            CompositionLocalProvider(LocalDirectSeekGestureEnvironment provides environment, LocalDirectSeekPreview provides preview.value) {
                fixture.Content()
            }
        }
        awaitHorizon(fixture)
        composeRule.onNodeWithTag("pocketHorizonSeek").performTouchInput { down(center); moveBy(Offset(12f, 0f)) }
        composeRule.runOnIdle {
            assertTrue(preview.value != null)
            assertTrue(fixture.seeks.isEmpty())
            fixture.queueVisible.value = true
        }
        composeRule.onNodeWithTag("player-host").performTouchInput { up() }
        composeRule.runOnIdle {
            assertNull(preview.value)
            assertTrue(fixture.seeks.isEmpty())
            assertEquals(PlayerPresentation.Expanded, fixture.morph.settledPresentation)
        }
    }

    @Test fun horizonSystemBackLeavesDetailsBeforeNowPlayingWithoutCollapsingThePlayer() {
        val fixture = horizonFixture(PlayerPresentation.Expanded)
        composeRule.setContent { fixture.Content() }
        awaitHorizon(fixture)
        composeRule.runOnIdle { fixture.horizonNavigation.openAlbum("/music") }
        awaitHorizon(fixture)
        composeRule.waitUntil(5_000) { composeRule.runOnUiThread { composeRule.activity.window.decorView.hasWindowFocus() } }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeRule.runOnIdle {
            assertNull(fixture.horizonNavigation.albumKey)
            assertEquals(PocketHorizonCategory.Albums, fixture.horizonNavigation.category)
        }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeRule.runOnIdle {
            assertEquals(PocketHorizonCategory.NowPlaying, fixture.horizonNavigation.category)
            assertEquals(PlayerPresentation.Expanded, fixture.morph.settledPresentation)
            assertTrue(fixture.calls.isEmpty())
        }
    }

    private fun horizonFixture(initial: PlayerPresentation = PlayerPresentation.Collapsed) = tabletFixture(initial).apply {
        viewport.value = DpSize(1200.dp, 900.dp)
        adaptiveDefaultLayout.value = false
        deviceKind.value = PlayerDeviceKind.TABLET
        controlledLandscapeIdentity.value = PlayerPresentationIdentity.PocketHorizon
        observeHorizonGeometry = true
    }

    private data class HorizonSnapshot(val pair: PocketHorizonEndpointPair,
        val mini: PocketHorizonEndpointMeasurements, val expanded: PocketHorizonEndpointMeasurements)

    private fun awaitHorizon(fixture: Fixture): HorizonSnapshot {
        var found: HorizonSnapshot? = null
        composeRule.waitUntil(5_000) {
            found = composeRule.runOnIdle {
                val scopes = mutableSetOf<PocketHorizonEndpointMeasurements>()
                fun collect(data: CompositionData) {
                    data.compositionGroups.forEach { group ->
                        scopes.addAll(group.data.filterIsInstance<PocketHorizonEndpointMeasurements>())
                        collect(group)
                    }
                }
                fixture.playerComposition?.let(::collect)
                // A complete pair may still be for the previously rendered song, especially with
                // a paused test clock. Never report readiness for content the fixture has replaced.
                val expectedContent = pocketHorizonMeasurementKey(fixture.current.value, fixture.playerLayoutDirection)
                val currentScopes = scopes.filter { it.contentKey == expectedContent }
                currentScopes.firstNotNullOfOrNull { mini -> currentScopes.firstNotNullOfOrNull { expanded ->
                    resolvePocketHorizonEndpointPair(fixture.endpoints, mini, expanded)?.let { HorizonSnapshot(it, mini, expanded) }
                } }
            }
            found != null
        }
        return requireNotNull(found)
    }

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
        val librarySongs = mutableStateOf(songs)
        val libraryReady = mutableStateOf(true)
        val artistSortState = mutableStateOf(LibrarySortState(LibrarySortOption.NAME, LibrarySortDirection.ASCENDING))
        val albumSortState = mutableStateOf(LibrarySortState(LibrarySortOption.TITLE, LibrarySortDirection.ASCENDING))
        val songSelections = mutableListOf<Pair<Song, List<Song>>>()
        val current = mutableStateOf<Song?>(songs[1])
        val previous = mutableStateOf<Song?>(songs[0])
        val next = mutableStateOf<Song?>(songs[2])
        val theme = mutableStateOf(PlayerTheme.DEFAULT)
        val nominatedTheme = mutableStateOf<PlayerTheme?>(null)
        val controlledLandscapeIdentity = mutableStateOf<PlayerPresentationIdentity?>(null)
        var observeHorizonGeometry = false
        val tabletLandscapeTheme = mutableStateOf(TabletLandscapeTheme.NORMAL_PLAYER)
        val advanceAppearance = mutableStateOf(PocketAdvanceAppearance.Default)
        val primaryTokenOverrides = mutableStateOf(PlayerThemeTokenOverrides())
        val deviceKind = mutableStateOf(PlayerDeviceKind.UNKNOWN)
        val miniVisible = mutableStateOf(false)
        val navigationVisible = mutableStateOf(true)
        val metadataVisible = mutableStateOf(false)
        val overlaysVisible = mutableStateOf(true)
        val queueVisible = mutableStateOf(false)
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
        var playerLayoutDirection = LayoutDirection.Ltr
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
        lateinit var advanceNavigation: PocketAdvanceNavigationState
        lateinit var horizonNavigation: PocketHorizonNavigationState

        @Composable
        fun Content() {
            val inheritedDensity = LocalDensity.current
            val advancePalette = remember(advanceAppearance.value) { PocketAdvancePalette.from(advanceAppearance.value) }
            CompositionLocalProvider(LocalPocketAdvancePalette provides advancePalette, LocalDensity provides if (viewport.value != null) {
                // Fit a deterministic tablet-sized logical window on phone test devices.
                Density(0.25f, fontScale.value)
            } else inheritedDensity) {
                ContentInViewport()
            }
        }

        @Composable
        @OptIn(ExperimentalLayoutApi::class)
        private fun ContentInViewport() {
            playerLayoutDirection = LocalLayoutDirection.current
            contentPaddingPx = with(LocalDensity.current) { 16.dp.toPx() }
            advanceNavigation = rememberPocketAdvanceNavigationState()
            horizonNavigation = rememberPocketHorizonNavigationState()
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
                        .then(if ((tabletLandscapeTheme.value == TabletLandscapeTheme.POCKET_ADVANCE || controlledLandscapeIdentity.value == PlayerPresentationIdentity.PocketHorizon) && viewport.value != null) {
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
                    val primaryTokens = theme.value.defaultTokens().applyOverrides(primaryTokenOverrides.value)
                    val requestedPresentation = resolvePlayerPresentation(
                        primaryTheme = theme.value,
                        primaryThemeTokens = primaryTokens,
                        context = TabletLandscapePlayerContext(deviceKind.value, 800, DpSize(maxWidth, maxHeight)),
                        landscapeCandidate = tabletLandscapePlayerCandidate(tabletLandscapeTheme.value,
                            primaryTokens, LocalDensity.current.fontScale) ?: controlledLandscapeIdentity.value?.let { identity ->
                            TabletLandscapePlayerCandidate(ResolvedPlayerPresentation(identity, primaryTokens),
                                if (identity == PlayerPresentationIdentity.PocketHorizon) minimumPocketHorizonSize(LocalDensity.current.fontScale)
                                else minimumPocketAdvanceSize(LocalDensity.current.fontScale))
                        } ?: nominatedTheme.value?.let { nominated ->
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
                        songs = librarySongs.value,
                        currentSong = current.value,
                        previousPreviewSong = previous.value,
                        nextPreviewSong = next.value,
                        isPlaying = false,
                        isShuffleEnabled = false,
                        repeatMode = RepeatMode.OFF,
                        playbackProgressUiState = progress,
                        lyricsPlaybackUiState = LyricsPlaybackUiState.Hidden,
                        onSongClick = { selected, context -> songSelections += selected to context.toList() },
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
                        isQueueHubVisible = queueVisible.value,
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
                        pocketAdvanceNavigationState = advanceNavigation,
                        pocketHorizonNavigationState = horizonNavigation,
                        isLibraryReady = libraryReady.value,
                        artistSortState = artistSortState.value,
                        albumSortState = albumSortState.value,
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
                        if (observeDefaultMorphGeometry || observeAdvanceMorphGeometry || observeHorizonGeometry) {
                            playerComposition = currentComposer.compositionData
                        }
                    }
                }
            }
        }
    }
}
