package io.github.rsgarrido.sazanami.data.importing.spotify

import io.github.rsgarrido.sazanami.data.importing.ImportFileFailureReason
import io.github.rsgarrido.sazanami.data.importing.ImportFileFormat
import io.github.rsgarrido.sazanami.data.importing.ImportRecordErrorReason
import io.github.rsgarrido.sazanami.data.importing.ImportedCompletionEvidence
import io.github.rsgarrido.sazanami.data.importing.ImportedTimestampEvidence
import io.github.rsgarrido.sazanami.data.importing.ImportedTriState
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyExtendedStreamingParserTest {
    private val parser = SpotifyExtendedStreamingParser(FIXED_CLOCK)

    @Test
    fun dtoToleratesCompleteUnknownAndLegacyPrivacyFields() {
        val dto = Json { ignoreUnknownKeys = true }.decodeFromString<
            SpotifyExtendedStreamingRecordDto
            >(
            """{"ts":"2024-01-01T00:00:00Z","ms_played":42,
                "master_metadata_track_name":"Private Boundary",
                "ip_addr":"192.0.2.1","ip_addr_decrypted":"198.51.100.1",
                "user_agent_decrypted":"not retained","future_field":{"nested":true}}"""
        )
        assertEquals("2024-01-01T00:00:00Z", dto.ts)
        assertEquals(42L, dto.msPlayed)
        assertEquals("Private Boundary", dto.trackName)
        assertNull(dto.albumName)
        assertNull(dto.skipped)
    }

    @Test
    fun minimalMusicNormalizesExactEndEvidenceAndPreservesProviderValues() {
        val items = parseFixture("spotify_extended_minimal_music.json")
        assertEquals(3, items.size)
        val first = (items.first() as SpotifyParseItem.ValidMusic).record
        assertEquals("MinMusic000000000000001", first.externalMediaId)
        assertEquals("Clockwork Sunrise", first.trackTitle)
        assertEquals("The Fictional Satellites", first.trackArtist)
        assertEquals("The Fictional Satellites", first.albumArtist)
        assertNull(first.sourceStartedAt)
        assertEquals(Instant.parse("2024-01-01T00:00:00Z"), first.sourceEndedAt)
        assertEquals(ImportedTimestampEvidence.SOURCE_END_ONLY, first.timestampEvidence)
        assertEquals(ImportedCompletionEvidence.UNKNOWN, first.completionEvidence)
        assertEquals("trackdone", first.providerReasonEnd)
        assertEquals(ImportedTriState.FALSE, first.skippedEvidence)
        assertEquals(ImportedTriState.UNKNOWN,
            (items[2] as SpotifyParseItem.ValidMusic).record.skippedEvidence)
    }

    @Test
    fun nullAndMissingOptionalsRemainNullAndMetadataOnlyMusicGetsNoId() {
        val records = parseFixture("spotify_extended_null_and_missing_optional.json")
            .map { (it as SpotifyParseItem.ValidMusic).record }
        assertEquals(3, records.size)
        assertNull(records[0].albumTitle)
        assertNull(records[0].providerReasonStart)
        assertNull(records[1].externalMediaId)
        assertEquals("Metadata Compass", records[1].trackTitle)
    }

    @Test
    fun metadataAndUnicodeAreNotCanonicalized() {
        val records = parseFixture("spotify_extended_identity_edges.json")
            .map { (it as SpotifyParseItem.ValidMusic).record }
        assertEquals("Bright Wire (2024 Remaster)", records[4].trackTitle)
        assertEquals("Bright Wire - Live", records[5].trackTitle)
        assertEquals("Café Signals", records[6].trackTitle)
        assertEquals("Café Signals", records[7].trackTitle)
        assertFalse(records[6].trackTitle == records[7].trackTitle)
        assertEquals("Zoë Circuit feat. Niño", records[6].trackArtist)
    }

    @Test
    fun duplicateObjectsAreAllEmitted() {
        val items = parseFixture("spotify_extended_duplicate_records.json")
        assertEquals(3, items.size)
        assertEquals(
            (items[0] as SpotifyParseItem.ValidMusic).record,
            (items[1] as SpotifyParseItem.ValidMusic).record
        )
    }

    @Test
    fun mediaClassificationUsesUrisFirstAndRejectsStrongConflict() {
        val items = parseFixture("spotify_extended_non_music.json")
        assertEquals(
            listOf(
                "PODCAST_EPISODE",
                "AUDIOBOOK",
                "UNKNOWN",
                "MUSIC_TRACK",
                "VIDEO",
                "AMBIGUOUS_MEDIA_TYPE"
            ),
            items.map { item ->
                when (item) {
                    is SpotifyParseItem.UnsupportedMedia -> item.mediaType.name
                    is SpotifyParseItem.ValidMusic -> item.record.mediaType.name
                    is SpotifyParseItem.Invalid -> item.diagnostic.reason.name
                }
            }
        )
    }

    @Test
    fun invalidRecordReasonsAreLocalToTheirRecords() {
        val reasons = parseFixture("spotify_extended_invalid_records.json")
            .map { (it as SpotifyParseItem.Invalid).diagnostic.reason }
        assertEquals(
            listOf(
                ImportRecordErrorReason.MISSING_TIMESTAMP,
                ImportRecordErrorReason.INVALID_TIMESTAMP,
                ImportRecordErrorReason.MISSING_LISTENED_DURATION,
                ImportRecordErrorReason.NEGATIVE_LISTENED_DURATION,
                ImportRecordErrorReason.INVALID_TRACK_URI,
                ImportRecordErrorReason.MISSING_MUSIC_METADATA,
                ImportRecordErrorReason.AMBIGUOUS_MEDIA_TYPE
            ),
            reasons
        )
    }

    @Test
    fun durationAllowsZeroPositiveAndLargeLongButRejectsNegativeAndNull() {
        val json = """[
          {"ts":"2024-01-01T00:00:00Z","ms_played":0,"master_metadata_track_name":"Zero","master_metadata_album_artist_name":"Lab"},
          {"ts":"2024-01-01T00:00:01Z","ms_played":9223372036854775807,"master_metadata_track_name":"Large","master_metadata_album_artist_name":"Lab"},
          {"ts":"2024-01-01T00:00:02Z","ms_played":-1,"master_metadata_track_name":"Negative","master_metadata_album_artist_name":"Lab"},
          {"ts":"2024-01-01T00:00:03Z","master_metadata_track_name":"Null","master_metadata_album_artist_name":"Lab"}
        ]"""
        val items = parseText(json)
        assertEquals(0L, (items[0] as SpotifyParseItem.ValidMusic).record.listenedMs)
        assertEquals(Long.MAX_VALUE, (items[1] as SpotifyParseItem.ValidMusic).record.listenedMs)
        assertEquals(ImportRecordErrorReason.NEGATIVE_LISTENED_DURATION,
            (items[2] as SpotifyParseItem.Invalid).diagnostic.reason)
        assertEquals(ImportRecordErrorReason.MISSING_LISTENED_DURATION,
            (items[3] as SpotifyParseItem.Invalid).diagnostic.reason)
    }

    @Test
    fun wrongUriTypeIsInvalidAndNonblankTextKeepsWhitespaceAndCase() {
        val json = """[
          {"ts":"2024-01-01T00:00:00Z","ms_played":1,
           "master_metadata_track_name":"Wrong URI","master_metadata_album_artist_name":"Lab",
           "spotify_track_uri":"spotify:episode:WrongField000000000001"},
          {"ts":"2024-01-01T00:00:01Z","ms_played":2,
           "master_metadata_track_name":"  Spaced Title  ",
           "master_metadata_album_artist_name":"  Mixed CASE Artist  ",
           "reason_start":"  CustomToken  ","reason_end":"   "}
        ]"""
        val items = parseText(json)
        assertEquals(ImportRecordErrorReason.INVALID_TRACK_URI,
            (items[0] as SpotifyParseItem.Invalid).diagnostic.reason)
        val record = (items[1] as SpotifyParseItem.ValidMusic).record
        assertEquals("  Spaced Title  ", record.trackTitle)
        assertEquals("  Mixed CASE Artist  ", record.trackArtist)
        assertEquals("  CustomToken  ", record.providerReasonStart)
        assertNull(record.providerReasonEnd)
    }

    @Test
    fun timestampParsingHandlesFractionalAndBoundariesAndUsesInjectedFuturePolicy() {
        val items = parseFixture("spotify_extended_timestamp_edges.json")
        assertEquals(Instant.parse("2008-01-01T00:00:00Z"),
            (items[5] as SpotifyParseItem.ValidMusic).record.sourceEndedAt)
        assertEquals(Instant.parse("2023-12-31T23:59:59.999Z"),
            (items[2] as SpotifyParseItem.ValidMusic).record.sourceEndedAt)
        assertEquals(ImportRecordErrorReason.INVALID_TIMESTAMP,
            (items[6] as SpotifyParseItem.Invalid).diagnostic.reason)
        assertEquals(ImportRecordErrorReason.FUTURE_TIMESTAMP,
            (items[7] as SpotifyParseItem.Invalid).diagnostic.reason)
    }

    @Test
    fun basicUnknownEmptyAndMalformedHaveDistinctFileResults() {
        val basic = parseResult("spotify_basic_account_history_unsupported.json")
            as SpotifyFileParseResult.Failed
        assertEquals(ImportFileFormat.SPOTIFY_BASIC_ACCOUNT_HISTORY_UNSUPPORTED, basic.format)
        assertEquals(ImportFileFailureReason.UNSUPPORTED_FORMAT, basic.reason)

        val empty = parseResult("spotify_extended_empty_array.json")
            as SpotifyFileParseResult.Failed
        assertEquals(ImportFileFormat.UNKNOWN_JSON, empty.format)
        assertEquals(ImportFileFailureReason.UNKNOWN_FORMAT, empty.reason)

        val unknown = parser.parse(
            { ByteArrayInputStream("{\"hello\":\"world\"}".encodeToByteArray()) },
            { SpotifyParseControl.CONTINUE }
        ) as SpotifyFileParseResult.Failed
        assertEquals(ImportFileFailureReason.UNKNOWN_FORMAT, unknown.reason)

        val malformed = parseResult("spotify_extended_malformed_truncated.json")
            as SpotifyFileParseResult.Failed
        assertEquals(ImportFileFormat.MALFORMED_JSON, malformed.format)
        assertEquals(ImportFileFailureReason.MALFORMED_JSON, malformed.reason)
    }

    @Test
    fun lateSyntaxFailureReportsAlreadyEmittedRecordsWithoutRawData() {
        val text = """[
          {"ts":"2024-01-01T00:00:00Z","ms_played":1,
           "master_metadata_track_name":"Complete First",
           "master_metadata_album_artist_name":"Partial File"},
          {"ts":"2024-01-01T00:00:01Z","ms_played":2
        """
        var callbackCount = 0L
        val result = parser.parse({ ByteArrayInputStream(text.encodeToByteArray()) }) {
            callbackCount++
            SpotifyParseControl.CONTINUE
        } as SpotifyFileParseResult.Failed
        assertEquals(ImportFileFailureReason.MALFORMED_JSON, result.reason)
        assertEquals(1L, callbackCount)
        assertEquals(1L, result.recordsEmitted)
        assertFalse(result.safeMessage.contains("Complete First"))
    }

    @Test
    fun earlyStopClosesStreamAndDoesNotReadTheWholeInput() {
        val bytes = buildString {
            append('[')
            repeat(2_000) { index ->
                if (index > 0) append(',')
                append("{\"ts\":\"2024-01-01T00:00:00Z\",\"ms_played\":1,")
                append("\"master_metadata_track_name\":\"Track $index\",")
                append("\"master_metadata_album_artist_name\":\"Artist\"}")
            }
            append(']')
        }.encodeToByteArray()
        val tracked = TrackingInputStream(ByteArrayInputStream(bytes))
        val result = parser.parse({ tracked }) { SpotifyParseControl.STOP }
        assertTrue(result is SpotifyFileParseResult.Stopped)
        assertEquals(1L, result.recordsEmitted)
        assertTrue(tracked.closed)
        assertTrue("Parser eagerly consumed ${tracked.bytesRead} of ${bytes.size}",
            tracked.bytesRead < bytes.size)
    }

    private fun parseFixture(name: String): List<SpotifyParseItem> {
        val items = mutableListOf<SpotifyParseItem>()
        val result = parser.parse({ resource(name) }) {
            items += it
            SpotifyParseControl.CONTINUE
        }
        assertTrue("Unexpected parse result: $result", result is SpotifyFileParseResult.Completed)
        return items
    }

    private fun parseText(text: String): List<SpotifyParseItem> {
        val items = mutableListOf<SpotifyParseItem>()
        val result = parser.parse({ ByteArrayInputStream(text.encodeToByteArray()) }) {
            items += it
            SpotifyParseControl.CONTINUE
        }
        assertTrue(result is SpotifyFileParseResult.Completed)
        return items
    }

    private fun parseResult(name: String): SpotifyFileParseResult = parser.parse(
        { resource(name) },
        { SpotifyParseControl.CONTINUE }
    )

    private fun resource(name: String) = requireNotNull(
        javaClass.getResourceAsStream("/importing/spotify/$name")
    )

    private class TrackingInputStream(input: ByteArrayInputStream) : FilterInputStream(input) {
        var bytesRead = 0
        var closed = false

        override fun read(): Int = super.read().also { if (it >= 0) bytesRead++ }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            super.read(buffer, offset, length).also { if (it > 0) bytesRead += it }

        override fun close() {
            closed = true
            super.close()
        }
    }

    private companion object {
        val FIXED_CLOCK: Clock = Clock.fixed(
            Instant.parse("2035-01-01T00:00:00Z"),
            ZoneOffset.UTC
        )
    }
}
