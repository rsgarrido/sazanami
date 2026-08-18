package com.example.cdplaya.data.importing.spotify

import com.example.cdplaya.data.importing.ImportProvider
import com.example.cdplaya.data.importing.ImportedListeningRecord
import com.example.cdplaya.data.importing.ListeningImportCanonicalEncoder
import com.example.cdplaya.data.importing.ListeningImportFingerprint
import java.security.MessageDigest

/** Spotify event-evidence fingerprint v1. It deliberately contains no policy result or profile ID. */
object SpotifyListeningImportFingerprint {
    const val FINGERPRINT_VERSION = 1

    fun create(record: ImportedListeningRecord): ListeningImportFingerprint {
        val digest = MessageDigest.getInstance("SHA-256").digest(canonicalBytes(record))
        return ListeningImportFingerprint(FINGERPRINT_VERSION, digest.toLowerHex())
    }

    /** Exposed for deterministic encoder/hash contract tests, not for persistence. */
    fun canonicalBytes(record: ImportedListeningRecord): ByteArray {
        require(record.provider == ImportProvider.SPOTIFY)
        val hasExternalId = record.externalMediaId != null
        return ListeningImportCanonicalEncoder(FINGERPRINT_VERSION, FIELD_COUNT)
            .writeUtf8(1, record.provider.name)
            .writeLong(2, record.sourceEndedAt.epochSecond)
            .writeInt(3, record.sourceEndedAt.nano)
            .writeUtf8(4, record.mediaType.name)
            .writeUtf8(5, record.externalMediaId)
            .writeUtf8(6, record.trackTitle.takeUnless { hasExternalId })
            .writeUtf8(7, record.trackArtist.takeUnless { hasExternalId })
            .writeUtf8(8, record.albumTitle.takeUnless { hasExternalId })
            .writeUtf8(9, record.albumArtist.takeUnless { hasExternalId })
            .writeLong(10, record.listenedMs)
            .writeUtf8(11, SpotifyReasonTokenNormalizer.normalize(record.providerReasonStart))
            .writeUtf8(12, SpotifyReasonTokenNormalizer.normalize(record.providerReasonEnd))
            .writeUtf8(13, record.skippedEvidence.name)
            .toByteArray()
    }

    private fun ByteArray.toLowerHex(): String = CharArray(size * 2).also { chars ->
        forEachIndexed { index, byte ->
            val value = byte.toInt() and 0xff
            chars[index * 2] = HEX[value ushr 4]
            chars[index * 2 + 1] = HEX[value and 0x0f]
        }
    }.concatToString()

    private const val FIELD_COUNT = 13
    private const val HEX = "0123456789abcdef"
}
