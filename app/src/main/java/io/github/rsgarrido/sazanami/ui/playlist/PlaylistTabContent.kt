package io.github.rsgarrido.sazanami.ui.playlist

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.data.Playlist
import io.github.rsgarrido.sazanami.data.PlaylistFolder
import io.github.rsgarrido.sazanami.data.PlaylistSong
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.player.PlaybackShuffleMode
import io.github.rsgarrido.sazanami.ui.library.LibraryViewMode

@Composable
fun PlaylistsTabContent(
    songs: List<Song>,
    playlists: List<Playlist>,
    playlistFolders: List<PlaylistFolder>,
    selectedPlaylistId: Long?,
    selectedPlaylistStateId: Long?,
    selectedPlaylistName: String,
    selectedPlaylistSongs: List<PlaylistSong>,
    isSelectedPlaylistLoading: Boolean,
    currentSong: Song?,
    recentlyAddedSongIds: Set<Long>,
    favoriteMembershipKeys: Set<String>,
    viewMode: LibraryViewMode,
    onCreatePlaylistClick: (Long?) -> Unit,
    onCreateFolderClick: (String) -> Unit,
    onRenameFolderClick: (PlaylistFolder, String) -> Unit,
    onDeleteFolderClick: (PlaylistFolder) -> Unit,
    onMovePlaylistClick: (Playlist, Long?) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onDeletePlaylistClick: (Playlist) -> Unit,
    onExportPlaylistClick: (Playlist) -> Unit,
    onAddPlaylistToQueueClick: (Playlist) -> Unit,
    onImportPlaylistClick: () -> Unit,
    onChangePlaylistArtwork: (Playlist, Uri) -> Unit,
    onResetPlaylistArtwork: (Playlist) -> Unit,
    onBackFromPlaylist: () -> Unit,
    onPlaySongsClick: (List<Song>, PlaybackShuffleMode) -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onPlayNextClick: (Song) -> Unit,
    onAddToQueueClick: (Song) -> Unit,
    onToggleFavoriteClick: (Song) -> Unit,
    onRemovePlaylistSongClick: (PlaylistSong) -> Unit,
    onRenamePlaylistClick: (Playlist, String) -> Unit,
    onReorderPlaylistSongs: (Long, List<Long>) -> Unit,
    onAddSongsToCurrentPlaylistClick: (Playlist, List<Song>) -> Unit,
    onEditSongTagsClick: (Song) -> Unit,
    bottomContentPadding: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val smartUi = LocalSmartPlaylistUi.current
    var playlistPendingArtworkId by remember { mutableStateOf<Long?>(null) }
    var selectedFolderId by rememberSaveable { mutableStateOf<Long?>(null) }
    var creationChooserVisible by remember { mutableStateOf(false) }
    var creationFolderId by remember { mutableStateOf<Long?>(null) }
    var smartEditorRequest by remember { mutableStateOf<SmartPlaylistEditorRequest?>(null) }
    val returnToPlaylistRoot = { selectedFolderId = null }

    BackHandler(enabled = selectedPlaylistId == null && selectedFolderId != null) {
        returnToPlaylistRoot()
    }
    val artworkPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val playlist = playlistPendingArtworkId?.let { playlistId ->
            playlists.firstOrNull { it.playlistId == playlistId }
        }
        if (uri != null && playlist != null) {
            onChangePlaylistArtwork(playlist, uri)
        }
        playlistPendingArtworkId = null
    }
    val chooseArtwork: (Playlist) -> Unit = { playlist ->
        playlistPendingArtworkId = playlist.playlistId
        artworkPicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    if (selectedPlaylistId == null) {
        PlaylistListScreen(
            playlists = playlists,
            folders = playlistFolders,
            selectedFolderId = selectedFolderId,
            onFolderSelected = { folderId ->
                if (folderId == null) returnToPlaylistRoot() else selectedFolderId = folderId
            },
            onCreatePlaylistClick = { folderId ->
                creationFolderId = folderId
                creationChooserVisible = true
            },
            onCreateFolderClick = onCreateFolderClick,
            onRenameFolderClick = onRenameFolderClick,
            onDeleteFolderClick = onDeleteFolderClick,
            onMovePlaylistClick = onMovePlaylistClick,
            onPlaylistClick = onPlaylistClick,
            onDeletePlaylistClick = onDeletePlaylistClick,
            onExportPlaylistClick = onExportPlaylistClick,
            onAddPlaylistToQueueClick = onAddPlaylistToQueueClick,
            onImportPlaylistClick = onImportPlaylistClick,
            onChangeArtworkClick = chooseArtwork,
            onResetArtworkClick = onResetPlaylistArtwork,
            onRenamePlaylistClick = onRenamePlaylistClick,
            viewMode = viewMode,
            bottomContentPadding = bottomContentPadding,
            modifier = modifier
        )
    } else {
        val stateMatchesSelection = selectedPlaylistStateId == selectedPlaylistId
        val scopedPlaylistSongRows = if (stateMatchesSelection) {
            selectedPlaylistSongs
        } else {
            emptyList()
        }
        val availablePlaylistSongRows = scopedPlaylistSongRows.filter { it.resolvedSong != null }
        val availablePlaylistSongs = availablePlaylistSongRows.mapNotNull(PlaylistSong::resolvedSong)
        val selectedPlaylist = playlists.firstOrNull { playlist ->
            playlist.playlistId == selectedPlaylistId
        } ?: Playlist(
            playlistId = selectedPlaylistId,
            name = if (stateMatchesSelection) selectedPlaylistName else "Playlist",
            songCount = scopedPlaylistSongRows.size,
            totalDuration = scopedPlaylistSongRows.sumOf { it.duration.coerceAtLeast(0L) },
            automaticArtworkSongs = availablePlaylistSongs.distinctBy { song ->
                Triple(
                    song.albumArtist.ifBlank { song.artist }.lowercase(),
                    song.album.lowercase(),
                    song.folderPath.lowercase()
                )
            }.take(4)
        )

        key(selectedPlaylistId) {
            PlaylistDetailScreen(
                playlist = selectedPlaylist,
                allPlaylists = playlists,
                playlistFolders = playlistFolders,
                allSongs = songs,
                playlistSongRows = scopedPlaylistSongRows,
                isLoading = !stateMatchesSelection || isSelectedPlaylistLoading,
                currentSongId = currentSong?.id,
                recentlyAddedSongIds = recentlyAddedSongIds,
                favoriteMembershipKeys = favoriteMembershipKeys,
                onBackClick = onBackFromPlaylist,
                onPlayAllClick = { songsToPlay ->
                    if (selectedPlaylist.type == io.github.rsgarrido.sazanami.data.PlaylistType.SMART) {
                        smartUi.onResolve(selectedPlaylist.playlistId) { result ->
                            result.onSuccess {
                                onPlaySongsClick(it.songs.toList(), PlaybackShuffleMode.OFF)
                            }
                        }
                    } else {
                        onPlaySongsClick(songsToPlay.toList(), PlaybackShuffleMode.OFF)
                    }
                },
                onShuffleAllClick = { songsToPlay ->
                    if (selectedPlaylist.type == io.github.rsgarrido.sazanami.data.PlaylistType.SMART) {
                        smartUi.onResolve(selectedPlaylist.playlistId) { result ->
                            result.onSuccess {
                                onPlaySongsClick(it.songs.toList(), PlaybackShuffleMode.SONGS)
                            }
                        }
                    } else {
                        onPlaySongsClick(songsToPlay.toList(), PlaybackShuffleMode.SONGS)
                    }
                },
                onRenamePlaylistClick = onRenamePlaylistClick,
                onDeletePlaylistClick = onDeletePlaylistClick,
                onExportPlaylistClick = onExportPlaylistClick,
                onAddPlaylistToQueueClick = onAddPlaylistToQueueClick,
                onChangeArtworkClick = chooseArtwork,
                onResetArtworkClick = onResetPlaylistArtwork,
                onMovePlaylistClick = onMovePlaylistClick,
                onSongClick = onSongClick,
                onPlayNextClick = onPlayNextClick,
                onAddToQueueClick = onAddToQueueClick,
                onToggleFavoriteClick = onToggleFavoriteClick,
                onRemovePlaylistSongClick = onRemovePlaylistSongClick,
                onEditSongTagsClick = onEditSongTagsClick,
                onReorderPlaylistSongs = onReorderPlaylistSongs,
                onAddSongsClick = { selectedSongs ->
                    onAddSongsToCurrentPlaylistClick(selectedPlaylist, selectedSongs)
                },
                bottomContentPadding = bottomContentPadding,
                modifier = modifier
            )
        }
    }

    if (creationChooserVisible) {
        PlaylistCreationChooserDialog(
            onDismiss = { creationChooserVisible = false },
            onManual = {
                creationChooserVisible = false
                onCreatePlaylistClick(creationFolderId)
            },
            onSmart = { template ->
                creationChooserVisible = false
                smartEditorRequest = SmartPlaylistEditorRequest(
                    folderId = creationFolderId,
                    model = template?.let {
                        SmartPlaylistEditorModel.fromDraft(it.displayName, it.draft)
                    } ?: SmartPlaylistEditorModel(),
                    template = template
                )
            }
        )
    }

    smartEditorRequest?.let { request ->
        SmartPlaylistEditor(
            request = request,
            existingNames = playlists.map(Playlist::name),
            onDismiss = { smartEditorRequest = null },
            onSaved = { smartEditorRequest = null }
        )
    }
}
