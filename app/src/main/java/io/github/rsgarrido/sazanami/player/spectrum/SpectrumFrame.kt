package io.github.rsgarrido.sazanami.player.spectrum

internal enum class SpectrumAvailability {
    AVAILABLE,
    UNAVAILABLE
}

/** Immutable, primitive-array-backed spectrum snapshot for future visual consumers. */
internal class SpectrumFrame internal constructor(
    private val monoBands: FloatArray,
    private val leftBands: FloatArray?,
    private val rightBands: FloatArray?,
    val sampleRateHz: Int,
    val availability: SpectrumAvailability,
    val timestampNanos: Long
) {
    val bandCount: Int
        get() = monoBands.size

    val hasStereoBands: Boolean
        get() = leftBands != null && rightBands != null

    fun monoBand(index: Int): Float = monoBands[index]

    fun leftBand(index: Int): Float? = leftBands?.get(index)

    fun rightBand(index: Int): Float? = rightBands?.get(index)

    fun copyMonoBandsInto(destination: FloatArray): Int {
        val count = minOf(destination.size, monoBands.size)
        monoBands.copyInto(destination, endIndex = count)
        return count
    }

    companion object {
        fun unavailable(
            bandCount: Int = SpectrumAnalyzerConfig.DEFAULT_BAND_COUNT,
            timestampNanos: Long = 0L
        ) = SpectrumFrame(
            monoBands = FloatArray(bandCount.coerceAtLeast(0)),
            leftBands = null,
            rightBands = null,
            sampleRateHz = 0,
            availability = SpectrumAvailability.UNAVAILABLE,
            timestampNanos = timestampNanos
        )
    }
}
