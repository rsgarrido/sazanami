package io.github.rsgarrido.sazanami.performance

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** Opt-in benchmark/test counters. Disabled during ordinary app use. */
internal object VisualizerPerformanceCounters {
    @Volatile
    var enabled: Boolean = false

    private val activeLoops = AtomicInteger()
    private val updates = AtomicLong()
    private val draws = AtomicLong()
    private val skippedUpdates = AtomicLong()
    private val targetCadenceHz = AtomicInteger()

    fun onLoopStarted(cadenceHz: Int) {
        if (!enabled) return
        activeLoops.incrementAndGet()
        targetCadenceHz.set(cadenceHz)
    }

    fun onLoopStopped() {
        if (!enabled) return
        activeLoops.decrementAndGet()
    }

    fun onUpdate() {
        if (enabled) updates.incrementAndGet()
    }

    fun onDraw() {
        if (enabled) draws.incrementAndGet()
    }

    fun onSkippedUpdate() {
        if (enabled) skippedUpdates.incrementAndGet()
    }

    fun snapshot(): VisualizerCounterSnapshot = VisualizerCounterSnapshot(
        activeLoops = activeLoops.get(),
        updates = updates.get(),
        draws = draws.get(),
        skippedUpdates = skippedUpdates.get(),
        targetCadenceHz = targetCadenceHz.get()
    )

    fun reset() {
        activeLoops.set(0)
        updates.set(0)
        draws.set(0)
        skippedUpdates.set(0)
        targetCadenceHz.set(0)
    }
}

internal data class VisualizerCounterSnapshot(
    val activeLoops: Int,
    val updates: Long,
    val draws: Long,
    val skippedUpdates: Long,
    val targetCadenceHz: Int
)
