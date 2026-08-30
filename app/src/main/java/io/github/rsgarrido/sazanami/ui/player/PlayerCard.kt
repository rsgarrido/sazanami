package io.github.rsgarrido.sazanami.ui.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.player.RepeatMode
import io.github.rsgarrido.sazanami.ui.player.mini.MiniPlayerCallbacks
import io.github.rsgarrido.sazanami.ui.player.mini.MiniPlayerHost
import io.github.rsgarrido.sazanami.ui.player.mini.MiniPlayerState
import io.github.rsgarrido.sazanami.ui.player.modern.ModernExpandedPlayer
import io.github.rsgarrido.sazanami.ui.player.modern.DefaultPlayerMorphBounds
import io.github.rsgarrido.sazanami.ui.player.mini.DefaultMiniPlayerMorphCallbacks
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokens
import io.github.rsgarrido.sazanami.ui.player.classicwheel.ClassicWheelMorphBounds
import io.github.rsgarrido.sazanami.ui.player.retrorack.RetroRackMorphBounds
import io.github.rsgarrido.sazanami.ui.player.pocketflip.PocketFlipMorphBounds
import io.github.rsgarrido.sazanami.ui.player.pocketcassette.PocketCassetteMorphBounds

@Composable
fun PlayerCard(
    currentSong: Song?,
    isPlaying: Boolean,
    isExpanded: Boolean,
    currentPosition: Int,
    duration: Int,
    isShuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    selectedPlayerTheme: PlayerTheme,
    selectedPlayerThemeTokens: PlayerThemeTokens,
    playerMorphState: PlayerMorphState,
    modifier: Modifier = Modifier,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onSeekChange: (Int) -> Unit,
    onExpandClick: () -> Unit,
    onCollapseClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onOpenUpNextClick: () -> Unit = {},
    onOpenLyrics: () -> Unit = {},
    isCurrentSongFavorite: Boolean = false,
    onToggleFavoriteClick: (Song) -> Unit = {},
    onMiniPlayerBoundsChanged: (Rect) -> Unit = {},
    defaultMorphBounds: DefaultPlayerMorphBounds? = null,
    classicMorphBounds: ClassicWheelMorphBounds? = null,
    retroRackMorphBounds: RetroRackMorphBounds? = null,
    pocketFlipMorphBounds: PocketFlipMorphBounds? = null,
    pocketCassetteMorphBounds: PocketCassetteMorphBounds? = null,
    defaultMorphCallbacks: DefaultMiniPlayerMorphCallbacks? = null,
    morphOwnsVisuals: Boolean = false,
) {
    if (currentSong == null) {
        return
    }
    val lyricsTransitionState = rememberPlayerLyricsTransitionState(false) { visible ->
        if (visible) onOpenLyrics()
    }

    val albumArtSize by animateDpAsState(
        targetValue = if (isExpanded) 292.dp else 52.dp,
        animationSpec = tween(durationMillis = 300),
        label = "albumArtSize"
    )

    if (isExpanded) {
        ModernExpandedPlayer(
            currentSong = currentSong,
            isPlaying = isPlaying,
            isShuffleEnabled = isShuffleEnabled,
            repeatMode = repeatMode,
            currentPosition = currentPosition,
            duration = duration,
            isCurrentSongFavorite = isCurrentSongFavorite,
            onPlayPauseClick = onPlayPauseClick,
            onPreviousClick = onPreviousClick,
            onNextClick = onNextClick,
            onSeekChange = onSeekChange,
            onShuffleClick = onShuffleClick,
            onRepeatClick = onRepeatClick,
            onCollapseClick = onCollapseClick,
            playerMorphState = playerMorphState,
            lyricsTransitionState = lyricsTransitionState,
            onOpenUpNextClick = onOpenUpNextClick,
            onToggleFavoriteClick = onToggleFavoriteClick,
            albumArtSize = albumArtSize,
            modifier = modifier
        )
    } else {
        MiniPlayerHost(
            selectedPlayerTheme = selectedPlayerTheme,
            tokens = selectedPlayerThemeTokens,
            state = MiniPlayerState(
                currentSong = currentSong,
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                duration = duration,
                albumArtSize = albumArtSize
            ),
            callbacks = MiniPlayerCallbacks(
                onPlayPauseClick = onPlayPauseClick,
                onPreviousClick = onPreviousClick,
                onNextClick = onNextClick,
                onExpandClick = onExpandClick
            ),
            onBoundsChanged = onMiniPlayerBoundsChanged,
            defaultMorphBounds = defaultMorphBounds,
            classicMorphBounds = classicMorphBounds,
            retroRackMorphBounds = retroRackMorphBounds,
            pocketFlipMorphBounds = pocketFlipMorphBounds,
            pocketCassetteMorphBounds = pocketCassetteMorphBounds,
            defaultMorphCallbacks = defaultMorphCallbacks,
            morphOwnsVisuals = morphOwnsVisuals,
            modifier = modifier
        )
    }
}
