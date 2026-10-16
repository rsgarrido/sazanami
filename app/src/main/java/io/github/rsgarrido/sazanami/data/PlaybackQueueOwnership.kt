package io.github.rsgarrido.sazanami.data

import io.github.rsgarrido.sazanami.data.local.PlaybackQueueEntity

/** Stored in existing provenance columns so legacy queues need no reclassification. */
const val PLAYBACK_QUEUE_WORKING_SOURCE_TYPE = "sazanami:working:v1"

enum class PlaybackQueueWorkingOutput(val storageKey: String) {
    ANDROID("android"),
    DIRECT("direct")
}

val PlaybackQueueEntity.workingOutput: PlaybackQueueWorkingOutput?
    get() = if (sourceType == PLAYBACK_QUEUE_WORKING_SOURCE_TYPE) {
        PlaybackQueueWorkingOutput.entries.firstOrNull { it.storageKey == sourceKey }
    } else {
        null
    }
