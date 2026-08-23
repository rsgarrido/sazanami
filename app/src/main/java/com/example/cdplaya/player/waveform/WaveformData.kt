package com.example.cdplaya.player.waveform

import kotlin.math.ceil
import kotlin.math.floor

data class WaveformData(
    val amplitudes: List<Float>,
    val sourceKey: String
)

internal fun normalizeWaveformAmplitudes(amplitudes: List<Float>): List<Float> {
    if (amplitudes.isEmpty()) return emptyList()

    val sanitized = amplitudes.map { amplitude ->
        if (amplitude.isFinite()) amplitude.coerceAtLeast(0f) else 0f
    }
    val maximum = sanitized.maxOrNull() ?: return emptyList()
    if (maximum <= 0f) return List(sanitized.size) { 0f }

    return sanitized.map { amplitude ->
        (amplitude / maximum).coerceIn(0f, 1f)
    }
}

internal fun mapWaveformAmplitudes(
    amplitudes: List<Float>,
    barCount: Int
): List<Float> {
    if (amplitudes.isEmpty() || barCount <= 0) return emptyList()

    val normalized = amplitudes.map { amplitude ->
        if (amplitude.isFinite()) amplitude.coerceIn(0f, 1f) else 0f
    }
    if (normalized.size == barCount) return normalized

    return List(barCount) { index ->
        val start = index.toDouble() * normalized.size / barCount
        val end = (index + 1).toDouble() * normalized.size / barCount
        val firstSourceIndex = floor(start).toInt().coerceIn(normalized.indices)
        val lastSourceIndex = (ceil(end).toInt() - 1).coerceIn(normalized.indices)

        var weightedTotal = 0.0
        var totalWeight = 0.0
        var localPeak = 0f
        for (sourceIndex in firstSourceIndex..lastSourceIndex) {
            val overlapStart = maxOf(start, sourceIndex.toDouble())
            val overlapEnd = minOf(end, sourceIndex + 1.0)
            val weight = (overlapEnd - overlapStart).coerceAtLeast(0.0)
            weightedTotal += normalized[sourceIndex] * weight
            totalWeight += weight
            if (weight > 0.0) {
                localPeak = maxOf(localPeak, normalized[sourceIndex])
            }
        }

        if (totalWeight > 0.0) {
            val average = (weightedTotal / totalWeight).toFloat()
            // Keep mostly the time-weighted shape, but retain a small amount of local peak
            // information so 42/48/72-bar seekbars do not visually flatten short changes.
            (average * 0.78f + localPeak * 0.22f).coerceIn(0f, 1f)
        } else {
            normalized[firstSourceIndex]
        }
    }
}
