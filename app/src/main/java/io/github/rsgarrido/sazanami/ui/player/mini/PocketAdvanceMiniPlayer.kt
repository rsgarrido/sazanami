package io.github.rsgarrido.sazanami.ui.player.mini

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.ui.player.pocketadvance.PocketAdvanceArtist
import io.github.rsgarrido.sazanami.ui.player.pocketadvance.PocketAdvanceArtwork
import io.github.rsgarrido.sazanami.ui.player.pocketadvance.PocketAdvanceColors
import io.github.rsgarrido.sazanami.ui.player.pocketadvance.PocketAdvancePlayVisual
import io.github.rsgarrido.sazanami.ui.player.pocketadvance.PocketAdvancePlayerMorphBounds
import io.github.rsgarrido.sazanami.ui.player.pocketadvance.PocketAdvanceTitle
import io.github.rsgarrido.sazanami.ui.state.playbackPlayPauseShowsPause

/** Matching compact handheld. The source remains composed, measured and artwork-warm when hidden. */
@Composable
fun PocketAdvanceMiniPlayer(
    state: MiniPlayerState,
    callbacks: MiniPlayerCallbacks,
    modifier: Modifier = Modifier,
    morphCallbacks: DefaultMiniPlayerMorphCallbacks? = null,
    morphOwnsVisuals: Boolean = false,
    morphBounds: PocketAdvancePlayerMorphBounds? = null
) {
    // A viewport epoch gets a new holder: disposing this subtree cancels the old pointer scope.
    key(morphBounds) {
        MiniPlayerScaffold(
            state = state,
            callbacks = callbacks,
            modifier = modifier
                .testTag("pocketAdvanceMini")
                .graphicsLayer { alpha = if (morphOwnsVisuals) 0f else 1f }
                .then(if (morphOwnsVisuals) Modifier.clearAndSetSemantics { } else Modifier),
            containerColor = PocketAdvanceColors.Shell,
            borderColor = PocketAdvanceColors.ShellHighlight,
            shape = RoundedCornerShape(16.dp),
            defaultMorphCallbacks = morphCallbacks,
            onSurfaceBoundsChanged = { morphBounds?.updateMiniSurface(it) }
        ) { displayedState ->
            val isCurrentSource = displayedState.currentSong.id == state.currentSong.id
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                // A shallow embossed cross is decorative; track swipes remain the mini control.
                Canvas(Modifier.size(24.dp)) {
                    val unit = size.width / 3f
                    val cross = Path().apply {
                        moveTo(unit, 0f); lineTo(unit * 2, 0f); lineTo(unit * 2, unit)
                        lineTo(size.width, unit); lineTo(size.width, unit * 2)
                        lineTo(unit * 2, unit * 2); lineTo(unit * 2, size.height)
                        lineTo(unit, size.height); lineTo(unit, unit * 2)
                        lineTo(0f, unit * 2); lineTo(0f, unit); lineTo(unit, unit); close()
                    }
                    drawPath(cross, PocketAdvanceColors.ShellShade)
                    drawCircle(PocketAdvanceColors.ShellHighlight.copy(alpha = .45f), unit * .22f)
                }
                Spacer(Modifier.width(10.dp))
                Row(
                    Modifier.weight(1f).height(48.dp)
                        .onGloballyPositioned { if (isCurrentSource) morphBounds?.updateMiniDisplay(it.boundsInRoot()) }
                        .background(PocketAdvanceColors.Bezel, RoundedCornerShape(8.dp))
                        .padding(3.dp)
                        .background(PocketAdvanceColors.Screen, RoundedCornerShape(5.dp))
                        .padding(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PocketAdvanceArtwork(displayedState.currentSong, Modifier.size(36.dp)
                        .onGloballyPositioned { if (isCurrentSource) morphBounds?.updateMiniArtwork(it.boundsInRoot()) }, progress = 0f)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        // Reserve the unweighted track before measuring text. Independent dp
                        // rounding can otherwise leave the last child with zero pixels (notably
                        // in reduced-density hosts). fill=false keeps the normal wrap-content size.
                        Column(Modifier.weight(1f, fill = false)) {
                            PocketAdvanceTitle(displayedState.currentSong, 0f,
                                Modifier.fillMaxWidth().height(18.dp).clipToBounds()
                                    .onGloballyPositioned { if (isCurrentSource) morphBounds?.updateMiniTitle(it.boundsInRoot()) })
                            PocketAdvanceArtist(displayedState.currentSong, 0f,
                                Modifier.fillMaxWidth().height(14.dp).clipToBounds()
                                    .onGloballyPositioned { if (isCurrentSource) morphBounds?.updateMiniArtist(it.boundsInRoot()) })
                        }
                        Canvas(Modifier.fillMaxWidth().height(3.dp)
                            .onGloballyPositioned {
                                // The line is centered on y=0, not on its 3dp layout box.
                                if (isCurrentSource) {
                                    val rect = it.boundsInRoot()
                                    morphBounds?.updateMiniProgress(rect.translate(0f, -rect.height / 2f))
                                }
                            }) {
                            drawLine(PocketAdvanceColors.MutedInk.copy(alpha = .22f), Offset.Zero, Offset(size.width, 0f), size.height)
                            drawLine(PocketAdvanceColors.ScreenAccent, Offset.Zero,
                                Offset(size.width * normalizedMiniPlayerProgress(displayedState.currentPosition, displayedState.duration), 0f), size.height)
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                val label = stringResource(if (playbackPlayPauseShowsPause(displayedState.isPlaying)) R.string.player_pause else R.string.player_play)
                Box(
                    Modifier.size(48.dp)
                        .testTag("pocketAdvanceMiniPlay")
                        .clickable(enabled = !morphOwnsVisuals, role = Role.Button,
                            onClickLabel = label, onClick = callbacks.onPlayPauseClick)
                        .semantics { contentDescription = label },
                    contentAlignment = Alignment.Center
                ) {
                    PocketAdvancePlayVisual(displayedState.isPlaying,
                        Modifier.size(38.dp).onGloballyPositioned { if (isCurrentSource) morphBounds?.updateMiniPlayPause(it.boundsInRoot()) },
                        progress = 0f)
                }
            }
        }
    }
}
