package io.github.rsgarrido.sazanami.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.membershipKey
import io.github.rsgarrido.sazanami.player.RepeatMode
import io.github.rsgarrido.sazanami.ui.library.LibraryOrganizeButton
import io.github.rsgarrido.sazanami.ui.library.LibrarySearchBar
import io.github.rsgarrido.sazanami.ui.library.LibrarySortOption
import io.github.rsgarrido.sazanami.ui.library.LibrarySongFilterState
import io.github.rsgarrido.sazanami.ui.library.displayTitleFor
import io.github.rsgarrido.sazanami.ui.library.librarySortOptionsFor
import io.github.rsgarrido.sazanami.ui.library.showsQuickRateAction
import io.github.rsgarrido.sazanami.ui.ratings.LocalSongRatingUi
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import io.github.rsgarrido.sazanami.ui.library.LibrarySortState
import io.github.rsgarrido.sazanami.ui.library.LibraryTab
import io.github.rsgarrido.sazanami.ui.player.PlayerCard
import io.github.rsgarrido.sazanami.ui.player.PlayerMorphState
import io.github.rsgarrido.sazanami.ui.player.modern.DefaultPlayerMorphBounds
import io.github.rsgarrido.sazanami.ui.player.classicwheel.ClassicWheelMorphBounds
import io.github.rsgarrido.sazanami.ui.player.retrorack.RetroRackMorphBounds
import io.github.rsgarrido.sazanami.ui.player.pocketflip.PocketFlipMorphBounds
import io.github.rsgarrido.sazanami.ui.player.pocketcassette.PocketCassetteMorphBounds
import io.github.rsgarrido.sazanami.ui.player.pocketdisc.PocketDiscMorphBounds
import io.github.rsgarrido.sazanami.ui.player.mini.DefaultMiniPlayerMorphCallbacks
import io.github.rsgarrido.sazanami.ui.player.SleepTimerStatusBanner
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokens

@Composable
fun MusicScreenHeader(
    title: String = stringResource(R.string.app_name),
    onBackClick: (() -> Unit)? = null,
    onSettingsClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    backContentDescription: String = "Back to Home",
    backTitleSpacing: Dp = 0.dp,
    batchMetadataAction: (@Composable () -> Unit)? = null,
    viewModeAction: (@Composable () -> Unit)? = null,
    organizeAction: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(backTitleSpacing)
        ) {
            if (onBackClick != null) {
                AppShellIconButton(
                    onClick = onBackClick,
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = backContentDescription
                )
            }

            Text(
                text = title,
                style = AppShellTypography.ScreenTitle,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            batchMetadataAction?.invoke()

            viewModeAction?.invoke()

            organizeAction?.invoke()

            if (onSettingsClick != null) {
                AppShellIconButton(
                    onClick = onSettingsClick,
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "Settings"
                )
            }
        }
    }
}

@Composable
fun AppShellIconButton(
    onClick: () -> Unit,
    imageVector: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    accented: Boolean = false
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (accented) {
            AppShellAccent.copy(alpha = 0.15f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = if (accented) {
            AppShellAccent
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        border = BorderStroke(
            1.dp,
            if (accented) {
                AppShellAccent.copy(alpha = 0.36f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
            }
        )
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun MiniPlayerSection(
    currentSong: Song?,
    isPlaying: Boolean,
    isShuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    currentPosition: Int,
    duration: Int,
    selectedPlayerTheme: PlayerTheme,
    selectedPlayerThemeTokens: PlayerThemeTokens,
    playerMorphState: PlayerMorphState,
    favoriteMembershipKeys: Set<String>,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onSeekChange: (Int) -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onExpandClick: () -> Unit,
    onOpenUpNextClick: () -> Unit,
    onOpenQueueHubClick: () -> Unit,
    onToggleFavoriteClick: (Song) -> Unit,
    isSleepTimerActive: Boolean,
    sleepTimerDisplayText: String,
    onSleepTimerClick: () -> Unit,
    modifier: Modifier = Modifier,
    onMiniPlayerBoundsChanged: (Rect) -> Unit = {},
    defaultMorphBounds: DefaultPlayerMorphBounds? = null,
    classicMorphBounds: ClassicWheelMorphBounds? = null,
    retroRackMorphBounds: RetroRackMorphBounds? = null,
    pocketFlipMorphBounds: PocketFlipMorphBounds? = null,
    pocketCassetteMorphBounds: PocketCassetteMorphBounds? = null,
    pocketDiscMorphBounds: PocketDiscMorphBounds? = null,
    defaultMorphCallbacks: DefaultMiniPlayerMorphCallbacks? = null,
    morphOwnsVisuals: Boolean = false
) {
    Column(modifier = modifier) {
        SleepTimerStatusBanner(
            isSleepTimerActive = isSleepTimerActive,
            sleepTimerDisplayText = sleepTimerDisplayText,
            onSleepTimerClick = onSleepTimerClick
        )

        PlayerCard(
            currentSong = currentSong,
            isPlaying = isPlaying,
            isExpanded = false,
            isShuffleEnabled = isShuffleEnabled,
            repeatMode = repeatMode,
            currentPosition = currentPosition,
            duration = duration,
            selectedPlayerTheme = selectedPlayerTheme,
            selectedPlayerThemeTokens = selectedPlayerThemeTokens,
            playerMorphState = playerMorphState,
            onPlayPauseClick = onPlayPauseClick,
            onPreviousClick = onPreviousClick,
            onNextClick = onNextClick,
            onSeekChange = onSeekChange,
            onShuffleClick = onShuffleClick,
            onRepeatClick = onRepeatClick,
            onExpandClick = onExpandClick,
            onCollapseClick = {},
            onOpenUpNextClick = onOpenUpNextClick,
            onOpenQueueHubClick = onOpenQueueHubClick,
            isCurrentSongFavorite = currentSong?.let { song ->
                song.membershipKey() in favoriteMembershipKeys
            } == true,
            onToggleFavoriteClick = onToggleFavoriteClick,
            onMiniPlayerBoundsChanged = onMiniPlayerBoundsChanged,
            defaultMorphBounds = defaultMorphBounds,
            classicMorphBounds = classicMorphBounds,
            retroRackMorphBounds = retroRackMorphBounds,
            pocketFlipMorphBounds = pocketFlipMorphBounds,
            pocketCassetteMorphBounds = pocketCassetteMorphBounds,
            pocketDiscMorphBounds = pocketDiscMorphBounds,
            defaultMorphCallbacks = defaultMorphCallbacks,
            morphOwnsVisuals = morphOwnsVisuals
        )
    }
}

@Composable
fun LibrarySearchControl(
    selectedLibraryTab: LibraryTab,
    isSearchVisible: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit
) {
    if (selectedLibraryTab != LibraryTab.QUEUE && isSearchVisible) {
        LibrarySearchBar(
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange
        )
    }
}

@Composable
fun LibraryOrganizeAction(
    songs: List<Song>,
    selectedLibraryTab: LibraryTab,
    selectedArtistName: String?,
    selectedAlbumKey: String?,
    selectedSongSortState: LibrarySortState,
    selectedArtistSortState: LibrarySortState,
    selectedAlbumSortState: LibrarySortState,
    selectedFavoriteSortState: LibrarySortState,
    selectedSongFilterState: LibrarySongFilterState,
    onSongSortStateChanged: (LibrarySortState) -> Unit,
    onArtistSortStateChanged: (LibrarySortState) -> Unit,
    onAlbumSortStateChanged: (LibrarySortState) -> Unit,
    onFavoriteSortStateChanged: (LibrarySortState) -> Unit,
    onSongFilterStateChanged: (LibrarySongFilterState) -> Unit,
    songFiltersEnabled: Boolean = true,
    ratingFeaturesEnabled: Boolean = true
) {
    val shouldShowOrganizeAction =
        selectedLibraryTab == LibraryTab.SONGS ||
                selectedLibraryTab == LibraryTab.RATED ||
                selectedLibraryTab == LibraryTab.FAVORITES ||
                selectedLibraryTab == LibraryTab.RECENTLY_ADDED ||
                selectedLibraryTab == LibraryTab.ARTISTS && selectedArtistName == null ||
                selectedLibraryTab == LibraryTab.ALBUMS && selectedAlbumKey == null

    if (shouldShowOrganizeAction) {
        val requestedSortState = when (selectedLibraryTab) {
            LibraryTab.SONGS,
            LibraryTab.RATED,
            LibraryTab.RECENTLY_ADDED -> selectedSongSortState
            LibraryTab.FAVORITES -> selectedFavoriteSortState
            LibraryTab.ARTISTS -> selectedArtistSortState
            LibraryTab.ALBUMS -> selectedAlbumSortState
            LibraryTab.GENRES -> selectedSongSortState
            LibraryTab.PLAYLISTS -> selectedSongSortState
            LibraryTab.RECENTLY_PLAYED -> selectedSongSortState
            LibraryTab.MOST_PLAYED -> selectedSongSortState
            LibraryTab.QUEUE -> selectedSongSortState
        }

        val availableSortOptions = librarySortOptionsFor(
            tab = selectedLibraryTab,
            ratingFeaturesEnabled = ratingFeaturesEnabled
        )
        val selectedSortState = requestedSortState.takeIf {
            it.option in availableSortOptions
        } ?: availableSortOptions.firstOrNull()?.let(requestedSortState::select)

        val onSortStateChanged: (LibrarySortState) -> Unit = { state ->
            when (selectedLibraryTab) {
                LibraryTab.SONGS -> onSongSortStateChanged(state)
                LibraryTab.RATED -> onSongSortStateChanged(state)
                LibraryTab.FAVORITES -> onFavoriteSortStateChanged(state)
                LibraryTab.RECENTLY_ADDED -> onSongSortStateChanged(state)
                LibraryTab.ARTISTS -> onArtistSortStateChanged(state)
                LibraryTab.ALBUMS -> onAlbumSortStateChanged(state)
                LibraryTab.GENRES -> Unit
                LibraryTab.PLAYLISTS -> Unit
                LibraryTab.RECENTLY_PLAYED -> Unit
                LibraryTab.MOST_PLAYED -> Unit
                LibraryTab.QUEUE -> Unit
            }
        }
        if (selectedLibraryTab.showsQuickRateAction(ratingFeaturesEnabled)) {
            val ratingUi = LocalSongRatingUi.current
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AppShellIconButton(
                    onClick = {
                        ratingUi.onQuickRateModeChanged(!ratingUi.quickRateMode)
                    },
                    imageVector = if (ratingUi.quickRateMode) {
                        Icons.Filled.Star
                    } else {
                        Icons.Outlined.StarOutline
                    },
                    contentDescription = if (ratingUi.quickRateMode) {
                        "Exit Quick Rate"
                    } else {
                        "Start Quick Rate"
                    },
                    accented = ratingUi.quickRateMode
                )
                selectedSortState?.let { state ->
                    LibraryOrganizeButton(
                        songs = songs,
                        sortState = state,
                        sortOptions = availableSortOptions,
                        onSortStateChanged = onSortStateChanged,
                        filterState = selectedSongFilterState.takeIf {
                            selectedLibraryTab == LibraryTab.SONGS && songFiltersEnabled
                        },
                        onFilterStateChanged = onSongFilterStateChanged.takeIf {
                            selectedLibraryTab == LibraryTab.SONGS && songFiltersEnabled
                        },
                        optionTitle = { it.displayTitleFor(selectedLibraryTab) }
                    )
                }
            }
        } else if (selectedSortState != null) {
            LibraryOrganizeButton(
                songs = songs,
                sortState = selectedSortState,
                sortOptions = availableSortOptions,
                onSortStateChanged = onSortStateChanged,
                filterState = selectedSongFilterState.takeIf {
                    selectedLibraryTab == LibraryTab.SONGS && songFiltersEnabled
                },
                onFilterStateChanged = onSongFilterStateChanged.takeIf {
                    selectedLibraryTab == LibraryTab.SONGS && songFiltersEnabled
                },
                optionTitle = { it.displayTitleFor(selectedLibraryTab) }
            )
        }
    }
}
