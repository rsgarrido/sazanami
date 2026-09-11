package io.github.rsgarrido.sazanami.ui.player

import io.github.rsgarrido.sazanami.player.spectrum.SpectrumAvailability
import io.github.rsgarrido.sazanami.player.spectrum.SpectrumFrame
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Resamples the analyzer's logarithmic bands into a caller-owned visual buffer.
 *
 * Destination cells cover equal-width ranges of the selected source-band span. Each cell is the
 * overlap-weighted average of those source bands, so frequency order is preserved without
 * allocating intermediate collections.
 */
internal fun fillSpectrumRange(
    frame: SpectrumFrame,
    output: FloatArray,
    sourceStartBand: Int = 0,
    sourceEndBandExclusive: Int = frame.bandCount
): Boolean {
    output.fill(0f)
    if (frame.availability != SpectrumAvailability.AVAILABLE || output.isEmpty()) return false

    val startBand = sourceStartBand.coerceIn(0, frame.bandCount)
    val endBand = sourceEndBandExclusive.coerceIn(startBand, frame.bandCount)
    val sourceBandCount = endBand - startBand
    if (sourceBandCount == 0) return false

    val sourceBandsPerCell = sourceBandCount.toFloat() / output.size
    output.indices.forEach { destinationIndex ->
        val sourceStart = startBand + destinationIndex * sourceBandsPerCell
        val sourceEnd = startBand + (destinationIndex + 1) * sourceBandsPerCell
        val firstSourceBand = floor(sourceStart).toInt().coerceAtLeast(startBand)
        val lastSourceBandExclusive = ceil(sourceEnd).toInt().coerceAtMost(endBand)
        var weightedEnergy = 0f
        var totalWeight = 0f

        for (sourceBand in firstSourceBand until lastSourceBandExclusive) {
            val overlap = (
                minOf(sourceEnd, sourceBand + 1f) - maxOf(sourceStart, sourceBand.toFloat())
            ).coerceAtLeast(0f)
            val rawBandEnergy = frame.monoBand(sourceBand)
            val bandEnergy = if (rawBandEnergy.isFinite()) {
                rawBandEnergy.coerceIn(0f, 1f)
            } else {
                0f
            }
            weightedEnergy += bandEnergy * overlap
            totalWeight += overlap
        }

        output[destinationIndex] = if (totalWeight > 0f) {
            (weightedEnergy / totalWeight).coerceIn(0f, 1f)
        } else {
            0f
        }
    }
    return true
}

internal fun fillRetroRackSpectrum(
    frame: SpectrumFrame,
    columns: FloatArray
): Boolean {
    val available = fillSpectrumRange(frame, columns)
    if (!available) return false

    val denominator = columns.lastIndex.coerceAtLeast(1).toFloat()
    columns.indices.forEach { index ->
        val frequencyPosition = index / denominator
        val displayGain = RETRO_RACK_LOW_FREQUENCY_GAIN +
                (RETRO_RACK_HIGH_FREQUENCY_GAIN - RETRO_RACK_LOW_FREQUENCY_GAIN) *
                frequencyPosition
        columns[index] = (columns[index] * displayGain).coerceIn(0f, 1f)
    }
    return true
}

private const val RETRO_RACK_LOW_FREQUENCY_GAIN = 0.72f
private const val RETRO_RACK_HIGH_FREQUENCY_GAIN = 1.08f
