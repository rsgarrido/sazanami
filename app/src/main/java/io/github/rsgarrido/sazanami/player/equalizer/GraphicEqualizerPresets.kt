package io.github.rsgarrido.sazanami.player.equalizer

import java.util.UUID

internal data class BuiltInEqualizerPreset(
    val name: String,
    val preampDb: Double,
    val automaticHeadroomEnabled: Boolean,
    val bandGainsDb: List<Double>
)

data class EqualizerPresetMatch(
    val name: String,
    val userPresetId: String? = null
)

internal object GraphicEqualizerPresets {
    val builtIns: List<BuiltInEqualizerPreset> = listOf(
        builtIn("Flat", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
        builtIn("Bass Lift", 4.0, 3.5, 2.5, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
        builtIn("Treble Lift", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 2.5, 3.5, 4.0),
        builtIn("Vocal Focus", -2.0, -1.5, -0.5, 1.0, 2.0, 2.5, 2.0, 0.5, -1.0, -2.0),
        builtIn("Warm", 2.5, 2.0, 1.5, 1.0, 0.5, 0.0, -0.5, -1.0, -1.5, -1.5),
        builtIn("Reduced Bass", -4.0, -3.5, -2.5, -1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
    )

    val builtInNamesLowercase: Set<String> =
        builtIns.mapTo(linkedSetOf()) { preset ->
            preset.name.lowercase()
        }

    fun createUserPreset(
        name: String,
        state: EqualizerPreferencesState,
        id: String = UUID.randomUUID().toString()
    ): UserEqualizerPreset {
        val normalizedName = normalizePresetName(name)
        requireNameAvailable(
            name = normalizedName,
            userPresets = state.userPresets
        )
        return UserEqualizerPreset(
            id = id,
            name = normalizedName,
            preampDb = state.preampDb,
            automaticHeadroomEnabled =
                state.automaticHeadroomEnabled,
            bandGainsDb = state.bandGainsDb.toList()
        )
    }

    fun renameUserPreset(
        presetId: String,
        newName: String,
        userPresets: List<UserEqualizerPreset>
    ): List<UserEqualizerPreset> {
        require(userPresets.any { preset -> preset.id == presetId }) {
            "Unknown user equalizer preset ID: $presetId"
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
        userPresets: List<UserEqualizerPreset>,
        excludingPresetId: String? = null
    ) {
        val comparisonName = normalizePresetName(name).lowercase()
        require(comparisonName !in builtInNamesLowercase) {
            "Preset name conflicts with a built-in preset"
        }
        require(
            userPresets.none { preset ->
                preset.id != excludingPresetId &&
                    preset.name.lowercase() == comparisonName
            }
        ) {
            "A user preset with this name already exists"
        }
    }

    private fun builtIn(
        name: String,
        vararg bandGainsDb: Double
    ): BuiltInEqualizerPreset = BuiltInEqualizerPreset(
        name = name,
        preampDb = 0.0,
        automaticHeadroomEnabled = true,
        bandGainsDb = normalizeBandGains(bandGainsDb.toList())
    )
}

internal object EqualizerPresetMatcher {
    private const val MATCH_TOLERANCE_DB = 0.050_000_1

    fun match(
        state: EqualizerPreferencesState
    ): EqualizerPresetMatch? {
        GraphicEqualizerPresets.builtIns.firstOrNull { preset ->
            matches(
                state = state,
                preampDb = preset.preampDb,
                automaticHeadroomEnabled =
                    preset.automaticHeadroomEnabled,
                bandGainsDb = preset.bandGainsDb
            )
        }?.let { preset ->
            return EqualizerPresetMatch(name = preset.name)
        }

        return state.userPresets
            .sortedWith(
                compareBy<UserEqualizerPreset>(
                    { preset -> preset.name.lowercase() },
                    { preset -> preset.id }
                )
            )
            .firstOrNull { preset ->
                matches(
                    state = state,
                    preampDb = preset.preampDb,
                    automaticHeadroomEnabled =
                        preset.automaticHeadroomEnabled,
                    bandGainsDb = preset.bandGainsDb
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
        state: EqualizerPreferencesState,
        preampDb: Double,
        automaticHeadroomEnabled: Boolean,
        bandGainsDb: List<Double>
    ): Boolean {
        return state.automaticHeadroomEnabled ==
            automaticHeadroomEnabled &&
            close(state.preampDb, preampDb) &&
            state.bandGainsDb.zip(bandGainsDb)
                .all { (first, second) -> close(first, second) }
    }

    private fun close(first: Double, second: Double): Boolean =
        kotlin.math.abs(first - second) <= MATCH_TOLERANCE_DB
}
