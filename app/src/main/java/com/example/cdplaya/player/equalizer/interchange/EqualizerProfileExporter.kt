package com.example.cdplaya.player.equalizer.interchange

import com.example.cdplaya.player.equalizer.parametric.ParametricEqualizerState
import com.example.cdplaya.player.equalizer.parametric.ParametricFilter
import java.math.BigDecimal
import java.math.RoundingMode

object EqualizerProfileExporter {
    fun exportText(state: ParametricEqualizerState): String = buildString {
        append("# Exported by Sazanami\n")
        append("# Sample-peak limiter and automatic headroom settings are not included.\n")
        append("# Sazanami automatic headroom: ")
        append(if (state.automaticHeadroomEnabled) "ON" else "OFF")
        append('\n')
        append("Preamp: ")
        append(format(state.preampDb, 1))
        append(" dB\n")
        state.filters.forEachIndexed { index, filter ->
            append("Filter ")
            append(index + 1)
            append(": ")
            append(if (filter.enabled) "ON " else "OFF ")
            append(filter.externalToken())
            append(" Fc ")
            append(format(filter.frequencyHz, 1))
            append(" Hz")
            when (filter) {
                is ParametricFilter.Peaking -> {
                    append(" Gain ")
                    append(format(filter.gainDb, 1))
                    append(" dB Q ")
                    append(format(filter.q, 2))
                }
                is ParametricFilter.LowShelf -> {
                    append(" Gain ")
                    append(format(filter.gainDb, 1))
                    append(" dB Q ")
                    append(
                        format(
                            ShelfParameterConversion.slopeToQ(
                                filter.gainDb,
                                filter.slope
                            ).value,
                            6
                        )
                    )
                }
                is ParametricFilter.HighShelf -> {
                    append(" Gain ")
                    append(format(filter.gainDb, 1))
                    append(" dB Q ")
                    append(
                        format(
                            ShelfParameterConversion.slopeToQ(
                                filter.gainDb,
                                filter.slope
                            ).value,
                            6
                        )
                    )
                }
                is ParametricFilter.LowPass -> appendQ(filter.q)
                is ParametricFilter.HighPass -> appendQ(filter.q)
                is ParametricFilter.Notch -> appendQ(filter.q)
                is ParametricFilter.BandPass -> appendQ(filter.q)
            }
            append('\n')
        }
    }

    private fun StringBuilder.appendQ(q: Double) {
        append(" Q ")
        append(format(q, 2))
    }

    private fun ParametricFilter.externalToken(): String = when (this) {
        is ParametricFilter.Peaking -> "PK"
        is ParametricFilter.LowShelf -> "LSC"
        is ParametricFilter.HighShelf -> "HSC"
        is ParametricFilter.LowPass -> "LPQ"
        is ParametricFilter.HighPass -> "HPQ"
        is ParametricFilter.BandPass -> "BP"
        is ParametricFilter.Notch -> "NO"
    }

    internal fun format(value: Double, maximumScale: Int): String {
        require(value.isFinite())
        return BigDecimal.valueOf(value)
            .setScale(maximumScale, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
            .let { if (it == "-0") "0" else it }
    }
}
