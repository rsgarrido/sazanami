package io.github.rsgarrido.sazanami.player.equalizer.dsp

/**
 * Immutable parameters for one equalizer section.
 *
 * Peaking filters use quality factor [Peaking.q]. Shelf filters use the RBJ
 * shelf-slope parameter [LowShelf.slope] or [HighShelf.slope].
 */
internal sealed interface EqualizerFilterSpec {
    val frequencyHz: Double
    val enabled: Boolean

    data class Peaking(
        override val frequencyHz: Double,
        val gainDb: Double,
        val q: Double,
        override val enabled: Boolean = true
    ) : EqualizerFilterSpec

    data class LowShelf(
        override val frequencyHz: Double,
        val gainDb: Double,
        val slope: Double = 1.0,
        override val enabled: Boolean = true
    ) : EqualizerFilterSpec

    data class HighShelf(
        override val frequencyHz: Double,
        val gainDb: Double,
        val slope: Double = 1.0,
        override val enabled: Boolean = true
    ) : EqualizerFilterSpec

    data class LowPass(
        override val frequencyHz: Double,
        val q: Double,
        override val enabled: Boolean = true
    ) : EqualizerFilterSpec

    data class HighPass(
        override val frequencyHz: Double,
        val q: Double,
        override val enabled: Boolean = true
    ) : EqualizerFilterSpec

    data class Notch(
        override val frequencyHz: Double,
        val q: Double,
        override val enabled: Boolean = true
    ) : EqualizerFilterSpec

    /**
     * RBJ constant-0-dB-peak band-pass section.
     */
    data class BandPass(
        override val frequencyHz: Double,
        val q: Double,
        override val enabled: Boolean = true
    ) : EqualizerFilterSpec
}

internal val EqualizerFilterSpec.hasAudibleEffect: Boolean
    get() = enabled && when (this) {
        is EqualizerFilterSpec.Peaking ->
            !isEffectivelyZeroDb(gainDb)
        is EqualizerFilterSpec.LowShelf ->
            !isEffectivelyZeroDb(gainDb)
        is EqualizerFilterSpec.HighShelf ->
            !isEffectivelyZeroDb(gainDb)
        is EqualizerFilterSpec.LowPass,
        is EqualizerFilterSpec.HighPass,
        is EqualizerFilterSpec.Notch,
        is EqualizerFilterSpec.BandPass -> true
    }
