package io.github.rsgarrido.sazanami.player.equalizer.dsp

/**
 * Validated coefficients prepared away from the real-time processing loop.
 *
 * Coefficient layout is five values per section: b0, b1, b2, a1, a2.
 */
internal class PreparedEqualizerCascade(
    val sampleRateHz: Int,
    val channelCount: Int,
    val sectionCount: Int,
    val effectivePreampMultiplier: Double,
    coefficients: DoubleArray
) {
    private val coefficientValues = coefficients.copyOf()

    init {
        require(sampleRateHz > 0) {
            "sampleRateHz must be greater than 0"
        }
        require(channelCount > 0) {
            "channelCount must be greater than 0"
        }
        require(sectionCount >= 0) {
            "sectionCount must be non-negative"
        }
        require(coefficientValues.size == sectionCount * VALUES_PER_SECTION) {
            "coefficient count does not match sectionCount"
        }
        require(
            effectivePreampMultiplier.isFinite() &&
                effectivePreampMultiplier >= 0.0
        ) {
            "effectivePreampMultiplier must be finite and non-negative"
        }
        require(coefficientValues.all(Double::isFinite)) {
            "all prepared coefficients must be finite"
        }
    }

    fun coefficientsCopy(): DoubleArray = coefficientValues.copyOf()

    internal fun coefficient(
        sectionIndex: Int,
        valueIndex: Int
    ): Double {
        return coefficientValues[
            sectionIndex * VALUES_PER_SECTION + valueIndex
        ]
    }

    companion object {
        const val VALUES_PER_SECTION = 5
    }
}
