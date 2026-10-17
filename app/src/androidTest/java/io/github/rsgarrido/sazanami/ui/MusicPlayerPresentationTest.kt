package io.github.rsgarrido.sazanami.ui

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.controller.PlaybackQueueHubUiState
import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.lyrics.LyricsPlaybackUiState
import io.github.rsgarrido.sazanami.player.RepeatMode
import io.github.rsgarrido.sazanami.ui.player.ArtworkViewerEntryTag
import io.github.rsgarrido.sazanami.ui.player.PlayerEndpointBounds
import io.github.rsgarrido.sazanami.ui.player.PlayerLyricsTransitionState
import io.github.rsgarrido.sazanami.ui.player.PlayerMorphHost
import io.github.rsgarrido.sazanami.ui.player.PlayerMorphState
import io.github.rsgarrido.sazanami.ui.player.PlayerPresentation
import io.github.rsgarrido.sazanami.ui.player.classicwheel.ClassicWheelMenuScreen
import io.github.rsgarrido.sazanami.ui.player.classicwheel.ClassicWheelMenuState
import io.github.rsgarrido.sazanami.ui.player.modern.DefaultPlayerMorphBounds
import io.github.rsgarrido.sazanami.ui.player.modern.ModernArtworkTransitionStyle
import io.github.rsgarrido.sazanami.ui.player.modern.ModernPlayerAppearance
import io.github.rsgarrido.sazanami.ui.player.rememberPlayerLyricsTransitionState
import io.github.rsgarrido.sazanami.ui.player.rememberPlayerMorphState
import io.github.rsgarrido.sazanami.ui.player.theme.defaultTokens
import io.github.rsgarrido.sazanami.ui.state.PlaybackProgressUiState
import io.github.rsgarrido.sazanami.ui.theme.SazanamiTheme
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
        var contentPaddingPx = 0f
        var receivedPadding: Dp? = null
        val progress = MutableStateFlow(PlaybackProgressUiState(duration = 180_000))
        val calls = mutableListOf<String>()
        val openedArtists = mutableListOf<Song>()
        val openedAlbums = mutableListOf<Song>()
        val artworks = mutableListOf<Song>()
        val trackInfo = mutableListOf<Song>()
        val favorites = mutableListOf<Song>()
        lateinit var morph: PlayerMorphState
        lateinit var lyrics: PlayerLyricsTransitionState
        lateinit var endpoints: PlayerEndpointBounds

        @Composable
        fun Content() {
            contentPaddingPx = with(LocalDensity.current) { 16.dp.toPx() }
            morph = rememberPlayerMorphState(initial)
            lyrics = rememberPlayerLyricsTransitionState(lyricsVisible.value) { lyricsVisible.value = it }
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
                onSeekChange = {},
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
                selectedModernPlayerAppearance = ModernPlayerAppearance.Default,
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
                bodyPresentation = { value -> receivedPadding = value; Text("Body", Modifier.testTag("body")) },
                metadataEditorContent = { Text("Editor", Modifier.testTag("editor")) },
                metadataDialogContent = { Text("Metadata dialog", Modifier.testTag("metadata-dialog")) },
                transientOverlayContent = { Text("Transient", Modifier.testTag("transient")) },
                bottomNavigationContent = { if (navigationVisible.value) Text("Navigation", Modifier.align(Alignment.BottomCenter).testTag("navigation")) },
                onOpenMiniUpNext = { calls += "up-next" },
                onShowQueueHub = { calls += "queue" },
                onOpenSleepTimer = { calls += "sleep" },
                onOpenLyricsSettings = { calls += "lyrics-settings" },
                onShowExpandedMore = { calls += "more" },
                onOpenCurrentAlbumClick = { openedAlbums += it },
                onOpenCurrentArtistClick = { openedArtists += it },
                onTrackInfoClick = { trackInfo += it },
                onViewArtwork = { artworks += it },
            )
            SazanamiTheme {
                Box(Modifier.fillMaxSize().padding(
                    start = hostStartInset.value, top = hostTopInset.value, end = hostEndInset.value
                )) {
                    PlayerMorphHost(morph, Modifier.fillMaxSize().testTag("player-host")) { measuredEndpoints ->
                        endpoints = measuredEndpoints
                        player(measuredEndpoints)
                    }
                }
            }
        }
    }
}
