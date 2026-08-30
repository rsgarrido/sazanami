package io.github.rsgarrido.sazanami.ui.player

import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

internal fun buildRetroMeterLevels(
    amplitudes: List<Float>?,
    currentPositionMs: Long,
    durationMs: Long,
    columnCount: Int,
    animationPhase: Float,
    isPlaying: Boolean,
    songSeed: Long
): List<Float>? {
    if (columnCount <= 0) return null
    val output = FloatArray(columnCount)
    if (!fillRetroMeterLevels(
            output = output,
            amplitudes = amplitudes,
            currentPositionMs = currentPositionMs,
            durationMs = durationMs,
            animationPhase = animationPhase,
            isPlaying = isPlaying,
            songSeed = songSeed
        )
    ) return null
    return output.toList()
}

internal fun fillRetroMeterLevels(
    output: FloatArray,
    amplitudes: List<Float>?,
    currentPositionMs: Long,
    durationMs: Long,
    animationPhase: Float,
    isPlaying: Boolean,
    songSeed: Long
): Boolean {
    if (amplitudes.isNullOrEmpty() || durationMs <= 0L || output.isEmpty()) return false

    val progress = (currentPositionMs.toDouble() / durationMs.toDouble())
        .coerceIn(0.0, 1.0)
        .toFloat()
    val currentIndex = progress * amplitudes.lastIndex
    val immediateEnergy = sampleWaveform(amplitudes, currentIndex)
    val smoothedEnergy = (
            immediateEnergy * 0.82f +
                    sampleWaveform(amplitudes, currentIndex - 0.5f) * 0.08f +
                    sampleWaveform(amplitudes, currentIndex + 0.35f) * 0.10f
            ).coerceIn(0f, 1f)
    val localEnergy = if (immediateEnergy <= RETRO_METER_SILENCE_GATE) {
        immediateEnergy
    } else {
        smoothedEnergy
    }
    val gatedEnergy = ((localEnergy - RETRO_METER_SILENCE_GATE) /
            (1f - RETRO_METER_SILENCE_GATE)).coerceIn(0f, 1f)
    val normalizedPhase = if (animationPhase.isFinite()) {
        animationPhase - floor(animationPhase)
    } else {
        0f
    }
    val phaseRadians = normalizedPhase * (2f * PI.toFloat())

    output.indices.forEach { columnIndex ->
        val bandScale = 0.62f + seededUnit(songSeed, columnIndex, BAND_SCALE_SALT) * 0.38f
        val phaseOffset = seededUnit(songSeed, columnIndex, PHASE_OFFSET_SALT) *
                (2f * PI.toFloat())
        val harmonic = 1 + (
                seededUnit(songSeed, columnIndex, HARMONIC_SALT) * 3f
                ).toInt().coerceIn(0, 2)
        val bandEnergy = gatedEnergy * bandScale
        val baseLevel = if (isPlaying) {
            bandEnergy * 0.86f
        } else {
            bandEnergy * 0.58f
        }
        val movement = if (isPlaying) {
            val bounce = sin(phaseRadians * harmonic + phaseOffset)
            val flutter = sin(phaseRadians * (harmonic + 1) + phaseOffset * 0.73f)
            gatedEnergy * (
                    bounce * (0.08f + gatedEnergy * 0.14f) +
                            flutter * 0.03f
                    )
        } else {
            0f
        }

        output[columnIndex] = (baseLevel + movement).coerceIn(0f, 0.98f)
    }
    return true
}

internal fun isRetroMeterEffectivelySilent(
    amplitudes: List<Float>?,
    currentPositionMs: Long,
    durationMs: Long
): Boolean {
    if (amplitudes.isNullOrEmpty() || durationMs <= 0L) return false
    val progress = (currentPositionMs.toDouble() / durationMs.toDouble())
        .coerceIn(0.0, 1.0)
        .toFloat()
    return sampleWaveform(amplitudes, progress * amplitudes.lastIndex) <=
        RETRO_METER_SILENCE_GATE
}

private fun sampleWaveform(
    amplitudes: List<Float>,
    sourceIndex: Float
): Float {
    val clampedIndex = sourceIndex.coerceIn(0f, amplitudes.lastIndex.toFloat())
    val lowerIndex = floor(clampedIndex).toInt()
    val upperIndex = (lowerIndex + 1).coerceAtMost(amplitudes.lastIndex)
    val fraction = clampedIndex - lowerIndex
    return safeAmplitude(amplitudes[lowerIndex]) * (1f - fraction) +
            safeAmplitude(amplitudes[upperIndex]) * fraction
}

private fun seededUnit(
    songSeed: Long,
    columnIndex: Int,
    salt: Long
): Float {
    var state = songSeed xor salt xor (columnIndex + 1L) * 1_099_511_628_211L
    state = state * 6_364_136_223_846_793_005L + 1_442_695_040_888_963_407L
    return ((state ushr 40) and 0xFFFF).toFloat() / 0xFFFF
}

private fun safeAmplitude(amplitude: Float): Float =
    if (amplitude.isFinite()) amplitude.coerceIn(0f, 1f) else 0f

private const val BAND_SCALE_SALT = 0x42_41_4E_44L
private const val PHASE_OFFSET_SALT = 0x50_48_41_53_45L
private const val HARMONIC_SALT = 0x48_41_52_4DL
internal const val RETRO_METER_SILENCE_GATE = 0.055f
