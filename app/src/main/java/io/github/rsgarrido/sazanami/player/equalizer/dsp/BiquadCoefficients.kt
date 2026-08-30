package io.github.rsgarrido.sazanami.player.equalizer.dsp

/**
 * Normalized biquad coefficients. The denominator coefficient a0 is always 1.
 */
internal data class BiquadCoefficients(
    val b0: Double,
    val b1: Double,
    val b2: Double,
    val a1: Double,
    val a2: Double
) {
    init {
        require(
            b0.isFinite() &&
                b1.isFinite() &&
                b2.isFinite() &&
                a1.isFinite() &&
                a2.isFinite()
        ) {
            "Biquad coefficients must be finite"
        }
    }

    companion object {
        val UNITY = BiquadCoefficients(
            b0 = 1.0,
            b1 = 0.0,
            b2 = 0.0,
            a1 = 0.0,
            a2 = 0.0
        )
    }
}
