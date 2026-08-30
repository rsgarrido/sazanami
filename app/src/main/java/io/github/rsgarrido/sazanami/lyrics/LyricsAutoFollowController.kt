package io.github.rsgarrido.sazanami.lyrics

data class LyricsScrollRequest(
    val itemIndex: Int,
    val animate: Boolean
)

class LyricsAutoFollowController(
    private val largeJumpThreshold: Int = 8
) {
    var isEnabled: Boolean = true
        private set
    private var lastRequestedIndex: Int? = null
    private var lastAnchorRevision: Any? = null

    fun onTrackChanged() {
        isEnabled = true
        lastRequestedIndex = null
        lastAnchorRevision = null
    }

    fun onUserScroll() {
        isEnabled = false
    }

    fun onActiveItemChanged(
        itemIndex: Int?,
        anchorRevision: Any? = Unit
    ): LyricsScrollRequest? {
        if (!isEnabled || itemIndex == null) return null
        if (itemIndex == lastRequestedIndex && anchorRevision == lastAnchorRevision) return null
        val previous = lastRequestedIndex
        lastRequestedIndex = itemIndex
        lastAnchorRevision = anchorRevision
        return LyricsScrollRequest(
            itemIndex = itemIndex,
            animate = previous != null && kotlin.math.abs(itemIndex - previous) <= largeJumpThreshold
        )
    }

    fun returnToCurrent(itemIndex: Int?): LyricsScrollRequest? {
        isEnabled = true
        if (itemIndex == null) return null
        lastRequestedIndex = itemIndex
        return LyricsScrollRequest(itemIndex = itemIndex, animate = true)
    }
}
