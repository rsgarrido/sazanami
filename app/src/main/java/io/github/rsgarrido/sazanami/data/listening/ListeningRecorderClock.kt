package io.github.rsgarrido.sazanami.data.listening

/** A process-local, monotonic time source used only for measuring elapsed listening time. */
fun interface MonotonicClock {
    fun elapsedRealtimeMs(): Long
}

/** A civil time source used only for timestamps that will be persisted. */
fun interface WallClock {
    fun currentTimeMillis(): Long
}

/** Generates the stable event identifier at successful finalization time. */
fun interface ListeningEventUuidGenerator {
    fun newUuid(): String
}
