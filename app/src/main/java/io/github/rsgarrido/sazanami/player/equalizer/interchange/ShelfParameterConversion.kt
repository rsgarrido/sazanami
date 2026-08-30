package io.github.rsgarrido.sazanami.player.equalizer.interchange

import io.github.rsgarrido.sazanami.player.equalizer.parametric.MAX_PARAMETRIC_Q
import io.github.rsgarrido.sazanami.player.equalizer.parametric.MAX_PARAMETRIC_SHELF_SLOPE
import io.github.rsgarrido.sazanami.player.equalizer.parametric.MIN_PARAMETRIC_Q
import io.github.rsgarrido.sazanami.player.equalizer.parametric.MIN_PARAMETRIC_SHELF_SLOPE
import kotlin.math.pow
import kotlin.math.sqrt

data class ShelfParameterConversionResult(
    val value: Double,
    val boundaryNormalized: Boolean = false
)

/**
 * Converts between the common shelf-Q representation and Sazanami's RBJ
 * shelf slope S by matching the two equivalent RBJ alpha expressions.
 */
object ShelfParameterConversion {
    const val SLOPE_ONE_ROUNDING_TOLERANCE = 0.01

    fun qToSlope(
        gainDb: Double,
        q: Double
    ): ShelfParameterConversionResult {
        require(gainDb.isFinite() && q.isFinite()) {
            "Shelf gain and Q must be finite."
        }
        require(q in MIN_PARAMETRIC_Q..MAX_PARAMETRIC_Q) {
            "Shelf Q is outside the supported range."
        }
        val a = 10.0.pow(gainDb / 40.0)
        val denominator = 1.0 +
            ((1.0 / (q * q)) - 2.0) / (a + 1.0 / a)
        require(denominator.isFinite() && denominator > 0.0) {
            "Shelf Q cannot be converted to a finite positive slope."
        }
        val raw = 1.0 / denominator
        require(raw.isFinite()) {
            "Shelf Q conversion produced a non-finite slope."
        }
        if (
            raw > MAX_PARAMETRIC_SHELF_SLOPE &&
            raw <= MAX_PARAMETRIC_SHELF_SLOPE +
            SLOPE_ONE_ROUNDING_TOLERANCE
        ) {
            return ShelfParameterConversionResult(
                value = MAX_PARAMETRIC_SHELF_SLOPE,
                boundaryNormalized = true
            )
        }
        require(
            raw in MIN_PARAMETRIC_SHELF_SLOPE..
                MAX_PARAMETRIC_SHELF_SLOPE
        ) {
            "Converted shelf slope is outside Sazanami's supported range."
        }
        return ShelfParameterConversionResult(raw)
    }

    fun slopeToQ(
        gainDb: Double,
        slope: Double
    ): ShelfParameterConversionResult {
        require(gainDb.isFinite() && slope.isFinite()) {
            "Shelf gain and slope must be finite."
        }
        require(
            slope in MIN_PARAMETRIC_SHELF_SLOPE..
                MAX_PARAMETRIC_SHELF_SLOPE
        ) {
            "Shelf slope is outside the supported range."
        }
        val a = 10.0.pow(gainDb / 40.0)
        val radicand =
            (a + 1.0 / a) * (1.0 / slope - 1.0) + 2.0
        require(radicand.isFinite() && radicand > 0.0) {
            "Shelf slope cannot be converted to a finite positive Q."
        }
        val q = 1.0 / sqrt(radicand)
        require(q.isFinite() && q in MIN_PARAMETRIC_Q..MAX_PARAMETRIC_Q) {
            "Converted shelf Q is outside the supported range."
        }
        return ShelfParameterConversionResult(q)
    }
}
