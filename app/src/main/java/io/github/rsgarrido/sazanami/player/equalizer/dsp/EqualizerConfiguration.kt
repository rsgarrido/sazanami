package io.github.rsgarrido.sazanami.player.equalizer.dsp

import java.util.Collections

/**
 * Immutable equalizer configuration in processing order.
 */
internal class EqualizerConfiguration(
    val enabled: Boolean,
    val preampDb: Double,
    filters: List<EqualizerFilterSpec>
) {
    val filters: List<EqualizerFilterSpec> =
        Collections.unmodifiableList(filters.toList())

    init {
        require(preampDb.isFinite()) {
            "preampDb must be finite"
        }
    }

    val isEffectivelyFlat: Boolean
        get() {
            if (!enabled) return true
            if (!isEffectivelyZeroDb(preampDb)) return false

            return filters.none { filter -> filter.hasAudibleEffect }
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EqualizerConfiguration) return false

        return enabled == other.enabled &&
            preampDb.toBits() == other.preampDb.toBits() &&
            filters == other.filters
    }

    override fun hashCode(): Int {
        var result = enabled.hashCode()
        result = 31 * result + preampDb.hashCode()
        result = 31 * result + filters.hashCode()
        return result
    }

    override fun toString(): String {
        return "EqualizerConfiguration(" +
            "enabled=$enabled, " +
            "preampDb=$preampDb, " +
            "filters=$filters)"
    }
}
