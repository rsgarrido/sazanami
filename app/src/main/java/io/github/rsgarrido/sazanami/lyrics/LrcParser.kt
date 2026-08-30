package io.github.rsgarrido.sazanami.lyrics

import java.nio.charset.Charset

class LrcParser {
    fun parse(bytes: ByteArray): LyricsDocument = runCatching {
        parseDecodedText(decode(bytes))
    }.getOrElse {
        LyricsDocument.Unsynced(emptyList())
    }

    fun parse(text: String): LyricsDocument = runCatching {
        parseDecodedText(text.removePrefix(UTF_8_BOM_CHARACTER.toString()))
    }.getOrElse {
        LyricsDocument.Unsynced(emptyList())
    }

    private fun parseDecodedText(text: String): LyricsDocument {
        val metadataValues = MetadataValues()
        val timedLines = mutableListOf<ParsedTimedLine>()
        val staticLines = mutableListOf<StaticLyricLine>()
        var sourceOrder = 0

        text.lineSequence().forEach { sourceLine ->
            val line = sourceLine.removeSuffix("\r")
            val metadataMatch = METADATA_TAG.matchEntire(line.trim())
            if (metadataMatch != null) {
                metadataValues.accept(
                    key = metadataMatch.groupValues[1],
                    value = metadataMatch.groupValues[2]
                )
                return@forEach
            }

            val timestamped = parseTimestampedLine(line, sourceOrder)
            if (timestamped != null) {
                timedLines += timestamped
                sourceOrder += timestamped.timestampsMs.size
            } else if (!line.trimStart().startsWith("[")) {
                staticLines += StaticLyricLine(line)
            }
        }

        val metadata = metadataValues.toMetadata()
        if (timedLines.isEmpty()) {
            return LyricsDocument.Unsynced(
                lines = staticLines.dropTrailingEmptyLines(),
                metadata = metadata
            )
        }

        val cues = timedLines
            .flatMap { parsed ->
                parsed.timestampsMs.mapIndexed { index, timestampMs ->
                    OrderedCue(
                        cue = LyricCue(
                            timestampMs = applyOffset(timestampMs, metadata.offsetMs),
                            content = parsed.content
                        ),
                        sourceOrder = parsed.sourceOrder + index
                    )
                }
            }
            .sortedWith(compareBy<OrderedCue>({ it.cue.timestampMs }, { it.sourceOrder }))
            .map(OrderedCue::cue)

        return LyricsDocument.Synced(cues = cues, metadata = metadata)
    }

    private fun parseTimestampedLine(line: String, sourceOrder: Int): ParsedTimedLine? {
        val candidate = line.trimStart()
        val timestamps = mutableListOf<Long>()
        var nextIndex = 0

        while (nextIndex < candidate.length && candidate[nextIndex] == '[') {
            val match = TIMESTAMP_TAG.find(candidate, nextIndex)
                ?.takeIf { it.range.first == nextIndex }
                ?: break
            val timestamp = timestampMs(match) ?: return null
            timestamps += timestamp
            nextIndex = match.range.last + 1
        }

        if (timestamps.isEmpty()) return null

        val displayedText = ENHANCED_TIMESTAMP.replace(
            candidate.substring(nextIndex),
            ""
        )
        val content = if (displayedText.isBlank()) {
            LyricCueContent.Instrumental
        } else {
            LyricCueContent.Text(displayedText)
        }
        return ParsedTimedLine(
            timestampsMs = timestamps,
            content = content,
            sourceOrder = sourceOrder
        )
    }

    private fun timestampMs(match: MatchResult): Long? {
        val minutes = match.groupValues[1].toLongOrNull() ?: return null
        val seconds = match.groupValues[2].toLongOrNull()
            ?.takeIf { it in 0L..59L }
            ?: return null
        val fraction = match.groupValues[3]
        val fractionalMs = when (fraction.length) {
            0 -> 0L
            1 -> fraction.toLongOrNull()?.times(100L)
            2 -> fraction.toLongOrNull()?.times(10L)
            3 -> fraction.toLongOrNull()
            else -> null
        } ?: return null

        if (minutes > (Long.MAX_VALUE - seconds * 1_000L - fractionalMs) / 60_000L) {
            return null
        }
        return minutes * 60_000L + seconds * 1_000L + fractionalMs
    }

    private fun applyOffset(timestampMs: Long, offsetMs: Long): Long {
        val adjusted = when {
            offsetMs > 0L && timestampMs > Long.MAX_VALUE - offsetMs -> Long.MAX_VALUE
            offsetMs == Long.MIN_VALUE -> 0L
            offsetMs < 0L && timestampMs < -offsetMs -> 0L
            else -> timestampMs + offsetMs
        }
        return adjusted.coerceAtLeast(0L)
    }

    private fun decode(bytes: ByteArray): String = when {
        bytes.startsWith(UTF_8_BOM) ->
            bytes.decode(UTF_8_BOM.size, Charsets.UTF_8)

        bytes.startsWith(UTF_16_LE_BOM) ->
            bytes.decode(UTF_16_LE_BOM.size, Charsets.UTF_16LE)

        bytes.startsWith(UTF_16_BE_BOM) ->
            bytes.decode(UTF_16_BE_BOM.size, Charsets.UTF_16BE)

        else -> bytes.toString(Charsets.UTF_8)
    }

    private fun ByteArray.decode(offset: Int, charset: Charset): String =
        String(this, offset, size - offset, charset)

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { index -> this[index] == prefix[index] }

    private fun List<StaticLyricLine>.dropTrailingEmptyLines(): List<StaticLyricLine> =
        dropLastWhile { it.text.isEmpty() }

    private data class ParsedTimedLine(
        val timestampsMs: List<Long>,
        val content: LyricCueContent,
        val sourceOrder: Int
    )

    private data class OrderedCue(
        val cue: LyricCue,
        val sourceOrder: Int
    )

    private class MetadataValues {
        private var artist: String? = null
        private var album: String? = null
        private var title: String? = null
        private var creator: String? = null
        private var editor: String? = null
        private var version: String? = null
        private var offsetMs: Long = 0L

        fun accept(key: String, value: String) {
            when (key.lowercase()) {
                "ar" -> artist = value
                "al" -> album = value
                "ti" -> title = value
                "by" -> creator = value
                "re" -> editor = value
                "ve" -> version = value
                "offset" -> value.trim().toLongOrNull()?.let { offsetMs = it }
            }
        }

        fun toMetadata() = LyricsMetadata(
            artist = artist,
            album = album,
            title = title,
            creator = creator,
            editor = editor,
            version = version,
            offsetMs = offsetMs
        )
    }

    private companion object {
        private const val UTF_8_BOM_CHARACTER = '\uFEFF'
        private val UTF_8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        private val UTF_16_LE_BOM = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        private val UTF_16_BE_BOM = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
        private val METADATA_TAG = Regex("""\[([A-Za-z]+):(.*)]""")
        private val TIMESTAMP_TAG = Regex("""\[(\d+):(\d{2})(?:\.(\d{1,3}))?]""")
        private val ENHANCED_TIMESTAMP = Regex("""<\d+:\d{2}(?:\.\d{1,3})?>""")
    }
}
