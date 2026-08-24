package com.example.cdplaya.performance

import androidx.tracing.trace

internal object PerformanceTraceNames {
    const val PREFERENCES_READY = "CDP.PreferencesReady"
    const val CACHE_FIRST_PUBLICATION = "CDP.CacheFirstPublication"
    const val INITIAL_INDEX_PUBLICATION = "CDP.InitialIndexPublication"
    const val MEDIASTORE_INDEX_QUERY = "CDP.MediaStoreIndexQuery"
    const val LIBRARY_CLASSIFICATION = "CDP.LibraryClassification"
    const val LIBRARY_ENRICHMENT = "CDP.LibraryEnrichment"
    const val LIBRARY_INDEX_CONSTRUCTION = "CDP.LibraryIndexConstruction"
    const val LIBRARY_PUBLICATION = "CDP.LibraryPublication"
    const val ARTWORK_REPAIR_BATCH = "CDP.ArtworkRepairBatch"
    const val RECONCILIATION_PLAN = "CDP.ReconciliationPlan"
    const val PLAYBACK_CONNECT = "CDP.PlaybackConnect"
    const val PLAYBACK_METADATA_REPLACEMENT = "CDP.PlaybackMetadataReplacement"
    const val PLAYBACK_QUEUE_REPLACEMENT = "CDP.PlaybackQueueReplacement"
    const val AUDIO_OFFLOAD_PREFERENCE_APPLIED = "CDP.AudioOffloadPreferenceApplied"
    const val AUDIO_OFFLOAD_STATE_CHANGED = "CDP.AudioOffloadStateChanged"
    const val AUDIO_OFFLOAD_SLEEPING_CHANGED = "CDP.AudioOffloadSleepingChanged"
    const val AUDIO_INPUT_FORMAT_CHANGED = "CDP.AudioInputFormatChanged"
    const val AUDIO_ROUTE_CHANGED = "CDP.AudioRouteChanged"
    const val POCKET_FLIP_UPDATE = "CDP.PocketFlipUpdate"
    const val POCKET_FLIP_DRAW = "CDP.PocketFlipDraw"
    const val POCKET_CASSETTE_UPDATE = "CDP.PocketCassetteUpdate"
    const val POCKET_CASSETTE_DRAW = "CDP.PocketCassetteDraw"
    const val RETRO_RACK_UPDATE = "CDP.RetroRackUpdate"
    const val RETRO_RACK_DRAW = "CDP.RetroRackDraw"
}

internal object PerformanceTracing {
    @Volatile
    var bypassForTests: Boolean = false
}

internal inline fun <T> tracePerformance(
    name: String,
    block: () -> T
): T = if (PerformanceTracing.bypassForTests) block() else trace(name, block)
