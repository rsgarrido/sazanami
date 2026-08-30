package io.github.rsgarrido.sazanami.player.equalizer.dsp

import kotlin.math.abs
import kotlin.math.pow

internal const val EQUALIZER_DB_EPSILON = 1e-12

internal fun isEffectivelyZeroDb(gainDb: Double): Boolean {
    return abs(gainDb) <= EQUALIZER_DB_EPSILON
}

internal fun decibelsToLinear(gainDb: Double): Double {
    return 10.0.pow(gainDb / 20.0)
}

internal fun isEqualizerFrequencySupported(
    frequencyHz: Double,
    sampleRateHz: Int
): Boolean {
    return sampleRateHz > 0 &&
        frequencyHz.isFinite() &&
        frequencyHz > 0.0 &&
        frequencyHz < sampleRateHz / 2.0
}
