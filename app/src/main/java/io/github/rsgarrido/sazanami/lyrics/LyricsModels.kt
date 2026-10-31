package io.github.rsgarrido.sazanami.lyrics

sealed interface LyricsDocument {
    val metadata: LyricsMetadata

    data class Synced(
        val cues: List<LyricCue>,
        override val metadata: LyricsMetadata = LyricsMetadata()
    ) : LyricsDocument {
        init {
            require(cues.isNotEmpty()) { "Synced lyrics must contain at least one cue" }
            require(cues.zipWithNext().all { (first, second) ->
                first.timestampMs <= second.timestampMs
            }) {
                "Synced lyric cues must be sorted by timestamp"
            }
        }
    }

    data class Unsynced(
        val lines: List<StaticLyricLine>,
        override val metadata: LyricsMetadata = LyricsMetadata()
    ) : LyricsDocument
}

data class LyricCue(
    val timestampMs: Long,
    val content: LyricCueContent,
    val sourceLineId: String? = null,
    val sourceTimestampMs: Long = timestampMs,
    val segments: List<TimedLyricSegment> = emptyList()
) {
    init {
        require(timestampMs >= 0L) { "Lyric cue timestamps cannot be negative" }
    }
}

sealed interface LyricCueContent {
    data class Text(val text: String) : LyricCueContent {
        init {
            require(text.isNotBlank()) { "Visible lyric text cannot be blank" }
        }
    }

    data object Instrumental : LyricCueContent
}

data class TimedLyricSegment(
    val sourceTimestampMs: Long,
    val timestampMs: Long,
    val startChar: Int,
    val endCharExclusive: Int
)

data class StaticLyricLine(val text: String)

data class LyricsMetadata(
    val artist: String? = null,
    val album: String? = null,
    val title: String? = null,
    val creator: String? = null,
    val editor: String? = null,
    val version: String? = null,
    val offsetMs: Long = 0L
)

sealed interface LyricsSource {
    val stableId: String

    data class LocalOverride(val id: String) : LyricsSource {
        override val stableId: String = "local-lyrics:$id"
    }

    data class LocalSidecar(
        val documentUri: String,
        val displayName: String
    ) : LyricsSource {
        override val stableId: String = documentUri
    }

    data class Embedded(
        val trackKey: String,
        val field: String,
        val language: String? = null,
        val description: String? = null,
        val ordinal: Int = 0,
        val representation: EmbeddedLyricsRepresentation = EmbeddedLyricsRepresentation.TEXT,
        /** Original ID3 encoding byte, when available; source text itself is already decoded. */
        val textEncoding: Int? = null
    ) : LyricsSource {
        override val stableId: String = "embedded:" + lyricsIdentity(
            trackKey, representation.name, field, language.orEmpty(), description.orEmpty(), ordinal.toString())
    }
}

data class SourcedLyrics(
    val document: LyricsDocument,
    val source: LyricsSource,
    val sourceDocument: LyricsSourceDocument? = null,
    val revision: String = sourceDocument?.revision.orEmpty(),
    val issues: List<LyricsSourceIssue> = emptyList(),
    val alternatives: List<LyricsSource> = emptyList()
) {
    val timing: LyricsTiming
        get() = when (document) {
            is LyricsDocument.Unsynced -> LyricsTiming.PLAIN
            is LyricsDocument.Synced -> if (document.cues.any { it.segments.isNotEmpty() }) LyricsTiming.WORD else LyricsTiming.LINE
        }
}

enum class LyricsTiming { PLAIN, LINE, WORD }
enum class EmbeddedLyricsRepresentation { TEXT, ID3_USLT, VORBIS_COMMENT, MP4_ATOM }

data class LyricsSourceIssue(
    val sourceId: String,
    val kind: Kind,
    val candidates: List<LyricsSource> = emptyList(),
    val externalCandidates: List<LyricsCandidate> = emptyList()
) {
    enum class Kind {
        NO_ROOTS, NOT_FOUND, UNSUPPORTED_FORMAT, UNSUPPORTED_ACCESS, PERMISSION_DENIED,
        READ_FAILURE, METADATA_FAILURE, INVALID_LYRICS, INPUT_LIMIT, AMBIGUOUS, SCAN_FAILURE,
        STALE_FILE, LOOKUP_FAILURE, UNSUPPORTED_TIMING
    }
}
