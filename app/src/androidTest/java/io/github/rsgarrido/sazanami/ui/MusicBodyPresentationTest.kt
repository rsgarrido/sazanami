package io.github.rsgarrido.sazanami.ui

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.controller.ListeningHistoryReconciliationUiState
import io.github.rsgarrido.sazanami.controller.SpotifyImportUiState
import io.github.rsgarrido.sazanami.data.FolderSelectionMode
import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.data.Playlist
import io.github.rsgarrido.sazanami.data.PlaylistSong
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.buildFolderBrowseIndex
import io.github.rsgarrido.sazanami.data.membershipKey
import io.github.rsgarrido.sazanami.data.preferences.AppAppearance
import io.github.rsgarrido.sazanami.data.preferences.AppFont
import io.github.rsgarrido.sazanami.mediaaccess.MediaAccessPolicy
import io.github.rsgarrido.sazanami.player.RepeatMode
import io.github.rsgarrido.sazanami.player.audio.AudioOffloadPreference
import io.github.rsgarrido.sazanami.player.audio.AudioOutputUiState
import io.github.rsgarrido.sazanami.player.replaygain.ReplayGainMode
import io.github.rsgarrido.sazanami.ui.equalizer.EqualizerScreenState
import io.github.rsgarrido.sazanami.ui.equalizer.EqualizerUiActions
import io.github.rsgarrido.sazanami.ui.library.FolderBrowseScrollStateHolder
import io.github.rsgarrido.sazanami.ui.library.LibrarySelectionUiEnvironment
import io.github.rsgarrido.sazanami.ui.library.LibraryTab
import io.github.rsgarrido.sazanami.ui.library.LocalLibrarySelectionUi
import io.github.rsgarrido.sazanami.ui.library.SearchCategory
import io.github.rsgarrido.sazanami.ui.navigation.MainDestination
import io.github.rsgarrido.sazanami.ui.navigation.PlaybackLaunchContext
import io.github.rsgarrido.sazanami.ui.navigation.capturePlaybackLaunchContext
import io.github.rsgarrido.sazanami.ui.player.modern.ModernAppearanceChoice
import io.github.rsgarrido.sazanami.ui.player.modern.ModernArtworkTransitionStyle
import io.github.rsgarrido.sazanami.ui.player.modern.ModernPlayerAppearance
import io.github.rsgarrido.sazanami.ui.player.theme.defaultTokens
import io.github.rsgarrido.sazanami.ui.playlist.PlaylistSnackbarActions
import io.github.rsgarrido.sazanami.ui.queue.QueueSnackbarActions
import io.github.rsgarrido.sazanami.ui.settings.ListeningHistoryReconciliationUiActions
import io.github.rsgarrido.sazanami.ui.settings.SpotifyImportUiActions
import io.github.rsgarrido.sazanami.ui.state.LibraryAppearanceUiState
import io.github.rsgarrido.sazanami.ui.state.LibrarySelectionEntity
import io.github.rsgarrido.sazanami.ui.state.LibrarySelectionUiState
import io.github.rsgarrido.sazanami.ui.state.ListeningAnalyticsUiState
import io.github.rsgarrido.sazanami.ui.state.PlaybackProgressUiState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Exercises the prepared slot with external owners, without constructing a player/morph host. */
class MusicBodyPresentationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun homeUsesTheOwnersListStateAndFinalPaddingAcrossDestinationSwitches() {
        val fixture = mount()
        composeRule.onNodeWithText(text(R.string.app_name)).assertExists()
        composeRule.runOnIdle {
            assertEquals(fixture.expectedPaddingPx, fixture.homeScroll.layoutInfo.afterContentPadding)
            fixture.padding.value = 91.dp
        }
        composeRule.runOnIdle {
            assertEquals(fixture.expectedPaddingPx, fixture.homeScroll.layoutInfo.afterContentPadding)
            fixture.overlays.isSettingsScreenVisible.value = true
        }
        composeRule.onNodeWithText(text(R.string.settings_screen_title)).assertExists()
        composeRule.runOnIdle { fixture.overlays.isSettingsScreenVisible.value = false }
        composeRule.onNodeWithText(text(R.string.app_name)).assertExists()
        composeRule.runOnIdle {
            assertEquals(fixture.expectedPaddingPx, fixture.homeScroll.layoutInfo.afterContentPadding)
        }
    }

    @Test
    fun libraryAndSearchPlaybackCaptureTheLiveOwnerBeforeDispatch() {
        val fixture = mount()
        composeRule.runOnIdle { fixture.navigation.mainDestination.value = MainDestination.LIBRARY }
        composeRule.onAllNodesWithText(fixture.songs.first().title)[0].performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("capture", "play"), fixture.calls)
            assertEquals(PlaybackLaunchContext.LibrarySection(LibraryTab.SONGS), fixture.launches.single())
            assertSame(fixture.songs.first(), fixture.playedSong)
            fixture.calls.clear()
            fixture.navigation.mainDestination.value = MainDestination.SEARCH
            fixture.navigation.searchCategory.value = SearchCategory.SONGS
        }
        composeRule.onNode(hasSetTextAction()).performTextReplacement("Boundary track")
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(fixture.songs.first().title).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText(fixture.songs.first().title)[0].performClick()
        composeRule.runOnIdle {
            assertEquals("Boundary track", fixture.navigation.searchQuery.value)
            assertEquals(listOf("capture", "play"), fixture.calls)
            assertEquals(PlaybackLaunchContext.Search("Boundary track"), fixture.launches.last())
            assertEquals(fixture.songs, fixture.playedSongs)
        }
        composeRule.onNodeWithContentDescription(text(R.string.library_search_clear)).performClick()
        composeRule.runOnIdle { assertEquals("", fixture.navigation.searchQuery.value) }
    }

    @Test
    fun searchArtistAndAlbumEntriesKeepTheirReturnOriginAndSelectionCleanup() {
        val fixture = mount()
        composeRule.runOnIdle {
            fixture.navigation.mainDestination.value = MainDestination.SEARCH
            fixture.navigation.searchCategory.value = SearchCategory.ARTISTS
            fixture.navigation.searchQuery.value = "Boundary artist"
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(entityResult("Boundary artist")).fetchSemanticsNodes().isNotEmpty()
        }
        val beforeArtist = composeRule.runOnIdle { fixture.selectionClears }
        composeRule.onNode(entityResult("Boundary artist")).performClick()
        composeRule.runOnIdle {
            assertEquals("Boundary artist", fixture.navigation.selectedArtistName.value)
            assertEquals(DetailEntryOrigin.SEARCH, fixture.navigation.artistDetailOrigin.value)
            assertEquals(beforeArtist + 1, fixture.selectionClears)
        }
        composeRule.onNodeWithContentDescription(text(R.string.common_back)).performClick()
        composeRule.runOnIdle {
            assertEquals(MainDestination.SEARCH, fixture.navigation.mainDestination.value)
            assertEquals("Boundary artist", fixture.navigation.searchQuery.value)
            fixture.navigation.searchCategory.value = SearchCategory.ALBUMS
            fixture.navigation.searchQuery.value = "Boundary album"
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(entityResult("Boundary album")).fetchSemanticsNodes().isNotEmpty()
        }
        val beforeAlbum = composeRule.runOnIdle { fixture.selectionClears }
        composeRule.onNode(entityResult("Boundary album")).performClick()
        composeRule.runOnIdle {
            assertEquals(fixture.songs.first().folderPath, fixture.navigation.selectedAlbumKey.value)
            assertEquals(DetailEntryOrigin.SEARCH, fixture.navigation.albumDetailOrigin.value)
            assertEquals(beforeAlbum + 1, fixture.selectionClears)
        }
        composeRule.onNode(hasScrollToIndexAction())
            .performScrollToNode(hasText(fixture.songs.first().title))
        composeRule.onAllNodesWithText(fixture.songs.first().title)[0].performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("capture", "play"), fixture.calls)
            assertEquals(PlaybackLaunchContext.AlbumDetail(fixture.songs.first().folderPath),
                fixture.launches.single())
        }
        composeRule.onNodeWithContentDescription(text(R.string.common_back)).performClick()
        composeRule.runOnIdle {
            assertEquals(MainDestination.SEARCH, fixture.navigation.mainDestination.value)
            assertEquals("Boundary album", fixture.navigation.searchQuery.value)
            assertEquals(null, fixture.navigation.selectedAlbumKey.value)
        }
    }

    @Test
    fun playlistEntryPlaybackAndBackUseTheExistingSelectionCoordinator() {
        val fixture = mount()
        composeRule.runOnIdle {
            fixture.navigation.mainDestination.value = MainDestination.LIBRARY
            fixture.navigation.selectedLibraryTab.value = LibraryTab.PLAYLISTS
        }
        composeRule.onNodeWithText(fixture.playlist.name).performClick()
        composeRule.runOnIdle {
            assertEquals(fixture.playlist.playlistId, fixture.navigation.selectedPlaylistId.value)
            assertEquals(DetailEntryOrigin.LIBRARY, fixture.navigation.playlistDetailOrigin.value)
            assertEquals(listOf("playlist-selected"), fixture.calls)
            fixture.calls.clear()
        }
        composeRule.onNode(hasScrollToIndexAction())
            .performScrollToNode(hasText(fixture.songs.first().title))
        composeRule.onNodeWithText(fixture.songs.first().title).performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("capture", "play"), fixture.calls)
            assertEquals(PlaybackLaunchContext.PlaylistDetail(fixture.playlist.playlistId),
                fixture.launches.single())
        }
        composeRule.onNodeWithContentDescription(text(R.string.common_back)).performClick()
        composeRule.runOnIdle {
            assertEquals(null, fixture.navigation.selectedPlaylistId.value)
            assertEquals(MainDestination.LIBRARY, fixture.navigation.mainDestination.value)
        }
    }

    @Test
    fun folderEntryPlaybackAndParentBackKeepExternalNavigationAndScrollHolder() {
        val fixture = mount()
        val root = fixture.folderIndex.roots.single()
        val rootList = fixture.folderScroll.listStateFor(null)
        composeRule.runOnIdle {
            fixture.navigation.mainDestination.value = MainDestination.LIBRARY
            fixture.navigation.selectedLibraryTab.value = LibraryTab.FOLDERS
        }
        composeRule.onNodeWithText(root.displayName).performClick()
        composeRule.runOnIdle { assertEquals(root.id, fixture.navigation.selectedFolderId.value) }
        composeRule.onAllNodesWithText(fixture.songs.first().title)[0].performClick()
        composeRule.runOnIdle {
            assertEquals(PlaybackLaunchContext.FolderDetail(root.id), fixture.launches.single())
            assertEquals(listOf("capture", "play"), fixture.calls)
        }
        composeRule.onNodeWithContentDescription(text(R.string.library_folder_back_parent)).performClick()
        composeRule.runOnIdle {
            assertEquals(null, fixture.navigation.selectedFolderId.value)
            assertSame(rootList, fixture.folderScroll.listStateFor(null))
            assertEquals(2, fixture.selectionClears)
        }
    }

    @Test
    fun settingsHelpAndSleepTimerUseTheSuppliedOwnersAndCallbacks() {
        val fixture = mount()
        composeRule.runOnIdle { fixture.overlays.isSettingsScreenVisible.value = true }
        composeRule.onNodeWithContentDescription(text(R.string.settings_open_sleep_timer))
            .performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(listOf("sleep-timer"), fixture.calls) }
        composeRule.onNodeWithContentDescription(text(R.string.settings_help_open_tips))
            .performScrollTo().performClick()
        composeRule.onNodeWithText(text(R.string.tips_title)).assertExists()
        composeRule.runOnIdle {
            assertTrue(fixture.overlays.isTipsHelpScreenVisible.value)
        }
        composeRule.onNodeWithContentDescription(text(R.string.settings_help_back)).performClick()
        composeRule.onNodeWithContentDescription(text(R.string.settings_help_open_about))
            .performScrollTo().performClick()
        composeRule.onNodeWithText(text(R.string.about_title)).assertExists()
        composeRule.onNodeWithContentDescription(text(R.string.settings_help_back)).performClick()
        composeRule.onNodeWithContentDescription(text(R.string.settings_back))
            .performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("sleep-timer", "close-settings"), fixture.calls)
        }
    }

    @Test
    fun statisticsImportAndReconciliationMountAndKeepTheirReturnCallbacks() {
        val fixture = mount()
        composeRule.onNodeWithContentDescription(text(R.string.statistics_home_button_description)).performClick()
        composeRule.onNodeWithText(text(R.string.statistics_title)).assertExists()
        composeRule.runOnIdle {
            fixture.overlays.isStatisticsScreenVisible.value = false
            fixture.overlays.isSettingsScreenVisible.value = true
        }
        composeRule.onNodeWithContentDescription(text(R.string.settings_open_import_history))
            .performScrollTo().performClick()
        composeRule.onNodeWithText(text(R.string.history_import_title)).assertExists()
        composeRule.runOnIdle { assertEquals(listOf("import-enter"), fixture.calls) }
        composeRule.onNodeWithContentDescription(text(R.string.history_import_back)).performClick()
        composeRule.runOnIdle { assertTrue(fixture.overlays.isSettingsScreenVisible.value) }
        composeRule.onNodeWithContentDescription(text(R.string.settings_open_imported_matching))
            .performScrollTo().performClick()
        composeRule.onNodeWithText(text(R.string.history_match_title)).assertExists()
        composeRule.onNodeWithContentDescription(text(R.string.history_import_back)).performClick()
        composeRule.runOnIdle { assertTrue(fixture.overlays.isSettingsScreenVisible.value) }
    }

    @Test
    fun replacingBodyDoesNotRecreateExternalScrollOrSavedNavigationOwners() {
        val fixture = mount()
        val home = fixture.homeScroll
        val settings = fixture.settingsScroll
        val statistics = fixture.statisticsScroll
        composeRule.runOnIdle { home.requestScrollToItem(1, 17) }
        val position = composeRule.runOnIdle {
            home.firstVisibleItemIndex to home.firstVisibleItemScrollOffset
        }
        composeRule.runOnIdle {
            fixture.navigation.searchQuery.value = "saved query"
            fixture.bodyMounted.value = false
        }
        composeRule.onNodeWithText("Replacement content").assertExists()
        composeRule.onNodeWithText(text(R.string.app_name)).assertDoesNotExist()
        composeRule.runOnIdle { fixture.bodyMounted.value = true }
        composeRule.runOnIdle {
            assertEquals(position, home.firstVisibleItemIndex to home.firstVisibleItemScrollOffset)
            assertSame(home, fixture.homeScroll)
            assertSame(settings, fixture.settingsScroll)
            assertSame(statistics, fixture.statisticsScroll)
            assertEquals("saved query", fixture.navigation.searchQuery.value)
        }
    }

    @Test
    fun selectionEntryAndToggleUpdateTheSuppliedStateWithoutStartingPlayback() {
        val fixture = mount()
        composeRule.runOnIdle { fixture.navigation.mainDestination.value = MainDestination.LIBRARY }
        val song = fixture.songs.first()
        composeRule.onNodeWithText(song.title).performTouchInput { longClick() }
        composeRule.runOnIdle {
            assertEquals(LibrarySelectionEntity.SONG, fixture.selectionState.value.entity)
            assertEquals(setOf(song.membershipKey()), fixture.selectionState.value.selectedKeys)
            assertTrue(fixture.calls.isEmpty())
        }
        composeRule.onNodeWithText(song.title).performClick()
        composeRule.runOnIdle {
            assertTrue(fixture.selectionState.value.selectedKeys.isEmpty())
            assertTrue(fixture.calls.isEmpty())
        }
        composeRule.onNodeWithContentDescription(text(R.string.settings_screen_title)).performClick()
        composeRule.runOnIdle {
            assertEquals(LibrarySelectionUiState(), fixture.selectionState.value)
            assertTrue(fixture.overlays.isSettingsScreenVisible.value)
        }
    }

    @Test
    fun savedSearchRouteSurvivesStateRestorationWithThePreparedSlot() {
        val fixture = Fixture()
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { fixture.Content() }
        composeRule.runOnIdle {
            fixture.navigation.mainDestination.value = MainDestination.SEARCH
            fixture.navigation.searchCategory.value = SearchCategory.SONGS
        }
        composeRule.onNode(hasSetTextAction()).performTextReplacement("Boundary track")
        restoration.emulateSavedInstanceStateRestore()
        composeRule.runOnIdle {
            assertEquals(MainDestination.SEARCH, fixture.navigation.mainDestination.value)
            assertEquals(SearchCategory.SONGS, fixture.navigation.searchCategory.value)
            assertEquals("Boundary track", fixture.navigation.searchQuery.value)
        }
        composeRule.onNode(hasSetTextAction()).performTextReplacement("changed after restore")
        composeRule.runOnIdle { assertEquals("changed after restore", fixture.navigation.searchQuery.value) }
    }

    private fun entityResult(label: String) =
        hasText(label) and hasClickAction() and !hasSetTextAction()

    private fun text(id: Int) = composeRule.activity.getString(id)

    private fun mount(): Fixture = Fixture().also { fixture ->
        composeRule.setContent { fixture.Content() }
    }

    private class Fixture {
        val songs = (1L..3L).map { id ->
            Song(id, "Boundary track $id", "Boundary artist", "Boundary album", id.toInt(),
                60_000L, Uri.parse("content://media/external/audio/media/$id"),
                "/storage/emulated/0/Music/$id.mp3", "/storage/emulated/0/Music", null,
                volumeName = "external_primary", relativePath = "Music/")
        }
        val playlist = Playlist(42L, "Boundary playlist", songs.size)
        val playlistSongs = songs.mapIndexed { index, song ->
            PlaylistSong(song.id, playlist.playlistId, song.membershipKey(), index,
                song.title, song.artist, song.album, song.duration, resolvedSong = song)
        }
        val folderIndex = buildFolderBrowseIndex(songs)
        val folderScroll = FolderBrowseScrollStateHolder()
        val homeScroll = LazyListState()
        val statisticsScroll = LazyListState()
        val settingsScroll = ScrollState(0)
        val progress = MutableStateFlow(PlaybackProgressUiState())
        val padding = mutableStateOf(37.dp)
        val bodyMounted = mutableStateOf(true)
        val calls = mutableListOf<String>()
        val launches = mutableListOf<PlaybackLaunchContext>()
        var playedSong: Song? = null
        var playedSongs: List<Song> = emptyList()
        var selectionClears = 0
        var expectedPaddingPx = 0
        lateinit var navigation: MusicNavigationState
        lateinit var overlays: MusicOverlayState
        lateinit var help: SettingsHelpNavigation
        val selectionState = mutableStateOf(LibrarySelectionUiState())
        val queueActions = QueueSnackbarActions(emptySet(), {}, {}, { _, _ -> }, { _, _ -> })
        val playlistActions = PlaylistSnackbarActions({ _, _ -> }, { _, _ -> }, {})
        val equalizerActions = EqualizerUiActions(
            onBack = {},
            onEnabledChanged = {},
            onModeChanged = {},
            onPreviewBandGain = { _, _ -> },
            onCommitBandGain = { _, _ -> },
            onCancelBandGainPreview = { _, _ -> },
            onPreviewPreamp = {},
            onCommitPreamp = {},
            onCancelPreampPreview = {},
            onAutomaticHeadroomChanged = {},
            onLimiterEnabledChanged = {},
            onPreviewLimiterCeiling = {},
            onCommitLimiterCeiling = {},
            onCancelLimiterCeilingPreview = {},
            onResetLimiterMeters = {},
            onApplyBuiltInPreset = {},
            onApplyUserPreset = {},
            onSaveUserPreset = {},
            onRenameUserPreset = { _, _ -> },
            onDeleteUserPreset = {},
            onSelectParametricFilter = {},
            onAddParametricFilter = {},
            onPreviewParametricFilter = {},
            onCommitParametricFilter = {},
            onCancelParametricFilterPreview = {},
            onMoveParametricFilter = { _, _ -> },
            onDeleteParametricFilter = {},
            onApplyParametricFlatPreset = {},
            onApplyParametricUserPreset = {},
            onSaveParametricUserPreset = {},
            onRenameParametricUserPreset = { _, _ -> },
            onDeleteParametricUserPreset = {},
            onImportFromFile = {},
            onPasteEqText = {},
            onExportCurrentEqText = {},
            onCopyCurrentEqText = {},
            onExportCurrentNative = {},
            onExportParametricPresetText = {},
            onExportParametricPresetNative = {},
            onDismissImportPreview = {},
            onUpdateImportPreview = {},
            onReplaceWithImportedProfile = {},
            onSaveImportedProfile = {},
            onResetToFlat = {},
            onComparisonBypassedChanged = {},
        )
        val importActions = SpotifyImportUiActions(
            onEnter = { calls += "import-enter" },
            onFilesSelected = {},
            onAnalyze = {},
            onCancelAnalysis = {},
            onImport = {},
            onCancelImport = {},
            onRetry = {},
            onChangeFiles = {},
            onCleanStaleImport = {},
            onImportMore = {},
            onDone = {},
            onBack = {},
        )
        val reconciliationActions = ListeningHistoryReconciliationUiActions(
            onEnter = {},
            onBack = {},
            onRetry = {},
            onTabSelected = {},
            onBrowseModeSelected = {},
            onBrowseQueryChanged = {},
            onSortSelected = {},
            onReviewFilterSelected = {},
            onToggleExpanded = {},
            onToggleAlbum = {},
            onToggleArtist = {},
            onToggleSelected = {},
            onSelectItems = {},
            onClearSelection = {},
            onLinkSelectedRequested = {},
            onSkip = {},
            onCandidateSelected = { _, _ -> },
            onSearchRequested = {},
            onSearchQueryChanged = {},
            onSearchDismissed = {},
            onUnlinkRequested = {},
            onConfirmationCancelled = {},
            onConfirmed = {},
            onMessageDismissed = {},
        )

        private fun captureLaunchContext() {
            calls += "capture"
            launches += capturePlaybackLaunchContext(
                navigation.mainDestination.value, navigation.selectedLibraryTab.value,
                navigation.selectedAlbumKey.value, navigation.selectedArtistName.value,
                navigation.selectedGenreKey.value, navigation.selectedPlaylistId.value,
                navigation.searchQuery.value, navigation.selectedFolderId.value
            )
        }

        @Composable
        fun Content() {
            navigation = rememberMusicNavigationState()
            overlays = rememberMusicOverlayState()
            help = remember(overlays) { SettingsHelpNavigation(overlays) }
            expectedPaddingPx = with(LocalDensity.current) { padding.value.roundToPx() }
            val selectionUi = LibrarySelectionUiEnvironment(
                state = selectionState.value,
                allSongs = songs,
                onEnter = { entity, key -> selectionState.value = LibrarySelectionUiState(entity, setOf(key)) },
                onToggle = { entity, key ->
                    val keys = selectionState.value.selectedKeys
                    selectionState.value = LibrarySelectionUiState(entity,
                        if (key in keys) keys - key else keys + key)
                },
                onClear = { selectionClears++; selectionState.value = LibrarySelectionUiState() }
            )
            val body = prepareMusicBodyPresentation(
                songs = songs,
                mediaAccessState = MediaAccessPolicy.evaluate(22, emptySet(), emptySet(), emptySet()),
                isLibraryLoading = false,
                isLibraryRefreshing = false,
                lastLibraryRefreshSummary = null,
                libraryErrorMessage = null,
                onRequestAudioAccess = {},
                onRequestArtworkAccess = {},
                onOpenAppSettings = {},
                currentSong = null,
                isPlayerConnected = false,
                previousHistoryCount = 0,
                forwardHistoryCount = 0,
                isPlaying = false,
                isShuffleEnabled = false,
                repeatMode = RepeatMode.OFF,
                playbackProgressUiState = progress,
                onSongClick = { song, context -> calls += "play"; playedSong = song; playedSongs = context },
                onPlaySongsClick = { context, _ -> calls += "play-many"; playedSongs = context },
                onPlayPauseClick = {},
                onPreviousClick = {},
                onNextClick = {},
                onSeekChange = {},
                onShuffleClick = {},
                onRepeatClick = {},
                queuedSongs = emptyList(),
                upcomingSongs = emptyList(),
                onRemoveFromQueueClick = {},
                onMoveQueueItemUpClick = {},
                onMoveQueueItemDownClick = {},
                onClearQueueClick = {},
                libraryFolders = emptyList(),
                folderSelectionMode = FolderSelectionMode.ALL,
                selectedLibraryFolders = emptySet(),
                excludedLibraryFolders = emptySet(),
                onScanLibraryClick = {},
                onLibraryFolderToggle = {},
                onSelectAllLibraryFolders = {},
                onClearSelectedLibraryFolders = {},
                favoriteMembershipKeys = emptySet(),
                unresolvedFavoriteCount = 0,
                unresolvedPlaylistRowCount = 0,
                unresolvedListeningHistoryCount = 0,
                onToggleFavoriteClick = {},
                playlists = listOf(playlist),
                playlistFolders = emptyList(),
                selectedPlaylistStateId = navigation.selectedPlaylistId.value,
                selectedPlaylistName = "Boundary playlist",
                selectedPlaylistSongs = playlistSongs,
                isSelectedPlaylistLoading = false,
                onCreatePlaylistFolderClick = {},
                onRenamePlaylistFolderClick = { _, _ -> },
                onDeletePlaylistFolderClick = {},
                onMovePlaylistToFolderClick = { _, _ -> },
                onRenamePlaylistClick = { _, _ -> },
                onDeletePlaylistClick = {},
                onExportPlaylistClick = {},
                onImportPlaylistClick = {},
                onChangePlaylistArtwork = { _, _ -> },
                onResetPlaylistArtwork = {},
                onExportBackupClick = {},
                onRestoreBackupClick = {},
                onPlaylistSelected = { calls += "playlist-selected" },
                onReorderPlaylistSongs = { _, _ -> },
                isSleepTimerActive = false,
                sleepTimerDisplayText = "",
                recentlyPlayedSongs = songs,
                recentlyAddedLibrarySongs = songs,
                selectedPlayerTheme = PlayerTheme.DEFAULT,
                selectedAppFont = AppFont.SAZANAMI,
                onAppFontSelected = {},
                selectedAppAppearance = AppAppearance.SYSTEM,
                onAppAppearanceSelected = {},
                selectedPlayerThemeTokens = PlayerTheme.DEFAULT.defaultTokens(),
                onPlayerThemeSelected = {},
                onUpdatePlayerThemeTokenOverride = { _, _, _ -> },
                onResetPlayerThemeTokenOverrides = {},
                selectedModernArtworkTransitionStyle = ModernArtworkTransitionStyle.SLIDE,
                onModernArtworkTransitionStyleSelected = {},
                selectedModernPlayerAppearance = ModernPlayerAppearance.Default,
                activeModernAppearanceChoice = ModernAppearanceChoice.MY_PLAYER,
                onModernAppearanceChoiceSelected = {},
                onModernPlayerAppearanceEdited = {},
                onResetModernPlayerAppearance = {},
                selectedReplayGainMode = ReplayGainMode.OFF,
                onReplayGainModeSelected = {},
                selectedAudioOffloadPreference = AudioOffloadPreference.DISABLED,
                onAudioOffloadPreferenceSelected = {},
                smoothPlayPauseEnabled = false,
                onSmoothPlayPauseEnabledChanged = {},
                crossfadeEnabled = false,
                onCrossfadeEnabledChanged = {},
                crossfadeDurationMs = 0,
                onCrossfadeDurationMsChanged = {},
                preserveAlbumTransitions = false,
                onPreserveAlbumTransitionsChanged = {},
                audioOutputUiState = AudioOutputUiState(),
                equalizerScreenState = EqualizerScreenState(),
                equalizerActions = equalizerActions,
                libraryAppearanceUiState = LibraryAppearanceUiState(),
                onLibraryViewOptionSelected = { _, _ -> },
                mostPlayedSongs = emptyList(),
                listeningAnalyticsUiState = ListeningAnalyticsUiState(),
                showNotCountedPlays = false,
                onShowNotCountedPlaysChanged = {},
                onListeningAnalyticsPresetSelected = {},
                onListeningAnalyticsCustomRangeSelected = { _, _ -> },
                onRetryListeningAnalytics = {},
                onListeningAnalyticsTrendMetricSelected = {},
                onListeningAnalyticsRankingCategorySelected = {},
                spotifyImportUiState = SpotifyImportUiState.Landing,
                reconciliationUiState = ListeningHistoryReconciliationUiState.Loading,
                reconciliationActions = reconciliationActions,
                spotifyImportActions = importActions,
                navigationState = navigation,
                librarySelectionUi = selectionUi,
                isPlayerExpanded = false,
                folderBrowseIndex = folderIndex,
                folderBrowseScrollStateHolder = folderScroll,
                resolvedFolderId = navigation.selectedFolderId.value,
                recentlyAddedSongIds = emptySet(),
                settingsHelpNavigation = help,
                settingsScrollState = settingsScroll,
                homeListState = homeScroll,
                statisticsListState = statisticsScroll,
                queueSnackbarActions = queueActions,
                playlistSnackbarActions = playlistActions,
                addPlaylistToQueue = {},
                onCloseSettings = { calls += "close-settings"; overlays.isSettingsScreenVisible.value = false },
                clearPlaylistSelection = { returnToOrigin -> if (returnToOrigin) navigation.closePlaylist() else navigation.clearPlaylist() },
                onRecordPlaybackLaunchContext = ::captureLaunchContext,
                onExpandPlayer = {},
                onShowSleepTimer = { calls += "sleep-timer" },
                onShowAddToPlaylist = {},
                onShowBulkAddToPlaylist = {},
                onShowCreatePlaylist = {},
                onEditSongTagsClick = {},
                onEditAlbumMetadataClick = {},
                onBatchMetadataClick = {},
                folderScreenVisible = overlays.isFolderScreenVisible,
                settingsScreenVisible = overlays.isSettingsScreenVisible,
                tipsHelpScreenVisible = overlays.isTipsHelpScreenVisible,
                aboutScreenVisible = overlays.isAboutScreenVisible,
                diagnosticsScreenVisible = overlays.isDiagnosticsScreenVisible,
                equalizerScreenVisible = overlays.isEqualizerScreenVisible,
                statisticsScreenVisible = overlays.isStatisticsScreenVisible,
                listeningHistoryImportVisible = overlays.isListeningHistoryImportVisible,
                listeningHistoryReconciliationVisible = overlays.isListeningHistoryReconciliationVisible,
            )
            MaterialTheme {
                CompositionLocalProvider(LocalLibrarySelectionUi provides selectionUi) {
                    if (bodyMounted.value) body(padding.value) else Text("Replacement content")
                }
            }
        }
    }
}
