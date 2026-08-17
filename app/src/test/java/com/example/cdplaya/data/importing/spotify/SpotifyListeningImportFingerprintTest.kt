package com.example.cdplaya.data.importing.spotify

import com.example.cdplaya.data.importing.ImportProvider
import com.example.cdplaya.data.importing.ImportedCompletionEvidence
import com.example.cdplaya.data.importing.ImportedListeningRecord
import com.example.cdplaya.data.importing.ImportedMediaType
import com.example.cdplaya.data.importing.ImportedTimestampEvidence
import com.example.cdplaya.data.importing.ImportedTriState
import com.example.cdplaya.data.importing.ListeningImportCanonicalEncoder
import java.time.Instant
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class SpotifyListeningImportFingerprintTest {
    @Test fun canonicalEncoderHasKnownTypedBigEndianVector() {
        val encoded = ListeningImportCanonicalEncoder(1, 4)
            .writeNull(1)
            .writeUtf8(2, "")
            .writeLong(3, 1)
            .writeInt(4, 2)
            .toByteArray()
        assertEquals(
            "4344504c0001000400010000020100000000000302000000000000000100040300000002",
            encoded.hex()
        )
    }

    @Test fun canonicalEncoderRejectsFieldReordering() {
        val encoder = ListeningImportCanonicalEncoder(1, 2).writeUtf8(2, "later")
        assertThrows(IllegalStateException::class.java) { encoder.writeUtf8(1, "earlier") }
    }

    @Test fun stableIdExcludesMutableDisplayMetadataButNotOccurrenceEvidence() {
        val original = record()
        val changedDisplay = original.copy(
            trackTitle = "Renamed (Remastered)",
            trackArtist = "Different display artist",
            albumTitle = "New edition",
            albumArtist = "Different album artist"
        )
        assertEquals(fingerprint(original), fingerprint(changedDisplay))
        assertNotEquals(fingerprint(original), fingerprint(original.copy(externalMediaId = "other")))
        assertNotEquals(
            fingerprint(original),
            fingerprint(original.copy(sourceEndedAt = original.sourceEndedAt.plusNanos(1)))
        )
        assertNotEquals(fingerprint(original), fingerprint(original.copy(listenedMs = 30_001)))
        assertNotEquals(fingerprint(original), fingerprint(original.copy(providerReasonEnd = "fwdbtn")))
        assertNotEquals(
            fingerprint(original),
            fingerprint(original.copy(skippedEvidence = ImportedTriState.UNKNOWN))
        )
    }

    @Test fun uriLessFallbackPreservesProviderStringsExactly() {
        val uriLess = record().copy(externalMediaId = null)
        val variants = listOf(
            uriLess.copy(trackTitle = "Track!"),
            uriLess.copy(trackTitle = "TRACK"),
            uriLess.copy(trackTitle = "Tráck"),
            uriLess.copy(trackTitle = "Tra\u0301ck"),
            uriLess.copy(trackTitle = "Track (Live)"),
            uriLess.copy(trackTitle = "Track - Remastered"),
            uriLess.copy(trackArtist = "Artist feat. Guest"),
            uriLess.copy(albumTitle = "Album (Deluxe)"),
            uriLess.copy(albumTitle = null),
            uriLess.copy(albumTitle = "")
        )
        val hashes = (listOf(uriLess) + variants).map(::fingerprint)
        assertEquals(hashes.size, hashes.distinct().size)
        assertFalse("Tráck" == "Tra\u0301ck")
    }

    @Test fun normalizationDistinguishesNullBlankFalseAndUnknownAsContracted() {
        val base = record()
        assertEquals(
            fingerprint(base.copy(providerReasonStart = null)),
            fingerprint(base.copy(providerReasonStart = " \u2003 "))
        )
        assertNotEquals(
            fingerprint(base.copy(skippedEvidence = ImportedTriState.FALSE)),
            fingerprint(base.copy(skippedEvidence = ImportedTriState.UNKNOWN))
        )
        assertNotEquals(
            ListeningImportCanonicalEncoder(1, 1).writeNull(1).toByteArray().hex(),
            ListeningImportCanonicalEncoder(1, 1).writeUtf8(1, "").toByteArray().hex()
        )
    }

    @Test fun fingerprintIsLocaleTimezoneAndProfileIndependentKnownVector() {
        val priorLocale = Locale.getDefault()
        val priorZone = TimeZone.getDefault()
        try {
            val expected = fingerprint(record())
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"))
            assertEquals(expected, fingerprint(record()))
            assertEquals(1, SpotifyListeningImportFingerprint.FINGERPRINT_VERSION)
            assertEquals("a683395ff3ddee73cdfa4e8345d4eda9e4dce0bbb78898f5fc7df472d9bf553d", expected)
        } finally {
            Locale.setDefault(priorLocale)
            TimeZone.setDefault(priorZone)
        }
    }

    @Test fun canonicalTimestampKeepsSubMillisecondPrecision() {
        val millis = record().copy(sourceEndedAt = Instant.parse("2024-01-01T00:00:00.001Z"))
        val nanos = millis.copy(sourceEndedAt = Instant.parse("2024-01-01T00:00:00.001000001Z"))
        assertNotEquals(fingerprint(millis), fingerprint(nanos))
    }

    private fun fingerprint(record: ImportedListeningRecord) =
        SpotifyListeningImportFingerprint.create(record).fingerprint

    private fun record() = ImportedListeningRecord(
        provider = ImportProvider.SPOTIFY,
        externalMediaId = "stable-track-id",
        mediaType = ImportedMediaType.MUSIC_TRACK,
        trackTitle = "Track",
        trackArtist = "Artist",
        albumTitle = "Album",
        albumArtist = "Artist",
        sourceStartedAt = null,
        sourceEndedAt = Instant.parse("2024-01-01T00:00:00.123456789Z"),
        timestampEvidence = ImportedTimestampEvidence.SOURCE_END_ONLY,
        listenedMs = 30_000,
        skippedEvidence = ImportedTriState.FALSE,
        completionEvidence = ImportedCompletionEvidence.UNKNOWN,
        providerReasonStart = " clickrow ",
        providerReasonEnd = " trackdone "
    )

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
