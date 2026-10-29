package io.github.rsgarrido.sazanami.ui.player

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.data.membershipKey
import io.github.rsgarrido.sazanami.ui.blockPlayerInput
import io.github.rsgarrido.sazanami.player.RepeatMode
import io.github.rsgarrido.sazanami.ui.player.classicwheel.ClassicWheelNowPlayingDisplay
import io.github.rsgarrido.sazanami.ui.player.classicwheel.ClassicWheelMenuState
import io.github.rsgarrido.sazanami.ui.player.classicwheel.ClassicWheelMorphBounds
import io.github.rsgarrido.sazanami.ui.player.modern.DefaultPlayerMorphBounds
import io.github.rsgarrido.sazanami.ui.player.modern.ModernPlayerAppearance
import io.github.rsgarrido.sazanami.ui.player.modern.ModernArtworkTransitionStyle
import io.github.rsgarrido.sazanami.ui.player.modern.ModernQueueHubButton
import io.github.rsgarrido.sazanami.ui.player.modern.ModernMoreButton
import io.github.rsgarrido.sazanami.ui.player.pocketcassette.PocketCassetteControls
import io.github.rsgarrido.sazanami.ui.player.pocketcassette.PocketCassetteMorphBounds
import io.github.rsgarrido.sazanami.ui.player.pocketflip.PocketFlipControlHalf
import io.github.rsgarrido.sazanami.ui.player.pocketflip.PocketFlipMorphBounds
import io.github.rsgarrido.sazanami.ui.player.pocketdisc.PocketDiscMorphBounds
import io.github.rsgarrido.sazanami.ui.player.retrorack.RetroRackExpandedPlayer
import io.github.rsgarrido.sazanami.ui.player.retrorack.RetroRackMorphBounds
import io.github.rsgarrido.sazanami.ui.player.theme.defaultTokens
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ExpandedQueueHubEntryPointTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()
    private val retroThemes = listOf(PlayerTheme.RETRO_RACK, PlayerTheme.POCKET_FLIP,
        PlayerTheme.POCKET_CASSETTE, PlayerTheme.POCKET_DISC)

    @Test
    fun modernExpandedControlAreaHasOneQueueHubAction() {
        var openCount = 0
        composeRule.setContent {
            MaterialTheme {
                ModernQueueHubButton(onClick = { openCount += 1 })
            }
        }

        assertSingleQueueHubActionAndClick()
        composeRule.runOnIdle { assertEquals(1, openCount) }
    }

    @Test
    fun modernMoreAndQueueHubHaveIndependentCallbacksAndSingleActions() {
        var queueCount = 0
        var moreCount = 0
        composeRule.setContent {
            MaterialTheme {
                Row {
                    ModernMoreButton(onClick = { moreCount++ })
                    ModernQueueHubButton(onClick = { queueCount++ })
                }
            }
        }
        composeRule.onAllNodesWithContentDescription("More actions").assertCountEquals(1)
        composeRule.onNodeWithContentDescription("More actions").performClick()
        composeRule.runOnIdle {
            assertEquals(1, moreCount)
            assertEquals(0, queueCount)
        }
        assertSingleQueueHubActionAndClick()
        composeRule.runOnIdle {
            assertEquals(1, moreCount)
            assertEquals(1, queueCount)
        }
    }

    @Test
    fun inactiveMoreSlotExposesNoAccessibleOrClickableAction() {
        composeRule.setContent {
            MaterialTheme {
                ModernMoreButton(onClick = { error("Inactive More") }, enabled = false)
            }
        }
        composeRule.onAllNodesWithContentDescription("More actions").assertCountEquals(0)
    }

    @Test
    fun classicWheelExistingQueueControlOpensQueueHubWithoutADuplicateAction() {
        var openCount = 0
        composeRule.setContent {
            MaterialTheme {
                ClassicWheelNowPlayingDisplay(
                    currentSong = null,
                    currentPosition = 0,
                    duration = 0,
                    isCurrentSongFavorite = false,
                    isShuffleEnabled = false,
                    repeatMode = RepeatMode.OFF,
                    musicVolume = 0,
                    maxMusicVolume = 1,
                    isVolumeIndicatorVisible = false,
                    onSeekChange = {},
                    onShuffleClick = {},
                    onRepeatClick = {},
                    onOpenUpNextClick = { openCount += 1 },
                    onToggleFavoriteClick = {}
                )
            }
        }

        assertSingleQueueHubActionAndClick()
        composeRule.runOnIdle { assertEquals(1, openCount) }
    }

    @Test
    fun retroRackExistingQueueControlOpensQueueHubWithoutADuplicateAction() {
        var openCount = 0
        composeRule.setContent {
            MaterialTheme {
                RetroRackExpandedPlayer(
                    currentSong = null,
                    isVisualizerWorkAllowed = false,
                    isPlaying = false,
                    isShuffleEnabled = false,
                    repeatMode = RepeatMode.OFF,
                    currentPosition = 0,
                    duration = 0,
                    upcomingSongs = emptyList(),
                    onPlayPauseClick = {},
                    onPreviousClick = {},
                    onNextClick = {},
                    onSeekChange = {},
                    onShuffleClick = {},
                    onRepeatClick = {},
                    onCollapseClick = {},
                    onOpenUpNextClick = { openCount += 1 },
                    onMoreClick = {},
                    onSongClick = { _, _ -> }
                )
            }
        }

        assertSingleQueueHubActionAndClick()
        composeRule.runOnIdle { assertEquals(1, openCount) }
    }

    @Test
    fun retroRackRendersAuthoritativeQueueIncludingDuplicateEntries() {
        val duplicate = song(7L, "Duplicate")
        composeRule.setContent {
            MaterialTheme {
                RetroRackExpandedPlayer(
                    currentSong = duplicate,
                    isVisualizerWorkAllowed = false,
                    isPlaying = true,
                    isShuffleEnabled = false,
                    repeatMode = RepeatMode.OFF,
                    currentPosition = 0,
                    duration = 180_000,
                    upcomingSongs = emptyList(),
                    activeQueueSongs = listOf(duplicate, song(8L, "Middle"), duplicate),
                    onPlayPauseClick = {},
                    onPreviousClick = {},
                    onNextClick = {},
                    onSeekChange = {},
                    onShuffleClick = {},
                    onRepeatClick = {},
                    onCollapseClick = {},
                    onOpenUpNextClick = {},
                    onMoreClick = {},
                    onSongClick = { _, _ -> }
                )
            }
        }

        composeRule.onAllNodesWithText("Duplicate", substring = true)
            .assertCountEquals(2)
        composeRule.onAllNodesWithText("Middle", substring = true)
            .assertCountEquals(1)
    }

    @Test
    fun pocketFlipReusesItsSingleExistingQueueControl() {
        var flipOpenCount = 0
        composeRule.setContent {
            MaterialTheme {
                PocketFlipControlHalf(
                    currentSong = null,
                    isPlaying = false,
                    isShuffleEnabled = false,
                    repeatMode = RepeatMode.OFF,
                    onPlayPauseClick = {},
                    onPreviousClick = {},
                    onNextClick = {},
                    onShuffleClick = {},
                    onRepeatClick = {},
                    onOpenUpNextClick = { flipOpenCount += 1 },
                    onCollapseClick = {},
                    onMoreClick = {},
                    compact = true,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        assertSingleQueueHubActionAndClick()
        composeRule.runOnIdle { assertEquals(1, flipOpenCount) }
    }

    @Test
    fun pocketCassetteReusesItsSingleExistingQueueControl() {
        var cassetteOpenCount = 0
        composeRule.setContent {
            MaterialTheme {
                PocketCassetteControls(
                    currentSong = null,
                    isPlaying = false,
                    isShuffleEnabled = false,
                    repeatMode = RepeatMode.OFF,
                    currentPosition = 0,
                    duration = 0,
                    onPlayPauseClick = {},
                    onPreviousClick = {},
                    onNextClick = {},
                    onSeekChange = {},
                    onShuffleClick = {},
                    onRepeatClick = {},
                    onOpenUpNextClick = { cassetteOpenCount += 1 },
                    onMoreClick = {},
                    compact = true
                )
            }
        }
        assertSingleQueueHubActionAndClick()
        composeRule.runOnIdle { assertEquals(1, cassetteOpenCount) }
    }

    @Test
    fun allFourRetroThemesOpenTheSharedMoreDialogAndKeepQueueAndFavoriteIndependent() {
        val theme = mutableStateOf(PlayerTheme.RETRO_RACK)
        val target = song(1, "Current track")
        val moreTarget = mutableStateOf<Song?>(null)
        val favorites = mutableStateOf(emptySet<String>())
        val feedback = mutableListOf<NowPlayingFavoriteFeedback>()
        var moreCount = 0
        var queueCount = 0
        var favoriteCount = 0
        val toggle: (Song) -> Unit = {
            favoriteCount++
            favorites.value = if (it.membershipKey() in favorites.value) emptySet()
                else setOf(it.membershipKey())
        }
        composeRule.setContent {
            MaterialTheme {
                val player = rememberPlayerMorphState(PlayerPresentation.Expanded)
                val lyrics = rememberPlayerLyricsTransitionState(false) {}
                RetroThemeHostFixture(
                    theme.value, target, player, lyrics,
                    isFavorite = target.membershipKey() in favorites.value,
                    onMore = { moreCount++; moreTarget.value = target },
                    onQueue = { queueCount++ }, onFavorite = toggle
                )
                moreTarget.value?.let { captured ->
                    NowPlayingMoreDialog(
                        captured, nowPlayingActions(captured, favorites.value, listOf(target)),
                        onDismiss = { moreTarget.value = null },
                        onAction = { action ->
                            performNowPlayingAction(
                                action, captured, target, listOf(target),
                                onDismiss = { moreTarget.value = null }, onToggleFavorite = toggle,
                                onOpenAlbum = {}, onOpenLyrics = {},
                                isFavorite = captured.membershipKey() in favorites.value,
                                onFavoriteFeedback = { feedback += it }, onOpenArtist = {}
                            )
                        }
                    )
                }
            }
        }
        retroThemes.forEachIndexed { index, retroTheme ->
            composeRule.runOnIdle { theme.value = retroTheme; favorites.value = emptySet() }
            composeRule.onAllNodesWithContentDescription("More actions").assertCountEquals(1)
            composeRule.onAllNodesWithContentDescription("Add to favorites").assertCountEquals(0)
            composeRule.onAllNodesWithContentDescription("Remove from favorites").assertCountEquals(0)
            composeRule.onAllNodesWithText("SAVE").assertCountEquals(0)
            val moreNode = composeRule.onNodeWithContentDescription("More actions")
            assertEquals(Role.Button, moreNode.fetchSemanticsNode().config[SemanticsProperties.Role])
            moreNode.performClick()
            composeRule.onNodeWithTag(NowPlayingMoreDialogTag).assertExists()
            composeRule.onNodeWithText("Go to artist").assertExists()
            composeRule.onNodeWithText("Go to album").assertExists()
            composeRule.onNodeWithText("Add to playlist").assertExists()
            composeRule.onNodeWithContentDescription("Rate song").assertExists()
            composeRule.onNodeWithContentDescription("Track information").assertExists()
            composeRule.onNodeWithContentDescription("Sleep Timer").assertExists()
            composeRule.onNodeWithText("Lyrics").assertExists()
            composeRule.runOnIdle {
                assertEquals(index + 1, moreCount)
                assertEquals(index, queueCount)
                assertEquals(index, favoriteCount)
            }
            composeRule.onNodeWithContentDescription("Add to favorites").performClick()
            composeRule.onNodeWithTag(NowPlayingMoreDialogTag).assertDoesNotExist()
            assertSingleQueueHubActionAndClick()
            composeRule.runOnIdle {
                assertEquals(index + 1, moreCount)
                assertEquals(index + 1, queueCount)
                assertEquals(index + 1, favoriteCount)
                assertEquals(NowPlayingFavoriteFeedback.ADDED_TO_FAVORITES, feedback.last())
            }
        }
        composeRule.runOnIdle { theme.value = PlayerTheme.CLASSIC_WHEEL; favorites.value = emptySet() }
        composeRule.onAllNodesWithContentDescription("More actions").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Add to favorites").performClick()
        composeRule.runOnIdle { assertEquals(5, favoriteCount); assertEquals(4, moreCount) }
    }

    @Test
    fun retroMorphHiddenAndLyricsBlockedLayersExposeNoMoreAction() {
        val theme = mutableStateOf(PlayerTheme.RETRO_RACK)
        lateinit var player: PlayerMorphState
        lateinit var lyrics: PlayerLyricsTransitionState
        composeRule.setContent {
            MaterialTheme {
                val scope = rememberCoroutineScope()
                player = remember { PlayerMorphState(PlayerPresentation.Collapsed, scope) }
                lyrics = remember { PlayerLyricsTransitionState(false, scope) {} }
                RetroThemeHostFixture(
                    theme.value, song(1, "Track"), player, lyrics, false,
                    onMore = { error("Inactive More") }, onQueue = {}, onFavorite = {}
                )
            }
        }
        retroThemes.forEach { retroTheme ->
            composeRule.runOnIdle { theme.value = retroTheme }
            composeRule.onAllNodesWithContentDescription("More actions").assertCountEquals(0)
        }
        composeRule.runOnIdle {
            player.snapTo(PlayerPresentation.Expanded)
            lyrics.beginOpeningDrag()
            lyrics.dragOpeningBy(-400f, 800f)
        }
        retroThemes.forEach { retroTheme ->
            composeRule.runOnIdle { theme.value = retroTheme }
            composeRule.onAllNodesWithContentDescription("More actions").assertCountEquals(0)
        }
    }

    @Test
    fun onlyApprovedSettledRetroArtworkOpensTheSharedViewerEntry() {
        val theme = mutableStateOf(PlayerTheme.RETRO_RACK)
        val target = mutableStateOf(song(1, "Cover track").copy(albumArtUri = Uri.parse("content://art/1")))
        var opened = 0
        lateinit var player: PlayerMorphState
        lateinit var lyrics: PlayerLyricsTransitionState
        composeRule.setContent {
            MaterialTheme {
                player = rememberPlayerMorphState(PlayerPresentation.Expanded)
                lyrics = rememberPlayerLyricsTransitionState(false) {}
                RetroThemeHostFixture(theme.value, target.value, player, lyrics, false, {}, {}, {},
                    onViewArtwork = { assertEquals(target.value, it); opened++ })
            }
        }
        listOf(PlayerTheme.RETRO_RACK, PlayerTheme.POCKET_FLIP, PlayerTheme.POCKET_DISC).forEachIndexed { index, value ->
            composeRule.runOnIdle { theme.value = value }
            composeRule.onAllNodesWithTag(ArtworkViewerEntryTag).assertCountEquals(1)
            composeRule.onNodeWithTag(ArtworkViewerEntryTag).performClick()
            composeRule.runOnIdle { assertEquals(index + 1, opened); player.updateProgressFromDrag(0.5f) }
            composeRule.onAllNodesWithTag(ArtworkViewerEntryTag).assertCountEquals(0)
            composeRule.runOnIdle { player.snapTo(PlayerPresentation.Expanded) }
        }
        composeRule.runOnIdle { lyrics.beginOpeningDrag() }
        composeRule.onAllNodesWithTag(ArtworkViewerEntryTag).assertCountEquals(0)
        composeRule.runOnIdle { lyrics.snapToExpanded(); target.value = target.value.copy(albumArtUri = null) }
        composeRule.onAllNodesWithTag(ArtworkViewerEntryTag).assertCountEquals(0)
        listOf(PlayerTheme.POCKET_CASSETTE, PlayerTheme.CLASSIC_WHEEL).forEach { value ->
            composeRule.runOnIdle { theme.value = value; target.value = target.value.copy(albumArtUri = Uri.parse("content://art/1")) }
            composeRule.onAllNodesWithTag(ArtworkViewerEntryTag).assertCountEquals(0)
        }
    }

    @androidx.compose.runtime.Composable
    private fun RetroThemeHostFixture(
        theme: PlayerTheme,
        target: Song,
        player: PlayerMorphState,
        lyrics: PlayerLyricsTransitionState,
        isFavorite: Boolean,
        onMore: () -> Unit,
        onQueue: () -> Unit,
        onFavorite: (Song) -> Unit,
        onViewArtwork: ((Song) -> Unit)? = null
    ) {
        Box(Modifier.fillMaxSize().blockPlayerInput(lyrics.lyricsOwnsInput)) {
            ExpandedPlayerThemeHost(
                playerPresentation = resolvePlayerPresentation(theme, theme.defaultTokens()), currentSong = target,
                previousPreviewSong = null, nextPreviewSong = null,
                modernArtworkTransitionStyle = ModernArtworkTransitionStyle.SLIDE,
                modernPlayerAppearance = ModernPlayerAppearance.Default,
                isVisualizerWorkAllowed = false, isPlaying = false, isShuffleEnabled = false,
                repeatMode = RepeatMode.OFF, currentPosition = 0, duration = 180_000,
                isCurrentSongFavorite = isFavorite, onPlayPauseClick = {}, onPreviousClick = {},
                onNextClick = {}, onSeekChange = {}, onShuffleClick = {}, onRepeatClick = {},
                onCollapseClick = {}, playerMorphState = player, lyricsTransitionState = lyrics,
                lyricsGestureRegion = remember { PlayerLyricsGestureRegion() },
                onOpenQueueHubClick = onQueue, onOpenMoreClick = onMore,
                onToggleFavoriteClick = onFavorite, songs = listOf(target), upcomingSongs = emptyList(),
                activeQueueSongs = listOf(target), activeQueueName = "Queue", activeQueuePosition = 1,
                activeQueueCount = 1, onSongClick = { _, _ -> }, onOpenCurrentAlbumClick = {},
                onOpenCurrentArtistClick = {}, onViewArtwork = onViewArtwork,
                endpointBounds = remember { PlayerEndpointBounds() },
                defaultMorphBounds = remember { DefaultPlayerMorphBounds() },
                classicMorphBounds = remember { ClassicWheelMorphBounds() },
                classicWheelMenuState = remember { ClassicWheelMenuState() },
                retroRackMorphBounds = remember { RetroRackMorphBounds().apply {
                    val mini = Rect(0f, 700f, 48f, 748f)
                    updateMiniArtwork(mini); updateMiniTitle(mini); updateMiniArtist(mini); updateMiniProgress(mini); updateMiniPlay(mini)
                } },
                pocketFlipMorphBounds = remember { PocketFlipMorphBounds().apply {
                    val mini = Rect(0f, 700f, 48f, 748f)
                    updateMiniArtwork(mini); updateMiniTitle(mini); updateMiniArtist(mini); updateMiniProgress(mini); updateMiniPlay(mini)
                } },
                pocketCassetteMorphBounds = remember { PocketCassetteMorphBounds() },
                pocketDiscMorphBounds = remember { PocketDiscMorphBounds().apply {
                    val mini = Rect(0f, 700f, 48f, 748f)
                    updateMiniArtwork(mini); updateMiniTitle(mini); updateMiniArtist(mini); updateMiniProgress(mini); updateMiniPlay(mini)
                } }
            )
        }
    }

    private fun assertSingleQueueHubActionAndClick() {
        composeRule.onAllNodesWithContentDescription("Open queues").assertCountEquals(1)
        composeRule.onNodeWithContentDescription("Open queues").performClick()
    }

    private fun song(id: Long, title: String) = Song(
        id = id,
        title = title,
        artist = "Artist",
        album = "Album",
        trackNumber = id.toInt(),
        duration = 180_000L,
        uri = Uri.parse("content://media/$id"),
        filePath = "/music/$id.flac",
        folderPath = "/music",
        albumArtUri = null,
        volumeName = "external",
        displayName = "$id.flac",
        relativePath = "Music/",
        fileSizeBytes = 1_000L,
        dateModifiedEpochSeconds = 1L
    )
}
