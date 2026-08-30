package io.github.rsgarrido.sazanami.ui.player.mini

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokens
import io.github.rsgarrido.sazanami.ui.player.modern.DefaultPlayerMorphBounds
import io.github.rsgarrido.sazanami.ui.player.classicwheel.ClassicWheelMorphBounds
import io.github.rsgarrido.sazanami.ui.player.retrorack.RetroRackMorphBounds
import io.github.rsgarrido.sazanami.ui.player.pocketflip.PocketFlipMorphBounds
import io.github.rsgarrido.sazanami.ui.player.pocketcassette.PocketCassetteMorphBounds

data class MiniPlayerState(
    val currentSong: Song,
    val isPlaying: Boolean,
    val currentPosition: Int,
    val duration: Int,
    val albumArtSize: Dp
)

data class MiniPlayerCallbacks(
    val onPlayPauseClick: () -> Unit,
    val onPreviousClick: () -> Unit,
    val onNextClick: () -> Unit,
    val onExpandClick: () -> Unit
)

data class DefaultMiniPlayerMorphCallbacks(
    val onDragStart: () -> Unit,
    val onDragBy: (Float) -> Unit,
    val onDragEnd: (Float) -> Unit,
    val onDragCancel: () -> Unit
)

internal enum class MiniPlayerVariant {
    MODERN,
    CLASSIC_WHEEL,
    POCKET_CASSETTE,
    POCKET_FLIP,
    RETRO_RACK
}

internal fun miniPlayerVariantFor(playerTheme: PlayerTheme): MiniPlayerVariant =
    when (playerTheme) {
        PlayerTheme.DEFAULT -> MiniPlayerVariant.MODERN
        PlayerTheme.CLASSIC_WHEEL -> MiniPlayerVariant.CLASSIC_WHEEL
        PlayerTheme.POCKET_CASSETTE -> MiniPlayerVariant.POCKET_CASSETTE
        PlayerTheme.POCKET_FLIP -> MiniPlayerVariant.POCKET_FLIP
        PlayerTheme.RETRO_RACK -> MiniPlayerVariant.RETRO_RACK
    }

@Composable
fun MiniPlayerHost(
    selectedPlayerTheme: PlayerTheme,
    tokens: PlayerThemeTokens,
    state: MiniPlayerState,
    callbacks: MiniPlayerCallbacks,
    modifier: Modifier = Modifier,
    onBoundsChanged: (Rect) -> Unit = {},
    defaultMorphBounds: DefaultPlayerMorphBounds? = null,
    classicMorphBounds: ClassicWheelMorphBounds? = null,
    retroRackMorphBounds: RetroRackMorphBounds? = null,
    pocketFlipMorphBounds: PocketFlipMorphBounds? = null,
    pocketCassetteMorphBounds: PocketCassetteMorphBounds? = null,
    defaultMorphCallbacks: DefaultMiniPlayerMorphCallbacks? = null,
    morphOwnsVisuals: Boolean = false
) {
    val measuredModifier = modifier.onGloballyPositioned { coordinates ->
        onBoundsChanged(coordinates.boundsInRoot())
    }
    when (miniPlayerVariantFor(selectedPlayerTheme)) {
        MiniPlayerVariant.MODERN -> ModernMiniPlayer(
            state = state,
            callbacks = callbacks,
            morphBounds = defaultMorphBounds,
            morphCallbacks = defaultMorphCallbacks,
            morphOwnsVisuals = morphOwnsVisuals,
            modifier = measuredModifier
        )

        MiniPlayerVariant.CLASSIC_WHEEL -> ClassicWheelMiniPlayer(
            state = state,
            callbacks = callbacks,
            tokens = tokens,
            morphCallbacks = defaultMorphCallbacks,
            morphOwnsVisuals = morphOwnsVisuals,
            morphBounds = classicMorphBounds,
            modifier = measuredModifier
        )

        MiniPlayerVariant.POCKET_CASSETTE -> PocketCassetteMiniPlayer(
            state = state,
            callbacks = callbacks,
            tokens = tokens,
            morphCallbacks = defaultMorphCallbacks,
            morphOwnsVisuals = morphOwnsVisuals,
            morphBounds = pocketCassetteMorphBounds,
            modifier = measuredModifier
        )

        MiniPlayerVariant.POCKET_FLIP -> PocketFlipMiniPlayer(
            state = state,
            callbacks = callbacks,
            tokens = tokens,
            morphCallbacks = defaultMorphCallbacks,
            morphOwnsVisuals = morphOwnsVisuals,
            morphBounds = pocketFlipMorphBounds,
            modifier = measuredModifier
        )

        MiniPlayerVariant.RETRO_RACK -> RetroRackMiniPlayer(
            state = state,
            callbacks = callbacks,
            tokens = tokens,
            morphCallbacks = defaultMorphCallbacks,
            morphOwnsVisuals = morphOwnsVisuals,
            morphBounds = retroRackMorphBounds,
            modifier = measuredModifier
        )
    }
}
