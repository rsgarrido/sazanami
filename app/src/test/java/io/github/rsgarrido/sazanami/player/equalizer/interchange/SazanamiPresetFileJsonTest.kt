package io.github.rsgarrido.sazanami.player.equalizer.interchange

import io.github.rsgarrido.sazanami.player.equalizer.parametric.ParametricFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SazanamiPresetFileJsonTest {
    @Test
    fun nativeRoundTripIsLosslessAndPreservesIdsAndOrder() {
        val file = SazanamiPresetFile(
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
        val encoded = SazanamiPresetFileJson.encode(file)
        val decoded = SazanamiPresetFileJson.decode(encoded)
        assertEquals(file, decoded)
        assertTrue(encoded.contains(
            "\"kind\": \"sazanami-parametric-eq-preset\""
        ))
        assertTrue(encoded.contains("\"version\": 1"))
    }

    @Test
    fun unknownFieldsAreIgnoredButWrongKindAndVersionAreRejected() {
        val base = SazanamiPresetFileJson.encode(
            SazanamiPresetFile(
                "Native", 0.0, true, emptyList()
            )
        )
        val futureField = base.replace(
            "\"name\":",
            "\"futureMetadata\": \"ignored\",\n  \"name\":"
        )
        assertEquals(
            "Native",
            SazanamiPresetFileJson.decode(futureField).name
        )
        listOf(
            base.replace(
                "sazanami-parametric-eq-preset",
                "sazanami-backup"
            ),
            base.replace("\"version\": 1", "\"version\": 2"),
            "{}",
            "not json"
        ).forEach { invalid ->
            assertTrue(
                runCatching {
                    SazanamiPresetFileJson.decode(invalid)
                }.isFailure
            )
        }
    }

    @Test
    fun rejectsDuplicateIdsTooManyFiltersAndUnknownTypes() {
        val duplicate = SazanamiPresetFile(
            "Duplicate", 0.0, true,
            listOf(
                ParametricFilter.Peaking("same", true, 100.0, 1.0, 1.0),
                ParametricFilter.Peaking("same", true, 200.0, 1.0, 1.0)
            )
        )
        assertTrue(runCatching {
            SazanamiPresetFileJson.encode(duplicate)
        }.isFailure)
        val tooMany = SazanamiPresetFile(
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
            SazanamiPresetFileJson.encode(tooMany)
        }.isFailure)
        val valid = SazanamiPresetFileJson.encode(
            SazanamiPresetFile("Valid", 0.0, true, emptyList())
        )
        val unknown = valid.replace(
            "\"filters\": []",
            "\"filters\": [{\"id\":\"x\",\"enabled\":true," +
                "\"type\":\"CUSTOM\",\"frequencyHz\":1000,\"q\":1}]"
        )
        assertTrue(runCatching {
            SazanamiPresetFileJson.decode(unknown)
        }.isFailure)
    }

    @Test
    fun rejectsMissingFieldsNonFiniteValuesAndApplicationBackups() {
        val valid = SazanamiPresetFileJson.encode(
            SazanamiPresetFile(
                "Validation",
                0.0,
                true,
                emptyList()
            )
        )
        val invalidFiles = listOf(
            valid.replace("\"preampDb\": 0.0,", ""),
            valid.replace("\"preampDb\": 0.0", "\"preampDb\": NaN"),
            """{"kind":"sazanami-app-backup","version":6}"""
        )
        invalidFiles.forEach { invalid ->
            assertTrue(
                runCatching {
                    SazanamiPresetFileJson.decode(invalid)
                }.isFailure
            )
        }
    }
}
