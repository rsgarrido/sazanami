package io.github.rsgarrido.sazanami.lyrics

import io.github.rsgarrido.sazanami.data.local.LyricsOverrideEntity

internal object LyricsCommitPolicy {
    fun previous(rows: List<LyricsOverrideEntity>, baseline: LyricsDraftState): LyricsOverrideEntity? {
        if (rows.size > 1) throw LyricsEditException(LyricsEditError.TARGET_AMBIGUOUS)
        val previous = rows.singleOrNull()
        if (previous?.id != baseline.overrideId || previous?.revision != baseline.overrideRevision)
            throw LyricsEditException(LyricsEditError.REVISION_CONFLICT)
        return previous
    }
    fun checkBudget(otherCount: Int, otherBytes: Long, newBytes: Int) {
        if (newBytes > LyricsEditingLimits.DOCUMENT_BYTES || otherCount >= LyricsEditingLimits.OVERRIDES ||
            otherBytes > LyricsEditingLimits.TOTAL_OVERRIDE_BYTES - newBytes.toLong()) throw LyricsEditException(LyricsEditError.TOO_LARGE)
    }
}
