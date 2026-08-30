package io.github.rsgarrido.sazanami.player.equalizer.limiter

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

internal object LimiterMath {
    const val SILENCE_FLOOR_DBFS = -120.0
    private const val MIN_LINEAR_FOR_DB = 1.0e-6

    fun dbToLinear(db: Double): Double {
        require(db.isFinite()) { "Decibels must be finite" }
        return 10.0.pow(db / 20.0)
    }

    fun linearToDbfs(linearAmplitude: Double): Double {
        require(linearAmplitude.isFinite()) {
            "Linear amplitude must be finite"
        }
        if (linearAmplitude <= MIN_LINEAR_FOR_DB) {
            return SILENCE_FLOOR_DBFS
        }
        return (20.0 / ln(10.0) * ln(linearAmplitude))
            .coerceAtLeast(SILENCE_FLOOR_DBFS)
    }

    fun gainReductionDb(linearGain: Double): Double {
        require(linearGain.isFinite() && linearGain >= 0.0) {
            "Limiter gain must be finite and non-negative"
        }
        if (linearGain >= 1.0) return 0.0
        return (-linearToDbfs(linearGain.coerceAtMost(1.0)))
            .coerceAtLeast(0.0)
    }

    fun lookaheadFrames(sampleRateHz: Int): Int {
        require(sampleRateHz > 0) {
            "Sample rate must be positive"
        }
        return (
            sampleRateHz *
                LIMITER_LOOKAHEAD_MILLISECONDS /
                1_000.0
            ).roundToInt()
            .coerceAtLeast(1)
    }

    fun releaseCoefficient(sampleRateHz: Int): Double {
        require(sampleRateHz > 0) {
            "Sample rate must be positive"
        }
        val releaseFrames =
            sampleRateHz * LIMITER_RELEASE_MILLISECONDS / 1_000.0
        return exp(-1.0 / releaseFrames)
    }
}
