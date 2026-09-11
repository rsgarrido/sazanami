package io.github.rsgarrido.sazanami.ui.player

import io.github.rsgarrido.sazanami.player.spectrum.SpectrumAvailability
import io.github.rsgarrido.sazanami.player.spectrum.SpectrumFrame
import kotlin.math.ceil
import kotlin.math.sqrt

/** Fixed-scale, UI-independent math shared by progressive spectrum-derived level meters. */
internal fun aggregateSpectrumRegionRms(
    frame: SpectrumFrame,
    startBand: Int = 0,
    endBandExclusive: Int = frame.bandCount
): Float {
    if (frame.availability != SpectrumAvailability.AVAILABLE) return 0f
    val start = startBand.coerceIn(0, frame.bandCount)
    val end = endBandExclusive.coerceIn(start, frame.bandCount)
    val count = end - start
    if (count == 0) return 0f

    var sumOfSquares = 0.0
    for (band in start until end) {
        val rawValue = frame.monoBand(band)
        val value = if (rawValue.isFinite()) rawValue.coerceIn(0f, 1f) else 0f
        sumOfSquares += value * value
    }
    return sqrt(sumOfSquares / count).toFloat().coerceIn(0f, 1f)
}

/** Applies fixed display gain and response; it never normalizes relative to the current frame. */
internal fun calibrateSpectrumMeterLevel(
    aggregateLevel: Float,
    displayGain: Float,
    responseExponent: Float
): Float {
    if (!aggregateLevel.isFinite() || !displayGain.isFinite() ||
        !responseExponent.isFinite() || displayGain <= 0f || responseExponent <= 0f
    ) {
        return 0f
    }
    val boundedLevel = aggregateLevel.coerceIn(0f, 1f)
    return (Math.pow(boundedLevel.toDouble(), responseExponent.toDouble()) * displayGain)
        .toFloat()
        .coerceIn(0f, 1f)
}

internal fun contiguousMeterFillCount(level: Float, segmentCount: Int): Int {
    if (segmentCount <= 0 || !level.isFinite() || level <= 0f) return 0
    return ceil(level.coerceIn(0f, 1f) * segmentCount)
        .toInt()
        .coerceIn(0, segmentCount)
}
