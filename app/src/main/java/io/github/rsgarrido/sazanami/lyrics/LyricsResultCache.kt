package io.github.rsgarrido.sazanami.lyrics

/** Small process-local LRU. No audio bytes, persisted state or independent playback clock. */
internal class LyricsResultCache(
    private val clockMs: () -> Long,
    private val maximumEntries: Int = 8,
    private val maximumWeight: Long = 16L * 1024 * 1024
) {
    data class Key(val song: SongLyricsIdentity,
        val parserVersion: Int = LyricsSourceDocument.REPRESENTATION_VERSION)
    private data class Entry(val result: LyricsLookupResult, val expires: Long, val weight: Long)
    private val entries = LinkedHashMap<Key, Entry>(16, 0.75f, true)

    @Synchronized fun get(key: Key): LyricsLookupResult? {
        val entry = entries[key] ?: return null
        if (clockMs() >= entry.expires) { entries.remove(key); return null }
        return entry.result
    }

    @Synchronized fun put(key: Key, result: LyricsLookupResult) {
        val found = (result as? LyricsLookupResult.Found)?.lyrics
        val source = found?.sourceDocument
        val cues = (found?.document as? LyricsDocument.Synced)?.cues.orEmpty()
        // Conservative retained-memory estimate (bytes + raw/decoded/display strings + models).
        val weight = 1024L + (source?.byteSize ?: 0).toLong() * 8 +
            (source?.lines?.size ?: 0) * 256L + cues.size * 192L + cues.sumOf { it.segments.size.toLong() } * 128
        if (weight > maximumWeight) return
        entries[key] = Entry(result, clockMs() + if (found == null) 5_000L else 30_000L, weight)
        while (entries.size > maximumEntries || entries.values.sumOf(Entry::weight) > maximumWeight) {
            entries.remove(entries.keys.first())
        }
    }

    @Synchronized fun invalidate(change: LyricsInvalidation) {
        entries.entries.removeAll { (key, entry) ->
            val found = (entry.result as? LyricsLookupResult.Found)?.lyrics
            (change.trackKey == null || key.song.trackKey == change.trackKey) &&
                // Negative lookups and alternative sources also need invalidation on edits.
                (change.trackKey != null || change.sourceId == null || found == null || found.source.stableId == change.sourceId ||
                    found.issues.any { it.sourceId == change.sourceId } ||
                    found.issues.any { issue -> issue.externalCandidates.any { it.documentUri == change.sourceId } } ||
                    found.alternatives.any { it.stableId == change.sourceId })
        }
    }
}
