package io.github.rsgarrido.sazanami.lyrics

data class ActiveLyricGroup(
    val timestampMs: Long,
    val lines: List<String>
) {
    init {
        require(lines.isNotEmpty()) { "An active lyric group must contain visible text" }
    }
}

object ActiveLyricResolver {
    /**
     * Resolves the last timestamp at or before [positionMs] with an upper-bound binary search.
     * Cues are expected to be sorted, as guaranteed by [LyricsDocument.Synced].
     */
    fun resolve(cues: List<LyricCue>, positionMs: Long): ActiveLyricGroup? {
        if (cues.isEmpty() || positionMs < cues.first().timestampMs) return null

        var low = 0
        var high = cues.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (cues[middle].timestampMs <= positionMs) {
                low = middle + 1
            } else {
                high = middle
            }
        }

        val timestampMs = cues[low - 1].timestampMs
        var firstAtTimestamp = low - 1
        while (
            firstAtTimestamp > 0 &&
            cues[firstAtTimestamp - 1].timestampMs == timestampMs
        ) {
            firstAtTimestamp--
        }

        val lines = buildList {
            var index = firstAtTimestamp
            while (index < cues.size && cues[index].timestampMs == timestampMs) {
                val content = cues[index].content
                if (content is LyricCueContent.Text) add(content.text)
                index++
            }
        }
        return lines.takeIf(List<String>::isNotEmpty)?.let {
            ActiveLyricGroup(timestampMs = timestampMs, lines = it)
        }
    }
}
