package io.github.rsgarrido.sazanami.player.equalizer.parametric

import io.github.rsgarrido.sazanami.player.equalizer.normalizeEqualizerDb
import io.github.rsgarrido.sazanami.player.equalizer.normalizePresetName
import io.github.rsgarrido.sazanami.player.equalizer.requireValidPreamp
import java.util.Collections

class ParametricEqualizerState(
    preampDb: Double = 0.0,
    automaticHeadroomEnabled: Boolean = true,
    filters: List<ParametricFilter> = emptyList(),
    userPresets: List<ParametricEqualizerPreset> = emptyList()
) {
    val preampDb: Double =
        normalizeEqualizerDb(preampDb).also(::requireValidPreamp)
    val automaticHeadroomEnabled: Boolean =
        automaticHeadroomEnabled
    val filters: List<ParametricFilter> =
        normalizeAndValidateFilters(filters)
    val userPresets: List<ParametricEqualizerPreset> =
        Collections.unmodifiableList(userPresets.toList())

    init {
        require(
            this.userPresets.map { preset -> preset.id }
                .distinct().size == this.userPresets.size
        ) {
            "Parametric preset IDs must be unique"
        }
        require(
            this.userPresets.map { preset -> preset.name.lowercase() }
                .distinct().size == this.userPresets.size
        ) {
            "Parametric preset names must be unique"
        }
        require(
            this.userPresets.none { preset ->
                preset.name.equals("Flat", ignoreCase = true)
            }
        ) {
            "Parametric user preset name cannot duplicate Flat"
        }
    }

    val isEffectivelyFlat: Boolean
        get() = preampDb == 0.0 &&
            filters.none { filter -> filter.hasAudibleEffect }

    fun withPreampDb(value: Double): ParametricEqualizerState =
        copy(preampDb = value)

    fun withAutomaticHeadroomEnabled(
        value: Boolean
    ): ParametricEqualizerState =
        copy(automaticHeadroomEnabled = value)

    fun withFilter(filter: ParametricFilter): ParametricEqualizerState {
        val index = filters.indexOfFirst { candidate ->
            candidate.id == filter.id
        }
        require(index >= 0) {
            "Unknown parametric filter ID: ${filter.id}"
        }
        return copy(
            filters = filters.toMutableList().also { updated ->
                updated[index] = filter
            }
        )
    }

    fun addFilter(
        filter: ParametricFilter =
            ParametricFilterFactory.default()
    ): ParametricEqualizerState {
        require(filters.size < MAX_PARAMETRIC_FILTER_COUNT) {
            "Parametric equalizer supports at most " +
                "$MAX_PARAMETRIC_FILTER_COUNT filters"
        }
        require(filters.none { existing -> existing.id == filter.id }) {
            "Parametric filter IDs must be unique"
        }
        return copy(filters = filters + filter)
    }

    fun removeFilter(filterId: String): ParametricEqualizerState {
        require(filters.any { filter -> filter.id == filterId }) {
            "Unknown parametric filter ID: $filterId"
        }
        return copy(
            filters = filters.filterNot { filter ->
                filter.id == filterId
            }
        )
    }

    fun moveFilter(
        filterId: String,
        destinationIndex: Int
    ): ParametricEqualizerState {
        require(destinationIndex in filters.indices) {
            "Parametric filter destination is out of range"
        }
        val sourceIndex = filters.indexOfFirst { filter ->
            filter.id == filterId
        }
        require(sourceIndex >= 0) {
            "Unknown parametric filter ID: $filterId"
        }
        if (sourceIndex == destinationIndex) return this
        val updated = filters.toMutableList()
        val moved = updated.removeAt(sourceIndex)
        updated.add(destinationIndex, moved)
        return copy(filters = updated)
    }

    fun applyPreset(
        preset: ParametricEqualizerPreset
    ): ParametricEqualizerState = copy(
        preampDb = preset.preampDb,
        automaticHeadroomEnabled =
            preset.automaticHeadroomEnabled,
        filters = preset.filters.map { filter -> filter.normalized() }
    )

    fun flatCurve(): ParametricEqualizerState = copy(
        preampDb = 0.0,
        automaticHeadroomEnabled = true,
        filters = emptyList()
    )

    fun copy(
        preampDb: Double = this.preampDb,
        automaticHeadroomEnabled: Boolean =
            this.automaticHeadroomEnabled,
        filters: List<ParametricFilter> = this.filters,
        userPresets: List<ParametricEqualizerPreset> =
            this.userPresets
    ): ParametricEqualizerState = ParametricEqualizerState(
        preampDb = preampDb,
        automaticHeadroomEnabled =
            automaticHeadroomEnabled,
        filters = filters,
        userPresets = userPresets
    )

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is ParametricEqualizerState &&
            preampDb.toBits() == other.preampDb.toBits() &&
            automaticHeadroomEnabled ==
                other.automaticHeadroomEnabled &&
            filters == other.filters &&
            userPresets == other.userPresets

    override fun hashCode(): Int {
        var result = preampDb.hashCode()
        result = 31 * result +
            automaticHeadroomEnabled.hashCode()
        result = 31 * result + filters.hashCode()
        result = 31 * result + userPresets.hashCode()
        return result
    }

    override fun toString(): String =
        "ParametricEqualizerState(" +
            "preampDb=$preampDb, " +
            "automaticHeadroomEnabled=" +
            "$automaticHeadroomEnabled, " +
            "filters=$filters, userPresets=$userPresets)"
}

class ParametricEqualizerPreset(
    id: String,
    name: String,
    preampDb: Double,
    automaticHeadroomEnabled: Boolean,
    filters: List<ParametricFilter>
) {
    val id: String = id
    val name: String = normalizePresetName(name)
    val preampDb: Double =
        normalizeEqualizerDb(preampDb).also(::requireValidPreamp)
    val automaticHeadroomEnabled: Boolean =
        automaticHeadroomEnabled
    val filters: List<ParametricFilter> =
        normalizeAndValidateFilters(filters)

    init {
        require(this.id.isNotBlank()) {
            "Parametric preset ID must not be blank"
        }
        require(!this.name.equals("Flat", ignoreCase = true)) {
            "Parametric user preset name cannot duplicate Flat"
        }
    }

    fun renamed(newName: String): ParametricEqualizerPreset =
        copy(name = newName)

    fun copy(
        id: String = this.id,
        name: String = this.name,
        preampDb: Double = this.preampDb,
        automaticHeadroomEnabled: Boolean =
            this.automaticHeadroomEnabled,
        filters: List<ParametricFilter> = this.filters
    ): ParametricEqualizerPreset = ParametricEqualizerPreset(
        id = id,
        name = name,
        preampDb = preampDb,
        automaticHeadroomEnabled =
            automaticHeadroomEnabled,
        filters = filters
    )

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is ParametricEqualizerPreset &&
            id == other.id &&
            name == other.name &&
            preampDb.toBits() == other.preampDb.toBits() &&
            automaticHeadroomEnabled ==
                other.automaticHeadroomEnabled &&
            filters == other.filters

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + preampDb.hashCode()
        result = 31 * result +
            automaticHeadroomEnabled.hashCode()
        result = 31 * result + filters.hashCode()
        return result
    }
}

private fun normalizeAndValidateFilters(
    filters: List<ParametricFilter>
): List<ParametricFilter> {
    require(filters.size <= MAX_PARAMETRIC_FILTER_COUNT) {
        "Parametric equalizer supports at most " +
            "$MAX_PARAMETRIC_FILTER_COUNT filters"
    }
    val normalized = filters.map { filter -> filter.normalized() }
    require(
        normalized.map { filter -> filter.id }
            .distinct().size == normalized.size
    ) {
        "Parametric filter IDs must be unique"
    }
    return Collections.unmodifiableList(normalized)
}
