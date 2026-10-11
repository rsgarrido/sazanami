package io.github.rsgarrido.sazanami.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One immutable attempt snapshot for debug reports, independent of the current audio mode. */
internal class IntegratedDirectAttemptEvidence {
    private val mutableLatest=MutableStateFlow<IntegratedDirectPlaybackState?>(null)
    val latest=mutableLatest.asStateFlow()

    fun record(state: IntegratedDirectPlaybackState) {
        if (!state.enabled || state.phase==IntegratedDirectPhase.IDLE || state.generation<=0) return
        val previous=mutableLatest.value
        // Explicit Stop/cleanup can publish STOPPED after a terminal result. Keep that
        // result's original phase/error/progress rather than relabeling the report.
        if (previous!=null && previous.phase in setOf(IntegratedDirectPhase.ERROR,IntegratedDirectPhase.ENDED) &&
            state.phase==IntegratedDirectPhase.STOPPED && state.generation==previous.generation &&
            state.engine===previous.engine) return
        mutableLatest.value=state
    }
}
