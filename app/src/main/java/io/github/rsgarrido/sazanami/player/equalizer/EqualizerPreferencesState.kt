package io.github.rsgarrido.sazanami.player.equalizer

import io.github.rsgarrido.sazanami.player.equalizer.dsp.GraphicEqualizerDefaults
import io.github.rsgarrido.sazanami.player.equalizer.limiter.DEFAULT_LIMITER_CEILING_DBFS
import io.github.rsgarrido.sazanami.player.equalizer.limiter.LimiterConfiguration
import io.github.rsgarrido.sazanami.player.equalizer.limiter.normalizeLimiterCeilingDbfs
import io.github.rsgarrido.sazanami.player.equalizer.parametric.ParametricEqualizerState
import java.util.Collections
import kotlin.math.round

class EqualizerPreferencesState(
    enabled: Boolean = false,
    preampDb: Double = 0.0,
    automaticHeadroomEnabled: Boolean = true,
    bandGainsDb: List<Double> =
        List(GraphicEqualizerDefaults.BAND_COUNT) { 0.0 },
    userPresets: List<UserEqualizerPreset> = emptyList(),
    mode: EqualizerMode = EqualizerMode.GRAPHIC,
    parametricState: ParametricEqualizerState =
        ParametricEqualizerState(),
    limiterEnabled: Boolean = false,
    limiterCeilingDbfs: Double = DEFAULT_LIMITER_CEILING_DBFS
) {
    val enabled: Boolean = enabled
    val preampDb: Double =
        normalizeEqualizerDb(preampDb).also(::requireValidPreamp)
    val automaticHeadroomEnabled: Boolean =
        automaticHeadroomEnabled
    val bandGainsDb: List<Double> =
        normalizeBandGains(bandGainsDb)
    val userPresets: List<UserEqualizerPreset> =
        Collections.unmodifiableList(userPresets.toList())
    val mode: EqualizerMode = mode
    val parametricState: ParametricEqualizerState =
        parametricState.copy(
            filters = parametricState.filters.toList(),
            userPresets = parametricState.userPresets.toList()
        )
    val limiterEnabled: Boolean = limiterEnabled
    val limiterCeilingDbfs: Double =
        LimiterConfiguration(
            enabled = limiterEnabled,
            ceilingDbfs = limiterCeilingDbfs
        ).ceilingDbfs

    init {
        require(
            this.userPresets.map { preset -> preset.id }
                .distinct().size == this.userPresets.size
        ) {
            "User equalizer preset IDs must be unique"
        }
        require(
            this.userPresets.map { preset -> preset.name.lowercase() }
                .distinct()
                .size == this.userPresets.size
        ) {
            "User equalizer preset names must be unique"
        }
        require(
            this.userPresets.none { preset ->
                preset.name.lowercase() in
                    GraphicEqualizerPresets.builtInNamesLowercase
            }
        ) {
            "User equalizer preset names cannot duplicate built-ins"
        }
    }

    fun withEnabled(value: Boolean): EqualizerPreferencesState =
        copy(enabled = value)

    fun withPreampDb(value: Double): EqualizerPreferencesState =
        copy(preampDb = normalizeEqualizerDb(value).also(::requireValidPreamp))

    fun withAutomaticHeadroomEnabled(
        value: Boolean
    ): EqualizerPreferencesState =
        copy(automaticHeadroomEnabled = value)

    fun withLimiterEnabled(value: Boolean): EqualizerPreferencesState =
        copy(limiterEnabled = value)

    fun withMode(value: EqualizerMode): EqualizerPreferencesState =
        copy(mode = value)

    fun withParametricState(
        value: ParametricEqualizerState
    ): EqualizerPreferencesState =
        copy(parametricState = value)

    fun withLimiterCeilingDbfs(
        value: Double
    ): EqualizerPreferencesState =
        copy(
            limiterCeilingDbfs =
                normalizeLimiterCeilingDbfs(value)
        )

    fun withBandGainDb(
        index: Int,
        value: Double
    ): EqualizerPreferencesState {
        require(index in bandGainsDb.indices) {
            "Graphic equalizer band index is out of range: $index"
        }
        val normalized = normalizeEqualizerDb(value)
            .also(::requireValidBandGain)
        if (bandGainsDb[index] == normalized) return this
        return copy(
            bandGainsDb = bandGainsDb.toMutableList().also { gains ->
                gains[index] = normalized
            }.toList()
        )
    }

    fun withCurve(
        preampDb: Double,
        automaticHeadroomEnabled: Boolean,
        bandGainsDb: List<Double>
    ): EqualizerPreferencesState {
        return copy(
            preampDb = normalizeEqualizerDb(preampDb)
                .also(::requireValidPreamp),
            automaticHeadroomEnabled = automaticHeadroomEnabled,
            bandGainsDb = normalizeBandGains(bandGainsDb)
        )
    }

    fun flatCurve(): EqualizerPreferencesState = withCurve(
        preampDb = 0.0,
        automaticHeadroomEnabled = true,
        bandGainsDb =
            List(GraphicEqualizerDefaults.BAND_COUNT) { 0.0 }
    )

    fun copy(
        enabled: Boolean = this.enabled,
        preampDb: Double = this.preampDb,
        automaticHeadroomEnabled: Boolean =
            this.automaticHeadroomEnabled,
        bandGainsDb: List<Double> = this.bandGainsDb,
        userPresets: List<UserEqualizerPreset> =
            this.userPresets,
        mode: EqualizerMode = this.mode,
        parametricState: ParametricEqualizerState =
            this.parametricState,
        limiterEnabled: Boolean = this.limiterEnabled,
        limiterCeilingDbfs: Double = this.limiterCeilingDbfs
    ): EqualizerPreferencesState = EqualizerPreferencesState(
        enabled = enabled,
        preampDb = preampDb,
        automaticHeadroomEnabled =
            automaticHeadroomEnabled,
        bandGainsDb = bandGainsDb,
        userPresets = userPresets,
        mode = mode,
        parametricState = parametricState,
        limiterEnabled = limiterEnabled,
        limiterCeilingDbfs = limiterCeilingDbfs
    )

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is EqualizerPreferencesState &&
            enabled == other.enabled &&
            preampDb.toBits() == other.preampDb.toBits() &&
            automaticHeadroomEnabled ==
                other.automaticHeadroomEnabled &&
            bandGainsDb == other.bandGainsDb &&
            userPresets == other.userPresets &&
            mode == other.mode &&
            parametricState == other.parametricState &&
            limiterEnabled == other.limiterEnabled &&
            limiterCeilingDbfs.toBits() ==
                other.limiterCeilingDbfs.toBits()

    override fun hashCode(): Int {
        var result = enabled.hashCode()
        result = 31 * result + preampDb.hashCode()
        result = 31 * result +
            automaticHeadroomEnabled.hashCode()
        result = 31 * result + bandGainsDb.hashCode()
        result = 31 * result + userPresets.hashCode()
        result = 31 * result + mode.hashCode()
        result = 31 * result + parametricState.hashCode()
        result = 31 * result + limiterEnabled.hashCode()
        result = 31 * result + limiterCeilingDbfs.hashCode()
        return result
    }

    override fun toString(): String =
        "EqualizerPreferencesState(" +
            "enabled=$enabled, " +
            "preampDb=$preampDb, " +
            "automaticHeadroomEnabled=" +
            "$automaticHeadroomEnabled, " +
            "bandGainsDb=$bandGainsDb, " +
            "userPresets=$userPresets, " +
            "mode=$mode, " +
            "parametricState=$parametricState, " +
            "limiterEnabled=$limiterEnabled, " +
            "limiterCeilingDbfs=$limiterCeilingDbfs)"
}

class UserEqualizerPreset(
    id: String,
    name: String,
    preampDb: Double,
    automaticHeadroomEnabled: Boolean,
    bandGainsDb: List<Double>
) {
    val id: String = id
    val name: String = normalizePresetName(name)
    val preampDb: Double =
        normalizeEqualizerDb(preampDb).also(::requireValidPreamp)
    val automaticHeadroomEnabled: Boolean =
        automaticHeadroomEnabled
    val bandGainsDb: List<Double> =
        normalizeBandGains(bandGainsDb)

    init {
        require(this.id.isNotBlank()) {
            "User equalizer preset ID must not be blank"
        }
    }

    fun renamed(newName: String): UserEqualizerPreset =
        copy(name = normalizePresetName(newName))

    fun copy(
        id: String = this.id,
        name: String = this.name,
        preampDb: Double = this.preampDb,
        automaticHeadroomEnabled: Boolean =
            this.automaticHeadroomEnabled,
        bandGainsDb: List<Double> = this.bandGainsDb
    ): UserEqualizerPreset = UserEqualizerPreset(
        id = id,
        name = name,
        preampDb = preampDb,
        automaticHeadroomEnabled =
            automaticHeadroomEnabled,
        bandGainsDb = bandGainsDb
    )

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is UserEqualizerPreset &&
            id == other.id &&
            name == other.name &&
            preampDb.toBits() == other.preampDb.toBits() &&
            automaticHeadroomEnabled ==
                other.automaticHeadroomEnabled &&
            bandGainsDb == other.bandGainsDb

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + preampDb.hashCode()
        result = 31 * result +
            automaticHeadroomEnabled.hashCode()
        result = 31 * result + bandGainsDb.hashCode()
        return result
    }

    override fun toString(): String =
        "UserEqualizerPreset(" +
            "id=$id, name=$name, preampDb=$preampDb, " +
            "automaticHeadroomEnabled=" +
            "$automaticHeadroomEnabled, " +
            "bandGainsDb=$bandGainsDb)"
}

internal const val MIN_EQUALIZER_BAND_DB = -12.0
internal const val MAX_EQUALIZER_BAND_DB = 12.0
internal const val MIN_EQUALIZER_PREAMP_DB = -15.0
internal const val MAX_EQUALIZER_PREAMP_DB = 6.0
internal const val MAX_EQUALIZER_PRESET_NAME_LENGTH = 40

internal fun normalizeEqualizerDb(value: Double): Double {
    require(value.isFinite()) {
        "Equalizer decibel value must be finite"
    }
    return round(value * 10.0) / 10.0
}

internal fun normalizeBandGains(values: List<Double>): List<Double> {
    require(values.size == GraphicEqualizerDefaults.BAND_COUNT) {
        "Graphic equalizer requires exactly " +
            "${GraphicEqualizerDefaults.BAND_COUNT} band gains"
    }
    return Collections.unmodifiableList(
        values.map { value ->
            normalizeEqualizerDb(value)
                .also(::requireValidBandGain)
        }
    )
}

internal fun normalizePresetName(name: String): String =
    name.trim().also(::requireValidPresetName)

internal fun requireValidBandGain(value: Double) {
    require(
        value.isFinite() &&
            value in MIN_EQUALIZER_BAND_DB..MAX_EQUALIZER_BAND_DB
    ) {
        "Equalizer band gain must be finite and between " +
            "$MIN_EQUALIZER_BAND_DB and $MAX_EQUALIZER_BAND_DB dB"
    }
}

internal fun requireValidPreamp(value: Double) {
    require(
        value.isFinite() &&
            value in MIN_EQUALIZER_PREAMP_DB..MAX_EQUALIZER_PREAMP_DB
    ) {
        "Equalizer preamp must be finite and between " +
            "$MIN_EQUALIZER_PREAMP_DB and $MAX_EQUALIZER_PREAMP_DB dB"
    }
}

internal fun requireValidPresetName(name: String) {
    require(name == name.trim() && name.isNotBlank()) {
        "Preset name must be trimmed and non-blank"
    }
    require(name.length <= MAX_EQUALIZER_PRESET_NAME_LENGTH) {
        "Preset name must be at most " +
            "$MAX_EQUALIZER_PRESET_NAME_LENGTH characters"
    }
}
