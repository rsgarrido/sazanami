package io.github.rsgarrido.sazanami.lyrics

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException

class LrcParser {
    /** Compatibility API. Source-aware callers use parseSource followed by project. */
    fun parse(bytes: ByteArray): LyricsDocument = compatibilityParse { parseSource(bytes) }
    fun parse(text: String): LyricsDocument = compatibilityParse { parseSource(text) }

    private inline fun compatibilityParse(source: () -> LyricsSourceDocument): LyricsDocument = try {
        project(source())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: IllegalArgumentException) {
        LyricsDocument.Unsynced(emptyList())
    }

    fun parseSource(text: String, checkCancelled: () -> Unit = {}): LyricsSourceDocument {
        if (text.length > LyricsLimits.SOURCE_CHARACTERS) throw LyricsInputLimitException("source characters")
        return parseSource(text.toByteArray(Charsets.UTF_8), checkCancelled)
    }

    /**
     * Structured-editor boundary: explicitly supplied row IDs follow their rows, including a
     * reorder. Rebuild tokens/ranges after raw-line edits instead of trusting stale copied spans.
     * Multiline/raw edits use parseSource and establish new row identities.
     */
    fun reparseLines(source: LyricsSourceDocument, lines: List<LyricsSourceLine>,
        checkCancelled: () -> Unit = {}): LyricsSourceDocument {
        if (lines.size > LyricsLimits.LINES) throw LyricsInputLimitException("lines")
        require(lines.map { it.id }.distinct().size == lines.size) { "Duplicate lyric line identities" }
        val parsed = parseSource(source.encodeLines(lines), checkCancelled)
        require(parsed.lines.size == lines.size) { "Structured rows must retain their line boundaries" }
        val identities = parsed.lines.zip(lines).associate { (generated, supplied) -> generated.id to supplied.id }
        return LyricsSourceDocument(parsed.originalText, parsed.encoding, parsed.hasBom,
            parsed.lines.map { it.copy(id = identities.getValue(it.id)) }, parsed.metadata,
            parsed.diagnostics.map { it.copy(lineId = it.lineId?.let(identities::getValue)) },
            parsed.serializeUnchanged())
    }

    fun parseSource(bytes: ByteArray, checkCancelled: () -> Unit = {}): LyricsSourceDocument {
        checkCancelled()
        if (bytes.size > LyricsLimits.SOURCE_BYTES) throw LyricsInputLimitException("source bytes")
        val encoding = LyricsEncoding.entries.firstOrNull { candidate ->
            val bom = candidate.bom()
            bytes.size >= bom.size && bom.indices.all { bytes[it] == bom[it] }
        }
        val actualEncoding = encoding ?: LyricsEncoding.UTF8
        val hasBom = encoding != null
        val offset = if (hasBom) actualEncoding.bom().size else 0
        val diagnostics = mutableListOf<LyricsDiagnostic>()
        val text = try {
            actualEncoding.charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, offset, bytes.size - offset)).toString()
        } catch (_: java.nio.charset.CharacterCodingException) {
            diagnostics += LyricsDiagnostic(LyricsDiagnosticCode.INVALID_ENCODING)
            String(bytes, offset, bytes.size - offset, actualEncoding.charset)
        }
        if (text.length > LyricsLimits.SOURCE_CHARACTERS) throw LyricsInputLimitException("source characters")
        val lines = mutableListOf<LyricsSourceLine>()
        val metadata = linkedMapOf<String, String>()
        var timestamps = 0
        var segments = 0
        var start = 0
        var cursor = 0
        fun addLine(end: Int, newline: String) {
            checkCancelled()
            if (lines.size >= LyricsLimits.LINES) throw LyricsInputLimitException("lines")
            if (end - start > LyricsLimits.LINE_CHARACTERS) throw LyricsInputLimitException("line characters")
            val line = parseLine(text.substring(start, end), newline) { code, id ->
                if (diagnostics.size < LyricsLimits.DIAGNOSTICS) diagnostics += LyricsDiagnostic(code, id)
            }
            timestamps += line.timestamps.size
            segments += line.segments.size
            if (timestamps > LyricsLimits.TIMESTAMPS) throw LyricsInputLimitException("timestamps")
            if (segments > LyricsLimits.SEGMENTS) throw LyricsInputLimitException("segments")
            line.metadata?.let { token ->
                val key = token.key.lowercase(Locale.ROOT)
                if (key != "offset" || token.value.trim().toLongOrNull() != null) metadata[key] = token.value
            }
            lines += line
        }
        while (cursor < text.length) {
            when (text[cursor]) {
                '\r', '\n' -> {
                    val length = if (text[cursor] == '\r' && text.getOrNull(cursor + 1) == '\n') 2 else 1
                    addLine(cursor, text.substring(cursor, cursor + length))
                    cursor += length
                    start = cursor
                }
                else -> cursor++
            }
        }
        addLine(text.length, "")
        return LyricsSourceDocument(text, actualEncoding, hasBom, lines.toList(),
            LyricsMetadata(metadata["ar"], metadata["al"], metadata["ti"], metadata["by"],
                metadata["re"], metadata["ve"], metadata["offset"]?.trim()?.toLongOrNull() ?: 0L),
            diagnostics.toList(), bytes)
    }

    /** Offset application occurs only here, to both line and reliable segment times. */
    fun project(source: LyricsSourceDocument): LyricsDocument {
        val cues = source.lines.flatMap { line ->
            line.timestamps.map { timestamp ->
                LyricCue(
                    timestampMs = effectiveLyricTime(timestamp.timestampMs, source.metadata.offsetMs),
                    content = if (line.displayedText.isBlank()) LyricCueContent.Instrumental
                        else LyricCueContent.Text(line.displayedText),
                    sourceLineId = line.id,
                    sourceTimestampMs = timestamp.timestampMs,
                    segments = if (!line.wordTimingReliable) emptyList() else line.segments.map { segment ->
                        TimedLyricSegment(segment.timestamp.timestampMs,
                            effectiveLyricTime(segment.timestamp.timestampMs, source.metadata.offsetMs),
                            segment.startChar, segment.endCharExclusive)
                    }
                )
            }
        }.sortedBy(LyricCue::timestampMs)
        if (cues.isNotEmpty()) return LyricsDocument.Synced(cues, source.metadata)
        return LyricsDocument.Unsynced(source.lines.filter { it.kind == LyricsSourceLineKind.TEXT }
            .map { StaticLyricLine(it.rawText) }.dropLastWhile { it.text.isEmpty() }, source.metadata)
    }

    private fun parseLine(raw: String, newline: String, diagnostic: (LyricsDiagnosticCode, String) -> Unit): LyricsSourceLine {
        val id = UUID.randomUUID().toString()
        val metadata = METADATA.matchEntire(raw.trim())
        if (metadata != null) {
            val key = metadata.groupValues[1]
            val value = metadata.groupValues[2]
            if (key.lowercase(Locale.ROOT) !in METADATA_KEYS) diagnostic(LyricsDiagnosticCode.UNKNOWN_METADATA, id)
            if (key.equals("offset", true) && value.trim().toLongOrNull() == null) diagnostic(LyricsDiagnosticCode.INVALID_OFFSET, id)
            return LyricsSourceLine(id, raw, newline, LyricsSourceLineKind.METADATA,
                metadata = LyricsMetadataToken(key, value))
        }
        val candidate = raw.trimStart()
        val times = mutableListOf<LyricTimestampToken>()
        var cursor = 0
        while (candidate.getOrNull(cursor) == '[') {
            val token = TIMESTAMP.find(candidate, cursor)?.takeIf { it.range.first == cursor } ?: break
            val time = timestampMs(token.groupValues[1], token.groupValues[2], token.groupValues[3])
                ?: return LyricsSourceLine(id, raw, newline, LyricsSourceLineKind.UNSUPPORTED).also {
                    diagnostic(LyricsDiagnosticCode.INVALID_TIMESTAMP, id)
                }
            times += LyricTimestampToken(token.value, time)
            if (times.size > LyricsLimits.TIMESTAMPS) throw LyricsInputLimitException("timestamps")
            cursor = token.range.last + 1
        }
        if (times.isEmpty()) {
            val kind = if (candidate.startsWith("[")) LyricsSourceLineKind.UNSUPPORTED else LyricsSourceLineKind.TEXT
            if (kind == LyricsSourceLineKind.UNSUPPORTED) diagnostic(LyricsDiagnosticCode.UNSUPPORTED_LINE, id)
            return LyricsSourceLine(id, raw, newline, kind)
        }
        val body = candidate.substring(cursor)
        val text = StringBuilder()
        val markers = mutableListOf<Pair<LyricTimestampToken, Int>>()
        var reliable = true
        cursor = 0
        INLINE.findAll(body).forEach { match ->
            text.append(body, cursor, match.range.first)
            val parsed = INLINE_TIME.matchEntire(match.value)
            val time = parsed?.let { timestampMs(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }
            if (time == null) {
                if (parsed == null) text.append(match.value)
                reliable = false
                diagnostic(LyricsDiagnosticCode.INVALID_SEGMENT, id)
            } else {
                markers += LyricTimestampToken(match.value, time) to text.length
                if (markers.size > LyricsLimits.SEGMENTS) throw LyricsInputLimitException("segments")
            }
            cursor = match.range.last + 1
        }
        text.append(body, cursor, body.length)
        if (text.contains('<') || text.contains('>')) {
            reliable = false
            diagnostic(LyricsDiagnosticCode.INVALID_SEGMENT, id)
        }
        if (markers.zipWithNext().any { (a, b) -> a.first.timestampMs > b.first.timestampMs } ||
            markers.firstOrNull()?.first?.timestampMs?.let { it < times.first().timestampMs } == true) {
            reliable = false
            diagnostic(LyricsDiagnosticCode.DECREASING_SEGMENTS, id)
        }
        if (times.size > 1 && markers.isNotEmpty()) {
            reliable = false
            diagnostic(LyricsDiagnosticCode.AMBIGUOUS_SEGMENTS, id)
        }
        val segments = markers.mapIndexed { index, (timestamp, position) ->
            SourceLyricSegment(timestamp, position, markers.getOrNull(index + 1)?.second ?: text.length)
        }
        return LyricsSourceLine(id, raw, newline, LyricsSourceLineKind.TIMED, times.toList(),
            displayedText = text.toString(), segments = segments, wordTimingReliable = reliable && markers.isNotEmpty())
    }

    private fun timestampMs(minutesText: String, secondsText: String, fraction: String): Long? {
        val minutes = minutesText.toLongOrNull() ?: return null
        val seconds = secondsText.toLongOrNull()?.takeIf { it in 0L..59L } ?: return null
        val fractional = when (fraction.length) {
            0 -> 0L
            1 -> fraction.toLongOrNull()?.times(100)
            2 -> fraction.toLongOrNull()?.times(10)
            3 -> fraction.toLongOrNull()
            else -> null
        } ?: return null
        if (minutes > (Long.MAX_VALUE - seconds * 1_000 - fractional) / 60_000) return null
        return minutes * 60_000 + seconds * 1_000 + fractional
    }

    private companion object {
        val METADATA = Regex("""\[([A-Za-z]+):(.*)]""")
        val METADATA_KEYS = setOf("ar", "al", "ti", "by", "re", "ve", "offset")
        val TIMESTAMP = Regex("""\[(\d+):(\d{2})(?:\.(\d{1,3}))?]""")
        val INLINE_TIME = Regex("""<(\d+):(\d{2})(?:\.(\d{1,3}))?>""")
        val INLINE = Regex("""<[^<>\r\n]*>""")
    }
}
