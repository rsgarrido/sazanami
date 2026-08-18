package com.example.cdplaya.data.importing.spotify

import com.example.cdplaya.data.importing.ImportFileFailureReason
import com.example.cdplaya.data.importing.ImportFileFormat
import com.example.cdplaya.data.importing.ImportRecordDiagnostic
import com.example.cdplaya.data.importing.ImportRecordErrorReason
import com.example.cdplaya.data.importing.ImportedListeningRecord
import com.example.cdplaya.data.importing.ImportedMediaType
import java.io.IOException
import java.io.InputStream
import java.io.PushbackInputStream
import java.time.Clock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.decodeToSequence

sealed interface SpotifyParseItem {
    val recordIndex: Long

    data class ValidMusic(
        override val recordIndex: Long,
        val record: ImportedListeningRecord
    ) : SpotifyParseItem

    data class UnsupportedMedia(
        override val recordIndex: Long,
        val mediaType: ImportedMediaType,
        val sourceEndedAt: java.time.Instant,
        val listenedMs: Long
    ) : SpotifyParseItem

    data class Invalid(
        val diagnostic: ImportRecordDiagnostic
    ) : SpotifyParseItem {
        override val recordIndex: Long = diagnostic.recordIndex
    }
}

enum class SpotifyParseControl { CONTINUE, STOP }

sealed interface SpotifyFileParseResult {
    val format: ImportFileFormat
    val recordsEmitted: Long

    data class Completed(
        override val format: ImportFileFormat,
        override val recordsEmitted: Long
    ) : SpotifyFileParseResult

    data class Stopped(
        override val format: ImportFileFormat,
        override val recordsEmitted: Long
    ) : SpotifyFileParseResult

    data class Failed(
        override val format: ImportFileFormat,
        val reason: ImportFileFailureReason,
        val safeMessage: String,
        override val recordsEmitted: Long
    ) : SpotifyFileParseResult
}

/**
 * Synchronously parses one file. The parser owns and always closes the stream returned by
 * [openStream], including after callback-requested early termination.
 */
class SpotifyExtendedStreamingParser(
    clock: Clock = Clock.systemUTC(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val normalizer = SpotifyRecordNormalizer(clock)

    fun parse(
        openStream: () -> InputStream,
        onItem: (SpotifyParseItem) -> SpotifyParseControl
    ): SpotifyFileParseResult {
        val input = try {
            openStream()
        } catch (_: IOException) {
            return unreadable()
        } catch (_: SecurityException) {
            return unreadable()
        }
        var callbackCount = 0L
        return try {
            input.use {
                parseOwned(it) { item ->
                    callbackCount++
                    onItem(item)
                }
            }
        } catch (_: IOException) {
            unreadable(callbackCount)
        } catch (_: SerializationException) {
            malformed(callbackCount)
        }
    }

    /** Suspends between emitted records so import execution can apply bounded backpressure. */
    suspend fun parseSuspending(
        openStream: () -> InputStream,
        onItem: suspend (SpotifyParseItem) -> SpotifyParseControl
    ): SpotifyFileParseResult {
        val input = try {
            openStream()
        } catch (_: IOException) {
            return unreadable()
        } catch (_: SecurityException) {
            return unreadable()
        }
        var callbackCount = 0L
        return try {
            try {
                parseOwnedSuspending(input) { item ->
                    callbackCount++
                    onItem(item)
                }
            } finally {
                input.close()
            }
        } catch (_: IOException) {
            unreadable(callbackCount)
        } catch (_: SerializationException) {
            malformed(callbackCount)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun parseOwned(
        input: InputStream,
        onItem: (SpotifyParseItem) -> SpotifyParseControl
    ): SpotifyFileParseResult {
        val prepared = prepareUtf8(input)
        if (prepared.firstByte == null) return malformed()
        if (prepared.firstByte != '['.code) {
            json.decodeFromStream<JsonElement>(prepared.stream)
            return unknown()
        }

        val iterator = json.decodeToSequence<JsonElement>(
            prepared.stream,
            DecodeSequenceMode.ARRAY_WRAPPED
        ).iterator()
        val probe = ArrayList<JsonElement>(FORMAT_PROBE_RECORD_LIMIT)
        var detected: ImportFileFormat? = null
        while (iterator.hasNext() && probe.size < FORMAT_PROBE_RECORD_LIMIT) {
            val element = iterator.next()
            probe += element
            val elementFormat = (element as? JsonObject)?.let(SpotifyFormatDetector::detect)
            if (elementFormat != null) {
                detected = elementFormat
                break
            }
        }

        if (detected == ImportFileFormat.SPOTIFY_BASIC_ACCOUNT_HISTORY_UNSUPPORTED) {
            return SpotifyFileParseResult.Failed(
                format = detected,
                reason = ImportFileFailureReason.UNSUPPORTED_FORMAT,
                safeMessage = "Spotify Account Data history is not supported; select Extended Streaming History.",
                recordsEmitted = 0L
            )
        }
        if (detected == null) {
            while (iterator.hasNext()) iterator.next()
            return unknown()
        }

        var emitted = 0L
        fun emit(element: JsonElement): Boolean {
            val item = decodeItem(emitted, element)
            emitted++
            return onItem(item) == SpotifyParseControl.CONTINUE
        }
        probe.forEach { element ->
            if (!emit(element)) {
                return SpotifyFileParseResult.Stopped(detected, emitted)
            }
        }
        while (iterator.hasNext()) {
            if (!emit(iterator.next())) {
                return SpotifyFileParseResult.Stopped(detected, emitted)
            }
        }
        return SpotifyFileParseResult.Completed(detected, emitted)
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun parseOwnedSuspending(
        input: InputStream,
        onItem: suspend (SpotifyParseItem) -> SpotifyParseControl
    ): SpotifyFileParseResult {
        val prepared = prepareUtf8(input)
        if (prepared.firstByte == null) return malformed()
        if (prepared.firstByte != '['.code) {
            json.decodeFromStream<JsonElement>(prepared.stream)
            return unknown()
        }

        val iterator = json.decodeToSequence<JsonElement>(
            prepared.stream,
            DecodeSequenceMode.ARRAY_WRAPPED
        ).iterator()
        val probe = ArrayList<JsonElement>(FORMAT_PROBE_RECORD_LIMIT)
        var detected: ImportFileFormat? = null
        while (iterator.hasNext() && probe.size < FORMAT_PROBE_RECORD_LIMIT) {
            val element = iterator.next()
            probe += element
            val elementFormat = (element as? JsonObject)?.let(SpotifyFormatDetector::detect)
            if (elementFormat != null) {
                detected = elementFormat
                break
            }
        }

        if (detected == ImportFileFormat.SPOTIFY_BASIC_ACCOUNT_HISTORY_UNSUPPORTED) {
            return SpotifyFileParseResult.Failed(
                format = detected,
                reason = ImportFileFailureReason.UNSUPPORTED_FORMAT,
                safeMessage = "Spotify Account Data history is not supported; select Extended Streaming History.",
                recordsEmitted = 0L
            )
        }
        if (detected == null) {
            while (iterator.hasNext()) iterator.next()
            return unknown()
        }

        var emitted = 0L
        suspend fun emit(element: JsonElement): Boolean {
            val item = decodeItem(emitted, element)
            emitted++
            return onItem(item) == SpotifyParseControl.CONTINUE
        }
        for (element in probe) {
            if (!emit(element)) return SpotifyFileParseResult.Stopped(detected, emitted)
        }
        while (iterator.hasNext()) {
            if (!emit(iterator.next())) return SpotifyFileParseResult.Stopped(detected, emitted)
        }
        return SpotifyFileParseResult.Completed(detected, emitted)
    }

    private fun decodeItem(index: Long, element: JsonElement): SpotifyParseItem {
        val objectValue = element as? JsonObject ?: return SpotifyParseItem.Invalid(
            ImportRecordDiagnostic(index, ImportRecordErrorReason.INVALID_RECORD_SHAPE)
        )
        val dto = try {
            json.decodeFromJsonElement<SpotifyExtendedStreamingRecordDto>(objectValue)
        } catch (_: SerializationException) {
            return SpotifyParseItem.Invalid(
                ImportRecordDiagnostic(index, ImportRecordErrorReason.INVALID_RECORD_SHAPE)
            )
        } catch (_: IllegalArgumentException) {
            return SpotifyParseItem.Invalid(
                ImportRecordDiagnostic(index, ImportRecordErrorReason.INVALID_RECORD_SHAPE)
            )
        }
        return normalizer.normalize(index, dto)
    }

    private fun prepareUtf8(input: InputStream): PreparedStream {
        val stream = PushbackInputStream(input, 3)
        var byte = stream.read()
        if (byte == 0xEF) {
            val second = stream.read()
            val third = stream.read()
            if (second != 0xBB || third != 0xBF) {
                if (third >= 0) stream.unread(third)
                if (second >= 0) stream.unread(second)
                stream.unread(byte)
            }
            byte = stream.read()
        }
        while (byte >= 0 && byte.toChar().isWhitespace()) byte = stream.read()
        if (byte >= 0) stream.unread(byte)
        return PreparedStream(stream, byte.takeIf { it >= 0 })
    }

    private fun malformed(recordsEmitted: Long = 0L) = SpotifyFileParseResult.Failed(
        ImportFileFormat.MALFORMED_JSON,
        ImportFileFailureReason.MALFORMED_JSON,
        "The file is not complete, valid JSON.",
        recordsEmitted
    )

    private fun unreadable(recordsEmitted: Long = 0L) = SpotifyFileParseResult.Failed(
        ImportFileFormat.UNKNOWN_JSON,
        ImportFileFailureReason.UNREADABLE_STREAM,
        "The input stream could not be read.",
        recordsEmitted
    )

    private fun unknown() = SpotifyFileParseResult.Failed(
        ImportFileFormat.UNKNOWN_JSON,
        ImportFileFailureReason.UNKNOWN_FORMAT,
        "The JSON does not contain recognizable Spotify Extended Streaming History records.",
        0L
    )

    private data class PreparedStream(
        val stream: PushbackInputStream,
        val firstByte: Int?
    )

    private companion object {
        const val FORMAT_PROBE_RECORD_LIMIT = 20
    }
}
