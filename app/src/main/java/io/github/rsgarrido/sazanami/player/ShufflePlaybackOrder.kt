package io.github.rsgarrido.sazanami.player

internal data class PlaybackTimelineMove(
    val fromIndex: Int,
    val toIndex: Int
)

internal data class PlaybackTimelineReplacement(
    val fromIndex: Int,
    val toIndex: Int,
    val replacementEntryIds: List<String>
)

/** Matches Play Next items to distinct upcoming occurrences without collapsing duplicates. */
internal fun matchQueuedEntryIdsForShuffle(
    timelineEntryReferences: List<Pair<String, String>>,
    currentEntryId: String,
    queuedReferenceKeys: List<String>
): List<String> {
    val currentIndex = timelineEntryReferences.indexOfFirst { (entryId, _) ->
        entryId == currentEntryId
    }
    if (currentIndex < 0) return emptyList()

    val unmatchedUpcoming = timelineEntryReferences.drop(currentIndex + 1).toMutableList()
    return buildList {
        queuedReferenceKeys.forEach { referenceKey ->
            val matchIndex = unmatchedUpcoming.indexOfFirst { (_, candidateReferenceKey) ->
                candidateReferenceKey == referenceKey
            }
            if (matchIndex >= 0) {
                add(unmatchedUpcoming.removeAt(matchIndex).first)
            }
        }
    }
}

/** Uses the phone controller when available and otherwise invokes the service-owned fallback. */
internal suspend fun routeExternalSongShuffleRequest(
    enabled: Boolean,
    controllerSetter: (Boolean) -> Boolean,
    serviceOnlySetter: suspend (Boolean) -> Unit
): Boolean {
    val usedController = controllerSetter(enabled)
    if (!usedController) serviceOnlySetter(enabled)
    return usedController
}

/**
 * Builds the explicit timeline order used for logical song shuffle.
 *
 * The current occurrence is addressed by its stable entry ID. Explicit queued entries stay
 * immediately after it, while every other entry is either shuffled or returned to canonical
 * base order.
 */
internal fun buildSongShufflePlaybackOrder(
    baseEntryIds: List<String>,
    currentEntryId: String,
    queuedEntryIds: List<String>,
    shuffleEnabled: Boolean,
    shuffleRemaining: (List<String>) -> List<String> = { entries -> entries.shuffled() }
): List<String>? {
    if (
        baseEntryIds.isEmpty() ||
        baseEntryIds.any(String::isBlank) ||
        baseEntryIds.distinct().size != baseEntryIds.size ||
        currentEntryId !in baseEntryIds
    ) {
        return null
    }

    val normalizedQueuedIds = queuedEntryIds
        .filter { entryId -> entryId != currentEntryId && entryId in baseEntryIds }
        .distinct()
    val queuedIdSet = normalizedQueuedIds.toSet()
    val ordinaryBaseIds = baseEntryIds.filterNot { entryId -> entryId in queuedIdSet }

    if (shuffleEnabled) {
        val remaining = ordinaryBaseIds.filterNot { entryId -> entryId == currentEntryId }
        val shuffled = shuffleRemaining(remaining)
        if (shuffled.size != remaining.size || shuffled.toSet() != remaining.toSet()) return null
        return listOf(currentEntryId) + normalizedQueuedIds + shuffled
    }

    val currentBaseIndex = ordinaryBaseIds.indexOf(currentEntryId)
    if (currentBaseIndex < 0) return null
    return ordinaryBaseIds.toMutableList().apply {
        addAll(currentBaseIndex + 1, normalizedQueuedIds)
    }
}

/**
 * Plans a permutation using moves of non-current entries only. Moving entries across the current
 * item lets Media3 update its index without replacing, seeking, or restarting that item.
 */
internal fun planCurrentPreservingTimelineMoves(
    currentOrder: List<String>,
    targetOrder: List<String>,
    currentEntryId: String
): List<PlaybackTimelineMove>? {
    if (
        currentOrder.isEmpty() ||
        currentOrder.distinct().size != currentOrder.size ||
        targetOrder.distinct().size != targetOrder.size ||
        currentOrder.toSet() != targetOrder.toSet() ||
        currentEntryId !in currentOrder
    ) {
        return null
    }

    val working = currentOrder.toMutableList()
    val moves = mutableListOf<PlaybackTimelineMove>()
    val targetCurrentIndex = targetOrder.indexOf(currentEntryId)

    fun move(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        check(working[fromIndex] != currentEntryId) {
            "The current queue entry must not be moved directly"
        }
        moves += PlaybackTimelineMove(fromIndex, toIndex)
        working.add(toIndex, working.removeAt(fromIndex))
    }

    for (targetIndex in 0 until targetCurrentIndex) {
        val desiredEntryId = targetOrder[targetIndex]
        move(working.indexOf(desiredEntryId), targetIndex)
    }

    while (working.indexOf(currentEntryId) > targetCurrentIndex) {
        move(targetCurrentIndex, working.indexOf(currentEntryId))
    }
    check(working.indexOf(currentEntryId) == targetCurrentIndex)

    for (targetIndex in targetCurrentIndex + 1 until targetOrder.size) {
        val desiredEntryId = targetOrder[targetIndex]
        move(working.indexOf(desiredEntryId), targetIndex)
    }

    check(working == targetOrder)
    return moves
}

/**
 * Plans an exact timeline replacement without replacing the current occurrence.
 *
 * The suffix is replaced first so that the original current index remains a stable boundary for
 * the prefix replacement. Neither operation contains the current item, and no operation relies on
 * indices produced by a previous permutation move.
 */
internal fun planCurrentPreservingTimelineReplacements(
    currentOrder: List<String>,
    targetOrder: List<String>,
    currentEntryId: String
): List<PlaybackTimelineReplacement>? {
    if (
        currentOrder.isEmpty() ||
        currentOrder.distinct().size != currentOrder.size ||
        targetOrder.distinct().size != targetOrder.size ||
        currentOrder.toSet() != targetOrder.toSet() ||
        currentEntryId !in currentOrder
    ) {
        return null
    }

    val currentIndex = currentOrder.indexOf(currentEntryId)
    val targetCurrentIndex = targetOrder.indexOf(currentEntryId)
    val replacements = mutableListOf<PlaybackTimelineReplacement>()
    val currentSuffix = currentOrder.drop(currentIndex + 1)
    val targetSuffix = targetOrder.drop(targetCurrentIndex + 1)
    val currentPrefix = currentOrder.take(currentIndex)
    val targetPrefix = targetOrder.take(targetCurrentIndex)

    if (currentSuffix != targetSuffix) {
        replacements += PlaybackTimelineReplacement(
            fromIndex = currentIndex + 1,
            toIndex = currentOrder.size,
            replacementEntryIds = targetSuffix
        )
    }
    if (currentPrefix != targetPrefix) {
        replacements += PlaybackTimelineReplacement(
            fromIndex = 0,
            toIndex = currentIndex,
            replacementEntryIds = targetPrefix
        )
    }

    return replacements
}
