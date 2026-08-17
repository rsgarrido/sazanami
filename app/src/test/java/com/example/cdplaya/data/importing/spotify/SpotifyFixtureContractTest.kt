package com.example.cdplaya.data.importing.spotify

import com.example.cdplaya.data.importing.ImportFileFailureReason
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyFixtureContractTest {
    private val parser = SpotifyExtendedStreamingParser(
        Clock.fixed(Instant.parse("2035-01-01T00:00:00Z"), ZoneOffset.UTC)
    )

    @Test
    fun everyStaticFixtureHasItsDocumentedContract() {
        contracts.forEach { contract ->
            val bytes = resource(contract.name).readBytes()
            if (contract.malformed) {
                val result = parser.parse({ bytes.inputStream() }) {
                    SpotifyParseControl.CONTINUE
                }
                assertTrue(contract.purpose, result is SpotifyFileParseResult.Failed)
                assertEquals(
                    ImportFileFailureReason.MALFORMED_JSON,
                    (result as SpotifyFileParseResult.Failed).reason
                )
            } else {
                Json.parseToJsonElement(bytes.decodeToString())
            }
        }
    }

    @Test
    fun overlappingAndReexportFixturesRemainIndependentSourceEvidence() {
        assertEquals(2L, emitted("spotify_extended_overlap_a.json"))
        assertEquals(2L, emitted("spotify_extended_overlap_b.json"))
        assertEquals(2L, emitted("spotify_extended_reexport_initial.json"))
        assertEquals(3L, emitted("spotify_extended_reexport_later.json"))
    }

    @Test
    fun privacyVariantsParseButCannotEnterTheNormalizedModelShape() {
        assertEquals(1L, emitted("spotify_extended_full_fields_current.json"))
        assertEquals(1L, emitted("spotify_extended_legacy_decrypted_fields.json"))
        val fields = com.example.cdplaya.data.importing.ImportedListeningRecord::class.java
            .declaredFields.map { it.name.lowercase() }
        listOf(
            "ipaddr", "country", "platform", "useragent", "incognitomode",
            "sourcefilename", "filesystempath", "rawjson"
        ).forEach { forbidden -> assertTrue(forbidden, forbidden !in fields) }
    }

    private fun emitted(name: String): Long {
        val result = parser.parse({ resource(name) }) { SpotifyParseControl.CONTINUE }
        assertTrue(result is SpotifyFileParseResult.Completed)
        return result.recordsEmitted
    }

    private fun resource(name: String) = requireNotNull(
        javaClass.getResourceAsStream("/importing/spotify/$name")
    )

    private data class Contract(
        val name: String,
        val purpose: String,
        val malformed: Boolean = false
    )

    private val contracts = listOf(
        Contract("spotify_extended_minimal_music.json", "minimal music"),
        Contract("spotify_extended_full_fields_current.json", "current fields and privacy discard"),
        Contract("spotify_extended_legacy_decrypted_fields.json", "legacy unknown privacy fields"),
        Contract("spotify_extended_null_and_missing_optional.json", "nullable optional fields"),
        Contract("spotify_extended_duplicate_records.json", "duplicate preservation"),
        Contract("spotify_extended_overlap_a.json", "overlap side A"),
        Contract("spotify_extended_overlap_b.json", "overlap side B"),
        Contract("spotify_extended_reexport_initial.json", "initial export"),
        Contract("spotify_extended_reexport_later.json", "later re-export"),
        Contract("spotify_extended_qualification_edges.json", "uninterpreted qualification evidence"),
        Contract("spotify_extended_identity_edges.json", "metadata preservation"),
        Contract("spotify_extended_non_music.json", "media classification"),
        Contract("spotify_extended_timestamp_edges.json", "timestamp edges"),
        Contract("spotify_extended_invalid_records.json", "record-level invalid data"),
        Contract("spotify_basic_account_history_unsupported.json", "unsupported basic history"),
        Contract("spotify_extended_empty_array.json", "empty indeterminate JSON"),
        Contract("spotify_extended_malformed_truncated.json", "file-level malformed JSON", true),
        Contract("spotify_extended_out_of_order.json", "ordering-independent analysis")
    )
}
