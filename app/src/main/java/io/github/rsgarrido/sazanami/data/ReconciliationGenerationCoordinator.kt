package io.github.rsgarrido.sazanami.data

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes reconciliation work and prevents an obsolete snapshot from being published. */
internal class ReconciliationGenerationCoordinator {
    private val requestedGeneration = AtomicLong(0L)
    private val reconciliationMutex = Mutex()

    fun nextGeneration(): Long = requestedGeneration.incrementAndGet()

    fun isCurrent(generation: Long): Boolean = requestedGeneration.get() == generation

    suspend fun <T> runLatest(
        generation: Long,
        block: suspend () -> T
    ): T? = reconciliationMutex.withLock {
        if (!isCurrent(generation)) return@withLock null
        val value = block()
        value.takeIf { isCurrent(generation) }
    }
}
