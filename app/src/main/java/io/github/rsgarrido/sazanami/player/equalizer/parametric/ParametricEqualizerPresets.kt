package io.github.rsgarrido.sazanami.player.equalizer.parametric

import io.github.rsgarrido.sazanami.player.equalizer.EqualizerPresetMatch
import io.github.rsgarrido.sazanami.player.equalizer.normalizePresetName
import java.util.UUID
import kotlin.math.abs

internal object ParametricEqualizerPresets {
    const val FLAT_NAME = "Flat"

    fun createUserPreset(
        name: String,
        state: ParametricEqualizerState,
        id: String = UUID.randomUUID().toString()
    ): ParametricEqualizerPreset {
        val normalizedName = normalizePresetName(name)
        requireNameAvailable(
            name = normalizedName,
            userPresets = state.userPresets
        )
        return ParametricEqualizerPreset(
            id = id,
            name = normalizedName,
            preampDb = state.preampDb,
            automaticHeadroomEnabled =
                state.automaticHeadroomEnabled,
            filters = state.filters
        )
    }

    fun renameUserPreset(
        presetId: String,
        newName: String,
        userPresets: List<ParametricEqualizerPreset>
    ): List<ParametricEqualizerPreset> {
        require(userPresets.any { preset -> preset.id == presetId }) {
            "Unknown parametric preset ID: $presetId"
        }
        val normalizedName = normalizePresetName(newName)
        requireNameAvailable(
            name = normalizedName,
            userPresets = userPresets,
            excludingPresetId = presetId
        )
        return userPresets.map { preset ->
            if (preset.id == presetId) {
                preset.renamed(normalizedName)
            } else {
                preset
            }
        }
    }

    fun requireNameAvailable(
        name: String,
        userPresets: List<ParametricEqualizerPreset>,
        excludingPresetId: String? = null
    ) {
        val comparison = normalizePresetName(name).lowercase()
        require(comparison != FLAT_NAME.lowercase()) {
            "Parametric user preset name conflicts with Flat"
        }
        require(
            userPresets.none { preset ->
                preset.id != excludingPresetId &&
                    preset.name.lowercase() == comparison
            }
        ) {
            "A parametric preset with this name already exists"
        }
    }
}

internal object ParametricEqualizerPresetMatcher {
    private const val DB_TOLERANCE = 0.050_000_1
    private const val FREQUENCY_TOLERANCE_HZ = 0.050_000_1
    private const val TWO_DECIMAL_TOLERANCE = 0.005_000_1

    fun match(
        state: ParametricEqualizerState
    ): EqualizerPresetMatch? {
        if (
            matches(
                state = state,
                preampDb = 0.0,
                automaticHeadroomEnabled = true,
                filters = emptyList()
            )
        ) {
            return EqualizerPresetMatch(
                name = ParametricEqualizerPresets.FLAT_NAME
            )
        }
        return state.userPresets
            .sortedWith(
                compareBy<ParametricEqualizerPreset>(
                    { preset -> preset.name.lowercase() },
                    ParametricEqualizerPreset::id
                )
            )
            .firstOrNull { preset ->
                matches(
                    state = state,
                    preampDb = preset.preampDb,
                    automaticHeadroomEnabled =
                        preset.automaticHeadroomEnabled,
                    filters = preset.filters
                )
            }
            ?.let { preset ->
                EqualizerPresetMatch(
                    name = preset.name,
                    userPresetId = preset.id
                )
            }
    }

    private fun matches(
        state: ParametricEqualizerState,
        preampDb: Double,
        automaticHeadroomEnabled: Boolean,
        filters: List<ParametricFilter>
    ): Boolean {
        if (
            state.automaticHeadroomEnabled !=
            automaticHeadroomEnabled ||
            !close(state.preampDb, preampDb, DB_TOLERANCE) ||
            state.filters.size != filters.size
        ) {
            return false
        }
        return state.filters.zip(filters).all { (first, second) ->
            filtersMatch(first, second)
        }
    }

    private fun filtersMatch(
        first: ParametricFilter,
        second: ParametricFilter
    ): Boolean {
        return first.type == second.type &&
            first.enabled == second.enabled &&
            close(
                first.frequencyHz,
                second.frequencyHz,
                FREQUENCY_TOLERANCE_HZ
            ) &&
            nullableClose(
                first.gainDbOrNull,
                second.gainDbOrNull,
                DB_TOLERANCE
            ) &&
            nullableClose(
                first.qOrNull,
                second.qOrNull,
                TWO_DECIMAL_TOLERANCE
            ) &&
            nullableClose(
                first.slopeOrNull,
                second.slopeOrNull,
                TWO_DECIMAL_TOLERANCE
            )
    }

    private fun nullableClose(
        first: Double?,
        second: Double?,
        tolerance: Double
    ): Boolean {
        if (first == null || second == null) return first == second
        return close(first, second, tolerance)
    }

    private fun close(
        first: Double,
        second: Double,
        tolerance: Double
    ): Boolean = abs(first - second) <= tolerance
}
