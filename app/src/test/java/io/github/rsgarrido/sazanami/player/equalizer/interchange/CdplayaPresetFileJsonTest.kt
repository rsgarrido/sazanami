package io.github.rsgarrido.sazanami.player.equalizer.interchange

import io.github.rsgarrido.sazanami.player.equalizer.parametric.ParametricFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CdplayaPresetFileJsonTest {
    @Test
    fun nativeRoundTripIsLosslessAndPreservesIdsAndOrder() {
        val file = CdplayaPresetFile(
            name = "My Headphones",
            preampDb = -6.2,
            automaticHeadroomEnabled = false,
            filters = listOf(
                ParametricFilter.LowShelf(
                    "shelf", true, 105.0, 6.6, 0.98
                ),
                ParametricFilter.Peaking(
                    "peak", false, 1_000.0, -3.5, 1.4
                ),
                ParametricFilter.BandPass(
                    "band", true, 2_000.0, 1.0
                )
            )
        )
        val encoded = CdplayaPresetFileJson.encode(file)
        val decoded = CdplayaPresetFileJson.decode(encoded)
        assertEquals(file, decoded)
        assertTrue(encoded.contains(
            "\"kind\": \"cdplaya-parametric-eq-preset\""
        ))
        assertTrue(encoded.contains("\"version\": 1"))
    }

    @Test
    fun unknownFieldsAreIgnoredButWrongKindAndVersionAreRejected() {
        val base = CdplayaPresetFileJson.encode(
            CdplayaPresetFile(
                "Native", 0.0, true, emptyList()
            )
        )
        val futureField = base.replace(
            "\"name\":",
            "\"futureMetadata\": \"ignored\",\n  \"name\":"
        )
        assertEquals(
            "Native",
            CdplayaPresetFileJson.decode(futureField).name
        )
        listOf(
            base.replace(
                "cdplaya-parametric-eq-preset",
                "cdplaya-backup"
            ),
            base.replace("\"version\": 1", "\"version\": 2"),
            "{}",
            "not json"
        ).forEach { invalid ->
            assertTrue(
                runCatching {
                    CdplayaPresetFileJson.decode(invalid)
                }.isFailure
            )
        }
    }

    @Test
    fun rejectsDuplicateIdsTooManyFiltersAndUnknownTypes() {
        val duplicate = CdplayaPresetFile(
            "Duplicate", 0.0, true,
            listOf(
                ParametricFilter.Peaking("same", true, 100.0, 1.0, 1.0),
                ParametricFilter.Peaking("same", true, 200.0, 1.0, 1.0)
            )
        )
        assertTrue(runCatching {
            CdplayaPresetFileJson.encode(duplicate)
        }.isFailure)
        val tooMany = CdplayaPresetFile(
            "Too Many",
            0.0,
            true,
            (1..11).map { index ->
                ParametricFilter.Peaking(
                    "filter-$index",
                    true,
                    100.0 + index,
                    1.0,
                    1.0
                )
            }
        )
        assertTrue(runCatching {
            CdplayaPresetFileJson.encode(tooMany)
        }.isFailure)
        val valid = CdplayaPresetFileJson.encode(
            CdplayaPresetFile("Valid", 0.0, true, emptyList())
        )
        val unknown = valid.replace(
            "\"filters\": []",
            "\"filters\": [{\"id\":\"x\",\"enabled\":true," +
                "\"type\":\"CUSTOM\",\"frequencyHz\":1000,\"q\":1}]"
        )
        assertTrue(runCatching {
            CdplayaPresetFileJson.decode(unknown)
        }.isFailure)
    }

    @Test
    fun rejectsMissingFieldsNonFiniteValuesAndApplicationBackups() {
        val valid = CdplayaPresetFileJson.encode(
            CdplayaPresetFile(
                "Validation",
                0.0,
                true,
                emptyList()
            )
        )
        val invalidFiles = listOf(
            valid.replace("\"preampDb\": 0.0,", ""),
            valid.replace("\"preampDb\": 0.0", "\"preampDb\": NaN"),
            """{"kind":"cdplaya-app-backup","version":6}"""
        )
        invalidFiles.forEach { invalid ->
            assertTrue(
                runCatching {
                    CdplayaPresetFileJson.decode(invalid)
                }.isFailure
            )
        }
    }
}
