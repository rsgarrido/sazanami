package io.github.rsgarrido.sazanami.lyrics

enum class EmbeddedLyricsReadStage {
    FILE_ACCESS, PROVIDER_OPEN, DESCRIPTOR_PROBE, FILE_OPEN,
    METADATA_PREFLIGHT, CONTAINER_PARSING, LYRICS_EXTRACTION
}

/**
 * Opt-in observer for controlled diagnostic/test use. Production defaults to no logging.
 * Exceptions may contain private paths: callers must not log them for arbitrary user sources.
 * Observers must not throw or retain an unbounded history.
 */
fun interface EmbeddedLyricsDiagnostics {
    fun onFailure(stage: EmbeddedLyricsReadStage, failure: Throwable)

    companion object {
        val NONE = EmbeddedLyricsDiagnostics { _, _ -> }
    }
}
