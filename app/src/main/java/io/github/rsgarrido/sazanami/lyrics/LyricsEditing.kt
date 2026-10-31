package io.github.rsgarrido.sazanami.lyrics

import java.util.Locale
import java.util.UUID
import kotlinx.serialization.Serializable

object LyricsEditingLimits {
    const val DOCUMENT_BYTES = 256 * 1024
    const val TOTAL_OVERRIDE_BYTES = 8 * 1024 * 1024
    const val OVERRIDES = 1024
    const val DRAFTS = 20
    const val TOTAL_DRAFT_BYTES = 4 * 1024 * 1024
    const val UNDO = 12
    const val UNDO_WEIGHT = 16L * 1024 * 1024
}

@Serializable
enum class LyricsEditorMode { PLAIN, STRUCTURED, RAW }
enum class LyricsExportFormat(val extension: String, val mimeType: String) {
    PLAIN("txt", "text/plain"), LRC("lrc", "application/octet-stream");
    companion object {
        fun forSource(source: LyricsSourceDocument): LyricsExportFormat =
            if (source.lines.any { it.timestamps.isNotEmpty() }) LRC else PLAIN
    }
}
enum class LyricsEditError {
    EMPTY, TOO_LARGE, ENCODING, INVALID_TIMESTAMP, INVALID_OFFSET, RAW_REQUIRED,
    WORD_TIMING_CHOICE, STORAGE, TARGET_MISSING, TARGET_AMBIGUOUS, REVISION_CONFLICT,
    IMPORT_CANCELLED, IMPORT_READ, IMPORT_FORMAT, EXPORT_CANCELLED, EXPORT_WRITE,
    POSITION_UNAVAILABLE, TRACK_MISMATCH, RESTORE_CONFLICT
}

class LyricsEditException(val reason: LyricsEditError) : IllegalArgumentException(reason.name)

/** Operations always edit source rows. Playback cues are never used to serialize an edit. */
class LyricsDocumentEditor(private val parser: LrcParser = LrcParser()) {
    fun validate(source: LyricsSourceDocument) {
        if (source.byteSize > LyricsEditingLimits.DOCUMENT_BYTES) throw LyricsEditException(LyricsEditError.TOO_LARGE)
        if (source.diagnostics.any { it.code == LyricsDiagnosticCode.INVALID_ENCODING }) throw LyricsEditException(LyricsEditError.ENCODING)
        if (source.originalText.any { it.code < 32 && it !in "\t\r\n" }) throw LyricsEditException(LyricsEditError.IMPORT_FORMAT)
        val document = parser.project(source)
        val usable = when (document) {
            is LyricsDocument.Synced -> document.cues.isNotEmpty()
            is LyricsDocument.Unsynced -> document.lines.any { it.text.isNotBlank() }
        }
        if (!usable) throw LyricsEditException(LyricsEditError.EMPTY)
        // Retain unsupported source lines and unknown metadata, but never commit a malformed offset.
        if (source.diagnostics.any { it.code == LyricsDiagnosticCode.INVALID_OFFSET }) throw LyricsEditException(LyricsEditError.INVALID_OFFSET)
    }

    fun parse(bytes: ByteArray, checkCancelled: () -> Unit = {}): LyricsSourceDocument {
        if (bytes.size > LyricsEditingLimits.DOCUMENT_BYTES) throw LyricsEditException(LyricsEditError.TOO_LARGE)
        val source = parser.parseSource(bytes, checkCancelled)
        if (source.diagnostics.any { it.code == LyricsDiagnosticCode.INVALID_ENCODING }) throw LyricsEditException(LyricsEditError.ENCODING)
        if ('\u0000' in source.originalText) throw LyricsEditException(LyricsEditError.IMPORT_FORMAT)
        return source
    }

    fun raw(source: LyricsSourceDocument, text: String, checkCancelled: () -> Unit = {}): LyricsSourceDocument {
        val bytes = (if (source.hasBom) source.encoding.bom() else byteArrayOf()) + text.toByteArray(source.encoding.charset)
        return parse(bytes, checkCancelled)
    }

    fun editable(row: LyricsSourceLine): Boolean = row.kind == LyricsSourceLineKind.TEXT || row.kind == LyricsSourceLineKind.TIMED

    fun changeRow(source: LyricsSourceDocument, id: String, text: String, timestamps: String,
        removeWordTimings: Boolean = false): LyricsSourceDocument {
        if ('\n' in text || '\r' in text) throw LyricsEditException(LyricsEditError.RAW_REQUIRED)
        val row = source.lines.single { it.id == id }
        if (!editable(row)) throw LyricsEditException(LyricsEditError.RAW_REQUIRED)
        val tokens = timestamps.split(',').map(String::trim).filter(String::isNotEmpty).mapIndexed { index, token ->
            val ms = parseTime(token)
            row.timestamps.getOrNull(index)?.takeIf { it.timestampMs == ms }?.raw ?: "[${formatTime(ms)}]"
        }
        val textChanged = text != row.displayedText
        val hasInline = row.kind == LyricsSourceLineKind.TIMED && (row.rawText.contains('<') || row.rawText.contains('>'))
        if (hasInline && textChanged && !removeWordTimings) throw LyricsEditException(LyricsEditError.WORD_TIMING_CHOICE)
        // Keep original precision, spacing, repeated tokens and inline markers when unchanged.
        val originalTimes = row.timestamps.joinToString(", ") { formatTime(it.timestampMs) }
        if (!textChanged && timestamps.trim() == originalTimes) return source
        if (hasInline && tokens.isEmpty() && !removeWordTimings) throw LyricsEditException(LyricsEditError.WORD_TIMING_CHOICE)
        val leading = if (row.timestamps.isEmpty()) "" else row.rawText.takeWhile(Char::isWhitespace)
        val oldPrefix = leading.length + row.timestamps.sumOf { it.raw.length }
        val body = if (!textChanged && hasInline && !removeWordTimings) row.rawText.substring(oldPrefix) else text
        val prefix = if (timestamps.trim() == originalTimes) row.timestamps.joinToString("") { it.raw }
            else tokens.joinToString("")
        return replace(source, source.lines.map { if (it.id == id) it.copy(rawText = leading + prefix + body) else it })
    }

    fun stamp(source: LyricsSourceDocument, id: String, positionMs: Long): LyricsSourceDocument {
        val row = source.lines.single { it.id == id }
        if (row.timestamps.size > 1) throw LyricsEditException(LyricsEditError.RAW_REQUIRED)
        val raw = try { Math.subtractExact(positionMs, source.metadata.offsetMs) }
            catch (_: ArithmeticException) { throw LyricsEditException(LyricsEditError.INVALID_TIMESTAMP) }
        if (raw < 0) throw LyricsEditException(LyricsEditError.INVALID_TIMESTAMP)
        return changeRow(source, id, row.displayedText, formatTime(raw))
    }

    fun offset(source: LyricsSourceDocument, value: Long): LyricsSourceDocument {
        val rows = source.lines.toMutableList()
        val offsets = rows.filter { it.metadata?.key.equals("offset", true) }
        if (offsets.size == 1 && source.metadata.offsetMs == value) return source
        val anchor = offsets.lastOrNull()?.id
        rows.removeAll { it.metadata?.key.equals("offset", true) && it.id != anchor }
        if (anchor != null) {
            val at = rows.indexOfFirst { it.id == anchor }
            rows[at] = rows[at].copy(rawText = "[offset:$value]")
        } else {
            rows.add(0, LyricsSourceLine(UUID.randomUUID().toString(), "[offset:$value]", preferredNewline(source), LyricsSourceLineKind.METADATA))
        }
        return replace(source, rows)
    }

    fun insert(source: LyricsSourceDocument, after: String?): LyricsSourceDocument {
        val rows = source.lines.toMutableList()
        val index = after?.let { id -> rows.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.plus(1) } ?: rows.size
        rows.add(index, LyricsSourceLine(UUID.randomUUID().toString(), "", "", LyricsSourceLineKind.TEXT))
        return replace(source, repairBoundaries(source, rows))
    }

    fun remove(source: LyricsSourceDocument, id: String): LyricsSourceDocument = replace(source,
        repairBoundaries(source, source.lines.filterNot { it.id == id }.ifEmpty {
            listOf(LyricsSourceLine(UUID.randomUUID().toString(), "", "", LyricsSourceLineKind.TEXT))
        }))

    fun move(source: LyricsSourceDocument, id: String, delta: Int): LyricsSourceDocument {
        val rows = source.lines.toMutableList()
        val from = rows.indexOfFirst { it.id == id }
        require(from >= 0)
        val to = (from + delta).coerceIn(rows.indices)
        rows.add(to, rows.removeAt(from))
        return replace(source, repairBoundaries(source, rows))
    }

    private fun replace(source: LyricsSourceDocument, rows: List<LyricsSourceLine>): LyricsSourceDocument =
        parser.reparseLines(source, rows).also {
            if (it.byteSize > LyricsEditingLimits.DOCUMENT_BYTES) throw LyricsEditException(LyricsEditError.TOO_LARGE)
        }

    // A moved/inserted final row needs a separator, not concatenation with its neighbour.
    private fun repairBoundaries(source: LyricsSourceDocument, rows: List<LyricsSourceLine>): List<LyricsSourceLine> =
        rows.mapIndexed { index, row -> when {
            index == rows.lastIndex -> row.copy(newline = "")
            row.newline.isEmpty() -> row.copy(newline = preferredNewline(source))
            else -> row
        } }

    private fun preferredNewline(source: LyricsSourceDocument) = source.lines.firstOrNull { it.newline.isNotEmpty() }?.newline ?: "\n"

    companion object {
        fun parseTime(text: String): Long {
            val match = Regex("^(\\d+):([0-5]\\d)(?:\\.(\\d{1,3}))?$").matchEntire(text.trim())
                ?: throw LyricsEditException(LyricsEditError.INVALID_TIMESTAMP)
            return try {
                val minutes = match.groupValues[1].toLong()
                val seconds = match.groupValues[2].toLong()
                val millis = match.groupValues[3].padEnd(3, '0').toLong()
                Math.addExact(Math.addExact(Math.multiplyExact(minutes, 60_000L), seconds * 1000), millis)
            } catch (_: ArithmeticException) { throw LyricsEditException(LyricsEditError.INVALID_TIMESTAMP) }
              catch (_: NumberFormatException) { throw LyricsEditException(LyricsEditError.INVALID_TIMESTAMP) }
        }
        fun formatTime(ms: Long): String {
            if (ms < 0) throw LyricsEditException(LyricsEditError.INVALID_TIMESTAMP)
            return String.format(Locale.ROOT, "%02d:%02d.%03d", ms / 60_000, (ms / 1000) % 60, ms % 1000)
        }
    }
}
