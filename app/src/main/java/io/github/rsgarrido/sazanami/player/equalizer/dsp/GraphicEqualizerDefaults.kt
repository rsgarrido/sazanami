package io.github.rsgarrido.sazanami.player.equalizer.dsp

internal object GraphicEqualizerDefaults {
    const val Q = 1.41
    const val BAND_COUNT = 10

    private val standardFrequenciesHz = doubleArrayOf(
        31.0,
        62.0,
        125.0,
        250.0,
        500.0,
        1_000.0,
        2_000.0,
        4_000.0,
        8_000.0,
        16_000.0
    )

    val frequenciesHz: List<Double>
        get() = standardFrequenciesHz.toList()

    fun createFlatFilters(): List<EqualizerFilterSpec.Peaking> {
        return standardFrequenciesHz.map { frequencyHz ->
            EqualizerFilterSpec.Peaking(
                frequencyHz = frequencyHz,
                gainDb = 0.0,
                q = Q
            )
        }
    }
}
