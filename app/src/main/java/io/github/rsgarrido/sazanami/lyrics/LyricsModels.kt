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
    val content: LyricCueContent
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

    data class LocalSidecar(
        val documentUri: String,
        val displayName: String
    ) : LyricsSource {
        override val stableId: String = documentUri
    }
}

data class SourcedLyrics(
    val document: LyricsDocument,
    val source: LyricsSource
)
