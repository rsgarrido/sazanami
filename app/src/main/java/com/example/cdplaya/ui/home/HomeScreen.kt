package com.example.cdplaya.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.cdplaya.R
import com.example.cdplaya.data.Playlist
import com.example.cdplaya.data.Song
import com.example.cdplaya.data.recentlyAddedShelfSongs
import com.example.cdplaya.ui.AppShellTypography
import com.example.cdplaya.ui.AppShellIconButton
import com.example.cdplaya.ui.MusicScreenHeader
import com.example.cdplaya.ui.library.LibraryTab
import com.example.cdplaya.mediaaccess.MediaAccessState
import com.example.cdplaya.ui.EmptyLibraryNotice
import com.example.cdplaya.ui.LibraryErrorNotice
import com.example.cdplaya.ui.LibraryLoadingNotice
import com.example.cdplaya.ui.MediaAccessNotice

@Composable
internal fun HomeScreen(
    mediaAccessState: MediaAccessState,
    isLibraryLoading: Boolean,
    libraryErrorMessage: String?,
    onRequestAudioAccess: () -> Unit,
    onRequestArtworkAccess: () -> Unit,
    onOpenAppSettings: () -> Unit,
    recentlyPlayedSongs: List<Song>,
    recentlyAddedSongs: List<Song>,
    favoriteSongs: List<Song>,
    currentSongId: Long?,
    songCount: Int,
    albumCount: Int,
    artistCount: Int,
    playlistCount: Int,
    onSettingsClick: () -> Unit,
    onStatisticsClick: () -> Unit,
    onOpenLibrary: (LibraryTab) -> Unit,
    onPinnedSongClick: (Song) -> Unit,
    onPinnedAlbumClick: (String) -> Unit,
    onPinnedArtistClick: (String) -> Unit,
    onPinnedPlaylistClick: (Playlist) -> Unit,
    onRecentlyPlayedSongClick: (Song) -> Unit,
    onRecentlyAddedSongClick: (Song) -> Unit,
    onFavoriteSongClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
    bottomContentPadding: Dp = 24.dp
) {
    val visibleRecentlyPlayedSongs = recentlyPlayedSongs
        .filterNot { song -> song.id == currentSongId }
        .ifEmpty { recentlyPlayedSongs }
    val homePinUi = LocalHomePinUi.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomContentPadding),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HomeHeader(
                    onStatisticsClick = onStatisticsClick,
                    onSettingsClick = onSettingsClick,
                    modifier = Modifier.statusBarsPadding()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HomeStatBadge(
                        count = songCount,
                        label = pluralStringResource(R.plurals.song_label, songCount),
                        modifier = Modifier.weight(1f)
                    )
                    HomeStatBadge(
                        count = albumCount,
                        label = pluralStringResource(R.plurals.album_label, albumCount),
                        modifier = Modifier.weight(1f)
                    )
                    HomeStatBadge(
                        count = artistCount,
                        label = pluralStringResource(R.plurals.artist_label, artistCount),
                        modifier = Modifier.weight(1f)
                    )
                    HomeStatBadge(
                        count = playlistCount,
                        label = pluralStringResource(R.plurals.playlist_label, playlistCount),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (!mediaAccessState.hasAudioAccess) {
            item {
                MediaAccessNotice(
                    state = mediaAccessState,
                    onRequestAudioAccess = onRequestAudioAccess,
                    onRequestArtworkAccess = onRequestArtworkAccess,
                    onOpenAppSettings = onOpenAppSettings,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        } else if (isLibraryLoading) {
            item {
                LibraryLoadingNotice(modifier = Modifier.padding(horizontal = 16.dp))
            }
        } else if (libraryErrorMessage != null) {
            item {
                LibraryErrorNotice(
                    message = libraryErrorMessage,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        } else if (songCount == 0) {
            item {
                EmptyLibraryNotice(modifier = Modifier.padding(horizontal = 16.dp))
            }
        } else if (!mediaAccessState.hasArtworkAccess) {
            item {
                MediaAccessNotice(
                    state = mediaAccessState,
                    onRequestAudioAccess = onRequestAudioAccess,
                    onRequestArtworkAccess = onRequestArtworkAccess,
                    onOpenAppSettings = onOpenAppSettings,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        if (homePinUi.pins.isNotEmpty()) {
            item {
                HomePinnedShelf(
                    pins = homePinUi.pins,
                    onSongClick = onPinnedSongClick,
                    onAlbumClick = onPinnedAlbumClick,
                    onArtistClick = onPinnedArtistClick,
                    onPlaylistClick = onPinnedPlaylistClick
                )
            }
        }

        if (visibleRecentlyPlayedSongs.isNotEmpty()) {
            item {
                HomeRecentlyPlayedShelf(
                    songs = visibleRecentlyPlayedSongs.take(8),
                    onSeeAllClick = {
                        onOpenLibrary(LibraryTab.RECENTLY_PLAYED)
                    },
                    onSongClick = onRecentlyPlayedSongClick
                )
            }
        }

        val visibleRecentlyAddedSongs = recentlyAddedShelfSongs(recentlyAddedSongs)
        if (homePinUi.showRecentlyAddedOnHome && visibleRecentlyAddedSongs.isNotEmpty()) {
            item {
                HomeRecentlyAddedShelf(
                    songs = visibleRecentlyAddedSongs,
                    onSeeAllClick = { onOpenLibrary(LibraryTab.RECENTLY_ADDED) },
                    onSongClick = onRecentlyAddedSongClick
                )
            }
        }

        if (favoriteSongs.isNotEmpty()) {
            item {
                HomeFavoritesShelf(
                    songs = favoriteSongs.take(8),
                    onSeeAllClick = {
                        onOpenLibrary(LibraryTab.FAVORITES)
                    },
                    onSongClick = onFavoriteSongClick
                )
            }
        }

        if (mediaAccessState.hasAudioAccess && songCount > 0 && recentlyPlayedSongs.isEmpty() &&
            (visibleRecentlyAddedSongs.isEmpty() || !homePinUi.showRecentlyAddedOnHome) &&
            favoriteSongs.isEmpty() && homePinUi.pins.isEmpty()
        ) {
            item {
                Text(
                    text = "Choose something from Library to start building your listening history.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun HomeHeader(
    onStatisticsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    MusicScreenHeader(
        title = "CDPlaya",
        onSettingsClick = onSettingsClick,
        modifier = modifier,
        viewModeAction = {
            AppShellIconButton(
                onClick = onStatisticsClick,
                imageVector = Icons.Rounded.QueryStats,
                contentDescription = stringResource(
                    R.string.statistics_home_button_description
                ),
                accented = true
            )
        }
    )
}

@Composable
private fun HomeStatBadge(
    count: Int,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = count.toString(),
                style = AppShellTypography.StatNumber,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label.uppercase(),
                style = AppShellTypography.StatLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
