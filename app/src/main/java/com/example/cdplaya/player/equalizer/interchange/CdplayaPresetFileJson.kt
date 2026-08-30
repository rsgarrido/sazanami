package com.example.cdplaya.player.equalizer.interchange

import com.example.cdplaya.player.equalizer.parametric.ParametricEqualizerPreset
import com.example.cdplaya.player.equalizer.parametric.ParametricEqualizerState
import com.example.cdplaya.player.equalizer.parametric.ParametricFilter
import com.example.cdplaya.player.equalizer.parametric.ParametricFilterType
import com.example.cdplaya.player.equalizer.requireValidPresetName
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class CdplayaPresetFile(
    val name: String,
    val preampDb: Double,
    val automaticHeadroomEnabled: Boolean,
    val filters: List<ParametricFilter>
) {
    fun toUserPreset(
        presetId: String = UUID.randomUUID().toString()
    ): ParametricEqualizerPreset = ParametricEqualizerPreset(
        id = presetId,
        name = name,
        preampDb = preampDb,
        automaticHeadroomEnabled = automaticHeadroomEnabled,
        filters = filters
    )
}

object CdplayaPresetFileJson {
    const val KIND = "cdplaya-parametric-eq-preset"
    const val CURRENT_VERSION = 1
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(file: CdplayaPresetFile): String {
        validate(file)
        return json.encodeToString(file.toDto())
    }

    fun decode(text: String): CdplayaPresetFile {
        require(
            text.toByteArray(StandardCharsets.UTF_8).size <=
                EqualizerProfileLimits.MAX_INPUT_BYTES
        ) {
            "Native preset exceeds the 256 KiB limit."
        }
        val dto = try {
            json.decodeFromString<NativePresetDto>(
                text.removePrefix("\uFEFF")
            )
        } catch (error: SerializationException) {
            throw IllegalArgumentException(
                "Invalid Sazanami Parametric preset JSON.",
                error
            )
        }
        require(dto.kind == KIND) {
            "This JSON is not a Sazanami Parametric preset."
        }
        require(dto.version == CURRENT_VERSION) {
            "Unsupported Sazanami Parametric preset version ${dto.version}."
        }
        val file = CdplayaPresetFile(
            name = dto.name,
            preampDb = dto.preampDb,
            automaticHeadroomEnabled =
                dto.automaticHeadroomEnabled,
            filters = dto.filters.map { it.toDomain() }
        )
        validate(file)
        return file
    }

    fun fromPreset(
        preset: ParametricEqualizerPreset
    ): CdplayaPresetFile = CdplayaPresetFile(
        name = preset.name,
        preampDb = preset.preampDb,
        automaticHeadroomEnabled =
            preset.automaticHeadroomEnabled,
        filters = preset.filters
    )

    private fun validate(file: CdplayaPresetFile) {
        requireValidPresetName(file.name)
        ParametricEqualizerState(
            preampDb = file.preampDb,
            automaticHeadroomEnabled =
                file.automaticHeadroomEnabled,
            filters = file.filters
        )
    }
}

@Serializable
private data class NativePresetDto(
    val kind: String = CdplayaPresetFileJson.KIND,
    val version: Int =
        CdplayaPresetFileJson.CURRENT_VERSION,
    val name: String,
    val preampDb: Double,
    val automaticHeadroomEnabled: Boolean,
    val filters: List<NativeFilterDto>
)

@Serializable
private data class NativeFilterDto(
    val id: String,
    val enabled: Boolean,
    val type: String,
    val frequencyHz: Double,
    val gainDb: Double? = null,
    val q: Double? = null,
    val shelfSlope: Double? = null
) {
    fun toDomain(): ParametricFilter {
        val parsedType = runCatching {
            ParametricFilterType.valueOf(type)
        }.getOrNull() ?: throw IllegalArgumentException(
            "Unknown native preset filter type '$type'."
        )
        return when (parsedType) {
            ParametricFilterType.PEAKING -> ParametricFilter.Peaking(
                id, enabled, frequencyHz,
                requireNotNull(gainDb) { "Peaking gain is missing." },
                requireNotNull(q) { "Peaking Q is missing." }
            )
            ParametricFilterType.LOW_SHELF -> ParametricFilter.LowShelf(
                id, enabled, frequencyHz,
                requireNotNull(gainDb) { "Low-shelf gain is missing." },
                requireNotNull(shelfSlope) {
                    "Low-shelf slope is missing."
                }
            )
            ParametricFilterType.HIGH_SHELF -> ParametricFilter.HighShelf(
                id, enabled, frequencyHz,
                requireNotNull(gainDb) { "High-shelf gain is missing." },
                requireNotNull(shelfSlope) {
                    "High-shelf slope is missing."
                }
            )
            ParametricFilterType.LOW_PASS -> ParametricFilter.LowPass(
                id, enabled, frequencyHz,
                requireNotNull(q) { "Low-pass Q is missing." }
            )
            ParametricFilterType.HIGH_PASS -> ParametricFilter.HighPass(
                id, enabled, frequencyHz,
                requireNotNull(q) { "High-pass Q is missing." }
            )
            ParametricFilterType.NOTCH -> ParametricFilter.Notch(
                id, enabled, frequencyHz,
                requireNotNull(q) { "Notch Q is missing." }
            )
            ParametricFilterType.BAND_PASS -> ParametricFilter.BandPass(
                id, enabled, frequencyHz,
                requireNotNull(q) { "Band-pass Q is missing." }
            )
        }.also {
            validateApplicableFields(parsedType)
        }
    }

    private fun validateApplicableFields(type: ParametricFilterType) {
        when (type) {
            ParametricFilterType.PEAKING ->
                require(shelfSlope == null)
            ParametricFilterType.LOW_SHELF,
            ParametricFilterType.HIGH_SHELF ->
                require(q == null)
            else -> require(gainDb == null && shelfSlope == null)
        }
    }
}

private fun CdplayaPresetFile.toDto(): NativePresetDto =
    NativePresetDto(
        name = name,
        preampDb = preampDb,
        automaticHeadroomEnabled = automaticHeadroomEnabled,
        filters = filters.map { it.toNativeDto() }
    )

private fun ParametricFilter.toNativeDto(): NativeFilterDto = when (this) {
    is ParametricFilter.Peaking -> NativeFilterDto(
        id, enabled, type.name, frequencyHz, gainDb = gainDb, q = q
    )
    is ParametricFilter.LowShelf -> NativeFilterDto(
        id, enabled, type.name, frequencyHz,
        gainDb = gainDb, shelfSlope = slope
    )
    is ParametricFilter.HighShelf -> NativeFilterDto(
        id, enabled, type.name, frequencyHz,
        gainDb = gainDb, shelfSlope = slope
    )
    is ParametricFilter.LowPass -> NativeFilterDto(
        id, enabled, type.name, frequencyHz, q = q
    )
    is ParametricFilter.HighPass -> NativeFilterDto(
        id, enabled, type.name, frequencyHz, q = q
    )
    is ParametricFilter.Notch -> NativeFilterDto(
        id, enabled, type.name, frequencyHz, q = q
    )
    is ParametricFilter.BandPass -> NativeFilterDto(
        id, enabled, type.name, frequencyHz, q = q
    )
}
