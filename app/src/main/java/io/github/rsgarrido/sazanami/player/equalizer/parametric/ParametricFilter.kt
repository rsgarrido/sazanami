package io.github.rsgarrido.sazanami.player.equalizer.parametric

import java.util.UUID
import kotlin.math.round

enum class ParametricFilterType {
    PEAKING,
    LOW_SHELF,
    HIGH_SHELF,
    LOW_PASS,
    HIGH_PASS,
    NOTCH,
    BAND_PASS
}

sealed interface ParametricFilter {
    val id: String
    val enabled: Boolean
    val frequencyHz: Double
    val type: ParametricFilterType

    data class Peaking(
        override val id: String,
        override val enabled: Boolean,
        override val frequencyHz: Double,
        val gainDb: Double,
        val q: Double
    ) : ParametricFilter {
        override val type: ParametricFilterType =
            ParametricFilterType.PEAKING

        init {
            validateIdentityAndFrequency(id, frequencyHz)
            requireValidGain(gainDb)
            requireValidQ(q)
        }
    }

    data class LowShelf(
        override val id: String,
        override val enabled: Boolean,
        override val frequencyHz: Double,
        val gainDb: Double,
        val slope: Double
    ) : ParametricFilter {
        override val type: ParametricFilterType =
            ParametricFilterType.LOW_SHELF

        init {
            validateIdentityAndFrequency(id, frequencyHz)
            requireValidGain(gainDb)
            requireValidShelfSlope(slope)
        }
    }

    data class HighShelf(
        override val id: String,
        override val enabled: Boolean,
        override val frequencyHz: Double,
        val gainDb: Double,
        val slope: Double
    ) : ParametricFilter {
        override val type: ParametricFilterType =
            ParametricFilterType.HIGH_SHELF

        init {
            validateIdentityAndFrequency(id, frequencyHz)
            requireValidGain(gainDb)
            requireValidShelfSlope(slope)
        }
    }

    data class LowPass(
        override val id: String,
        override val enabled: Boolean,
        override val frequencyHz: Double,
        val q: Double
    ) : ParametricFilter {
        override val type: ParametricFilterType =
            ParametricFilterType.LOW_PASS

        init {
            validateIdentityAndFrequency(id, frequencyHz)
            requireValidQ(q)
        }
    }

    data class HighPass(
        override val id: String,
        override val enabled: Boolean,
        override val frequencyHz: Double,
        val q: Double
    ) : ParametricFilter {
        override val type: ParametricFilterType =
            ParametricFilterType.HIGH_PASS

        init {
            validateIdentityAndFrequency(id, frequencyHz)
            requireValidQ(q)
        }
    }

    data class Notch(
        override val id: String,
        override val enabled: Boolean,
        override val frequencyHz: Double,
        val q: Double
    ) : ParametricFilter {
        override val type: ParametricFilterType =
            ParametricFilterType.NOTCH

        init {
            validateIdentityAndFrequency(id, frequencyHz)
            requireValidQ(q)
        }
    }

    data class BandPass(
        override val id: String,
        override val enabled: Boolean,
        override val frequencyHz: Double,
        val q: Double
    ) : ParametricFilter {
        override val type: ParametricFilterType =
            ParametricFilterType.BAND_PASS

        init {
            validateIdentityAndFrequency(id, frequencyHz)
            requireValidQ(q)
        }
    }
}

internal object ParametricFilterFactory {
    fun default(
        type: ParametricFilterType = ParametricFilterType.PEAKING,
        id: String = UUID.randomUUID().toString()
    ): ParametricFilter = when (type) {
        ParametricFilterType.PEAKING -> ParametricFilter.Peaking(
            id = id,
            enabled = true,
            frequencyHz = 1_000.0,
            gainDb = 0.0,
            q = 1.0
        )

        ParametricFilterType.LOW_SHELF -> ParametricFilter.LowShelf(
            id = id,
            enabled = true,
            frequencyHz = 100.0,
            gainDb = 0.0,
            slope = 1.0
        )

        ParametricFilterType.HIGH_SHELF -> ParametricFilter.HighShelf(
            id = id,
            enabled = true,
            frequencyHz = 10_000.0,
            gainDb = 0.0,
            slope = 1.0
        )

        ParametricFilterType.LOW_PASS -> ParametricFilter.LowPass(
            id = id,
            enabled = true,
            frequencyHz = 18_000.0,
            q = 0.71
        )

        ParametricFilterType.HIGH_PASS -> ParametricFilter.HighPass(
            id = id,
            enabled = true,
            frequencyHz = 20.0,
            q = 0.71
        )

        ParametricFilterType.NOTCH -> ParametricFilter.Notch(
            id = id,
            enabled = true,
            frequencyHz = 1_000.0,
            q = 4.0
        )

        ParametricFilterType.BAND_PASS -> ParametricFilter.BandPass(
            id = id,
            enabled = true,
            frequencyHz = 1_000.0,
            q = 1.0
        )
    }
}

internal val ParametricFilter.gainDbOrNull: Double?
    get() = when (this) {
        is ParametricFilter.Peaking -> gainDb
        is ParametricFilter.LowShelf -> gainDb
        is ParametricFilter.HighShelf -> gainDb
        is ParametricFilter.LowPass,
        is ParametricFilter.HighPass,
        is ParametricFilter.Notch,
        is ParametricFilter.BandPass -> null
    }

internal val ParametricFilter.qOrNull: Double?
    get() = when (this) {
        is ParametricFilter.Peaking -> q
        is ParametricFilter.LowPass -> q
        is ParametricFilter.HighPass -> q
        is ParametricFilter.Notch -> q
        is ParametricFilter.BandPass -> q
        is ParametricFilter.LowShelf,
        is ParametricFilter.HighShelf -> null
    }

internal val ParametricFilter.slopeOrNull: Double?
    get() = when (this) {
        is ParametricFilter.LowShelf -> slope
        is ParametricFilter.HighShelf -> slope
        is ParametricFilter.Peaking,
        is ParametricFilter.LowPass,
        is ParametricFilter.HighPass,
        is ParametricFilter.Notch,
        is ParametricFilter.BandPass -> null
    }

internal val ParametricFilter.hasAudibleEffect: Boolean
    get() = enabled && when (this) {
        is ParametricFilter.Peaking -> gainDb != 0.0
        is ParametricFilter.LowShelf -> gainDb != 0.0
        is ParametricFilter.HighShelf -> gainDb != 0.0
        is ParametricFilter.LowPass,
        is ParametricFilter.HighPass,
        is ParametricFilter.Notch,
        is ParametricFilter.BandPass -> true
    }

internal fun ParametricFilter.normalized(): ParametricFilter = when (this) {
    is ParametricFilter.Peaking -> copy(
        frequencyHz = normalizeParametricFrequency(frequencyHz),
        gainDb = normalizeParametricGain(gainDb),
        q = normalizeParametricQ(q)
    )

    is ParametricFilter.LowShelf -> copy(
        frequencyHz = normalizeParametricFrequency(frequencyHz),
        gainDb = normalizeParametricGain(gainDb),
        slope = normalizeParametricShelfSlope(slope)
    )

    is ParametricFilter.HighShelf -> copy(
        frequencyHz = normalizeParametricFrequency(frequencyHz),
        gainDb = normalizeParametricGain(gainDb),
        slope = normalizeParametricShelfSlope(slope)
    )

    is ParametricFilter.LowPass -> copy(
        frequencyHz = normalizeParametricFrequency(frequencyHz),
        q = normalizeParametricQ(q)
    )

    is ParametricFilter.HighPass -> copy(
        frequencyHz = normalizeParametricFrequency(frequencyHz),
        q = normalizeParametricQ(q)
    )

    is ParametricFilter.Notch -> copy(
        frequencyHz = normalizeParametricFrequency(frequencyHz),
        q = normalizeParametricQ(q)
    )

    is ParametricFilter.BandPass -> copy(
        frequencyHz = normalizeParametricFrequency(frequencyHz),
        q = normalizeParametricQ(q)
    )
}

internal fun ParametricFilter.withEnabled(value: Boolean): ParametricFilter =
    when (this) {
        is ParametricFilter.Peaking -> copy(enabled = value)
        is ParametricFilter.LowShelf -> copy(enabled = value)
        is ParametricFilter.HighShelf -> copy(enabled = value)
        is ParametricFilter.LowPass -> copy(enabled = value)
        is ParametricFilter.HighPass -> copy(enabled = value)
        is ParametricFilter.Notch -> copy(enabled = value)
        is ParametricFilter.BandPass -> copy(enabled = value)
    }

internal fun ParametricFilter.withFrequencyHz(value: Double): ParametricFilter {
    val normalized = normalizeParametricFrequency(value)
    return when (this) {
        is ParametricFilter.Peaking -> copy(frequencyHz = normalized)
        is ParametricFilter.LowShelf -> copy(frequencyHz = normalized)
        is ParametricFilter.HighShelf -> copy(frequencyHz = normalized)
        is ParametricFilter.LowPass -> copy(frequencyHz = normalized)
        is ParametricFilter.HighPass -> copy(frequencyHz = normalized)
        is ParametricFilter.Notch -> copy(frequencyHz = normalized)
        is ParametricFilter.BandPass -> copy(frequencyHz = normalized)
    }
}

internal fun ParametricFilter.withGainDb(value: Double): ParametricFilter {
    val normalized = normalizeParametricGain(value)
    return when (this) {
        is ParametricFilter.Peaking -> copy(gainDb = normalized)
        is ParametricFilter.LowShelf -> copy(gainDb = normalized)
        is ParametricFilter.HighShelf -> copy(gainDb = normalized)
        else -> error("$type does not use gain")
    }
}

internal fun ParametricFilter.withQ(value: Double): ParametricFilter {
    val normalized = normalizeParametricQ(value)
    return when (this) {
        is ParametricFilter.Peaking -> copy(q = normalized)
        is ParametricFilter.LowPass -> copy(q = normalized)
        is ParametricFilter.HighPass -> copy(q = normalized)
        is ParametricFilter.Notch -> copy(q = normalized)
        is ParametricFilter.BandPass -> copy(q = normalized)
        else -> error("$type does not use Q")
    }
}

internal fun ParametricFilter.withShelfSlope(
    value: Double
): ParametricFilter {
    val normalized = normalizeParametricShelfSlope(value)
    return when (this) {
        is ParametricFilter.LowShelf -> copy(slope = normalized)
        is ParametricFilter.HighShelf -> copy(slope = normalized)
        else -> error("$type does not use shelf slope")
    }
}

internal fun ParametricFilter.changeType(
    newType: ParametricFilterType
): ParametricFilter {
    if (newType == type) return this
    val preservedGain = gainDbOrNull ?: 0.0
    val preservedQ = qOrNull ?: when (newType) {
        ParametricFilterType.NOTCH -> 4.0
        ParametricFilterType.LOW_PASS,
        ParametricFilterType.HIGH_PASS -> 0.71
        else -> 1.0
    }
    val preservedSlope = slopeOrNull ?: 1.0
    return when (newType) {
        ParametricFilterType.PEAKING -> ParametricFilter.Peaking(
            id, enabled, frequencyHz, preservedGain, preservedQ
        )

        ParametricFilterType.LOW_SHELF -> ParametricFilter.LowShelf(
            id, enabled, frequencyHz, preservedGain, preservedSlope
        )

        ParametricFilterType.HIGH_SHELF -> ParametricFilter.HighShelf(
            id, enabled, frequencyHz, preservedGain, preservedSlope
        )

        ParametricFilterType.LOW_PASS -> ParametricFilter.LowPass(
            id, enabled, frequencyHz, preservedQ
        )

        ParametricFilterType.HIGH_PASS -> ParametricFilter.HighPass(
            id, enabled, frequencyHz, preservedQ
        )

        ParametricFilterType.NOTCH -> ParametricFilter.Notch(
            id, enabled, frequencyHz, preservedQ
        )

        ParametricFilterType.BAND_PASS -> ParametricFilter.BandPass(
            id, enabled, frequencyHz, preservedQ
        )
    }.normalized()
}

internal const val MIN_PARAMETRIC_FREQUENCY_HZ = 20.0
internal const val MAX_PARAMETRIC_FREQUENCY_HZ = 20_000.0
internal const val MIN_PARAMETRIC_GAIN_DB = -15.0
internal const val MAX_PARAMETRIC_GAIN_DB = 15.0
internal const val MIN_PARAMETRIC_Q = 0.10
internal const val MAX_PARAMETRIC_Q = 20.0
internal const val MIN_PARAMETRIC_SHELF_SLOPE = 0.10
internal const val MAX_PARAMETRIC_SHELF_SLOPE = 1.00
internal const val MAX_PARAMETRIC_FILTER_COUNT = 10

internal fun normalizeParametricFrequency(value: Double): Double {
    requireValidFrequency(value)
    return round(value * 10.0) / 10.0
}

internal fun normalizeParametricGain(value: Double): Double {
    requireValidGain(value)
    return round(value * 10.0) / 10.0
}

internal fun normalizeParametricQ(value: Double): Double {
    requireValidQ(value)
    return round(value * 100.0) / 100.0
}

internal fun normalizeParametricShelfSlope(value: Double): Double {
    requireValidShelfSlope(value)
    return round(value * 100.0) / 100.0
}

private fun validateIdentityAndFrequency(
    id: String,
    frequencyHz: Double
) {
    require(id.isNotBlank()) { "Parametric filter ID must not be blank" }
    requireValidFrequency(frequencyHz)
}

private fun requireValidFrequency(value: Double) {
    require(
        value.isFinite() &&
            value in MIN_PARAMETRIC_FREQUENCY_HZ..
                MAX_PARAMETRIC_FREQUENCY_HZ
    ) {
        "Parametric frequency must be finite and between " +
            "$MIN_PARAMETRIC_FREQUENCY_HZ and " +
            "$MAX_PARAMETRIC_FREQUENCY_HZ Hz"
    }
}

private fun requireValidGain(value: Double) {
    require(
        value.isFinite() &&
            value in MIN_PARAMETRIC_GAIN_DB..MAX_PARAMETRIC_GAIN_DB
    ) {
        "Parametric gain must be finite and between " +
            "$MIN_PARAMETRIC_GAIN_DB and $MAX_PARAMETRIC_GAIN_DB dB"
    }
}

private fun requireValidQ(value: Double) {
    require(
        value.isFinite() &&
            value in MIN_PARAMETRIC_Q..MAX_PARAMETRIC_Q
    ) {
        "Parametric Q must be finite and between " +
            "$MIN_PARAMETRIC_Q and $MAX_PARAMETRIC_Q"
    }
}

private fun requireValidShelfSlope(value: Double) {
    require(
        value.isFinite() &&
            value in MIN_PARAMETRIC_SHELF_SLOPE..
                MAX_PARAMETRIC_SHELF_SLOPE
    ) {
        "Parametric shelf slope must be finite and between " +
            "$MIN_PARAMETRIC_SHELF_SLOPE and " +
            "$MAX_PARAMETRIC_SHELF_SLOPE"
    }
}
