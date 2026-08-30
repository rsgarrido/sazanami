package io.github.rsgarrido.sazanami.player.equalizer.dsp

/**
 * Double-precision Direct Form I reference equalizer engine.
 *
 * Separate arrays and exact in-place processing with matching offsets are
 * supported. Partially overlapping ranges in the same array are rejected.
 */
internal class KotlinEqualizerDspEngine : EqualizerDspEngine {
    private var configured = false
    private var channelCount = 0
    private var bypass = true
    private var preampMultiplier = 1.0
    private var cascade: BiquadCascade? = null

    fun configure(
        preparedCascade: PreparedEqualizerCascade,
        minimumSectionCapacity: Int = 0,
        minimumChannelCapacity: Int = 0
    ) {
        require(minimumSectionCapacity >= 0) {
            "minimumSectionCapacity must be non-negative"
        }
        require(minimumChannelCapacity >= 0) {
            "minimumChannelCapacity must be non-negative"
        }

        val nextCascade = BiquadCascade(
            preparedCascade = preparedCascade,
            minimumSectionCapacity = minimumSectionCapacity,
            minimumChannelCapacity = minimumChannelCapacity
        )
        channelCount = preparedCascade.channelCount
        bypass = false
        preampMultiplier = preparedCascade.effectivePreampMultiplier
        cascade = nextCascade
        configured = true
    }

    fun capacitySnapshot(): EqualizerEngineCapacity? {
        return cascade?.capacitySnapshot()
    }

    override fun configure(
        configuration: EqualizerConfiguration,
        sampleRateHz: Int,
        channelCount: Int,
        automaticHeadroomDb: Double
    ) {
        require(sampleRateHz > 0) {
            "sampleRateHz must be greater than 0"
        }
        require(channelCount > 0) {
            "channelCount must be greater than 0"
        }
        require(automaticHeadroomDb.isFinite() && automaticHeadroomDb >= 0.0) {
            "automaticHeadroomDb must be finite and non-negative"
        }

        val nextBypass = configuration.isEffectivelyFlat

        if (nextBypass) {
            this.channelCount = channelCount
            bypass = true
            preampMultiplier = 1.0
            cascade = null
            configured = true
            return
        }

        val effectivePreampDb =
            configuration.preampDb - automaticHeadroomDb
        require(effectivePreampDb.isFinite()) {
            "effective preamp must be finite"
        }
        val nextPreampMultiplier = decibelsToLinear(effectivePreampDb)
        require(nextPreampMultiplier.isFinite()) {
            "effective preamp produces a non-finite multiplier"
        }

        val coefficients = configuration.filters
            .asSequence()
            .filter { filter -> filter.hasAudibleEffect }
            .map { filter ->
                BiquadDesigner.design(
                    filter = filter,
                    sampleRateHz = sampleRateHz
                )
            }
            .toList()
        val nextCascade = BiquadCascade(
            coefficients = coefficients,
            channelCount = channelCount
        )

        this.channelCount = channelCount
        bypass = false
        preampMultiplier = nextPreampMultiplier
        cascade = nextCascade
        configured = true
    }

    override fun processInterleaved(
        input: FloatArray,
        inputOffset: Int,
        output: FloatArray,
        outputOffset: Int,
        frameCount: Int
    ) {
        check(configured) {
            "Equalizer engine must be configured before processing"
        }
        validateBufferRange(
            input = input,
            inputOffset = inputOffset,
            output = output,
            outputOffset = outputOffset,
            frameCount = frameCount
        )

        val sampleCount = (frameCount.toLong() * channelCount).toInt()
        if (bypass) {
            if (input !== output || inputOffset != outputOffset) {
                input.copyInto(
                    destination = output,
                    destinationOffset = outputOffset,
                    startIndex = inputOffset,
                    endIndex = inputOffset + sampleCount
                )
            }
            return
        }

        val configuredCascade = checkNotNull(cascade)
        var sampleIndex = 0
        while (sampleIndex < sampleCount) {
            val channelIndex = sampleIndex % channelCount
            val preampedSample =
                input[inputOffset + sampleIndex].toDouble() * preampMultiplier
            output[outputOffset + sampleIndex] = configuredCascade.processSample(
                inputSample = preampedSample,
                channelIndex = channelIndex
            ).toFloat()
            sampleIndex++
        }
    }

    override fun reset() {
        cascade?.reset()
    }

    private fun validateBufferRange(
        input: FloatArray,
        inputOffset: Int,
        output: FloatArray,
        outputOffset: Int,
        frameCount: Int
    ) {
        require(inputOffset >= 0) {
            "inputOffset must be non-negative"
        }
        require(outputOffset >= 0) {
            "outputOffset must be non-negative"
        }
        require(frameCount >= 0) {
            "frameCount must be non-negative"
        }

        val sampleCount = frameCount.toLong() * channelCount
        require(sampleCount <= Int.MAX_VALUE) {
            "required sample count overflows Int"
        }
        require(inputOffset.toLong() + sampleCount <= input.size) {
            "input does not contain the requested frames"
        }
        require(outputOffset.toLong() + sampleCount <= output.size) {
            "output does not contain the requested frames"
        }

        if (input === output && inputOffset != outputOffset && sampleCount > 0) {
            val inputEnd = inputOffset.toLong() + sampleCount
            val outputEnd = outputOffset.toLong() + sampleCount
            val rangesOverlap =
                inputOffset.toLong() < outputEnd &&
                    outputOffset.toLong() < inputEnd
            require(!rangesOverlap) {
                "partially overlapping input and output ranges are unsupported"
            }
        }
    }
}

internal data class EqualizerEngineCapacity(
    val sectionCapacity: Int,
    val channelCapacity: Int,
    val coefficientArrayIdentity: Int,
    val stateArrayIdentity: Int
)
