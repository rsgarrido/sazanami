package io.github.rsgarrido.sazanami.ui.player.pockethorizon

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs

internal fun clampHorizonAnalog(value: Offset): Offset {
    val length = value.getDistance()
    return if (!length.isFinite()) Offset.Zero else if (length > 1f) value / length else value
}

/** Dominant axis wins, including diagonals; movement never confirms or starts playback. */
internal fun horizonAnalogDirection(value: Offset): Int = when {
    value.getDistance() < .28f -> 0
    abs(value.x) > abs(value.y) -> if (value.x < 0) -2 else 2
    else -> if (value.y < 0) -1 else 1
}
