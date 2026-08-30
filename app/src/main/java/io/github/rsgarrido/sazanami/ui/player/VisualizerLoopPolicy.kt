package io.github.rsgarrido.sazanami.ui.player

internal data class VisualizerLoopConditions(
    val themeSelected: Boolean,
    val expandedPlayerVisible: Boolean,
    val lifecycleStarted: Boolean,
    val coveredByOverlay: Boolean,
    val playbackActive: Boolean,
    val effectivelySilent: Boolean
)

internal fun VisualizerLoopConditions.shouldRun(): Boolean =
    themeSelected &&
        expandedPlayerVisible &&
        lifecycleStarted &&
        !coveredByOverlay &&
        playbackActive &&
        !effectivelySilent
