package io.github.rsgarrido.sazanami.lyrics

import java.nio.charset.Charset
import java.security.MessageDigest

/** Limits apply before building playback cues; containers have a separate metadata budget. */
object LyricsLimits {
    const val SOURCE_BYTES = 2 * 1024 * 1024
    const val SOURCE_CHARACTERS = SOURCE_BYTES
    const val LINES = 20_000
    const val LINE_CHARACTERS = 65_536
    const val TIMESTAMPS = 40_000
    const val SEGMENTS = 80_000
    const val CANDIDATES = 16
    const val DIAGNOSTICS = 256
}

class LyricsInputLimitException(val limit: String) : IllegalArgumentException(limit)

enum class LyricsEncoding(val charset: Charset) {
    UTF8(Charsets.UTF_8), UTF16_LE(Charsets.UTF_16LE), UTF16_BE(Charsets.UTF_16BE)
}

enum class LyricsDiagnosticCode {
    INVALID_TIMESTAMP, INVALID_OFFSET, UNKNOWN_METADATA, UNSUPPORTED_LINE,
    INVALID_SEGMENT, DECREASING_SEGMENTS, AMBIGUOUS_SEGMENTS, INVALID_ENCODING
}

data class LyricsDiagnostic(val code: LyricsDiagnosticCode, val lineId: String? = null)
data class LyricTimestampToken(val raw: String, val timestampMs: Long)
data class LyricsMetadataToken(val key: String, val value: String)

/** Ranges use Kotlin/Compose UTF-16 indices; no whitespace-based tokenization. */
data class SourceLyricSegment(val timestamp: LyricTimestampToken, val startChar: Int, val endCharExclusive: Int)
enum class LyricsSourceLineKind { TEXT, TIMED, METADATA, UNSUPPORTED }

data class LyricsSourceLine(
    /** Opaque identity survives a future copy/reorder; never the display index. */
    val id: String,
    val rawText: String,
    val newline: String,
    val kind: LyricsSourceLineKind,
    val timestamps: List<LyricTimestampToken> = emptyList(),
    val metadata: LyricsMetadataToken? = null,
    val displayedText: String = rawText,
    val segments: List<SourceLyricSegment> = emptyList(),
    val wordTimingReliable: Boolean = false
)

/** Authoritative source snapshot, separate from sorted, offset-adjusted playback cues. */
class LyricsSourceDocument internal constructor(
    val originalText: String,
    val encoding: LyricsEncoding,
    val hasBom: Boolean,
    val lines: List<LyricsSourceLine>,
    val metadata: LyricsMetadata,
    val diagnostics: List<LyricsDiagnostic>,
    originalBytes: ByteArray
) {
    private val bytes = originalBytes.copyOf()
    val revision: String = lyricsDigest(bytes)
    val byteSize: Int get() = bytes.size

    /** Exact bytes, including malformed encoded bytes, BOM and mixed newline styles. */
    fun serializeUnchanged(): ByteArray = bytes.copyOf()

    /**
     * Explicit rewrite path: retain untouched raw lines/newlines and their IDs in the editor.
     * Re-encoding cannot preserve malformed original bytes. Parse changed text before playback.
     * Never serialize LyricsDocument: its times already include the document offset.
     */
    fun encodeLines(replacementLines: List<LyricsSourceLine>): ByteArray {
        if (replacementLines.size == lines.size && lines.indices.all { index ->
            replacementLines[index].rawText == lines[index].rawText && replacementLines[index].newline == lines[index].newline
        }) return serializeUnchanged()
        if (replacementLines.size > LyricsLimits.LINES) throw LyricsInputLimitException("lines")
        var characters = 0L
        replacementLines.forEach {
            if (it.rawText.length > LyricsLimits.LINE_CHARACTERS) throw LyricsInputLimitException("line characters")
            characters += it.rawText.length.toLong() + it.newline.length
        }
        if (characters > LyricsLimits.SOURCE_CHARACTERS) throw LyricsInputLimitException("source characters")
        val text = replacementLines.joinToString("") { it.rawText + it.newline }
        val body = text.toByteArray(encoding.charset)
        val bom = if (hasBom) encoding.bom() else byteArrayOf()
        if (body.size + bom.size > LyricsLimits.SOURCE_BYTES) throw LyricsInputLimitException("source bytes")
        return bom + body
    }

    companion object { const val REPRESENTATION_VERSION = 2 }
}

internal fun LyricsEncoding.bom(): ByteArray = when (this) {
    LyricsEncoding.UTF8 -> byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte())
    LyricsEncoding.UTF16_LE -> byteArrayOf(0xff.toByte(), 0xfe.toByte())
    LyricsEncoding.UTF16_BE -> byteArrayOf(0xfe.toByte(), 0xff.toByte())
}

internal fun lyricsDigest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes).joinToString("") { "%02x".format(it) }

internal fun lyricsIdentity(vararg parts: String): String = lyricsDigest(
    parts.joinToString("") { "${it.length}:$it" }.toByteArray(Charsets.UTF_8)
)

internal fun effectiveLyricTime(timestampMs: Long, offsetMs: Long): Long = when {
    offsetMs > 0 && timestampMs > Long.MAX_VALUE - offsetMs -> Long.MAX_VALUE
    offsetMs == Long.MIN_VALUE -> 0L
    offsetMs < 0 && timestampMs < -offsetMs -> 0L
    else -> (timestampMs + offsetMs).coerceAtLeast(0L)
}
