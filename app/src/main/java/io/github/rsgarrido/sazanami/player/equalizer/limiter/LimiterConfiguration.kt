package io.github.rsgarrido.sazanami.player.equalizer.limiter

import kotlin.math.round

internal const val MIN_LIMITER_CEILING_DBFS = -3.0
internal const val MAX_LIMITER_CEILING_DBFS = 0.0
internal const val DEFAULT_LIMITER_CEILING_DBFS = -1.0
internal const val LIMITER_LOOKAHEAD_MILLISECONDS = 5.0
internal const val LIMITER_RELEASE_MILLISECONDS = 100.0

internal class LimiterConfiguration(
    enabled: Boolean = false,
    ceilingDbfs: Double = DEFAULT_LIMITER_CEILING_DBFS
) {
    val enabled: Boolean = enabled
    val ceilingDbfs: Double =
        normalizeLimiterCeilingDbfs(ceilingDbfs)

    init {
        require(ceilingDbfs.isFinite()) {
            "Limiter ceiling must be finite"
        }
        require(
            this.ceilingDbfs in
                MIN_LIMITER_CEILING_DBFS..MAX_LIMITER_CEILING_DBFS
        ) {
            "Limiter ceiling must be between -3.0 and 0.0 dBFS"
        }
    }

    fun withEnabled(value: Boolean): LimiterConfiguration =
        copy(enabled = value)

    fun withCeilingDbfs(value: Double): LimiterConfiguration =
        copy(ceilingDbfs = normalizeLimiterCeilingDbfs(value))

    fun copy(
        enabled: Boolean = this.enabled,
        ceilingDbfs: Double = this.ceilingDbfs
    ): LimiterConfiguration = LimiterConfiguration(
        enabled = enabled,
        ceilingDbfs = ceilingDbfs
    )

    override fun equals(other: Any?): Boolean {
        return other is LimiterConfiguration &&
            enabled == other.enabled &&
            ceilingDbfs == other.ceilingDbfs
    }

    override fun hashCode(): Int =
        31 * enabled.hashCode() + ceilingDbfs.hashCode()

    override fun toString(): String =
        "LimiterConfiguration(enabled=$enabled, ceilingDbfs=$ceilingDbfs)"
}

internal fun normalizeLimiterCeilingDbfs(value: Double): Double {
    require(value.isFinite()) {
        "Limiter ceiling must be finite"
    }
    return round(value * 10.0) / 10.0
}
