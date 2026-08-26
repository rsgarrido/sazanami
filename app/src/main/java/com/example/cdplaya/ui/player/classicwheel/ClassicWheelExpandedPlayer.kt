package com.example.cdplaya.ui.player.classicwheel

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.example.cdplaya.data.Song
import com.example.cdplaya.player.RepeatMode
import com.example.cdplaya.ui.player.theme.PlayerThemeTokens
import com.example.cdplaya.ui.player.playerEndpointInput
import com.example.cdplaya.ui.player.classicwheel.ClassicWheelMorphBounds
import kotlinx.coroutines.delay


@Composable
fun ClassicWheelExpandedPlayer(
    currentSong: Song?,
    songs: List<Song>,
    menuState: ClassicWheelMenuState,
    isPlaying: Boolean,
    isShuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    currentPosition: Int,
    duration: Int,
    isCurrentSongFavorite: Boolean,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onSeekChange: (Int) -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onCollapseClick: () -> Unit,
    onOpenUpNextClick: () -> Unit,
    onToggleFavoriteClick: (Song) -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    tokens: PlayerThemeTokens = ClassicWheelDefaultTokens,
    screenAlpha: Float = 1f,
    wheelAlpha: Float = 1f,
    wheelInputEnabled: Boolean = true,
    morphBounds: ClassicWheelMorphBounds? = null,
    sharedContentVisible: Boolean = true,
    onMorphDragStart: () -> Unit = {},
    onMorphDragBy: (Float) -> Unit = {},
    onMorphDragEnd: (Float) -> Unit = {},
    onMorphDragCancel: () -> Unit = {},
    wheelPlayControlAlpha: Float = 1f,
    displayLyricsGestureModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    val palette = remember(tokens) { ClassicWheelPalette.from(tokens) }

    CompositionLocalProvider(LocalClassicWheelPalette provides palette) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 16.dp)
    ) {
        val context = androidx.compose.ui.platform.LocalContext.current

        val audioManager = remember {
            context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        }

        val maxMusicVolume = remember {
            audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        }

        var musicVolume by remember {
            mutableIntStateOf(
                audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            )
        }

        var isVolumeOverlayVisible by remember {
            mutableStateOf(false)
        }

        fun changeMusicVolume(delta: Int) {
            val currentSystemVolume = audioManager.getStreamVolume(
                AudioManager.STREAM_MUSIC
            )

            val updatedVolume = (currentSystemVolume + delta)
                .coerceIn(0, maxMusicVolume)

            if (updatedVolume == currentSystemVolume) {
                musicVolume = currentSystemVolume
                isVolumeOverlayVisible = true
                return
            }

            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                updatedVolume,
                0
            )

            musicVolume = updatedVolume
            isVolumeOverlayVisible = true
        }

        LaunchedEffect(isVolumeOverlayVisible, musicVolume) {
            if (isVolumeOverlayVisible) {
                delay(1200)
                isVolumeOverlayVisible = false
            }
        }

        val mainMenuItems = buildClassicWheelMainMenuItems()
        val songMenuItems = buildClassicWheelSongMenuItems(songs)

        val artistGroups = buildClassicWheelArtistGroups(songs)
        val albumGroups = buildClassicWheelAlbumGroups(songs)

        val artistMenuItems = buildClassicWheelArtistMenuItems(artistGroups)
        val albumMenuItems = buildClassicWheelAlbumMenuItems(albumGroups)

        val albumCarouselItems = buildClassicWheelAlbumCarouselItems(albumGroups)

        val currentScreen = menuState.currentScreen

        val selectedArtistSongs = if (currentScreen is ClassicWheelMenuScreen.ArtistSongs) {
            artistGroups.firstOrNull { artistGroup ->
                artistGroup.name == currentScreen.artistName
            }?.songs ?: emptyList()
        } else {
            emptyList()
        }

        val selectedAlbumSongs = if (currentScreen is ClassicWheelMenuScreen.AlbumSongs) {
            albumGroups.firstOrNull { albumGroup ->
                albumGroup.key == currentScreen.albumKey
            }?.songs ?: emptyList()
        } else {
            emptyList()
        }

        val selectedArtistSongMenuItems = buildClassicWheelSongMenuItems(selectedArtistSongs)
        val selectedAlbumSongMenuItems = buildClassicWheelSongMenuItems(selectedAlbumSongs)

        val onMenuClick = {
            if (menuState.currentScreen == ClassicWheelMenuScreen.NowPlaying) {
                menuState.openMainMenu()
            } else {
                menuState.goBack()
            }
        }

        val onCenterClick = {
            when (val screen = menuState.currentScreen) {
                ClassicWheelMenuScreen.NowPlaying -> {
                    menuState.openMainMenu()
                }

                ClassicWheelMenuScreen.MainMenu -> {
                    val selectedItem = mainMenuItems
                        .getOrNull(menuState.selectedIndex)

                    if (selectedItem != null) {
                        handleClassicWheelMenuAction(
                            action = selectedItem.action,
                            menuState = menuState
                        )
                    }
                }

                ClassicWheelMenuScreen.Songs -> {
                    val selectedSong = songs.getOrNull(menuState.selectedIndex)

                    if (selectedSong != null) {
                        onSongClick(selectedSong, songs)
                        menuState.openNowPlaying()
                    }
                }

                ClassicWheelMenuScreen.Artists -> {
                    val selectedArtist = artistGroups.getOrNull(menuState.selectedIndex)

                    if (selectedArtist != null) {
                        menuState.openArtistSongs(selectedArtist.name)
                    }
                }

                is ClassicWheelMenuScreen.ArtistSongs -> {
                    val selectedSong = selectedArtistSongs.getOrNull(menuState.selectedIndex)

                    if (selectedSong != null) {
                        onSongClick(selectedSong, selectedArtistSongs)
                        menuState.openNowPlaying()
                    }
                }

                ClassicWheelMenuScreen.Albums -> {
                    val selectedAlbum = albumGroups.getOrNull(menuState.selectedIndex)

                    if (selectedAlbum != null) {
                        menuState.openAlbumSongs(
                            albumKey = selectedAlbum.key,
                            albumTitle = selectedAlbum.title
                        )
                    }
                }

                is ClassicWheelMenuScreen.AlbumSongs -> {
                    val selectedSong = selectedAlbumSongs.getOrNull(menuState.selectedIndex)

                    if (selectedSong != null) {
                        onSongClick(selectedSong, selectedAlbumSongs)
                        menuState.openNowPlaying()
                    }
                }
            }
        }

        val currentMenuItemCount = when (menuState.currentScreen) {
            ClassicWheelMenuScreen.NowPlaying -> 0
            ClassicWheelMenuScreen.MainMenu -> mainMenuItems.size
            ClassicWheelMenuScreen.Songs -> songMenuItems.size
            ClassicWheelMenuScreen.Artists -> artistMenuItems.size
            is ClassicWheelMenuScreen.ArtistSongs -> selectedArtistSongMenuItems.size
            ClassicWheelMenuScreen.Albums -> albumMenuItems.size
            is ClassicWheelMenuScreen.AlbumSongs -> selectedAlbumSongMenuItems.size
        }

        val onRotateClockwise = {
            if (menuState.currentScreen == ClassicWheelMenuScreen.NowPlaying) {
                changeMusicVolume(1)
            } else if (currentMenuItemCount > 1) {
                menuState.moveSelectionDown(currentMenuItemCount)
            }
        }

        val onRotateCounterClockwise = {
            if (menuState.currentScreen == ClassicWheelMenuScreen.NowPlaying) {
                changeMusicVolume(-1)
            } else if (currentMenuItemCount > 1) {
                menuState.moveSelectionUp(currentMenuItemCount)
            }
        }

        val screenHeight = minOf(
            maxHeight * 0.42f,
            360.dp
        )

        val wheelSize = minOf(
            maxWidth * 0.9f,
            maxHeight * 0.43f,
            370.dp
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = screenAlpha.coerceIn(0f, 1f) },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(screenHeight)
                    .then(displayLyricsGestureModifier)
            ) {
                ClassicWheelScreen(
                    currentSong = currentSong,
                    currentPosition = currentPosition,
                    duration = duration,
                    isCurrentSongFavorite = isCurrentSongFavorite,
                    isShuffleEnabled = isShuffleEnabled,
                    repeatMode = repeatMode,
                    menuState = menuState,
                    mainMenuItems = mainMenuItems,
                    songMenuItems = songMenuItems,
                    artistMenuItems = artistMenuItems,
                    albumMenuItems = albumMenuItems,
                    albumCarouselItems = albumCarouselItems,
                    selectedArtistSongMenuItems = selectedArtistSongMenuItems,
                    selectedAlbumSongMenuItems = selectedAlbumSongMenuItems,
                    musicVolume = musicVolume,
                    maxMusicVolume = maxMusicVolume,
                    isVolumeOverlayVisible = isVolumeOverlayVisible,
                    onSeekChange = onSeekChange,
                    onShuffleClick = onShuffleClick,
                    onRepeatClick = onRepeatClick,
                    onCollapseClick = onCollapseClick,
                    onOpenUpNextClick = onOpenUpNextClick,
                    onToggleFavoriteClick = onToggleFavoriteClick,
                    modifier = Modifier
                        .fillMaxSize()
                        .classicWheelDownwardCollapseDrag(
                            onStart = onMorphDragStart,
                            onDelta = onMorphDragBy,
                            onEnd = onMorphDragEnd,
                            onCancel = onMorphDragCancel
                        ),
                    morphBounds = morphBounds,
                    sharedContentVisible = sharedContentVisible
                )
            }

            ClassicControlWheel(
                isPlaying = isPlaying,
                onPlayPauseClick = onPlayPauseClick,
                onPreviousClick = onPreviousClick,
                onNextClick = onNextClick,
                onMenuClick = onMenuClick,
                onCenterClick = onCenterClick,
                onRotateClockwise = onRotateClockwise,
                onRotateCounterClockwise = onRotateCounterClockwise,
                rotationItemCount = currentMenuItemCount,
                isRotationEnabled = menuState.currentScreen == ClassicWheelMenuScreen.NowPlaying ||
                        currentMenuItemCount > 1,
                rotationStepDegrees = if (menuState.currentScreen == ClassicWheelMenuScreen.NowPlaying) {
                    95f
                } else {
                    55f
                },
                playControlAlpha = wheelPlayControlAlpha,
                modifier = Modifier
                    .size(wheelSize)
                    .onGloballyPositioned { coordinates ->
                        val wheel = coordinates.boundsInRoot()
                        val width = wheel.width * .28f
                        val height = wheel.height * .24f
                        morphBounds?.updateExpandedPlayPause(
                            androidx.compose.ui.geometry.Rect(
                                wheel.center.x - width / 2f,
                                wheel.bottom - height - wheel.height * .06f,
                                wheel.center.x + width / 2f,
                                wheel.bottom - wheel.height * .06f
                            )
                        )
                    }
                    .graphicsLayer {
                        alpha = wheelAlpha.coerceIn(0f, 1f)
                        scaleX = 0.82f + 0.18f * wheelAlpha.coerceIn(0f, 1f)
                        scaleY = scaleX
                    }
                    .then(
                        if (wheelInputEnabled) Modifier else
                            Modifier.playerEndpointInput(false)
                    )
            )
        }
    }
    }
}

/** Claims downward collapse drags and leaves upward intent to lyrics or menu scrolling. */
private fun Modifier.classicWheelDownwardCollapseDrag(
    onStart: () -> Unit, onDelta: (Float) -> Unit, onEnd: (Float) -> Unit, onCancel: () -> Unit
): Modifier = pointerInput(onStart, onDelta, onEnd, onCancel) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val velocityTracker = VelocityTracker().apply {
            addPosition(down.uptimeMillis, down.position)
        }
        var started = false

        val slopChange = awaitVerticalTouchSlopOrCancellation(down.id) { change, overSlop ->
            if (overSlop > 0f) {
                started = true
                change.consume()
                velocityTracker.addPosition(change.uptimeMillis, change.position)
                onStart()
                onDelta(overSlop)
            }
        }

        if (!started || slopChange == null) {
            if (started) onCancel()
            return@awaitEachGesture
        }

        var active = true
        while (active) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            velocityTracker.addPosition(change.uptimeMillis, change.position)
            val deltaY = change.positionChange().y

            if (deltaY != 0f) {
                change.consume()
                onDelta(deltaY)
            }
            active = change.pressed
        }

        onEnd(velocityTracker.calculateVelocity().y)
    }
}

private fun handleClassicWheelMenuAction(
    action: ClassicWheelMenuAction,
    menuState: ClassicWheelMenuState
) {
    when (action) {
        ClassicWheelMenuAction.OPEN_NOW_PLAYING -> {
            menuState.openNowPlaying()
        }

        ClassicWheelMenuAction.OPEN_SONGS -> {
            menuState.openSongs()
        }

        ClassicWheelMenuAction.OPEN_ARTISTS -> {
            menuState.openArtists()
        }

        ClassicWheelMenuAction.OPEN_ALBUMS -> {
            menuState.openAlbums()
        }
    }
}


