package io.github.rsgarrido.sazanami.ui.player.pocketadvance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.ui.player.mini.normalizedMiniPlayerProgress
import io.github.rsgarrido.sazanami.ui.state.playbackPlayPauseShowsPause
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Only PlayerMorphState supplies [progress]. The endpoint stays composed and measured underneath
 * this layer, including while collapsed. One persistent shared-content owner spans the whole open
 * lifetime; the endpoint does not acquire a second artwork painter or play visual at completion.
 */
@Composable
internal fun PocketAdvancePlayerMorph(
    progress: Float,
    geometry: PocketAdvanceMorphGeometry?,
    currentSong: Song?,
    isPlaying: Boolean,
    currentPosition: Int,
    duration: Int,
    onPlayPauseClick: () -> Unit,
    nowPlayingContentVisible: Boolean = true,
    modifier: Modifier = Modifier,
    expandedContent: @Composable (PocketAdvanceMorphVisualState) -> Unit
) {
    val colors = LocalPocketAdvancePalette.current
    val p = pocketAdvanceSafeProgress(progress)
    val visual = pocketAdvanceMorphVisualState(p, geometry != null)
    val density = LocalDensity.current
    Box(modifier.fillMaxSize()) {
        // A handheld sits in its own presentation space, while the home/library surface yields.
        // This is a passive draw layer: it introduces neither input ownership nor an animation.
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer {
                    alpha = if (visual.isReady || visual.endpointOwnsVisuals) {
                        ((p - 0.04f) / 0.70f).coerceIn(0f, 1f)
                    } else 0f
                }
                .background(Brush.radialGradient(listOf(colors.ShellHighlight, colors.ShellShade)))
        )
        if (geometry != null) {
            PocketAdvanceShell(
                progress = p,
                modifier = Modifier
                    .atPocketAdvanceRect(geometry.shell, density)
                    .testTag("pocketAdvanceMorphShell")
                    .graphicsLayer { alpha = visual.shellAlpha }
            )
            // The compact LCD is part of the transformation, rather than vanishing while dark
            // metadata travels over the purple shell. Its bezel grows into the same final display.
            Box(
                Modifier.fillMaxSize().graphicsLayer { alpha = visual.shellAlpha }
                    .clipPocketAdvanceMorphShell(geometry, p)
            ) {
                val bezelShape = RoundedCornerShape((8f + 12f * p).dp)
                Box(
                    Modifier.atPocketAdvanceRect(geometry.display, density)
                        .background(colors.Bezel, bezelShape)
                        .border(1.dp, colors.ShellShade.copy(alpha = p), bezelShape)
                        .padding((3f + 5f * p).dp)
                        .background(colors.Screen, RoundedCornerShape((5f + 7f * p).dp))
                )
            }
        }

        // Only display/hardware details live here. Hidden shared placeholders report their real
        // destination rectangles. A missing complete pair draws no expanded endpoint pixels.
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = if ((visual.isReady && p > 0f) || visual.endpointOwnsVisuals) 1f else 0f }
                .then(if (visual.endpointOwnsVisuals) Modifier else Modifier.clipPocketAdvanceMorphShell(geometry, p))
                .then(if (!visual.inputEnabled) Modifier.clearAndSetSemantics { } else Modifier)
        ) {
            expandedContent(visual)
        }

        if (geometry != null) {
            // Keep this subtree at p=0 too: its artwork request is warm before the first gesture.
            // It claims pixels only when the mini relinquishes them, after a complete measurement.
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer { alpha = if (visual.morphOwnsSharedVisuals && nowPlayingContentVisible) 1f else 0f }
                    .clipPocketAdvanceMorphShell(geometry, p)
                    .then(if (!visual.inputEnabled || !nowPlayingContentVisible) Modifier.clearAndSetSemantics { } else Modifier)
            ) {
                PocketAdvanceArtwork(
                    currentSong = currentSong,
                    progress = p,
                    modifier = Modifier.atPocketAdvanceRect(geometry.artwork, density)
                        .testTag("pocketAdvanceMorphArtwork")
                )
                PocketAdvanceTitle(
                    currentSong = currentSong,
                    progress = p,
                    modifier = Modifier.atPocketAdvanceRect(geometry.title, density).clipToBounds()
                        .testTag("pocketAdvanceMorphTitle")
                )
                PocketAdvanceArtist(
                    currentSong = currentSong,
                    progress = p,
                    modifier = Modifier.atPocketAdvanceRect(geometry.artist, density).clipToBounds()
                        .testTag("pocketAdvanceMorphArtist").clearAndSetSemantics { }
                )

                // Preserve the source engraving while the independently measured track moves into the LCD.
                pocketAdvanceMiniChromeGeometry(geometry, p, density.density)?.let { chrome ->
                    if (chrome.alpha > 0f) {
                        Canvas(
                            Modifier.atPocketAdvanceRect(chrome.cross, density)
                                .graphicsLayer { alpha = chrome.alpha }
                        ) {
                            val unit = size.width / 3f
                            val cross = Path().apply {
                                moveTo(unit, 0f); lineTo(unit * 2f, 0f); lineTo(unit * 2f, unit)
                                lineTo(size.width, unit); lineTo(size.width, unit * 2f)
                                lineTo(unit * 2f, unit * 2f); lineTo(unit * 2f, size.height)
                                lineTo(unit, size.height); lineTo(unit, unit * 2f)
                                lineTo(0f, unit * 2f); lineTo(0f, unit); lineTo(unit, unit); close()
                            }
                            drawPath(cross, colors.ShellShade)
                            drawCircle(colors.ShellHighlight.copy(alpha = .45f), unit * .22f)
                        }

                    }
                }

                Canvas(Modifier.atPocketAdvanceRect(geometry.progressTrack, density)
                    .testTag("pocketAdvanceMorphProgress")) {
                    drawPocketAdvanceProgress(normalizedMiniPlayerProgress(currentPosition, duration), p, colors)
                }

                // The measured rectangle describes the visible button. Its final interactive
                // region is independently expanded around that center to at least 48dp.
                val minTouchPx = with(density) { 48.dp.toPx() }
                val touchWidth = max(minTouchPx, geometry.playPause.width)
                val touchHeight = max(minTouchPx, geometry.playPause.height)
                val touchBounds = Rect(
                    geometry.playPause.center - Offset(touchWidth / 2f, touchHeight / 2f),
                    androidx.compose.ui.geometry.Size(touchWidth, touchHeight)
                )
                val label = stringResource(
                    if (playbackPlayPauseShowsPause(isPlaying)) R.string.player_pause
                    else R.string.player_play
                )
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                Box(
                    Modifier.atPocketAdvanceRect(touchBounds, density)
                        .testTag("pocketAdvanceMorphPlay")
                        // Remove the pointer node while browsing; a disabled clickable would still
                        // win hit testing over the endpoint's contextual A target underneath it.
                        .then(if (nowPlayingContentVisible) Modifier.clickable(
                            interactionSource = interaction, indication = null,
                            enabled = visual.inputEnabled,
                            role = Role.Button,
                            onClickLabel = label,
                            onClick = onPlayPauseClick
                        ) else Modifier)
                        .then(if (visual.inputEnabled && nowPlayingContentVisible) {
                            Modifier.clearAndSetSemantics {
                                contentDescription = label
                                this.role = Role.Button
                                onClick(label) {
                                    onPlayPauseClick()
                                    true
                                }
                            }
                        } else Modifier.clearAndSetSemantics { }),
                    contentAlignment = Alignment.Center
                ) {
                    PocketAdvancePlayVisual(
                        isPlaying = isPlaying,
                        progress = p,
                        pressed = pressed,
                        modifier = Modifier.size(
                            with(density) { geometry.playPause.width.toDp() },
                            with(density) { geometry.playPause.height.toDp() }
                        )
                    )
                }
            }
        }
    }
}

private fun Modifier.atPocketAdvanceRect(
    rect: Rect,
    density: androidx.compose.ui.unit.Density
): Modifier = offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
    .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })

private fun Modifier.clipPocketAdvanceMorphShell(
    geometry: PocketAdvanceMorphGeometry?,
    progress: Float
): Modifier = drawWithContent {
    val shell = geometry?.shell ?: return@drawWithContent
    val outline = PocketAdvanceShellShape(progress).createOutline(shell.size, layoutDirection, this)
    val path = Path().apply {
        addOutline(outline)
        translate(shell.topLeft)
    }
    clipPath(path) { this@drawWithContent.drawContent() }
}
