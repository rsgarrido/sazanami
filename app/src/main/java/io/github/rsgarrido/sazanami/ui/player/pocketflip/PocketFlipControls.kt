package io.github.rsgarrido.sazanami.ui.player.pocketflip

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.player.RepeatMode

@Composable
internal fun PocketFlipControlHalf(
    currentSong: Song?,
    isPlaying: Boolean,
    isShuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    isCurrentSongFavorite: Boolean,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onOpenUpNextClick: () -> Unit,
    onCollapseClick: () -> Unit,
    onToggleFavoriteClick: (Song) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
    controlsReveal: Float = 1f,
    inputEnabled: Boolean = true,
    morphBounds: PocketFlipMorphBounds? = null,
    sharedOwner: PocketFlipSharedOwner = PocketFlipSharedOwner.EXPANDED,
    deckDetailsDragModifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = controlsReveal.coerceIn(0f, 1f) }
            .then(if (inputEnabled) Modifier else Modifier.clearAndSetSemantics { }),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PocketFlipDirectionPad(
                isShuffleEnabled = isShuffleEnabled,
                repeatMode = repeatMode,
                onPreviousClick = onPreviousClick,
                onNextClick = onNextClick,
                onShuffleClick = onShuffleClick,
                onRepeatClick = onRepeatClick,
                compact = compact,
                inputEnabled = inputEnabled
            )

            PocketFlipActionCluster(
                currentSong = currentSong,
                isPlaying = isPlaying,
                isCurrentSongFavorite = isCurrentSongFavorite,
                onPlayPauseClick = onPlayPauseClick,
                onToggleFavoriteClick = onToggleFavoriteClick,
                compact = compact,
                inputEnabled = inputEnabled,
                morphBounds = morphBounds,
                sharedOwner = sharedOwner
            )
        }

        PocketFlipDeckDetails(
            compact = compact,
            modifier = deckDetailsDragModifier
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PocketFlipUtilitySwitch(
                label = "QUEUE",
                contentDescription = "Open up next queue",
                onClick = onOpenUpNextClick,
                compact = compact,
                enabled = inputEnabled
            )
            Spacer(modifier = Modifier.width(if (compact) 12.dp else 18.dp))
            PocketFlipUtilitySwitch(
                label = "CLOSE",
                contentDescription = "Collapse player",
                onClick = onCollapseClick,
                compact = compact,
                enabled = inputEnabled
            )
        }
    }
}

@Composable
private fun PocketFlipDirectionPad(
    isShuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    compact: Boolean,
    inputEnabled: Boolean
) {
    val padSize = if (compact) 136.dp else 150.dp
    val hitSize = if (compact) 52.dp else 58.dp

    Box(
        modifier = Modifier.size(padSize),
        contentAlignment = Alignment.Center
    ) {
        PocketFlipPadBody(modifier = Modifier.matchParentSize())

        PocketFlipPadHitTarget(
            icon = Icons.Filled.Shuffle,
            contentDescription = if (isShuffleEnabled) "Disable shuffle" else "Enable shuffle",
            active = isShuffleEnabled,
            size = hitSize,
            onClick = onShuffleClick,
            enabled = inputEnabled,
            modifier = Modifier.align(Alignment.TopCenter)
        )
        PocketFlipPadHitTarget(
            icon = Icons.Filled.SkipPrevious,
            contentDescription = "Previous track",
            size = hitSize,
            onClick = onPreviousClick,
            enabled = inputEnabled,
            modifier = Modifier.align(Alignment.CenterStart)
        )
        PocketFlipPadHitTarget(
            icon = Icons.Filled.SkipNext,
            contentDescription = "Next track",
            size = hitSize,
            onClick = onNextClick,
            enabled = inputEnabled,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
        PocketFlipPadHitTarget(
            icon = if (repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
            contentDescription = when (repeatMode) {
                RepeatMode.OFF -> "Enable repeat all"
                RepeatMode.ALL -> "Enable repeat one"
                RepeatMode.ONE -> "Disable repeat"
            },
            active = repeatMode != RepeatMode.OFF,
            size = hitSize,
            onClick = onRepeatClick,
            enabled = inputEnabled,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun PocketFlipPadBody(modifier: Modifier = Modifier) {
    val colors = PocketFlipColors
    Canvas(modifier = modifier) {
        val wellRadius = size.minDimension * 0.46f
        val thickness = size.minDimension * 0.31f
        val armInset = size.minDimension * 0.085f
        val corner = CornerRadius(size.minDimension * 0.07f)
        val horizontalTop = (size.height - thickness) / 2f
        val verticalLeft = (size.width - thickness) / 2f
        val bodyColor = colors.button

        drawCircle(
            color = colors.controlWell,
            radius = wellRadius,
            center = center
        )
        drawCircle(
            color = colors.controlGroove,
            radius = wellRadius,
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )

        drawRoundRect(
            color = colors.buttonShadow,
            topLeft = Offset(armInset, horizontalTop + 3.dp.toPx()),
            size = Size(size.width - armInset * 2f, thickness),
            cornerRadius = corner
        )
        drawRoundRect(
            color = colors.buttonShadow,
            topLeft = Offset(verticalLeft, armInset + 3.dp.toPx()),
            size = Size(thickness, size.height - armInset * 2f),
            cornerRadius = corner
        )
        drawRoundRect(
            color = bodyColor,
            topLeft = Offset(armInset, horizontalTop),
            size = Size(size.width - armInset * 2f, thickness),
            cornerRadius = corner
        )
        drawRoundRect(
            color = bodyColor,
            topLeft = Offset(verticalLeft, armInset),
            size = Size(thickness, size.height - armInset * 2f),
            cornerRadius = corner
        )
        drawCircle(
            color = colors.buttonCenter,
            radius = thickness * 0.33f,
            center = center
        )
        drawLine(
            color = colors.buttonHighlight,
            start = Offset(armInset + corner.x, horizontalTop + 1.dp.toPx()),
            end = Offset(size.width - armInset - corner.x, horizontalTop + 1.dp.toPx()),
            strokeWidth = 1.dp.toPx()
        )
    }
}

@Composable
private fun PocketFlipPadHitTarget(
    icon: ImageVector,
    contentDescription: String,
    size: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true
) {
    val colors = PocketFlipColors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = modifier
            .size(size)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            if (isPressed) {
                drawCircle(
                    color = colors.buttonPressed.copy(alpha = 0.58f),
                    radius = drawContext.size.minDimension * 0.31f,
                    center = center
                )
            }
            if (active) {
                drawCircle(
                    color = colors.modeGlow,
                    radius = drawContext.size.minDimension * 0.29f,
                    center = center
                )
                val lampCenter = Offset(center.x, drawContext.size.height * 0.78f)
                drawCircle(
                    color = colors.modeLamp.copy(alpha = 0.38f),
                    radius = 4.dp.toPx(),
                    center = lampCenter
                )
                drawCircle(
                    color = colors.modeLamp,
                    radius = 2.dp.toPx(),
                    center = lampCenter
                )
            }
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) {
                PocketFlipColors.buttonActiveIcon
            } else {
                PocketFlipColors.buttonIcon
            },
            modifier = Modifier.size(size * 0.39f)
        )
    }
}

@Composable
private fun PocketFlipActionCluster(
    currentSong: Song?,
    isPlaying: Boolean,
    isCurrentSongFavorite: Boolean,
    onPlayPauseClick: () -> Unit,
    onToggleFavoriteClick: (Song) -> Unit,
    compact: Boolean,
    inputEnabled: Boolean,
    morphBounds: PocketFlipMorphBounds?,
    sharedOwner: PocketFlipSharedOwner
) {
    Box(
        modifier = Modifier.size(
            width = if (compact) 136.dp else 148.dp,
            height = if (compact) 96.dp else 104.dp
        ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(
                    width = if (compact) 134.dp else 146.dp,
                    height = if (compact) 86.dp else 92.dp
                )
                .background(
                    PocketFlipColors.controlWell,
                    RoundedCornerShape(50)
                )
                .pocketFlipActionPlateFinish()
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(if (compact) 0.dp else 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PocketFlipRoundAction(
                icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                markCount = 1,
                contentDescription = if (isPlaying) "Pause" else "Play",
                faceSize = if (compact) 48.dp else 52.dp,
                onClick = onPlayPauseClick,
                enabled = inputEnabled && sharedOwner == PocketFlipSharedOwner.EXPANDED,
                visualAlpha = if (sharedOwner == PocketFlipSharedOwner.EXPANDED) 1f else 0f,
                modifier = Modifier.onGloballyPositioned { coordinates ->
                    morphBounds?.updateExpandedPlay(coordinates.boundsInRoot())
                }
            )
            PocketFlipRoundAction(
                icon = if (isCurrentSongFavorite) {
                    Icons.Filled.Favorite
                } else {
                    Icons.Filled.FavoriteBorder
                },
                markCount = 2,
                contentDescription = if (isCurrentSongFavorite) {
                    "Remove from favorites"
                } else {
                    "Add to favorites"
                },
                faceSize = if (compact) 48.dp else 52.dp,
                active = isCurrentSongFavorite,
                enabled = inputEnabled,
                onClick = { currentSong?.let(onToggleFavoriteClick) }
            )
        }
    }
}

@Composable
private fun PocketFlipRoundAction(
    icon: ImageVector,
    markCount: Int,
    contentDescription: String,
    faceSize: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    visualAlpha: Float = 1f
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val faceColor = when {
        isPressed -> PocketFlipColors.actionPressed
        active -> PocketFlipColors.actionActive
        else -> PocketFlipColors.action
    }

    Column(
        modifier = modifier
            .width(faceSize + 16.dp)
            .graphicsLayer { alpha = visualAlpha.coerceIn(0f, 1f) }
            .then(if (visualAlpha > 0f) Modifier else Modifier.clearAndSetSemantics { }),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(faceSize + 12.dp)
                .semantics {
                    this.contentDescription = contentDescription
                    role = Role.Button
                }
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    enabled = enabled,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .offset(y = if (isPressed) 2.dp else 0.dp)
                    .size(faceSize)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                faceColor.copy(red = (faceColor.red + 0.12f).coerceAtMost(1f)),
                                faceColor
                            )
                        ),
                        CircleShape
                    )
                    .pocketFlipRoundButtonFinish(isPressed = isPressed),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = PocketFlipColors.actionIcon,
                    modifier = Modifier.size(faceSize * 0.42f)
                )
            }
        }
        Row(
            modifier = Modifier.height(6.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(markCount) {
                Box(
                    modifier = Modifier
                        .size(width = 4.dp, height = 2.dp)
                        .background(PocketFlipColors.controlMark, RoundedCornerShape(50))
                )
            }
        }
    }
}

@Composable
private fun PocketFlipDeckDetails(
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(if (compact) 24.dp else 28.dp)
            .padding(horizontal = if (compact) 4.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PocketFlipScrew()
        Spacer(modifier = Modifier.weight(1f))
        PocketFlipSpeakerGrille(compact = compact)
        Spacer(modifier = Modifier.weight(1f))
        PocketFlipScrew(reverseSlot = true)
    }
}

@Composable
private fun PocketFlipUtilitySwitch(
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    compact: Boolean,
    enabled: Boolean
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Column(
        modifier = Modifier
            .width(if (compact) 78.dp else 88.dp)
            .height(if (compact) 48.dp else 52.dp)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                enabled = enabled,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            color = PocketFlipColors.engravedText,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 8.sp,
            letterSpacing = 0.8.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .offset(y = if (isPressed) 2.dp else 0.dp)
                .size(
                    width = if (compact) 48.dp else 54.dp,
                    height = if (compact) 17.dp else 19.dp
                )
                .background(
                    if (isPressed) {
                        PocketFlipColors.utilityPressed
                    } else {
                        PocketFlipColors.utility
                    },
                    RoundedCornerShape(50)
                )
                .pocketFlipUtilitySwitchFinish(isPressed = isPressed)
        )
    }
}

@Composable
private fun PocketFlipSpeakerGrille(compact: Boolean) {
    val colors = PocketFlipColors
    val width = if (compact) 58.dp else 68.dp
    val height = if (compact) 21.dp else 24.dp
    val holes = listOf(
        0.10f to 0.50f,
        0.22f to 0.28f,
        0.22f to 0.72f,
        0.36f to 0.50f,
        0.49f to 0.28f,
        0.49f to 0.72f,
        0.63f to 0.50f,
        0.77f to 0.28f,
        0.77f to 0.72f,
        0.90f to 0.50f
    )

    Canvas(modifier = Modifier.size(width = width, height = height)) {
        val radius = if (compact) 1.7.dp.toPx() else 2.dp.toPx()
        holes.forEach { (x, y) ->
            drawCircle(
                color = colors.speakerHighlight,
                radius = radius,
                center = Offset(size.width * x, size.height * y + 1.dp.toPx())
            )
            drawCircle(
                color = colors.speaker,
                radius = radius,
                center = Offset(size.width * x, size.height * y)
            )
        }
    }
}

@Composable
private fun PocketFlipScrew(reverseSlot: Boolean = false) {
    val colors = PocketFlipColors
    Canvas(modifier = Modifier.size(13.dp)) {
        val radius = size.minDimension / 2f
        drawCircle(
            color = colors.shellShadow,
            radius = radius,
            center = center + Offset(0f, 1.dp.toPx())
        )
        drawCircle(
            color = colors.screw,
            radius = radius * 0.82f,
            center = center
        )
        val slotOffset = radius * 0.32f
        drawLine(
            color = colors.screwSlot,
            start = if (reverseSlot) {
                center + Offset(-slotOffset, slotOffset)
            } else {
                center + Offset(-slotOffset, -slotOffset)
            },
            end = if (reverseSlot) {
                center + Offset(slotOffset, -slotOffset)
            } else {
                center + Offset(slotOffset, slotOffset)
            },
            strokeWidth = 1.dp.toPx()
        )
    }
}
