package io.github.rsgarrido.sazanami.ui.player

import android.net.Uri
import android.content.res.Configuration
import android.graphics.Rect
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.CoordinatesProvider
import androidx.test.espresso.action.GeneralClickAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Tap
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.controller.SongRatingDialogState
import io.github.rsgarrido.sazanami.controller.SongRatingUiState
import io.github.rsgarrido.sazanami.ui.ratings.LocalSongRatingUi
import io.github.rsgarrido.sazanami.ui.ratings.SongRatingUiEnvironment
import io.github.rsgarrido.sazanami.ui.ratings.SongRatingDialog
import io.github.rsgarrido.sazanami.ui.playlist.AddToPlaylistDialog
import io.github.rsgarrido.sazanami.data.membershipKey
import io.github.rsgarrido.sazanami.player.RepeatMode
import io.github.rsgarrido.sazanami.ui.player.modern.ModernExpandedPlayer
import io.github.rsgarrido.sazanami.ui.player.modern.ModernPlayerAppearance
import io.github.rsgarrido.sazanami.ui.player.modern.ModernArtworkCarouselPresentation
import io.github.rsgarrido.sazanami.ui.player.modern.rememberModernArtworkCarouselPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class NowPlayingMoreDialogTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun compactHeaderAndFavoriteLabelFollowTheSharedFavoriteState() {
        val target = song(1).copy(artist = "", album = "")
        val favorites = mutableStateOf(emptySet<String>())
        composeRule.setContent {
            MaterialTheme {
                NowPlayingMoreDialog(
                    target, nowPlayingActions(target, favorites.value, listOf(target)), {}, {}
                )
            }
        }
        composeRule.onNodeWithText("Song 1").assertExists()
        composeRule.onNodeWithText("Unknown Artist · Unknown Album").assertExists()
        composeRule.onNodeWithContentDescription("Add to favorites").assertExists()
        composeRule.onNodeWithText("Go to album").assertExists()
        composeRule.onNodeWithText("Go to artist").assertDoesNotExist()
        composeRule.onNodeWithText("Lyrics").assertExists()
        composeRule.runOnIdle { favorites.value = setOf(target.membershipKey()) }
        composeRule.onNodeWithContentDescription("Add to favorites").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Remove from favorites").assertExists()
    }

    @Test
    fun backDismissesTheDialogBeforeTheUnderlyingPlayerBackHandler() {
        val visible = mutableStateOf(true)
        var playerBackCount = 0
        composeRule.setContent {
            MaterialTheme {
                BackHandler { playerBackCount++ }
                if (visible.value) {
                    val target = song(1)
                    NowPlayingMoreDialog(
                        target, nowPlayingActions(target, emptySet(), listOf(target)),
                        onDismiss = { visible.value = false }, onAction = {}
                    )
                }
            }
        }
        composeRule.onNodeWithTag(NowPlayingMoreDialogTag).assertExists()
        Espresso.pressBack()
        composeRule.onNodeWithTag(NowPlayingMoreDialogTag).assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(0, playerBackCount) }
    }

    @Test
    fun outsideTapDismissesWithoutClickingThePlayerBehindTheDialog() {
        val visible = mutableStateOf(true)
        val originalSong = song(1)
        val currentSong = mutableStateOf(originalSong)
        lateinit var player: PlayerMorphState
        var playerClickCount = 0
        var dismissCount = 0
        var actionCount = 0
        composeRule.setContent {
            MaterialTheme {
                player = rememberPlayerMorphState(PlayerPresentation.Expanded)
                Box(Modifier.fillMaxSize().testTag("more-outside-player").clickable {
                    playerClickCount++
                    currentSong.value = song(2)
                    player.collapse()
                }) { Text(currentSong.value.title) }
                if (visible.value) {
                    NowPlayingMoreDialog(
                        originalSong, nowPlayingActions(originalSong, emptySet(), listOf(originalSong)),
                        onDismiss = { dismissCount++; visible.value = false },
                        onAction = { actionCount++ }
                    )
                }
            }
        }
        composeRule.onNodeWithTag(NowPlayingMoreDialogTag).assertIsDisplayed()
        Espresso.onView(isRoot()).inRoot(isDialog()).perform(GeneralClickAction(
            Tap.SINGLE,
            CoordinatesProvider { dialogDecor ->
                val dialogPosition = IntArray(2)
                dialogDecor.getLocationOnScreen(dialogPosition)
                val dialogWindow = Rect(dialogPosition[0], dialogPosition[1],
                    dialogPosition[0] + dialogDecor.width, dialogPosition[1] + dialogDecor.height)
                val activityDecor = composeRule.activity.window.decorView
                val activityPosition = IntArray(2)
                activityDecor.getLocationOnScreen(activityPosition)
                val activityViewport = Rect()
                activityDecor.getWindowVisibleDisplayFrame(activityViewport)
                assertTrue("Activity must have a visible viewport: $activityViewport", activityViewport.intersect(
                    Rect(activityPosition[0], activityPosition[1],
                        activityPosition[0] + activityDecor.width, activityPosition[1] + activityDecor.height)))
                // Native outside handling can include window touch slop. Avoid that boundary too.
                val exclusion = Rect(dialogWindow).apply {
                    val slop = ViewConfiguration.get(dialogDecor.context).scaledWindowTouchSlop
                    inset(-slop, -slop)
                }
                val outside = listOf(
                    Rect(activityViewport.left, activityViewport.top, activityViewport.right, min(activityViewport.bottom, exclusion.top)),
                    Rect(activityViewport.left, max(activityViewport.top, exclusion.bottom), activityViewport.right, activityViewport.bottom),
                    Rect(activityViewport.left, activityViewport.top, min(activityViewport.right, exclusion.left), activityViewport.bottom),
                    Rect(max(activityViewport.left, exclusion.right), activityViewport.top, activityViewport.right, activityViewport.bottom)
                ).filter { it.width() > 0 && it.height() > 0 }
                    .maxByOrNull { it.width().toLong() * it.height() }
                    ?: throw AssertionError("No native outside region: dialog=$dialogWindow; " +
                        "activity=$activityViewport; exclusion=$exclusion")
                val x = outside.exactCenterX()
                val y = outside.exactCenterY()
                assertTrue("Outside tap must remain in the visible activity", activityViewport.contains(x.toInt(), y.toInt()))
                assertFalse("Outside tap must leave the native window: $dialogWindow", dialogWindow.contains(x.toInt(), y.toInt()))
                // GeneralClickAction consumes screen coordinates, not Compose-root coordinates.
                floatArrayOf(x, y)
            },
            Press.FINGER
        ))
        composeRule.onNodeWithTag(NowPlayingMoreDialogTag).assertDoesNotExist()
        composeRule.onNodeWithTag("more-outside-player").assertIsDisplayed()
        composeRule.onNodeWithText(originalSong.title).assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(1, dismissCount)
            assertEquals(0, actionCount)
            assertEquals(0, playerClickCount)
            assertSame(originalSong, currentSong.value)
            assertEquals(PlayerPresentation.Expanded, player.targetPresentation)
            assertEquals(PlayerPresentation.Expanded, player.settledPresentation)
            assertEquals(1f, player.progress, 0f)
        }
    }

    @Test
    fun actionTapDoesNotReachThePlayerBehindTheDialog() {
        val visible = mutableStateOf(true)
        var playerClickCount = 0
        var actionCount = 0
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize().clickable { playerClickCount++ })
                if (visible.value) {
                    val target = song(1)
                    NowPlayingMoreDialog(
                        target, nowPlayingActions(target, emptySet(), listOf(target)),
                        onDismiss = { visible.value = false },
                        onAction = {
                            assertEquals(NowPlayingAction.FAVORITE, it)
                            actionCount++
                            visible.value = false
                        }
                    )
                }
            }
        }
        composeRule.onNodeWithContentDescription("Add to favorites").performTouchInput { click(center) }
        composeRule.onNodeWithTag(NowPlayingMoreDialogTag).assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(1, actionCount)
            assertEquals(0, playerClickCount)
        }
    }

    @Test
    fun lyricsDismissesMoreAndOpensTheExistingLyricsTransition() {
        val visible = mutableStateOf(true)
        lateinit var lyrics: PlayerLyricsTransitionState
        composeRule.setContent {
            MaterialTheme {
                lyrics = rememberPlayerLyricsTransitionState(false) {}
                if (visible.value) {
                    val target = song(1)
                    NowPlayingMoreDialog(
                        target, nowPlayingActions(target, emptySet(), listOf(target)),
                        onDismiss = { visible.value = false },
                        onAction = { action ->
                            performNowPlayingAction(
                                action, target, target, listOf(target),
                                onDismiss = { visible.value = false },
                                onToggleFavorite = {}, onOpenAlbum = {},
                                onOpenLyrics = lyrics::openLyrics
                            )
                        }
                    )
                }
            }
        }
        composeRule.onNodeWithText("Lyrics").performClick()
        composeRule.onNodeWithTag(NowPlayingMoreDialogTag).assertDoesNotExist()
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.runOnIdle { assertEquals(PlayerSurfaceState.LYRICS, lyrics.settledSurface) }
    }

    @Test
    fun defaultPlayerHasOneMoreTriggerWithPreviewTracksAndSuppressesItDuringCarouselDrag() {
        lateinit var carousel: ModernArtworkCarouselPresentation
        var moreCount = 0
        composeRule.setContent {
            MaterialTheme {
                val scope = rememberCoroutineScope()
                val target = remember { song(1) }
                val player = remember { PlayerMorphState(PlayerPresentation.Expanded, scope) }
                val lyrics = rememberPlayerLyricsTransitionState(false) {}
                carousel = rememberModernArtworkCarouselPresentation(
                    target, song(2), song(3), {}, {}
                )
                ModernExpandedPlayer(
                    currentSong = target,
                    previousPreviewSong = song(2), nextPreviewSong = song(3),
                    appearance = ModernPlayerAppearance.Default.copy(
                        layout = ModernPlayerAppearance.Default.layout.copy(showAudioQualityBadge = false)
                    ),
                    isPlaying = false, isShuffleEnabled = false, repeatMode = RepeatMode.OFF,
                    currentPosition = 0, duration = 120_000, isCurrentSongFavorite = false,
                    onPlayPauseClick = {}, onPreviousClick = {}, onNextClick = {},
                    onSeekChange = {}, onShuffleClick = {}, onRepeatClick = {}, onCollapseClick = {},
                    playerMorphState = player, lyricsTransitionState = lyrics,
                    onOpenUpNextClick = {}, onToggleFavoriteClick = {},
                    onOpenMoreClick = { moreCount++ }, carouselPresentation = carousel
                )
            }
        }
        composeRule.onAllNodesWithContentDescription("More actions").assertCountEquals(1)
        composeRule.onNodeWithContentDescription("More actions").performClick()
        composeRule.runOnIdle {
            assertEquals(1, moreCount)
            carousel.state.updateArtworkWidth(300)
            carousel.state.startDrag()
            carousel.state.dragBy(-100f)
        }
        composeRule.onAllNodesWithContentDescription("More actions").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("Open queues").assertCountEquals(1)
    }

    @Test
    fun libraryArtistRowUsesTheSharedActionAndDismissesBeforeNavigation() {
        val target = song(1)
        val visible = mutableStateOf(true)
        val events = mutableListOf<String>()
        composeRule.setContent {
            MaterialTheme {
                if (visible.value) NowPlayingMoreDialog(
                    target, nowPlayingActions(target, emptySet(), listOf(target)),
                    onDismiss = { visible.value = false },
                    onAction = { action ->
                        performNowPlayingAction(
                            action, target, target, listOf(target),
                            onDismiss = { events += "dismiss"; visible.value = false },
                            onToggleFavorite = {}, onOpenAlbum = {}, onOpenLyrics = {},
                            onOpenArtist = { events += "artist" },
                            onFavoriteFeedback = { error("Artist must not emit Favorite feedback") }
                        )
                    }
                )
            }
        }
        composeRule.onNodeWithText("Go to artist").performClick()
        composeRule.onNodeWithTag(NowPlayingMoreDialogTag).assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(listOf("dismiss", "artist"), events) }
    }

    @Test
    fun workflowsAreRowsAndFourEqualQuickActionsHaveLabelsWithoutVisibleCaptions() {
        val target = song(1)
        composeRule.setContent {
            MaterialTheme {
                NowPlayingMoreDialog(target,
                    nowPlayingActions(target, setOf(target.membershipKey()), listOf(target), isRated = true), {}, {})
            }
        }
        composeRule.onNodeWithTag(NowPlayingMoreWorkflowRowsTag).onChildren().assertCountEquals(4)
        composeRule.onNodeWithTag(NowPlayingMoreQuickActionsTag).onChildren().assertCountEquals(4)
        val workflowLabels = listOf("Go to artist", "Go to album", "Add to playlist", "Lyrics")
        val workflowTops = workflowLabels.map { composeRule.onNodeWithText(it).fetchSemanticsNode().boundsInRoot.top }
        assertTrue(workflowTops.zipWithNext().all { (first, second) -> first < second })
        val quickLabels = listOf("Remove from favorites", "Rate song", "Sleep Timer", "Track information")
        val quickNodes = quickLabels.map { composeRule.onNodeWithContentDescription(it).fetchSemanticsNode() }
        val quickBounds = quickNodes.map { it.boundsInRoot }
        val density = composeRule.activity.resources.displayMetrics.density
        quickBounds.forEach {
            assertEquals(quickBounds.first().width, it.width, 1f)
            assertTrue(it.width >= 48f * density)
            assertTrue(it.height >= 48f * density)
        }
        assertTrue(quickBounds.zipWithNext().all { (first, second) -> first.left < second.left })
        quickNodes.forEach { assertEquals(Role.Button, it.config[SemanticsProperties.Role]) }
        quickLabels.forEach { composeRule.onNodeWithText(it).assertDoesNotExist() }
        composeRule.onNodeWithContentDescription("Rate song").assertIsSelected()
        composeRule.onNodeWithContentDescription("Remove from favorites").assertIsSelected()
        composeRule.onNodeWithTag("star_rating_control").assertDoesNotExist()
    }

    @Test
    fun timerButtonHasLocalizedButtonAndLiveStateSemanticsWithoutVisibleCaption() {
        val active = mutableStateOf(false)
        val target = song(1)
        val originalDefaultLocale = Locale.getDefault()
        lateinit var originalConfiguration: Configuration
        lateinit var originalApplicationConfiguration: Configuration
        var originalContent = emptyList<View>()
        var host: ComposeView? = null
        var timerClicks = 0
        var dismissals = 0
        composeRule.activityRule.scenario.onActivity { activity ->
            originalConfiguration = Configuration(activity.resources.configuration)
            originalApplicationConfiguration = Configuration(activity.applicationContext.resources.configuration)
            val content = requireNotNull(activity.window.decorView.findViewById<ViewGroup>(android.R.id.content))
            originalContent = (0 until content.childCount).map { requireNotNull(content.getChildAt(it)) }
        }
        try {
            listOf("en", "es").forEachIndexed { index, language ->
                composeRule.activityRule.scenario.onActivity { activity ->
                    // Dispose the old native dialog before creating a differently localized host.
                    host?.disposeComposition()
                    active.value = false
                    val context = ContextThemeWrapper(activity, 0).apply {
                        applyOverrideConfiguration(Configuration(originalConfiguration).apply {
                            setLocale(Locale.forLanguageTag(language))
                        })
                    }
                    // Dialog reads LocalView.context. Give the real host View localized resources;
                    // activity/application resources and the process default locale stay unchanged.
                    val localizedHost = ComposeView(context).apply {
                        setContent {
                            MaterialTheme {
                                NowPlayingMoreDialog(target,
                                    nowPlayingActions(target, emptySet(), listOf(target), isSleepTimerActive = active.value),
                                    onDismiss = { dismissals++ },
                                    onAction = {
                                        assertEquals(NowPlayingAction.SLEEP_TIMER, it)
                                        timerClicks++
                                        active.value = !active.value
                                    })
                            }
                        }
                    }
                    host = localizedHost
                    activity.setContentView(localizedHost)
                }
                val label = if (language == "en") "Sleep Timer" else "Temporizador de apagado"
                val inactive = if (language == "en") "No sleep timer" else "No hay un temporizador activo"
                val activeDescription = if (language == "en") "Sleep timer active" else "Temporizador de apagado activo"
                val button = composeRule.onNodeWithContentDescription(label).assertIsDisplayed().assertIsEnabled()
                Espresso.onView(isRoot()).inRoot(isDialog()).check { view, noViewError ->
                    if (noViewError != null) throw noViewError
                    assertEquals(language, requireNotNull(view).resources.configuration.locales[0].language)
                }
                val inactiveNode = button.fetchSemanticsNode()
                assertEquals(Role.Button, inactiveNode.config[SemanticsProperties.Role])
                assertFalse(inactiveNode.config[SemanticsProperties.Selected])
                assertEquals(inactive, inactiveNode.config[SemanticsProperties.StateDescription])
                composeRule.onNodeWithText(label).assertDoesNotExist()
                button.performClick()
                button.assertIsSelected()
                val activeNode = button.fetchSemanticsNode()
                assertEquals(Role.Button, activeNode.config[SemanticsProperties.Role])
                assertEquals(activeDescription, activeNode.config[SemanticsProperties.StateDescription])
                composeRule.runOnIdle { assertEquals(index * 2 + 1, timerClicks); assertEquals(0, dismissals) }
                composeRule.onNodeWithText(label).assertDoesNotExist()
                composeRule.onNodeWithTag(NowPlayingMoreQuickActionsTag).onChildren().assertCountEquals(4)
                button.performClick()
                val stoppedNode = button.fetchSemanticsNode()
                assertFalse(stoppedNode.config[SemanticsProperties.Selected])
                assertEquals(inactive, stoppedNode.config[SemanticsProperties.StateDescription])
                composeRule.runOnIdle { assertEquals((index + 1) * 2, timerClicks); assertEquals(0, dismissals) }
            }
        } finally {
            composeRule.activityRule.scenario.onActivity { activity ->
                try {
                    host?.disposeComposition()
                } finally {
                    val content = requireNotNull(activity.window.decorView.findViewById<ViewGroup>(android.R.id.content))
                    content.removeAllViews()
                    originalContent.forEach { content.addView(it) }
                }
                assertEquals(originalConfiguration.locales, activity.resources.configuration.locales)
                assertEquals(originalApplicationConfiguration.locales, activity.applicationContext.resources.configuration.locales)
                assertEquals(originalDefaultLocale, Locale.getDefault())
            }
        }
    }

    @Test
    fun rateQuickActionUsesTheExistingRatingDialogAndKeepsItsCapturedSong() {
        val target = song(1)
        val current = mutableStateOf(target)
        val visible = mutableStateOf(true)
        val rating = mutableStateOf<SongRatingDialogState?>(null)
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalSongRatingUi provides SongRatingUiEnvironment(
                    state = SongRatingUiState(dialog = rating.value),
                    onOpen = {
                        assertFalse(visible.value)
                        rating.value = SongRatingDialogState(it, isLoading = false)
                    }
                )) {
                    val openers = rememberNowPlayingWorkflowOpeners(current.value, true) {}
                    if (visible.value) NowPlayingMoreDialog(target,
                        nowPlayingActions(target, emptySet(), listOf(target)), {}, onAction = { action ->
                            performNowPlayingAction(action, target, current.value, listOf(target),
                                onDismiss = { visible.value = false }, onToggleFavorite = {}, onOpenAlbum = {},
                                onOpenLyrics = {}, onRateSong = openers.rateSong)
                        })
                    rating.value?.let {
                        SongRatingDialog(it, onDismiss = { rating.value = null }, onRatingSelected = {}, onSave = {}, onClear = {})
                    }
                }
            }
        }
        composeRule.onNodeWithContentDescription("Rate song").performClick()
        composeRule.onNodeWithTag(NowPlayingMoreDialogTag).assertDoesNotExist()
        composeRule.onNodeWithTag("star_rating_control").assertExists()
        composeRule.runOnIdle { assertSame(target, rating.value?.song); current.value = song(2) }
        composeRule.runOnIdle { assertSame(target, rating.value?.song) }
    }

    @Test
    fun playlistRowUsesTheExistingPickerWithCreationAndCapturedTarget() {
        val target = song(1)
        val current = mutableStateOf(target)
        val visible = mutableStateOf(true)
        val pending = mutableStateOf<Song?>(null)
        composeRule.setContent {
            MaterialTheme {
                val openers = rememberNowPlayingWorkflowOpeners(current.value, true) {
                    assertFalse(visible.value)
                    pending.value = it
                }
                if (visible.value) NowPlayingMoreDialog(target,
                    nowPlayingActions(target, emptySet(), listOf(target)), {}, onAction = { action ->
                        performNowPlayingAction(action, target, current.value, listOf(target),
                            onDismiss = { visible.value = false }, onToggleFavorite = {}, onOpenAlbum = {},
                            onOpenLyrics = {}, onAddToPlaylist = openers.addToPlaylist)
                    })
                pending.value?.let {
                    AddToPlaylistDialog(emptyList(), listOf(it), onDismiss = { pending.value = null },
                        onPlaylistSelected = { _, _ -> }, onCreatePlaylist = { _, _ -> })
                }
            }
        }
        composeRule.onNodeWithText("Add to playlist").performClick()
        composeRule.onNodeWithTag(NowPlayingMoreDialogTag).assertDoesNotExist()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.playlist_add_to_title)).assertExists()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.playlist_create_new)).assertExists()
        composeRule.runOnIdle { assertSame(target, pending.value); current.value = song(2) }
        composeRule.runOnIdle { assertSame(target, pending.value) }
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.settings_cancel)).performClick()
        composeRule.runOnIdle { assertEquals(null, pending.value) }
    }

    @Test
    fun workflowCallbacksRemainStableAndRejectLateTargetsOrUnavailablePlayer() {
        val target = song(1)
        val current = mutableStateOf(target)
        val available = mutableStateOf(true)
        val playlists = mutableListOf<Song>()
        val ratings = mutableListOf<Song>()
        lateinit var openers: NowPlayingWorkflowOpeners
        composeRule.setContent {
            CompositionLocalProvider(LocalSongRatingUi provides SongRatingUiEnvironment(onOpen = { ratings += it })) {
                openers = rememberNowPlayingWorkflowOpeners(current.value, available.value) { playlists += it }
            }
        }
        lateinit var initial: NowPlayingWorkflowOpeners
        composeRule.runOnIdle { initial = openers; openers.addToPlaylist(target); openers.rateSong(target); current.value = song(2) }
        composeRule.runOnIdle {
            assertSame(initial, openers)
            openers.addToPlaylist(target)
            openers.rateSong(target)
            available.value = false
        }
        composeRule.runOnIdle {
            openers.addToPlaylist(current.value)
            openers.rateSong(current.value)
            assertEquals(listOf(target), playlists)
            assertEquals(listOf(target), ratings)
        }
    }

    private fun song(id: Long) = Song(
        id, "Song $id", "Artist", "Album", 1, 120_000L,
        Uri.parse("content://media/external/audio/media/$id"), "/music/$id.flac", "/music", null,
        volumeName = "external"
    )
}
