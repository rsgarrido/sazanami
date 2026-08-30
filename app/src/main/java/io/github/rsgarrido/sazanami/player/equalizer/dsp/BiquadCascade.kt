package io.github.rsgarrido.sazanami.player.equalizer.dsp

/**
 * Double state below -480 dBFS is inaudible and far below Float PCM output
 * precision. Flushing only filter history at this threshold prevents
 * pathological subnormal arithmetic after long high-Q decays.
 */
internal const val BIQUAD_STATE_FLUSH_THRESHOLD = 1.0e-24

/**
 * Direct Form I cascade with independent history for every channel and section.
 *
 * Coefficients and the four state values x1, x2, y1, and y2 are stored in
 * primitive arrays. State index `channel * sectionCapacity + section`
 * identifies one channel/section pair.
 */
internal class BiquadCascade {
    private val sectionCapacity: Int
    private val channelCapacity: Int
    private val sectionCount: Int
    private val b0: DoubleArray
    private val b1: DoubleArray
    private val b2: DoubleArray
    private val a1: DoubleArray
    private val a2: DoubleArray
    private val x1: DoubleArray
    private val x2: DoubleArray
    private val y1: DoubleArray
    private val y2: DoubleArray

    constructor(
        coefficients: List<BiquadCoefficients>,
        channelCount: Int
    ) {
        require(channelCount > 0) {
            "channelCount must be greater than 0"
        }
        sectionCapacity = coefficients.size
        channelCapacity = channelCount
        sectionCount = coefficients.size
        val stateSize = checkedStateSize(
            sectionCapacity = sectionCapacity,
            channelCapacity = channelCapacity
        )
        b0 = DoubleArray(sectionCapacity)
        b1 = DoubleArray(sectionCapacity)
        b2 = DoubleArray(sectionCapacity)
        a1 = DoubleArray(sectionCapacity)
        a2 = DoubleArray(sectionCapacity)
        coefficients.forEachIndexed { sectionIndex, coefficient ->
            b0[sectionIndex] = coefficient.b0
            b1[sectionIndex] = coefficient.b1
            b2[sectionIndex] = coefficient.b2
            a1[sectionIndex] = coefficient.a1
            a2[sectionIndex] = coefficient.a2
        }
        x1 = DoubleArray(stateSize)
        x2 = DoubleArray(stateSize)
        y1 = DoubleArray(stateSize)
        y2 = DoubleArray(stateSize)
    }

    constructor(
        preparedCascade: PreparedEqualizerCascade,
        minimumSectionCapacity: Int,
        minimumChannelCapacity: Int
    ) {
        sectionCapacity = maxOf(
            preparedCascade.sectionCount,
            minimumSectionCapacity
        )
        channelCapacity = maxOf(
            preparedCascade.channelCount,
            minimumChannelCapacity
        )
        sectionCount = preparedCascade.sectionCount
        val stateSize = checkedStateSize(
            sectionCapacity = sectionCapacity,
            channelCapacity = channelCapacity
        )
        b0 = DoubleArray(sectionCapacity)
        b1 = DoubleArray(sectionCapacity)
        b2 = DoubleArray(sectionCapacity)
        a1 = DoubleArray(sectionCapacity)
        a2 = DoubleArray(sectionCapacity)
        repeat(sectionCount) { sectionIndex ->
            b0[sectionIndex] = preparedCascade.coefficient(sectionIndex, 0)
            b1[sectionIndex] = preparedCascade.coefficient(sectionIndex, 1)
            b2[sectionIndex] = preparedCascade.coefficient(sectionIndex, 2)
            a1[sectionIndex] = preparedCascade.coefficient(sectionIndex, 3)
            a2[sectionIndex] = preparedCascade.coefficient(sectionIndex, 4)
        }
        x1 = DoubleArray(stateSize)
        x2 = DoubleArray(stateSize)
        y1 = DoubleArray(stateSize)
        y2 = DoubleArray(stateSize)
    }

    fun processSample(
        inputSample: Double,
        channelIndex: Int
    ): Double {
        var sectionInput = inputSample
        var sectionIndex = 0
        var stateIndex = channelIndex * sectionCapacity

        while (sectionIndex < sectionCount) {
            val stableInput = flushNearZero(sectionInput)
            val sectionOutput =
                b0[sectionIndex] * stableInput +
                    b1[sectionIndex] * x1[stateIndex] +
                    b2[sectionIndex] * x2[stateIndex] -
                    a1[sectionIndex] * y1[stateIndex] -
                    a2[sectionIndex] * y2[stateIndex]
            val stableOutput = flushNearZero(sectionOutput)

            x2[stateIndex] = flushNearZero(x1[stateIndex])
            x1[stateIndex] = stableInput
            y2[stateIndex] = flushNearZero(y1[stateIndex])
            y1[stateIndex] = stableOutput

            sectionInput = stableOutput
            sectionIndex++
            stateIndex++
        }

        return sectionInput
    }

    private fun flushNearZero(value: Double): Double {
        return if (
            value > -BIQUAD_STATE_FLUSH_THRESHOLD &&
            value < BIQUAD_STATE_FLUSH_THRESHOLD
        ) {
            0.0
        } else {
            value
        }
    }

    fun reset() {
        x1.fill(0.0)
        x2.fill(0.0)
        y1.fill(0.0)
        y2.fill(0.0)
    }

    fun capacitySnapshot(): EqualizerEngineCapacity {
        return EqualizerEngineCapacity(
            sectionCapacity = sectionCapacity,
            channelCapacity = channelCapacity,
            coefficientArrayIdentity = System.identityHashCode(b0),
            stateArrayIdentity = System.identityHashCode(x1)
        )
    }

    private fun checkedStateSize(
        sectionCapacity: Int,
        channelCapacity: Int
    ): Int {
        require(sectionCapacity >= 0) {
            "sectionCapacity must be non-negative"
        }
        require(channelCapacity > 0) {
            "channelCapacity must be greater than 0"
        }
        val stateValueCount =
            sectionCapacity.toLong() * channelCapacity
        require(stateValueCount <= Int.MAX_VALUE) {
            "channel and section capacity require too much filter state"
        }
        return stateValueCount.toInt()
    }
}
